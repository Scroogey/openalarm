package de.laurik.openalarm

import android.content.Context
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.Calendar

data class NextAlarmInfo(
    val timeString: String,
    val countdownString: String,
    val timestamp: Long
)

object AlarmUtils {

    /**
     * @param minTimestamp The baseline time. Result will always be > this time.
     *                     Pass System.currentTimeMillis() for standard behavior.
     *                     Pass alarm.skippedUntil to find the next valid time AFTER a skip.
     */
    fun getNextOccurrence(
        hour: Int,
        minute: Int,
        daysOfWeek: List<Int>,
        groupOffsetMinutes: Int,
        temporaryOverrideTime: Long? = null,
        snoozeUntil: Long? = null,
        minTimestamp: Long = System.currentTimeMillis()
    ): Long {
        // 1. Snooze & Override Priority (Fast path, pure math)
        if (snoozeUntil != null && snoozeUntil > minTimestamp) return snoozeUntil
        if (temporaryOverrideTime != null && temporaryOverrideTime > minTimestamp + 1000) return temporaryOverrideTime

        // 2. Setup Java.Time
        val zoneId = ZoneId.systemDefault()
        // Convert 'now' to a manipulatable DateTime
        val now = Instant.ofEpochMilli(minTimestamp).atZone(zoneId).toLocalDateTime()

        // 3. Set the Target Time
        // withHour/Minute returns a COPY (Immutable)
        // We handle the group offset by adding minutes
        var target = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
            .plusMinutes(groupOffsetMinutes.toLong())

        // Helper: Convert back to Long for comparison
        fun toMillis(t: LocalDateTime): Long = t.atZone(zoneId).toInstant().toEpochMilli()

        // 4. Logic
        if (daysOfWeek.isEmpty()) {
            // "Once" mode
            if (toMillis(target) <= minTimestamp) {
                target = target.plusDays(1) // Move to tomorrow
            }
            return toMillis(target)
        }

        // "Repeating" mode
        // If the calculated time for today is in the past, start checking from tomorrow
        // OR if today is not in the list of selected days.
        while (toMillis(target) <= minTimestamp || !daysOfWeek.contains(target.dayOfWeek.value)) {
            target = target.plusDays(1)
        }

        return toMillis(target)
    }

    // --- ACTIONS ---

    // Restored function for AlarmReceiver
    fun skipNextAlarm(context: Context) {
        val info = getNextAlarm(context) ?: return
        val targetTime = info.timestamp

        for (group in AlarmRepository.groups) {
            for (alarm in group.alarms) {
                // Check if this alarm matches the target time
                val nextTime = getNextOccurrence(
                    alarm.hour, alarm.minute, alarm.daysOfWeek, group.offsetMinutes,
                    alarm.temporaryOverrideTime, alarm.snoozeUntil,
                    minTimestamp = if (alarm.skippedUntil > System.currentTimeMillis()) alarm.skippedUntil else System.currentTimeMillis()
                )

                if (nextTime == targetTime) {
                    // Skip logic: Set skippedUntil to 1 second after this target time.
                    val updated = alarm.copy(skippedUntil = nextTime + 1000)
                    AlarmRepository.updateAlarm(context, updated)

                    // We also need to reschedule so Android AlarmManager knows to wake up for the NEW next time
                    val scheduler = AlarmScheduler(context)
                    scheduler.schedule(updated, group.offsetMinutes)
                    return
                }
            }
        }
    }

    // --- FORMATTING HELPERS ---

    fun formatSkippedUntil(context: Context, timestamp: Long): String {
        val zoneId = ZoneId.systemDefault()
        val target = Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalDateTime()
        val now = LocalDateTime.now(zoneId)

        val timeStr = String.format("%02d:%02d", target.hour, target.minute)

        // Check if tomorrow using ChronoUnit for accuracy
        val daysBetween = ChronoUnit.DAYS.between(now.toLocalDate(), target.toLocalDate())

        if (daysBetween == 1L) return context.getString(R.string.fmt_skipped_tomorrow, timeStr)

        // Format Day Name
        val dayName = when(target.dayOfWeek) {
            DayOfWeek.MONDAY -> context.getString(R.string.day_mon_short)
            DayOfWeek.TUESDAY -> context.getString(R.string.day_tue_short)
            DayOfWeek.WEDNESDAY -> context.getString(R.string.day_wed_short)
            DayOfWeek.THURSDAY -> context.getString(R.string.day_thu_short)
            DayOfWeek.FRIDAY -> context.getString(R.string.day_fri_short)
            DayOfWeek.SATURDAY -> context.getString(R.string.day_sat_short)
            DayOfWeek.SUNDAY -> context.getString(R.string.day_sun_short)
        }

        return if (daysBetween < 7) {
            context.getString(R.string.fmt_skipped_short, dayName, timeStr)
        } else {
            context.getString(R.string.fmt_skipped_long, dayName, target.dayOfMonth, target.monthValue, timeStr)
        }
    }

    fun getTimeUntilString(context: Context, triggerTime: Long, now: Long = System.currentTimeMillis()): String {
        val diff = triggerTime - now
        if (diff <= 0) return context.getString(R.string.status_ringing_soon)

        val seconds = diff / 1000
        val days = seconds / (24 * 3600)
        val hours = (seconds % (24 * 3600)) / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60

        return when {
            days > 0 -> context.getString(R.string.fmt_d_h, days, hours)
            hours > 0 -> context.getString(R.string.fmt_h_m, hours, mins)
            mins > 0 -> context.getString(R.string.fmt_m_s, mins, secs)
            else -> context.getString(R.string.fmt_s, secs)
        }
    }

    fun getRepeatText(context: Context, days: List<Int>): String? {
        if (days.isEmpty()) return context.getString(R.string.repeat_once)
        if (days.size == 7) return context.getString(R.string.repeat_every_day)
        val sorted = days.sorted()
        if (sorted == listOf(1, 2, 3, 4, 5)) return context.getString(R.string.repeat_weekdays)
        if (sorted == listOf(6, 7)) return context.getString(R.string.repeat_weekends)
        return null
    }

    fun getNextAlarm(context: Context): NextAlarmInfo? {
        val now = System.currentTimeMillis()
        var minDiff = Long.MAX_VALUE
        var nextAlarmTime: Long? = null

        for (group in AlarmRepository.groups) {
            for (alarm in group.alarms) {
                if (!alarm.isEnabled) continue

                // Get REAL occurrence (accounting for skips)
                val effectiveTime = getNextOccurrence(
                    alarm.hour, alarm.minute, alarm.daysOfWeek, group.offsetMinutes,
                    alarm.temporaryOverrideTime, alarm.snoozeUntil,
                    minTimestamp = if (alarm.skippedUntil > now) alarm.skippedUntil else now
                )

                val diff = effectiveTime - now
                if (diff < minDiff) {
                    minDiff = diff
                    nextAlarmTime = effectiveTime
                }
            }
        }

        if (nextAlarmTime == null) return null

        val cNext = Calendar.getInstance().apply { timeInMillis = nextAlarmTime }
        val timeStr = String.format("%02d:%02d", cNext.get(Calendar.HOUR_OF_DAY), cNext.get(Calendar.MINUTE))
        val hoursLeft = minDiff / (1000 * 60 * 60)
        val minsLeft = (minDiff / (1000 * 60)) % 60
        val secondsLeft = (minDiff / 1000) % 60

        val countdownStr = if (minDiff < 8 * 60 * 1000) {
            context.getString(R.string.fmt_countdown_long, hoursLeft, minsLeft, secondsLeft)
        } else {
            context.getString(R.string.fmt_countdown_short, hoursLeft, minsLeft)
        }

        return NextAlarmInfo(timeStr, countdownStr, nextAlarmTime)
    }

    // Kept for backward compatibility if any older layouts use it
    fun formatDuration(context: Context, seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        val parts = mutableListOf<String>()
        if (h > 0) parts.add(context.getString(R.string.fmt_duration_h, h))
        if (m > 0) parts.add(context.getString(R.string.fmt_duration_m, m))
        if (s > 0) parts.add(context.getString(R.string.fmt_duration_s, s))
        if (parts.isEmpty()) return context.getString(R.string.fmt_duration_0s)
        return parts.joinToString(" ")
    }

    fun formatMinutes(context: Context, minutes: Int): String {
        if (minutes < 60) return context.getString(R.string.fmt_duration_m, minutes)
        val h = minutes / 60
        val m = minutes % 60
        return if (m == 0) context.getString(R.string.fmt_duration_h, h) 
               else "${context.getString(R.string.fmt_duration_h, h)} ${context.getString(R.string.fmt_duration_m, m)}"
    }
}