package de.laurik.openalarm

import androidx.compose.runtime.getValue
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class TtsMode { NONE, ONCE, EVERY_MINUTE }
@Serializable
enum class AlarmType { SOFT, REGULAR, CRITICAL }
@Serializable
enum class RingingScreenMode { DEFAULT, EASY, CLEAN }

// --- ENTITIES (Database Tables) ---

@Serializable
@Entity(tableName = "alarm_groups")
data class AlarmGroupEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val offsetMinutes: Int = 0,
    val skippedUntil: Long = 0L,
    val colorArgb: Int = 0xFFFFFFFF.toInt()
)

@Serializable
@Entity(
    tableName = "alarms",
    foreignKeys = [
        ForeignKey(
            entity = AlarmGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE // If group is deleted, delete alarms
        )
    ],
    indices = [Index("groupId")]
)
data class AlarmItem(
    @PrimaryKey
    val id: Int, // We will manually manage IDs to keep them safe for PendingIntents
    val groupId: String,

    // Time
    var hour: Int,
    var minute: Int,
    var temporaryOverrideTime: Long? = null,
    var snoozeUntil: Long? = null,

    // If null, use global default from SettingsRepository
    val snoozeDuration: Int? = null,
    val autoStopDuration: Int? = null,

    // NEW: Snooze Settings
    var isSnoozeEnabled: Boolean = true,
    var directSnooze: Boolean = false, // If true, snooze immediately on trigger
    var maxSnoozes: Int? = null, // null = unlimited, otherwise count
    var currentSnoozeCount: Int = 0,

    // Config
    var isEnabled: Boolean = true,
    val type: AlarmType = AlarmType.REGULAR,
    var label: String = "",

    // We will use a TypeConverter for this List
    var daysOfWeek: List<Int> = listOf(1, 2, 3, 4, 5),
    var skippedUntil: Long = 0L,

    // Style
    var vibrationEnabled: Boolean = true,
    var ringtoneUri: String? = null,
    var customVolume: Float? = null,
    var fadeInSeconds: Int = 0,
    var ttsMode: TtsMode = TtsMode.NONE,
    var isSingleUse: Boolean = false,
    var isSelfDestroying: Boolean = false,
    
    // NEW: Ringing Screen Revamp
    var ringingScreenMode: RingingScreenMode = RingingScreenMode.DEFAULT,
    var backgroundType: String = "COLOR", // COLOR, GRADIENT, IMAGE
    var backgroundValue: String = "0xFF000000", // hex color, or comma-separated hex colors, or URI
    var snoozePresets: List<Int>? = null, // null = use global defaults

    // Internal
    var lastTriggerTime: Long = 0L
)

@Entity(tableName = "timers")
data class TimerItem(
    @PrimaryKey
    val id: Int,
    val durationSeconds: Int,
    val endTime: Long,
    val totalDuration: Long,
)

@Entity(tableName = "interrupted_items")
data class InterruptedItem(
    @PrimaryKey(autoGenerate = true)
    val dbId: Int = 0, // Auto-id for DB storage
    val id: Int,       // The actual Alarm/Timer ID
    val type: String,
    val label: String = "",
    val timestamp: Long
)

// --- UI HELPERS (Not stored in DB directly) ---
// This class combines the Group Entity + Its List of Alarms
class AlarmGroup(
    val id: String,
    val name: String,
    var offsetMinutes: Int,
    var skippedUntil: Long = 0L,
    var colorArgb: Int,
    // Using a class with mutableState for UI toggle prevents full list recomposition
    isExpandedInitial: Boolean = false
) {
    var isExpanded by mutableStateOf(isExpandedInitial)
    val alarms: MutableList<AlarmItem> = mutableStateListOf()
}