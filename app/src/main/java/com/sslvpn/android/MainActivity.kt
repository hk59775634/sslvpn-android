package com.sslvpn.android

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.sslvpn.android.vpn.SslVpnService

class MainActivity : AppCompatActivity() {
    private lateinit var serverInput: EditText
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        serverInput = findViewById(R.id.serverInput)
        usernameInput = findViewById(R.id.usernameInput)
        passwordInput = findViewById(R.id.passwordInput)
        statusText = findViewById(R.id.statusText)

        val connectButton: Button = findViewById(R.id.connectButton)
        val disconnectButton: Button = findViewById(R.id.disconnectButton)

        connectButton.setOnClickListener { requestVpnPermissionAndConnect() }
        disconnectButton.setOnClickListener { disconnectVpn() }
    }

    private fun requestVpnPermissionAndConnect() {
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            startActivityForResult(prepareIntent, REQ_PREPARE_VPN)
        } else {
            connectVpn()
        }
    }

    private fun connectVpn() {
        val intent = Intent(this, SslVpnService::class.java).apply {
            action = SslVpnService.ACTION_CONNECT
            putExtra(SslVpnService.EXTRA_SERVER, serverInput.text.toString())
            putExtra(SslVpnService.EXTRA_USERNAME, usernameInput.text.toString())
            putExtra(SslVpnService.EXTRA_PASSWORD, passwordInput.text.toString())
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
                connectVpn()
            } else {
                statusText.text = getString(R.string.status_permission_denied)
            }
        }
    }

    companion object {
        private const val REQ_PREPARE_VPN = 1001
    }
}
