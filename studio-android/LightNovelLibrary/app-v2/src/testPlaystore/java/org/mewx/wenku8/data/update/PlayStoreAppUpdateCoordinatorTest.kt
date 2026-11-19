package org.mewx.wenku8.data.update

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.appupdate.testing.FakeAppUpdateManager
import com.google.android.play.core.common.IntentSenderForResultStarter
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.ActivityResult
import com.google.android.play.core.install.model.AppUpdateType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mewx.wenku8.data.local.preferences.UserPreferencesRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayStoreAppUpdateCoordinatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createPrefs(scope: TestScope, name: String): UserPreferencesRepository {
        val file = tempFolder.newFile("$name.preferences_pb")
        val store = PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = { file },
        )
        return UserPreferencesRepository(store)
    }

    private fun createNoOpLauncher(): ActivityResultLauncher<IntentSenderRequest> =
        object : ActivityResultLauncher<IntentSenderRequest>() {
            override fun launch(input: IntentSenderRequest, options: ActivityOptionsCompat?) = Unit
            override fun unregister() = Unit
            override val contract: ActivityResultContract<IntentSenderRequest, *> =
                ActivityResultContracts.StartIntentSenderForResult()
        }

    @Test
    fun flexibleUpdateEndToEndFlowTracksProgressCompletesInstallAndRecoversDownloadedOnResume() =
        runTest(UnconfinedTestDispatcher()) {
            val fakeManager = FakeAppUpdateManager(ApplicationProvider.getApplicationContext()).apply {
                setUpdateAvailable(108, AppUpdateType.FLEXIBLE)
            }
            val prefsRepo = createPrefs(this, "play_e2e")
            var nowMs = 1_000_000L
            val coordinator = PlayStoreAppUpdateCoordinator(
                appUpdateManager = fakeManager,
                userPreferencesRepository = prefsRepo,
                currentVersionCode = 101,
                clockEpochMs = { nowMs },
            )

            assertTrue(coordinator.isPlayStoreChannel)

            // 1. Cold-start silent check detects Flexible update 108
            coordinator.checkForUpdate(isUserInitiated = false)
            val available = coordinator.uiState.value as AppUpdateUiState.UpdateAvailable
            assertEquals(108, available.targetVersionCode)
            assertTrue(available.isPlayStoreManaged)
            assertNull(available.downloadPageUrl)
            assertTrue(coordinator.showProfileUpdateBadge.value)
            assertEquals(
                AppUpdateAutoPrompt.PlayStoreFlexible(108),
                coordinator.pendingAutoPrompt.value,
            )

            // 2. Launch Play Flexible flow and accept
            val launcher = createNoOpLauncher()
            assertTrue(coordinator.startFlexibleUpdate(launcher))
            assertNull(coordinator.pendingAutoPrompt.value)
            assertTrue(fakeManager.isConfirmationDialogVisible)

            fakeManager.userAcceptsUpdate()
            coordinator.onUpdateFlowResult(Activity.RESULT_OK)
            fakeManager.downloadStarts()
            fakeManager.setTotalBytesToDownload(10_000L)
            fakeManager.setBytesDownloaded(4_000L)

            val downloading = coordinator.uiState.value as AppUpdateUiState.Downloading
            assertEquals(4_000L, downloading.bytesDownloaded)
            assertEquals(10_000L, downloading.totalBytes)
            assertEquals(0.4f, downloading.progressFraction ?: 0f, 0.001f)

            // 3. Background download completes -> ReadyToInstall + Profile badge
            fakeManager.downloadCompletes()
            assertEquals(AppUpdateUiState.ReadyToInstall, coordinator.uiState.value)
            assertTrue(coordinator.showProfileUpdateBadge.value)

            // 4. Even if badge is cleared and host resumes, ReadyToInstall is preserved/re-surfaced
            coordinator.clearProfileUpdateBadge()
            coordinator.onHostResumed()
            assertEquals(AppUpdateUiState.ReadyToInstall, coordinator.uiState.value)
            assertTrue(coordinator.showProfileUpdateBadge.value)

            // 5. Complete update triggers Play install splash screen -> INSTALLED resets state
            coordinator.completeUpdate()
            assertTrue(fakeManager.isInstallSplashScreenVisible)
            fakeManager.installCompletes()
            assertEquals(AppUpdateUiState.Idle, coordinator.uiState.value)
            assertFalse(coordinator.showProfileUpdateBadge.value)

            coordinator.dispose()
        }

    @Test
    fun canceledPlaySheetSuppressesFutureAutoPromptsWhileManualCheckStillTriggers() =
        runTest(UnconfinedTestDispatcher()) {
            val fakeManager = FakeAppUpdateManager(ApplicationProvider.getApplicationContext()).apply {
                setUpdateAvailable(109, AppUpdateType.FLEXIBLE)
            }
            val prefsRepo = createPrefs(this, "play_cancel")
            var nowMs = 20_000_000L
            val coordinator = PlayStoreAppUpdateCoordinator(
                appUpdateManager = fakeManager,
                userPreferencesRepository = prefsRepo,
                currentVersionCode = 101,
                clockEpochMs = { nowMs },
            )

            coordinator.checkForUpdate(isUserInitiated = false)
            assertTrue(coordinator.startFlexibleUpdate(createNoOpLauncher()))
            fakeManager.userRejectsUpdate()
            coordinator.onUpdateFlowResult(Activity.RESULT_CANCELED)

            // Version 109 is now persisted as dismissed while card still knows update is available
            assertEquals(109, prefsRepo.preferencesFlow.first().dismissedUpdateVersionCode)
            assertTrue(coordinator.uiState.value is AppUpdateUiState.UpdateAvailable)

            // After 24h cooldown, silent check does NOT re-pop Play sheet for dismissed version 109
            nowMs += AppUpdateCoordinator.AUTO_CHECK_INTERVAL_MS + 1_000L
            coordinator.clearProfileUpdateBadge()
            coordinator.checkForUpdate(isUserInitiated = false)
            assertNull(coordinator.pendingAutoPrompt.value)
            assertFalse(coordinator.showProfileUpdateBadge.value)

            // Manual check bypasses suppression and requests a fresh AppUpdateInfo
            coordinator.checkForUpdate(isUserInitiated = true)
            assertEquals(
                AppUpdateAutoPrompt.PlayStoreFlexible(109),
                coordinator.pendingAutoPrompt.value,
            )

            // Play failure result on manual check surfaces Play Store fallback error
            assertTrue(coordinator.startFlexibleUpdate(createNoOpLauncher()))
            coordinator.onUpdateFlowResult(ActivityResult.RESULT_IN_APP_UPDATE_FAILED)
            assertEquals(
                AppUpdateUiState.Failed(canOpenPlayStoreFallback = true),
                coordinator.uiState.value,
            )

            coordinator.dispose()
        }

    @Test
    fun playStoreUnavailableSwallowsErrorSilentlyOnColdStartAndOffersStoreFallbackOnManualCheck() =
        runTest(UnconfinedTestDispatcher()) {
            val failingManager = FailingAppUpdateManager()
            val prefsRepo = createPrefs(this, "play_fail")
            val coordinator = PlayStoreAppUpdateCoordinator(
                appUpdateManager = failingManager,
                userPreferencesRepository = prefsRepo,
                currentVersionCode = 101,
            )

            // Cold start -> silent Idle, zero cooldown burned
            coordinator.checkForUpdate(isUserInitiated = false)
            assertEquals(AppUpdateUiState.Idle, coordinator.uiState.value)
            assertNull(coordinator.pendingAutoPrompt.value)
            assertEquals(0L, prefsRepo.preferencesFlow.first().lastAutoUpdateCheckEpochMs)

            // Manual check -> Failed with canOpenPlayStoreFallback = true
            coordinator.checkForUpdate(isUserInitiated = true)
            assertEquals(
                AppUpdateUiState.Failed(canOpenPlayStoreFallback = true),
                coordinator.uiState.value,
            )

            // Resume swallows errors cleanly
            coordinator.onHostResumed()
            assertEquals(
                AppUpdateUiState.Failed(canOpenPlayStoreFallback = true),
                coordinator.uiState.value,
            )
        }

    @Test
    fun manualCheckReportsUpToDateWhenPlayHasNoUpdate() = runTest(UnconfinedTestDispatcher()) {
        val fakeManager = FakeAppUpdateManager(ApplicationProvider.getApplicationContext()).apply {
            setUpdateNotAvailable()
        }
        val prefsRepo = createPrefs(this, "play_uptodate")
        val coordinator = PlayStoreAppUpdateCoordinator(
            appUpdateManager = fakeManager,
            userPreferencesRepository = prefsRepo,
            currentVersionCode = 101,
        )

        coordinator.checkForUpdate(isUserInitiated = true)
        assertEquals(AppUpdateUiState.UpToDate(101), coordinator.uiState.value)
        assertFalse(coordinator.showProfileUpdateBadge.value)
        assertNull(coordinator.pendingAutoPrompt.value)
    }

    private class FailingAppUpdateManager : AppUpdateManager {
        override fun getAppUpdateInfo(): Task<AppUpdateInfo> =
            Tasks.forException(IllegalStateException("Play Store unavailable"))

        override fun completeUpdate(): Task<Void> =
            Tasks.forException(IllegalStateException("Play Store unavailable"))

        override fun registerListener(listener: InstallStateUpdatedListener) = Unit
        override fun unregisterListener(listener: InstallStateUpdatedListener) = Unit

        @Deprecated("Deprecated in Play Core")
        override fun startUpdateFlowForResult(
            appUpdateInfo: AppUpdateInfo,
            appUpdateType: Int,
            activity: Activity,
            requestCode: Int,
        ): Boolean = false

        @Deprecated("Deprecated in Play Core")
        override fun startUpdateFlowForResult(
            appUpdateInfo: AppUpdateInfo,
            appUpdateType: Int,
            starter: IntentSenderForResultStarter,
            requestCode: Int,
        ): Boolean = false

        override fun startUpdateFlowForResult(
            appUpdateInfo: AppUpdateInfo,
            activity: Activity,
            options: AppUpdateOptions,
            requestCode: Int,
        ): Boolean = false

        override fun startUpdateFlowForResult(
            appUpdateInfo: AppUpdateInfo,
            starter: IntentSenderForResultStarter,
            options: AppUpdateOptions,
            requestCode: Int,
        ): Boolean = false

        override fun startUpdateFlowForResult(
            appUpdateInfo: AppUpdateInfo,
            activityResultLauncher: ActivityResultLauncher<IntentSenderRequest>,
            options: AppUpdateOptions,
        ): Boolean = false

        override fun startUpdateFlow(
            appUpdateInfo: AppUpdateInfo,
            activity: Activity,
            options: AppUpdateOptions,
        ): Task<Int> = Tasks.forException(IllegalStateException("Play Store unavailable"))
    }
}
