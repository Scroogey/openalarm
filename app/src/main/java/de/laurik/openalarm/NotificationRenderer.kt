package de.laurik.openalarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import java.util.Calendar
import java.util.Locale

object NotificationRenderer {

    // --- PUBLIC API ---

    fun refreshAll(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val now = System.currentTimeMillis()

        // 1. UPDATE TIMERS
        AlarmRepository.activeTimers.forEach { timer ->
            if (timer.id == AlarmRepository.currentRingingId) return@forEach

            // Generate notification using shared logic
            val note = createNotification(context, timer.id, "TIMER", isRinging = false, timerOverride = timer)
            nm.notify(timer.id, note)
        }

        // 1.5 UPDATE INTERRUPTED ALARMS (Silent Ringing)
        InternalDataStore.interruptedItems.forEach { item ->
             showSilentRinging(context, item.id, item.type, item.label)
        }

        // 2. SNOOZE
        val snoozedAlarm = AlarmRepository.groups.flatMap { it.alarms }
            .filter { it.snoozeUntil != null && it.snoozeUntil!! > now }
            .minByOrNull { it.snoozeUntil!! }

        updateSnooze(context, nm, now, snoozedAlarm)

        // 3. NEXT ALARM
        updateNextAlarm(context, nm, now, isSnoozing = snoozedAlarm != null)
    }

    fun buildRingingNotification(context: Context, id: Int, type: String, label: String = ""): Notification {
        val channelId = "ALARM_CHANNEL_ID"

        val title = if (type == "TIMER") {
            context.getString(R.string.notif_timer_done)
        } else {
            val alarm = AlarmRepository.getAlarm(id)
            if (alarm != null) {
                val group = AlarmRepository.groups.find { it.id == alarm.groupId }
                val offset = group?.offsetMinutes ?: 0
                val baseTime = java.time.LocalTime.of(alarm.hour, alarm.minute)
                val shiftedTime = baseTime.plusMinutes(offset.toLong())
                val timeStr = shiftedTime.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

                if (label.isNotBlank()) {
                    context.getString(R.string.notif_alarm_set_for_with_label, label, timeStr)
                } else {
                    context.getString(R.string.notif_alarm_ringing)
                }
            } else {
                context.getString(R.string.notif_alarm_ringing)
            }
        }

        val layout = if (type == "TIMER") R.layout.notification_timer_done else R.layout.notification_call_style
        val color = if (type == "TIMER") AlarmRepository.TIMER_DONE_COLOR else AlarmRepository.NOTIF_COLOR
        val now = System.currentTimeMillis()
        val elapsedNow = SystemClock.elapsedRealtime()

        val baseTime = if (type == "TIMER") {
            val timer = AlarmRepository.getTimer(id)
            val endTime = timer?.endTime ?: now
            val timeSinceFinish = now - endTime
            elapsedNow - timeSinceFinish
        } else {
            val alarm = AlarmRepository.getAlarm(id)
            val triggerTime = alarm?.lastTriggerTime ?: now
            val effectiveTrigger = if (triggerTime > 0) triggerTime else now
            val durationRinging = now - effectiveTrigger
            elapsedNow - durationRinging
        }

        // Intents
        val fullScreenIntent = Intent(context, RingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
            putExtra("ALARM_TYPE", type)
            putExtra("ALARM_ID", id)
            putExtra("ALARM_LABEL", label)
            setData(android.net.Uri.parse("custom://$type/$id"))
        }

        val fullScreenPending = PendingIntent.getActivity(
            context, id + 12000, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, RingtoneService::class.java).apply {
            action = "STOP_RINGING"
            putExtra("TARGET_ID", id)
        }
        val stopPending = PendingIntent.getService(context, id, stopIntent, PendingIntent.FLAG_IMMUTABLE)

        // Custom View
        val customView = RemoteViews(context.packageName, layout)
        customView.setOnClickPendingIntent(R.id.btn_stop, stopPending)
        customView.setTextViewText(R.id.notif_title_text, title)

        // FIX: API 23 Compatibility
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            customView.setChronometer(R.id.notif_chronometer, baseTime, null, true)
        } else {
            // Legacy way for Android 6
            customView.setLong(R.id.notif_chronometer, "setBase", baseTime)
            customView.setBoolean(R.id.notif_chronometer, "setStarted", true)
        }

        if (type == "ALARM") {
            val snoozeIntent = Intent(context, RingtoneService::class.java).apply {
                action = "SNOOZE_1"
                putExtra("ALARM_ID", id)
                putExtra("TARGET_ID", id)
            }
            val snoozePending = PendingIntent.getService(
                context, id * 20 + 10000, snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            customView.setViewVisibility(R.id.btn_snooze, android.view.View.VISIBLE)
            customView.setOnClickPendingIntent(R.id.btn_snooze, snoozePending)
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPending, true)
            .setContentTitle(title)
            .setCustomContentView(customView)
            .setCustomBigContentView(customView)
            .setOngoing(true)
            .setAutoCancel(false)
            .setVibrate(longArrayOf(0L)) // near zero vibration to avoid being applied a low priority label
            .setColorized(true)
            .setColor(color)

        if (type == "TIMER") {
            val presets = SettingsRepository.getInstance(context).timerAdjustPresets.value
            presets.forEachIndexed { index, seconds ->
                val addIntent = Intent(context, RingtoneService::class.java).apply {
                    action = "ADD_TIME"
                    putExtra("TARGET_ID", id)
                    putExtra("SECONDS", seconds)
                }
                val p = PendingIntent.getService(context, id * 30 + 100 + index, addIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                builder.addAction(0, "+${seconds / 60}m", p)
            }
        }

        return builder.build()
    }

    fun showSilentRinging(context: Context, id: Int, type: String, label: String = "") {
        val note = createNotification(context, id, type, isRinging = false, labelOverride = label)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(id, note)
    }

    fun showMissedNotification(context: Context, id: Int, label: String, scheduledTime: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
 
        val content = if (label.isNotEmpty()) {
            context.getString(R.string.fmt_alarm_missed, label, scheduledTime)
        } else {
            context.getString(R.string.fmt_alarm_missed_no_label, scheduledTime)
        }
 
        val note = NotificationCompat.Builder(context, "STATUS_CHANNEL_ID")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.notif_alarm_missed))
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setUsesChronometer(false)
            .setShowWhen(true)
            .setAutoCancel(true)
            .setColor(AlarmRepository.NOTIF_COLOR)
            .build()
 
        nm.notify(id + 10000, note)
    }

    // --- INTERNAL BUILDER ---

    fun createNotification(
        context: Context,
        id: Int,
        type: String,
        isRinging: Boolean,
        timerOverride: TimerItem? = null,
        labelOverride: String? = null
    ): Notification {
        val now = System.currentTimeMillis()

        val timer = if (type == "TIMER") {
            timerOverride ?: AlarmRepository.getTimer(id)
        } else null

        val actuallyRinging = isRinging || (id == AlarmRepository.currentRingingId)
        val isVirtuallyDone = timer != null && (now + 250 >= timer.endTime)

        val config = when {
            type == "TIMER" && actuallyRinging -> {
                val timeSinceFinish = if (timer != null) now - timer.endTime else 0L
                NotifConfig(
                    layoutId = R.layout.notification_timer_done,
                    color = AlarmRepository.TIMER_DONE_COLOR,
                    title = context.getString(R.string.notif_timer_done),
                    channelId = "ALARM_CHANNEL_ID",
                    isChronometerCountDown = false,
                    baseTime = SystemClock.elapsedRealtime() - timeSinceFinish
                )
            }
            type == "TIMER" && !isVirtuallyDone && timer != null -> {
                NotifConfig(
                    layoutId = R.layout.notification_timer_running,
                    color = AlarmRepository.TIMER_RUNNING_COLOR,
                    title = context.getString(R.string.notif_timer_running),
                    channelId = "ACTIVE_TIMER_CHANNEL_ID",
                    isChronometerCountDown = true,
                    baseTime = SystemClock.elapsedRealtime() + (timer.endTime - now)
                )
            }
            isRinging -> {
                NotifConfig(
                    layoutId = R.layout.notification_call_style,
                    color = AlarmRepository.NOTIF_COLOR,
                    title = context.getString(R.string.notif_wake_up),
                    channelId = "ALARM_CHANNEL_ID",
                    isChronometerCountDown = false,
                    baseTime = SystemClock.elapsedRealtime()
                )
            }
            type == "ALARM" -> {
                // Backgrounded but technically "ringing" (Interrupted)
                val alarm = AlarmRepository.getAlarm(id)
                val label = labelOverride ?: alarm?.label ?: ""
                val triggerTime = alarm?.lastTriggerTime ?: now
                val effectiveTrigger = if (triggerTime > 0) triggerTime else now
                val durationRinging = now - effectiveTrigger
                
                NotifConfig(
                    layoutId = R.layout.notification_call_style,
                    color = AlarmRepository.NOTIF_COLOR,
                    title = if (label.isNotBlank()) context.getString(R.string.notif_alarm_silent_ringing, label) else context.getString(R.string.notif_wake_up),
                    channelId = "STATUS_CHANNEL_ID", // High priority but silent channel
                    isChronometerCountDown = false,
                    baseTime = SystemClock.elapsedRealtime() - durationRinging
                )
            }
            else -> {
                NotifConfig(
                    layoutId = R.layout.notification_timer_running, // Use a neutral layout or similar
                    color = AlarmRepository.NOTIF_COLOR,
                    title = context.getString(R.string.notif_alarm_missed),
                    channelId = "STATUS_CHANNEL_ID",
                    isChronometerCountDown = false,
                    baseTime = SystemClock.elapsedRealtime()
                )
            }
        }

        val fullScreenIntent = Intent(context, RingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("ALARM_TYPE", type)
            putExtra("ALARM_ID", id)
            val alarm = AlarmRepository.getAlarm(id)
            putExtra("ALARM_LABEL", labelOverride ?: alarm?.label ?: "")
            if (timer != null) putExtra("START_TIME", timer.endTime - timer.totalDuration)
            setData(android.net.Uri.parse("custom://timer/$id"))
        }
        val fullScreenPending = PendingIntent.getActivity(context, id + 14000, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val finalStopIntent = if (type == "TIMER" && !isRinging) {
            Intent(context, AlarmReceiver::class.java).apply { action = "STOP_SPECIFIC_TIMER"; putExtra("TARGET_ID", id) }
        } else {
            Intent(context, RingtoneService::class.java).apply { action = "STOP_RINGING"; putExtra("TARGET_ID", id) }
        }

        val stopPending = if (finalStopIntent.component?.className?.contains("Service") == true) {
            PendingIntent.getService(context, id * 10, finalStopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else {
            PendingIntent.getBroadcast(context, id * 10, finalStopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        val snoozeIntent = Intent(context, RingtoneService::class.java).apply { action = "SNOOZE_1" }
        val snoozePending = PendingIntent.getService(context, id * 20, snoozeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val customView = RemoteViews(context.packageName, config.layoutId)
        customView.setOnClickPendingIntent(R.id.btn_stop, stopPending)

        if (type == "ALARM") {
            customView.setViewVisibility(R.id.btn_snooze, android.view.View.VISIBLE)
            customView.setOnClickPendingIntent(R.id.btn_snooze, snoozePending)
        } else if (type == "TIMER") {
            val presets = SettingsRepository.getInstance(context).timerAdjustPresets.value
            val btnIds = listOf(R.id.btn_add_1, R.id.btn_add_2)
            val txtIds = listOf(R.id.txt_add_1, R.id.txt_add_2)

            presets.forEachIndexed { index, seconds ->
                if (index < btnIds.size) {
                    val addIntent = Intent(context, RingtoneService::class.java).apply {
                        action = "ADD_TIME"
                        putExtra("TARGET_ID", id)
                        putExtra("SECONDS", seconds)
                    }
                    val p = PendingIntent.getService(context, id * 30 + 200 + index, addIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    customView.setViewVisibility(btnIds[index], android.view.View.VISIBLE)
                    customView.setTextViewText(txtIds[index], "+${seconds / 60}m")
                    customView.setOnClickPendingIntent(btnIds[index], p)
                }
            }
            // Hide unused buttons if presets < 2
            for (i in presets.size until btnIds.size) {
                customView.setViewVisibility(btnIds[i], android.view.View.GONE)
            }
        }

        val presets = if (type == "TIMER") SettingsRepository.getInstance(context).timerAdjustPresets.value else emptyList()
        val addTimePendings = if (type == "TIMER") {
             presets.mapIndexed { index, seconds ->
                val i = Intent(context, RingtoneService::class.java).apply { action = "ADD_TIME"; putExtra("TARGET_ID", id); putExtra("SECONDS", seconds) }
                PendingIntent.getService(context, id * 30 + 200 + index, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }
        } else emptyList()

        customView.setTextViewText(R.id.notif_title_text, config.title)

        // FIX: API 23 Compatibility for Chronometer
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Android 7+ (API 24+) support CountDown mode and setChronometer directly
            customView.setChronometerCountDown(R.id.notif_chronometer, config.isChronometerCountDown)
            customView.setChronometer(R.id.notif_chronometer, config.baseTime, null, true)
        } else {
            // Android 6 (API 23) does not support CountDown mode natively in RemoteViews.
            customView.setLong(R.id.notif_chronometer, "setBase", config.baseTime)
            customView.setBoolean(R.id.notif_chronometer, "setStarted", true)
        }

        val builder = NotificationCompat.Builder(context, config.channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setStyle(null)
            .setColorized(true).setColor(config.color)
            .setCustomContentView(customView)
            .setCustomBigContentView(customView)
            .setCustomHeadsUpContentView(customView)
            .setContentIntent(fullScreenPending)

        if (type == "TIMER") {
            presets.forEachIndexed { index, seconds ->
                builder.addAction(0, "+${seconds / 60}m", addTimePendings[index])
            }
        }

        if (config.channelId == "ALARM_CHANNEL_ID") {
            builder.setPriority(NotificationCompat.PRIORITY_MAX)
            if (actuallyRinging) {
                builder.setFullScreenIntent(fullScreenPending, true)
                // FOREGROUND_SERVICE_IMMEDIATE is only for API 31+, compat handles this or ignores it
                if (Build.VERSION.SDK_INT >= 31) {
                    builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                }
            }
        } else if (config.channelId == "ACTIVE_TIMER_CHANNEL_ID"){
            builder.setPriority(NotificationCompat.PRIORITY_HIGH)
        } else {
            builder.setPriority(NotificationCompat.PRIORITY_LOW)
        }

        return builder.build()
    }

    private fun updateNextAlarm(context: Context, nm: NotificationManager, now: Long, isSnoozing: Boolean) {
        val info = AlarmUtils.getNextAlarm(context)
        if (isSnoozing) {
            nm.cancel(2)
            return
        }

        val settings = SettingsRepository.getInstance(context)
        if (!settings.notifyBeforeEnabled.value) {
            nm.cancel(2)
            return
        }

        if (info != null) {
            val leadMs = settings.notifyBeforeMinutes.value * 60 * 1000L
            val diff = info.timestamp - now
            // Add a generous buffer (10 seconds) to avoid immediate cancellation due to tiny timing drifts 
            // and include a bit of "past" time to keep it visible while ringing or right before.
            if (diff in -15000..(leadMs + 10000)) {
                val skipIntent = Intent(context, AlarmReceiver::class.java).apply { action = "SKIP_NEXT" }
                val skipPending = PendingIntent.getBroadcast(context, 999, skipIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val openIntent = Intent(context, MainActivity::class.java)
                val openPending = PendingIntent.getActivity(context, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

                val note = NotificationCompat.Builder(context, "STATUS_CHANNEL_ID")
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle(context.getString(R.string.notif_next_alarm, info.timeString))
                    .setWhen(info.timestamp).setUsesChronometer(true)
                    .apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            setChronometerCountDown(true)
                        }
                    }
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setColorized(true).setColor(AlarmRepository.NOTIF_COLOR)
                    .addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.action_skip_today), skipPending)
                    .setContentIntent(openPending)
                    .build()
                nm.notify(2, note)
            } else { nm.cancel(2) }
        } else { nm.cancel(2) }
    }

    private fun updateSnooze(context: Context, nm: NotificationManager, now: Long, snoozedAlarm: AlarmItem?) {
        if (snoozedAlarm != null) {
            val target = snoozedAlarm.snoozeUntil!!
            val cancelIntent = Intent(context, AlarmReceiver::class.java).apply {
                action = "CANCEL_SNOOZE"
                putExtra("ALARM_ID", snoozedAlarm.id)
            }
            val cancelPending = PendingIntent.getBroadcast(context, snoozedAlarm.id, cancelIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val openIntent = Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
            val openPending = PendingIntent.getActivity(context, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val cal = Calendar.getInstance().apply { timeInMillis = target }
            val timeStr = String.format(Locale.getDefault(), "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            val diffMs = target - now
            val diffMin = (diffMs / 60000) + 1
            val label = if (snoozedAlarm.label.isNotEmpty()) "${snoozedAlarm.label} (${context.getString(R.string.notif_snoozed_for, diffMin)})" else context.getString(R.string.notif_snoozed_for, diffMin)

            val note = NotificationCompat.Builder(context, "STATUS_CHANNEL_ID")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(label)
                .setContentText(context.getString(R.string.notif_ringing_at, timeStr))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setColorized(true).setColor(AlarmRepository.NOTIF_COLOR)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, context.getString(R.string.action_dismiss), cancelPending)
                .setContentIntent(openPending)
                .build()

            nm.notify(4, note)
        } else {
            nm.cancel(4)
        }
    }
}

private data class NotifConfig(
    val layoutId: Int,
    val color: Int,
    val title: String,
    val channelId: String,
    val isChronometerCountDown: Boolean,
    val baseTime: Long
)