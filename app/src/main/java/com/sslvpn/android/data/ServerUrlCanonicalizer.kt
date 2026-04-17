package com.sslvpn.android.data

import android.net.Uri

/** Normalizes gateway URL to https with host (matches sslcon `CanonicalServerURL`). */
internal object ServerUrlCanonicalizer {
    fun canonical(raw: String): String {
        var s = raw.trim()
        if (s.isEmpty()) return ""
        val lower = s.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            s = "https://$s"
        }
        val u = Uri.parse(s) ?: return s
        val b = u.buildUpon().fragment(null).build()
        return b.toString()
    }

    fun hostLabelFromUrl(url: String): String {
        val u = Uri.parse(canonical(url))
        return u.host?.trim().orEmpty().ifEmpty { url.trim() }
    }
}
