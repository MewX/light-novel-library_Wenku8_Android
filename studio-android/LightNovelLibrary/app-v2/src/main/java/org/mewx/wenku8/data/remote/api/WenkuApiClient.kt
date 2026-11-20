package org.mewx.wenku8.data.remote.api

import android.content.ContentValues
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.data.remote.parser.ParsedCloudBookshelfEntry
import org.mewx.wenku8.data.remote.parser.ParsedNovelFullMeta
import org.mewx.wenku8.data.remote.parser.ParsedNovelListItem
import org.mewx.wenku8.data.remote.parser.ParsedNovelListPage
import org.mewx.wenku8.data.remote.parser.ParsedReviewReplyPage
import org.mewx.wenku8.data.remote.parser.ParsedReviewThreadPage
import org.mewx.wenku8.data.remote.parser.WenkuXmlParsers
import org.mewx.wenku8.data.remote.transport.LegacyLightNetworkTransport
import org.mewx.wenku8.data.remote.transport.WenkuTransport
import org.mewx.wenku8.data.repository.ParsedVolumeToc
import java.nio.charset.StandardCharsets

/**
 * Typed outcome for every Wenku8 `:api` request so callers distinguish unconfigured `:api-stub`
 * builds, transport failures, numeric server error codes, and malformed payloads without throwing.
 */
sealed interface WenkuApiResult<out T> {
    data class Success<T>(val data: T) : WenkuApiResult<T>

    /** Returned on `-DforceApiStub` builds (or host JVM without Android JNI) where `:api` is unconfigured. */
    data object ApiNotConfigured : WenkuApiResult<Nothing>

    /** Returned when the transport could not reach the server or timed out. */
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
 * Abstracts static `Wenku8API` parameter generation so host-JVM unit tests can exercise every
 * branch of [WenkuApiClient] without loading Android Bionic `libcpp.so`, while production code
 * delegates 100% to `:api`'s [Wenku8API].
 */
interface WenkuParamsFactory {
    fun baseUrlOrUnknown(): String
    fun resolveCoverUrl(aid: Int): String
    fun resolveAvatarUrl(uid: Int): String = ""
    fun findBadWord(text: String): String? = null
    fun minReplyTextLength(): Int = 7
    fun novelListWithInfo(sortedBy: Wenku8API.NovelSortedBy, page: Int, lang: Wenku8API.AppLanguage): ContentValues?
    fun searchByNovelName(query: String, lang: Wenku8API.AppLanguage): ContentValues?
    fun searchByAuthorName(query: String, lang: Wenku8API.AppLanguage): ContentValues?
    fun novelShortInfo(aid: Int, lang: Wenku8API.AppLanguage): ContentValues?
    fun novelFullMeta(aid: Int, lang: Wenku8API.AppLanguage): ContentValues?
    fun novelFullIntro(aid: Int, lang: Wenku8API.AppLanguage): ContentValues?
    fun novelIndex(aid: Int, lang: Wenku8API.AppLanguage): ContentValues?
    fun novelContent(aid: Int, cid: Int, lang: Wenku8API.AppLanguage): ContentValues?
    fun bookshelfListParams(lang: Wenku8API.AppLanguage): ContentValues?
    fun bookshelfListAidParams(lang: Wenku8API.AppLanguage): ContentValues? = null
    fun bookshelfAddParams(aid: Int): ContentValues? = null
    fun bookshelfDelParams(aid: Int): ContentValues? = null
    fun commentListParams(aid: Int, page: Int, lang: Wenku8API.AppLanguage): ContentValues? = null
    fun commentContentParams(rid: Int, page: Int, lang: Wenku8API.AppLanguage): ContentValues? = null
    fun commentNewThreadParams(aid: Int, title: String, content: String): ContentValues? = null
    fun commentReplyParams(rid: Int, content: String): ContentValues? = null
}

object NativeWenkuParamsFactory : WenkuParamsFactory {
    override fun baseUrlOrUnknown(): String = safeCall(LegacyLightNetworkTransport.UNKNOWN_ENDPOINT_SENTINEL) {
        Wenku8API.BASE_URL.orEmpty().ifBlank { LegacyLightNetworkTransport.UNKNOWN_ENDPOINT_SENTINEL }
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

    override fun novelListWithInfo(
        sortedBy: Wenku8API.NovelSortedBy,
        page: Int,
        lang: Wenku8API.AppLanguage,
    ): ContentValues? = safeCall(null) {
        Wenku8API.getNovelListWithInfo(sortedBy, page, lang)
    }

    override fun searchByNovelName(query: String, lang: Wenku8API.AppLanguage): ContentValues? =
        safeCall(null) { Wenku8API.searchNovelByNovelName(query, lang) }

    override fun searchByAuthorName(query: String, lang: Wenku8API.AppLanguage): ContentValues? =
        safeCall(null) { Wenku8API.searchNovelByAuthorName(query, lang) }

    override fun novelShortInfo(aid: Int, lang: Wenku8API.AppLanguage): ContentValues? =
        safeCall(null) { Wenku8API.getNovelShortInfoUpdate_CV(aid, lang) }

    override fun novelFullMeta(aid: Int, lang: Wenku8API.AppLanguage): ContentValues? =
        safeCall(null) { Wenku8API.getNovelFullMeta(aid, lang) }

    override fun novelFullIntro(aid: Int, lang: Wenku8API.AppLanguage): ContentValues? =
        safeCall(null) { Wenku8API.getNovelFullIntro(aid, lang) }

    override fun novelIndex(aid: Int, lang: Wenku8API.AppLanguage): ContentValues? =
        safeCall(null) { Wenku8API.getNovelIndex(aid, lang) }

    override fun novelContent(aid: Int, cid: Int, lang: Wenku8API.AppLanguage): ContentValues? =
        safeCall(null) { Wenku8API.getNovelContent(aid, cid, lang) }

    override fun bookshelfListParams(lang: Wenku8API.AppLanguage): ContentValues? =
        safeCall(null) { Wenku8API.getBookshelfListParams(lang) }

    override fun bookshelfListAidParams(lang: Wenku8API.AppLanguage): ContentValues? =
        safeCall(null) { Wenku8API.getBookshelfListAid(lang) }

    override fun bookshelfAddParams(aid: Int): ContentValues? =
        safeCall(null) { Wenku8API.getAddToBookshelfParams(aid) }

    override fun bookshelfDelParams(aid: Int): ContentValues? =
        safeCall(null) { Wenku8API.getDelFromBookshelfParams(aid) }

    override fun commentListParams(aid: Int, page: Int, lang: Wenku8API.AppLanguage): ContentValues? =
        safeCall(null) {
            Wenku8API.CurrentLang = lang
            Wenku8API.getCommentListParams(aid, page)
        }

    override fun commentContentParams(rid: Int, page: Int, lang: Wenku8API.AppLanguage): ContentValues? =
        safeCall(null) {
            Wenku8API.CurrentLang = lang
            Wenku8API.getCommentContentParams(rid, page)
        }

    override fun commentNewThreadParams(aid: Int, title: String, content: String): ContentValues? =
        safeCall(null) { Wenku8API.getCommentNewThreadParams(aid, title, content) }

    override fun commentReplyParams(rid: Int, content: String): ContentValues? =
        safeCall(null) { Wenku8API.getCommentReplyParams(rid, content) }

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
 * Stub-safe and host-JVM-safe Kotlin coroutine gateway around the untouched `:api` submodule.
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
                url != LegacyLightNetworkTransport.UNKNOWN_ENDPOINT_SENTINEL
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
        val params = paramsFactory.novelListWithInfo(sortedBy, safePage, appLanguage)
            ?: return WenkuApiResult.ApiNotConfigured

        return executeXmlRequest(params) { rawXml ->
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

        val params = when (searchField) {
            OnlineSearchField.NOVEL_TITLE -> paramsFactory.searchByNovelName(trimmedQuery, appLanguage)
            OnlineSearchField.AUTHOR_NAME -> paramsFactory.searchByAuthorName(trimmedQuery, appLanguage)
        } ?: return WenkuApiResult.ApiNotConfigured

        return executeXmlRequest(params, isIdempotentReadOnly = false) { rawXml ->
            WenkuXmlParsers.parseSearchResultAidList(rawXml)
        }
    }

    override suspend fun fetchNovelShortInfo(
        aid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<ParsedNovelListItem> {
        if (!isApiConfigured) return WenkuApiResult.ApiNotConfigured
        val params = paramsFactory.novelShortInfo(aid, appLanguage)
            ?: return WenkuApiResult.ApiNotConfigured

        return executeXmlRequest(params, isIdempotentReadOnly = true) { rawXml ->
            WenkuXmlParsers.parseNovelShortInfoUpdate(rawXml)
        }
    }

    override suspend fun fetchNovelFullMeta(
        aid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<ParsedNovelFullMeta> {
        if (!isApiConfigured) return WenkuApiResult.ApiNotConfigured
        val params = paramsFactory.novelFullMeta(aid, appLanguage)
            ?: return WenkuApiResult.ApiNotConfigured

        return executeXmlRequest(params, isIdempotentReadOnly = true) { rawXml ->
            WenkuXmlParsers.parseNovelFullMeta(rawXml)
        }
    }

    override suspend fun fetchNovelFullIntro(
        aid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<String> {
        if (!isApiConfigured) return WenkuApiResult.ApiNotConfigured
        val params = paramsFactory.novelFullIntro(aid, appLanguage)
            ?: return WenkuApiResult.ApiNotConfigured

        return executeRawTextRequest(
            formParams = params,
            isIdempotentReadOnly = true,
            rejectNullLiteral = true,
        )
    }

    override suspend fun fetchNovelVolumeToc(
        aid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<List<ParsedVolumeToc>> {
        if (!isApiConfigured) return WenkuApiResult.ApiNotConfigured
        val params = paramsFactory.novelIndex(aid, appLanguage)
            ?: return WenkuApiResult.ApiNotConfigured

        return executeXmlRequest(params, isIdempotentReadOnly = true) { rawXml ->
            WenkuXmlParsers.parseVolumeTocList(rawXml).takeIf { it.isNotEmpty() }
        }
    }

    override suspend fun fetchChapterContent(
        aid: Int,
        cid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<String> {
        if (!isApiConfigured) return WenkuApiResult.ApiNotConfigured
        val params = paramsFactory.novelContent(aid, cid, appLanguage)
            ?: return WenkuApiResult.ApiNotConfigured

        return executeRawTextRequest(
            formParams = params,
            isIdempotentReadOnly = true,
            rejectNullLiteral = true,
        )
    }

    override suspend fun fetchCloudBookshelf(
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<List<ParsedCloudBookshelfEntry>> {
        if (!isApiConfigured) return WenkuApiResult.ApiNotConfigured
        val params = paramsFactory.bookshelfListParams(appLanguage)
            ?: return WenkuApiResult.ApiNotConfigured

        val richResult = executeXmlRequest(params, isIdempotentReadOnly = true) { rawXml ->
            WenkuXmlParsers.parseCloudBookshelfList(rawXml)
        }
        if (richResult !is WenkuApiResult.ParseError) {
            return richResult
        }

        // Fallback to compact `action=bookcase&do=list` (`Wenku8API.getBookshelfListAid`) if an
        // unescaped character in a book title/chapter broke rich `action=bookcase` XML parsing.
        return when (val aidResult = fetchCloudBookshelfAidList(appLanguage)) {
            is WenkuApiResult.Success -> WenkuApiResult.Success(
                aidResult.data.map { aid -> ParsedCloudBookshelfEntry(aid = aid) },
            )
            WenkuApiResult.ApiNotConfigured -> richResult
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
        val params = paramsFactory.bookshelfListAidParams(appLanguage)
            ?: return WenkuApiResult.ApiNotConfigured

        return executeXmlRequest(params, isIdempotentReadOnly = true) { rawXml ->
            WenkuXmlParsers.parseCloudBookshelfAidList(rawXml)
        }
    }

    override suspend fun addToCloudBookshelf(aid: Int): WenkuApiResult<Unit> {
        if (!isApiConfigured) return WenkuApiResult.ApiNotConfigured
        val params = paramsFactory.bookshelfAddParams(aid)
            ?: return WenkuApiResult.ApiNotConfigured
        return executeNumericMutationRequest(params)
    }

    override suspend fun removeFromCloudBookshelf(aid: Int): WenkuApiResult<Unit> {
        if (!isApiConfigured) return WenkuApiResult.ApiNotConfigured
        val params = paramsFactory.bookshelfDelParams(aid)
            ?: return WenkuApiResult.ApiNotConfigured
        return executeNumericMutationRequest(params)
    }

    override suspend fun fetchReviewThreadListPage(
        aid: Int,
        pageNumber: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<ParsedReviewThreadPage> {
        if (!isApiConfigured) return WenkuApiResult.ApiNotConfigured
        val params = paramsFactory.commentListParams(aid, pageNumber.coerceAtLeast(1), appLanguage)
            ?: return WenkuApiResult.ApiNotConfigured

        return executeXmlRequest(params, isIdempotentReadOnly = true) { rawXml ->
            WenkuXmlParsers.parseReviewThreadListPage(rawXml)
        }
    }

    override suspend fun fetchReviewReplyPage(
        rid: Int,
        pageNumber: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<ParsedReviewReplyPage> {
        if (!isApiConfigured) return WenkuApiResult.ApiNotConfigured
        val params = paramsFactory.commentContentParams(rid, pageNumber.coerceAtLeast(1), appLanguage)
            ?: return WenkuApiResult.ApiNotConfigured

        return executeXmlRequest(params, isIdempotentReadOnly = true) { rawXml ->
            WenkuXmlParsers.parseReviewReplyPage(rawXml)
        }
    }

    override suspend fun postNewReviewThread(
        aid: Int,
        title: String,
        content: String,
    ): WenkuApiResult<Unit> {
        if (!isApiConfigured) return WenkuApiResult.ApiNotConfigured
        val params = paramsFactory.commentNewThreadParams(aid, title, content)
            ?: return WenkuApiResult.ApiNotConfigured
        return executeNumericMutationRequest(params)
    }

    override suspend fun postReviewReply(
        rid: Int,
        content: String,
    ): WenkuApiResult<Unit> {
        if (!isApiConfigured) return WenkuApiResult.ApiNotConfigured
        val params = paramsFactory.commentReplyParams(rid, content)
            ?: return WenkuApiResult.ApiNotConfigured
        return executeNumericMutationRequest(params)
    }

    private suspend fun executeNumericMutationRequest(formParams: ContentValues): WenkuApiResult<Unit> {
        val endpointUrl = paramsFactory.baseUrlOrUnknown()
        if (!transport.isEndpointConfigured ||
            endpointUrl.isBlank() ||
            endpointUrl == LegacyLightNetworkTransport.UNKNOWN_ENDPOINT_SENTINEL
        ) {
            return WenkuApiResult.ApiNotConfigured
        }

        val responseBytes = transport.postForm(
            endpointUrl = endpointUrl,
            formParams = formParams,
            isIdempotentReadOnly = false,
        ) ?: return WenkuApiResult.NetworkError()

        val trimmed = String(responseBytes, StandardCharsets.UTF_8).trim()
        val code = trimmed.toIntOrNull() ?: return WenkuApiResult.ParseError(trimmed.take(120))
        return if (code == 1) {
            WenkuApiResult.Success(Unit)
        } else {
            WenkuApiResult.ServerErrorCode(code)
        }
    }

    private suspend fun <T : Any> executeXmlRequest(
        formParams: ContentValues,
        isIdempotentReadOnly: Boolean = true,
        parserBlock: (String) -> T?,
    ): WenkuApiResult<T> {
        val endpointUrl = paramsFactory.baseUrlOrUnknown()
        if (!transport.isEndpointConfigured ||
            endpointUrl.isBlank() ||
            endpointUrl == LegacyLightNetworkTransport.UNKNOWN_ENDPOINT_SENTINEL
        ) {
            return WenkuApiResult.ApiNotConfigured
        }

        val responseBytes = transport.postForm(
            endpointUrl = endpointUrl,
            formParams = formParams,
            isIdempotentReadOnly = isIdempotentReadOnly,
        ) ?: return WenkuApiResult.NetworkError()

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
        formParams: ContentValues,
        isIdempotentReadOnly: Boolean = true,
        rejectNullLiteral: Boolean = false,
    ): WenkuApiResult<String> {
        val endpointUrl = paramsFactory.baseUrlOrUnknown()
        if (!transport.isEndpointConfigured ||
            endpointUrl.isBlank() ||
            endpointUrl == LegacyLightNetworkTransport.UNKNOWN_ENDPOINT_SENTINEL
        ) {
            return WenkuApiResult.ApiNotConfigured
        }

        val responseBytes = transport.postForm(
            endpointUrl = endpointUrl,
            formParams = formParams,
            isIdempotentReadOnly = isIdempotentReadOnly,
        ) ?: return WenkuApiResult.NetworkError()

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
