package com.sslvpn.android.vpn.oc

import android.util.Log
import java.net.HttpURLConnection
import java.net.URI

/**
 * Some deployments publish a **DNS alias** that only answers HTTP with **302** to the real ASA
 * AnyConnect hostname. libopenconnect may not follow that hop the same way a browser would, so
 * we probe with a lightweight GET (redirects disabled per hop, manual cap) and pass the resolved
 * gateway base into [org.infradead.libopenconnect.LibOpenConnect.parseURL].
 */
internal object GatewayRedirectResolver {
    private const val TAG = "GatewayRedirect"
    private const val CONNECT_MS = 15_000
    private const val READ_MS = 15_000
    private const val MAX_HOPS = 16

    /**
     * Returns [scheme]://[authority]/ suitable for AnyConnect (path/query stripped).
     * On any failure, returns [server] trimmed unchanged so existing gateways keep working.
     */
    fun resolveGatewayUrl(server: String): String {
        val trimmed = server.trim()
        if (trimmed.isEmpty()) return trimmed
        return try {
            val start = normalizeProbeUri(trimmed)
            val finalUri = followRedirects(start)
            val base = gatewayBase(finalUri)
            if (base != trimmed && base.trimEnd('/') != trimmed.trimEnd('/')) {
                Log.i(TAG, "resolved gateway: '$trimmed' -> '$base'")
            }
            base
        } catch (e: Exception) {
            Log.w(TAG, "redirect probe failed, using raw server string: ${e.message}")
            trimmed
        }
    }

    private fun normalizeProbeUri(serverTrimmed: String): URI {
        val abs = if (serverTrimmed.contains("://", ignoreCase = true)) {
            serverTrimmed
        } else {
            "https://$serverTrimmed"
        }
        val u = URI.create(abs)
        val path = u.rawPath
        val newPath = when {
            path.isNullOrEmpty() || path == "" -> "/"
            else -> path
        }
        return URI(
            u.scheme,
            u.userInfo,
            u.host,
            u.port,
            newPath,
            u.rawQuery,
            u.rawFragment
        )
    }

    private fun followRedirects(start: URI): URI {
        var current = start
        var hop = 0
        while (hop < MAX_HOPS) {
            val conn = current.toURL().openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = CONNECT_MS
            conn.readTimeout = READ_MS
            conn.requestMethod = "GET"
            conn.setRequestProperty(
                "User-Agent",
                "OpenConnect-compatible gateway-probe (com.sslvpn.android)"
            )
            conn.useCaches = false
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location")
                drainQuietly(conn)
                conn.disconnect()
                if (loc.isNullOrBlank()) {
                    Log.w(TAG, "redirect $code but no Location from $current")
                    return current
                }
                val next = current.resolve(loc)
                Log.i(TAG, "hop $hop: HTTP $code -> $next")
                current = next.normalize()
                hop++
                continue
            }
            drainQuietly(conn)
            conn.disconnect()
            return current
        }
        Log.w(TAG, "redirect cap ($MAX_HOPS) reached; last URI: $current")
        return current
    }

    private fun drainQuietly(conn: HttpURLConnection) {
        runCatching {
            (conn.errorStream ?: conn.inputStream)?.use { ins ->
                val buf = ByteArray(8192)
                while (ins.read(buf) != -1) {
                    // discard
                }
            }
        }
    }

    /** AnyConnect gateway base: strip path/query/fragment; keep [URI.rawAuthority] (IPv6-safe). */
    private fun gatewayBase(uri: URI): String {
        val scheme = (uri.scheme ?: "https").lowercase()
        val auth = uri.rawAuthority
        return if (!auth.isNullOrEmpty()) {
            "$scheme://$auth/"
        } else {
            val h = uri.host ?: return uri.toString()
            val port = uri.port
            if (port != -1) {
                val def = if (scheme == "https") 443 else 80
                if (port != def) {
                    return "$scheme://$h:$port/"
                }
            }
            "$scheme://$h/"
        }
    }
}
