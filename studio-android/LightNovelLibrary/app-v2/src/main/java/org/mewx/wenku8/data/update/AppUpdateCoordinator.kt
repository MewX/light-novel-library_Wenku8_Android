package org.mewx.wenku8.data.update

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.flow.StateFlow

/**
 * Channel-agnostic UI state for Wenku8 `:app-v2` application updates.
 *
 * - On the `playstore` flavor, states are driven exclusively by Google Play In-App Updates
 *   (`AppUpdateType.FLEXIBLE`) with zero external APK links.
 * - On the `default` flavor, states are driven by `ServerNoticeAndUpdateRepository` (`/version`)
 *   and direct release-page links (`https://wenku8.mewx.org/`).
 */
sealed interface AppUpdateUiState {
    data object Idle : AppUpdateUiState

    data object Checking : AppUpdateUiState

    data class UpToDate(
        val currentVersionCode: Int,
    ) : AppUpdateUiState

    data class UpdateAvailable(
        val currentVersionCode: Int,
        val targetVersionCode: Int,
        val isPlayStoreManaged: Boolean,
        val downloadPageUrl: String? = null,
    ) : AppUpdateUiState

    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long,
    ) : AppUpdateUiState {
        val progressFraction: Float?
            get() = if (totalBytes > 0L) {
                (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
            } else {
                null
            }
    }

    data object ReadyToInstall : AppUpdateUiState

    data class Failed(
        val canOpenPlayStoreFallback: Boolean,
    ) : AppUpdateUiState
}

/**
 * One-shot update prompt surfaced once the user is on the root home shell (never interrupting
 * an active Reader or deep-linked screen).
 */
sealed interface AppUpdateAutoPrompt {
    val targetVersionCode: Int

    data class PlayStoreFlexible(
        override val targetVersionCode: Int,
    ) : AppUpdateAutoPrompt

    data class DirectDownloadDialog(
        val currentVersionCode: Int,
        override val targetVersionCode: Int,
        val downloadPageUrl: String,
    ) : AppUpdateAutoPrompt
}

/**
 * Unified application update coordinator implemented separately per distribution flavor
 * (`playstore` vs `default`) via Gradle flavor source sets.
 */
interface AppUpdateCoordinator {
    val uiState: StateFlow<AppUpdateUiState>
    val pendingAutoPrompt: StateFlow<AppUpdateAutoPrompt?>
    val showProfileUpdateBadge: StateFlow<Boolean>
    val isPlayStoreChannel: Boolean

    /**
     * Checks for an app update.
     * - Silent checks (`isUserInitiated = false`) obey [AUTO_CHECK_INTERVAL_MS] throttling and
     *   never surface transient failures or up-to-date notices.
     * - Manual checks (`isUserInitiated = true`) bypass throttling and dismissed-version state.
     */
    suspend fun checkForUpdate(isUserInitiated: Boolean)

    /**
     * Reconciles any in-progress or completed Flexible update when the host Activity resumes
     * (e.g. Play background download completed while the app was backgrounded).
     */
    suspend fun onHostResumed()

    /**
     * Launches the Play Flexible In-App Update flow using [launcher].
     * Always returns `false` on non-Play flavors.
     */
    fun startFlexibleUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>): Boolean

    /**
     * Processes the Activity result returned from Play's Flexible In-App Update sheet.
     */
    suspend fun onUpdateFlowResult(resultCode: Int)

    /**
     * Completes a downloaded Play Flexible update (installs and restarts the app).
     */
    fun completeUpdate()

    /**
     * Dismisses the currently pending prompt. When [suppressForThisVersion] is `true`, persists
     * the target version code so future cold starts do not auto-prompt again for the same release.
     */
    suspend fun dismissAutoPrompt(suppressForThisVersion: Boolean = true)

    /**
     * Clears the Profile navigation badge once the user visits the Profile tab.
     */
    fun clearProfileUpdateBadge()

    /**
     * Releases any platform listeners attached to the underlying update manager.
     */
    fun dispose()

    companion object {
        const val AUTO_CHECK_INTERVAL_MS: Long = 24L * 60L * 60L * 1000L
        const val PLAY_STORE_MARKET_URI_PREFIX: String = "market://details?id="
        const val PLAY_STORE_WEB_URL_PREFIX: String = "https://play.google.com/store/apps/details?id="
    }
}
