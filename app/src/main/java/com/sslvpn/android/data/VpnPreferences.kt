package com.sslvpn.android.data

import android.content.Context
import com.sslvpn.android.vpn.model.VpnConfig

class VpnPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveConfig(config: VpnConfig) {
        prefs.edit()
            .putString(KEY_SERVER, config.server)
            .putString(KEY_USERNAME, config.username)
            .putString(KEY_PASSWORD, config.password)
            .apply()
    }

    fun loadConfig(): VpnConfig {
        return VpnConfig(
            server = prefs.getString(KEY_SERVER, "").orEmpty(),
            username = prefs.getString(KEY_USERNAME, "").orEmpty(),
            password = prefs.getString(KEY_PASSWORD, "").orEmpty()
        )
    }

    companion object {
        private const val PREFS_NAME = "sslvpn_prefs"
        private const val KEY_SERVER = "server"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
    }
}
