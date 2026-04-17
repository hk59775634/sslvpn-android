package com.sslvpn.android.vpn.oc

import java.util.regex.Pattern

/**
 * Normalizes route strings from AnyConnect / ocserv into a form [CidrCalculator] and
 * [VpnService.Builder] can consume. Cisco often sends IPv4 as "a.b.c.d w.x.y.z" (space + netmask).
 */
internal object OpenConnectRouteParser {
    private val IPV4 = Pattern.compile(
        "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$"
    )

    /**
     * Returns a string suitable for [CidrCalculator.fromCidr] (IPv4) or passed to IPv6 handling
     * (still contains "/" for prefix length).
     */
    fun normalizeRoute(raw: String): String? {
        val t = raw.trim()
        if (t.isEmpty()) return null

        // IPv6: keep as-is if it already has a prefix; otherwise host route.
        if (t.contains(":")) {
            if (t.contains("/")) return t
            return "$t/128"
        }

        // IPv4 with slash: "10.0.0.0/8" or "10.0.0.0/255.0.0.0"
        if (t.contains("/")) {
            return t
        }

        // IPv4 space + netmask: "10.0.0.0 255.255.0.0"
        val sp = t.indexOf(' ')
        if (sp > 0) {
            val ip = t.substring(0, sp).trim()
            val mask = t.substring(sp + 1).trim()
            if (IPV4.matcher(ip).matches() && IPV4.matcher(mask).matches()) {
                return "$ip/$mask"
            }
        }

        // Plain IPv4 host
        if (IPV4.matcher(t).matches()) {
            return "$t/32"
        }

        return t
    }
}
