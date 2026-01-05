package de.laurik.openalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.*
import android.os.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.core.net.toUri
import de.laurik.openalarm.utils.AppLogger

// TODO: fix two alarms ringing at the same time not being correctly stopped

/**
 * Service that handles playing alarm tones, managing audio focus, and handling actions like snooze and stop.
 *
 * This service manages:
 * - Playing alarm tones with configurable volume and fade-in effects
 * - Handling audio focus changes
 * - Managing vibration
 * - Text-to-speech functionality for announcing the time
 * - Handling timeout logic for alarms
 * - Managing foreground notifications
 */
class RingtoneService : Service(), TextToSpeech.OnInitListener {
    private val logger by lazy {
        try {
            (applicationContext as BaseApplication).getLogger()
        } catch (e: Exception) {
            AppLogger(applicationContext)
        }
    }

    companion object {
        private const val TAG = "RingtoneService"
        private const val LOADING_NOTIF_ID = 69999
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var ttsMode = TtsMode.NONE
    private var customTtsText: String? = null

    private var targetSliderValue = 1.0f
    private val duckVolume = 0.1f

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var fadeJob: Job? = null
    private var ttsJob: Job? = null
    private var timeoutJob: Job? = null

    private var currentRingingId: Int = -1
    private var currentType: String = "NONE"

    /**
     * Listener for audio focus changes to adjust volume appropriately.
     */
    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> try { mediaPlayer?.setVolume(0.1f, 0.1f) } catch (e: Exception) {}
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> try { mediaPlayer?.setVolume(0.1f, 0.1f) } catch (e: Exception) {}
            AudioManager.AUDIOFOCUS_GAIN -> try { mediaPlayer?.setVolume(targetSliderValue, targetSliderValue) } catch (e: Exception) {}
        }
    }

    override fun onCreate() {
        super.onCreate()
        logger.d(TAG, "Service created")
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        tts = TextToSpeech(this, this)
    }

    /**
     * Called when the TextToSpeech engine is initialized.
     */
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            isTtsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts?.setAudioAttributes(attrs)

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    try { mediaPlayer?.setVolume(duckVolume, duckVolume) } catch(e:Exception){}
                }
                override fun onDone(utteranceId: String?) {
                    serviceScope.launch { try { if (mediaPlayer?.isPlaying == true) mediaPlayer?.setVolume(targetSliderValue, targetSliderValue) } catch(e:Exception){} }
                }
                override fun onError(utteranceId: String?) {
                    serviceScope.launch { try { mediaPlayer?.setVolume(targetSliderValue, targetSliderValue) } catch(e:Exception){} }
                }
            })
        } else {
            logger.e(TAG, "TextToSpeech initialization failed")
        }
    }

    /**
     * Handles the intent when the service is started.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        logger.d(TAG, "onStartCommand called with intent: ${intent?.action}")

        if (intent == null) {
            logger.w(TAG, "Service restarted with null intent (Sticky). Stopping.")
            stopSelf()
            return START_NOT_STICKY
        }

        val placeholderNotif = createPlaceholderNotification()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(LOADING_NOTIF_ID, placeholderNotif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(LOADING_NOTIF_ID, placeholderNotif)
            }
        } catch (e: Exception) {
            logger.e(TAG, "Failed to start placeholder foreground", e)
        }

        serviceScope.launch {
            try {
                logger.d(TAG, "Loading database...")
                AlarmRepository.ensureLoaded(applicationContext)
                handleIntent(intent)
            } catch (e: Exception) {
                logger.e(TAG, "Error in onStartCommand", e)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    /**
     * New helper for lightweight notification without DB access to instantly start ringing
     **/
    private fun createPlaceholderNotification(): Notification {
        val channelId = "ALARM_CHANNEL_ID"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val chan = NotificationChannel(channelId, "Alarm Ringing", NotificationManager.IMPORTANCE_HIGH)
            chan.setSound(null, null)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(chan)
        }

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Alarm")
            .setContentText("Loading...")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .build()
    }

    /**
     * Handles the intent based on its action.
     *
     * @param intent The received intent
     */
    private fun handleIntent(intent: Intent?) {
        val action = intent?.action
        logger.d(TAG, "Handling action: $action")

        if (action == "STOP_RINGING" || action == "STOP") {
            handleStopAction(intent)
            return
        }
        if (action == "SNOOZE_1" || action == "SNOOZE_CUSTOM") {
            handleSnoozeAction(intent)
            return
        }
        if (action == "ADD_TIME") {
            handleAddTimeAction(intent)
            return
        }
        handleStartNewAction(intent)
    }

    /**
     * Handles the stop action.
     */
    private fun handleStopAction(intent: Intent?) {
        try {
            val targetId = intent?.getIntExtra("TARGET_ID", -1) ?: -1
            logger.d(TAG, "Stop action received for ID: $targetId")

            if (targetId == currentRingingId || targetId == -1) {
                handleStopCurrentAlarm()
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error handling stop action", e)
            // Optionally notify the user or take other action
        }
    }

    /**
     * Handles stopping the current alarm.
     */
    private fun handleStopCurrentAlarm() {
        // 1. CLEANUP STACK: Remove the alarm we are stopping from the interrupted stack.
        // This prevents the "Ring Again" bug where a stale duplicate resumes immediately.
        if (currentRingingId != -1) {
            // Remove from memory
            InternalDataStore.interruptedItems.removeAll { it.id == currentRingingId }
        }

        val alarm = AlarmRepository.getAlarm(currentRingingId)
        if (alarm != null) {
            val now = System.currentTimeMillis()
            val group = AlarmRepository.groups.find { it.id == alarm.groupId }
            val offset = group?.offsetMinutes ?: 0

            // 1. Calculate the "Normal" next occurrence of this alarm (ignoring current overrides)
            val normalTime = AlarmUtils.getNextOccurrence(
                hour = alarm.hour,
                minute = alarm.minute,
                daysOfWeek = alarm.daysOfWeek,
                groupOffsetMinutes = offset,
                temporaryOverrideTime = null,
                snoozeUntil = null,
                minTimestamp = now - 60_000 // look back slightly
            )

            // 2. 6-Hour Rule: If the normal time is within 6 hours of now, skip it.
            val shouldSkip = normalTime <= now + (6 * 60 * 60 * 1000)

            // 3. Reset Snooze & Override, applying Skip if needed
            if (alarm.isSelfDestroying) {
                logger.d(TAG, "Alarm is self-destroying, deleting: ID=${alarm.id}")
                AlarmRepository.deleteAlarm(this, alarm)
            } else {
                val updated = alarm.copy(
                    isEnabled = if (alarm.isSingleUse) false else alarm.isEnabled,
                    snoozeUntil = null,
                    currentSnoozeCount = 0,
                    temporaryOverrideTime = null,
                    skippedUntil = if (shouldSkip) normalTime + 1000 else alarm.skippedUntil
                )
                logger.d(TAG, "Updating alarm state: ID=${alarm.id}, Skip=$shouldSkip")
                AlarmRepository.updateAlarm(this, updated)
            }

            Toast.makeText(applicationContext, R.string.toast_alarm_stopped, Toast.LENGTH_SHORT).show()
            rescheduleAlarm(currentRingingId)
        }

        StatusHub.trigger(StatusEvent.Stopped(currentRingingId, currentType))
        if (currentType == "TIMER") {
            logger.d(TAG, "Removing timer: ID=$currentRingingId")
            AlarmRepository.removeTimer(this, currentRingingId)
        }

        stopForeground(true)
        stopAll()
        AlarmRepository.setCurrentRingingId(-1)
        NotificationRenderer.refreshAll(this)
        checkStackAndResume()
    }

    /**
     * Handles the snooze action.
     */
    private fun handleSnoozeAction(intent: Intent?) {
        try {
            val targetId = intent?.getIntExtra("TARGET_ID", -1) ?: -1
            val finalId = if (targetId != -1) targetId else currentRingingId
            val alarm = AlarmRepository.getAlarm(finalId)
            val customMins = if (intent?.action == "SNOOZE_CUSTOM") intent.getIntExtra("MINUTES", 10) else null

            if (alarm != null) {
                logger.d(TAG, "Snoozing alarm: ID=$finalId, Custom mins=$customMins")
                handleSnooze(alarm, isAuto = false, customMinutes = customMins)
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error handling snooze action", e)
            // Optionally notify the user or take other action
        }
    }

    /**
     * Handles the add time action for timers.
     */
    private fun handleAddTimeAction(intent: Intent?) {
        try {
            val seconds = intent?.getIntExtra("SECONDS", 60) ?: 60
            val targetId = intent?.getIntExtra("TARGET_ID", currentRingingId) ?: currentRingingId

            if (targetId == -1) {
                logger.w(TAG, "Add time action with invalid target ID")
                return
            }

            logger.d(TAG, "Adding time to timer: ID=$targetId, Seconds=$seconds")

            val timer = AlarmRepository.getTimer(targetId)
            if (timer != null) {
                val now = System.currentTimeMillis()
                val currentRemaining = (timer.endTime - now).coerceAtLeast(0L)
                val newEndTime = now + currentRemaining + (seconds * 1000L)

                val newTimer = timer.copy(endTime = newEndTime, totalDuration = timer.totalDuration + (seconds * 1000L))
                AlarmRepository.updateTimer(this@RingtoneService, newTimer)
                val scheduler = AlarmScheduler(this@RingtoneService)
                scheduler.scheduleExact(newEndTime, targetId, "TIMER")

                val tSvc = Intent(this@RingtoneService, TimerRunningService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(tSvc)
                } else {
                    startService(tSvc)
                }

                StatusHub.trigger(StatusEvent.Extended(targetId, "TIMER", newEndTime))

                if (targetId == currentRingingId) {
                    stopForeground(true)
                    stopAll()
                    AlarmRepository.setCurrentRingingId(-1)
                    checkStackAndResume()
                }
                NotificationRenderer.refreshAll(this@RingtoneService)
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error handling add time action", e)
            // Optionally notify the user or take other action
        }
    }

    /**
     * Handles the start new alarm action.
     */
    private fun handleStartNewAction(intent: Intent?) {
        try {
            val newId = intent?.getIntExtra("ALARM_ID", 0) ?: 0
            val newType = intent?.getStringExtra("ALARM_TYPE") ?: "ALARM"

            if (newId <= 0) {
                logger.w(TAG, "Ignored start request with Invalid ID: $newId")
                return
            }
            logger.d(TAG, "Starting new ringing for ID: $newId, Type: $newType")

            if (currentRingingId == -1) {
                startRinging(newId, newType)
                return
            }

            if (currentRingingId == newId) {
                logger.d(TAG, "Already ringing ID: $newId, ignoring")
                return
            }

            val item = InterruptedItem(id = currentRingingId, type = currentType, timestamp = System.currentTimeMillis())
            AlarmRepository.addInterruptedItem(this, item)
            NotificationRenderer.showSilentRinging(this, currentRingingId, currentType)
            startRinging(newId, newType)
        } catch (e: Exception) {
            logger.e(TAG, "Error handling start new action", e)
            // Optionally notify the user or take other action
        }
    }

    /**
     * Starts ringing an alarm or timer.
     *
     * @param id The ID of the alarm or timer
     * @param type The type of alarm (e.g., "ALARM", "TIMER")
     */
    private fun startRinging(id: Int, type: String) {
        if (type == "ALARM") {
            val alarmCheck = AlarmRepository.getAlarm(id)
            if (alarmCheck == null) {
                logger.w(TAG, "Attempted to ring non-existent alarm ID: $id. Aborting.")
                stopSelf()
                return
            }
        }
        try {
            logger.d(TAG, "Starting ringing for ID: $id, Type: $type")

            if (id <= 0) {
                logger.e(TAG, "Aborting ring for invalid ID: $id")
                stopSelf()
                return
            }

            currentRingingId = id
            currentType = type
            AlarmRepository.setCurrentRingingId(id)

            var label = ""
            val settingsRepo = SettingsRepository.getInstance(applicationContext)

            if (type == "ALARM") {
                val alarm = AlarmRepository.getAlarm(id)
                if (alarm != null) {
                    label = alarm.label
                    val updated = alarm.copy(
                        snoozeUntil = null,
                        lastTriggerTime = System.currentTimeMillis()
                    )
                    AlarmRepository.updateAlarm(this, updated)
                }
            }

            StatusHub.trigger(StatusEvent.Ringing(currentRingingId, currentType))

            // --- TIMEOUT LOGIC ---
            val globalAutoStop = settingsRepo.defaultAutoStop.value
            val timerAutoStop = settingsRepo.defaultTimerAutoStop.value
            val alarmItem = if (type == "ALARM") AlarmRepository.getAlarm(id) else null

            val timeoutMinutes = when {
                type == "ALARM" && alarmItem != null && alarmItem.autoStopDuration != null -> {
                    logger.d(TAG, "Using CUSTOM alarm timeout: ${alarmItem.autoStopDuration} min")
                    alarmItem.autoStopDuration
                }
                type == "ALARM" -> {
                    logger.d(TAG, "Using GLOBAL alarm timeout: $globalAutoStop min")
                    globalAutoStop
                }
                else -> {
                    logger.d(TAG, "Using TIMER timeout: $timerAutoStop min")
                    timerAutoStop
                }
            }

            val safeTimeoutMs = (timeoutMinutes.toLong() * 60 * 1000).coerceAtLeast(10000L)
            logger.d(TAG, "Setting timeout for $safeTimeoutMs ms")

            timeoutJob?.cancel()
            timeoutJob = serviceScope.launch {
                delay(safeTimeoutMs)
                handleTimeout(id, type)
            }

            // --- WAKELOCK ---
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            val wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                "OpenAlarm:WakeLock"
            )
            wakeLock.acquire(safeTimeoutMs + 5000)

            // Build notification first
            val notification = NotificationRenderer.buildRingingNotification(this, id, type, label)

            try {
                if (Build.VERSION.SDK_INT >= 29) {
                    startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(id, notification)
                }
                if (id != LOADING_NOTIF_ID) {
                    val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                    nm.cancel(LOADING_NOTIF_ID)
                }
            } catch (e: Exception) {
                logger.e(TAG, "Failed to start foreground", e)
                if (Build.VERSION.SDK_INT >= 29) {
                    try { startForeground(id, notification) } catch (e2: Exception) {
                        logger.e(TAG, "Failed to start fallback foreground", e2)
                    }
                }
            }
            // Then when notification and foreground is there, start audio to avoid issues with no notifications but alarm ringing
            startAudioWithFeatures()

            val fullScreenIntent = Intent(this, RingActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("ALARM_TYPE", type)
                putExtra("ALARM_ID", id)
                putExtra("ALARM_LABEL", label)
                setData("custom://$type/$id".toUri())
            }
            try {
                startActivity(fullScreenIntent)
            } catch (e: Exception) {
                logger.e(TAG, "Failed to start activity", e)
            }
            NotificationRenderer.refreshAll(this)
        } catch (e: Exception) {
            logger.e(TAG, "Error in startRinging", e)
            // Optionally notify the user or take other action
        }
    }

    /**
     * Reschedules an alarm after it has been stopped or snoozed.
     *
     * @param alarmId The ID of the alarm to reschedule
     */
    private fun rescheduleAlarm(alarmId: Int) {
        try {
            logger.d(TAG, "Rescheduling alarm: ID=$alarmId")

            val alarm = AlarmRepository.getAlarm(alarmId) ?: run {
                logger.w(TAG, "Alarm not found for rescheduling: ID=$alarmId")
                return
            }

            if (alarm.isSelfDestroying) {
                logger.d(TAG, "Alarm is self-destroying, deleting: ID=$alarmId")
                AlarmRepository.deleteAlarm(this, alarm)
                return
            }

            if (alarm.isSingleUse || (alarm.daysOfWeek.isEmpty() && alarm.temporaryOverrideTime == null)) {
                AlarmRepository.updateAlarm(this, alarm.copy(isEnabled = false))
            } else {
                val scheduler = AlarmScheduler(this)
                val group = AlarmRepository.groups.find { it.alarms.any { a -> a.id == alarmId } }
                val offset = group?.offsetMinutes ?: 0
                scheduler.schedule(alarm, offset)
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error in rescheduleAlarm", e)
            // Optionally notify the user or take other action
        }
    }

    /**
     * Requests audio focus for playing the alarm sound.
     */
    private fun requestAudioFocus() {
        try {
            if (audioManager == null) {
                audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            }

            val res = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(audioFocusListener)
                    .build()
                audioManager?.requestAudioFocus(audioFocusRequest!!)
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(audioFocusListener, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            }

            if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                logger.d(TAG, "Audio focus granted")
            } else {
                logger.w(TAG, "Audio focus request failed")
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error requesting audio focus", e)
            // Continue without audio focus if possible
        }
    }

    /**
     * Abandons audio focus when the alarm stops.
     */
    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(audioFocusListener)
            }
            logger.d(TAG, "Audio focus abandoned")
        } catch (e: Exception) {
            logger.e(TAG, "Error abandoning audio focus", e)
        }
    }

    /**
     * Handles the timeout for alarms and timers.
     *
     * @param id The ID of the alarm or timer
     * @param type The type of alarm (e.g., "ALARM", "TIMER")
     */
    private fun handleTimeout(id: Int, type: String) {
        try {
            val now = System.currentTimeMillis()
            logger.d(TAG, "TIMEOUT triggered at $now for $id ($type)")

            if (id != currentRingingId) {
                logger.d(TAG, "Timeout ignored: triggered for $id but current is $currentRingingId")
                return
            }

            StatusHub.trigger(StatusEvent.Timeout(id, type))

            val alarm = if (type == "ALARM") AlarmRepository.getAlarm(id) else null

            if (alarm != null && alarm.isSnoozeEnabled && (alarm.maxSnoozes == null || alarm.currentSnoozeCount < alarm.maxSnoozes!!)) {
                logger.d(TAG, "Auto-snoozing alarm $id")
                handleSnooze(alarm, isAuto = true)
            } else {
                logger.d(TAG, "Stopping alarm $id after timeout (Snooze disabled or limit reached)")
                stopForeground(true)
                stopAll()

                if (type == "ALARM") {
                    // Mark as Missed
                    val group = AlarmRepository.groups.find { it.id == alarm?.groupId }
                    val offset = group?.offsetMinutes ?: 0
                    val baseTime = LocalTime.of(alarm?.hour ?: 0, alarm?.minute ?: 0)
                    val shiftedTime = baseTime.plusMinutes(offset.toLong())
                    val timeStr = shiftedTime.format(DateTimeFormatter.ofPattern("HH:mm"))

                    NotificationRenderer.showMissedNotification(this, id, alarm?.label ?: "", timeStr)
                    Toast.makeText(applicationContext, R.string.toast_alarm_auto_stopped, Toast.LENGTH_LONG).show()
                    rescheduleAlarm(id)
                }

                AlarmRepository.setCurrentRingingId(-1)
                checkStackAndResume()
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error in handleTimeout", e)
            // Optionally notify the user or take other action
        }
    }

    /**
     * Handles snoozing an alarm.
     *
     * @param alarm The alarm to snooze
     * @param isAuto Whether the snooze is automatic (timeout) or user-initiated
     * @param customMinutes Custom snooze duration in minutes, or null to use default
     */
    private fun handleSnooze(alarm: AlarmItem, isAuto: Boolean, customMinutes: Int? = null) {
        try {
            logger.d(TAG, "Handling snooze for alarm: ID=${alarm.id}, Auto=$isAuto, CustomMins=$customMinutes")

            val settingsRepo = SettingsRepository.getInstance(applicationContext)
            val globalSnooze = settingsRepo.defaultSnooze.value
            val snoozeMinutes = customMinutes ?: alarm.snoozeDuration ?: globalSnooze
            val snoozeTarget = System.currentTimeMillis() + (snoozeMinutes * 60 * 1000)

            val updated = alarm.copy(
                snoozeUntil = snoozeTarget,
                currentSnoozeCount = alarm.currentSnoozeCount + 1
            )
            AlarmRepository.updateAlarm(this, updated)

            val scheduler = AlarmScheduler(this)
            val group = AlarmRepository.groups.find { it.alarms.any { a -> a.id == alarm.id } }
            val offset = group?.offsetMinutes ?: 0
            scheduler.schedule(updated, offset)

            val msg = if (isAuto) "Auto-snoozed for $snoozeMinutes min" else "Snoozed for $snoozeMinutes min"
            Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()

            stopAll()
            stopForeground(true)
            AlarmRepository.setCurrentRingingId(-1)
            NotificationRenderer.refreshAll(this)
            checkStackAndResume()
        } catch (e: Exception) {
            logger.e(TAG, "Error in handleSnooze", e)
            // Optionally notify the user or take other action
        }
    }

    /**
     * Starts playing the alarm sound with configured features.
     * Handles volume fade-in, vibration, and text-to-speech.
     */
    private fun startAudioWithFeatures() {
        try {
            logger.d(TAG, "Starting audio...")
            stopAll()
            val settingsRepo = SettingsRepository.getInstance(applicationContext)
            val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            var uriToPlay = defaultUri
            var fadeSeconds = 0
            var useVibration = true

            if (currentType == "TIMER") {
                val tUriStr = settingsRepo.timerRingtone.value
                val tVol = settingsRepo.timerVolume.value
                val tTts = settingsRepo.timerTtsEnabled.value
                val tText = settingsRepo.timerTtsText.value
                val tVibration = settingsRepo.timerVibration.value

                uriToPlay = if (tUriStr != null) tUriStr.toUri() else defaultUri
                targetSliderValue = tVol
                useVibration = tVibration
                fadeSeconds = 0

                if (tTts) {
                    ttsMode = TtsMode.ONCE
                    customTtsText = if (tText.isNotBlank()) tText else getString(R.string.notif_timer_done)
                } else {
                    ttsMode = TtsMode.NONE
                    customTtsText = null
                }
            } else {
                val alarmItem = AlarmRepository.getAlarm(currentRingingId)
                uriToPlay = if (alarmItem?.ringtoneUri != null) android.net.Uri.parse(alarmItem.ringtoneUri) else defaultUri
                targetSliderValue = alarmItem?.customVolume ?: 1.0f
                fadeSeconds = alarmItem?.fadeInSeconds ?: 0
                useVibration = alarmItem?.vibrationEnabled ?: true
                ttsMode = alarmItem?.ttsMode ?: TtsMode.NONE
                customTtsText = null
            }

            logger.d(TAG, "Audio config: URI=$uriToPlay, Volume=$targetSliderValue, Fade=$fadeSeconds, Vibration=$useVibration, TTS=$ttsMode")

            // --- VOLUME FORCE ---
            // If app slider is ~100%, set System Stream to MAX.
            if (targetSliderValue >= 0.99f) {
                try {
                    val max = audioManager?.getStreamMaxVolume(AudioManager.STREAM_ALARM) ?: 0
                    if (max > 0) {
                        logger.d(TAG, "Setting system volume to max: $max")
                        audioManager?.setStreamVolume(AudioManager.STREAM_ALARM, max, 0)
                    }
                } catch (e: Exception) {
                    logger.e(TAG, "Error setting system volume", e)
                }
            }

            try {
                requestAudioFocus()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(applicationContext, uriToPlay)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    isLooping = true

                    // Set Volume
                    if (fadeSeconds > 0) {
                        setVolume(0.01f, 0.01f)
                    } else {
                        setVolume(targetSliderValue, targetSliderValue)
                    }

                    setOnPreparedListener { mp ->
                        mp.start()
                        logger.d(TAG, "Media player started via Async")
                    }
                    prepareAsync()
                }
                logger.d(TAG, "Media player started successfully")
            } catch(e: Exception) {
                logger.e(TAG, "Error starting media player", e)
                // Optionally notify the user or take other action
            }

            if (useVibration) {
                try {
                    val pattern = longArrayOf(0, 500, 500)
                    vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                    } else {
                        @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
                    } else {
                        @Suppress("DEPRECATION") vibrator?.vibrate(pattern, 0)
                    }
                    logger.d(TAG, "Vibration started successfully")
                } catch (e: Exception) {
                    logger.e(TAG, "Error starting vibration", e)
                    // Continue without vibration
                }
            }

            fadeJob = serviceScope.launch {
                try {
                    // Handle fade-in effect
                    if (fadeSeconds > 0) {
                        logger.d(TAG, "Starting fade-in effect for $fadeSeconds seconds")
                        val steps = fadeSeconds * 10
                        val sliderStep = targetSliderValue / steps.toFloat()
                        var currentSlider = 0.0f

                        for (i in 1..steps) {
                            if (mediaPlayer == null || !mediaPlayer!!.isPlaying) break
                            currentSlider += sliderStep
                            if (currentSlider > targetSliderValue) currentSlider = targetSliderValue

                            mediaPlayer?.setVolume(currentSlider, currentSlider)
                            delay(100)
                        }
                        mediaPlayer?.setVolume(targetSliderValue, targetSliderValue)
                    }

                    // Handle text-to-speech
                    if (ttsMode != TtsMode.NONE) {
                        var attempts = 0
                        while (!isTtsReady && attempts < 10) {
                            delay(500)
                            attempts++
                        }

                        if (isTtsReady) {
                            logger.d(TAG, "Starting TTS loop")
                            startTtsLoop()
                        } else {
                            logger.w(TAG, "TTS not ready after $attempts attempts")
                        }
                    }
                } catch (e: Exception) {
                    logger.e(TAG, "Error in fade job", e)
                }
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error in startAudioWithFeatures", e)
            // Optionally notify the user or take other action
        }
    }

    /**
     * Starts the text-to-speech loop based on the configured mode.
     */
    private suspend fun startTtsLoop() {
        try {
            if (ttsMode == TtsMode.ONCE) {
                speakInfo()
            } else if (ttsMode == TtsMode.EVERY_MINUTE) {
                while (true) {
                    speakInfo()
                    val now = System.currentTimeMillis()
                    val millisUntilNextMinute = 60_000 - (now % 60_000)
                    delay(millisUntilNextMinute + 500)
                }
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error in TTS loop", e)
        }
    }

    /**
     * Speaks the current time or custom text using text-to-speech.
     */
    private fun speakInfo() {
        try {
            if (tts == null || !isTtsReady) return

            val params = Bundle()
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, targetSliderValue)

            if (customTtsText != null) {
                tts?.speak(customTtsText, TextToSpeech.QUEUE_ADD, params, "timer_speak")
            } else {
                val is24Hour = android.text.format.DateFormat.is24HourFormat(applicationContext)
                val pattern = if (is24Hour) "H:mm" else "h:mm"
                val now = LocalTime.now()
                val formatter = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
                val timeStr = now.format(formatter)
                val text = getString(R.string.tts_time_announce, timeStr)

                tts?.speak(text, TextToSpeech.QUEUE_ADD, params, "time_speak")
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error in speakInfo", e)
        }
    }

    /**
     * Stops all audio playback, vibration, and cancels all coroutines.
     */
    private fun stopAll() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            logger.e(TAG, "Error stopping media player", e)
        }

        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            logger.e(TAG, "Error canceling vibration", e)
        }

        if (tts?.isSpeaking == true) {
            tts?.stop()
        }

        mediaPlayer = null
        fadeJob?.cancel()
        ttsJob?.cancel()
        timeoutJob?.cancel()
        abandonAudioFocus()
    }

    /**
     * Called when the service is destroyed.
     */
    override fun onDestroy() {
        super.onDestroy()
        try {
            stopAll()
            tts?.shutdown()
            serviceScope.cancel()
            AlarmRepository.setCurrentRingingId(-1)
            logger.d(TAG, "Service destroyed")
        } catch (e: Exception) {
            logger.e(TAG, "Error in onDestroy", e)
        }
    }

    /**
     * Checks if there are interrupted alarms to resume.
     */
    private fun checkStackAndResume() {
        try {
            val nextItem = AlarmRepository.popInterruptedItem(this)
            if (nextItem != null) {
                // Double check we aren't resuming the same ID we just stopped (safety check)
                if (nextItem.id == currentRingingId) {
                    logger.w(TAG, "Aborting resume of same ID that was just stopped: ${nextItem.id}")
                    checkStackAndResume() // skip this one, try next
                    return
                }

                if (nextItem.type == "TIMER" && AlarmRepository.getTimer(nextItem.id) == null) {
                    checkStackAndResume()
                    return
                }
                logger.d(TAG, "Resuming interrupted item: ID=${nextItem.id}, Type=${nextItem.type}")
                startRinging(nextItem.id, nextItem.type)
            } else {
                stopSelf()
            }
        } catch (e: Exception) {
            logger.e(TAG, "Error in checkStackAndResume", e)
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}