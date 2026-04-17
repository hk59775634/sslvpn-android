package com.sslvpn.android.vpn.engine

import android.net.VpnService
import android.util.Log
import com.sslvpn.android.vpn.model.VpnConfig

/**
 * Placeholder bridge for future gomobile/JNI integration.
 * This keeps service architecture stable while native bridge is implemented.
 */
class GoBridgeVpnEngine : VpnEngine {
    override fun connect(service: VpnService, tunFd: Int, config: VpnConfig) {
        try {
            // Future: call native function:
            // SslVpnGoBridge.connect(tunFd, config.server, config.username, config.password)
            Log.i(TAG, "Go bridge placeholder connect for ${config.server}")
        } catch (t: Throwable) {
            Log.e(TAG, "Go bridge connect failed", t)
            throw t
        }
    }

    override fun disconnect() {
        try {
            // Future: SslVpnGoBridge.disconnect()
            Log.i(TAG, "Go bridge placeholder disconnect")
        } catch (t: Throwable) {
            Log.e(TAG, "Go bridge disconnect failed", t)
        }
    }

    companion object {
        private const val TAG = "GoBridgeVpnEngine"
    }
}
