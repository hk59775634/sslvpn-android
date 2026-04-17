package com.sslvpn.android.vpn.oc

import android.net.IpPrefix
import android.net.VpnService
import android.os.Build
import android.util.Log
import java.net.InetAddress
import org.infradead.libopenconnect.LibOpenConnect

/**
 * Applies [LibOpenConnect.IPInfo] to a [VpnService.Builder] (aligned with ics-openconnect `setIPInfo`).
 *
 * Covers: tunnel IPv4/IPv6 addresses, MTU, split includes/excludes, DNS (+ per-DNS /32 routes),
 * and optional NBNS routes so WINS traffic can reach the corporate side.
 */
internal object OpenConnectTunnelConfigurator {
    private const val TAG = "OpenConnectTunnel"

    fun applyToBuilder(b: VpnService.Builder, ip: LibOpenConnect.IPInfo, log: (String) -> Unit) {
        logSummary(ip, log)

        var minMtu = 576

        runCatching {
            if (!ip.addr.isNullOrBlank() && !ip.netmask.isNullOrBlank()) {
                val (host, len) = CidrCalculator.tunnelIpv4AddressAndPrefixLen(ip.addr, ip.netmask)
                b.addAddress(host, len)
                log("IPv4 address: $host/$len")
            }
        }.onFailure { Log.e(TAG, "addAddress IPv4 failed", it) }

        runCatching {
            if (!ip.netmask6.isNullOrBlank()) {
                val ss = ip.netmask6.split("/")
                if (ss.size == 2) {
                    val netmask = ss[1].toInt()
                    b.addAddress(ss[0], netmask)
                    log("IPv6 address: ${ip.netmask6}")
                    minMtu = 1280
                }
            }
        }.onFailure { Log.e(TAG, "addAddress IPv6 failed", it) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            minMtu = 1280
        }

        if (ip.MTU < minMtu) {
            b.setMtu(minMtu)
            log("MTU: $minMtu (forced min)")
        } else {
            b.setMtu(ip.MTU)
            log("MTU: ${ip.MTU}")
        }

        val subnets = ArrayList<String>()
        for (s in ip.splitIncludes ?: emptyList()) {
            OpenConnectRouteParser.normalizeRoute(s)?.let { subnets.add(it) }
        }

        addDefaultRoutes(b, ip, subnets, log)
        addSubnetRoutes(b, subnets, log)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            applySplitExcludesTiramisu(b, ip.splitExcludes, log)
        } else if (!ip.splitExcludes.isNullOrEmpty()) {
            Log.w(
                TAG,
                "Server sent ${ip.splitExcludes.size} split-exclude routes; " +
                    "install Android 13+ for excludeRoute support — traffic may not match desktop split-tunnel behavior."
            )
        }

        val dnsList = ip.DNS ?: emptyList()
        for (raw in dnsList) {
            val s = raw.trim()
            if (s.isEmpty()) continue
            try {
                b.addDnsServer(s)
                b.addRoute(s, if (s.contains(":")) 128 else 32)
                log("DNS: $s (+ /${if (s.contains(":")) 128 else 32} route)")
            } catch (e: Exception) {
                Log.w(TAG, "skip dns $s", e)
            }
        }

        for (raw in ip.NBNS ?: emptyList()) {
            val s = raw.trim()
            if (s.isEmpty()) continue
            try {
                b.addRoute(s, if (s.contains(":")) 128 else 32)
                log("NBNS route: $s")
            } catch (e: Exception) {
                Log.w(TAG, "skip nbns $s", e)
            }
        }

        if (!ip.domain.isNullOrBlank()) {
            try {
                b.addSearchDomain(ip.domain)
                log("DNS search domain: ${ip.domain}")
            } catch (e: Exception) {
                Log.w(TAG, "addSearchDomain failed", e)
            }
        }
    }

    private fun logSummary(ip: LibOpenConnect.IPInfo, log: (String) -> Unit) {
        log(
            "IPInfo: addr=${ip.addr} mask=${ip.netmask} v6=${ip.netmask6} " +
                "gw=${ip.gatewayAddr} MTU=${ip.MTU}"
        )
        log("splitIncludes raw (${ip.splitIncludes?.size ?: 0}): ${ip.splitIncludes}")
        log("splitExcludes raw (${ip.splitExcludes?.size ?: 0}): ${ip.splitExcludes}")
        log("DNS: ${ip.DNS} NBNS: ${ip.NBNS} domain=${ip.domain}")
    }

    private fun applySplitExcludesTiramisu(
        b: VpnService.Builder,
        excludes: ArrayList<String>?,
        log: (String) -> Unit
    ) {
        if (excludes.isNullOrEmpty()) return
        for (raw in excludes) {
            val norm = OpenConnectRouteParser.normalizeRoute(raw) ?: continue
            try {
                b.excludeRoute(routeToIpPrefix(norm))
                log("EXCLUDE route: $norm")
            } catch (e: Exception) {
                Log.w(TAG, "excludeRoute failed for $norm", e)
            }
        }
    }

    /** [VpnService.Builder.excludeRoute] needs [IpPrefix]; string ctor is not available on all API levels. */
    private fun routeToIpPrefix(norm: String): IpPrefix {
        return if (norm.contains(":")) {
            val slash = norm.lastIndexOf('/')
            require(slash > 0) { "bad v6 route: $norm" }
            val host = norm.substring(0, slash)
            val len = norm.substring(slash + 1).toInt()
            IpPrefix(InetAddress.getByName(host), len)
        } else {
            val (host, len) = CidrCalculator.fromCidr(norm)
            IpPrefix(InetAddress.getByName(host), len)
        }
    }

    private fun addDefaultRoutes(
        b: VpnService.Builder,
        ip: LibOpenConnect.IPInfo,
        subnets: ArrayList<String>,
        log: (String) -> Unit
    ) {
        var ip4def = true
        var ip6def = true
        for (s in subnets) {
            if (s.contains(":")) {
                ip6def = false
            } else if (s.isNotBlank()) {
                ip4def = false
            }
        }
        if (ip4def && !ip.addr.isNullOrBlank()) {
            b.addRoute("0.0.0.0", 0)
            log("default IPv4: 0.0.0.0/0")
        }
        if (ip6def && !ip.netmask6.isNullOrBlank()) {
            b.addRoute("::", 0)
            log("default IPv6: ::/0")
        }
    }

    private fun addSubnetRoutes(b: VpnService.Builder, subnets: ArrayList<String>, log: (String) -> Unit) {
        for (raw in subnets) {
            val s = raw.trim()
            if (s.isEmpty()) continue
            try {
                if (s.contains(":")) {
                    val slash = s.lastIndexOf('/')
                    if (slash < 0) {
                        b.addRoute(s, 128)
                    } else {
                        val host = s.substring(0, slash)
                        val pfx = s.substring(slash + 1).toInt()
                        b.addRoute(host, pfx)
                    }
                    log("INCLUDE v6: $s")
                } else {
                    val (host, len) = if (!s.contains("/")) {
                        s to 32
                    } else {
                        val cidr = CidrCalculator.fromCidr(s)
                        cidr.first to cidr.second
                    }
                    b.addRoute(host, len)
                    log("INCLUDE v4: $host/$len")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add split-include route: raw='$raw' norm='$s'", e)
            }
        }
    }
}
