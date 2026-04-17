package com.sslvpn.android.data

import android.net.Uri
import java.util.regex.Pattern

/**
 * Parses AnyConnect-style profile XML for &lt;HostEntry&gt; blocks (same regex approach as
 * sslcon-client/core/anyprofile/anyprofile.go).
 */
internal object AnyConnectProfileParser {
    private val hostEntryBlock = Pattern.compile("(?is)<HostEntry[^>]*>(.*?)</HostEntry>")
    private val hostNameTag = Pattern.compile("(?is)<HostName[^>]*>(.*?)</HostName>")
    private val hostAddrTag = Pattern.compile("(?is)<HostAddress[^>]*>(.*?)</HostAddress>")

    fun parseHostEntries(xml: String): List<ServerEntry> {
        val m = hostEntryBlock.matcher(xml)
        val out = ArrayList<ServerEntry>()
        while (m.find()) {
            val block = m.group(1) ?: continue
            val name = hostNameTag.matcher(block).let { mm ->
                if (mm.find()) cleanXmlText(mm.group(1)) else ""
            }
            val addrRaw = hostAddrTag.matcher(block).let { mm ->
                if (mm.find()) cleanXmlText(mm.group(1)) else ""
            }
            val addr = addrRaw.trim()
            if (addr.isEmpty()) continue
            val canon = ServerUrlCanonicalizer.canonical(addr)
            if (canon.isEmpty()) continue
            val disp = name.trim().ifEmpty { ServerUrlCanonicalizer.hostLabelFromUrl(canon) }
            out.add(ServerEntry(displayName = disp, address = canon))
        }
        return out
    }

    private fun cleanXmlText(s: String?): String {
        if (s == null) return ""
        var t = s.trim()
        if (t.startsWith("<![CDATA[")) {
            t = t.removePrefix("<![CDATA[")
        }
        if (t.endsWith("]]>")) {
            t = t.removeSuffix("]]>")
        }
        return t.trim()
    }

    /** Host first label is `node` (sslcon `HostFirstLabelIsNode`). */
    fun hostFirstLabelIsNode(canonicalUrl: String): Boolean {
        val host = hostFromHttpUrl(canonicalUrl) ?: return false
        if (host.contains(':')) return false
        val first = host.substringBefore('.').lowercase()
        return first == "node"
    }

}

private fun hostFromHttpUrl(url: String): String? {
    val u = Uri.parse(ServerUrlCanonicalizer.canonical(url))
    return u.host?.lowercase()
}
