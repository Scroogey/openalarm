package de.laurik.openalarm

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.*

class TimerRunningService : Service() {

    // Scope for background work (DB loading)
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. Launch Coroutine to handle Suspend DB Load
        serviceScope.launch {
            // Suspends here until DB is ready
            AlarmRepository.ensureLoaded(this@TimerRunningService)

            // 2. Logic moved to helper function
            updateForegroundState()
        }

        return START_STICKY
    }

    private fun updateForegroundState() {
        // Check if we actually have active timers
        val timers = AlarmRepository.activeTimers

        if (timers.isEmpty()) {
            stopForeground(true)
            stopSelf()
            return
        }

        // Find the timer that ends soonest to display its countdown
        val nextTimer = timers.minByOrNull { it.endTime }

        if (nextTimer != null) {
            // Build the "Running" notification
            val notification = NotificationRenderer.createNotification(
                this,
                nextTimer.id,
                "TIMER",
                isRinging = false,
                timerOverride = nextTimer
            )

            // Start Foreground to keep app alive
            try {
                if (Build.VERSION.SDK_INT >= 34) { // Android 14+
                    startForeground(
                        nextTimer.id,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(nextTimer.id, notification)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up coroutines to prevent memory leaks
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}