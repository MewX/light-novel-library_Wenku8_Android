package org.mewx.wenku8.data.update

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallState
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.requestAppUpdateInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import org.mewx.wenku8.BuildConfig
import org.mewx.wenku8.data.local.preferences.UserPreferencesRepository

/**
 * Google Play (`playstore` flavor) implementation of [AppUpdateCoordinator].
 *
 * Exclusively drives Google Play Flexible In-App Updates (`AppUpdateType.FLEXIBLE`) so Play
 * users receive differential background updates and restart-to-install prompts while strictly
 * complying with Google Play distribution policies (no external APK links or checks).
 */
class PlayStoreAppUpdateCoordinator(
    private val appUpdateManager: AppUpdateManager,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val currentVersionCode: Int = BuildConfig.VERSION_CODE,
    private val clockEpochMs: () -> Long = System::currentTimeMillis,
) : AppUpdateCoordinator {

    private val _uiState = MutableStateFlow<AppUpdateUiState>(AppUpdateUiState.Idle)
    override val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    private val _pendingAutoPrompt = MutableStateFlow<AppUpdateAutoPrompt?>(null)
    override val pendingAutoPrompt: StateFlow<AppUpdateAutoPrompt?> = _pendingAutoPrompt.asStateFlow()

    private val _showProfileUpdateBadge = MutableStateFlow(false)
    override val showProfileUpdateBadge: StateFlow<Boolean> = _showProfileUpdateBadge.asStateFlow()

    override val isPlayStoreChannel: Boolean = true

    @Volatile
    private var cachedAppUpdateInfo: AppUpdateInfo? = null

    @Volatile
    private var activeFlowVersionCode: Int = 0

    @Volatile
    private var lastCheckWasUserInitiated: Boolean = false

    @Volatile
    private var isListenerRegistered: Boolean = false

    private val installStateListener = InstallStateUpdatedListener { state ->
        handleInstallStateUpdate(state)
    }

    override suspend fun checkForUpdate(isUserInitiated: Boolean) {
        // Preserve active download / ready-to-install states instead of overwriting with Checking.
        val currentState = _uiState.value
        if (currentState is AppUpdateUiState.Downloading || currentState is AppUpdateUiState.ReadyToInstall) {
            return
        }

        lastCheckWasUserInitiated = isUserInitiated

        if (!isUserInitiated) {
            val prefs = userPreferencesRepository.preferencesFlow.first()
            val nowMs = clockEpochMs()
            val withinCooldown = prefs.lastAutoUpdateCheckEpochMs > 0L &&
                nowMs - prefs.lastAutoUpdateCheckEpochMs in 0L until AppUpdateCoordinator.AUTO_CHECK_INTERVAL_MS

            val info = fetchAppUpdateInfoOrNull() ?: return
            if (handleExistingInstallProgress(info)) {
                return
            }
            if (withinCooldown) {
                return
            }

            userPreferencesRepository.recordAutoUpdateCheck(nowMs)

            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                cachedAppUpdateInfo = info
                val targetCode = info.availableVersionCode()
                _uiState.value = AppUpdateUiState.UpdateAvailable(
                    currentVersionCode = currentVersionCode,
                    targetVersionCode = targetCode,
                    isPlayStoreManaged = true,
                    downloadPageUrl = null,
                )
                if (targetCode > prefs.dismissedUpdateVersionCode) {
                    _showProfileUpdateBadge.value = true
                    _pendingAutoPrompt.value = AppUpdateAutoPrompt.PlayStoreFlexible(
                        targetVersionCode = targetCode,
                    )
                }
            } else {
                _showProfileUpdateBadge.value = false
            }
            return
        }

        _uiState.value = AppUpdateUiState.Checking
        val info = fetchAppUpdateInfoOrNull()
        if (info == null) {
            _uiState.value = AppUpdateUiState.Failed(canOpenPlayStoreFallback = true)
            return
        }

        if (handleExistingInstallProgress(info)) {
            return
        }

        if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
            info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
        ) {
            cachedAppUpdateInfo = info
            val targetCode = info.availableVersionCode()
            _uiState.value = AppUpdateUiState.UpdateAvailable(
                currentVersionCode = currentVersionCode,
                targetVersionCode = targetCode,
                isPlayStoreManaged = true,
                downloadPageUrl = null,
            )
            _pendingAutoPrompt.value = AppUpdateAutoPrompt.PlayStoreFlexible(
                targetVersionCode = targetCode,
            )
        } else {
            cachedAppUpdateInfo = null
            _uiState.value = AppUpdateUiState.UpToDate(currentVersionCode)
            _showProfileUpdateBadge.value = false
            _pendingAutoPrompt.value = null
        }
    }

    override suspend fun onHostResumed() {
        val info = fetchAppUpdateInfoOrNull() ?: return
        handleExistingInstallProgress(info)
    }

    override fun startFlexibleUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>): Boolean {
        val info = cachedAppUpdateInfo
        // Play Core `AppUpdateInfo` instances are single-use; clear immediately to prevent reuse.
        cachedAppUpdateInfo = null
        _pendingAutoPrompt.value = null

        if (info == null || !info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)) {
            if (lastCheckWasUserInitiated) {
                _uiState.value = AppUpdateUiState.Failed(canOpenPlayStoreFallback = true)
            }
            return false
        }

        activeFlowVersionCode = info.availableVersionCode()
        registerInstallListenerIfNeeded()

        val started = runCatching {
            appUpdateManager.startUpdateFlowForResult(
                info,
                launcher,
                AppUpdateOptions.defaultOptions(AppUpdateType.FLEXIBLE),
            )
        }.getOrDefault(false)

        if (!started) {
            unregisterInstallListenerIfNeeded()
            if (lastCheckWasUserInitiated) {
                _uiState.value = AppUpdateUiState.Failed(canOpenPlayStoreFallback = true)
            }
        }
        return started
    }

    override suspend fun onUpdateFlowResult(resultCode: Int) {
        _pendingAutoPrompt.value = null
        when (resultCode) {
            Activity.RESULT_OK -> {
                registerInstallListenerIfNeeded()
                val current = _uiState.value
                if (current !is AppUpdateUiState.Downloading && current !is AppUpdateUiState.ReadyToInstall) {
                    _uiState.value = AppUpdateUiState.Downloading(
                        bytesDownloaded = 0L,
                        totalBytes = 0L,
                    )
                }
            }
            Activity.RESULT_CANCELED -> {
                unregisterInstallListenerIfNeeded()
                val dismissedCode = activeFlowVersionCode
                if (dismissedCode > 0) {
                    userPreferencesRepository.setDismissedUpdateVersionCode(dismissedCode)
                    _uiState.value = AppUpdateUiState.UpdateAvailable(
                        currentVersionCode = currentVersionCode,
                        targetVersionCode = dismissedCode,
                        isPlayStoreManaged = true,
                        downloadPageUrl = null,
                    )
                } else {
                    _uiState.value = AppUpdateUiState.Idle
                }
            }
            else -> {
                unregisterInstallListenerIfNeeded()
                if (lastCheckWasUserInitiated) {
                    _uiState.value = AppUpdateUiState.Failed(canOpenPlayStoreFallback = true)
                } else if (activeFlowVersionCode > 0) {
                    _uiState.value = AppUpdateUiState.UpdateAvailable(
                        currentVersionCode = currentVersionCode,
                        targetVersionCode = activeFlowVersionCode,
                        isPlayStoreManaged = true,
                        downloadPageUrl = null,
                    )
                } else {
                    _uiState.value = AppUpdateUiState.Idle
                }
            }
        }
    }

    override fun completeUpdate() {
        runCatching {
            appUpdateManager.completeUpdate()
        }.onFailure {
            _uiState.value = AppUpdateUiState.Failed(canOpenPlayStoreFallback = true)
        }
    }

    override suspend fun dismissAutoPrompt(suppressForThisVersion: Boolean) {
        val currentPrompt = _pendingAutoPrompt.value
        _pendingAutoPrompt.value = null
        if (suppressForThisVersion && currentPrompt != null) {
            userPreferencesRepository.setDismissedUpdateVersionCode(currentPrompt.targetVersionCode)
        }
    }

    override fun clearProfileUpdateBadge() {
        _showProfileUpdateBadge.value = false
    }

    override fun dispose() {
        unregisterInstallListenerIfNeeded()
        cachedAppUpdateInfo = null
    }

    private suspend fun fetchAppUpdateInfoOrNull(): AppUpdateInfo? = try {
        appUpdateManager.requestAppUpdateInfo()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        null
    }

    private fun handleExistingInstallProgress(info: AppUpdateInfo): Boolean {
        return when (info.installStatus()) {
            InstallStatus.DOWNLOADED -> {
                _pendingAutoPrompt.value = null
                _uiState.value = AppUpdateUiState.ReadyToInstall
                _showProfileUpdateBadge.value = true
                true
            }
            InstallStatus.PENDING,
            InstallStatus.DOWNLOADING,
            -> {
                registerInstallListenerIfNeeded()
                _pendingAutoPrompt.value = null
                _uiState.value = AppUpdateUiState.Downloading(
                    bytesDownloaded = info.bytesDownloaded().coerceAtLeast(0L),
                    totalBytes = info.totalBytesToDownload().coerceAtLeast(0L),
                )
                true
            }
            else -> false
        }
    }

    private fun handleInstallStateUpdate(state: InstallState) {
        when (state.installStatus()) {
            InstallStatus.PENDING -> {
                _uiState.value = AppUpdateUiState.Downloading(
                    bytesDownloaded = 0L,
                    totalBytes = state.totalBytesToDownload().coerceAtLeast(0L),
                )
            }
            InstallStatus.DOWNLOADING -> {
                _uiState.value = AppUpdateUiState.Downloading(
                    bytesDownloaded = state.bytesDownloaded().coerceAtLeast(0L),
                    totalBytes = state.totalBytesToDownload().coerceAtLeast(0L),
                )
            }
            InstallStatus.DOWNLOADED -> {
                _pendingAutoPrompt.value = null
                _uiState.value = AppUpdateUiState.ReadyToInstall
                _showProfileUpdateBadge.value = true
            }
            InstallStatus.INSTALLED -> {
                unregisterInstallListenerIfNeeded()
                _pendingAutoPrompt.value = null
                _showProfileUpdateBadge.value = false
                _uiState.value = AppUpdateUiState.Idle
            }
            InstallStatus.CANCELED -> {
                unregisterInstallListenerIfNeeded()
                if (activeFlowVersionCode > 0) {
                    _uiState.value = AppUpdateUiState.UpdateAvailable(
                        currentVersionCode = currentVersionCode,
                        targetVersionCode = activeFlowVersionCode,
                        isPlayStoreManaged = true,
                        downloadPageUrl = null,
                    )
                } else {
                    _uiState.value = AppUpdateUiState.Idle
                }
            }
            InstallStatus.FAILED -> {
                unregisterInstallListenerIfNeeded()
                _uiState.value = AppUpdateUiState.Failed(canOpenPlayStoreFallback = true)
            }
            else -> Unit
        }
    }

    private fun registerInstallListenerIfNeeded() {
        if (!isListenerRegistered) {
            isListenerRegistered = true
            runCatching { appUpdateManager.registerListener(installStateListener) }
        }
    }

    private fun unregisterInstallListenerIfNeeded() {
        if (isListenerRegistered) {
            isListenerRegistered = false
            runCatching { appUpdateManager.unregisterListener(installStateListener) }
        }
    }
}
