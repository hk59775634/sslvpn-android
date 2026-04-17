package com.sslvpn.android.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.sslvpn.android.BuildConfig
import com.sslvpn.android.VpnGatewayActivity
import com.sslvpn.android.R
import com.sslvpn.android.vpn.engine.MockVpnEngine
import com.sslvpn.android.vpn.engine.VpnEngine
import com.sslvpn.android.vpn.model.VpnConfig
import com.sslvpn.android.vpn.model.VpnState
import com.sslvpn.android.vpn.oc.OpenConnectSession
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class SslVpnService : VpnService() {
    private var tunFd: ParcelFileDescriptor? = null
    @Volatile private var running: Boolean = false
    private val shutdownOnce = AtomicBoolean(false)
    private val mockEngine: VpnEngine? = if (BuildConfig.USE_MOCK_ENGINE) MockVpnEngine() else null

    internal fun newTunnelBuilder(): Builder = Builder().setSession("SSLVPN")

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                if (!running) {
                    shutdownOnce.set(false)
                    startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.status_connecting)))
                    emitState(VpnState.CONNECTING)
                    running = true
                    startVpnSession(
                        server = intent.getStringExtra(EXTRA_SERVER).orEmpty(),
                        username = intent.getStringExtra(EXTRA_USERNAME).orEmpty(),
                        password = intent.getStringExtra(EXTRA_PASSWORD).orEmpty(),
                        authGroup = intent.getStringExtra(EXTRA_AUTH_GROUP).orEmpty()
                    )
                }
            }
            ACTION_DISCONNECT -> {
                emitState(VpnState.DISCONNECTED)
                stopVpnSession()
            }
        }
        return START_STICKY
    }

    private fun startVpnSession(server: String, username: String, password: String, authGroup: String) {
        thread(name = "sslvpn-connect") {
            val config = VpnConfig(
                server = server.trim(),
                username = username.trim(),
                password = password,
                authGroup = authGroup.trim()
            )
            try {
                if (BuildConfig.USE_MOCK_ENGINE) {
                    tunFd = Builder()
                        .setSession("SSLVPN")
                        .addAddress("10.8.0.2", 24)
                        .addRoute("0.0.0.0", 0)
                        .addDnsServer("1.1.1.1")
                        .setMtu(1400)
                        .establish()

                    val fd = tunFd?.fd
                    if (fd == null || fd < 0) {
                        emitState(VpnState.ERROR, getString(R.string.status_error_tun))
                        return@thread
                    }
                    mockEngine!!.connect(this, fd, config)
                    emitState(VpnState.CONNECTED)
                    while (running) {
                        Thread.sleep(1000)
                    }
                } else {
                    OpenConnectSession.runBlocking(
                        service = this,
                        config = config,
                        emitState = { state, message -> emitState(state, message) },
                        shouldContinue = { running }
                    )
                }
            } catch (t: Throwable) {
                emitState(VpnState.ERROR, getString(R.string.status_error_connect, t.message ?: "unknown"))
            } finally {
                stopVpnSession()
            }
        }
    }

    private fun stopVpnSession() {
        if (!shutdownOnce.compareAndSet(false, true)) {
            return
        }
        running = false
        mockEngine?.disconnect()
        OpenConnectSession.cancelActive()
        tunFd?.close()
        tunFd = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(content: String): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SSL VPN",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, VpnGatewayActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(content)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .build()
    }

    override fun onDestroy() {
        stopVpnSession()
        super.onDestroy()
    }

    private fun emitState(state: VpnState, message: String? = null) {
        sendBroadcast(Intent(ACTION_STATE_CHANGED).apply {
            setPackage(packageName)
            putExtra(EXTRA_STATE, state.name)
            if (!message.isNullOrBlank()) {
                putExtra(EXTRA_MESSAGE, message)
            }
        })
    }

    companion object {
        const val ACTION_CONNECT = "com.sslvpn.android.action.CONNECT"
        const val ACTION_DISCONNECT = "com.sslvpn.android.action.DISCONNECT"
        const val ACTION_STATE_CHANGED = "com.sslvpn.android.action.STATE_CHANGED"

        const val EXTRA_SERVER = "server"
        const val EXTRA_USERNAME = "username"
        const val EXTRA_PASSWORD = "password"
        const val EXTRA_AUTH_GROUP = "auth_group"
        const val EXTRA_STATE = "state"
        const val EXTRA_MESSAGE = "message"

        private const val CHANNEL_ID = "sslvpn_channel"
        private const val NOTIFICATION_ID = 42
    }
}
