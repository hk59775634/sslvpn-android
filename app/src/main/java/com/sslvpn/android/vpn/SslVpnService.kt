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
import com.sslvpn.android.MainActivity
import com.sslvpn.android.R
import kotlin.concurrent.thread

class SslVpnService : VpnService() {
    private var tunFd: ParcelFileDescriptor? = null
    @Volatile private var running: Boolean = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                if (!running) {
                    startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.status_connecting)))
                    running = true
                    startVpnSession(
                        server = intent.getStringExtra(EXTRA_SERVER).orEmpty(),
                        username = intent.getStringExtra(EXTRA_USERNAME).orEmpty(),
                        password = intent.getStringExtra(EXTRA_PASSWORD).orEmpty()
                    )
                }
            }
            ACTION_DISCONNECT -> stopVpnSession()
        }
        return START_STICKY
    }

    private fun startVpnSession(server: String, username: String, password: String) {
        thread(name = "sslvpn-connect") {
            // Skeleton implementation:
            // 1) establish TUN via VpnService.Builder
            // 2) hand TUN fd + credentials to Go core bridge in later step
            // 3) pump packets until disconnect
            tunFd = Builder()
                .setSession("SSLVPN")
                .addAddress("10.8.0.2", 24)
                .addRoute("0.0.0.0", 0)
                .addDnsServer("1.1.1.1")
                .setMtu(1400)
                .establish()

            // Placeholder to keep session alive and demonstrate service lifecycle.
            while (running) {
                Thread.sleep(1000)
            }

            val ignored = Triple(server, username, password)
            if (ignored.first.isEmpty() && ignored.second.isEmpty() && ignored.third.isEmpty()) {
                // Keep lint happy while this is still skeleton wiring.
            }
        }
    }

    private fun stopVpnSession() {
        running = false
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
            Intent(this, MainActivity::class.java),
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

    companion object {
        const val ACTION_CONNECT = "com.sslvpn.android.action.CONNECT"
        const val ACTION_DISCONNECT = "com.sslvpn.android.action.DISCONNECT"

        const val EXTRA_SERVER = "server"
        const val EXTRA_USERNAME = "username"
        const val EXTRA_PASSWORD = "password"

        private const val CHANNEL_ID = "sslvpn_channel"
        private const val NOTIFICATION_ID = 42
    }
}
