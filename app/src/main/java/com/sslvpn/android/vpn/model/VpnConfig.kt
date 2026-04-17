package com.sslvpn.android.vpn.model

data class VpnConfig(
    /** AnyConnect/OpenConnect gateway URL, typically https://vpn.example.com */
    val server: String,
    val username: String,
    val password: String,
    /** Optional AnyConnect "group" / OpenConnect authgroup (may be empty). */
    val authGroup: String = ""
)
