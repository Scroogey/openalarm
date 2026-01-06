package de.laurik.openalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import de.laurik.openalarm.utils.AppLogger
import kotlinx.coroutines.launch

/**
 * Handles alarm-related broadcast intents including starting, stopping, and snoozing alarms.
 *
 * This receiver processes the following actions:
 * - UPDATE_NOTIFICATIONS_Background: Updates notifications for upcoming alarms
 * - STOP_SPECIFIC_TIMER/STOP_ID: Stops a specific timer or alarm
 * - CANCEL_SNOOZE: Cancels a snoozed alarm
 * - SKIP_NEXT: Skips the next occurrence of an alarm
 * - Default action: Starts ringing an alarm or timer
 */
class AlarmReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val logger = AppLogger(context)

        // 1. Tell Android "Wait, I'm doing background work"
        val pendingResult = goAsync()
        logger.d(TAG, "Received intent: ${intent.action}")

        // 2. Launch a coroutine to handle the intent asynchronously
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            try {
                // 3. Load DB (Suspends here, doesn't block UI)
                logger.d(TAG, "Loading database...")
                AlarmRepository.ensureLoaded(context)

                // 4. Proceed with logic
                handleIntent(context, intent)
            } catch (e: Exception) {
                logger.e(TAG, "Error processing intent", e)
                // Optionally notify the user about the error
            } finally {
                // 5. Tell Android "I'm done"
                pendingResult.finish()
            }
        }
    }

    /**
     * Handles the received intent based on its action.
     *
     * @param context The context
     * @param intent The received intent
     */
    private fun handleIntent(context: Context, intent: Intent) {
        val logger = AppLogger(context)

        val receivedAction = intent.action
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        logger.d(TAG, "Handling action: $receivedAction")

        try {
            // Handle notification updates
            if (receivedAction == "UPDATE_NOTIFICATIONS_Background") {
                logger.d(TAG, "Updating notifications")
                NotificationRenderer.refreshAll(context)
                return
            }

            // --- STOP ANY ID (Timer or Alarm) ---
            if (receivedAction == "STOP_SPECIFIC_TIMER" || receivedAction == "STOP_ID") {
                handleStopAction(context, intent, am)
                return
            }

            // --- CANCEL SNOOZE ---
            if (receivedAction == "CANCEL_SNOOZE") {
                handleCancelSnooze(context, intent)
                return
            }

            // --- SKIP NEXT ---
            if (receivedAction == "SKIP_NEXT") {
                handleSkipNext(context)
                return
            }

            // --- START RINGING (default action) ---
            handleStartRinging(context, intent, am)
        } catch (e: Exception) {
            logger.e(TAG, "Error handling intent action: $receivedAction", e)
            // Optionally notify the user about the error
        }
    }

    /**
     * Handles the stop action for timers or alarms.
     */
    private fun handleStopAction(context: Context, intent: Intent, am: AlarmManager) {
        val logger = AppLogger(context)

        val id = intent.getIntExtra("TARGET_ID", -1).takeIf { it != -1 }
            ?: intent.getIntExtra("TIMER_ID", -1)

        if (id != -1) {
            logger.d(TAG, "Stopping ID: $id")

            // 1. Data Cleanup
            AlarmRepository.removeTimer(context, id)

            // 2. Cancel Notification & Schedule
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.cancel(id)
            val pi = PendingIntent.getBroadcast(
                context,
                id,
                Intent(context, AlarmReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            am.cancel(pi)

            // 3. Stop Audio (if ringing or in background)
            val stopSvc = Intent(context, RingtoneService::class.java).apply {
                action = "STOP_RINGING"
                putExtra("TARGET_ID", id)
            }
            context.startService(stopSvc)

            if (AlarmRepository.currentRingingId == id) {
                AlarmRepository.setCurrentRingingId(-1)
            }

            val type = if (id > 1000) "TIMER" else "ALARM"
            StatusHub.trigger(StatusEvent.Stopped(id, type))

            NotificationRenderer.refreshAll(context)

            // If no timers left, stop the foreground service
            if (AlarmRepository.activeTimers.isEmpty()) {
                val svc = Intent(context, TimerRunningService::class.java)
                context.stopService(svc)
            } else {
                // Update service to show next timer
                val svc = Intent(context, TimerRunningService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(svc)
                } else {
                    context.startService(svc)
                }
            }
        }
    }

    /**
     * Handles the cancel snooze action.
     */
    private fun handleCancelSnooze(context: Context, intent: Intent) {
        val logger = AppLogger(context)

        val id = intent.getIntExtra("ALARM_ID", -1)
        if (id != -1) {
            logger.d(TAG, "Canceling snooze for ID: $id")

            // 1. Get the alarm
            val alarm = AlarmRepository.getAlarm(id)
            if (alarm != null) {
                // 2. Clear Snooze
                val updated = alarm.copy(snoozeUntil = null)
                AlarmRepository.updateAlarm(context, updated)

                // 3. Reschedule for the next NORMAL time
                // (We need the group offset)
                val group = AlarmRepository.groups.find { it.alarms.any { a -> a.id == id } }
                val offset = group?.offsetMinutes ?: 0

                val scheduler = AlarmScheduler(context)
                scheduler.schedule(updated, offset)
            }

            NotificationRenderer.refreshAll(context)
        }
    }

    /**
     * Handles the skip next action.
     */
    private fun handleSkipNext(context: Context) {
        val logger = AppLogger(context)

        logger.d(TAG, "Skipping next alarm occurrence")
        AlarmUtils.skipNextAlarm(context)
        NotificationRenderer.refreshAll(context)
    }

    /**
     * Handles the start ringing action.
     */
    private fun handleStartRinging(context: Context, intent: Intent, am: AlarmManager) {
        val logger = AppLogger(context)

        val id = intent.getIntExtra("ALARM_ID", 0)
        if (id == 0) {
            logger.e(TAG, "Error: Received Alarm with ID 0")
            return
        }

        val type = intent.getStringExtra("ALARM_TYPE") ?: if (id > 1000) "TIMER" else "ALARM"
        val resolvedType = when (type) {
            "SNOOZE", "SOFT", "REGULAR", "CRITICAL" -> "ALARM"
            else -> type
        }

        logger.d(TAG, "Starting ringing for ID: $id, Type: $resolvedType")

        val label = intent.getStringExtra("ALARM_LABEL") ?: ""
        val serviceIntent = Intent(context, RingtoneService::class.java).apply {
            putExtra("ALARM_ID", id)
            putExtra("ALARM_TYPE", resolvedType)
            putExtra("ALARM_LABEL", label)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            logger.e(TAG, "Failed to start ringing service for ID: $id", e)
            // Optionally notify the user about the failure
        }
    }
}
