package de.laurik.openalarm

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    // 1. Expose State from Repository
    // Because Repository uses SnapshotStateList, Compose will update automatically
    val groups = AlarmRepository.groups
    val activeTimers = AlarmRepository.activeTimers
    val currentTime = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), System.currentTimeMillis())

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

        // Calculate where it would normally ring (preserving all the original logic)
        val minTime = if ((group?.skippedUntil ?: 0L) > System.currentTimeMillis()) {
            group!!.skippedUntil
        } else {
            System.currentTimeMillis()
        }

        val currentNext = AlarmUtils.getNextOccurrence(
            alarm.hour,
            alarm.minute,
            alarm.daysOfWeek,
            offset,
            alarm.temporaryOverrideTime,
            alarm.snoozeUntil,
            if (alarm.skippedUntil > minTime) alarm.skippedUntil else minTime
        )

        // Create an updated alarm with the new temporary override time
        // Let the scheduler handle the actual scheduling with the adjustment limit
        val updated = alarm.copy(
            temporaryOverrideTime = currentNext + (minutesToAdd * 60 * 1000)
        )

        // Save and schedule the alarm
        saveAlarm(updated, isNew = false)

        // The scheduler will handle the adjustment limit when scheduling
        AlarmScheduler(context).schedule(updated, offset)
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

    fun adjustGroupAlarms(group: AlarmGroup, minutes: Int) {
        viewModelScope.launch {
            try {
                // Get all enabled alarms in the group
                val alarmsToAdjust = group.alarms.filter { it.isEnabled }
                if (alarmsToAdjust.isEmpty()) {
                    Log.d("DashboardViewModel", "No enabled alarms to adjust in group ${group.id}")
                    return@launch
                }

                // Update each alarm individually
                alarmsToAdjust.forEach { alarm ->
                    // Calculate the next occurrence time
                    val nextOccurrence = AlarmUtils.getNextOccurrence(
                        alarm.hour,
                        alarm.minute,
                        alarm.daysOfWeek,
                        0, // Ignore group offset as requested
                        alarm.temporaryOverrideTime,
                        alarm.snoozeUntil,
                        System.currentTimeMillis()
                    )

                    // Update in database with the adjustment
                    val updatedAlarm = alarm.copy(
                        temporaryOverrideTime = nextOccurrence + (minutes * 60 * 1000L)
                    )
                    AlarmRepository.updateAlarm(context, updatedAlarm)

                    // Reschedule the alarm with the adjustment
                    // The scheduler will handle clamping the adjustment
                    AlarmScheduler(context).schedule(updatedAlarm, 0, minutes)
                }

                refreshAlarms()
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Error adjusting group alarms", e)
            }
        }
    }


    fun resetGroupAlarms(group: AlarmGroup) {
        viewModelScope.launch {
            try {
                // Create a transaction to ensure all updates happen together
                val db = AppDatabase.getDatabase(context).alarmDao()

                // Reset all alarms in the group in a single transaction
                group.alarms.forEach { alarm ->
                    if (alarm.temporaryOverrideTime != null) {
                        val updatedAlarm = alarm.copy(temporaryOverrideTime = null)

                        // Update in memory
                        InternalDataStore.groups.forEach { g ->
                            g.alarms.replaceAll { a ->
                                if (a.id == alarm.id) updatedAlarm else a
                            }
                        }

                        // Update in database
                        db.updateAlarm(updatedAlarm)

                        // Reschedule the alarm
                        val scheduler = AlarmScheduler(context)
                        val groupOffset = group.offsetMinutes
                        scheduler.schedule(updatedAlarm, groupOffset)
                    }
                }

                refreshAlarms()
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Error resetting group alarms", e)
                // Show error to user if needed
            }
        }
    }

    private fun refreshAlarms() {
        viewModelScope.launch {
            try {
                // Force reload from database
                AlarmRepository.ensureLoaded(context)
                // Refresh notifications
                NotificationRenderer.refreshAll(context)
            } catch (e: Exception) {
                Log.e("DashboardViewModel", "Error refreshing alarms", e)
            }
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

            // For new alarms, don't apply any adjustment
            val group = groups.find { it.id == finalAlarm.groupId }
            val offset = group?.offsetMinutes ?: 0
            scheduler.schedule(finalAlarm, offset)
        } else {
            AlarmRepository.updateAlarm(context, alarm)

            // For existing alarms, check if there's a temporary override
            val group = groups.find { it.id == alarm.groupId }
            val offset = group?.offsetMinutes ?: 0

            // If there's a temporary override, we need to calculate the adjustment
            if (alarm.temporaryOverrideTime != null) {
                val currentNext = AlarmUtils.getNextOccurrence(
                    alarm.hour,
                    alarm.minute,
                    alarm.daysOfWeek,
                    offset,
                    null, // Don't use temporary override for base calculation
                    alarm.snoozeUntil,
                    if (alarm.skippedUntil > System.currentTimeMillis()) alarm.skippedUntil else System.currentTimeMillis()
                )

                // Calculate the adjustment in minutes
                val adjustmentMinutes = ((alarm.temporaryOverrideTime!! - currentNext) / (60 * 1000)).toInt()

                // Schedule with the adjustment
                scheduler.schedule(alarm, offset, adjustmentMinutes)
            } else {
                // No adjustment, schedule normally
                scheduler.schedule(alarm, offset)
            }
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