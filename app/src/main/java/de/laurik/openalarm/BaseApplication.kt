package de.laurik.openalarm

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import de.laurik.openalarm.utils.AppLogger

class BaseApplication : Application() {
    private val _logger by lazy { AppLogger(this) }
    fun getLogger(): AppLogger = _logger

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val receiver = NotificationUpdateReceiver()
        registerReceiver(receiver, android.content.IntentFilter(android.content.Intent.ACTION_TIME_TICK))
    }

    private fun createNotificationChannel() {
        // Notification Channels are only required for Android 8.0 (API 26) and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "ALARM_CHANNEL_ID",
                "Alarm Service",
                NotificationManager.IMPORTANCE_HIGH // CRITICAL: Must be HIGH to make sound
            ).apply {
                description = "Channel for Alarm Manager"
                setSound(null, null) // We play sound manually via MediaPlayer, so silent notification
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}