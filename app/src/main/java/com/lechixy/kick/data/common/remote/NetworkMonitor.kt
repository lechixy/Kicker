package com.lechixy.kick.data.common.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object NetworkMonitor {
    private val handler = Handler(Looper.getMainLooper())
    private val state = ConnectionNoticeState().apply { setForeground(false) }
    private val mutableNotice = MutableStateFlow<ConnectionNotice?>(null)
    val notice = mutableNotice.asStateFlow()
    private var initialized = false
    private lateinit var connectivityManager: ConnectivityManager
    private var defaultNetwork: Network? = null
    private var capabilities: NetworkCapabilities? = null
    private var playbackStalled = false
    private var blocked = false
    private val expireRestored = Runnable {
        state.expireRestored()
        mutableNotice.value = state.notice
    }
    private val lostNetwork = Runnable {
        if (defaultNetwork == null) {
            capabilities = null
            updateQuality()
        }
    }
    private val weakPlayback = Runnable {
        playbackStalled = true
        updateQuality()
    }

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
        connectivityManager = manager
        defaultNetwork = manager.activeNetwork
        capabilities = defaultNetwork?.let(manager::getNetworkCapabilities)
        updateQuality()
        manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                handler.removeCallbacks(lostNetwork)
                defaultNetwork = network
                blocked = false
                // Wait for onCapabilitiesChanged: capabilities are not available reliably in onAvailable.
            }
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                if (network != defaultNetwork) return
                capabilities = networkCapabilities
                updateQuality()
            }
            override fun onBlockedStatusChanged(network: Network, isBlocked: Boolean) {
                if (network != defaultNetwork) return
                blocked = isBlocked
                updateQuality()
            }
            override fun onLost(network: Network) {
                if (network != defaultNetwork) return
                defaultNetwork = null
                // A Wi-Fi/mobile handover should not flash an offline bar.
                handler.postDelayed(lostNetwork, 800)
            }
        }, handler)
    }

    fun setForeground(visible: Boolean) {
        if (!initialized) return
        handler.removeCallbacks(expireRestored)
        if (visible) {
            // Resample before enabling notices so a resume is a fresh baseline.
            defaultNetwork = connectivityManager.activeNetwork
            capabilities = defaultNetwork?.let(connectivityManager::getNetworkCapabilities)
            blocked = false
            updateQuality()
        } else {
            handler.removeCallbacks(weakPlayback)
            playbackStalled = false
        }
        state.setForeground(visible)
        mutableNotice.value = state.notice
    }

    fun reportPlaybackBuffering(buffering: Boolean) {
        handler.removeCallbacks(weakPlayback)
        if (buffering) handler.postDelayed(weakPlayback, 8_000)
        else {
            playbackStalled = false
            updateQuality()
        }
    }

    private fun updateQuality() {
        val caps = capabilities
        val online = !blocked && caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val limitedBandwidth = caps != null && caps.linkDownstreamBandwidthKbps in 1..999
        val next = when {
            !online -> ConnectionQuality.Offline
            limitedBandwidth || playbackStalled -> ConnectionQuality.Weak
            else -> ConnectionQuality.Online
        }
        if (state.quality == next) return
        handler.removeCallbacks(expireRestored)
        state.update(next)
        mutableNotice.value = state.notice
        if (state.notice == ConnectionNotice.Restored) handler.postDelayed(expireRestored, 3_500)
    }
}
