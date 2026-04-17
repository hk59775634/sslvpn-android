package com.sslvpn.android

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.sslvpn.android.data.VpnPreferences
import com.sslvpn.android.vpn.SslVpnService
import com.sslvpn.android.vpn.model.VpnConfig
import com.sslvpn.android.vpn.model.VpnState

class MainActivity : AppCompatActivity() {
    private lateinit var serverInput: EditText
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var statusText: TextView
    private lateinit var preferences: VpnPreferences
    private var pendingConfig: VpnConfig? = null

    private val vpnStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != SslVpnService.ACTION_STATE_CHANGED) {
                return
            }
            val stateRaw = intent.getStringExtra(SslVpnService.EXTRA_STATE).orEmpty()
            val message = intent.getStringExtra(SslVpnService.EXTRA_MESSAGE)
            val state = runCatching { VpnState.valueOf(stateRaw) }.getOrDefault(VpnState.IDLE)
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
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        preferences = VpnPreferences(this)

        serverInput = findViewById(R.id.serverInput)
        usernameInput = findViewById(R.id.usernameInput)
        passwordInput = findViewById(R.id.passwordInput)
        statusText = findViewById(R.id.statusText)

        val saved = preferences.loadConfig()
        if (saved.server.isNotBlank()) {
            serverInput.setText(saved.server)
        }
        if (saved.username.isNotBlank()) {
            usernameInput.setText(saved.username)
        }
        if (saved.password.isNotBlank()) {
            passwordInput.setText(saved.password)
        }

        val connectButton: Button = findViewById(R.id.connectButton)
        val disconnectButton: Button = findViewById(R.id.disconnectButton)

        connectButton.setOnClickListener { requestVpnPermissionAndConnect() }
        disconnectButton.setOnClickListener { disconnectVpn() }
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

    private fun requestVpnPermissionAndConnect() {
        val config = VpnConfig(
            server = serverInput.text.toString().trim(),
            username = usernameInput.text.toString().trim(),
            password = passwordInput.text.toString()
        )
        if (config.server.isBlank() || config.username.isBlank() || config.password.isBlank()) {
            statusText.text = getString(R.string.status_error_validation)
            return
        }
        pendingConfig = config
        preferences.saveConfig(config)

        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            startActivityForResult(prepareIntent, REQ_PREPARE_VPN)
        } else {
            connectVpn(config)
        }
    }

    private fun connectVpn(config: VpnConfig) {
        val intent = Intent(this, SslVpnService::class.java).apply {
            action = SslVpnService.ACTION_CONNECT
            putExtra(SslVpnService.EXTRA_SERVER, config.server)
            putExtra(SslVpnService.EXTRA_USERNAME, config.username)
            putExtra(SslVpnService.EXTRA_PASSWORD, config.password)
        }
        startService(intent)
        statusText.text = getString(R.string.status_connecting)
    }

    private fun disconnectVpn() {
        val intent = Intent(this, SslVpnService::class.java).apply {
            action = SslVpnService.ACTION_DISCONNECT
        }
        startService(intent)
        statusText.text = getString(R.string.status_disconnected)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_PREPARE_VPN) {
            if (resultCode == Activity.RESULT_OK) {
                pendingConfig?.let { connectVpn(it) }
            } else {
                statusText.text = getString(R.string.status_permission_denied)
            }
        }
    }

    companion object {
        private const val REQ_PREPARE_VPN = 1001
    }
}
