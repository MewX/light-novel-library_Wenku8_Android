package org.mewx.wenku8.v2.data.remote.diagnostics

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.PowerManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class UnderlyingTransportKind {
    WIFI,
    CELLULAR,
    ETHERNET,
    BLUETOOTH,
    OTHER,
}

/**
 * Privacy-safe snapshot of the active default network.
 *
 * Note: [internalNetworkHandleKey] is used strictly in-memory to detect Wi-Fi/Cellular switches
 * (`hasTransitionedFrom`) and is **never** serialized into `wenku8-network-report-v1`.
 */
data class NetworkSnapshot(
    internal val internalNetworkHandleKey: String? = null,
    val isConnected: Boolean = true,
    val underlyingTransports: Set<UnderlyingTransportKind> = setOf(UnderlyingTransportKind.WIFI),
    val isVpnActive: Boolean = false,
    val isMetered: Boolean = false,
    val isDataSaverRestricted: Boolean = false,
    val isPowerSaveMode: Boolean = false,
) {
    /**
     * Returns true when a genuine route transition occurred compared to [baseline] (ignoring
     * signal-strength, link-bandwidth, or routine capability updates on the same route).
     */
    fun hasTransitionedFrom(baseline: NetworkSnapshot): Boolean {
        if (!baseline.isConnected && !this.isConnected) return false
        if (baseline.isConnected != this.isConnected) return true
        if (baseline.internalNetworkHandleKey != null &&
            this.internalNetworkHandleKey != null &&
            baseline.internalNetworkHandleKey != this.internalNetworkHandleKey
        ) {
            return true
        }
        if (baseline.isVpnActive != this.isVpnActive) return true
        if (baseline.underlyingTransports != this.underlyingTransports) return true
        return false
    }

    /**
     * Wire transport label for `wenku8-network-report-v1` (separating underlying transport from VPN).
     */
    fun wireTransportLabel(): String {
        if (!isConnected) return "offline"
        if (underlyingTransports.isEmpty()) {
            return if (isVpnActive) "unknown_behind_vpn" else "unknown"
        }
        return underlyingTransports
            .map {
                when (it) {
                    UnderlyingTransportKind.WIFI -> "wifi"
                    UnderlyingTransportKind.CELLULAR -> "cellular"
                    UnderlyingTransportKind.ETHERNET -> "ethernet"
                    UnderlyingTransportKind.BLUETOOTH -> "bluetooth"
                    UnderlyingTransportKind.OTHER -> "other"
                }
            }
            .sorted()
            .joinToString("+")
    }
}

interface NetworkConnectivityObserver {
    fun currentSnapshot(): NetworkSnapshot
    fun observeDefaultNetworkSnapshots(): Flow<NetworkSnapshot>
}

/**
 * Baseline-first Android `ConnectivityManager` observer (`minSdk 24+`).
 *
 * Uses `registerDefaultNetworkCallback` and snapshots `NetworkCapabilities` + `PowerManager`
 * without requesting location/Wi-Fi permissions or reading SSID/BSSID.
 */
class DiagnosticsNetworkMonitor(
    context: Context,
) : NetworkConnectivityObserver {

    private val appContext = context.applicationContext
    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    private val powerManager =
        appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager

    override fun currentSnapshot(): NetworkSnapshot {
        val cm = connectivityManager ?: return NetworkSnapshot(
            isConnected = false,
            underlyingTransports = emptySet(),
        )
        val activeNetwork = cm.activeNetwork
            ?: return NetworkSnapshot(
                isConnected = false,
                underlyingTransports = emptySet(),
                isDataSaverRestricted = readDataSaverRestricted(cm),
                isPowerSaveMode = powerManager?.isPowerSaveMode == true,
            )
        return snapshotForNetwork(cm, activeNetwork)
    }

    override fun observeDefaultNetworkSnapshots(): Flow<NetworkSnapshot> = callbackFlow {
        val cm = connectivityManager
        if (cm == null) {
            trySend(currentSnapshot())
            close()
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(snapshotForNetwork(cm, network))
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                trySend(snapshotFromCapabilities(cm, network, networkCapabilities))
            }

            override fun onLost(network: Network) {
                trySend(currentSnapshot())
            }
        }

        // Emit baseline before any asynchronous callback arrives.
        trySend(currentSnapshot())
        val registered = runCatching {
            cm.registerDefaultNetworkCallback(callback)
            true
        }.getOrDefault(false)

        awaitClose {
            if (registered) {
                runCatching { cm.unregisterNetworkCallback(callback) }
            }
        }
    }

    private fun snapshotForNetwork(cm: ConnectivityManager, network: Network): NetworkSnapshot {
        val capabilities = cm.getNetworkCapabilities(network)
            ?: return NetworkSnapshot(
                internalNetworkHandleKey = network.networkHandle.toString(),
                isConnected = false,
                underlyingTransports = emptySet(),
                isDataSaverRestricted = readDataSaverRestricted(cm),
                isPowerSaveMode = powerManager?.isPowerSaveMode == true,
            )
        return snapshotFromCapabilities(cm, network, capabilities)
    }

    private fun snapshotFromCapabilities(
        cm: ConnectivityManager,
        network: Network,
        capabilities: NetworkCapabilities,
    ): NetworkSnapshot {
        val transports = buildSet {
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                add(UnderlyingTransportKind.WIFI)
            }
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                add(UnderlyingTransportKind.CELLULAR)
            }
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                add(UnderlyingTransportKind.ETHERNET)
            }
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH)) {
                add(UnderlyingTransportKind.BLUETOOTH)
            }
        }
        val hasVpn = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        val isMetered = !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        return NetworkSnapshot(
            internalNetworkHandleKey = network.networkHandle.toString(),
            isConnected = hasInternet,
            underlyingTransports = transports,
            isVpnActive = hasVpn,
            isMetered = isMetered,
            isDataSaverRestricted = readDataSaverRestricted(cm),
            isPowerSaveMode = powerManager?.isPowerSaveMode == true,
        )
    }

    private fun readDataSaverRestricted(cm: ConnectivityManager): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cm.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        } else {
            false
        }
    }
}
