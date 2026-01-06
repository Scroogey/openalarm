package de.laurik.openalarm

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.*
import android.os.*
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.core.net.toUri
import de.laurik.openalarm.utils.AppLogger
import kotlinx.coroutines.*
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

class RingtoneService : Service(), TextToSpeech.OnInitListener {

    private val logger by lazy { AppLogger(applicationContext) }

    companion object {
        private const val TAG = "RingtoneService"
        private const val RINGING_NOTIF_ID = 69999
    }

    // Media & Hardware
    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null

    // Audio Focus
    private val focusListener = AudioManager.OnAudioFocusChangeListener { _ -> }

    // TTS
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var ttsJob: Job? = null

    // State
    private var currentRingingId: Int = -1
    private var currentType: String = "NONE"

    // Coroutines & Jobs
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var fadeJob: Job? = null

    // MAP of Timeouts
    private val timeoutJobs = mutableMapOf<Int, Job>()

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        tts = TextToSpeech(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val action = intent.action
        val id = intent.getIntExtra("ALARM_ID", -1)

        // Immediate Foreground
        if (id != -1 && (action == null || action == "START_ALARM")) {
            if (currentRingingId == -1) {
                val type = intent.getStringExtra("ALARM_TYPE") ?: "ALARM"
                val label = intent.getStringExtra("ALARM_LABEL") ?: ""
                val triggerTime = intent.getLongExtra("TRIGGER_TIME", System.currentTimeMillis())

                val notification = NotificationRenderer.buildRingingNotification(this, id, type, label, triggerTime)
                if (Build.VERSION.SDK_INT >= 29) {
                    startForeground(RINGING_NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else {
                    startForeground(RINGING_NOTIF_ID, notification)
                }
            }
        }

        serviceScope.launch {
            AlarmRepository.ensureLoaded(applicationContext)
            handleIntent(intent)
        }

        return START_NOT_STICKY
    }

    private fun handleIntent(intent: Intent) {
        val action = intent.action
        when (action) {
            "STOP_RINGING", "STOP" -> handleStopAction(intent)
            "SNOOZE_1", "SNOOZE_CUSTOM" -> handleSnoozeAction(intent)
            "ADD_TIME" -> handleAddTimeAction(intent)
            else -> handleStartRequest(intent)
        }
    }

    // --- START REQUEST ---

    private fun handleStartRequest(intent: Intent) {
        val newId = intent.getIntExtra("ALARM_ID", -1)
        val newType = intent.getStringExtra("ALARM_TYPE") ?: "ALARM"
        val label = intent.getStringExtra("ALARM_LABEL") ?: ""
        val triggerTime = intent.getLongExtra("TRIGGER_TIME", System.currentTimeMillis())

        if (newId == -1) return

        // Deduplication
        if (currentRingingId == newId) return
        if (InternalDataStore.interruptedItems.any { it.id == newId }) return

        // 1. SCHEDULE TIMEOUT
        scheduleTimeout(newId, newType)

        // 2. DECIDE STATE
        if (currentRingingId != -1) {
            logger.d(TAG, "Already ringing $currentRingingId. Queueing $newId")
            val item = InterruptedItem(id = newId, type = newType, label = label, timestamp = triggerTime)
            AlarmRepository.addInterruptedItem(this, item)
            NotificationRenderer.showSilentRinging(this, newId, newType, label)
        } else {
            startRingingSession(newId, newType, label, triggerTime)
        }
    }

    private fun startRingingSession(id: Int, type: String, label: String, triggerTime: Long) {
        currentRingingId = id
        currentType = type
        AlarmRepository.setCurrentRingingId(id)

        // Notification & UI
        val notification = NotificationRenderer.buildRingingNotification(this, id, type, label, triggerTime)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(RINGING_NOTIF_ID, notification)

        // Force stop Timer Service to remove duplicate "Running" notification
        stopService(Intent(this, TimerRunningService::class.java))

        NotificationRenderer.refreshAll(this)

        // Audio
        startAudio(id, type)

        // Activity
        val fullScreenIntent = Intent(this, RingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("ALARM_TYPE", type)
            putExtra("ALARM_ID", id)
            putExtra("ALARM_LABEL", label)
            putExtra("START_TIME", triggerTime)
            data = "custom://$type/$id".toUri()
        }
        startActivity(fullScreenIntent)

        StatusHub.trigger(StatusEvent.Ringing(id, type))
    }

    // --- TIMEOUT LOGIC ---

    private fun scheduleTimeout(id: Int, type: String) {
        timeoutJobs[id]?.cancel()

        val durationMin = if (type == "ALARM") {
            AlarmRepository.getAlarm(id)?.autoStopDuration ?: SettingsRepository.getInstance(this).defaultAutoStop.value
        } else {
            SettingsRepository.getInstance(this).defaultTimerAutoStop.value
        }
        val ms = durationMin * 60 * 1000L

        val job = serviceScope.launch {
            delay(ms)
            handleTimeoutTriggered(id, type)
        }
        timeoutJobs[id] = job
        logger.d(TAG, "Scheduled timeout for $id ($type) in ${durationMin}m")
    }

    private fun handleTimeoutTriggered(id: Int, type: String) {
        logger.d(TAG, "TIMEOUT triggered for $id")
        timeoutJobs.remove(id)
        // A: Active Alarm Timeout
        if (id == currentRingingId) {
            val alarm = AlarmRepository.getAlarm(id)
            if (type == "ALARM" && alarm != null && alarm.isSnoozeEnabled) {
                handleAutoSnooze(alarm)
            } else {
                stopCurrentRinging(isTimeout = true)
            }
            return
        }

        StatusHub.trigger(StatusEvent.Timeout(id, type))

        // B: Background Timeout
        val queuedItem = InternalDataStore.interruptedItems.find { it.id == id }
        if (queuedItem != null) {
            InternalDataStore.interruptedItems.remove(queuedItem)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(id)

            val alarm = AlarmRepository.getAlarm(id)
            if (type == "ALARM" && alarm != null && alarm.isSnoozeEnabled) {
                // Auto-Snooze
                val snoozeMins = alarm.snoozeDuration ?: SettingsRepository.getInstance(this).defaultSnooze.value
                val snoozeTime = System.currentTimeMillis() + (snoozeMins * 60 * 1000)
                val updated = alarm.copy(snoozeUntil = snoozeTime, currentSnoozeCount = alarm.currentSnoozeCount + 1)
                AlarmRepository.updateAlarm(this, updated)
                val group = AlarmRepository.groups.find { it.id == alarm.groupId }
                AlarmScheduler(this).schedule(updated, group?.offsetMinutes ?: 0)
                NotificationRenderer.showMissedNotification(this, id, alarm.label, "Auto-Snoozed ($snoozeMins m)")
            } else {
                // Missed
                NotificationRenderer.showMissedNotification(this, id, queuedItem.label, "Timeout")
                if (type == "ALARM" && alarm != null) {
                    val now = System.currentTimeMillis()
                    val group = AlarmRepository.groups.find { it.id == alarm.groupId }
                    val nextOcc = AlarmUtils.getNextOccurrence(alarm.hour, alarm.minute, alarm.daysOfWeek, group?.offsetMinutes ?: 0, null, null, now + 60_000)
                    val updated = alarm.copy(skippedUntil = nextOcc + 1000, currentSnoozeCount = 0)
                    AlarmRepository.updateAlarm(this, updated)
                    AlarmScheduler(this).schedule(updated, group?.offsetMinutes ?: 0)
                }
            }
        }
    }

    // --- STOPPING LOGIC ---

    private fun handleStopAction(intent: Intent) {
        val targetId = intent.getIntExtra("TARGET_ID", -1)

        // Stop specific background ID
        if (targetId != -1 && targetId != currentRingingId) {
            InternalDataStore.interruptedItems.removeAll { it.id == targetId }
            timeoutJobs[targetId]?.cancel()
            timeoutJobs.remove(targetId)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(targetId)
            return
        }

        // Stop current
        stopCurrentRinging(isTimeout = false)
    }

    private fun stopCurrentRinging(isTimeout: Boolean) {
        if (currentRingingId == -1) return

        val id = currentRingingId
        val type = currentType

        timeoutJobs[id]?.cancel()
        timeoutJobs.remove(id)

        if (type == "ALARM") {
            val alarm = AlarmRepository.getAlarm(id)
            if (alarm != null) {
                val now = System.currentTimeMillis()
                val group = AlarmRepository.groups.find { it.id == alarm.groupId }
                val offset = group?.offsetMinutes ?: 0
                val nextOcc = AlarmUtils.getNextOccurrence(alarm.hour, alarm.minute, alarm.daysOfWeek, offset, null, null, now - 60_000)
                val skippedTime = if (nextOcc <= now + 6 * 60 * 60 * 1000) {
                    now + 6 * 60 * 60 * 1000
                } else {
                    nextOcc
                }
                val finalSkippedTime = max(skippedTime, alarm.skippedUntil)

                val updated = alarm.copy(
                    snoozeUntil = null,
                    currentSnoozeCount = 0,
                    skippedUntil = finalSkippedTime,
                    isEnabled = if (alarm.isSingleUse) false else alarm.isEnabled
                )
                AlarmRepository.updateAlarm(this, updated)

                if (updated.isEnabled) {
                    AlarmScheduler(this).schedule(updated, offset)
                }

                if (isTimeout) {
                    NotificationRenderer.showMissedNotification(this, id, alarm.label, "Timeout")
                }
            }
        } else if (type == "TIMER") {
            AlarmRepository.removeTimer(this, id)
        }

        StatusHub.trigger(StatusEvent.Stopped(id, type))
        stopMedia()

        // Restart TimerRunningService if active timers remain
        if (AlarmRepository.activeTimers.isNotEmpty()) {
            val tIntent = Intent(this, TimerRunningService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(tIntent) else startService(tIntent)
        }

        checkQueueAndResume()
    }

    // --- SNOOZE LOGIC ---

    private fun handleSnoozeAction(intent: Intent) {
        if (currentRingingId == -1) return
        val id = currentRingingId
        val alarm = AlarmRepository.getAlarm(id) ?: return

        timeoutJobs[id]?.cancel()
        timeoutJobs.remove(id)

        val customMins = if (intent.action == "SNOOZE_CUSTOM") intent.getIntExtra("MINUTES", 10) else null
        val snoozeMins = customMins ?: alarm.snoozeDuration ?: SettingsRepository.getInstance(this).defaultSnooze.value
        val snoozeTime = System.currentTimeMillis() + (snoozeMins * 60 * 1000)

        val updated = alarm.copy(snoozeUntil = snoozeTime, currentSnoozeCount = alarm.currentSnoozeCount + 1)
        AlarmRepository.updateAlarm(this, updated)

        val group = AlarmRepository.groups.find { it.id == alarm.groupId }
        AlarmScheduler(this).schedule(updated, group?.offsetMinutes ?: 0)

        Toast.makeText(this, "Snoozed for $snoozeMins min", Toast.LENGTH_SHORT).show()
        StatusHub.trigger(StatusEvent.Snoozed(id, "ALARM", snoozeTime))

        stopMedia()
        checkQueueAndResume()
    }

    private fun handleAutoSnooze(alarm: AlarmItem) {

        val snoozeMins = alarm.snoozeDuration ?: SettingsRepository.getInstance(this).defaultSnooze.value
        val snoozeTime = System.currentTimeMillis() + (snoozeMins * 60 * 1000)

        val updated = alarm.copy(snoozeUntil = snoozeTime, currentSnoozeCount = alarm.currentSnoozeCount + 1)
        AlarmRepository.updateAlarm(this, updated)
        val group = AlarmRepository.groups.find { it.id == alarm.groupId }
        AlarmScheduler(this).schedule(updated, group?.offsetMinutes ?: 0)

        NotificationRenderer.showMissedNotification(this, alarm.id, alarm.label, "Auto-Snoozed ($snoozeMins m)")
        StatusHub.trigger(StatusEvent.Snoozed(alarm.id, "ALARM", snoozeTime))

        stopMedia()
        checkQueueAndResume()
    }

    private fun handleAddTimeAction(intent: Intent) {
        val targetId = intent.getIntExtra("TARGET_ID", currentRingingId)
        val seconds = intent.getIntExtra("SECONDS", 60)
        val timer = AlarmRepository.getTimer(targetId) ?: return

        val newEnd = System.currentTimeMillis() + (timer.endTime - System.currentTimeMillis()).coerceAtLeast(0) + (seconds * 1000)
        val updated = timer.copy(endTime = newEnd, totalDuration = timer.totalDuration + (seconds * 1000))
        AlarmRepository.updateTimer(this, updated)

        if (targetId == currentRingingId) {
            stopMedia()
            timeoutJobs[targetId]?.cancel()
            timeoutJobs.remove(targetId)

            AlarmRepository.setCurrentRingingId(-1)
            StatusHub.trigger(StatusEvent.Extended(targetId, "TIMER", newEnd))
            checkQueueAndResume()
        }
        NotificationRenderer.refreshAll(this)
    }

    private fun checkQueueAndResume() {
        val nextItem = AlarmRepository.popInterruptedItem(this)

        if (nextItem != null) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(nextItem.id)
            startRingingSession(nextItem.id, nextItem.type, nextItem.label, nextItem.timestamp)
        } else {
            currentRingingId = -1
            stopForeground(true)
            stopSelf()
            NotificationRenderer.refreshAll(this)
        }
    }

    // --- AUDIO & TTS ---

    private fun startAudio(id: Int, type: String) {
        stopMedia() // Cleanup previous media

        var uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        var volume = 1.0f
        var vibrate = true
        var ttsMode = TtsMode.NONE
        var ttsText = ""

        if (type == "ALARM") {
            AlarmRepository.getAlarm(id)?.let {
                if (it.ringtoneUri != null) uri = android.net.Uri.parse(it.ringtoneUri)
                volume = it.customVolume ?: 1.0f
                vibrate = it.vibrationEnabled
                ttsMode = it.ttsMode
            }
        } else {
            val s = SettingsRepository.getInstance(this)
            s.timerRingtone.value?.let { uri = it.toUri() }
            volume = s.timerVolume.value
            vibrate = s.timerVibration.value
            if (s.timerTtsEnabled.value) {
                ttsMode = TtsMode.ONCE
                ttsText = s.timerTtsText.value.ifBlank { "Timer Done" }
            }
        }

        // Focus
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attr = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attr)
                .setOnAudioFocusChangeListener(focusListener)
                .build()
            audioManager?.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(focusListener, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        }

        // MediaPlayer
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, uri)
                setAudioStreamType(AudioManager.STREAM_ALARM)
                isLooping = true
                setVolume(volume, volume)
                prepare()
                start()
            }
        } catch (e: Exception) { logger.e(TAG, "MediaPlayer failed", e) }

        // Vibration
        if (vibrate) {
            vibrator = if (Build.VERSION.SDK_INT >= 31) {
                (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION") getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            val pattern = longArrayOf(0, 500, 500)
            if (Build.VERSION.SDK_INT >= 26) vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            else @Suppress("DEPRECATION") vibrator?.vibrate(pattern, 0)
        }

        if (ttsMode != TtsMode.NONE) {
            ttsJob = serviceScope.launch {
                startTtsLoop(ttsMode, ttsText, volume)
            }
        }
    }

    private suspend fun startTtsLoop(mode: TtsMode, customText: String, volume: Float) {
        var attempts = 0
        while (!isTtsReady && attempts < 20) { delay(200); attempts++ }
        if (!isTtsReady || tts == null) return

        val params = Bundle()
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volume)

        try {
            if (mode == TtsMode.ONCE) {
                delay(1000)
                speak(customText, params)
            } else if (mode == TtsMode.EVERY_MINUTE) {
                while (true) {
                    currentCoroutineContext().ensureActive() // Correct cancellation check

                    val now = LocalTime.now()
                    val text = getString(R.string.tts_time_announce, now.format(DateTimeFormatter.ofPattern("H:mm")))
                    speak(text, params)

                    val delayMs = 60_000 - (System.currentTimeMillis() % 60_000)
                    delay(delayMs + 1000)
                }
            }
        } catch (e: CancellationException) {
            // Expected on stop
        }
    }

    private fun speak(text: String, params: Bundle) {
        tts?.speak(text, TextToSpeech.QUEUE_ADD, params, "ID_${System.currentTimeMillis()}")
    }

    private fun stopMedia() {
        try { mediaPlayer?.stop(); mediaPlayer?.release() } catch (e: Exception) {}
        mediaPlayer = null
        try { vibrator?.cancel() } catch (e: Exception) {}
        try { tts?.stop() } catch(e: Exception) {}

        fadeJob?.cancel()
        ttsJob?.cancel()
    }

    override fun onDestroy() {
        stopMedia()
        tts?.shutdown()
        serviceScope.cancel()
        AlarmRepository.setCurrentRingingId(-1)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.getDefault())
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                logger.e(TAG, "TTS Language not supported")
                isTtsReady = false
            } else {
                val attrs = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts?.setAudioAttributes(attrs)
                isTtsReady = true
            }
        } else {
            logger.e(TAG, "TTS Init failed")
            isTtsReady = false
        }
    }
}