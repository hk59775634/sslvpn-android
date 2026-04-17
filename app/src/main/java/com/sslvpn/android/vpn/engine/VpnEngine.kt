package com.sslvpn.android.vpn.engine

import android.net.VpnService
import com.sslvpn.android.vpn.model.VpnConfig

interface VpnEngine {
    fun connect(service: VpnService, tunFd: Int, config: VpnConfig)
    fun disconnect()
}
