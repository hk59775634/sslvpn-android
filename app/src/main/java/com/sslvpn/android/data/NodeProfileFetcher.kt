package com.sslvpn.android.data

import android.content.Context
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Downloads AnyConnect server list XML from `node.<apex>` (sslcon `DownloadNodeProfileXML`):
 * tries `/profile.xml` then `/servers.xml`.
 */
internal object NodeProfileFetcher {
    private const val TAG = "NodeProfileFetcher"
    private const val TIMEOUT_MS = 20_000
    private val paths = arrayOf("/profile.xml", "/servers.xml")

    fun fetchMergedServerList(
        context: Context,
        apex: String,
        onDone: (List<ServerEntry>) -> Unit
    ) {
        val nodeBase = SiteDomainUtils.nodeGatewayBase(apex.trim())
        if (nodeBase.isEmpty()) {
            onDone(VpnPreferences(context).loadServerEntries())
            return
        }
        thread(name = "node-profile") {
            val downloaded = downloadProfileXml(nodeBase)
            val fromXml = if (downloaded != null) {
                AnyConnectProfileParser.parseHostEntries(String(downloaded, Charsets.UTF_8))
                    .filter { !AnyConnectProfileParser.hostFirstLabelIsNode(it.address) }
            } else {
                emptyList()
            }
            val prefs = VpnPreferences(context)
            val merged = prefs.mergeDownloadedEntries(apex.trim(), nodeBase, fromXml)
            prefs.saveServerEntries(merged)
            Log.i(TAG, "merged ${merged.size} server(s) for apex=$apex")
            android.os.Handler(context.mainLooper).post { onDone(merged) }
        }
    }

    private fun downloadProfileXml(nodeBase: String): ByteArray? {
        val base = nodeBase.trimEnd('/')
        var last: Exception? = null
        for (p in paths) {
            val u = URL(base + p)
            try {
                val conn = u.openConnection() as HttpURLConnection
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "sslvpn-android/profile")
                conn.setRequestProperty("Accept", "application/xml,text/xml,*/*")
                conn.useCaches = false
                val code = conn.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    conn.disconnect()
                    continue
                }
                val body = conn.inputStream.use { ins ->
                    readLimited(ins, 8 * 1024 * 1024)
                }
                conn.disconnect()
                if (body.isNotEmpty()) return body
            } catch (e: Exception) {
                last = e
            }
        }
        if (last != null) {
            Log.w(TAG, "profile download failed: ${last.message}")
        }
        return null
    }

    private fun readLimited(ins: InputStream, max: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var total = 0
        while (total < max) {
            val toRead = minOf(chunk.size, max - total)
            val n = ins.read(chunk, 0, toRead)
            if (n <= 0) break
            out.write(chunk, 0, n)
            total += n
        }
        return out.toByteArray()
    }
}
