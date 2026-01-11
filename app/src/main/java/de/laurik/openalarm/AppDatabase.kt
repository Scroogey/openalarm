package de.laurik.openalarm

import android.content.Context
import androidx.room.*

// 1. TYPE CONVERTERS (To save Lists and Enums)
class Converters {
    @TypeConverter
    fun fromDaysList(days: List<Int>): String = days.joinToString(",")

    @TypeConverter
    fun toDaysList(data: String): List<Int> {
        if (data.isEmpty()) return emptyList()
        return data.split(",").mapNotNull { it.toIntOrNull() }
    }

    @TypeConverter
    fun fromAlarmType(type: AlarmType): String = type.name
    @TypeConverter
    fun toAlarmType(data: String): AlarmType = AlarmType.valueOf(data)

    @TypeConverter
    fun fromTtsMode(mode: TtsMode): String = mode.name
    @TypeConverter
    fun toTtsMode(data: String): TtsMode = TtsMode.NONE.let { try { TtsMode.valueOf(data) } catch(e:Exception){it} }

    @TypeConverter
    fun fromRingingScreenMode(mode: RingingScreenMode): String = mode.name
    @TypeConverter
    fun toRingingScreenMode(data: String): RingingScreenMode = RingingScreenMode.DEFAULT.let { try { RingingScreenMode.valueOf(data) } catch(e:Exception){it} }
}

// 2. DATA ACCESS OBJECT (The SQL commands)
@Dao
interface AlarmDao {
    // --- GROUPS & ALARMS ---

    @Transaction
    @Query("SELECT * FROM alarm_groups")
    suspend fun getAllGroups(): List<AlarmGroupEntity>

    @Update
    suspend fun updateGroup(group: AlarmGroupEntity)

    @Delete
    suspend fun deleteGroup(group: AlarmGroupEntity) // Cascades (deletes alarms) due to ForeignKey

    @Query("UPDATE alarms SET groupId = :targetGroupId WHERE groupId = :oldGroupId")
    suspend fun moveAlarmsToGroup(oldGroupId: String, targetGroupId: String)

    @Query("SELECT * FROM alarms WHERE groupId = :groupId")
    suspend fun getAlarmsForGroup(groupId: String): List<AlarmItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: AlarmGroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlarm(alarm: AlarmItem)

    @Update
    suspend fun updateAlarm(alarm: AlarmItem)

    @Delete
    suspend fun deleteAlarm(alarm: AlarmItem)

    // --- TIMERS ---

    @Query("SELECT * FROM timers")
    suspend fun getAllTimers(): List<TimerItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimer(timer: TimerItem)

    @Delete
    suspend fun deleteTimer(timer: TimerItem)

    // --- INTERRUPTED STACK ---

    @Query("SELECT * FROM interrupted_items ORDER BY dbId ASC")
    suspend fun getInterruptedItems(): List<InterruptedItem>

    @Insert
    suspend fun insertInterrupted(item: InterruptedItem)

    @Delete
    suspend fun deleteInterrupted(item: InterruptedItem)

    @Query("DELETE FROM alarms")
    suspend fun clearAllAlarms()

    @Query("DELETE FROM alarm_groups")
    suspend fun clearAllGroups()

    @Query("DELETE FROM timers")
    suspend fun clearAllTimers()

    @Query("DELETE FROM interrupted_items")
    suspend fun clearAllInterrupted()

    // --- ID GENERATION ---
    @Query("SELECT MAX(id) FROM alarms")
    suspend fun getMaxAlarmId(): Int?

    @Query("SELECT MAX(id) FROM timers")
    suspend fun getMaxTimerId(): Int?
}

// 3. DATABASE INSTANCE
@Database(
    entities = [AlarmGroupEntity::class, AlarmItem::class, TimerItem::class, InterruptedItem::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "openalarm_db"
                ).fallbackToDestructiveMigration()
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}