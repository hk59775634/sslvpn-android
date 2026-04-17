package com.sslvpn.android.vpn.engine

import android.net.VpnService
import android.util.Log
import com.sslvpn.android.vpn.model.VpnConfig

class MockVpnEngine : VpnEngine {
    override fun connect(service: VpnService, tunFd: Int, config: VpnConfig) {
        Log.i(
            TAG,
            "Mock connect: server=${config.server}, user=${config.username}, authGroup=${config.authGroup}, tunFd=$tunFd"
        )
    }

    override fun disconnect() {
        Log.i(TAG, "Mock disconnect")
    }

    companion object {
        private const val TAG = "MockVpnEngine"
    }
}
