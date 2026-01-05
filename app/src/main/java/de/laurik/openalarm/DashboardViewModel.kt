package de.laurik.openalarm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.util.UUID

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    // 1. Expose State from Repository
    // Because Repository uses SnapshotStateList, Compose will update automatically
    val groups = AlarmRepository.groups
    val activeTimers = AlarmRepository.activeTimers

    private val context = application.applicationContext
    private val scheduler = AlarmScheduler(context)

    init {
        // Safe loading on startup
        viewModelScope.launch {
            AlarmRepository.ensureLoaded(context)
            // The UI (InternalDataStore) will automatically update when this finishes
        }
    }

    fun saveAlarmWithNewGroup(alarm: AlarmItem, groupName: String, groupColor: Int, isNewAlarm: Boolean) {
        viewModelScope.launch {
            // 1. Create Group
            val newGroupId = UUID.randomUUID().toString()
            val groupEntity = AlarmGroupEntity(id = newGroupId, name = groupName, colorArgb = groupColor)
            
            // 2. Add via repository (updates memory + DB)
            AlarmRepository.addGroup(context, groupEntity)

            // 3. Save Alarm linked to new Group
            val finalAlarm = alarm.copy(groupId = newGroupId)

            // Re-use existing save logic but ensure we are in the same suspend scope
            if (isNewAlarm) {
                val realId = AlarmRepository.getNextAlarmId(context)
                val alarmWithId = finalAlarm.copy(id = realId)
                AlarmRepository.addAlarm(context, alarmWithId) // This updates Memory + DB

                val scheduler = AlarmScheduler(context)
                scheduler.schedule(alarmWithId, 0) // New group has 0 offset initially
            } else {
                AlarmRepository.updateAlarm(context, finalAlarm)
                val scheduler = AlarmScheduler(context)
                scheduler.schedule(finalAlarm, 0)
            }

            NotificationRenderer.refreshAll(context)
        }
    }

    fun updateGroupDetails(group: AlarmGroup, newName: String, newColor: Int) {
        val entity = AlarmGroupEntity(group.id, newName, group.offsetMinutes, group.skippedUntil, newColor)
        viewModelScope.launch {
            AlarmRepository.updateGroup(context, entity)
            // No need to reschedule alarms, just UI update
        }
    }



    // --- QUICK ADJUST ---
    fun adjustAlarmTime(alarm: AlarmItem, minutesToAdd: Int) {
        val group = groups.find { it.id == alarm.groupId }
        val offset = group?.offsetMinutes ?: 0
        val minTime = if ((group?.skippedUntil ?: 0L) > System.currentTimeMillis()) group!!.skippedUntil else System.currentTimeMillis()

        // Calculate where it would normally ring
        val currentNext = AlarmUtils.getNextOccurrence(
            alarm.hour, alarm.minute, alarm.daysOfWeek, offset,
            alarm.temporaryOverrideTime, alarm.snoozeUntil,
            minTimestamp = if (alarm.skippedUntil > minTime) alarm.skippedUntil else minTime
        )

        // Adjust
        val newTime = currentNext + (minutesToAdd * 60 * 1000)

        // Save as temporary override
        val updated = alarm.copy(temporaryOverrideTime = newTime)
        saveAlarm(updated, isNew = false)
    }

    // --- GROUP LOGIC ---
    fun shiftGroup(group: AlarmGroup, minutesOffset: Int) {
        val newOffset = group.offsetMinutes + minutesOffset
        val entity = AlarmGroupEntity(group.id, group.name, newOffset, group.skippedUntil)

        viewModelScope.launch {
            AlarmRepository.updateGroup(context, entity)
            val scheduler = AlarmScheduler(context)
            group.alarms.forEach { alarm ->
                if (alarm.isEnabled) scheduler.schedule(alarm, newOffset)
            }
            NotificationRenderer.refreshAll(context)
        }
    }

    fun skipGroup(group: AlarmGroup, until: Long) {
        val entity = AlarmGroupEntity(group.id, group.name, group.offsetMinutes, until)
        viewModelScope.launch {
            AlarmRepository.updateGroup(context, entity)
            val scheduler = AlarmScheduler(context)
            group.alarms.forEach { alarm ->
                if (alarm.isEnabled) scheduler.schedule(alarm, group.offsetMinutes)
            }
            NotificationRenderer.refreshAll(context)
        }
    }

    fun deleteGroup(group: AlarmGroup, keepAlarms: Boolean) {
        viewModelScope.launch {
            AlarmRepository.deleteGroup(context, group, keepAlarms)
            NotificationRenderer.refreshAll(context)
        }
    }

    fun adjustGroupAlarms(group: AlarmGroup, minutesToAdd: Int) {
        group.alarms.forEach { alarm ->
            if (alarm.isEnabled) {
               adjustAlarmTime(alarm, minutesToAdd)
            }
        }
    }

    fun resetGroupAlarms(group: AlarmGroup) {
        group.alarms.forEach { alarm ->
            resetAlarmAdjustment(alarm)
        }
    }
    // --- ALARM INTENTS ---

    fun toggleAlarm(alarm: AlarmItem, isEnabled: Boolean) {
        // When toggling, we reset Snooze AND Temporary Adjustments
        val updated = alarm.copy(
            isEnabled = isEnabled,
            snoozeUntil = null,
            temporaryOverrideTime = null
        )

        AlarmRepository.updateAlarm(context, updated)

        val group = groups.find { it.id == alarm.groupId }
        val offset = group?.offsetMinutes ?: 0

        if (isEnabled) {
            scheduler.schedule(updated, offset)
        } else {
            scheduler.cancel(updated)
        }
        NotificationRenderer.refreshAll(context)
    }

    fun resetAlarmAdjustment(alarm: AlarmItem) {
        if (alarm.temporaryOverrideTime == null) return // Nothing to do

        val updated = alarm.copy(temporaryOverrideTime = null)
        saveAlarm(updated, isNew = false)
    }

    fun saveAlarm(alarm: AlarmItem, isNew: Boolean) {
        if (isNew) {
            val realId = AlarmRepository.getNextAlarmId(context)
            val finalAlarm = alarm.copy(id = realId)
            AlarmRepository.addAlarm(context, finalAlarm)
            
            // Reschedule with correct group offset
            val group = groups.find { it.id == finalAlarm.groupId }
            val offset = group?.offsetMinutes ?: 0
            scheduler.schedule(finalAlarm, offset)
        } else {
            AlarmRepository.updateAlarm(context, alarm)
            // Reschedule
            val group = groups.find { it.id == alarm.groupId }
            val offset = group?.offsetMinutes ?: 0
            scheduler.schedule(alarm, offset)
        }
        NotificationRenderer.refreshAll(context)
    }

    fun deleteAlarm(alarm: AlarmItem) {
        AlarmRepository.deleteAlarm(context, alarm)
        scheduler.cancel(alarm)
        NotificationRenderer.refreshAll(context)
    }

    // --- TIMER INTENTS ---


    fun startTimer(seconds: Int) {
        if (seconds <= 0) return
        val tId = AlarmRepository.getNextTimerId(context)
        val endTime = System.currentTimeMillis() + (seconds * 1000)
        val newTimer = TimerItem(
            id = tId,
            durationSeconds = seconds,
            endTime = endTime,
            totalDuration = (seconds * 1000).toLong()
        )
        AlarmRepository.addTimer(context, newTimer)
        scheduler.scheduleExact(endTime, tId, "TIMER")

        NotificationRenderer.refreshAll(context)

        // START THE SERVICE TO KEEP ALIVE
        val serviceIntent = android.content.Intent(context, TimerRunningService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    fun stopTimer(timerId: Int) {
        // We use the Receiver intent to ensure clean shutdown (Notifications etc)
        val intent = android.content.Intent(context, AlarmReceiver::class.java).apply {
            action = "STOP_SPECIFIC_TIMER"
            putExtra("TIMER_ID", timerId)
        }
        context.sendBroadcast(intent)
        NotificationRenderer.refreshAll(context)
    }
}