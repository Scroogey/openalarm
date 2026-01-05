package de.laurik.openalarm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = SettingsRepository.getInstance(application)

    val themeMode = repository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppThemeMode.SYSTEM)

    fun setThemeMode(mode: AppThemeMode) {
        repository.setThemeMode(mode)
    }

    val isPureBlack = repository.isPureBlack
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setPureBlack(enabled: Boolean) = repository.setPureBlack(enabled)

    val quickAdjustPresets = repository.quickAdjustPresets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(10, 30, 60))

    val timerPresets = repository.timerPresets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(1, 5, 10))

    val defaultSnooze = repository.defaultSnooze
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10)

    val defaultAutoStop = repository.defaultAutoStop
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10)

    val timerRingtone = repository.timerRingtone
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val timerVibration = repository.timerVibration
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val timerVolume = repository.timerVolume
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val timerTtsEnabled = repository.timerTtsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val timerTtsText = repository.timerTtsText
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val defaultTimerAutoStop = repository.defaultTimerAutoStop
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)

    fun setDefaultSnooze(m: Int) = repository.setDefaultSnooze(m)
    fun setDefaultAutoStop(m: Int) = repository.setDefaultAutoStop(m)

    fun setTimerRingtone(uri: String?) = repository.setTimerRingtone(uri)
    fun setTimerVibration(enabled: Boolean) = repository.setTimerVibration(enabled)
    fun setTimerVolume(v: Float) = repository.setTimerVolume(v)
    fun setTimerTts(enabled: Boolean, text: String) = repository.setTimerTts(enabled, text)

    fun setQuickAdjustPresets(list: List<Int>) = repository.setQuickAdjustPresets(list)
    fun setTimerPresets(list: List<Int>) = repository.setTimerPresets(list)
    fun setDefaultTimerAutoStop(m: Int) = repository.setDefaultTimerAutoStop(m)

    // NEW: Ringing Mode & Presets
    val defaultRingingMode = repository.defaultRingingMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RingingScreenMode.CLEAN)
    
    fun setDefaultRingingMode(mode: RingingScreenMode) = repository.setDefaultRingingMode(mode)

    val defaultSnoozePresets = repository.defaultSnoozePresets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(5, 10, 15))
    
    fun setDefaultSnoozePresets(list: List<Int>) = repository.setDefaultSnoozePresets(list)

    val timerAdjustPresets = repository.timerAdjustPresets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(60, 300))
    
    fun setTimerAdjustPresets(list: List<Int>) = repository.setTimerAdjustPresets(list)

    // ALARM DEFAULTS
    val defVibrationEnabled = repository.defVibrationEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    fun setDefVibration(e: Boolean) = repository.setDefVibration(e)

    val defRingtoneUri = repository.defRingtoneUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun setDefRingtone(u: String?) = repository.setDefRingtone(u)

    val defCustomVolume = repository.defCustomVolume
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun setDefVolume(v: Float?) = repository.setDefVolume(v)

    val defFadeInSeconds = repository.defFadeInSeconds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    fun setDefFadeIn(s: Int) = repository.setDefFadeIn(s)

    val defTtsMode = repository.defTtsMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TtsMode.NONE)
    fun setDefTtsMode(m: TtsMode) = repository.setDefTtsMode(m)

    val defIsSingleUse = repository.defIsSingleUse
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    fun setDefSingleUse(e: Boolean) = repository.setDefSingleUse(e)

    val defIsSelfDestroying = repository.defIsSelfDestroying
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    fun setDefSelfDestroy(e: Boolean) = repository.setDefSelfDestroy(e)

    val defDaysOfWeek = repository.defDaysOfWeek
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(1, 2, 3, 4, 5))
    fun setDefDaysOfWeek(days: List<Int>) = repository.setDefDaysOfWeek(days)

    val defIsSnoozeEnabled = repository.defIsSnoozeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    fun setDefSnoozeEnabled(e: Boolean) = repository.setDefSnoozeEnabled(e)

    val defMaxSnoozes = repository.defMaxSnoozes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    fun setDefMaxSnoozes(m: Int?) = repository.setDefMaxSnoozes(m)

    val defDirectSnooze = repository.defDirectSnooze
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    fun setDefDirectSnooze(e: Boolean) = repository.setDefDirectSnooze(e)

    val notifyBeforeEnabled = repository.notifyBeforeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    fun setNotifyBeforeEnabled(e: Boolean) {
        repository.setNotifyBeforeEnabled(e)
        NotificationRenderer.refreshAll(getApplication())
        AlarmScheduler(getApplication()).scheduleNotificationUpdate()
    }

    val notifyBeforeMinutes = repository.notifyBeforeMinutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 120)
    fun setNotifyBeforeMinutes(m: Int) {
        repository.setNotifyBeforeMinutes(m)
        NotificationRenderer.refreshAll(getApplication())
        AlarmScheduler(getApplication()).scheduleNotificationUpdate()
    }

    fun createDefaultAlarm(hour: Int, minute: Int): AlarmItem {
        return AlarmItem(
            id = 0,
            hour = hour,
            minute = minute,
            groupId = "default",
            vibrationEnabled = defVibrationEnabled.value,
            ringtoneUri = defRingtoneUri.value,
            customVolume = defCustomVolume.value,
            fadeInSeconds = defFadeInSeconds.value,
            ttsMode = defTtsMode.value,
            isSingleUse = defIsSingleUse.value,
            isSelfDestroying = defIsSelfDestroying.value,
            daysOfWeek = defDaysOfWeek.value,
            isSnoozeEnabled = defIsSnoozeEnabled.value,
            directSnooze = defDirectSnooze.value,
            maxSnoozes = defMaxSnoozes.value,
            snoozePresets = null, // New alarms use null to inherit global defaults unless set specifically
            ringingScreenMode = defaultRingingMode.value
        )
    }
}
