package org.mewx.wenku8.data.remote.api

import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.data.remote.parser.ParsedCloudBookshelfEntry
import org.mewx.wenku8.data.remote.parser.ParsedNovelFullMeta
import org.mewx.wenku8.data.remote.parser.ParsedNovelListItem
import org.mewx.wenku8.data.remote.parser.ParsedNovelListPage
import org.mewx.wenku8.data.remote.parser.ParsedReviewReplyPage
import org.mewx.wenku8.data.remote.parser.ParsedReviewThreadPage
import org.mewx.wenku8.data.remote.parser.WenkuXmlParsers
import org.mewx.wenku8.data.remote.transport.RelayPostResult
import org.mewx.wenku8.data.remote.transport.WenkuTransport
import org.mewx.wenku8.data.repository.ParsedVolumeToc
import java.nio.charset.StandardCharsets

/**
 * Typed outcome for every Wenku8 `:api` request so callers distinguish unconfigured `:api-stub`
 * builds, server-enforced upgrades (`426`), auth rejections (`403`), transport failures, numeric
 * upstream error codes, and malformed payloads without throwing.
 */
sealed interface WenkuApiResult<out T> {
    data class Success<T>(val data: T) : WenkuApiResult<T>

    /** Returned on `-DforceApiStub` builds (or host JVM without Android JNI) where `:api` is unconfigured. */
    data object ApiNotConfigured : WenkuApiResult<Nothing>

    /** Returned when the server responds with HTTP `426 Upgrade Required` (`SCHEME_RETIRED` / `VERSION_TOO_OLD`). */
    data class UpgradeRequired(
        val minVersionCode: Int? = null,
        val minVersionName: String? = null,
    ) : WenkuApiResult<Nothing>

    /** Returned when the server rejects the sealed request with HTTP `403` (or non-clock-skew `401`). */
    data object AuthRejected : WenkuApiResult<Nothing>

    /** Returned when the transport could not reach the server, hit rate limits, or timed out. */
    data class NetworkError(val debugDetail: String? = null) : WenkuApiResult<Nothing>

    /**
     * Returned when Wenku8 responds with a short numeric status code (e.g. `0` request error /
     * search rate-limit, `4` not logged in, `11` post cooldown).
     */
    data class ServerErrorCode(val numericCode: Int) : WenkuApiResult<Nothing>

    /**
     * Returned when HTTP 200 succeeded with an empty/blank body (or literal `"null"` for
     * `do=text`/`do=intro`), e.g. when Wenku8's upstream API fails to output a chapter's body.
     */
    data object EmptyResponse : WenkuApiResult<Nothing>

    /** Returned when HTTP 200 returned unexpected HTML/XML that failed validation. */
    data class ParseError(val rawPreview: String = "") : WenkuApiResult<Nothing>
}

enum class OnlineSearchField {
    NOVEL_TITLE,
    AUTHOR_NAME,
}

/**
 * Abstracts static `Wenku8API` raw query generation so host-JVM unit tests can exercise every
 * branch of [WenkuApiClient] without loading Android Bionic `libmewx.so`, while production code
 * delegates 100% to `:api`'s [Wenku8API].
 */
interface WenkuParamsFactory {
    fun baseUrlOrUnknown(): String
    fun resolveCoverUrl(aid: Int): String
    fun resolveAvatarUrl(uid: Int): String = ""
    fun findBadWord(text: String): String? = null
    fun minReplyTextLength(): Int = 7
    fun novelListWithInfoQuery(sortedBy: Wenku8API.NovelSortedBy, page: Int, lang: Wenku8API.AppLanguage): String?
    fun searchByNovelNameQuery(query: String, lang: Wenku8API.AppLanguage): String?
    fun searchByAuthorNameQuery(query: String, lang: Wenku8API.AppLanguage): String?
    fun novelShortInfoQuery(aid: Int, lang: Wenku8API.AppLanguage): String?
    fun novelFullMetaQuery(aid: Int, lang: Wenku8API.AppLanguage): String?
    fun novelFullIntroQuery(aid: Int, lang: Wenku8API.AppLanguage): String?
    fun novelIndexQuery(aid: Int, lang: Wenku8API.AppLanguage): String?
    fun novelContentQuery(aid: Int, cid: Int, lang: Wenku8API.AppLanguage): String?
    fun bookshelfListQuery(lang: Wenku8API.AppLanguage): String?
    fun bookshelfListAidQuery(lang: Wenku8API.AppLanguage): String? = null
    fun bookshelfAddQuery(aid: Int): String? = null
    fun bookshelfDelQuery(aid: Int): String? = null
    fun commentListQuery(aid: Int, page: Int, lang: Wenku8API.AppLanguage): String? = null
    fun commentContentQuery(rid: Int, page: Int, lang: Wenku8API.AppLanguage): String? = null
    fun commentNewThreadQuery(aid: Int, title: String, content: String): String? = null
    fun commentReplyQuery(rid: Int, content: String): String? = null
}

object NativeWenkuParamsFactory : WenkuParamsFactory {
    override fun baseUrlOrUnknown(): String = safeCall(WenkuTransport.UNKNOWN_ENDPOINT_SENTINEL) {
        Wenku8API.BASE_URL.orEmpty().ifBlank { WenkuTransport.UNKNOWN_ENDPOINT_SENTINEL }
    }

    override fun resolveCoverUrl(aid: Int): String = safeCall("") {
        Wenku8API.getCoverURL(aid).orEmpty()
    }

    override fun resolveAvatarUrl(uid: Int): String = safeCall("") {
        if (uid <= 0) "" else Wenku8API.getAvatarURL(uid).orEmpty()
    }

    override fun findBadWord(text: String): String? = safeCall(null) {
        Wenku8API.searchBadWords(text)
    }

    override fun minReplyTextLength(): Int = safeCall(7) {
        Wenku8API.MIN_REPLY_TEXT.takeIf { it > 0 } ?: 7
    }

    override fun novelListWithInfoQuery(
        sortedBy: Wenku8API.NovelSortedBy,
        page: Int,
        lang: Wenku8API.AppLanguage,
    ): String? = safeCall(null) {
        Wenku8API.buildNovelListWithInfoQuery(sortedBy, page, lang)
    }

    override fun searchByNovelNameQuery(query: String, lang: Wenku8API.AppLanguage): String? =
        safeCall(null) { Wenku8API.buildSearchNovelByNameQuery(query, lang) }

    override fun searchByAuthorNameQuery(query: String, lang: Wenku8API.AppLanguage): String? =
        safeCall(null) { Wenku8API.buildSearchNovelByAuthorQuery(query, lang) }

    override fun novelShortInfoQuery(aid: Int, lang: Wenku8API.AppLanguage): String? =
        safeCall(null) { Wenku8API.buildNovelShortInfoUpdateQuery(aid, lang) }

    override fun novelFullMetaQuery(aid: Int, lang: Wenku8API.AppLanguage): String? =
        safeCall(null) { Wenku8API.buildNovelFullMetaQuery(aid, lang) }

    override fun novelFullIntroQuery(aid: Int, lang: Wenku8API.AppLanguage): String? =
        safeCall(null) { Wenku8API.buildNovelFullIntroQuery(aid, lang) }

    override fun novelIndexQuery(aid: Int, lang: Wenku8API.AppLanguage): String? =
        safeCall(null) { Wenku8API.buildNovelIndexQuery(aid, lang) }

    override fun novelContentQuery(aid: Int, cid: Int, lang: Wenku8API.AppLanguage): String? =
        safeCall(null) { Wenku8API.buildNovelContentQuery(aid, cid, lang) }

    override fun bookshelfListQuery(lang: Wenku8API.AppLanguage): String? =
        safeCall(null) { Wenku8API.buildBookshelfListQuery(lang) }

    override fun bookshelfListAidQuery(lang: Wenku8API.AppLanguage): String? =
        safeCall(null) { Wenku8API.buildBookshelfListAidQuery(lang) }

    override fun bookshelfAddQuery(aid: Int): String? =
        safeCall(null) { Wenku8API.buildAddToBookshelfQuery(aid) }

    override fun bookshelfDelQuery(aid: Int): String? =
        safeCall(null) { Wenku8API.buildDelFromBookshelfQuery(aid) }

    override fun commentListQuery(aid: Int, page: Int, lang: Wenku8API.AppLanguage): String? =
        safeCall(null) { Wenku8API.buildCommentListQuery(aid, page, lang) }

    override fun commentContentQuery(rid: Int, page: Int, lang: Wenku8API.AppLanguage): String? =
        safeCall(null) { Wenku8API.buildCommentContentQuery(rid, page, lang) }

    override fun commentNewThreadQuery(aid: Int, title: String, content: String): String? =
        safeCall(null) { Wenku8API.buildCommentNewThreadQuery(aid, title, content) }

    override fun commentReplyQuery(rid: Int, content: String): String? =
        safeCall(null) { Wenku8API.buildCommentReplyQuery(rid, content) }

    private inline fun <T> safeCall(fallback: T, block: () -> T): T {
        return try {
            block()
        } catch (_: UnsupportedOperationException) {
            fallback
        } catch (_: LinkageError) {
            fallback
        }
    }
}

/**
 * Gateway contract consumed by repositories so unit tests run identically against both the private
 * `:api` submodule and `-DforceApiStub` CI builds.
 */
interface WenkuCatalogGateway {
    val isApiConfigured: Boolean
    val minReplyTextLength: Int
        get() = 7

    fun resolveCoverUrl(aid: Int): String
    fun resolveAvatarUrl(uid: Int): String = ""
    fun findBadWord(text: String): String? = null

    suspend fun fetchNovelListPage(
        sortedBy: Wenku8API.NovelSortedBy,
        pageNumber: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<ParsedNovelListPage>

    suspend fun searchNovelsByField(
        query: String,
        searchField: OnlineSearchField,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<List<Int>>

    suspend fun fetchNovelShortInfo(
        aid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<ParsedNovelListItem>

    suspend fun fetchNovelFullMeta(
        aid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<ParsedNovelFullMeta>

    suspend fun fetchNovelFullIntro(
        aid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<String>

    suspend fun fetchNovelVolumeToc(
        aid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<List<ParsedVolumeToc>>

    suspend fun fetchChapterContent(
        aid: Int,
        cid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<String>

    suspend fun fetchCloudBookshelf(
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<List<ParsedCloudBookshelfEntry>>

    suspend fun fetchCloudBookshelfAidList(
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<List<Int>> = WenkuApiResult.ApiNotConfigured

    suspend fun addToCloudBookshelf(
        aid: Int,
    ): WenkuApiResult<Unit> = WenkuApiResult.ApiNotConfigured

    suspend fun removeFromCloudBookshelf(
        aid: Int,
    ): WenkuApiResult<Unit> = WenkuApiResult.ApiNotConfigured

    suspend fun fetchReviewThreadListPage(
        aid: Int,
        pageNumber: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<ParsedReviewThreadPage> = WenkuApiResult.ApiNotConfigured

    suspend fun fetchReviewReplyPage(
        rid: Int,
        pageNumber: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<ParsedReviewReplyPage> = WenkuApiResult.ApiNotConfigured

    suspend fun postNewReviewThread(
        aid: Int,
        title: String,
        content: String,
    ): WenkuApiResult<Unit> = WenkuApiResult.ApiNotConfigured

    suspend fun postReviewReply(
        rid: Int,
        content: String,
    ): WenkuApiResult<Unit> = WenkuApiResult.ApiNotConfigured

    /**
     * Fetches raw binary image bytes (novel covers or inline chapter illustrations) via the active
     * [WenkuTransport] so offline downloads save permanent files under `filesDir/saves/imgs/`.
     */
    suspend fun downloadBinaryBytes(url: String): ByteArray? = null
}

/**
 * Stub-safe and host-JVM-safe Kotlin coroutine gateway around `:api`'s `WK8-RNE-1` sealed protocol.
 */
class WenkuApiClient(
    private val transport: WenkuTransport,
    private val paramsFactory: WenkuParamsFactory = NativeWenkuParamsFactory,
) : WenkuCatalogGateway {

    override val isApiConfigured: Boolean
        get() {
            val url = paramsFactory.baseUrlOrUnknown()
            return transport.isEndpointConfigured &&
                url.isNotBlank() &&
                url != WenkuTransport.UNKNOWN_ENDPOINT_SENTINEL
        }

    override val minReplyTextLength: Int
        get() = paramsFactory.minReplyTextLength()

    override fun resolveCoverUrl(aid: Int): String {
        if (aid <= 0) return ""
        return paramsFactory.resolveCoverUrl(aid)
    }

    override fun resolveAvatarUrl(uid: Int): String {
        if (uid <= 0) return ""
        return paramsFactory.resolveAvatarUrl(uid)
    }

    override fun findBadWord(text: String): String? = paramsFactory.findBadWord(text)

    override suspend fun downloadBinaryBytes(url: String): ByteArray? {
        val cleanUrl = url.trim()
        if (cleanUrl.isEmpty()) return null
        return transport.getBytes(cleanUrl)
    }

    override suspend fun fetchNovelListPage(
        sortedBy: Wenku8API.NovelSortedBy,
        pageNumber: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<ParsedNovelListPage> {
        if (!isApiConfigured) return WenkuApiResult.ApiNotConfigured
        val safePage = pageNumber.coerceAtLeast(1)
        val rawAction = paramsFactory.novelListWithInfoQuery(sortedBy, safePage, appLanguage)
            ?: return WenkuApiResult.ApiNotConfigured

        return executeXmlRequest(rawAction) { rawXml ->
            WenkuXmlParsers.parseNovelListWithInfo(rawXml)
        }
    }

    override suspend fun searchNovelsByField(
        query: String,
        searchField: OnlineSearchField,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<List<Int>> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isEmpty()) {
            return WenkuApiResult.Success(emptyList())
        }
        if (!isApiConfigured) return WenkuApiResult.ApiNotConfigured

        val rawAction = when (searchField) {
            OnlineSearchField.NOVEL_TITLE -> paramsFactory.searchByNovelNameQuery(trimmedQuery, appLanguage)
            OnlineSearchField.AUTHOR_NAME -> paramsFactory.searchByAuthorNameQuery(trimmedQuery, appLanguage)
        } ?: return WenkuApiResult.ApiNotConfigured

        return executeXmlRequest(rawAction, isIdempotentReadOnly = false) { rawXml ->
            WenkuXmlParsers.parseSearchResultAidList(rawXml)
        }
    }

    override suspend fun fetchNovelShortInfo(
        aid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<ParsedNovelListItem> {
        if (!isApiConfigured) return WenkuApiResult.ApiNotConfigured
        val rawAction = paramsFactory.novelShortInfoQuery(aid, appLanguage)
            ?: return WenkuApiResult.ApiNotConfigured

        return executeXmlRequest(rawAction, isIdempotentReadOnly = true) { rawXml ->
            WenkuXmlParsers.parseNovelShortInfoUpdate(rawXml)
        }
    }

    override suspend fun fetchNovelFullMeta(
        aid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<ParsedNovelFullMeta> {
        if (!isApiConfigured) return WenkuApiResult.ApiNotConfigured
        val rawAction = paramsFactory.novelFullMetaQuery(aid, appLanguage)
            ?: return WenkuApiResult.ApiNotConfigured

        return executeXmlRequest(rawAction, isIdempotentReadOnly = true) { rawXml ->
            WenkuXmlParsers.parseNovelFullMeta(rawXml)
        }
    }

    override suspend fun fetchNovelFullIntro(
        aid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<String> {
        if (!isApiConfigured) return WenkuApiResult.ApiNotConfigured
        val rawAction = paramsFactory.novelFullIntroQuery(aid, appLanguage)
            ?: return WenkuApiResult.ApiNotConfigured

        return executeRawTextRequest(
            rawActionQuery = rawAction,
            isIdempotentReadOnly = true,
            rejectNullLiteral = true,
        )
    }

    override suspend fun fetchNovelVolumeToc(
        aid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<List<ParsedVolumeToc>> {
        if (!isApiConfigured) return WenkuApiResult.ApiNotConfigured
        val rawAction = paramsFactory.novelIndexQuery(aid, appLanguage)
            ?: return WenkuApiResult.ApiNotConfigured

        return executeXmlRequest(rawAction, isIdempotentReadOnly = true) { rawXml ->
            WenkuXmlParsers.parseVolumeTocList(rawXml).takeIf { it.isNotEmpty() }
        }
    }

    override suspend fun fetchChapterContent(
        aid: Int,
        cid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<String> {
        if (!isApiConfigured) return WenkuApiResult.ApiNotConfigured
        val rawAction = paramsFactory.novelContentQuery(aid, cid, appLanguage)
            ?: return WenkuApiResult.ApiNotConfigured

        return executeRawTextRequest(
            rawActionQuery = rawAction,
            isIdempotentReadOnly = true,
            rejectNullLiteral = true,
        )
    }

    override suspend fun fetchCloudBookshelf(
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<List<ParsedCloudBookshelfEntry>> {
        if (!isApiConfigured) return WenkuApiResult.ApiNotConfigured
        val rawAction = paramsFactory.bookshelfListQuery(appLanguage)
            ?: return WenkuApiResult.ApiNotConfigured

        val richResult = executeXmlRequest(rawAction, isIdempotentReadOnly = true) { rawXml ->
            WenkuXmlParsers.parseCloudBookshelfList(rawXml)
        }
        if (richResult !is WenkuApiResult.ParseError) {
            return richResult
        }

        // Fallback to compact `action=bookcase&do=list` if an unescaped character in a book
        // title/chapter broke rich `action=bookcase` XML parsing.
        return when (val aidResult = fetchCloudBookshelfAidList(appLanguage)) {
            is WenkuApiResult.Success -> WenkuApiResult.Success(
                aidResult.data.map { aid -> ParsedCloudBookshelfEntry(aid = aid) },
            )
            WenkuApiResult.ApiNotConfigured -> richResult
            is WenkuApiResult.UpgradeRequired -> aidResult
            WenkuApiResult.AuthRejected -> WenkuApiResult.AuthRejected
            is WenkuApiResult.NetworkError -> aidResult
            is WenkuApiResult.ServerErrorCode -> aidResult
            WenkuApiResult.EmptyResponse -> richResult
            is WenkuApiResult.ParseError -> richResult
        }
    }

    override suspend fun fetchCloudBookshelfAidList(
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<List<Int>> {
        if (!isApiConfigured) return WenkuApiResult.ApiNotConfigured
        val rawAction = paramsFactory.bookshelfListAidQuery(appLanguage)
            ?: return WenkuApiResult.ApiNotConfigured

        return executeXmlRequest(rawAction, isIdempotentReadOnly = true) { rawXml ->
            WenkuXmlParsers.parseCloudBookshelfAidList(rawXml)
        }
    }

    override suspend fun addToCloudBookshelf(aid: Int): WenkuApiResult<Unit> {
        if (!isApiConfigured) return WenkuApiResult.ApiNotConfigured
        val rawAction = paramsFactory.bookshelfAddQuery(aid)
            ?: return WenkuApiResult.ApiNotConfigured
        return executeNumericMutationRequest(rawAction)
    }

    override suspend fun removeFromCloudBookshelf(aid: Int): WenkuApiResult<Unit> {
        if (!isApiConfigured) return WenkuApiResult.ApiNotConfigured
        val rawAction = paramsFactory.bookshelfDelQuery(aid)
            ?: return WenkuApiResult.ApiNotConfigured
        return executeNumericMutationRequest(rawAction)
    }

    override suspend fun fetchReviewThreadListPage(
        aid: Int,
        pageNumber: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<ParsedReviewThreadPage> {
        if (!isApiConfigured) return WenkuApiResult.ApiNotConfigured
        val rawAction = paramsFactory.commentListQuery(aid, pageNumber.coerceAtLeast(1), appLanguage)
            ?: return WenkuApiResult.ApiNotConfigured

        return executeXmlRequest(rawAction, isIdempotentReadOnly = true) { rawXml ->
            WenkuXmlParsers.parseReviewThreadListPage(rawXml)
        }
    }

    override suspend fun fetchReviewReplyPage(
        rid: Int,
        pageNumber: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<ParsedReviewReplyPage> {
        if (!isApiConfigured) return WenkuApiResult.ApiNotConfigured
        val rawAction = paramsFactory.commentContentQuery(rid, pageNumber.coerceAtLeast(1), appLanguage)
            ?: return WenkuApiResult.ApiNotConfigured

        return executeXmlRequest(rawAction, isIdempotentReadOnly = true) { rawXml ->
            WenkuXmlParsers.parseReviewReplyPage(rawXml)
        }
    }

    override suspend fun postNewReviewThread(
        aid: Int,
        title: String,
        content: String,
    ): WenkuApiResult<Unit> {
        if (!isApiConfigured) return WenkuApiResult.ApiNotConfigured
        val rawAction = paramsFactory.commentNewThreadQuery(aid, title, content)
            ?: return WenkuApiResult.ApiNotConfigured
        return executeNumericMutationRequest(rawAction)
    }

    override suspend fun postReviewReply(
        rid: Int,
        content: String,
    ): WenkuApiResult<Unit> {
        if (!isApiConfigured) return WenkuApiResult.ApiNotConfigured
        val rawAction = paramsFactory.commentReplyQuery(rid, content)
            ?: return WenkuApiResult.ApiNotConfigured
        return executeNumericMutationRequest(rawAction)
    }

    private suspend fun executeNumericMutationRequest(rawActionQuery: String): WenkuApiResult<Unit> {
        if (!isApiConfigured) return WenkuApiResult.ApiNotConfigured

        val responseBytes = when (
            val postResult = transport.postSealedAction(
                rawActionQuery = rawActionQuery,
                isIdempotentReadOnly = false,
            )
        ) {
            is RelayPostResult.Success -> postResult.bodyBytes
            is RelayPostResult.UpgradeRequired -> return WenkuApiResult.UpgradeRequired(
                minVersionCode = postResult.minVersionCode,
                minVersionName = postResult.minVersionName,
            )
            RelayPostResult.AuthRejected -> return WenkuApiResult.AuthRejected
            RelayPostResult.RateLimited -> return WenkuApiResult.NetworkError("rate_limited")
            is RelayPostResult.NetworkFailure -> return WenkuApiResult.NetworkError(postResult.debugDetail)
        }

        val trimmed = String(responseBytes, StandardCharsets.UTF_8).trim()
        val code = trimmed.toIntOrNull() ?: return WenkuApiResult.ParseError(trimmed.take(120))
        return if (code == 1) {
            WenkuApiResult.Success(Unit)
        } else {
            WenkuApiResult.ServerErrorCode(code)
        }
    }

    private suspend fun <T : Any> executeXmlRequest(
        rawActionQuery: String,
        isIdempotentReadOnly: Boolean = true,
        parserBlock: (String) -> T?,
    ): WenkuApiResult<T> {
        if (!isApiConfigured) return WenkuApiResult.ApiNotConfigured

        val responseBytes = when (
            val postResult = transport.postSealedAction(
                rawActionQuery = rawActionQuery,
                isIdempotentReadOnly = isIdempotentReadOnly,
            )
        ) {
            is RelayPostResult.Success -> postResult.bodyBytes
            is RelayPostResult.UpgradeRequired -> return WenkuApiResult.UpgradeRequired(
                minVersionCode = postResult.minVersionCode,
                minVersionName = postResult.minVersionName,
            )
            RelayPostResult.AuthRejected -> return WenkuApiResult.AuthRejected
            RelayPostResult.RateLimited -> return WenkuApiResult.NetworkError("rate_limited")
            is RelayPostResult.NetworkFailure -> return WenkuApiResult.NetworkError(postResult.debugDetail)
        }

        val rawString = String(responseBytes, StandardCharsets.UTF_8).trim()
        if (rawString.isEmpty()) {
            return WenkuApiResult.ParseError("")
        }

        val shortNumericError = rawString.takeIf { it.length <= 4 }?.toIntOrNull()
        if (shortNumericError != null) {
            return WenkuApiResult.ServerErrorCode(shortNumericError)
        }

        val parsed = parserBlock(rawString)
        return if (parsed != null) {
            WenkuApiResult.Success(parsed)
        } else {
            WenkuApiResult.ParseError(rawString.take(120))
        }
    }

    private suspend fun executeRawTextRequest(
        rawActionQuery: String,
        isIdempotentReadOnly: Boolean = true,
        rejectNullLiteral: Boolean = false,
    ): WenkuApiResult<String> {
        if (!isApiConfigured) return WenkuApiResult.ApiNotConfigured

        val responseBytes = when (
            val postResult = transport.postSealedAction(
                rawActionQuery = rawActionQuery,
                isIdempotentReadOnly = isIdempotentReadOnly,
            )
        ) {
            is RelayPostResult.Success -> postResult.bodyBytes
            is RelayPostResult.UpgradeRequired -> return WenkuApiResult.UpgradeRequired(
                minVersionCode = postResult.minVersionCode,
                minVersionName = postResult.minVersionName,
            )
            RelayPostResult.AuthRejected -> return WenkuApiResult.AuthRejected
            RelayPostResult.RateLimited -> return WenkuApiResult.NetworkError("rate_limited")
            is RelayPostResult.NetworkFailure -> return WenkuApiResult.NetworkError(postResult.debugDetail)
        }

        val rawText = String(responseBytes, StandardCharsets.UTF_8)
        val trimmed = rawText.trim { it.isWhitespace() || it == '\uFEFF' }
        if (trimmed.isEmpty()) {
            return WenkuApiResult.EmptyResponse
        }
        if (rejectNullLiteral && trimmed.equals("null", ignoreCase = true)) {
            return WenkuApiResult.EmptyResponse
        }

        val shortNumericError = trimmed.takeIf { it.length <= 4 }?.toIntOrNull()
        if (shortNumericError != null) {
            return WenkuApiResult.ServerErrorCode(shortNumericError)
        }

        if (trimmed.startsWith("<!doctype html", ignoreCase = true) ||
            trimmed.startsWith("<html", ignoreCase = true)
        ) {
            return WenkuApiResult.ParseError(trimmed.take(120))
        }

        return WenkuApiResult.Success(rawText)
    }
}

internal fun String?.isEffectivelyBlankWenkuText(): Boolean =
    this == null || trim { it.isWhitespace() || it == '\uFEFF' }.isEmpty()
