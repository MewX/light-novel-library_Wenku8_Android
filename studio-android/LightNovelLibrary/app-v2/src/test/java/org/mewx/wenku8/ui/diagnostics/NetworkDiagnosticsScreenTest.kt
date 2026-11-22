package org.mewx.wenku8.ui.diagnostics

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToKey
import androidx.compose.ui.test.performTextReplacement
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mewx.wenku8.data.remote.diagnostics.DiagnosticsEndpointConfig
import org.mewx.wenku8.data.remote.diagnostics.DiagnosticsEndpointResolution
import org.mewx.wenku8.data.remote.diagnostics.DiagnosticsProbeTransport
import org.mewx.wenku8.data.remote.diagnostics.DiagnosticsReport
import org.mewx.wenku8.data.remote.diagnostics.DiagnosticsRunner
import org.mewx.wenku8.data.remote.diagnostics.IpAddressFamily
import org.mewx.wenku8.data.remote.diagnostics.ManifestRepository
import org.mewx.wenku8.data.remote.diagnostics.NetworkConnectivityObserver
import org.mewx.wenku8.data.remote.diagnostics.NetworkSnapshot
import org.mewx.wenku8.data.remote.diagnostics.ParsedServerProbePayload
import org.mewx.wenku8.data.remote.diagnostics.ProbeArmConfig
import org.mewx.wenku8.data.remote.diagnostics.ProbeErrorCategory
import org.mewx.wenku8.data.remote.diagnostics.ProbeSampleResult
import org.mewx.wenku8.data.remote.diagnostics.ReadOnlyApiDiagnosticCapability
import org.mewx.wenku8.data.remote.diagnostics.RelayRouteController
import org.mewx.wenku8.data.remote.diagnostics.RoutingLocalStore
import org.mewx.wenku8.data.remote.diagnostics.UnderlyingTransportKind
import org.mewx.wenku8.data.remote.diagnostics.ValidatedRelayEndpoint
import org.mewx.wenku8.ui.theme.Wenku8ExpressiveTheme
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h892dp")
class NetworkDiagnosticsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var appScope: CoroutineScope
    private lateinit var routingLocalStore: RoutingLocalStore

    private val availableResolution =
        DiagnosticsEndpointConfig.parseAndValidate("https://wenku8-relay.mewx.org/")
            as DiagnosticsEndpointResolution.Available

    @Before
    fun setUp() {
        appScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val dsFile = File(tempFolder.newFolder("datastore"), "ui_routing.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = appScope,
            produceFile = { dsFile },
        )
        routingLocalStore = RoutingLocalStore(dataStore)
    }

    @After
    fun tearDown() {
        appScope.cancel()
    }

    @Test
    fun quickCheckRendersMetricsReuseBadgesCopySummaryAndPreShareSheetExport() {
        val fakeTransport = fakeProbeTransport { sampleIndex, nonce ->
            if (sampleIndex == 6) {
                buildSample(
                    sampleIndex = sampleIndex,
                    succeeded = false,
                    reused = false,
                    elapsedMs = 5002L,
                    errorCategory = ProbeErrorCategory.READ_TIMEOUT,
                    nonce = nonce ?: "n-$sampleIndex",
                )
            } else {
                buildSample(
                    sampleIndex = sampleIndex,
                    succeeded = true,
                    reused = sampleIndex > 1,
                    elapsedMs = if (sampleIndex == 1) 260L else 180L,
                    errorCategory = null,
                    nonce = nonce ?: "n-$sampleIndex",
                )
            }
        }

        val viewModel = createViewModel(
            resolution = availableResolution,
            probeTransport = fakeTransport,
            isApiSupported = false,
        )

        var exportedPair: Pair<DiagnosticsReport, String?>? = null
        composeTestRule.setContent {
            Wenku8ExpressiveTheme {
                NetworkDiagnosticsScreen(
                    viewModel = viewModel,
                    autoOptimizeEnabled = true,
                    onAutoOptimizeChanged = {},
                    onNavigateBack = {},
                    onExportReportOverride = { report, note ->
                        exportedPair = report to note
                    },
                )
            }
        }

        // Start Quick Check
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.START_QUICK_CHECK_BUTTON)
            .assertIsDisplayed()
            .performClick()
        waitForTag(NetworkDiagnosticsTestTags.METRIC_SUCCESS_VALUE)

        // Verify 9 / 10 summary metrics
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.METRIC_SUCCESS_VALUE)
            .assertTextEquals("9 / 10 次")
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.METRIC_MEDIAN_FULL_VALUE)
            .assertTextEquals("180 ms")
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.METRIC_MIN_MAX_VALUE)
            .assertTextEquals("180 ms / 260 ms")

        // Copy summary to clipboard
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.COPY_SUMMARY_BUTTON)
            .performScrollTo()
            .performClick()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val copiedText = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
        assertTrue(copiedText.contains("9 / 10"))

        // Expand sample details and check cold vs reused vs failed badges + ingressColo
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.CONTENT_LIST)
            .performScrollToKey("sample_details_header")
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.DETAILS_TOGGLE_ROW)
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.CONTENT_LIST)
            .performScrollToKey("sample_1_default")
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.SAMPLE_ROW_PREFIX + "1")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("首请求 · 新建连接").assertIsDisplayed()

        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.CONTENT_LIST)
            .performScrollToKey("sample_2_default")
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.SAMPLE_ROW_PREFIX + "2")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.CONTENT_LIST)
            .performScrollToKey("sample_6_default")
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.SAMPLE_ROW_PREFIX + "6")
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("失败").assertIsDisplayed()

        // Open Pre-Share privacy disclosure sheet via sticky header share button
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.HEADER_SHARE_BUTTON)
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.PRE_SHARE_SHEET)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.PRE_SHARE_DISCLOSED_TEXT)
            .assertTextEquals("报告将包含观测到的国家/地区：US · 网络自治系统 (ASN)：AS13335")

        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.PRE_SHARE_NOTE_INPUT)
            .performTextReplacement("Shanghai Telecom Wi-Fi")
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.PRE_SHARE_CONFIRM_BUTTON)
            .performClick()
        composeTestRule.waitForIdle()

        val confirmedExport = exportedPair
        assertNotNull(confirmedExport)
        assertEquals("Shanghai Telecom Wi-Fi", confirmedExport?.second)

        // Verify FileProvider export helper + 24h cache purge + read-only URI permission
        val cacheRoot = tempFolder.newFolder("app_cache")
        val staleDir = File(cacheRoot, DiagnosticsReportShareHelper.CACHE_SUBDIR_NAME).apply { mkdirs() }
        val staleFile = File(staleDir, "wenku8-network-report-old.json").apply {
            writeText("{}")
            setLastModified(1_000L)
        }
        val purged = DiagnosticsReportShareHelper.purgeExpiredReportFiles(
            cacheRootDir = cacheRoot,
            nowEpochMillis = 1_000L + DiagnosticsReportShareHelper.MAX_ATTACHMENT_AGE_MILLIS + 5_000L,
        )
        assertEquals(1, purged)
        assertFalse(staleFile.exists())

        val chooser = DiagnosticsReportShareHelper.createChooserShareIntent(
            context = context,
            report = confirmedExport!!.first,
            optionalUserNote = confirmedExport.second,
            chooserTitle = "Share Diagnostics",
        )
        val innerSend = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        assertNotNull(innerSend)
        assertEquals("application/json", innerSend!!.type)
        assertNotNull(innerSend.clipData)
        assertTrue((innerSend.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0)
        assertFalse((innerSend.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION) != 0)

        val writtenFiles = File(context.cacheDir, DiagnosticsReportShareHelper.CACHE_SUBDIR_NAME).listFiles()
        assertNotNull(writtenFiles)
        assertTrue(writtenFiles!!.isNotEmpty())
        val parsedJson = JSONObject(writtenFiles.first().readText())
        assertEquals("wenku8-network-report-v1", parsedJson.getString("schema"))
        assertEquals("Shanghai Telecom Wi-Fi", parsedJson.getString("userNote"))
    }

    @Test
    fun zeroSuccessRunRendersEmDashNeverZeroMilliseconds() {
        val allFailingTransport = fakeProbeTransport { sampleIndex, nonce ->
            buildSample(
                sampleIndex = sampleIndex,
                succeeded = false,
                reused = false,
                elapsedMs = 3001L,
                errorCategory = ProbeErrorCategory.CONNECT_TIMEOUT,
                nonce = nonce ?: "fail-$sampleIndex",
            )
        }

        val viewModel = createViewModel(
            resolution = availableResolution,
            probeTransport = allFailingTransport,
            isApiSupported = false,
        )

        composeTestRule.setContent {
            Wenku8ExpressiveTheme {
                NetworkDiagnosticsScreen(
                    viewModel = viewModel,
                    autoOptimizeEnabled = true,
                    onAutoOptimizeChanged = {},
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.START_QUICK_CHECK_BUTTON)
            .performClick()
        waitForTag(NetworkDiagnosticsTestTags.METRIC_SUCCESS_VALUE)

        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.METRIC_SUCCESS_VALUE)
            .assertTextEquals("0 / 10 次")
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.METRIC_MEDIAN_FULL_VALUE)
            .assertTextEquals("—")
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.METRIC_MEDIAN_HEADERS_VALUE)
            .assertTextEquals("—")
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.METRIC_MIN_MAX_VALUE)
            .assertTextEquals("—")
    }

    @Test
    fun einkModeUsesFlatProgressBarDuringActiveRunAndStubModeDisablesControlsCleanly() {
        val gate = CompletableDeferred<Unit>()
        val blockingTransport = fakeProbeTransport { sampleIndex, nonce ->
            if (sampleIndex == 2) {
                gate.await()
            }
            buildSample(
                sampleIndex = sampleIndex,
                succeeded = true,
                reused = sampleIndex > 1,
                elapsedMs = 150L,
                errorCategory = null,
                nonce = nonce ?: "eink-$sampleIndex",
            )
        }

        val activeViewModel = createViewModel(
            resolution = availableResolution,
            probeTransport = blockingTransport,
            isApiSupported = false,
        )

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(einkModeOverride = true) {
                NetworkDiagnosticsScreen(
                    viewModel = activeViewModel,
                    autoOptimizeEnabled = true,
                    onAutoOptimizeChanged = {},
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.START_QUICK_CHECK_BUTTON)
            .performClick()
        waitForTag(NetworkDiagnosticsTestTags.FLAT_EINK_PROGRESS_INDICATOR)

        // While suspended at probe #02, E-Ink mode must render flat LinearProgressIndicator
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.FLAT_EINK_PROGRESS_INDICATOR)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.STOP_ACTIVE_RUN_BUTTON)
            .performClick()
        gate.complete(Unit)
        composeTestRule.waitForIdle()

        // Also verify UnavailableStub resolution disables controls cleanly
        val stubResolution = DiagnosticsEndpointConfig.parseAndValidate("Unknown")
        val stubViewModel = createViewModel(
            resolution = stubResolution,
            probeTransport = blockingTransport,
            isApiSupported = false,
        )
        assertFalse(stubViewModel.isServiceConfigured)
    }

    @Test
    fun stubBuildDisablesAutoOptimizeSwitchAndStartQuickCheckCleanly() {
        val stubResolution = DiagnosticsEndpointConfig.parseAndValidate("Unknown")
        val viewModel = createViewModel(
            resolution = stubResolution,
            probeTransport = fakeProbeTransport { idx, _ ->
                buildSample(idx, succeeded = false, reused = false, elapsedMs = 0L, ProbeErrorCategory.CANCELLED, "n")
            },
            isApiSupported = false,
        )

        composeTestRule.setContent {
            Wenku8ExpressiveTheme {
                NetworkDiagnosticsScreen(
                    viewModel = viewModel,
                    autoOptimizeEnabled = true,
                    onAutoOptimizeChanged = {},
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.AUTO_OPTIMIZE_SWITCH)
            .assertIsNotEnabled()
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.START_QUICK_CHECK_BUTTON)
            .assertIsNotEnabled()

        // Expand comparison lab and verify stub message for Read-Only API check
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.LAB_TOGGLE_ROW)
            .performScrollTo()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.LAB_READ_ONLY_API_STATUS)
            .performScrollTo()
            .assertTextEquals("此版本不支持实际接口检测")
    }

    @Test
    fun nonEinkWavyProgressIndicatorHandlesIncrementalProgressFramesAndCompletionCleanly() {
        val gateAtSample3 = CompletableDeferred<Unit>()
        val gateAtSample8 = CompletableDeferred<Unit>()
        val steppedTransport = fakeProbeTransport { sampleIndex, nonce ->
            when (sampleIndex) {
                3 -> gateAtSample3.await()
                8 -> gateAtSample8.await()
            }
            if (sampleIndex == 9) {
                buildSample(
                    sampleIndex = sampleIndex,
                    succeeded = false,
                    reused = false,
                    elapsedMs = 140L,
                    errorCategory = ProbeErrorCategory.HTTP_STATUS_ERROR,
                    nonce = nonce ?: "step-$sampleIndex",
                ).copy(httpStatusCode = 502)
            } else {
                buildSample(
                    sampleIndex = sampleIndex,
                    succeeded = true,
                    reused = sampleIndex > 1,
                    elapsedMs = 135L,
                    errorCategory = null,
                    nonce = nonce ?: "step-$sampleIndex",
                )
            }
        }

        val viewModel = createViewModel(
            resolution = availableResolution,
            probeTransport = steppedTransport,
            isApiSupported = true,
        )

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(einkModeOverride = false) {
                NetworkDiagnosticsScreen(
                    viewModel = viewModel,
                    autoOptimizeEnabled = true,
                    onAutoOptimizeChanged = {},
                    onNavigateBack = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.START_QUICK_CHECK_BUTTON)
            .performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule
                .onAllNodes(androidx.compose.ui.test.hasTestTag(NetworkDiagnosticsTestTags.WAVY_PROGRESS_INDICATOR))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.WAVY_PROGRESS_INDICATOR)
            .assertIsDisplayed()

        // Step from 2/10 -> 7/10 while wavy indicator animates across frames
        gateAtSample3.complete(Unit)
        composeTestRule.mainClock.advanceTimeBy(120L)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.WAVY_PROGRESS_INDICATOR)
            .assertIsDisplayed()

        // Release final gate -> complete 10th probe -> transition Running -> Finished cleanly
        gateAtSample8.complete(Unit)
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule
                .onAllNodes(androidx.compose.ui.test.hasTestTag(NetworkDiagnosticsTestTags.METRIC_SUCCESS_VALUE))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.METRIC_SUCCESS_VALUE)
            .assertTextEquals("9 / 10 次")

        // Verify HTTP status code formatting on failed sample #09 and localized Read-Only API button
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.CONTENT_LIST)
            .performScrollToKey("sample_details_header")
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.DETAILS_TOGGLE_ROW)
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.CONTENT_LIST)
            .performScrollToKey("sample_9_default")
        composeTestRule.onNodeWithText("— · http_status (HTTP 502 · 140 ms)")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.CONTENT_LIST)
            .performScrollToKey("advanced_comparison_lab")
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.LAB_TOGGLE_ROW)
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.LAB_READ_ONLY_API_BUTTON)
            .performScrollTo()
            .assertTextEquals("实际接口连通性检测（只读）")
    }

    @Test
    fun quickCheckRecordsGoodIpNearToggleAndTogglingSwitchDisplaysSelectedIpAndUsageGuide() {
        val nowWall = 1_762_740_000_000L
        val manifestRepository = ManifestRepository(
            localStore = routingLocalStore,
            wallClockEpochMillis = { nowWall },
            monotonicClockMillis = { 10_000L },
        )
        kotlinx.coroutines.runBlocking {
            manifestRepository.ingestManifestPayload(
                rawJson = """
                    {
                      "schema": "wenku8-ip-candidates-v1",
                      "revision": "rev-ui-record",
                      "hostname": "wenku8-relay.mewx.org",
                      "enabled": true,
                      "minAppVersionCode": 0,
                      "rolloutPermille": 1000,
                      "issuedAt": "2025-11-10T00:00:00Z",
                      "expiresAt": "2025-11-10T12:00:00Z",
                      "candidates": [
                        { "id": "cf-v4-slow", "ip": "104.18.32.77" },
                        { "id": "cf-v4-best", "ip": "162.159.135.99" }
                      ]
                    }
                """.trimIndent(),
                expectedRelayHostname = "wenku8-relay.mewx.org",
            )
        }

        val probeTransport = object : DiagnosticsProbeTransport {
            override suspend fun executeProbe(
                endpoint: ValidatedRelayEndpoint,
                armConfig: ProbeArmConfig,
                sampleIndex: Int,
                explicitNonce: String?,
            ): ProbeSampleResult {
                val candidateId =
                    (armConfig.dnsPolicy as? org.mewx.wenku8.data.remote.diagnostics.ProbeDnsPolicy.PinnedCandidate)?.candidateId
                val isWarm = armConfig.armId.startsWith("discover_warm:") || sampleIndex > 1 && candidateId == null
                val latency = when (candidateId) {
                    "cf-v4-best" -> if (isWarm) 86L else 94L
                    "cf-v4-slow" -> 210L
                    else -> 190L
                }
                return buildSample(
                    sampleIndex = sampleIndex,
                    succeeded = true,
                    reused = isWarm,
                    elapsedMs = latency,
                    errorCategory = null,
                    nonce = explicitNonce ?: "ui-$sampleIndex",
                ).copy(armId = armConfig.armId, candidateId = candidateId)
            }
        }

        val networkFlow = MutableStateFlow(
            NetworkSnapshot(
                internalNetworkHandleKey = "net-wifi-ui",
                isConnected = true,
                underlyingTransports = setOf(UnderlyingTransportKind.WIFI),
                isVpnActive = false,
                isMetered = false,
            ),
        )
        val observer = object : NetworkConnectivityObserver {
            override fun currentSnapshot(): NetworkSnapshot = networkFlow.value
            override fun observeDefaultNetworkSnapshots(): Flow<NetworkSnapshot> = networkFlow
        }
        val runner = DiagnosticsRunner(probeTransport = probeTransport, networkObserver = observer)
        val controller = RelayRouteController(
            applicationScope = appScope,
            endpointResolutionProvider = { availableResolution },
            probeTransport = probeTransport,
            manifestRepository = manifestRepository,
            routingLocalStore = routingLocalStore,
            networkObserver = observer,
            autoProbeGapMillis = 0L,
            initialAutoOptimizeEnabled = false,
            allowAutoSelectOnEnable = true,
            monotonicClockMillis = { 10_000L },
            wallClockEpochMillis = { nowWall },
        )
        val viewModel = NetworkDiagnosticsViewModel(
            endpointResolutionProvider = { availableResolution },
            diagnosticsRunner = runner,
            relayRouteController = controller,
            networkObserver = observer,
            quickCheckInterSampleGapMillis = 0L,
            comparisonInterSampleGapMillis = 0L,
        )

        var exportedReport: DiagnosticsReport? = null
        val toggleState = androidx.compose.runtime.mutableStateOf(false)
        composeTestRule.setContent {
            Wenku8ExpressiveTheme {
                NetworkDiagnosticsScreen(
                    viewModel = viewModel,
                    autoOptimizeEnabled = toggleState.value,
                    onAutoOptimizeChanged = { toggleState.value = it },
                    onNavigateBack = {},
                    onExportReportOverride = { report, _ -> exportedReport = report },
                )
            }
        }

        // 1. Verify Usage Guide card exists and explains how to use & relay-only scope
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.CONTENT_LIST)
            .performScrollToKey("usage_guide_card")
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.USAGE_GUIDE_CARD)
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("使用说明")
            .assertIsDisplayed()

        // 2. Scroll back up and run Quick Check while toggle is OFF
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.CONTENT_LIST)
            .performScrollToKey("quick_check_card")
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.START_QUICK_CHECK_BUTTON)
            .performClick()
        waitForTag(NetworkDiagnosticsTestTags.METRIC_SUCCESS_VALUE)

        // 3. Recorded good IP appears right near the OFF toggle!
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.CONTENT_LIST)
            .performScrollToKey("network_and_auto_routing_card")
        waitForTag(NetworkDiagnosticsTestTags.RECORDED_IP_BANNER)
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.RECORDED_IP_BANNER)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.RECORDED_IP_TEXT)
            .assertTextEquals("已记录可用优选 IP：162.159.135.99 · 90 ms · 节点 LAX")

        // 4. Flip toggle ON -> immediately activates recorded IP and shows Selected IP banner!
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.AUTO_OPTIMIZE_SWITCH)
            .performClick()
        waitForTag(NetworkDiagnosticsTestTags.SELECTED_IP_BANNER)
        assertTrue(toggleState.value)
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.SELECTED_IP_BANNER)
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.SELECTED_IP_TEXT)
            .assertTextEquals("当前生效中转 IP：162.159.135.99 · 90 ms · 节点 LAX")

        // 5. Privacy guarantee: exported JSON report still never leaks raw candidate IP literals
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.HEADER_SHARE_BUTTON)
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(NetworkDiagnosticsTestTags.PRE_SHARE_CONFIRM_BUTTON)
            .performClick()
        composeTestRule.waitForIdle()
        assertNotNull(exportedReport)
        val redactedJson = exportedReport!!.toRedactedJsonString("test-note")
        assertFalse(redactedJson.contains("162.159.135.99"))
    }

    private fun waitForTag(tag: String, timeoutMillis: Long = 5_000L) {
        composeTestRule.waitUntil(timeoutMillis = timeoutMillis) {
            composeTestRule
                .onAllNodes(androidx.compose.ui.test.hasTestTag(tag))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.waitForIdle()
    }

    private fun fakeProbeTransport(
        handler: suspend (sampleIndex: Int, explicitNonce: String?) -> ProbeSampleResult,
    ): DiagnosticsProbeTransport = object : DiagnosticsProbeTransport {
        override suspend fun executeProbe(
            endpoint: ValidatedRelayEndpoint,
            armConfig: ProbeArmConfig,
            sampleIndex: Int,
            explicitNonce: String?,
        ): ProbeSampleResult = handler(sampleIndex, explicitNonce)
    }

    private fun createViewModel(
        resolution: DiagnosticsEndpointResolution,
        probeTransport: DiagnosticsProbeTransport,
        isApiSupported: Boolean,
    ): NetworkDiagnosticsViewModel {
        val networkFlow = MutableStateFlow(
            NetworkSnapshot(
                internalNetworkHandleKey = "net-wifi-1",
                isConnected = true,
                underlyingTransports = setOf(UnderlyingTransportKind.WIFI),
                isVpnActive = false,
                isMetered = false,
            ),
        )
        val observer = object : NetworkConnectivityObserver {
            override fun currentSnapshot(): NetworkSnapshot = networkFlow.value
            override fun observeDefaultNetworkSnapshots(): Flow<NetworkSnapshot> = networkFlow
        }
        val runner = DiagnosticsRunner(
            probeTransport = probeTransport,
            networkObserver = observer,
        )
        val manifestRepository = ManifestRepository(localStore = routingLocalStore)
        val controller = RelayRouteController(
            applicationScope = appScope,
            endpointResolutionProvider = { resolution },
            probeTransport = probeTransport,
            manifestRepository = manifestRepository,
            routingLocalStore = routingLocalStore,
            networkObserver = observer,
            autoProbeGapMillis = 0L,
        )
        val apiCapability = object : ReadOnlyApiDiagnosticCapability {
            override val isSupportedInCurrentBuild: Boolean = isApiSupported
            override suspend fun runBoundedReadOnlyApiSample(): Result<Long> = Result.success(95L)
        }
        return NetworkDiagnosticsViewModel(
            endpointResolutionProvider = { resolution },
            diagnosticsRunner = runner,
            relayRouteController = controller,
            networkObserver = observer,
            readOnlyApiCapability = apiCapability,
            quickCheckInterSampleGapMillis = 0L,
            comparisonInterSampleGapMillis = 0L,
        )
    }

    private fun buildSample(
        sampleIndex: Int,
        succeeded: Boolean,
        reused: Boolean,
        elapsedMs: Long,
        errorCategory: ProbeErrorCategory?,
        nonce: String,
    ): ProbeSampleResult {
        return ProbeSampleResult(
            sampleIndex = sampleIndex,
            armId = "default",
            candidateId = null,
            succeeded = succeeded,
            httpStatusCode = if (succeeded) 200 else null,
            retryAfterSeconds = null,
            errorCategory = errorCategory,
            fullResponseElapsedMs = elapsedMs,
            responseHeadersStartMs = if (succeeded) (elapsedMs - 30L).coerceAtLeast(1L) else null,
            dnsDurationMs = if (succeeded && !reused) 14L else null,
            tcpConnectDurationMs = if (succeeded && !reused) 32L else null,
            tlsHandshakeDurationMs = if (succeeded && !reused) 68L else null,
            connectionReused = reused,
            connectAttemptCount = if (reused) 0 else 1,
            observedProxyType = Proxy.Type.DIRECT,
            localSocketFamily = IpAddressFamily.IPV6,
            negotiatedProtocol = if (succeeded) "h2" else null,
            responseBodyBytesRead = if (succeeded) 196 else 0,
            bodyComplete = succeeded,
            serverPayload = if (succeeded) {
                ParsedServerProbePayload(
                    schema = "wenku8-network-probe-v1",
                    nonce = nonce,
                    endpointRole = "cloudflare_relay",
                    ingressColo = "LAX",
                    country = "US",
                    asn = 13335L,
                    clientIpFamily = IpAddressFamily.IPV6,
                    httpProtocol = "HTTP/2",
                    transportRttMs = 28L,
                    transportRttSource = "cf_tcp_rtt",
                    handlerElapsedMs = 1L,
                )
            } else {
                null
            },
            observedEvents = if (reused) listOf("connectionAcquired") else listOf("connectStart", "connectionAcquired"),
        )
    }
}
