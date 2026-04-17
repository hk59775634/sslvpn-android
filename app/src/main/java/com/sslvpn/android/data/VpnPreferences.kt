package com.sslvpn.android.data

import android.content.Context
import com.sslvpn.android.vpn.model.VpnConfig
import org.json.JSONArray
import org.json.JSONException

internal class VpnPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Step 1 + credentials; clears server list cache when [apex] changes. */
    fun saveLoginSession(siteDomain: String, apex: String, username: String, password: String) {
        val oldApex = prefs.getString(KEY_APEX, "").orEmpty()
        val ed = prefs.edit()
            .putString(KEY_SITE_DOMAIN, siteDomain.trim())
            .putString(KEY_APEX, apex.trim().lowercase())
            .putString(KEY_USERNAME, username.trim())
            .putString(KEY_PASSWORD, password)
        if (!oldApex.equals(apex.trim().lowercase(), ignoreCase = true)) {
            ed.remove(KEY_SERVERS_JSON)
            ed.remove(KEY_LAST_SERVER)
        }
        ed.apply()
    }

    fun siteDomain(): String = prefs.getString(KEY_SITE_DOMAIN, "").orEmpty()
    fun apexDomain(): String = prefs.getString(KEY_APEX, "").orEmpty()
    fun username(): String = prefs.getString(KEY_USERNAME, "").orEmpty()
    fun password(): String = prefs.getString(KEY_PASSWORD, "").orEmpty()

    fun hasLoginSession(): Boolean =
        apexDomain().isNotBlank() && username().isNotBlank()

    fun saveLastSelectedServer(url: String) {
        prefs.edit().putString(KEY_LAST_SERVER, ServerUrlCanonicalizer.canonical(url)).apply()
    }

    fun lastSelectedServer(): String = prefs.getString(KEY_LAST_SERVER, "").orEmpty()

    /** Legacy single-field gateway from older UI builds (migration). */
    fun legacyGatewayServer(): String = prefs.getString(KEY_SERVER_LEGACY, "").orEmpty()

    fun saveServerEntries(entries: List<ServerEntry>) {
        val arr = JSONArray()
        for (e in entries) {
            arr.put(e.toJson())
        }
        prefs.edit().putString(KEY_SERVERS_JSON, arr.toString()).apply()
    }

    fun loadServerEntries(): List<ServerEntry> {
        val raw = prefs.getString(KEY_SERVERS_JSON, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            val out = ArrayList<ServerEntry>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                ServerEntry.fromJson(o)?.let { out.add(it) }
            }
            out
        } catch (_: JSONException) {
            emptyList()
        }
    }

    /**
     * Merges downloaded profile hosts with disk cache; always includes `https://node.<apex>/` as
     * first-class choice (sslcon behavior).
     */
    fun mergeDownloadedEntries(apex: String, nodeBase: String, fromXml: List<ServerEntry>): List<ServerEntry> {
        val nodeCanon = ServerUrlCanonicalizer.canonical(nodeBase)
        val map = LinkedHashMap<String, ServerEntry>()

        fun putNoNodeHost(e: ServerEntry) {
            val k = e.address.lowercase()
            if (k.isEmpty()) return
            if (AnyConnectProfileParser.hostFirstLabelIsNode(e.address)) return
            map[k] = e
        }

        fun putForce(e: ServerEntry) {
            val k = e.address.lowercase()
            if (k.isNotEmpty()) map[k] = e
        }

        val apexClean = apex.trim().lowercase()
        if (nodeCanon.isNotEmpty() && apexClean.isNotEmpty()) {
            putForce(ServerEntry(displayName = "node.$apexClean", address = nodeCanon))
        }
        for (e in loadServerEntries()) {
            putNoNodeHost(e)
        }
        for (e in fromXml) {
            putNoNodeHost(e)
        }
        val list = map.values.toMutableList()
        list.sortWith(
            compareBy<ServerEntry> { it.address.equals(nodeCanon, ignoreCase = true).not() }
                .thenBy { it.optionLine().lowercase() }
        )
        return list
    }

    /** Build [VpnConfig] for the openconnect core (unchanged contract). */
    fun vpnConfigForGateway(serverUrl: String): VpnConfig {
        return VpnConfig(
            server = ServerUrlCanonicalizer.canonical(serverUrl),
            username = username(),
            password = password()
        )
    }

    /** Optional: persist gateway after successful connect (also updates legacy key for tools). */
    fun saveConnectedGateway(serverUrl: String) {
        val canon = ServerUrlCanonicalizer.canonical(serverUrl)
        prefs.edit()
            .putString(KEY_LAST_SERVER, canon)
            .putString(KEY_SERVER_LEGACY, canon)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "sslvpn_prefs"
        private const val KEY_SITE_DOMAIN = "site_domain"
        private const val KEY_APEX = "apex_domain"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_SERVERS_JSON = "servers_json"
        private const val KEY_LAST_SERVER = "last_server"
        /** Older builds stored a single gateway field here. */
        private const val KEY_SERVER_LEGACY = "server"
    }
}
