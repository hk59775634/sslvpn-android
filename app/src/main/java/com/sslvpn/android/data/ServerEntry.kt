package com.sslvpn.android.data

import org.json.JSONObject

internal data class ServerEntry(
    val displayName: String,
    val address: String
) {
    fun optionLine(): String {
        val name = displayName.trim()
        val addr = address.trim()
        return if (name.isEmpty()) ServerUrlCanonicalizer.hostLabelFromUrl(addr) else name
    }

    fun toJson(): JSONObject =
        JSONObject().apply {
            put("name", displayName)
            put("address", address)
        }

    companion object {
        fun fromJson(o: JSONObject): ServerEntry? {
            val addr = o.optString("address", "").trim()
            if (addr.isEmpty()) return null
            return ServerEntry(
                displayName = o.optString("name", "").trim(),
                address = addr
            )
        }
    }
}
