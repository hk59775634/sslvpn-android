package com.sslvpn.android.data

import com.google.common.net.InternetDomainName

/**
 * Mirrors [sslcon-client/cmd/sslvpn/site_domain.go]: site input → hostname → registrable domain.
 */
internal object SiteDomainUtils {
    fun hostFromSiteInput(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("https://", ignoreCase = true)) {
            s = s.removePrefix("https://").removePrefix("HTTPS://")
        } else if (s.startsWith("http://", ignoreCase = true)) {
            s = s.removePrefix("http://").removePrefix("HTTP://")
        }
        val slash = s.indexOf('/')
        if (slash >= 0) {
            s = s.substring(0, slash)
        }
        s = s.trim()
        val hostPort = s.split(':', limit = 2)
        return hostPort[0].trim().lowercase()
    }

    fun apexDomain(host: String): String? {
        val h = host.trim().lowercase()
        if (h.isEmpty()) return null
        return try {
            InternetDomainName.from(h).topPrivateDomain().toString()
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /** Same as sslcon `NodeGatewayURL`: `https://node.<apex>` without trailing slash. */
    fun nodeGatewayBase(apex: String): String {
        val a = apex.trim().lowercase()
        return if (a.isEmpty()) "" else "https://node.$a"
    }

    /** Main site URL with `www` prefix: `https://www.<apex>/` (sslcon footer style). */
    fun mainSiteWwwUrl(apex: String): String {
        val a = apex.trim().lowercase()
        return if (a.isEmpty()) "" else "https://www.$a/"
    }
}
