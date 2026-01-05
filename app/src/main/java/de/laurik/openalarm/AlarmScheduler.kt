package de.laurik.openalarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import de.laurik.openalarm.utils.AppLogger
import androidx.compose.ui.platform.LocalContext

/**
 * Manages scheduling, cancellation, and updates for alarms and timers.
 *
 * This class handles the core alarm scheduling functionality including:
 * - Scheduling alarms with proper time calculations
 * - Canceling alarms
 * - Managing notification updates
 * - Handling different Android versions for alarm scheduling
 */
class AlarmScheduler(private val context: Context) {
    val logger = (context as BaseApplication).getLogger()
    companion object {
        private const val TAG = "AlarmScheduler"
    }
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /**
     * Schedules an alarm to ring at the calculated next occurrence.
     *
     * @param alarm The alarm item to schedule
     * @param groupOffset The offset in minutes for the alarm group
     */
    fun schedule(alarm: AlarmItem, groupOffset: Int) {
        try {
            logger.d(TAG, "Scheduling alarm: ID=${alarm.id}, Enabled=${alarm.isEnabled}")

            if (!alarm.isEnabled) {
                cancel(alarm)
                return
            }

            val now = System.currentTimeMillis()

            /**
             * --- PREVENT RACE CONDITION ---
             * A 60-second grace period to handle race conditions.
             * If an alarm is set for 12:00:00 and the code runs at 12:00:30,
             * we still want to schedule it for 12:00:00 (today) rather than
             * incorrectly scheduling it for tomorrow.
             *
             * TODO: currently if alarm is created for current minute, it is ringing shortly after
             *
             */
            val gracePeriodBuffer = 60_000L

            // Use skippedUntil if it's in the future, otherwise use (now - buffer)
            // This ensures we don't skip "today" just because we are a few seconds late.
            val baseTime = if (alarm.snoozeUntil != null) {
                // Snoozes are absolute, use them directly
                0L
            } else {
                maxOf(now - gracePeriodBuffer, alarm.skippedUntil)
            }

            // Calculate next time
            val triggerTime = AlarmUtils.getNextOccurrence(
                hour = alarm.hour,
                minute = alarm.minute,
                daysOfWeek = alarm.daysOfWeek,
                groupOffsetMinutes = groupOffset,
                temporaryOverrideTime = alarm.temporaryOverrideTime,
                snoozeUntil = alarm.snoozeUntil,
                minTimestamp = baseTime
            )

            logger.d(TAG, "Calculated trigger time: $triggerTime for alarm ID=${alarm.id}")

            // Safety: Only return if the calculated time is largely in the past (older than buffer)
            // If it's 5 seconds in the past, we still want to schedule it so it fires immediately!
            if (triggerTime <= (now - gracePeriodBuffer)) {
                logger.w(TAG, "Trigger time is in the past, not scheduling alarm ID=${alarm.id}")
                return
            }

            scheduleExact(triggerTime, alarm.id, alarm.type.name)
            scheduleNotificationUpdate()
            logger.d(TAG, "Alarm scheduled successfully: ID=${alarm.id}, Time=$triggerTime")
        } catch (e: SecurityException) {
            logger.e(TAG, "Permission denied to schedule alarm: ID=${alarm.id}", e)
            // Optionally notify the user about permission issues
        } catch (e: Exception) {
            logger.e(TAG, "Failed to schedule alarm: ID=${alarm.id}", e)
            // Optionally notify the user about the failure
        }
    }

    /**
     * Schedules an alarm to ring at an exact time.
     *
     * @param timeInMillis The exact time in milliseconds to trigger the alarm
     * @param alarmId The ID of the alarm
     * @param typeName The type of alarm (e.g., "ALARM", "TIMER")
     */
    fun scheduleExact(timeInMillis: Long, alarmId: Int, typeName: String) {
        try {
            logger.d(TAG, "Scheduling exact alarm: ID=$alarmId, Time=$timeInMillis, Type=$typeName")

            val intent = Intent(context, AlarmReceiver::class.java).apply {
                putExtra("ALARM_ID", alarmId)
                putExtra("ALARM_TYPE", typeName)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarmId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val alarmInfo = AlarmManager.AlarmClockInfo(timeInMillis, pendingIntent)
            alarmManager.setAlarmClock(alarmInfo, pendingIntent)
            scheduleNotificationUpdate()
        } catch (e: Exception) {
            logger.e(TAG, "Failed to schedule exact alarm: ID=$alarmId", e)
            // Optionally notify the user or take other action
        }
    }

    /**
     * Schedules a notification update to show the next alarm information.
     * This handles showing notifications before alarms ring and updating them as needed.
     */
    fun scheduleNotificationUpdate() {
        try {
            logger.d(TAG, "Scheduling notification update")

            val now = System.currentTimeMillis()
            val nextAlarm = AlarmUtils.getNextAlarm(context)

            val settings = SettingsRepository.getInstance(context)

            // 1. If disabled or no alarm, clear it immediately
            if (nextAlarm == null || !settings.notifyBeforeEnabled.value) {
                logger.d(TAG, "Notification update: No alarm or notifications disabled")
                NotificationRenderer.refreshAll(context)
                // Cancel any pending lead-time trigger
                val intent = Intent(context, AlarmReceiver::class.java).apply { action = "UPDATE_NOTIFICATIONS_Background" }
                val pi = PendingIntent.getBroadcast(context, 99998, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                alarmManager.cancel(pi)
                return
            }

            val leadMs = settings.notifyBeforeMinutes.value * 60 * 1000L
            val showNotificationTime = nextAlarm.timestamp - leadMs

            logger.d(TAG, "Next alarm at ${nextAlarm.timestamp}, notification at $showNotificationTime")

            // 2. If it's time (or passed), show it now
            if (now >= showNotificationTime - 5000) { // Small 5s buffer to be safe
                logger.d(TAG, "Showing notification immediately")
                NotificationRenderer.refreshAll(context)
            }

            // 3. Always schedule the trigger for the future (either to show it later, or to keep it updated)
            // Note: Even if already showing, we schedule it for the next alarm's lead time to be sure.
            if (showNotificationTime > now) {
                logger.d(TAG, "Scheduling notification for future: $showNotificationTime")
                val intent = Intent(context, AlarmReceiver::class.java).apply {
                    action = "UPDATE_NOTIFICATIONS_Background"
                }
                val pi = PendingIntent.getBroadcast(context, 99998, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (alarmManager.canScheduleExactAlarms()) {
                            logger.d(TAG, "Using exact alarm scheduling")
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, showNotificationTime, pi)
                        } else {
                            logger.w(TAG, "Exact alarm permission missing, using fallback")
                            // Fallback to non-exact if permission missing
                            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, showNotificationTime, pi)
                        }
                    } else {
                        logger.d(TAG, "Using exact alarm scheduling (pre-Android 12)")
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, showNotificationTime, pi)
                    }
                } catch (e: SecurityException) {
                    logger.e(TAG, "Security exception when scheduling notification", e)
                    // Fallback to basic set if there are permission issues
                    alarmManager.set(AlarmManager.RTC_WAKEUP, showNotificationTime, pi)
                } catch (e: Exception) {
                    logger.e(TAG, "Error scheduling notification", e)
                    // Fallback to basic set for any other exceptions
                    alarmManager.set(AlarmManager.RTC_WAKEUP, showNotificationTime, pi)
                }
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error in scheduleNotificationUpdate", e)
            // Optionally notify the user or take other action
        }
    }

    /**
     * Cancels a scheduled alarm.
     *
     * @param alarm The alarm to cancel
     */
    fun cancel(alarm: AlarmItem) {
        try {
            logger.d(TAG, "Canceling alarm: ID=${alarm.id}")

            val intent = Intent(context, AlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                alarm.id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()

            // RE-CALCULATE NOTIFICATION: If the soonest alarm was just cancelled, we need a new trigger
            scheduleNotificationUpdate()
        } catch (e: Exception) {
            logger.e(TAG, "Failed to cancel alarm: ID=${alarm.id}", e)
            // Optionally notify the user or take other action
        }
    }
}
