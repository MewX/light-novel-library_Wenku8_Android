package org.mewx.wenku8.data.update

import android.content.ContentValues
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mewx.wenku8.data.local.preferences.UserPreferencesRepository
import org.mewx.wenku8.data.remote.transport.WenkuTransport
import org.mewx.wenku8.data.repository.ServerNoticeAndUpdateRepository
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalCoroutinesApi::class)
class DirectAppUpdateCoordinatorTest {

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

    @Test
    fun silentColdStartThrottlesBy24HoursSuppressesDismissedVersionAndRePromptsOnNewerVersion() =
        runTest(UnconfinedTestDispatcher()) {
            var nowMs = 10_000_000L
            var remoteVersion: String? = "105"
            var networkCalls = 0
            val transport = FakeVersionTransport {
                networkCalls++
                remoteVersion?.toByteArray(StandardCharsets.UTF_8)
            }
            val noticeRepo = ServerNoticeAndUpdateRepository(
                transport = transport,
                assetRootsProvider = { emptyList() },
                ioDispatcher = UnconfinedTestDispatcher(),
            )
            val prefsRepo = createPrefs(this, "direct_throttle")
            val coordinator = DirectAppUpdateCoordinator(
                serverNoticeAndUpdateRepository = noticeRepo,
                userPreferencesRepository = prefsRepo,
                currentVersionCode = 101,
                clockEpochMs = { nowMs },
            )

            assertFalse(coordinator.isPlayStoreChannel)

            // 1. First cold-start silent check detects build 105 -> badge + dialog prompt
            coordinator.checkForUpdate(isUserInitiated = false)
            assertEquals(1, networkCalls)
            val state1 = coordinator.uiState.value as AppUpdateUiState.UpdateAvailable
            assertEquals(101, state1.currentVersionCode)
            assertEquals(105, state1.targetVersionCode)
            assertFalse(state1.isPlayStoreManaged)
            assertEquals(ServerNoticeAndUpdateRepository.BLOG_DOWNLOAD_URL, state1.downloadPageUrl)
            assertTrue(coordinator.showProfileUpdateBadge.value)
            val prompt1 = coordinator.pendingAutoPrompt.value as AppUpdateAutoPrompt.DirectDownloadDialog
            assertEquals(105, prompt1.targetVersionCode)

            // 2. Dismiss prompt -> persists dismissed version 105; clearing badge hides red dot
            coordinator.dismissAutoPrompt(suppressForThisVersion = true)
            assertNull(coordinator.pendingAutoPrompt.value)
            assertEquals(105, prefsRepo.preferencesFlow.first().dismissedUpdateVersionCode)
            coordinator.clearProfileUpdateBadge()
            assertFalse(coordinator.showProfileUpdateBadge.value)

            // 3. Another silent check within 24h is throttled -> zero extra network hits
            nowMs += AppUpdateCoordinator.AUTO_CHECK_INTERVAL_MS - 1_000L
            coordinator.checkForUpdate(isUserInitiated = false)
            assertEquals(1, networkCalls)
            assertNull(coordinator.pendingAutoPrompt.value)

            // 4. After 24h, same build 105 stays suppressed (no auto dialog, no badge re-trigger)
            nowMs += 2_000L
            coordinator.checkForUpdate(isUserInitiated = false)
            assertEquals(2, networkCalls)
            assertNull(coordinator.pendingAutoPrompt.value)
            assertFalse(coordinator.showProfileUpdateBadge.value)

            // 5. After another 24h, build 106 is released -> auto dialog and badge appear again
            nowMs += AppUpdateCoordinator.AUTO_CHECK_INTERVAL_MS + 1_000L
            remoteVersion = "106"
            coordinator.checkForUpdate(isUserInitiated = false)
            assertEquals(3, networkCalls)
            assertTrue(coordinator.showProfileUpdateBadge.value)
            val prompt2 = coordinator.pendingAutoPrompt.value as AppUpdateAutoPrompt.DirectDownloadDialog
            assertEquals(106, prompt2.targetVersionCode)
        }

    @Test
    fun silentFailureSwallowsErrorWithoutBurningCooldownAndManualCheckBypassesAllSuppression() =
        runTest(UnconfinedTestDispatcher()) {
            var nowMs = 50_000_000L
            var remoteVersion: String? = null
            var networkCalls = 0
            val transport = FakeVersionTransport {
                networkCalls++
                remoteVersion?.toByteArray(StandardCharsets.UTF_8)
            }
            val noticeRepo = ServerNoticeAndUpdateRepository(
                transport = transport,
                assetRootsProvider = { emptyList() },
                ioDispatcher = UnconfinedTestDispatcher(),
            )
            val prefsRepo = createPrefs(this, "direct_manual")
            val coordinator = DirectAppUpdateCoordinator(
                serverNoticeAndUpdateRepository = noticeRepo,
                userPreferencesRepository = prefsRepo,
                currentVersionCode = 101,
                clockEpochMs = { nowMs },
            )

            // Silent check fails -> stays Idle and does NOT record cooldown timestamp
            coordinator.checkForUpdate(isUserInitiated = false)
            assertEquals(1, networkCalls)
            assertEquals(AppUpdateUiState.Idle, coordinator.uiState.value)
            assertEquals(0L, prefsRepo.preferencesFlow.first().lastAutoUpdateCheckEpochMs)

            // Immediately retry silently once network recovers -> NOT throttled!
            remoteVersion = "102"
            coordinator.checkForUpdate(isUserInitiated = false)
            assertEquals(2, networkCalls)
            assertNotNull(coordinator.pendingAutoPrompt.value)
            coordinator.dismissAutoPrompt(suppressForThisVersion = true)
            assertNull(coordinator.pendingAutoPrompt.value)

            // Manual check within cooldown AND for already-dismissed version 102 STILL prompts!
            nowMs += 5_000L
            coordinator.checkForUpdate(isUserInitiated = true)
            assertEquals(3, networkCalls)
            val manualPrompt = coordinator.pendingAutoPrompt.value as AppUpdateAutoPrompt.DirectDownloadDialog
            assertEquals(102, manualPrompt.targetVersionCode)

            // Manual check when up to date -> UpToDate and clears badge/prompt
            remoteVersion = "101"
            coordinator.checkForUpdate(isUserInitiated = true)
            assertEquals(AppUpdateUiState.UpToDate(101), coordinator.uiState.value)
            assertFalse(coordinator.showProfileUpdateBadge.value)
            assertNull(coordinator.pendingAutoPrompt.value)

            // Manual check when offline -> Failed with canOpenPlayStoreFallback = false
            remoteVersion = null
            coordinator.checkForUpdate(isUserInitiated = true)
            assertEquals(
                AppUpdateUiState.Failed(canOpenPlayStoreFallback = false),
                coordinator.uiState.value,
            )
        }

    private class FakeVersionTransport(
        private val onGetVersion: () -> ByteArray?,
    ) : WenkuTransport {
        override val isEndpointConfigured: Boolean = true
        override suspend fun postForm(endpointUrl: String, formParams: ContentValues): ByteArray? = null
        override suspend fun getBytes(url: String): ByteArray? =
            if (url == ServerNoticeAndUpdateRepository.VERSION_CHECK_URL) onGetVersion() else null
        override fun clearSessionCookie() = Unit
    }
}
