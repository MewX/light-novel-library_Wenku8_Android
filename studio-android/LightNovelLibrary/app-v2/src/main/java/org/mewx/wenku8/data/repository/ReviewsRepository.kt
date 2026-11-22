package org.mewx.wenku8.data.repository

import android.os.SystemClock
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.data.local.db.Wenku8Database
import org.mewx.wenku8.data.local.db.entity.NovelEntity
import org.mewx.wenku8.data.local.db.entity.ReviewDraftEntity
import org.mewx.wenku8.data.remote.api.WenkuApiResult
import org.mewx.wenku8.data.remote.api.WenkuCatalogGateway
import kotlin.math.ceil

data class ReviewThreadCardItem(
    val rid: Int,
    val title: String,
    val uid: Int,
    val userName: String,
    val avatarUrl: String,
    val replyCount: Int,
    val postTimeFormatted: String,
    val lastReplyTimeFormatted: String,
)

data class ReviewThreadListResult(
    val currentPage: Int,
    val totalPages: Int,
    val items: List<ReviewThreadCardItem>,
)

data class ReviewReplyFloorItem(
    val floorNumber: Int,
    val isOriginalPosterFloor: Boolean,
    val uid: Int,
    val userName: String,
    val avatarUrl: String,
    val replyTimeFormatted: String,
    val content: String,
)

data class ReviewReplyPageResult(
    val currentPage: Int,
    val totalPages: Int,
    val items: List<ReviewReplyFloorItem>,
)

/**
 * Live pre-submit validation snapshot powering the M3e composer status chips:
 * `✓ 字数 >= 7`, `✓ 无敏感词`, and `✓ 10秒间隔就绪`.
 */
data class ReviewComposerValidation(
    val minRequiredChars: Int,
    val requireTitle: Boolean,
    val titleLengthOk: Boolean,
    val contentLengthOk: Boolean,
    val detectedBadWord: String?,
    val cooldownRemainingSeconds: Int,
) {
    val lengthSatisfied: Boolean
        get() = contentLengthOk && (!requireTitle || titleLengthOk)

    val noBadWordsSatisfied: Boolean
        get() = detectedBadWord == null

    val cooldownReady: Boolean
        get() = cooldownRemainingSeconds <= 0

    val canSubmit: Boolean
        get() = lengthSatisfied && noBadWordsSatisfied && cooldownReady
}

sealed interface ReviewSubmitOutcome {
    data object Success : ReviewSubmitOutcome
    data object ApiNotConfigured : ReviewSubmitOutcome
    data object NotLoggedIn : ReviewSubmitOutcome
    data class ValidationFailed(val validation: ReviewComposerValidation) : ReviewSubmitOutcome
    data class PostLockedOrCooldown(val remainingSeconds: Int) : ReviewSubmitOutcome
    data class NetworkOrServerError(
        val numericCode: Int? = null,
        val debugDetail: String? = null,
    ) : ReviewSubmitOutcome
}

/**
 * Community Reviews (`书评讨论`) repository for Wenku8 `:app-v2` (`P3.3`).
 *
 * Responsibilities:
 * 1. **Paginated Thread & Floor Loading**: Wraps `action=review&do=list` and `action=review&do=show`,
 *    resolving stub-safe `avatar.wenku8.com` URLs via [WenkuCatalogGateway.resolveAvatarUrl] and
 *    assigning sequential floor numbers (`1楼 · 楼主`, `2楼`, ...).
 * 2. **Live Pre-Submit Validation & `10s` Cooldown**: Enforces `>= MIN_REPLY_TEXT` (`7` chars),
 *    stub-safe `Wenku8API.searchBadWords()` checks across title and body, and Wenku8's `10s`
 *    inter-post rate limit via an injectable monotonic clock.
 * 3. **Bounded Single Re-Login**: Transparently calls
 *    [UserSessionRepository.reauthenticateFromSavedCredentialsIfPossible] at most once if posting
 *    returns `ServerErrorCode(4)` (`SYSTEM_4_NOT_LOGGED_IN`).
 * 4. **FK-Safe Room Draft Auto-Save (`REVIEW_DRAFTS`)**: Ensures a parent [NovelEntity] row exists
 *    inside a Room transaction before upserting `review_drafts(aid, rid)`, and clears the draft
 *    automatically on successful submission or when both fields become blank.
 */
class ReviewsRepository(
    private val database: Wenku8Database,
    private val catalogGateway: WenkuCatalogGateway,
    private val userSessionRepository: UserSessionRepository? = null,
    private val postCooldownMs: Long = DEFAULT_POST_COOLDOWN_MS,
    private val monotonicClockMs: () -> Long = { SystemClock.elapsedRealtime() },
    private val wallClockMs: () -> Long = { System.currentTimeMillis() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    companion object {
        const val DEFAULT_POST_COOLDOWN_MS: Long = 10_000L
        const val NEW_THREAD_DRAFT_RID: Int = 0
        const val SERVER_CODE_NOT_LOGGED_IN: Int = 4
        const val SERVER_CODE_POST_LOCKED_OR_COOLDOWN: Int = 11

        fun formatFloorQuotePrefix(floorNumber: Int, userName: String): String {
            val cleanUser = userName.trim()
            return if (cleanUser.isNotEmpty()) {
                "回复 ${floorNumber}楼 @$cleanUser：\n"
            } else {
                "回复 ${floorNumber}楼：\n"
            }
        }
    }

    private val submitMutex = Mutex()
    private val _cooldownUntilMonotonicMs = MutableStateFlow(0L)
    val cooldownUntilMonotonicMs: StateFlow<Long> = _cooldownUntilMonotonicMs.asStateFlow()

    val isReviewsApiConfigured: Boolean
        get() = catalogGateway.isApiConfigured

    val minReplyTextLength: Int
        get() = catalogGateway.minReplyTextLength.coerceAtLeast(1)

    fun remainingCooldownSeconds(nowMonotonicMs: Long = monotonicClockMs()): Int {
        val remainingMs = _cooldownUntilMonotonicMs.value - nowMonotonicMs
        if (remainingMs <= 0L) return 0
        return ceil(remainingMs.toDouble() / 1000.0).toInt().coerceAtLeast(1)
    }

    /**
     * Evaluates composer text synchronously for live status chips (`✓ 字数 >= 7`, `✓ 无敏感词`,
     * `✓ 10秒间隔就绪`) without triggering network calls.
     */
    fun evaluateComposer(
        title: String = "",
        content: String,
        requireTitle: Boolean,
        nowMonotonicMs: Long = monotonicClockMs(),
    ): ReviewComposerValidation {
        val minLen = minReplyTextLength
        val trimmedTitle = title.trim()
        val trimmedContent = content.trim()

        val titleLengthOk = !requireTitle || trimmedTitle.length >= minLen
        val contentLengthOk = trimmedContent.length >= minLen

        val badWordInTitle = if (requireTitle && trimmedTitle.isNotEmpty()) {
            catalogGateway.findBadWord(trimmedTitle)
        } else {
            null
        }
        val badWordInContent = if (badWordInTitle == null && trimmedContent.isNotEmpty()) {
            catalogGateway.findBadWord(trimmedContent)
        } else {
            null
        }

        return ReviewComposerValidation(
            minRequiredChars = minLen,
            requireTitle = requireTitle,
            titleLengthOk = titleLengthOk,
            contentLengthOk = contentLengthOk,
            detectedBadWord = badWordInTitle ?: badWordInContent,
            cooldownRemainingSeconds = remainingCooldownSeconds(nowMonotonicMs),
        )
    }

    suspend fun fetchThreadListPage(
        aid: Int,
        pageNumber: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<ReviewThreadListResult> = withContext(ioDispatcher) {
        val safePage = pageNumber.coerceAtLeast(1)
        when (val result = catalogGateway.fetchReviewThreadListPage(aid, safePage, appLanguage)) {
            is WenkuApiResult.Success -> {
                val decoratedItems = result.data.items.map { raw ->
                    ReviewThreadCardItem(
                        rid = raw.rid,
                        title = raw.title,
                        uid = raw.uid,
                        userName = raw.userName,
                        avatarUrl = catalogGateway.resolveAvatarUrl(raw.uid),
                        replyCount = raw.replyCount,
                        postTimeFormatted = raw.postTimeFormatted,
                        lastReplyTimeFormatted = raw.lastReplyTimeFormatted,
                    )
                }
                WenkuApiResult.Success(
                    ReviewThreadListResult(
                        currentPage = safePage,
                        totalPages = result.data.totalPages.coerceAtLeast(1),
                        items = decoratedItems,
                    ),
                )
            }
            WenkuApiResult.ApiNotConfigured -> WenkuApiResult.ApiNotConfigured
            is WenkuApiResult.UpgradeRequired -> result
            WenkuApiResult.AuthRejected -> WenkuApiResult.AuthRejected
            is WenkuApiResult.NetworkError -> result
            is WenkuApiResult.ServerErrorCode -> result
            WenkuApiResult.EmptyResponse -> WenkuApiResult.EmptyResponse
            is WenkuApiResult.ParseError -> result
        }
    }

    suspend fun fetchReplyPage(
        rid: Int,
        pageNumber: Int,
        startingFloorOffset: Int = 0,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<ReviewReplyPageResult> = withContext(ioDispatcher) {
        val safePage = pageNumber.coerceAtLeast(1)
        val safeOffset = startingFloorOffset.coerceAtLeast(0)
        when (val result = catalogGateway.fetchReviewReplyPage(rid, safePage, appLanguage)) {
            is WenkuApiResult.Success -> {
                val decoratedFloors = result.data.items.mapIndexed { index, raw ->
                    val floorNumber = safeOffset + index + 1
                    ReviewReplyFloorItem(
                        floorNumber = floorNumber,
                        isOriginalPosterFloor = floorNumber == 1,
                        uid = raw.uid,
                        userName = raw.userName,
                        avatarUrl = catalogGateway.resolveAvatarUrl(raw.uid),
                        replyTimeFormatted = raw.replyTimeFormatted,
                        content = raw.content,
                    )
                }
                WenkuApiResult.Success(
                    ReviewReplyPageResult(
                        currentPage = safePage,
                        totalPages = result.data.totalPages.coerceAtLeast(1),
                        items = decoratedFloors,
                    ),
                )
            }
            WenkuApiResult.ApiNotConfigured -> WenkuApiResult.ApiNotConfigured
            is WenkuApiResult.UpgradeRequired -> result
            WenkuApiResult.AuthRejected -> WenkuApiResult.AuthRejected
            is WenkuApiResult.NetworkError -> result
            is WenkuApiResult.ServerErrorCode -> result
            WenkuApiResult.EmptyResponse -> WenkuApiResult.EmptyResponse
            is WenkuApiResult.ParseError -> result
        }
    }

    suspend fun submitNewThread(
        aid: Int,
        novelTitleHint: String = "",
        title: String,
        content: String,
    ): ReviewSubmitOutcome = withContext(ioDispatcher) {
        submitMutex.withLock {
            if (!catalogGateway.isApiConfigured) {
                return@withLock ReviewSubmitOutcome.ApiNotConfigured
            }
            if (!isAccountReadyForPosting()) {
                return@withLock ReviewSubmitOutcome.NotLoggedIn
            }

            val validation = evaluateComposer(
                title = title,
                content = content,
                requireTitle = true,
            )
            if (!validation.lengthSatisfied || !validation.noBadWordsSatisfied) {
                return@withLock ReviewSubmitOutcome.ValidationFailed(validation)
            }
            if (!validation.cooldownReady) {
                return@withLock ReviewSubmitOutcome.PostLockedOrCooldown(validation.cooldownRemainingSeconds)
            }

            val cleanTitle = title.trim()
            val cleanContent = content.trim()
            var postResult = catalogGateway.postNewReviewThread(
                aid = aid,
                title = cleanTitle,
                content = cleanContent,
            )
            if (postResult is WenkuApiResult.ServerErrorCode &&
                postResult.numericCode == SERVER_CODE_NOT_LOGGED_IN &&
                userSessionRepository != null &&
                userSessionRepository.reauthenticateFromSavedCredentialsIfPossible()
            ) {
                postResult = catalogGateway.postNewReviewThread(
                    aid = aid,
                    title = cleanTitle,
                    content = cleanContent,
                )
            }

            val outcome = mapMutationResultAndHandleDraft(
                aid = aid,
                rid = NEW_THREAD_DRAFT_RID,
                novelTitleHint = novelTitleHint,
                result = postResult,
            )
            org.mewx.wenku8.util.AnalyticsTracker.logReviewPost(
                aid = aid,
                success = outcome is ReviewSubmitOutcome.Success,
            )
            outcome
        }
    }

    suspend fun submitReply(
        aid: Int,
        novelTitleHint: String = "",
        rid: Int,
        content: String,
    ): ReviewSubmitOutcome = withContext(ioDispatcher) {
        submitMutex.withLock {
            if (!catalogGateway.isApiConfigured) {
                return@withLock ReviewSubmitOutcome.ApiNotConfigured
            }
            if (!isAccountReadyForPosting()) {
                return@withLock ReviewSubmitOutcome.NotLoggedIn
            }

            val validation = evaluateComposer(
                title = "",
                content = content,
                requireTitle = false,
            )
            if (!validation.lengthSatisfied || !validation.noBadWordsSatisfied) {
                return@withLock ReviewSubmitOutcome.ValidationFailed(validation)
            }
            if (!validation.cooldownReady) {
                return@withLock ReviewSubmitOutcome.PostLockedOrCooldown(validation.cooldownRemainingSeconds)
            }

            val cleanContent = content.trim()
            var postResult = catalogGateway.postReviewReply(
                rid = rid,
                content = cleanContent,
            )
            if (postResult is WenkuApiResult.ServerErrorCode &&
                postResult.numericCode == SERVER_CODE_NOT_LOGGED_IN &&
                userSessionRepository != null &&
                userSessionRepository.reauthenticateFromSavedCredentialsIfPossible()
            ) {
                postResult = catalogGateway.postReviewReply(
                    rid = rid,
                    content = cleanContent,
                )
            }

            val outcome = mapMutationResultAndHandleDraft(
                aid = aid,
                rid = rid,
                novelTitleHint = novelTitleHint,
                result = postResult,
            )
            org.mewx.wenku8.util.AnalyticsTracker.logReviewReply(
                aid = aid,
                success = outcome is ReviewSubmitOutcome.Success,
            )
            outcome
        }
    }

    suspend fun loadDraft(aid: Int, rid: Int = NEW_THREAD_DRAFT_RID): ReviewDraftEntity? =
        withContext(ioDispatcher) {
            if (aid <= 0) return@withContext null
            database.reviewDraftDao().getDraft(aid = aid, rid = rid)
        }

    /**
     * Auto-saves or clears a draft in `review_drafts(aid, rid)`.
     *
     * Because `review_drafts` declares `FOREIGN KEY(aid) REFERENCES novels(aid) ON DELETE CASCADE`,
     * opening reviews for a discovery novel that has not yet been persisted in `novels` must insert
     * a minimal placeholder [NovelEntity] inside the same Room transaction first.
     */
    suspend fun saveOrClearDraft(
        aid: Int,
        novelTitleHint: String = "",
        rid: Int = NEW_THREAD_DRAFT_RID,
        draftTitle: String = "",
        draftContent: String,
    ) = withContext(ioDispatcher) {
        if (aid <= 0) return@withContext
        val hasTitle = draftTitle.isNotBlank()
        val hasContent = draftContent.isNotBlank()
        if (!hasTitle && !hasContent) {
            database.reviewDraftDao().deleteDraft(aid = aid, rid = rid)
            return@withContext
        }

        database.withTransaction {
            val existingNovel = database.novelDao().getNovelByAid(aid)
            if (existingNovel == null) {
                database.novelDao().upsertNovel(
                    NovelEntity(
                        aid = aid,
                        title = novelTitleHint.trim().ifEmpty { "AID $aid" },
                    ),
                )
            }
            database.reviewDraftDao().upsertDraft(
                ReviewDraftEntity(
                    aid = aid,
                    rid = rid,
                    draftTitle = draftTitle,
                    draftContent = draftContent,
                    updatedAtMs = wallClockMs(),
                ),
            )
        }
    }

    suspend fun clearDraft(aid: Int, rid: Int = NEW_THREAD_DRAFT_RID) = withContext(ioDispatcher) {
        if (aid <= 0) return@withContext
        database.reviewDraftDao().deleteDraft(aid = aid, rid = rid)
    }

    private suspend fun isAccountReadyForPosting(): Boolean {
        val repo = userSessionRepository ?: return true
        return when (repo.sessionState.value) {
            is AccountSessionState.SignedIn -> true
            is AccountSessionState.OfflineWithSavedAccount,
            is AccountSessionState.RestoringSession,
            -> repo.reauthenticateFromSavedCredentialsIfPossible()
            AccountSessionState.SignedOut,
            AccountSessionState.UnavailableStub,
            -> false
        }
    }

    private suspend fun mapMutationResultAndHandleDraft(
        aid: Int,
        rid: Int,
        novelTitleHint: String,
        result: WenkuApiResult<Unit>,
    ): ReviewSubmitOutcome {
        return when (result) {
            is WenkuApiResult.Success -> {
                _cooldownUntilMonotonicMs.value = monotonicClockMs() + postCooldownMs
                if (aid > 0) {
                    database.reviewDraftDao().deleteDraft(aid = aid, rid = rid)
                }
                ReviewSubmitOutcome.Success
            }

            WenkuApiResult.ApiNotConfigured -> ReviewSubmitOutcome.ApiNotConfigured

            is WenkuApiResult.UpgradeRequired -> {
                ReviewSubmitOutcome.NetworkOrServerError(debugDetail = "upgrade_required")
            }

            WenkuApiResult.AuthRejected -> {
                ReviewSubmitOutcome.NetworkOrServerError(debugDetail = "auth_rejected")
            }

            is WenkuApiResult.ServerErrorCode -> when (result.numericCode) {
                SERVER_CODE_NOT_LOGGED_IN -> ReviewSubmitOutcome.NotLoggedIn
                SERVER_CODE_POST_LOCKED_OR_COOLDOWN -> {
                    _cooldownUntilMonotonicMs.value = monotonicClockMs() + postCooldownMs
                    ReviewSubmitOutcome.PostLockedOrCooldown(remainingCooldownSeconds())
                }
                else -> ReviewSubmitOutcome.NetworkOrServerError(numericCode = result.numericCode)
            }

            is WenkuApiResult.NetworkError -> {
                ReviewSubmitOutcome.NetworkOrServerError(debugDetail = result.debugDetail)
            }

            WenkuApiResult.EmptyResponse -> {
                ReviewSubmitOutcome.NetworkOrServerError(debugDetail = "empty_response")
            }

            is WenkuApiResult.ParseError -> {
                ReviewSubmitOutcome.NetworkOrServerError(debugDetail = result.rawPreview)
            }
        }
    }
}
