package com.sslvpn.android.vpn.oc

import java.util.Locale

/**
 * Minimal IPv4 CIDR helpers (ported from OpenVPN for Android / ics-openconnect `CIDRIP`).
 */
internal object CidrCalculator {
    /**
     * IPv4 **tunnel interface** address: keep [addr] exactly as assigned by the server; only convert
     * dotted [netmask] to a prefix length. Do not use [fromAddressAndMask] for TUN — that normalizes
     * the host IP to the subnet **network** address (e.g. 10.0.0.5 → 10.0.0.0), which breaks
     * [android.net.VpnService.Builder.addAddress].
     */
    fun tunnelIpv4AddressAndPrefixLen(addr: String, netmask: String): Pair<String, Int> {
        return addr.trim() to maskToPrefixLen(netmask)
    }

    /** IPv4 network from address + mask (normalizes host IP to subnet base). For routes / CIDR parsing. */
    fun fromAddressAndMask(ip: String, mask: String): Pair<String, Int> {
        val len = maskToPrefixLen(mask)
        val normalizedIp = normalizeIp(ip, len)
        return normalizedIp to len
    }

    fun fromCidr(combo: String): Pair<String, Int> {
        val parts = combo.split("/")
        val ip = parts[0]
        val len = if (parts.size < 2) {
            32
        } else if (parts[1].matches(Regex("^[0-9]+$"))) {
            parts[1].toInt().coerceIn(0, 128)
        } else {
            maskToPrefixLen(parts[1])
        }
        return if (len <= 32) {
            normalizeIp(ip, len) to len.coerceIn(0, 32)
        } else {
            ip to len
        }
    }

    private fun maskToPrefixLen(mask: String): Int {
        var netmask = ipToLong(mask) + (1L shl 32)
        var lenZeros = 0
        while (netmask and 0x1L == 0L) {
            lenZeros++
            netmask = netmask shr 1
        }
        return if (netmask != ((0x1_ffff_ffffL) shr lenZeros)) {
            32
        } else {
            32 - lenZeros
        }
    }

    private fun normalizeIp(ip: String, len: Int): String {
        if (len !in 0..32) return ip
        var ipLong = ipToLong(ip)
        val mask = if (len == 0) 0L else 0xffff_ffffL shl (32 - len)
        val newIp = ipLong and mask
        if (newIp == ipLong) return ip
        ipLong = newIp
        return String.format(
            Locale.US,
            "%d.%d.%d.%d",
            (ipLong shr 24) and 0xff,
            (ipLong shr 16) and 0xff,
            (ipLong shr 8) and 0xff,
            ipLong and 0xff
        )
    }

    private fun ipToLong(ipaddr: String): Long {
        val ipt = ipaddr.split(".")
        require(ipt.size == 4) { "invalid IPv4: $ipaddr" }
        var ip = 0L
        ip += ipt[0].toLong() shl 24
        ip += ipt[1].toInt() shl 16
        ip += ipt[2].toInt() shl 8
        ip += ipt[3].toInt()
        return ip and 0xffff_ffffL
    }
}
