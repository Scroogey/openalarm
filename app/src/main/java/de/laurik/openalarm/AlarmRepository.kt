package de.laurik.openalarm

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Internal Cache (UI observes this)
internal object InternalDataStore {
    val groups = mutableStateListOf<AlarmGroup>()
    val activeTimers = mutableStateListOf<TimerItem>()
    val interruptedItems = mutableListOf<InterruptedItem>()

    var currentRingingId: Int = -1

    // CONSTANTS
    const val NOTIF_COLOR = 0xFF101010.toInt()
    const val TIMER_RUNNING_COLOR = 0xFFE65100.toInt()
    const val TIMER_DONE_COLOR = 0xFFB71C1C.toInt()
}

object AlarmRepository {
    // Scope for database writes
    private val scope = CoroutineScope(Dispatchers.IO)
    private var isLoaded = false

    // Exposed State
    val groups: SnapshotStateList<AlarmGroup> get() = InternalDataStore.groups
    val activeTimers: SnapshotStateList<TimerItem> get() = InternalDataStore.activeTimers
    val currentRingingId: Int get() = InternalDataStore.currentRingingId

    val NOTIF_COLOR = InternalDataStore.NOTIF_COLOR
    val TIMER_RUNNING_COLOR = InternalDataStore.TIMER_RUNNING_COLOR
    val TIMER_DONE_COLOR = InternalDataStore.TIMER_DONE_COLOR

    suspend fun ensureLoaded(context: Context) {
        if (isLoaded) return

        val (uiGroups, dbTimers, dbInterrupted) = withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context).alarmDao()

            val dbGroups = db.getAllGroups()
            val loadedGroups = mutableListOf<AlarmGroup>()

            if (dbGroups.isEmpty()) {
                // CRITICAL FIX: Hardcode ID to "default" so Dashboard can find it
                val default = AlarmGroupEntity(
                    id = "default",
                    name = "Default",
                    colorArgb = 0xFFFFFFFF.toInt()
                )
                db.insertGroup(default)

                loadedGroups.add(
                    AlarmGroup(
                        id = default.id,
                        name = default.name,
                        offsetMinutes = default.offsetMinutes,
                        skippedUntil = 0L,
                        colorArgb = default.colorArgb
                    )
                )
            } else {
                dbGroups.forEach { g ->
                    val alarms = db.getAlarmsForGroup(g.id).toMutableList()
                    val uiGroup = AlarmGroup(
                        id = g.id,
                        name = g.name,
                        offsetMinutes = g.offsetMinutes,
                        skippedUntil = g.skippedUntil,
                        colorArgb = g.colorArgb // Pass Color
                    )
                    uiGroup.alarms.addAll(alarms)
                    loadedGroups.add(uiGroup)
                }
            }

            val timers = db.getAllTimers()
            val interrupted = db.getInterruptedItems()

            Triple(loadedGroups, timers, interrupted)
        }

        InternalDataStore.groups.clear()
        InternalDataStore.groups.addAll(uiGroups)

        InternalDataStore.activeTimers.clear()
        InternalDataStore.activeTimers.addAll(dbTimers)

        InternalDataStore.interruptedItems.clear()
        InternalDataStore.interruptedItems.addAll(dbInterrupted)

        isLoaded = true
    }

    suspend fun forceReload(context: Context) {
        isLoaded = false
        ensureLoaded(context)
    }

    // --- ALARMS ---
    fun getAlarm(id: Int): AlarmItem? {
        return InternalDataStore.groups.flatMap { it.alarms }.find { it.id == id }
    }

    fun addAlarm(context: Context, alarm: AlarmItem) {

        if (InternalDataStore.groups.isEmpty()) {
            // Create default in memory so we can add the alarm
            InternalDataStore.groups.add(
                AlarmGroup(id="default", name="Default", offsetMinutes=0, colorArgb=0xFFFFFFFF.toInt())
            )
        }

        // Find correct group UI object or fallback to first (default)

        val targetGroup = InternalDataStore.groups.find { it.id == alarm.groupId }
            ?: InternalDataStore.groups.firstOrNull()

        targetGroup?.alarms?.add(alarm)

        scope.launch {
            val db = AppDatabase.getDatabase(context).alarmDao()
            val groups = db.getAllGroups()
            if (groups.none { it.id == alarm.groupId }) {
                db.insertGroup(AlarmGroupEntity(id="default", name="Default"))
            }
            db.insertAlarm(alarm)
        }
    }

    fun updateAlarm(context: Context, alarm: AlarmItem) {
        // UI Update: Remove from old location, add to new (in case GroupID changed)
        InternalDataStore.groups.forEach { it.alarms.removeIf { a -> a.id == alarm.id } }

        val targetGroup = InternalDataStore.groups.find { it.id == alarm.groupId }
            ?: InternalDataStore.groups.firstOrNull()
        targetGroup?.alarms?.add(alarm)

        scope.launch { AppDatabase.getDatabase(context).alarmDao().updateAlarm(alarm) }
    }

    fun deleteAlarm(context: Context, alarm: AlarmItem) {
        InternalDataStore.groups.forEach { it.alarms.remove(alarm) }
        InternalDataStore.interruptedItems.removeAll { it.id == alarm.id && it.type == "ALARM" }
        // Cancel any pending notifications
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(alarm.id)
        // Cancel any pending intents
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("ALARM_ID", alarm.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)

        scope.launch {
            try {
                AppDatabase.getDatabase(context).alarmDao().deleteAlarm(alarm)
            } catch (e: Exception) {
                Log.e("AlarmRepository", "Error deleting alarm from DB", e)
            }
        }
    }

    fun getNextAlarmId(context: Context): Int {
        var candidate = 1
        val memIds = InternalDataStore.groups.flatMap { it.alarms }.map { it.id }.toSet()
        while(memIds.contains(candidate)) candidate++
        return candidate
    }

    // --- GROUPS ---
    suspend fun addGroup(context: Context, group: AlarmGroupEntity) {
        val uiGroup = AlarmGroup(group.id, group.name, group.offsetMinutes, group.skippedUntil, group.colorArgb)
        InternalDataStore.groups.add(uiGroup)
        AppDatabase.getDatabase(context).alarmDao().insertGroup(group)
    }

    suspend fun updateGroup(context: Context, group: AlarmGroupEntity) {
        val uiGroup = InternalDataStore.groups.find { it.id == group.id }
        if (uiGroup != null) {
            uiGroup.offsetMinutes = group.offsetMinutes
            uiGroup.skippedUntil = group.skippedUntil
            uiGroup.colorArgb = group.colorArgb
        }
        AppDatabase.getDatabase(context).alarmDao().updateGroup(group)
    }

    suspend fun deleteGroup(context: Context, group: AlarmGroup, keepAlarms: Boolean) {
        val db = AppDatabase.getDatabase(context).alarmDao()
        val defaultId = "default"

        if (keepAlarms) {
            // Move UI Alarms
            val defGroupUi = InternalDataStore.groups.find { it.id == defaultId }
            if (defGroupUi != null) {
                // Update groupId on items
                val movedAlarms = group.alarms.map { it.copy(groupId = defaultId) }
                defGroupUi.alarms.addAll(movedAlarms)
            }
            db.moveAlarmsToGroup(group.id, defaultId)
        }

        InternalDataStore.groups.remove(group)
        db.deleteGroup(AlarmGroupEntity(group.id, group.name, group.offsetMinutes, group.skippedUntil, group.colorArgb))
    }

    // --- TIMERS / INTERRUPTED ---
    fun getTimer(id: Int): TimerItem? = InternalDataStore.activeTimers.find { it.id == id }

    fun addTimer(context: Context, timer: TimerItem) {
        InternalDataStore.activeTimers.add(timer)
        scope.launch { AppDatabase.getDatabase(context).alarmDao().insertTimer(timer) }
    }

    fun updateTimer(context: Context, timer: TimerItem) {
        val index = InternalDataStore.activeTimers.indexOfFirst { it.id == timer.id }
        if (index != -1) {
            InternalDataStore.activeTimers[index] = timer
        } else {
            InternalDataStore.activeTimers.add(timer)
        }
        scope.launch { AppDatabase.getDatabase(context).alarmDao().insertTimer(timer) }
    }

    fun removeTimer(context: Context, id: Int) {
        val t = getTimer(id)
        if (t != null) {
            InternalDataStore.activeTimers.remove(t)
            scope.launch { AppDatabase.getDatabase(context).alarmDao().deleteTimer(t) }
        }
    }

    fun getNextTimerId(context: Context): Int {
        var candidate = 1001
        val memIds = InternalDataStore.activeTimers.map { it.id }.toSet()
        while(memIds.contains(candidate)) candidate++
        return candidate
    }

    fun addInterruptedItem(context: Context, item: InterruptedItem) {
        // Enforce uniqueness: Remove existing entry for this ID to prevent duplicates
        InternalDataStore.interruptedItems.removeAll { it.id == item.id }
        val itemExists = if (item.type == "TIMER") {
            getTimer(item.id) != null
        } else {
            getAlarm(item.id) != null
        }

        if (itemExists) {
            InternalDataStore.interruptedItems.add(item)
            scope.launch {
                with(AppDatabase.getDatabase(context).alarmDao()) {
                    deleteInterrupted(item) // Remove old entry if exists
                    insertInterrupted(item)
                }
            }
        }
    }

    fun popInterruptedItem(context: Context): InterruptedItem? {
        if (InternalDataStore.interruptedItems.isEmpty()) {
            return null
        }

        // Get the oldest item (FIFO)
        val item = InternalDataStore.interruptedItems.removeAt(0)

        // Remove from database
        scope.launch {
            try {
                AppDatabase.getDatabase(context).alarmDao().deleteInterrupted(item)
            } catch (e: Exception) {
                Log.e("AlarmRepository", "Error deleting interrupted item from DB", e)
            }
        }

        // Verify the item still exists before returning it
        val itemExists = if (item.type == "TIMER") {
            getTimer(item.id) != null
        } else {
            getAlarm(item.id) != null
        }

        return if (itemExists) item else null
    }
    fun setCurrentRingingId(id: Int) { InternalDataStore.currentRingingId = id }
}