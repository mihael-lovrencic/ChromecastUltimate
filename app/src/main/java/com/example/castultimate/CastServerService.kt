package com.example.castultimate

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class CastServerService : Service() {

    companion object {
        private const val CHANNEL_ID = "cast_server"
        private const val NOTIFICATION_ID = 5000

        @Volatile
        private var running = false

        @Volatile
        private var mirrorHandler: (() -> Boolean)? = null

        fun isRunning(): Boolean = running

        fun setMirrorHandler(handler: (() -> Boolean)?) {
            mirrorHandler = handler
        }

        fun start(context: Context) {
            val intent = Intent(context, CastServerService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, CastServerService::class.java)
            context.stopService(intent)
        }
    }

    private var server: CastServer? = null

    override fun onCreate() {
        super.onCreate()
        CastManager.initialize(applicationContext)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        server = CastServer(5000, object : CastServer.MirrorController {
            override fun requestMirror(url: String?): Boolean {
                return mirrorHandler?.invoke() ?: false
            }
        })
        server?.start()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stop()
        server = null
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification() =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_legacy)
            .setContentTitle("Chromecast Ultimate")
            .setContentText("Server running on port 5000")
            .setOngoing(true)
            .build()

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Chromecast Ultimate Server",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
