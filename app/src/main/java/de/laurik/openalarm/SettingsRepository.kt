package de.laurik.openalarm

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode {
    SYSTEM, LIGHT, DARK
}

class SettingsRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(getVideoThemeMode())
    val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private val _isPureBlack = MutableStateFlow(prefs.getBoolean("is_pure_black", false))
    val isPureBlack: StateFlow<Boolean> = _isPureBlack.asStateFlow()

    private val _quickAdjustPresets = MutableStateFlow(getIntList("quick_adjust_presets", listOf(10, 30, 60)))
    val quickAdjustPresets: StateFlow<List<Int>> = _quickAdjustPresets.asStateFlow()

    private val _timerPresets = MutableStateFlow(getIntList("timer_presets", listOf(1, 5, 10)))
    val timerPresets: StateFlow<List<Int>> = _timerPresets.asStateFlow()

    private val _defaultSnooze = MutableStateFlow(prefs.getInt("def_snooze", 10))
    val defaultSnooze: StateFlow<Int> = _defaultSnooze.asStateFlow()

    private val _defaultAutoStop = MutableStateFlow(prefs.getInt("def_auto_stop", 10))
    val defaultAutoStop: StateFlow<Int> = _defaultAutoStop.asStateFlow()

    // --- TIMER SETTINGS ---
    private val _timerRingtone = MutableStateFlow(prefs.getString("timer_ringtone", null))
    val timerRingtone: StateFlow<String?> = _timerRingtone.asStateFlow()

    private val _timerVolume = MutableStateFlow(prefs.getFloat("timer_volume", 1.0f))
    val timerVolume: StateFlow<Float> = _timerVolume.asStateFlow()

    private val _timerVibration = MutableStateFlow(prefs.getBoolean("timer_volume", true))
    val timerVibration: StateFlow<Boolean> = _timerVibration.asStateFlow()

    private val _timerTtsEnabled = MutableStateFlow(prefs.getBoolean("timer_tts_enabled", false))
    val timerTtsEnabled: StateFlow<Boolean> = _timerTtsEnabled.asStateFlow()

    private val _timerTtsText = MutableStateFlow(prefs.getString("timer_tts_text", "") ?: "")
    val timerTtsText: StateFlow<String> = _timerTtsText.asStateFlow()

    private val _defaultTimerAutoStop = MutableStateFlow(prefs.getInt("def_timer_auto_stop", 5))
    val defaultTimerAutoStop: StateFlow<Int> = _defaultTimerAutoStop.asStateFlow()

    // --- NEW: RINGING SCREEN & DEFAULTS ---
    private val _defaultRingingMode = MutableStateFlow(getRingingMode("def_ringing_mode", RingingScreenMode.CLEAN))
    val defaultRingingMode: StateFlow<RingingScreenMode> = _defaultRingingMode.asStateFlow()

    private val _defaultSnoozePresets = MutableStateFlow(getIntList("def_snooze_presets", listOf(5, 10, 15)))
    val defaultSnoozePresets: StateFlow<List<Int>> = _defaultSnoozePresets.asStateFlow()

    private val _timerAdjustPresets = MutableStateFlow(getIntList("timer_adjust_presets", listOf(60, 300)))
    val timerAdjustPresets: StateFlow<List<Int>> = _timerAdjustPresets.asStateFlow()

    // --- DEFAULT ALARM CONFIG ---
    private val _defVibrationEnabled = MutableStateFlow(prefs.getBoolean("def_alarm_vibration", true))
    val defVibrationEnabled: StateFlow<Boolean> = _defVibrationEnabled.asStateFlow()

    private val _defRingtoneUri = MutableStateFlow(prefs.getString("def_alarm_ringtone", null))
    val defRingtoneUri: StateFlow<String?> = _defRingtoneUri.asStateFlow()

    private val _defCustomVolume = MutableStateFlow(prefs.getFloat("def_alarm_volume", -1f).takeIf { it >= 0 })
    val defCustomVolume: StateFlow<Float?> = _defCustomVolume.asStateFlow()

    private val _defFadeInSeconds = MutableStateFlow(prefs.getInt("def_alarm_fade", 0))
    val defFadeInSeconds: StateFlow<Int> = _defFadeInSeconds.asStateFlow()

    private val _defTtsMode = MutableStateFlow(getTtsMode("def_alarm_tts", TtsMode.NONE))
    val defTtsMode: StateFlow<TtsMode> = _defTtsMode.asStateFlow()

    private val _defIsSingleUse = MutableStateFlow(prefs.getBoolean("def_alarm_single_use", false))
    val defIsSingleUse: StateFlow<Boolean> = _defIsSingleUse.asStateFlow()

    private val _defIsSelfDestroying = MutableStateFlow(prefs.getBoolean("def_alarm_self_destroy", false))
    val defIsSelfDestroying: StateFlow<Boolean> = _defIsSelfDestroying.asStateFlow()

    private val _defDaysOfWeek = MutableStateFlow(getIntList("def_alarm_days", listOf(1, 2, 3, 4, 5, 6, 7)))
    val defDaysOfWeek: StateFlow<List<Int>> = _defDaysOfWeek.asStateFlow()

    private val _defIsSnoozeEnabled = MutableStateFlow(prefs.getBoolean("def_alarm_snooze_enabled", true))
    val defIsSnoozeEnabled: StateFlow<Boolean> = _defIsSnoozeEnabled.asStateFlow()

    private val _defMaxSnoozes = MutableStateFlow(prefs.getInt("def_alarm_max_snoozes", -1).takeIf { it >= 0 })
    val defMaxSnoozes: StateFlow<Int?> = _defMaxSnoozes.asStateFlow()

    private val _defDirectSnooze = MutableStateFlow(prefs.getBoolean("def_alarm_direct_snooze", false))
    val defDirectSnooze: StateFlow<Boolean> = _defDirectSnooze.asStateFlow()

    private val _notifyBeforeEnabled = MutableStateFlow(prefs.getBoolean("notify_before_enabled", true))
    val notifyBeforeEnabled: StateFlow<Boolean> = _notifyBeforeEnabled.asStateFlow()

    private val _notifyBeforeMinutes = MutableStateFlow(prefs.getInt("notify_before_minutes", 120)) // Default 2 hours
    val notifyBeforeMinutes: StateFlow<Int> = _notifyBeforeMinutes.asStateFlow()

    fun refreshAll() {
        _themeMode.value = getVideoThemeMode()
        _isPureBlack.value = prefs.getBoolean("is_pure_black", false)
        _quickAdjustPresets.value = getIntList("quick_adjust_presets", listOf(10, 30, 60))
        _timerPresets.value = getIntList("timer_presets", listOf(1, 5, 10))
        _defaultSnooze.value = prefs.getInt("def_snooze", 10)
        _defaultAutoStop.value = prefs.getInt("def_auto_stop", 10)
        _timerRingtone.value = prefs.getString("timer_ringtone", null)
        _timerVolume.value = prefs.getFloat("timer_volume", 1.0f)
        _timerVibration.value = prefs.getBoolean("timer_vibration", true)
        _timerTtsEnabled.value = prefs.getBoolean("timer_tts_enabled", false)
        _timerTtsText.value = prefs.getString("timer_tts_text", "") ?: ""
        _defaultTimerAutoStop.value = prefs.getInt("def_timer_auto_stop", 5)
        _defaultRingingMode.value = getRingingMode("def_ringing_mode", RingingScreenMode.CLEAN)
        _defaultSnoozePresets.value = getIntList("def_snooze_presets", listOf(5, 10, 15))
        _timerAdjustPresets.value = getIntList("timer_adjust_presets", listOf(60, 300))
        _defVibrationEnabled.value = prefs.getBoolean("def_alarm_vibration", true)
        _defRingtoneUri.value = prefs.getString("def_alarm_ringtone", null)
        _defCustomVolume.value = prefs.getFloat("def_alarm_volume", -1f).takeIf { it >= 0 }
        _defFadeInSeconds.value = prefs.getInt("def_alarm_fade", 0)
        _defTtsMode.value = getTtsMode("def_alarm_tts", TtsMode.NONE)
        _defIsSingleUse.value = prefs.getBoolean("def_alarm_single_use", false)
        _defIsSelfDestroying.value = prefs.getBoolean("def_alarm_self_destroy", false)
        _defDaysOfWeek.value = getIntList("def_alarm_days", listOf(1, 2, 3, 4, 5, 6, 7))
        _defIsSnoozeEnabled.value = prefs.getBoolean("def_alarm_snooze_enabled", true)
        _defMaxSnoozes.value = prefs.getInt("def_alarm_max_snoozes", -1).takeIf { it >= 0 }
        _defDirectSnooze.value = prefs.getBoolean("def_alarm_direct_snooze", false)
        _notifyBeforeEnabled.value = prefs.getBoolean("notify_before_enabled", true)
        _notifyBeforeMinutes.value = prefs.getInt("notify_before_minutes", 120)
    }

    private fun getRingingMode(key: String, default: RingingScreenMode): RingingScreenMode {
        val name = prefs.getString(key, null) ?: return default
        return try { RingingScreenMode.valueOf(name) } catch (e: Exception) { default }
    }

    private fun getTtsMode(key: String, default: TtsMode): TtsMode {
        val name = prefs.getString(key, null) ?: return default
        return try { TtsMode.valueOf(name) } catch (e: Exception) { default }
    }

    private fun getVideoThemeMode(): AppThemeMode {
        val name = prefs.getString("theme_mode", AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        return try {
            AppThemeMode.valueOf(name)
        } catch (e: Exception) {
            AppThemeMode.SYSTEM
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _themeMode.value = mode
    }

    fun setPureBlack(enabled: Boolean) {
        prefs.edit().putBoolean("is_pure_black", enabled).apply()
        _isPureBlack.value = enabled
    }

    fun setQuickAdjustPresets(presets: List<Int>) {
        saveIntList("quick_adjust_presets", presets)
        _quickAdjustPresets.value = presets
    }

    fun setTimerPresets(presets: List<Int>) {
        saveIntList("timer_presets", presets)
        _timerPresets.value = presets
    }

    // --- SETTERS ---
    fun setDefaultSnooze(minutes: Int) {
        prefs.edit().putInt("def_snooze", minutes).apply()
        _defaultSnooze.value = minutes
    }

    fun setDefaultAutoStop(minutes: Int) {
        prefs.edit().putInt("def_auto_stop", minutes).apply()
        _defaultAutoStop.value = minutes
    }

    fun setTimerRingtone(uri: String?) {
        prefs.edit().putString("timer_ringtone", uri).apply()
        _timerRingtone.value = uri
    }

    fun setTimerVolume(vol: Float) {
        prefs.edit().putFloat("timer_volume", vol).apply()
        _timerVolume.value = vol
    }

    fun setTimerVibration(enabled: Boolean) {
        prefs.edit().putBoolean("timer_vibration", enabled).apply()
        _timerVibration.value = enabled
    }

    fun setTimerTts(enabled: Boolean, text: String) {
        prefs.edit()
            .putBoolean("timer_tts_enabled", enabled)
            .putString("timer_tts_text", text)
            .apply()
        _timerTtsEnabled.value = enabled
        _timerTtsText.value = text
    }

    fun setDefaultTimerAutoStop(minutes: Int) {
        prefs.edit().putInt("def_timer_auto_stop", minutes).apply()
        _defaultTimerAutoStop.value = minutes
    }

    fun setDefaultRingingMode(mode: RingingScreenMode) {
        prefs.edit().putString("def_ringing_mode", mode.name).apply()
        _defaultRingingMode.value = mode
    }

    fun setDefaultSnoozePresets(presets: List<Int>) {
        saveIntList("def_snooze_presets", presets)
        _defaultSnoozePresets.value = presets
    }

    fun setTimerAdjustPresets(presets: List<Int>) {
        val limited = presets.take(2)
        saveIntList("timer_adjust_presets", limited)
        _timerAdjustPresets.value = limited
    }

    // --- ALARM DEFAULTS SETTERS ---
    fun setDefVibration(enabled: Boolean) {
        prefs.edit().putBoolean("def_alarm_vibration", enabled).apply()
        _defVibrationEnabled.value = enabled
    }

    fun setDefRingtone(uri: String?) {
        prefs.edit().putString("def_alarm_ringtone", uri).apply()
        _defRingtoneUri.value = uri
    }

    fun setDefVolume(vol: Float?) {
        if (vol == null) prefs.edit().remove("def_alarm_volume").apply()
        else prefs.edit().putFloat("def_alarm_volume", vol).apply()
        _defCustomVolume.value = vol
    }

    fun setDefFadeIn(seconds: Int) {
        prefs.edit().putInt("def_alarm_fade", seconds).apply()
        _defFadeInSeconds.value = seconds
    }

    fun setDefTtsMode(mode: TtsMode) {
        prefs.edit().putString("def_alarm_tts", mode.name).apply()
        _defTtsMode.value = mode
    }

    fun setDefSingleUse(enabled: Boolean) {
        prefs.edit().putBoolean("def_alarm_single_use", enabled).apply()
        _defIsSingleUse.value = enabled
    }

    fun setDefSelfDestroy(enabled: Boolean) {
        prefs.edit().putBoolean("def_alarm_self_destroy", enabled).apply()
        _defIsSelfDestroying.value = enabled
    }

    fun setDefDaysOfWeek(days: List<Int>) {
        saveIntList("def_alarm_days", days)
        _defDaysOfWeek.value = days
    }

    fun setDefSnoozeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("def_alarm_snooze_enabled", enabled).apply()
        _defIsSnoozeEnabled.value = enabled
    }

    fun setDefMaxSnoozes(max: Int?) {
        if (max == null) prefs.edit().remove("def_alarm_max_snoozes").apply()
        else prefs.edit().putInt("def_alarm_max_snoozes", max).apply()
        _defMaxSnoozes.value = max
    }

    fun setDefDirectSnooze(enabled: Boolean) {
        prefs.edit().putBoolean("def_alarm_direct_snooze", enabled).apply()
        _defDirectSnooze.value = enabled
    }

    fun setNotifyBeforeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("notify_before_enabled", enabled).apply()
        _notifyBeforeEnabled.value = enabled
    }

    fun setNotifyBeforeMinutes(minutes: Int) {
        prefs.edit().putInt("notify_before_minutes", minutes).apply()
        _notifyBeforeMinutes.value = minutes
    }

    private fun getIntList(key: String, default: List<Int>): List<Int> {
        val str = prefs.getString(key, null) ?: return default
        return try {
            if (str.isEmpty()) emptyList() else str.split(",").map { it.toInt() }
        } catch (e: Exception) {
            default
        }
    }

    private fun saveIntList(key: String, list: List<Int>) {
        val str = list.joinToString(",")
        prefs.edit().putString(key, str).apply()
    }

    companion object {
        @Volatile
        private var INSTANCE: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsRepository(context).also { INSTANCE = it }
            }
        }
    }
}
