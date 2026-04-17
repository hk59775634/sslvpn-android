package com.sslvpn.android

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.sslvpn.android.data.SiteDomainUtils
import com.sslvpn.android.data.VpnPreferences

/**
 * Step 1 (sslcon-client): site domain + username + password, then continue to [VpnGatewayActivity].
 */
class LoginActivity : AppCompatActivity() {
    private lateinit var domainInput: EditText
    private lateinit var usernameInput: EditText
    private lateinit var passwordInput: EditText
    private lateinit var loginErrorText: TextView
    private lateinit var preferences: VpnPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        preferences = VpnPreferences(this)

        domainInput = findViewById(R.id.domainInput)
        usernameInput = findViewById(R.id.usernameInput)
        passwordInput = findViewById(R.id.passwordInput)
        loginErrorText = findViewById(R.id.loginErrorText)

        if (preferences.siteDomain().isNotBlank()) {
            domainInput.setText(preferences.siteDomain())
        } else if (preferences.legacyGatewayServer().isNotBlank()) {
            domainInput.setText(preferences.legacyGatewayServer())
        }
        if (preferences.username().isNotBlank()) {
            usernameInput.setText(preferences.username())
        }
        if (preferences.password().isNotBlank()) {
            passwordInput.setText(preferences.password())
        }

        findViewById<Button>(R.id.continueButton).setOnClickListener { onContinue() }
    }

    private fun onContinue() {
        loginErrorText.visibility = View.GONE
        val rawDomain = domainInput.text.toString().trim()
        val user = usernameInput.text.toString().trim()
        val pass = passwordInput.text.toString()
        if (rawDomain.isEmpty() || user.isEmpty() || pass.isEmpty()) {
            showError(getString(R.string.status_error_login_step1))
            return
        }
        val host = SiteDomainUtils.hostFromSiteInput(rawDomain)
        if (host.isEmpty()) {
            showError(getString(R.string.status_error_domain))
            return
        }
        val apex = SiteDomainUtils.apexDomain(host)
        if (apex.isNullOrBlank()) {
            showError(getString(R.string.status_error_apex))
            return
        }
        preferences.saveLoginSession(rawDomain, apex, user, pass)
        startActivity(Intent(this, VpnGatewayActivity::class.java))
    }

    private fun showError(msg: String) {
        loginErrorText.text = msg
        loginErrorText.visibility = View.VISIBLE
    }
}
