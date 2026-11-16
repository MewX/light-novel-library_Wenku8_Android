package org.mewx.wenku8.v2.ui.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mewx.wenku8.v2.data.remote.diagnostics.ComparisonAxis
import org.mewx.wenku8.v2.data.remote.diagnostics.DiagnosticsEndpointResolution
import org.mewx.wenku8.v2.data.remote.diagnostics.DiagnosticsLiveProgress
import org.mewx.wenku8.v2.data.remote.diagnostics.DiagnosticsReport
import org.mewx.wenku8.v2.data.remote.diagnostics.DiagnosticsRunner
import org.mewx.wenku8.v2.data.remote.diagnostics.DiagnosticsStopReason
import org.mewx.wenku8.v2.data.remote.diagnostics.NetworkConnectivityObserver
import org.mewx.wenku8.v2.data.remote.diagnostics.NetworkSnapshot
import org.mewx.wenku8.v2.data.remote.diagnostics.ReadOnlyApiDiagnosticCapability
import org.mewx.wenku8.v2.data.remote.diagnostics.RelayRouteController
import org.mewx.wenku8.v2.data.remote.diagnostics.StubSafeReadOnlyApiCapability

sealed interface DiagnosticsRunUiState {
    data object Idle : DiagnosticsRunUiState
    data class Running(val progress: DiagnosticsLiveProgress) : DiagnosticsRunUiState
    data class Finished(val report: DiagnosticsReport) : DiagnosticsRunUiState
}

/**
 * Lifecycle-owned ViewModel for `NetworkDiagnosticsScreen` (`11-network-diagnostics-and-auto-routing-ui.md` §4.2).
 *
 * - Survives Activity configuration changes (rotation, foldable posture, SC/TC locale changes).
 * - Enforces strict mutual exclusion against [RelayRouteController]'s automatic background probes.
 * - Cancels an active run with [DiagnosticsStopReason.BACKGROUNDED] when the host Activity stops
 *   without configuration change (Home, screen lock), while preserving completed reports across
 *   Android's Share Sheet chooser.
 */
class NetworkDiagnosticsViewModel(
    private val endpointResolutionProvider: () -> DiagnosticsEndpointResolution,
    private val diagnosticsRunner: DiagnosticsRunner,
    private val relayRouteController: RelayRouteController,
    private val networkObserver: NetworkConnectivityObserver,
    val readOnlyApiCapability: ReadOnlyApiDiagnosticCapability = StubSafeReadOnlyApiCapability,
    private val quickCheckInterSampleGapMillis: Long = DiagnosticsRunner.DEFAULT_QUICK_CHECK_GAP_MS,
    private val comparisonInterSampleGapMillis: Long = 500L,
) : ViewModel() {

    private val _networkSnapshot = MutableStateFlow(networkObserver.currentSnapshot())
    val networkSnapshot: StateFlow<NetworkSnapshot> = _networkSnapshot.asStateFlow()

    private val _runState = MutableStateFlow<DiagnosticsRunUiState>(DiagnosticsRunUiState.Idle)
    val runState: StateFlow<DiagnosticsRunUiState> = _runState.asStateFlow()

    val autoRoutingState = relayRouteController.uiState

    private var activeRunJob: Job? = null
    @Volatile private var pendingStopReason: DiagnosticsStopReason = DiagnosticsStopReason.USER_CANCELLED

    init {
        viewModelScope.launch {
            networkObserver.observeDefaultNetworkSnapshots().collect { snapshot ->
                _networkSnapshot.value = snapshot
            }
        }
    }

    val isServiceConfigured: Boolean
        get() = endpointResolutionProvider() is DiagnosticsEndpointResolution.Available

    fun startQuickCheck() {
        if (activeRunJob?.isActive == true) return
        val resolution = endpointResolutionProvider()
        if (resolution !is DiagnosticsEndpointResolution.Available) return

        pendingStopReason = DiagnosticsStopReason.USER_CANCELLED
        activeRunJob = viewModelScope.launch {
            relayRouteController.onManualDiagnosticsStarted()
            try {
                val report = diagnosticsRunner.runQuickCheck(
                    endpoint = resolution.endpoint,
                    activeCandidate = relayRouteController.currentPinnedCandidateOrNull(),
                    manifestRevision = relayRouteController.currentManifestRevisionOrNull(),
                    interSampleGapMillis = quickCheckInterSampleGapMillis,
                    stopReasonOnCancellation = { pendingStopReason },
                    onProgress = { progress ->
                        _runState.value = DiagnosticsRunUiState.Running(progress)
                    },
                )
                _runState.value = DiagnosticsRunUiState.Finished(report)
            } finally {
                relayRouteController.onManualDiagnosticsFinished()
            }
        }
    }

    fun startComparisonLab(axis: ComparisonAxis) {
        if (activeRunJob?.isActive == true) return
        val resolution = endpointResolutionProvider()
        if (resolution !is DiagnosticsEndpointResolution.Available) return

        pendingStopReason = DiagnosticsStopReason.USER_CANCELLED
        activeRunJob = viewModelScope.launch {
            relayRouteController.onManualDiagnosticsStarted()
            try {
                val report = diagnosticsRunner.runSingleAxisComparison(
                    endpoint = resolution.endpoint,
                    axis = axis,
                    interSampleGapMillis = comparisonInterSampleGapMillis,
                    stopReasonOnCancellation = { pendingStopReason },
                    onProgress = { progress ->
                        _runState.value = DiagnosticsRunUiState.Running(progress)
                    },
                )
                _runState.value = DiagnosticsRunUiState.Finished(report)
            } finally {
                relayRouteController.onManualDiagnosticsFinished()
            }
        }
    }

    fun stopActiveRunByUser() {
        val job = activeRunJob ?: return
        if (job.isActive) {
            pendingStopReason = DiagnosticsStopReason.USER_CANCELLED
            job.cancel()
        }
    }

    /**
     * Invoked from `Lifecycle.Event.ON_STOP` ONLY when `!activity.isChangingConfigurations`.
     * Stops an active probe run while keeping any already-finished report intact.
     */
    fun onHostStoppedNotChangingConfigurations() {
        val job = activeRunJob ?: return
        if (job.isActive) {
            pendingStopReason = DiagnosticsStopReason.BACKGROUNDED
            job.cancel()
        }
    }

    fun resetDefaultAndReevaluateRoute() {
        if (activeRunJob?.isActive == true) return
        relayRouteController.resetToDefaultAndReevaluate(triggerTournamentNow = true)
    }

    override fun onCleared() {
        activeRunJob?.cancel()
        super.onCleared()
    }

    class Factory(
        private val endpointResolutionProvider: () -> DiagnosticsEndpointResolution,
        private val diagnosticsRunner: DiagnosticsRunner,
        private val relayRouteController: RelayRouteController,
        private val networkObserver: NetworkConnectivityObserver,
        private val readOnlyApiCapability: ReadOnlyApiDiagnosticCapability = StubSafeReadOnlyApiCapability,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return NetworkDiagnosticsViewModel(
                endpointResolutionProvider = endpointResolutionProvider,
                diagnosticsRunner = diagnosticsRunner,
                relayRouteController = relayRouteController,
                networkObserver = networkObserver,
                readOnlyApiCapability = readOnlyApiCapability,
            ) as T
        }
    }
}
