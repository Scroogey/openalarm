package de.laurik.openalarm

import android.content.Context
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

@Serializable
data class BackupData(
    val version: Int = 1,
    val settings: Map<String, String>,
    val settingTypes: Map<String, String>, // "bool", "int", "string", "float", "long"
    val groups: List<AlarmGroupEntity>,
    val alarms: List<AlarmItem>
)

object BackupManager {
    private const val TAG = "BackupManager"
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    suspend fun exportData(context: Context, outputStream: OutputStream): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(context).alarmDao()
                val groups = db.getAllGroups()
                val alarms = groups.flatMap { db.getAlarmsForGroup(it.id) }.map { it.toBackup() }

                val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                val allEntries = prefs.all
                val settings = mutableMapOf<String, String>()
                val settingTypes = mutableMapOf<String, String>()

                for ((key, value) in allEntries) {
                    if (value == null) continue
                    settings[key] = value.toString()
                    settingTypes[key] = when (value) {
                        is Boolean -> "bool"
                        is Int -> "int"
                        is Float -> "float"
                        is Long -> "long"
                        else -> "string"
                    }
                }

                val backup = BackupData(
                    settings = settings,
                    settingTypes = settingTypes,
                    groups = groups,
                    alarms = alarms
                )

                val content = json.encodeToString(backup)
                outputStream.write(content.toByteArray())
                outputStream.flush()
                true
            } catch (e: Exception) {
                Log.e(TAG, "Export failed", e)
                false
            } finally {
                try { outputStream.close() } catch (e: Exception) {}
            }
        }
    }

    suspend fun importData(context: Context, inputStream: InputStream): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val content = inputStream.bufferedReader().use { it.readText() }
                val backup = json.decodeFromString<BackupData>(content)

                // 1. Restore Settings
                val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                val editor = prefs.edit()
                editor.clear() // Remove existing settings
                for ((key, value) in backup.settings) {
                    val type = backup.settingTypes[key]
                    when (type) {
                        "bool" -> editor.putBoolean(key, value.toBoolean())
                        "int" -> editor.putInt(key, value.toInt())
                        "float" -> editor.putFloat(key, value.toFloat())
                        "long" -> editor.putLong(key, value.toLong())
                        else -> editor.putString(key, value)
                    }
                }
                editor.commit()

                // 2. Clear and Restore DB
                val db = AppDatabase.getDatabase(context).alarmDao()
                val dbInstance = AppDatabase.getDatabase(context)
                
                dbInstance.runInTransaction {
                    // Transactional clear and insert
                }
                
                // Clear existing
                db.clearAllAlarms()
                db.clearAllGroups()
                db.clearAllTimers()
                db.clearAllInterrupted()

                // Insert groups first (due to foreign key)
                backup.groups.forEach { db.insertGroup(it) }
                // Insert alarms
                backup.alarms.forEach { db.insertAlarm(it) }

                // 3. Refresh Application State
                SettingsRepository.getInstance(context).refreshAll()
                AlarmRepository.forceReload(context)

                // 4. Reschedule Alarms
                val scheduler = AlarmScheduler(context)
                backup.alarms.forEach { alarm ->
                    if (alarm.isEnabled) {
                        val group = backup.groups.find { it.id == alarm.groupId }
                        scheduler.schedule(alarm, group?.offsetMinutes ?: 0)
                    } else {
                        scheduler.cancel(alarm)
                    }
                }

                true
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                false
            } finally {
                try { inputStream.close() } catch (e: Exception) {}
            }
        }
    }

    private fun AlarmItem.toBackup(): AlarmItem {
        return this.copy(
            temporaryOverrideTime = null,
            snoozeUntil = null,
            currentSnoozeCount = 0,
            skippedUntil = 0L,
            lastTriggerTime = 0L
        )
    }
}
