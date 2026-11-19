package org.mewx.wenku8.data.update

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import org.mewx.wenku8.BuildConfig
import org.mewx.wenku8.data.local.preferences.UserPreferencesRepository
import org.mewx.wenku8.data.repository.AppVersionCheckResult
import org.mewx.wenku8.data.repository.ServerNoticeAndUpdateRepository

/**
 * Direct-distribution (`default` flavor) implementation of [AppUpdateCoordinator].
 *
 * Uses [ServerNoticeAndUpdateRepository.checkForAppUpdate] (`https://wenku8.mewx.org/version`)
 * with 24-hour cold-start throttling, once-per-version auto-dialog suppression, and Profile
 * navigation badge signaling while preserving the external release-page download flow.
 */
class DirectAppUpdateCoordinator(
    private val serverNoticeAndUpdateRepository: ServerNoticeAndUpdateRepository,
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

    override val isPlayStoreChannel: Boolean = false

    override suspend fun checkForUpdate(isUserInitiated: Boolean) {
        if (!isUserInitiated) {
            val prefs = userPreferencesRepository.preferencesFlow.first()
            val nowMs = clockEpochMs()
            if (prefs.lastAutoUpdateCheckEpochMs > 0L &&
                nowMs - prefs.lastAutoUpdateCheckEpochMs in 0L until AppUpdateCoordinator.AUTO_CHECK_INTERVAL_MS
            ) {
                return
            }

            when (val result = serverNoticeAndUpdateRepository.checkForAppUpdate(currentVersionCode)) {
                is AppVersionCheckResult.UpdateAvailable -> {
                    userPreferencesRepository.recordAutoUpdateCheck(nowMs)
                    _uiState.value = AppUpdateUiState.UpdateAvailable(
                        currentVersionCode = result.currentVersionCode,
                        targetVersionCode = result.latestVersionCode,
                        isPlayStoreManaged = false,
                        downloadPageUrl = result.downloadPageUrl,
                    )
                    if (result.latestVersionCode > prefs.dismissedUpdateVersionCode) {
                        _showProfileUpdateBadge.value = true
                        _pendingAutoPrompt.value = AppUpdateAutoPrompt.DirectDownloadDialog(
                            currentVersionCode = result.currentVersionCode,
                            targetVersionCode = result.latestVersionCode,
                            downloadPageUrl = result.downloadPageUrl,
                        )
                    }
                }
                is AppVersionCheckResult.UpToDate -> {
                    userPreferencesRepository.recordAutoUpdateCheck(nowMs)
                    _showProfileUpdateBadge.value = false
                }
                AppVersionCheckResult.NetworkOrParseFailure -> {
                    // Silent cold-start checks swallow transient network failures without burning cooldown.
                }
            }
            return
        }

        _uiState.value = AppUpdateUiState.Checking
        when (val result = serverNoticeAndUpdateRepository.checkForAppUpdate(currentVersionCode)) {
            is AppVersionCheckResult.UpdateAvailable -> {
                _uiState.value = AppUpdateUiState.UpdateAvailable(
                    currentVersionCode = result.currentVersionCode,
                    targetVersionCode = result.latestVersionCode,
                    isPlayStoreManaged = false,
                    downloadPageUrl = result.downloadPageUrl,
                )
                _pendingAutoPrompt.value = AppUpdateAutoPrompt.DirectDownloadDialog(
                    currentVersionCode = result.currentVersionCode,
                    targetVersionCode = result.latestVersionCode,
                    downloadPageUrl = result.downloadPageUrl,
                )
            }
            is AppVersionCheckResult.UpToDate -> {
                _uiState.value = AppUpdateUiState.UpToDate(result.currentVersionCode)
                _showProfileUpdateBadge.value = false
                _pendingAutoPrompt.value = null
            }
            AppVersionCheckResult.NetworkOrParseFailure -> {
                _uiState.value = AppUpdateUiState.Failed(canOpenPlayStoreFallback = false)
            }
        }
    }

    override suspend fun onHostResumed() = Unit

    override fun startFlexibleUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>): Boolean = false

    override suspend fun onUpdateFlowResult(resultCode: Int) = Unit

    override fun completeUpdate() = Unit

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

    override fun dispose() = Unit
}
