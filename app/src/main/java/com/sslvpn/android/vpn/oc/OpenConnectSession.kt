package com.sslvpn.android.vpn.oc

import android.os.ParcelFileDescriptor
import android.util.Log
import com.sslvpn.android.BuildConfig
import com.sslvpn.android.vpn.SslVpnService
import com.sslvpn.android.vpn.model.VpnConfig
import com.sslvpn.android.vpn.model.VpnState
import org.infradead.libopenconnect.LibOpenConnect
import java.util.Locale

/**
 * Runs a blocking OpenConnect / AnyConnect session using the same sequencing as ics-openconnect.
 */
object OpenConnectSession {
    private const val TAG = "OpenConnectSession"

    @Volatile private var active: LibOpenConnect? = null

    fun cancelActive() {
        active?.cancel()
    }

    fun runBlocking(
        service: SslVpnService,
        config: VpnConfig,
        emitState: (VpnState, String?) -> Unit,
        shouldContinue: () -> Boolean
    ) {
        val paths = OpenConnectAssets.prepareRuntimeFiles(service.applicationContext)
        if (paths == null) {
            emitState(
                VpnState.ERROR,
                "Missing OpenConnect bootstrap files. Run: .\\gradlew.bat :app:syncOpenConnectBootstrap"
            )
            return
        }

        val oc = object : LibOpenConnect("com.sslvpn.android/${BuildConfig.VERSION_NAME}") {
            override fun onProcessAuthForm(authForm: LibOpenConnect.AuthForm): Int {
                fillAuthForm(authForm, config)
                return LibOpenConnect.OC_FORM_RESULT_OK
            }

            override fun onProgress(level: Int, msg: String?) {
                if (!msg.isNullOrBlank()) {
                    Log.i(TAG, "[${level}] ${msg.trim()}")
                }
            }

            override fun onValidatePeerCert(msg: String?): Int {
                Log.w(TAG, "Accepting server certificate (no interactive pinning): $msg")
                return 0
            }

            override fun onProtectSocket(fd: Int) {
                if (!service.protect(fd)) {
                    Log.e(TAG, "VpnService.protect($fd) failed")
                }
            }
        }

        active = oc
        var pfd: ParcelFileDescriptor? = null
        try {
            if (BuildConfig.DEBUG) {
                oc.setLogLevel(LibOpenConnect.PRG_DEBUG)
            } else {
                oc.setLogLevel(LibOpenConnect.PRG_INFO)
            }
            oc.setCSDWrapper(paths.csdScript, paths.cacheDir, paths.path)
            oc.setXMLPost(true)
            // Some legacy ASA / ocserv builds negotiate more reliably without mandatory PFS here.
            oc.setPFS(false)
            // Prefer system CA store + our explicit accept in onValidatePeerCert for labs.
            oc.setSystemTrust(true)
            oc.setReportedOS("android")
            oc.setMobileInfo("1.0", "android", "00000000000000000000000000000000")
            oc.setProtocol("anyconnect")

            // Landing hostnames that only 302 to the real ASA are invisible to libopenconnect unless
            // we resolve redirects first (browser-style entry URL).
            val gatewayUrl = GatewayRedirectResolver.resolveGatewayUrl(config.server)
            if (oc.parseURL(gatewayUrl) != 0) {
                emitState(VpnState.ERROR, "Invalid gateway URL")
                return
            }

            when (val ret = oc.obtainCookie()) {
                in Int.MIN_VALUE until 0 -> {
                    emitState(
                        VpnState.ERROR,
                        OpenConnectErrorMapper.obtainCookieFailure(service.resources, ret)
                    )
                    return
                }
                0 -> Unit
                else -> {
                    emitState(VpnState.ERROR, "Login cancelled")
                    return
                }
            }

            if (oc.makeCSTPConnection() != 0) {
                emitState(VpnState.ERROR, "CSTP handshake failed")
                return
            }

            val b = service.newTunnelBuilder()
            OpenConnectTunnelConfigurator.applyToBuilder(b, oc.ipInfo) { Log.d(TAG, it) }
            pfd = b.establish()
            if (pfd == null || oc.setupTunFD(pfd.fd) != 0) {
                emitState(VpnState.ERROR, "TUN setup failed")
                return
            }

            emitState(VpnState.CONNECTED, null)
            oc.setupDTLS(60)

            while (shouldContinue()) {
                if (oc.mainloop(300, LibOpenConnect.RECONNECT_INTERVAL_MIN) < 0) {
                    break
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "session failed", t)
            emitState(VpnState.ERROR, t.message ?: "unknown error")
        } finally {
            runCatching { pfd?.close() }
            runCatching { oc.destroy() }
            active = null
        }
    }

    private fun fillAuthForm(form: LibOpenConnect.AuthForm, config: VpnConfig) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "auth form: banner=${form.banner} error=${form.error} method=${form.method}")
            for (opt in form.opts) {
                if ((opt.flags and LibOpenConnect.OC_FORM_OPT_IGNORE.toLong()) != 0L) continue
                Log.d(TAG, "  opt type=${opt.type} name=${opt.name} label=${opt.label} value=${opt.value}")
            }
        }

        val group = config.authGroup.trim()
        if (group.isNotEmpty() && form.authgroupOpt != null) {
            val opt = form.authgroupOpt
            for (i in 0 until opt.choices.size) {
                val ch = opt.choices[i]
                if (group.equals(ch.name, ignoreCase = true) ||
                    group.equals(ch.label, ignoreCase = true)
                ) {
                    opt.value = ch.name
                    form.authgroupSelection = i
                    break
                }
            }
        }

        for (opt in form.opts) {
            if ((opt.flags and LibOpenConnect.OC_FORM_OPT_IGNORE.toLong()) != 0L) {
                continue
            }
            when (opt.type) {
                LibOpenConnect.OC_FORM_OPT_PASSWORD -> {
                    opt.value = config.password
                }
                LibOpenConnect.OC_FORM_OPT_TEXT -> {
                    val n = opt.name?.lowercase(Locale.US).orEmpty()
                    when {
                        n.contains("user") || n.contains("login") || n == "username" ||
                            n == "userid" || n == "name" && !n.contains("domain") ||
                            n == "principal" || n.endsWith("user") || n.contains("uname") -> {
                            opt.value = config.username
                        }
                    }
                }
                LibOpenConnect.OC_FORM_OPT_SELECT -> {
                    if (opt.name.equals("group_list", ignoreCase = true) && group.isNotEmpty()) {
                        for (ch in opt.choices) {
                            if (group.equals(ch.name, ignoreCase = true) ||
                                group.equals(ch.label, ignoreCase = true)
                            ) {
                                opt.value = ch.name
                                break
                            }
                        }
                    }
                }
                LibOpenConnect.OC_FORM_OPT_TOKEN -> {
                    Log.w(
                        TAG,
                        "Server requested OTP/token; auto-fill not implemented — connection may fail."
                    )
                }
            }
        }

        // Fill remaining empty text/password fields (many gateways use opaque field names).
        for (opt in form.opts) {
            if ((opt.flags and LibOpenConnect.OC_FORM_OPT_IGNORE.toLong()) != 0L) {
                continue
            }
            when (opt.type) {
                LibOpenConnect.OC_FORM_OPT_TEXT -> {
                    if (opt.value.isNullOrBlank() && shouldAutofillUsernameForField(opt.name)) {
                        opt.value = config.username
                    }
                }
                LibOpenConnect.OC_FORM_OPT_PASSWORD -> {
                    if (opt.value.isNullOrBlank()) {
                        opt.value = config.password
                    }
                }
            }
        }
    }

    private fun shouldAutofillUsernameForField(name: String?): Boolean {
        val n = name?.lowercase(Locale.US).orEmpty()
        if (n.contains("domain") && !n.contains("user")) return false
        if (n.contains("realm") && !n.contains("user")) return false
        if (n.contains("host") && !n.contains("user")) return false
        if (n.contains("otp") || n.contains("token") || n.contains("pin")) return false
        return true
    }
}
