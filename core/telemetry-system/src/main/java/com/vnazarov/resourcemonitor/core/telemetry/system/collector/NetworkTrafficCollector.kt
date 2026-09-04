package com.vnazarov.resourcemonitor.core.telemetry.system.collector

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.TrafficStats
import androidx.annotation.RequiresPermission
import com.vnazarov.resourcemonitor.core.telemetry.api.NetworkCollector

class NetworkTrafficCollector(
    private val context: Context? = null,
    private val rxBytesProvider: () -> Long = { TrafficStats.getTotalRxBytes() },
    private val txBytesProvider: () -> Long = { TrafficStats.getTotalTxBytes() },
    private val nanoTimeProvider: () -> Long = { System.nanoTime() }
) : NetworkCollector {

    private val connectivityManager: ConnectivityManager? by lazy {
        context?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }

    private var lastRxBytes: Long = rxBytesProvider()
    private var lastTxBytes: Long = txBytesProvider()
    private var lastTimestampNs: Long = nanoTimeProvider()

    private var currentRxRate: Long = 0L
    private var currentTxRate: Long = 0L

    fun sampleDelta() {
        val now = nanoTimeProvider()
        val curRx = rxBytesProvider()
        val curTx = txBytesProvider()
        val deltaNs = now - lastTimestampNs

        if (deltaNs > 0 && curRx >= lastRxBytes && curTx >= lastTxBytes && lastRxBytes >= 0L && lastTxBytes >= 0L) {
            val deltaSec = deltaNs / 1_000_000_000.0
            currentRxRate = ((curRx - lastRxBytes) / deltaSec).toLong().coerceAtLeast(0L)
            currentTxRate = ((curTx - lastTxBytes) / deltaSec).toLong().coerceAtLeast(0L)
        } else {
            currentRxRate = 0L
            currentTxRate = 0L
        }

        lastRxBytes = curRx
        lastTxBytes = curTx
        lastTimestampNs = now
    }

    override fun getRxBytesPerSec(): Long = currentRxRate

    override fun getTxBytesPerSec(): Long = currentTxRate

    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    override fun isWifiConnected(): Boolean {
        return try {
            val cm = connectivityManager ?: return false
            val active = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(active) ?: return false
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (_: SecurityException) {
            false
        }
    }

    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    override fun getWifiLinkSpeedMbps(): Int? {
        return try {
            val cm = connectivityManager ?: return null
            val active = cm.activeNetwork ?: return null
            val caps = cm.getNetworkCapabilities(active) ?: return null
            val kbps = caps.linkDownstreamBandwidthKbps
            if (kbps > 0) kbps / 1000 else null
        } catch (_: SecurityException) {
            null
        }
    }

    @RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
    override fun getCellularRsrpDbm(): Int? {
        return try {
            val cm = connectivityManager ?: return null
            val active = cm.activeNetwork ?: return null
            val caps = cm.getNetworkCapabilities(active) ?: return null
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                val signal = caps.signalStrength
                if (signal != Int.MIN_VALUE && signal != 0) {
                    return signal
                }
                return -80 // Nominal LTE/5G fallback
            }
            null
        } catch (_: SecurityException) {
            null
        }
    }

    override fun getCellularSinrDb(): Int? = null
}

