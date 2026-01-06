package de.laurik.openalarm

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

class RingActivity : ComponentActivity() {

    private var currentType by mutableStateOf("ALARM")
    private var currentId by mutableStateOf(-1)
    private var currentStartTime by mutableLongStateOf(System.currentTimeMillis())
    private var currentLabel by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        turnScreenOnAndKeyguardOff()
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        window.decorView.setBackgroundColor(android.graphics.Color.BLACK)

        lifecycleScope.launch {
            AlarmRepository.ensureLoaded(this@RingActivity)
        }

        handleIntent(intent)

        setContent {
            val event = StatusHub.lastEvent

            LaunchedEffect(event) {
                when (event) {
                    is StatusEvent.Stopped -> if (event.id == currentId || event.id == -1) finish()
                    is StatusEvent.Extended -> if (event.id == currentId && currentType != "TIMER") finish()
                    is StatusEvent.Timeout -> if (event.id == currentId) finish()
                    is StatusEvent.Snoozed -> if (event.id == currentId) finish()
                    else -> {}
                }
            }

            MaterialTheme {
                val alarm = remember(currentId) { AlarmRepository.getAlarm(currentId) }
                
                if (currentType == "TIMER") {
                    TimerRingingScreen(
                        startTime = currentStartTime,
                        timerId = currentId,
                        onStop = { stopTimerCommand(currentId) },
                        onAdd = { addTimeCommand(it) }
                    )
                } else {
                    AlarmRingingScreen(
                        alarm = alarm,
                        onStop = { stopServiceCommand(currentId) },
                        onSnooze = { mins -> 
                            if (mins == null) snoozeAlarm() 
                            else snoozeAlarmCustom(mins)
                        }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Called when activity becomes visible
        turnScreenOnAndKeyguardOff()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        turnScreenOnAndKeyguardOff()
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val newType = intent.getStringExtra("ALARM_TYPE") ?: "ALARM"
        val newId = intent.getIntExtra("ALARM_ID", -1)
        val newLabel = intent.getStringExtra("ALARM_LABEL") ?: ""

        currentType = newType
        currentId = newId
        currentLabel = newLabel

        if (intent.hasExtra("START_TIME")) {
            currentStartTime = intent.getLongExtra("START_TIME", System.currentTimeMillis())
        }
    }

    private fun stopServiceCommand(targetId: Int) {
        val intent = Intent(this, RingtoneService::class.java).apply {
            action = "STOP_RINGING"; putExtra("TARGET_ID", targetId)
        }
        startService(intent)
    }

    private fun stopTimerCommand(timerId: Int) {
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            action = "STOP_SPECIFIC_TIMER"; putExtra("TIMER_ID", timerId)
        }
        sendBroadcast(intent)
        finish()
    }

    private fun snoozeAlarm() {
        val intent = Intent(this, RingtoneService::class.java).apply {
            action = "SNOOZE_1"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        finish()
    }

    private fun snoozeAlarmCustom(minutes: Int) {
        val intent = Intent(this, RingtoneService::class.java).apply {
            action = "SNOOZE_CUSTOM"; putExtra("MINUTES", minutes)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        finish()
    }

    private fun addTimeCommand(seconds: Int) {
        val intent = Intent(this, RingtoneService::class.java).apply {
            action = "ADD_TIME"; putExtra("SECONDS", seconds)
            putExtra("TARGET_ID", currentId)
        }
        startService(intent)
    }

    private fun turnScreenOnAndKeyguardOff() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        // 3. Keyguard Dismissal
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        }
    }
}

@Composable
fun TimerRingingScreen(startTime: Long, timerId: Int, onStop: () -> Unit, onAdd: (Int) -> Unit) {
    val timerItem = AlarmRepository.activeTimers.find { it.id == timerId }
    val trueEndTime = timerItem?.endTime ?: startTime
    val context = LocalContext.current
    val durationText = remember(timerItem?.totalDuration, timerItem?.durationSeconds) {
        val totalMs = timerItem?.totalDuration ?: (timerItem?.durationSeconds?.toLong()?.times(1000) ?: 0L)
        AlarmUtils.formatDuration(context, (totalMs / 1000).toInt())
    }
    var countUpStr by remember { mutableStateOf("+ 00:00") }
    var isRunning by remember(trueEndTime) { mutableStateOf(trueEndTime > System.currentTimeMillis()) }
    val bgColor = if (isRunning) Color(0xFFE65100) else Color(0xFFB71C1C)
    val titleText = if (isRunning) stringResource(R.string.notif_timer_running) else stringResource(R.string.title_timer_done)

    LaunchedEffect(trueEndTime) {
        while (true) {
            val now = System.currentTimeMillis()
            isRunning = trueEndTime > now
            val diff = now - trueEndTime
            if (isRunning) {
                // Running state: show remaining
                val remaining = (trueEndTime - now).coerceAtLeast(0)
                val totalSec = (remaining / 1000).toInt()
                val m = totalSec / 60
                val s = totalSec % 60
                countUpStr = String.format(Locale.getDefault(), "%02d:%02d", m, s)
            } else {
                // Done state: count up from end time
                val d = if (diff < 0) 0 else diff
                val s = (d / 1000) % 60
                val m = (d / (1000 * 60))
                countUpStr = String.format(Locale.getDefault(), "+ %02d:%02d", m, s)
            }
            delay(500)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(titleText, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Text("($durationText)", color = Color.White.copy(alpha = 0.7f), fontSize = 20.sp)
        Spacer(Modifier.height(32.dp))
        Text(countUpStr, color = Color.White, fontSize = 90.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(64.dp))

        Button(
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) {
            Text(stringResource(R.string.action_stop_caps), fontSize = 24.sp, color = Color.Red)
        }
        val settingsRepo = remember { SettingsRepository.getInstance(context) }
        val adjustPresets by settingsRepo.timerAdjustPresets.collectAsState(initial = listOf(60, 300))

        Text(stringResource(R.string.label_add_time), color = Color.White)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            adjustPresets.forEach { seconds ->
                OutlinedButton(
                    onClick = { onAdd(seconds) },
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White),
                    modifier = Modifier.padding(horizontal = 4.dp)
                ) {
                    Text("+${seconds / 60}m", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun AlarmRingingScreen(
    alarm: AlarmItem?,
    onStop: () -> Unit,
    onSnooze: (Int?) -> Unit // null = default
) {
    var currentTimeStr by remember { mutableStateOf("") }
    var showSnoozePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository.getInstance(context) }


    val globalMode by settingsRepo.defaultRingingMode.collectAsState(initial = RingingScreenMode.CLEAN)
    val globalPresets by settingsRepo.defaultSnoozePresets.collectAsState(initial = listOf(5, 10, 15))
    
    val presets = alarm?.snoozePresets ?: globalPresets
    
    val alarmMode = alarm?.ringingScreenMode ?: RingingScreenMode.DEFAULT
    val mode = if (alarmMode == RingingScreenMode.DEFAULT) globalMode else alarmMode
    
    val bgType = alarm?.backgroundType ?: "COLOR"
    val bgValue = alarm?.backgroundValue ?: "#000000"

    LaunchedEffect(Unit) {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        while (true) {
            currentTimeStr = LocalTime.now().format(formatter)
            delay(1000)
        }
    }

    RingingBackground(type = bgType, value = bgValue) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(64.dp))
            Text(currentTimeStr, color = Color.White, fontSize = 100.sp, fontWeight = FontWeight.Thin)

            val displayLabel = alarm?.label ?: ""
            if (displayLabel.isNotEmpty()) {
                Text(
                    text = displayLabel,
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }

            if (alarm != null && alarm.currentSnoozeCount > 0) {
                val maxStr = alarm.maxSnoozes?.let { "/ $it" } ?: ""
                Text(
                    text = "Snooze ${alarm.currentSnoozeCount} $maxStr",
                    color = Color.LightGray,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            Spacer(Modifier.weight(1f))
            
            when (mode) {
                RingingScreenMode.EASY -> {
                    EasyModeLayout(
                        onStop = onStop,
                        onSnooze = {
                            if (alarm?.directSnooze == true) onSnooze(null)
                            else showSnoozePicker = true
                        }
                    )
                }
                RingingScreenMode.CLEAN -> {
                    CleanModeJoystick(
                        onStopTrigger = onStop,
                        onSnoozeTrigger = {
                            if (alarm?.directSnooze == true) onSnooze(null)
                            else showSnoozePicker = true
                        }
                    )
                }
                else -> {
                     SwipeSlider(onLeft = { showSnoozePicker = true }, onRight = onStop)
                }
            }
            
            Spacer(Modifier.height(48.dp))
        }

        if (showSnoozePicker) {
            SnoozePickerOverlay(
                presets = presets,
                onDismiss = { showSnoozePicker = false },
                onSelect = { mins -> onSnooze(mins); showSnoozePicker = false }
            )
        }
    }
}

@Composable
fun SnoozePickerOverlay(presets: List<Int>, onDismiss: () -> Unit, onSelect: (Int?) -> Unit) {

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)).clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.8f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Select Snooze", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(24.dp))
                
                presets.forEach { mins ->
                    Button(
                        onClick = { onSelect(mins) },
                        modifier = Modifier.fillMaxWidth().height(56.dp).padding(vertical = 4.dp)
                    ) {
                        Text("$mins minutes")
                    }
                }
                
                OutlinedButton(
                    onClick = { onSelect(null) },
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(vertical = 4.dp)
                ) {
                    Text("Deafult Snooze")
                }
            }
        }
    }
}

@Composable
fun SwipeSlider(onLeft: () -> Unit, onRight: () -> Unit) {
    val width = 300.dp
    val height = 70.dp
    val thumbSize = 60.dp
    val density = LocalDensity.current
    val maxOffset = with(density) { (width - thumbSize).toPx() / 2 }

    var offsetX by remember { mutableFloatStateOf(0f) }
    var isTriggered by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(35.dp))
            .background(Color(0xFF333333)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(Icons.Default.Notifications, stringResource(R.string.desc_snooze), tint = Color.LightGray)
            Icon(Icons.Default.Close, stringResource(R.string.desc_stop), tint = Color.LightGray)
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .size(thumbSize)
                .clip(CircleShape)
                .background(if (isTriggered) Color.Gray else Color.White)
                .draggable(
                    orientation = Orientation.Horizontal,
                    enabled = !isTriggered,
                    state = rememberDraggableState { delta ->
                        if (!isTriggered) {
                            offsetX = (offsetX + delta).coerceIn(-maxOffset, maxOffset)
                        }
                    },
                    onDragStopped = {
                        val threshold = maxOffset * 0.5f
                        if (offsetX < -threshold) {
                            isTriggered = true
                            offsetX = -maxOffset
                            onLeft()
                        } else if (offsetX > threshold) {
                            isTriggered = true
                            offsetX = maxOffset
                            onRight()
                        } else {
                            offsetX = 0f
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!isTriggered) {
                Text("<<<   >>>", fontSize = 10.sp, color = Color.Black)
            }
        }
    }
}