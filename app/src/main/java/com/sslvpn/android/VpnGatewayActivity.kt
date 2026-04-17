package com.sslvpn.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.sslvpn.android.data.NodeProfileFetcher
import com.sslvpn.android.data.ServerEntry
import com.sslvpn.android.data.ServerUrlCanonicalizer
import com.sslvpn.android.data.SiteDomainUtils
import com.sslvpn.android.data.VpnPreferences
import com.sslvpn.android.vpn.SslVpnService
import com.sslvpn.android.vpn.model.VpnConfig
import com.sslvpn.android.vpn.model.VpnState

/**
 * Step 2: server dropdown + one VPN action button (Connect / Disconnect by state) + back to login.
 */
class VpnGatewayActivity : AppCompatActivity() {
    private lateinit var preferences: VpnPreferences
    private lateinit var serverSpinner: Spinner
    private lateinit var statusText: TextView
    private lateinit var accountSummary: TextView
    private lateinit var vpnActionButton: Button
    private var serverEntries: List<ServerEntry> = emptyList()
    private lateinit var spinnerAdapter: ArrayAdapter<String>

    private var pendingConfig: VpnConfig? = null
    private var lastVpnState: VpnState = VpnState.IDLE

    private val vpnPrepareLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            pendingConfig?.let {
                statusText.text = getString(R.string.status_connecting)
                updateVpnActionUi(VpnState.CONNECTING)
                connectVpn(it)
            }
        } else {
            statusText.text = getString(R.string.status_permission_denied)
            updateVpnActionUi(VpnState.IDLE)
        }
        pendingConfig = null
    }

    private val vpnStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != SslVpnService.ACTION_STATE_CHANGED) {
                return
            }
            val stateRaw = intent.getStringExtra(SslVpnService.EXTRA_STATE).orEmpty()
            val message = intent.getStringExtra(SslVpnService.EXTRA_MESSAGE)
            val state = runCatching { VpnState.valueOf(stateRaw) }.getOrDefault(VpnState.IDLE)
            lastVpnState = state
            statusText.text = if (!message.isNullOrBlank()) {
                message
            } else {
                when (state) {
                    VpnState.IDLE -> getString(R.string.status_idle)
                    VpnState.CONNECTING -> getString(R.string.status_connecting)
                    VpnState.CONNECTED -> getString(R.string.status_connected)
                    VpnState.DISCONNECTED -> getString(R.string.status_disconnected)
                    VpnState.ERROR -> getString(R.string.status_error_connect, "unknown")
                }
            }
            updateVpnActionUi(state)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_gateway)
        preferences = VpnPreferences(this)

        if (!preferences.hasLoginSession()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        accountSummary = findViewById(R.id.accountSummary)
        accountSummary.text = getString(R.string.gateway_account_fmt, preferences.username())

        serverSpinner = findViewById(R.id.serverSpinner)
        statusText = findViewById(R.id.statusText)
        vpnActionButton = findViewById(R.id.vpnActionButton)
        setupMainSiteHyperlink()

        spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            mutableListOf<String>()
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        serverSpinner.adapter = spinnerAdapter

        serverEntries = preferences.loadServerEntries()
        if (serverEntries.isEmpty()) {
            val apex = preferences.apexDomain()
            val node = SiteDomainUtils.nodeGatewayBase(apex)
            if (node.isNotEmpty()) {
                val canon = ServerUrlCanonicalizer.canonical(node)
                serverEntries = listOf(
                    ServerEntry(
                        displayName = "node.${apex.trim().lowercase()}",
                        address = canon
                    )
                )
            }
        }
        applyServerListToSpinner(selectUrl = preferences.lastSelectedServer())

        vpnActionButton.setOnClickListener { onVpnPrimaryClicked() }
        findViewById<Button>(R.id.backLoginButton).setOnClickListener {
            disconnectVpn()
            finish()
        }

        updateVpnActionUi(lastVpnState)

        val apex = preferences.apexDomain()
        if (apex.isNotBlank()) {
            NodeProfileFetcher.fetchMergedServerList(this, apex) { merged ->
                serverEntries = merged
                applyServerListToSpinner(selectUrl = preferences.lastSelectedServer())
            }
        }
    }

    private fun setupMainSiteHyperlink() {
        val apex = preferences.apexDomain().trim().lowercase()
        val tv = findViewById<TextView>(R.id.mainSiteLink)
        val url = SiteDomainUtils.mainSiteWwwUrl(apex)
        if (url.isEmpty()) {
            tv.visibility = View.GONE
            return
        }
        val prefix = getString(R.string.gateway_main_site_prefix)
        val fullText = prefix + url
        val span = SpannableString(fullText)
        val start = prefix.length
        span.setSpan(URLSpan(url), start, fullText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        tv.text = span
        tv.movementMethod = LinkMovementMethod.getInstance()
        tv.visibility = View.VISIBLE
    }

    private fun onVpnPrimaryClicked() {
        when (lastVpnState) {
            VpnState.CONNECTED, VpnState.CONNECTING -> disconnectVpn()
            VpnState.IDLE, VpnState.DISCONNECTED, VpnState.ERROR ->
                requestVpnPermissionAndConnect()
        }
    }

    private fun updateVpnActionUi(state: VpnState) {
        when (state) {
            VpnState.CONNECTED, VpnState.CONNECTING -> {
                vpnActionButton.text = getString(R.string.action_disconnect)
                vpnActionButton.isEnabled = true
                serverSpinner.isEnabled = false
            }
            VpnState.IDLE, VpnState.DISCONNECTED, VpnState.ERROR -> {
                vpnActionButton.text = getString(R.string.action_connect)
                vpnActionButton.isEnabled = true
                serverSpinner.isEnabled = true
            }
        }
    }

    private fun applyServerListToSpinner(selectUrl: String) {
        val labels = serverEntries.map { it.optionLine() }
        spinnerAdapter.clear()
        spinnerAdapter.addAll(labels)
        spinnerAdapter.notifyDataSetChanged()
        val want = ServerUrlCanonicalizer.canonical(selectUrl)
        val idx = serverEntries.indexOfFirst {
            it.address.equals(want, ignoreCase = true)
        }
        if (idx >= 0) {
            serverSpinner.setSelection(idx)
        } else if (serverEntries.isNotEmpty()) {
            serverSpinner.setSelection(0)
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            vpnStateReceiver,
            IntentFilter(SslVpnService.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        unregisterReceiver(vpnStateReceiver)
        super.onStop()
    }

    private fun selectedServerUrl(): String {
        val i = serverSpinner.selectedItemPosition
        if (i < 0 || i >= serverEntries.size) return ""
        return serverEntries[i].address
    }

    private fun requestVpnPermissionAndConnect() {
        val url = selectedServerUrl().trim()
        if (url.isEmpty()) {
            statusText.text = getString(R.string.status_error_no_server)
            return
        }
        val config = preferences.vpnConfigForGateway(url)
        pendingConfig = config
        preferences.saveLastSelectedServer(url)
        preferences.saveConnectedGateway(url)

        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            vpnPrepareLauncher.launch(prepareIntent)
        } else {
            connectVpn(config)
        }
        statusText.text = getString(R.string.status_connecting)
        updateVpnActionUi(VpnState.CONNECTING)
    }

    private fun connectVpn(config: VpnConfig) {
        val intent = Intent(this, SslVpnService::class.java).apply {
            action = SslVpnService.ACTION_CONNECT
            putExtra(SslVpnService.EXTRA_SERVER, config.server)
            putExtra(SslVpnService.EXTRA_USERNAME, config.username)
            putExtra(SslVpnService.EXTRA_PASSWORD, config.password)
        }
        startService(intent)
    }

    private fun disconnectVpn() {
        val intent = Intent(this, SslVpnService::class.java).apply {
            action = SslVpnService.ACTION_DISCONNECT
        }
        startService(intent)
        statusText.text = getString(R.string.status_disconnected)
        updateVpnActionUi(VpnState.DISCONNECTED)
    }
}
