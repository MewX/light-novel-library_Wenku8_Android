package org.mewx.wenku8.data.repository

import android.content.ContentValues
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.api.Wenku8Error
import org.mewx.wenku8.network.LightUserSession
import org.mewx.wenku8.data.remote.parser.ParsedUserInfo
import org.mewx.wenku8.data.remote.parser.WenkuXmlParsers
import org.mewx.wenku8.data.remote.transport.LegacyLightNetworkTransport
import org.mewx.wenku8.data.remote.transport.WenkuTransport
import java.io.File
import java.nio.charset.StandardCharsets

data class StoredAccountCredentials(
    val identifier: String,
    val password: String,
)

enum class DailySignStatus {
    IDLE,
    SIGNED_IN_SUCCESS,
    ALREADY_SIGNED_OR_REJECTED,
}

sealed interface AccountSessionState {
    /** `:api-stub` build where account endpoints are not configured. */
    data object UnavailableStub : AccountSessionState

    /** No saved account (`cert.wk8` absent or cleared). */
    data object SignedOut : AccountSessionState

    /** Restoring stored `cert.wk8` session in the background. */
    data class RestoringSession(
        val identifierHint: String,
        val cachedAvatarFile: File?,
    ) : AccountSessionState

    /** Active authenticated session with parsed profile details. */
    data class SignedIn(
        val profile: ParsedUserInfo,
        val cachedAvatarFile: File?,
        val avatarCacheBusterMs: Long = 0L,
        val dailySignStatus: DailySignStatus = DailySignStatus.IDLE,
    ) : AccountSessionState

    /**
     * Stored `cert.wk8` exists on disk, but the network was unreachable during startup/refresh.
     * Preserves `cert.wk8` and `avatar.jpg` intact so temporary offline launches never wipe user login!
     */
    data class OfflineWithSavedAccount(
        val identifierHint: String,
        val cachedAvatarFile: File?,
    ) : AccountSessionState
}

sealed interface LoginOutcome {
    data class Success(val profile: ParsedUserInfo) : LoginOutcome
    data object InvalidCredentialLength : LoginOutcome
    data object UsernameNotFound : LoginOutcome
    data object IncorrectPassword : LoginOutcome
    data object ApiNotConfigured : LoginOutcome
    data class NetworkOrServerError(val errorCode: Wenku8Error.ErrorCode) : LoginOutcome
}

sealed interface DailySignOutcome {
    data class SignedAndRefreshed(val updatedProfile: ParsedUserInfo) : DailySignOutcome
    data object AlreadySignedOrRefused : DailySignOutcome
    data object NotSignedIn : DailySignOutcome
    data class NetworkOrServerError(val errorCode: Wenku8Error.ErrorCode) : DailySignOutcome
}

/**
 * Stub-safe and host-JVM-safe adapter around `:api` account parameter builders, error mappings,
 * and `LightUserSession` `cert.wk8` encryption/decryption.
 */
interface AccountAuthGateway {
    val isAuthConfigured: Boolean
    val baseUrlOrEmpty: String
    val registerUrlOrEmpty: String

    fun loginParams(identifier: String, password: String): ContentValues?
    fun logoutParams(): ContentValues?
    fun userInfoParams(): ContentValues?
    fun userSignParams(): ContentValues?
    fun avatarUrlForUid(uid: Int): String
    fun mapServerErrorCode(rawCode: Int): Wenku8Error.ErrorCode

    fun decodeCertBlob(rawCertContent: String): StoredAccountCredentials?
    fun encodeCertBlob(identifier: String, password: String): String?
    fun syncInMemoryCredentials(identifier: String, password: String)
    fun clearInMemorySession()
}

object NativeAccountAuthGateway : AccountAuthGateway {

    override val isAuthConfigured: Boolean
        get() = safeCall(false) {
            val base = Wenku8API.BASE_URL.orEmpty()
            val probeParams = Wenku8API.getUserInfoParams()
            base.isNotBlank() &&
                base != LegacyLightNetworkTransport.UNKNOWN_ENDPOINT_SENTINEL &&
                probeParams != null &&
                probeParams.size() > 0
        }

    override val baseUrlOrEmpty: String
        get() = safeCall("") {
            val raw = Wenku8API.BASE_URL.orEmpty().trim()
            if (raw.isEmpty() || raw.equals(LegacyLightNetworkTransport.UNKNOWN_ENDPOINT_SENTINEL, ignoreCase = true)) {
                ""
            } else {
                raw
            }
        }

    override val registerUrlOrEmpty: String
        get() = safeCall("") {
            val raw = Wenku8API.REGISTER_URL.orEmpty().trim()
            if (raw.isEmpty() || raw.equals(Wenku8API.UNKNOWN, ignoreCase = true)) "" else raw
        }

    override fun loginParams(identifier: String, password: String): ContentValues? =
        safeCall(null) {
            if (identifier.contains('@')) {
                Wenku8API.getUserLoginEmailParams(identifier, password)
            } else {
                Wenku8API.getUserLoginParams(identifier, password)
            }?.takeIf { it.size() > 0 }
        }

    override fun logoutParams(): ContentValues? =
        safeCall(null) {
            Wenku8API.getUserLogoutParams()?.takeIf { it.size() > 0 }
        }

    override fun userInfoParams(): ContentValues? =
        safeCall(null) {
            Wenku8API.getUserInfoParams()?.takeIf { it.size() > 0 }
        }

    override fun userSignParams(): ContentValues? =
        safeCall(null) {
            Wenku8API.getUserSignParams()?.takeIf { it.size() > 0 }
        }

    override fun avatarUrlForUid(uid: Int): String =
        safeCall("") {
            if (uid <= 0) "" else Wenku8API.getAvatarURL(uid).orEmpty().trim()
        }

    override fun mapServerErrorCode(rawCode: Int): Wenku8Error.ErrorCode =
        safeCall(Wenku8Error.ErrorCode.ERROR_DEFAULT) {
            Wenku8Error.getSystemDefinedErrorCode(rawCode)
        }

    override fun decodeCertBlob(rawCertContent: String): StoredAccountCredentials? =
        safeCall(null) {
            val trimmed = rawCertContent.trim()
            if (trimmed.isEmpty()) return@safeCall null
            synchronized(LightUserSession::class.java) {
                LightUserSession.setUserInfo("", "")
                LightUserSession.decAndSetUserFile(trimmed)
                val decodedId = LightUserSession.getUsernameOrEmail().orEmpty()
                val decodedPwd = LightUserSession.getPassword().orEmpty()
                if (decodedId.isNotEmpty() && decodedPwd.isNotEmpty()) {
                    StoredAccountCredentials(identifier = decodedId, password = decodedPwd)
                } else {
                    null
                }
            }
        }

    override fun encodeCertBlob(identifier: String, password: String): String? =
        safeCall(null) {
            synchronized(LightUserSession::class.java) {
                LightUserSession.setUserInfo(identifier, password)
                LightUserSession.encUserFile().takeIf { it.isNotBlank() }
            }
        }

    override fun syncInMemoryCredentials(identifier: String, password: String) {
        safeCall(Unit) {
            synchronized(LightUserSession::class.java) {
                LightUserSession.setUserInfo(identifier, password)
            }
        }
    }

    override fun clearInMemorySession() {
        safeCall(Unit) {
            synchronized(LightUserSession::class.java) {
                runCatching { LightUserSession.logOut { } }
                runCatching { LightUserSession.setUserInfo("", "") }
                // Note: LightUserSession.setSession ignores empty strings, so RelayHttpTransport's
                // clearSessionCookie / empty PHPSESSID is complemented by clearing credentials.
            }
        }
    }

    private inline fun <T> safeCall(fallback: T, block: () -> T): T {
        return try {
            block()
        } catch (_: UnsupportedOperationException) {
            fallback
        } catch (_: LinkageError) {
            fallback
        } catch (_: Throwable) {
            fallback
        }
    }
}

/**
 * Coroutine-first Account Session & Profile repository for Wenku8 `:app-v2` (`P3.1`).
 *
 * Guarantees:
 * 1. **100% `v1.31` `cert.wk8` & `avatar.jpg` Upgrade Compatibility**: Directly reads and writes
 *    `filesDir/saves/cert.wk8` and `filesDir/saves/avatar.jpg` so Play Store upgrades from `v1.31`
 *    stay seamlessly authenticated without logging credentials to logcat.
 * 2. **Relay-Optimized Transport**: Routes login, logout, `UserInfo`, and `每日签到` requests
 *    through [WenkuTransport] (`RelayHttpTransport` + `"自动优化连接"`).
 * 3. **Bounded Single Re-Login**: Follows `AccountInfoLoader`'s strict at-most-once session
 *    restoration rule on `SYSTEM_4_NOT_LOGGED_IN`, and never deletes `cert.wk8` on offline/network
 *    errors (wiping stored credentials only on explicit logout or server `SYSTEM_2`/`SYSTEM_3`).
 */
class UserSessionRepository(
    private val transport: WenkuTransport,
    private val assetRootsProvider: () -> List<File>,
    private val authGateway: AccountAuthGateway = NativeAccountAuthGateway,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val sessionClearCallback: () -> Unit = {},
) {
    companion object {
        const val MAX_CREDENTIAL_LENGTH: Int = 30
        const val FORGOT_PASSWORD_URL: String = "https://www.wenku8.com/getpass.php"
        const val SAVES_FOLDER_NAME: String = "saves"
        const val CERT_FILE_NAME: String = "cert.wk8"
        const val CANONICAL_UNAME_FILE_NAME: String = "account_uname.wk8"
        const val AVATAR_FILE_NAME: String = "avatar.jpg"
        private const val MAX_SIGN_JSONP_CHARS: Int = 512
        private val SIGN_JSONP_RET_REGEX = Regex(
            pattern = """^(?:[A-Za-z0-9_$]+\s*\(\s*)?\{\s*"ret"\s*:\s*(-?\d+)(?:\s*,.*?)?\}\s*(?:\)\s*;?\s*)?$""",
            option = RegexOption.DOT_MATCHES_ALL,
        )

        fun isValidCredentialLength(identifier: String, password: String): Boolean {
            val cleanId = identifier.trim()
            return cleanId.length in 1..MAX_CREDENTIAL_LENGTH &&
                password.length in 1..MAX_CREDENTIAL_LENGTH
        }

        fun normalizeAccountKey(rawIdentifier: String): String =
            rawIdentifier.trim().lowercase(java.util.Locale.ROOT)

        /**
         * Parses Wenku8's `action=block&do=sign` payload:
         * - First daily check-in returns JSONP `_cb({"ret":0});` (documented in `Wenku8API.getUserSignParams`)
         *   or plain `"0"` / `"1"`.
         * - Subsequent same-day check-in returns `"9"` (`SYSTEM_9_SIGN_FAILED`) or `{"ret":9}`.
         * - Lapsed session returns `"4"` (`SYSTEM_4_NOT_LOGGED_IN`) or `{"ret":4}`.
         * - HTML error pages / captive portals fail safely with `STRING_CONVERSION_ERROR`.
         */
        internal fun parseSignResponseBody(
            rawBody: String,
            mapServerErrorCode: (Int) -> Wenku8Error.ErrorCode,
        ): SignServerResponse {
            val cleaned = rawBody.trimStart('\uFEFF').trim()
            if (cleaned.isEmpty() || cleaned.length > MAX_SIGN_JSONP_CHARS) {
                return SignServerResponse.Failure(Wenku8Error.ErrorCode.STRING_CONVERSION_ERROR)
            }
            val numericCode = cleaned.toIntOrNull()
                ?: SIGN_JSONP_RET_REGEX.matchEntire(cleaned)?.groupValues?.getOrNull(1)?.toIntOrNull()
                ?: return SignServerResponse.Failure(Wenku8Error.ErrorCode.STRING_CONVERSION_ERROR)

            return when (numericCode) {
                0, 1 -> SignServerResponse.Success
                4 -> SignServerResponse.NotLoggedIn
                9 -> SignServerResponse.AlreadySigned
                else -> SignServerResponse.Failure(mapServerErrorCode(numericCode))
            }
        }
    }

    private val authMutex = Mutex()
    @Volatile
    private var cachedCredentials: StoredAccountCredentials? = null
    @Volatile
    private var cachedCanonicalUsername: String? = null

    private val _sessionState = MutableStateFlow<AccountSessionState>(
        if (authGateway.isAuthConfigured && authGateway.baseUrlOrEmpty.isNotEmpty() && transport.isEndpointConfigured) {
            AccountSessionState.SignedOut
        } else {
            AccountSessionState.UnavailableStub
        },
    )
    val sessionState: StateFlow<AccountSessionState> = _sessionState.asStateFlow()

    val isAuthSupported: Boolean
        get() = authGateway.isAuthConfigured &&
            authGateway.baseUrlOrEmpty.isNotEmpty() &&
            transport.isEndpointConfigured

    val registerPageUrl: String
        get() = authGateway.registerUrlOrEmpty

    /**
     * Returns the normalized (`lowercase`) canonical Wenku8 account key for the active or saved
     * session, or `null` when signed out / stubbed.
     *
     * Prefers the server-resolved `uname` (`ParsedUserInfo.username`, persisted in
     * `saves/account_uname.wk8` across offline restarts) over an email login identifier so
     * `cloud_bookcase_ledger` entries stay bound to the same key online and offline.
     */
    fun activeAccountKeyOrNull(): String? {
        return when (val state = _sessionState.value) {
            is AccountSessionState.SignedIn -> {
                val uname = cachedCanonicalUsername?.takeIf { it.isNotBlank() }
                    ?: state.profile.username.takeIf { it.isNotBlank() }
                uname?.let(::normalizeAccountKey)
            }

            is AccountSessionState.OfflineWithSavedAccount -> {
                val uname = cachedCanonicalUsername?.takeIf { it.isNotBlank() }
                    ?: loadCanonicalUsernameFromDisk()
                    ?: state.identifierHint.takeIf { it.isNotBlank() }
                uname?.let(::normalizeAccountKey)
            }

            is AccountSessionState.RestoringSession -> {
                val uname = cachedCanonicalUsername?.takeIf { it.isNotBlank() }
                    ?: loadCanonicalUsernameFromDisk()
                    ?: state.identifierHint.takeIf { it.isNotBlank() }
                uname?.let(::normalizeAccountKey)
            }

            AccountSessionState.SignedOut -> {
                if (!isAuthSupported) {
                    null
                } else {
                    val persistedCreds = cachedCredentials ?: loadCredentialsFromDisk()
                    if (persistedCreds == null) {
                        null
                    } else {
                        val uname = cachedCanonicalUsername?.takeIf { it.isNotBlank() }
                            ?: loadCanonicalUsernameFromDisk()
                            ?: persistedCreds.identifier.takeIf { it.isNotBlank() }
                        uname?.let(::normalizeAccountKey)
                    }
                }
            }

            AccountSessionState.UnavailableStub -> null
        }
    }

    /**
     * Returns the normalized (`lowercase`) raw login identifier (`username` or `email`) from
     * `cert.wk8` when available, allowing [BookshelfRepository] to migrate any ledger rows recorded
     * under an email hint before the canonical `uname` was first resolved.
     */
    fun rawSavedIdentifierKeyOrNull(): String? {
        val raw = cachedCredentials?.identifier?.takeIf { it.isNotBlank() }
            ?: when (val state = _sessionState.value) {
                is AccountSessionState.OfflineWithSavedAccount -> state.identifierHint
                is AccountSessionState.RestoringSession -> state.identifierHint
                AccountSessionState.SignedOut -> {
                    if (isAuthSupported) loadCredentialsFromDisk()?.identifier else null
                }
                else -> null
            }
        return raw?.takeIf { it.isNotBlank() }?.let(::normalizeAccountKey)
    }

    /**
     * Restores any saved `saves/cert.wk8` session on startup without blocking cold start.
     */
    suspend fun initializeSessionOnStartup() = withContext(ioDispatcher) {
        authMutex.withLock {
            if (!isAuthSupported) {
                _sessionState.value = AccountSessionState.UnavailableStub
                return@withLock
            }
            if (_sessionState.value is AccountSessionState.SignedIn) {
                return@withLock
            }

            val loadedCreds = loadCredentialsFromDisk()
            if (loadedCreds == null) {
                cachedCanonicalUsername = null
                _sessionState.value = AccountSessionState.SignedOut
                return@withLock
            }

            cachedCredentials = loadedCreds
            cachedCanonicalUsername = loadCanonicalUsernameFromDisk()
            authGateway.syncInMemoryCredentials(loadedCreds.identifier, loadedCreds.password)
            val existingAvatar = findExistingAvatarFile()
            _sessionState.value = AccountSessionState.RestoringSession(
                identifierHint = loadedCreds.identifier,
                cachedAvatarFile = existingAvatar,
            )

            when (val restoreCode = executeLoginCallLocked(loadedCreds.identifier, loadedCreds.password)) {
                Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED -> {
                    val infoResult = fetchUserInfoAndAvatarLocked(allowSingleRelogin = false)
                    val resolvedUname = infoResult.first?.username?.takeIf { it.isNotBlank() }
                        ?: cachedCanonicalUsername
                        ?: loadedCreds.identifier.takeIf { !it.contains('@') }
                    if (!resolvedUname.isNullOrBlank()) {
                        persistCanonicalUsernameLocked(resolvedUname)
                    }
                    val profile = infoResult.first ?: ParsedUserInfo(
                        username = resolvedUname ?: loadedCreds.identifier,
                        nickname = resolvedUname ?: loadedCreds.identifier,
                    )
                    _sessionState.value = AccountSessionState.SignedIn(
                        profile = profile,
                        cachedAvatarFile = infoResult.second ?: existingAvatar,
                        avatarCacheBusterMs = System.currentTimeMillis(),
                    )
                }

                Wenku8Error.ErrorCode.SYSTEM_2_ERROR_USERNAME,
                Wenku8Error.ErrorCode.SYSTEM_3_ERROR_PASSWORD,
                -> {
                    // Server explicitly rejected stored credentials -> wipe stale cert & avatar.
                    wipeStoredAccountFilesAndMemoryLocked()
                    _sessionState.value = AccountSessionState.SignedOut
                }

                else -> {
                    // Network error or transient server status -> preserve cert.wk8 & avatar.jpg!
                    _sessionState.value = AccountSessionState.OfflineWithSavedAccount(
                        identifierHint = loadedCreds.identifier,
                        cachedAvatarFile = existingAvatar,
                    )
                }
            }
        }
    }

    /**
     * Authenticates with explicit username/email (`<= 30` chars) and password (`<= 30` chars),
     * saves encrypted `saves/cert.wk8`, and hydrates `ParsedUserInfo` + `saves/avatar.jpg`.
     */
    suspend fun loginWithCredentials(
        rawIdentifier: String,
        rawPassword: String,
    ): LoginOutcome = withContext(ioDispatcher) {
        val cleanIdentifier = rawIdentifier.trim()
        if (!isValidCredentialLength(cleanIdentifier, rawPassword)) {
            return@withContext LoginOutcome.InvalidCredentialLength
        }

        authMutex.withLock {
            if (!isAuthSupported) {
                _sessionState.value = AccountSessionState.UnavailableStub
                return@withLock LoginOutcome.ApiNotConfigured
            }

            when (val code = executeLoginCallLocked(cleanIdentifier, rawPassword)) {
                Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED -> {
                    val previousIdentifier = cachedCredentials?.identifier
                    if (previousIdentifier == null ||
                        normalizeAccountKey(previousIdentifier) != normalizeAccountKey(cleanIdentifier)
                    ) {
                        clearCanonicalUsernameLocked()
                    }
                    val creds = StoredAccountCredentials(cleanIdentifier, rawPassword)
                    cachedCredentials = creds
                    authGateway.syncInMemoryCredentials(cleanIdentifier, rawPassword)
                    persistCredentialsToDisk(creds)

                    val (fetchedInfo, avatarFile) = fetchUserInfoAndAvatarLocked(allowSingleRelogin = false)
                    val resolvedUname = fetchedInfo?.username?.takeIf { it.isNotBlank() }
                        ?: cachedCanonicalUsername
                        ?: cleanIdentifier.takeIf { !it.contains('@') }
                    if (!resolvedUname.isNullOrBlank()) {
                        persistCanonicalUsernameLocked(resolvedUname)
                    }
                    val resolvedProfile = fetchedInfo ?: ParsedUserInfo(
                        username = resolvedUname ?: cleanIdentifier,
                        nickname = resolvedUname ?: cleanIdentifier,
                    )
                    _sessionState.value = AccountSessionState.SignedIn(
                        profile = resolvedProfile,
                        cachedAvatarFile = avatarFile ?: findExistingAvatarFile(),
                        avatarCacheBusterMs = System.currentTimeMillis(),
                        dailySignStatus = DailySignStatus.IDLE,
                    )
                    LoginOutcome.Success(resolvedProfile)
                }

                Wenku8Error.ErrorCode.SYSTEM_2_ERROR_USERNAME -> LoginOutcome.UsernameNotFound
                Wenku8Error.ErrorCode.SYSTEM_3_ERROR_PASSWORD -> LoginOutcome.IncorrectPassword
                else -> LoginOutcome.NetworkOrServerError(code)
            }
        }
    }

    /**
     * Refreshes the signed-in user's profile (`score`, `experience`, `rank`, `avatar.jpg`), with
     * at-most-once automatic session re-authentication if `PHPSESSID` lapsed (`SYSTEM_4_NOT_LOGGED_IN`).
     */
    suspend fun refreshProfile(): Boolean = withContext(ioDispatcher) {
        authMutex.withLock {
            if (!isAuthSupported) return@withLock false
            val (info, avatar) = fetchUserInfoAndAvatarLocked(allowSingleRelogin = true)
            if (info != null) {
                if (info.username.isNotBlank()) {
                    persistCanonicalUsernameLocked(info.username)
                }
                val previousSignStatus = (_sessionState.value as? AccountSessionState.SignedIn)
                    ?.dailySignStatus ?: DailySignStatus.IDLE
                _sessionState.value = AccountSessionState.SignedIn(
                    profile = info,
                    cachedAvatarFile = avatar ?: findExistingAvatarFile(),
                    avatarCacheBusterMs = System.currentTimeMillis(),
                    dailySignStatus = previousSignStatus,
                )
                true
            } else {
                false
            }
        }
    }

    /**
     * Performs `每日签到` (`Wenku8API.getUserSignParams()`), automatically re-authenticating once
     * if the session cookie lapsed, and refreshes `score` / `experience` on success.
     */
    suspend fun performDailySignIn(): DailySignOutcome = withContext(ioDispatcher) {
        authMutex.withLock {
            if (!isAuthSupported) return@withLock DailySignOutcome.NotSignedIn
            val currentSignedIn = _sessionState.value as? AccountSessionState.SignedIn
            val creds = cachedCredentials ?: loadCredentialsFromDisk()
            if (currentSignedIn == null && creds == null) {
                return@withLock DailySignOutcome.NotSignedIn
            }

            var signResponse = executeSignRequestLocked()
            if (signResponse is SignServerResponse.NotLoggedIn && creds != null) {
                val relogin = executeLoginCallLocked(creds.identifier, creds.password)
                if (relogin == Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED) {
                    signResponse = executeSignRequestLocked()
                } else {
                    return@withLock DailySignOutcome.NetworkOrServerError(relogin)
                }
            }

            when (signResponse) {
                SignServerResponse.Success -> {
                    val (freshInfo, freshAvatar) = fetchUserInfoAndAvatarLocked(allowSingleRelogin = false)
                    val resolvedUname = freshInfo?.username?.takeIf { it.isNotBlank() }
                        ?: cachedCanonicalUsername
                        ?: currentSignedIn?.profile?.username?.takeIf { it.isNotBlank() }
                        ?: creds?.identifier?.takeIf { !it.contains('@') }
                    if (!resolvedUname.isNullOrBlank()) {
                        persistCanonicalUsernameLocked(resolvedUname)
                    }
                    val updatedProfile = freshInfo ?: currentSignedIn?.profile ?: ParsedUserInfo(
                        username = resolvedUname ?: creds?.identifier.orEmpty(),
                        nickname = resolvedUname ?: creds?.identifier.orEmpty(),
                    )
                    _sessionState.value = AccountSessionState.SignedIn(
                        profile = updatedProfile,
                        cachedAvatarFile = freshAvatar ?: currentSignedIn?.cachedAvatarFile ?: findExistingAvatarFile(),
                        avatarCacheBusterMs = System.currentTimeMillis(),
                        dailySignStatus = DailySignStatus.SIGNED_IN_SUCCESS,
                    )
                    DailySignOutcome.SignedAndRefreshed(updatedProfile)
                }

                SignServerResponse.AlreadySigned -> {
                    if (currentSignedIn != null) {
                        _sessionState.value = currentSignedIn.copy(
                            dailySignStatus = DailySignStatus.ALREADY_SIGNED_OR_REJECTED,
                        )
                    }
                    DailySignOutcome.AlreadySignedOrRefused
                }

                SignServerResponse.NotLoggedIn -> DailySignOutcome.NetworkOrServerError(
                    Wenku8Error.ErrorCode.SYSTEM_4_NOT_LOGGED_IN,
                )

                is SignServerResponse.Failure -> DailySignOutcome.NetworkOrServerError(signResponse.errorCode)
            }
        }
    }

    /**
     * Logs out on the server (best-effort) and unconditionally wipes local `cert.wk8`, `avatar.jpg`,
     * and in-memory session cookies so the user is never trapped in a stale session.
     */
    suspend fun logout() = withContext(ioDispatcher) {
        authMutex.withLock {
            val endpointUrl = authGateway.baseUrlOrEmpty
            if (isAuthSupported && endpointUrl.isNotEmpty()) {
                authGateway.logoutParams()?.let { params ->
                    runCatching {
                        transport.postForm(
                            endpointUrl = endpointUrl,
                            formParams = params,
                            isIdempotentReadOnly = false,
                        )
                    }
                }
            }
            wipeStoredAccountFilesAndMemoryLocked()
            _sessionState.value = if (isAuthSupported) {
                AccountSessionState.SignedOut
            } else {
                AccountSessionState.UnavailableStub
            }
        }
    }

    /**
     * Ensures saved `saves/cert.wk8` credentials have been restored into an active session before
     * cold-start Cloud Bookcase sync runs. Shares [authMutex] with [initializeSessionOnStartup] so
     * that if startup restoration already completed while waiting on the lock, the active session
     * is reused without issuing a duplicate login request.
     */
    suspend fun ensureSessionRestoredForStartupSync(): Boolean = withContext(ioDispatcher) {
        authMutex.withLock {
            if (_sessionState.value is AccountSessionState.SignedIn) return@withLock true
            restoreFromSavedCredentialsLocked()
        }
    }

    /**
     * Performs a bounded single re-login using stored `saves/cert.wk8` credentials when another
     * authenticated endpoint (e.g., review posting or cloud bookcase sync) receives
     * `SYSTEM_4_NOT_LOGGED_IN` (`4`) due to an expired `PHPSESSID` cookie.
     *
     * Never retries more than once per caller request, and wipes local credentials only if the
     * server explicitly rejects the username (`SYSTEM_2`) or password (`SYSTEM_3`).
     */
    suspend fun reauthenticateFromSavedCredentialsIfPossible(): Boolean = withContext(ioDispatcher) {
        authMutex.withLock {
            restoreFromSavedCredentialsLocked()
        }
    }

    private suspend fun restoreFromSavedCredentialsLocked(): Boolean {
        if (!isAuthSupported) return false
        val creds = cachedCredentials ?: loadCredentialsFromDisk() ?: return false
        cachedCredentials = creds
        authGateway.syncInMemoryCredentials(creds.identifier, creds.password)

        return when (executeLoginCallLocked(creds.identifier, creds.password)) {
            Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED -> {
                val (freshInfo, freshAvatar) = fetchUserInfoAndAvatarLocked(allowSingleRelogin = false)
                val currentSignedIn = _sessionState.value as? AccountSessionState.SignedIn
                val resolvedUname = freshInfo?.username?.takeIf { it.isNotBlank() }
                    ?: cachedCanonicalUsername
                    ?: currentSignedIn?.profile?.username?.takeIf { it.isNotBlank() && !it.contains('@') }
                    ?: creds.identifier.takeIf { !it.contains('@') }
                if (!resolvedUname.isNullOrBlank()) {
                    persistCanonicalUsernameLocked(resolvedUname)
                }
                val profile = freshInfo ?: currentSignedIn?.profile ?: ParsedUserInfo(
                    username = resolvedUname ?: creds.identifier,
                    nickname = resolvedUname ?: creds.identifier,
                )
                _sessionState.value = AccountSessionState.SignedIn(
                    profile = profile,
                    cachedAvatarFile = freshAvatar ?: currentSignedIn?.cachedAvatarFile ?: findExistingAvatarFile(),
                    avatarCacheBusterMs = System.currentTimeMillis(),
                    dailySignStatus = currentSignedIn?.dailySignStatus ?: DailySignStatus.IDLE,
                )
                true
            }

            Wenku8Error.ErrorCode.SYSTEM_2_ERROR_USERNAME,
            Wenku8Error.ErrorCode.SYSTEM_3_ERROR_PASSWORD,
            -> {
                wipeStoredAccountFilesAndMemoryLocked()
                _sessionState.value = AccountSessionState.SignedOut
                false
            }

            else -> false
        }
    }

    private suspend fun executeLoginCallLocked(
        identifier: String,
        password: String,
    ): Wenku8Error.ErrorCode {
        val endpointUrl = authGateway.baseUrlOrEmpty.takeIf { it.isNotEmpty() }
            ?: return Wenku8Error.ErrorCode.NETWORK_ERROR
        val params = authGateway.loginParams(identifier, password)
            ?: return Wenku8Error.ErrorCode.PARAM_COUNT_NOT_MATCHED
        val bytes = transport.postForm(
            endpointUrl = endpointUrl,
            formParams = params,
            isIdempotentReadOnly = false,
        ) ?: return Wenku8Error.ErrorCode.NETWORK_ERROR
        val rawBody = String(bytes, StandardCharsets.UTF_8).trim()
        val numeric = rawBody.toIntOrNull() ?: return Wenku8Error.ErrorCode.RETURNED_VALUE_EXCEPTION
        return authGateway.mapServerErrorCode(numeric)
    }

    private suspend fun executeSignRequestLocked(): SignServerResponse {
        val endpointUrl = authGateway.baseUrlOrEmpty.takeIf { it.isNotEmpty() }
            ?: return SignServerResponse.Failure(Wenku8Error.ErrorCode.NETWORK_ERROR)
        val params = authGateway.userSignParams()
            ?: return SignServerResponse.Failure(Wenku8Error.ErrorCode.PARAM_COUNT_NOT_MATCHED)
        val bytes = transport.postForm(
            endpointUrl = endpointUrl,
            formParams = params,
            isIdempotentReadOnly = false,
        ) ?: return SignServerResponse.Failure(Wenku8Error.ErrorCode.NETWORK_ERROR)
        val rawBody = String(bytes, StandardCharsets.UTF_8)
        return parseSignResponseBody(rawBody, authGateway::mapServerErrorCode)
    }

    private suspend fun fetchUserInfoAndAvatarLocked(
        allowSingleRelogin: Boolean,
    ): Pair<ParsedUserInfo?, File?> {
        val endpointUrl = authGateway.baseUrlOrEmpty.takeIf { it.isNotEmpty() } ?: return null to null
        val infoParams = authGateway.userInfoParams() ?: return null to null
        var rawBytes = transport.postForm(
            endpointUrl = endpointUrl,
            formParams = infoParams,
            isIdempotentReadOnly = true,
        ) ?: return null to null
        var xmlOrCode = String(rawBytes, StandardCharsets.UTF_8).trim()

        val initialNumeric = xmlOrCode.toIntOrNull()
        if (initialNumeric != null) {
            val mapped = authGateway.mapServerErrorCode(initialNumeric)
            if (mapped == Wenku8Error.ErrorCode.SYSTEM_4_NOT_LOGGED_IN && allowSingleRelogin) {
                val creds = cachedCredentials ?: loadCredentialsFromDisk()
                if (creds != null) {
                    val relogin = executeLoginCallLocked(creds.identifier, creds.password)
                    if (relogin == Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED) {
                        rawBytes = transport.postForm(
                            endpointUrl = endpointUrl,
                            formParams = infoParams,
                            isIdempotentReadOnly = true,
                        ) ?: return null to null
                        xmlOrCode = String(rawBytes, StandardCharsets.UTF_8).trim()
                    } else {
                        if (relogin == Wenku8Error.ErrorCode.SYSTEM_2_ERROR_USERNAME ||
                            relogin == Wenku8Error.ErrorCode.SYSTEM_3_ERROR_PASSWORD
                        ) {
                            wipeStoredAccountFilesAndMemoryLocked()
                            _sessionState.value = AccountSessionState.SignedOut
                        }
                        return null to null
                    }
                } else {
                    return null to null
                }
            } else {
                return null to null
            }
        }

        val parsedInfo = WenkuXmlParsers.parseUserInfo(xmlOrCode) ?: return null to null
        val avatarUrl = authGateway.avatarUrlForUid(parsedInfo.uid)
        val savedAvatarFile = if (avatarUrl.isNotEmpty()) {
            val avatarBytes = transport.getBytes(avatarUrl)
            if (avatarBytes != null && avatarBytes.isNotEmpty()) {
                writeAvatarBytesToDisk(avatarBytes)
            } else {
                findExistingAvatarFile()
            }
        } else {
            findExistingAvatarFile()
        }
        return parsedInfo to savedAvatarFile
    }

    private fun loadCredentialsFromDisk(): StoredAccountCredentials? {
        for (root in assetRootsProvider()) {
            val certFile = File(File(root, SAVES_FOLDER_NAME), CERT_FILE_NAME)
            if (certFile.isFile && certFile.canRead()) {
                val rawContent = runCatching { certFile.readText(StandardCharsets.UTF_8) }.getOrNull()
                if (!rawContent.isNullOrBlank()) {
                    val decoded = authGateway.decodeCertBlob(rawContent)
                    if (decoded != null) return decoded
                }
            }
        }
        return null
    }

    private fun persistCredentialsToDisk(credentials: StoredAccountCredentials) {
        val encoded = authGateway.encodeCertBlob(credentials.identifier, credentials.password) ?: return
        val primaryRoot = assetRootsProvider().firstOrNull() ?: return
        val savesDir = File(primaryRoot, SAVES_FOLDER_NAME).apply { mkdirs() }
        runCatching {
            File(savesDir, CERT_FILE_NAME).writeText(encoded, StandardCharsets.UTF_8)
        }
    }

    private fun loadCanonicalUsernameFromDisk(): String? {
        for (root in assetRootsProvider()) {
            val unameFile = File(File(root, SAVES_FOLDER_NAME), CANONICAL_UNAME_FILE_NAME)
            if (unameFile.isFile && unameFile.canRead()) {
                val uname = runCatching { unameFile.readText(StandardCharsets.UTF_8).trim() }.getOrNull()
                if (!uname.isNullOrBlank()) {
                    return uname
                }
            }
        }
        return null
    }

    private fun persistCanonicalUsernameLocked(canonicalUsername: String) {
        val trimmed = canonicalUsername.trim()
        if (trimmed.isEmpty()) return
        cachedCanonicalUsername = trimmed
        val primaryRoot = assetRootsProvider().firstOrNull() ?: return
        val savesDir = File(primaryRoot, SAVES_FOLDER_NAME).apply { mkdirs() }
        runCatching {
            File(savesDir, CANONICAL_UNAME_FILE_NAME).writeText(trimmed, StandardCharsets.UTF_8)
        }
    }

    private fun clearCanonicalUsernameLocked() {
        cachedCanonicalUsername = null
        for (root in assetRootsProvider()) {
            val savesDir = File(root, SAVES_FOLDER_NAME)
            runCatching { File(savesDir, CANONICAL_UNAME_FILE_NAME).delete() }
        }
    }

    private fun findExistingAvatarFile(): File? {
        for (root in assetRootsProvider()) {
            val avatarFile = File(File(root, SAVES_FOLDER_NAME), AVATAR_FILE_NAME)
            if (avatarFile.isFile && avatarFile.length() > 0L) {
                return avatarFile
            }
        }
        return null
    }

    private fun writeAvatarBytesToDisk(bytes: ByteArray): File? {
        val primaryRoot = assetRootsProvider().firstOrNull() ?: return null
        val savesDir = File(primaryRoot, SAVES_FOLDER_NAME).apply { mkdirs() }
        val target = File(savesDir, AVATAR_FILE_NAME)
        return if (runCatching { target.writeBytes(bytes) }.isSuccess) target else null
    }

    private fun wipeStoredAccountFilesAndMemoryLocked() {
        cachedCredentials = null
        cachedCanonicalUsername = null
        authGateway.clearInMemorySession()
        transport.clearSessionCookie()
        sessionClearCallback()
        for (root in assetRootsProvider()) {
            val savesDir = File(root, SAVES_FOLDER_NAME)
            runCatching { File(savesDir, CERT_FILE_NAME).delete() }
            runCatching { File(savesDir, CANONICAL_UNAME_FILE_NAME).delete() }
            runCatching { File(savesDir, AVATAR_FILE_NAME).delete() }
        }
    }

    internal sealed interface SignServerResponse {
        data object Success : SignServerResponse
        data object AlreadySigned : SignServerResponse
        data object NotLoggedIn : SignServerResponse
        data class Failure(val errorCode: Wenku8Error.ErrorCode) : SignServerResponse
    }
}
