package org.mewx.wenku8.v2.data.repository

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
import org.mewx.wenku8.v2.data.remote.parser.ParsedUserInfo
import org.mewx.wenku8.v2.data.remote.parser.WenkuXmlParsers
import org.mewx.wenku8.v2.data.remote.transport.LegacyLightNetworkTransport
import org.mewx.wenku8.v2.data.remote.transport.WenkuTransport
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
        const val SAVES_FOLDER_NAME: String = "saves"
        const val CERT_FILE_NAME: String = "cert.wk8"
        const val AVATAR_FILE_NAME: String = "avatar.jpg"

        fun isValidCredentialLength(identifier: String, password: String): Boolean {
            val cleanId = identifier.trim()
            return cleanId.length in 1..MAX_CREDENTIAL_LENGTH &&
                password.length in 1..MAX_CREDENTIAL_LENGTH
        }
    }

    private val authMutex = Mutex()
    private var cachedCredentials: StoredAccountCredentials? = null

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
     * Restores any saved `saves/cert.wk8` session on startup without blocking cold start.
     */
    suspend fun initializeSessionOnStartup() = withContext(ioDispatcher) {
        authMutex.withLock {
            if (!isAuthSupported) {
                _sessionState.value = AccountSessionState.UnavailableStub
                return@withLock
            }

            val loadedCreds = loadCredentialsFromDisk()
            if (loadedCreds == null) {
                _sessionState.value = AccountSessionState.SignedOut
                return@withLock
            }

            cachedCredentials = loadedCreds
            authGateway.syncInMemoryCredentials(loadedCreds.identifier, loadedCreds.password)
            val existingAvatar = findExistingAvatarFile()
            _sessionState.value = AccountSessionState.RestoringSession(
                identifierHint = loadedCreds.identifier,
                cachedAvatarFile = existingAvatar,
            )

            when (val restoreCode = executeLoginCallLocked(loadedCreds.identifier, loadedCreds.password)) {
                Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED -> {
                    val infoResult = fetchUserInfoAndAvatarLocked(allowSingleRelogin = false)
                    val profile = infoResult.first ?: ParsedUserInfo(
                        username = loadedCreds.identifier,
                        nickname = loadedCreds.identifier,
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
                    val creds = StoredAccountCredentials(cleanIdentifier, rawPassword)
                    cachedCredentials = creds
                    authGateway.syncInMemoryCredentials(cleanIdentifier, rawPassword)
                    persistCredentialsToDisk(creds)

                    val (fetchedInfo, avatarFile) = fetchUserInfoAndAvatarLocked(allowSingleRelogin = false)
                    val resolvedProfile = fetchedInfo ?: ParsedUserInfo(
                        username = cleanIdentifier,
                        nickname = cleanIdentifier,
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

            var signCode = executeSignRequestLocked()
            if (signCode == Wenku8Error.ErrorCode.SYSTEM_4_NOT_LOGGED_IN && creds != null) {
                val relogin = executeLoginCallLocked(creds.identifier, creds.password)
                if (relogin == Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED) {
                    signCode = executeSignRequestLocked()
                } else {
                    return@withLock DailySignOutcome.NetworkOrServerError(relogin)
                }
            }

            when (signCode) {
                Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED -> {
                    val (freshInfo, freshAvatar) = fetchUserInfoAndAvatarLocked(allowSingleRelogin = false)
                    val updatedProfile = freshInfo ?: currentSignedIn?.profile ?: ParsedUserInfo(
                        username = creds?.identifier.orEmpty(),
                        nickname = creds?.identifier.orEmpty(),
                    )
                    _sessionState.value = AccountSessionState.SignedIn(
                        profile = updatedProfile,
                        cachedAvatarFile = freshAvatar ?: currentSignedIn?.cachedAvatarFile ?: findExistingAvatarFile(),
                        avatarCacheBusterMs = System.currentTimeMillis(),
                        dailySignStatus = DailySignStatus.SIGNED_IN_SUCCESS,
                    )
                    DailySignOutcome.SignedAndRefreshed(updatedProfile)
                }

                Wenku8Error.ErrorCode.SYSTEM_9_SIGN_FAILED -> {
                    if (currentSignedIn != null) {
                        _sessionState.value = currentSignedIn.copy(
                            dailySignStatus = DailySignStatus.ALREADY_SIGNED_OR_REJECTED,
                        )
                    }
                    DailySignOutcome.AlreadySignedOrRefused
                }

                else -> DailySignOutcome.NetworkOrServerError(signCode)
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
     * Performs a bounded single re-login using stored `saves/cert.wk8` credentials when another
     * authenticated endpoint (e.g., review posting or cloud bookcase sync) receives
     * `SYSTEM_4_NOT_LOGGED_IN` (`4`) due to an expired `PHPSESSID` cookie.
     *
     * Never retries more than once per caller request, and wipes local credentials only if the
     * server explicitly rejects the username (`SYSTEM_2`) or password (`SYSTEM_3`).
     */
    suspend fun reauthenticateFromSavedCredentialsIfPossible(): Boolean = withContext(ioDispatcher) {
        authMutex.withLock {
            if (!isAuthSupported) return@withLock false
            val creds = cachedCredentials ?: loadCredentialsFromDisk() ?: return@withLock false
            cachedCredentials = creds
            authGateway.syncInMemoryCredentials(creds.identifier, creds.password)

            when (executeLoginCallLocked(creds.identifier, creds.password)) {
                Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED -> {
                    val (freshInfo, freshAvatar) = fetchUserInfoAndAvatarLocked(allowSingleRelogin = false)
                    val currentSignedIn = _sessionState.value as? AccountSessionState.SignedIn
                    val profile = freshInfo ?: currentSignedIn?.profile ?: ParsedUserInfo(
                        username = creds.identifier,
                        nickname = creds.identifier,
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

    private suspend fun executeSignRequestLocked(): Wenku8Error.ErrorCode {
        val endpointUrl = authGateway.baseUrlOrEmpty.takeIf { it.isNotEmpty() }
            ?: return Wenku8Error.ErrorCode.NETWORK_ERROR
        val params = authGateway.userSignParams()
            ?: return Wenku8Error.ErrorCode.PARAM_COUNT_NOT_MATCHED
        val bytes = transport.postForm(
            endpointUrl = endpointUrl,
            formParams = params,
            isIdempotentReadOnly = false,
        ) ?: return Wenku8Error.ErrorCode.NETWORK_ERROR
        val rawBody = String(bytes, StandardCharsets.UTF_8).trim()
        val numeric = rawBody.toIntOrNull() ?: return Wenku8Error.ErrorCode.STRING_CONVERSION_ERROR
        return authGateway.mapServerErrorCode(numeric)
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
        authGateway.clearInMemorySession()
        transport.clearSessionCookie()
        sessionClearCallback()
        for (root in assetRootsProvider()) {
            val savesDir = File(root, SAVES_FOLDER_NAME)
            runCatching { File(savesDir, CERT_FILE_NAME).delete() }
            runCatching { File(savesDir, AVATAR_FILE_NAME).delete() }
        }
    }
}
