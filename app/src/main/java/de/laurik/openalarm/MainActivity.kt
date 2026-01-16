package de.laurik.openalarm

import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import android.app.AlarmManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.collections.associate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        createNotificationChannels()
        checkPermissions()

        setContent {
            val settingsViewModel = viewModel<SettingsViewModel>()
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val isPureBlack by settingsViewModel.isPureBlack.collectAsState()
            var showSettings by remember { mutableStateOf(false) }

            BackHandler(enabled = showSettings) {
                showSettings = false
            }

            val systemDark = isSystemInDarkTheme()
            val useDarkIcons = when (themeMode) {
                AppThemeMode.LIGHT -> true
                AppThemeMode.DARK -> false
                AppThemeMode.SYSTEM -> !systemDark
            }

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as android.app.Activity).window
                    window.statusBarColor = Color.Transparent.toArgb()
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = useDarkIcons
                }
            }

            de.laurik.openalarm.ui.theme.OpenAlarmTheme(themeMode = themeMode, isPureBlack = isPureBlack) {
                if (showSettings) {
                    SettingsScreen(settingsViewModel, onClose = { showSettings = false })
                } else {
                    Dashboard(onSettingsClick = { showSettings = true })
                    CheckSystemPermissions()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            AlarmRepository.ensureLoaded(this@MainActivity)
            AlarmRepository.cleanupStaleInterruptedItems(this@MainActivity)
            NotificationRenderer.refreshAll(this@MainActivity)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // 1. RINGING (Max Priority)
            val ringChannel = NotificationChannel(
                "ALARM_CHANNEL_ID",
                getString(R.string.channel_alarm_ringing),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                description = getString(R.string.channel_alarm_desc)
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }

            // 2. STATUS (Snooze / Next Alarm)
            // CHANGE: IMPORTANCE_LOW -> IMPORTANCE_DEFAULT
            val statusChannel = NotificationChannel(
                "STATUS_CHANNEL_ID",
                getString(R.string.channel_status),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                setShowBadge(false)
                description = getString(R.string.channel_status_desc)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setBypassDnd(true) // Ensure Snooze shows through DND
                setSound(null, null) // Silent but visible
            }

            // 3. ACTIVE TIMER
            val activeTimerChannel = NotificationChannel(
                "ACTIVE_TIMER_CHANNEL_ID",
                getString(R.string.channel_active_timer),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                setSound(null, null)
                setShowBadge(false)
                description = getString(R.string.channel_active_timer_desc)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }

            manager.createNotificationChannel(activeTimerChannel)
            manager.createNotificationChannel(ringChannel)
            manager.createNotificationChannel(statusChannel)
        }
    }

    private fun checkPermissions() {
        // Notification Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                val launcher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) {}
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
fun CheckSystemPermissions() {
    val context = LocalContext.current
    val packageName = context.packageName

    var showExactAlarmDialog by remember { mutableStateOf(false) }

    // Full Screen Intent Check (Android 14+)
    var showFullScreenDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                showExactAlarmDialog = true
            }
        }
    }

    if (showExactAlarmDialog) {
        AlertDialog(
            onDismissRequest = { showExactAlarmDialog = false },
            title = { Text(stringResource(R.string.dialog_title_permission_required)) },
            text = { Text(stringResource(R.string.dialog_msg_permission_exact_alarm)) },
            confirmButton = {
                TextButton(onClick = {
                    showExactAlarmDialog = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        context.startActivity(intent)
                    }
                }) { Text(stringResource(R.string.action_grant)) }
            },
            dismissButton = {
                TextButton(onClick = { showExactAlarmDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (showFullScreenDialog) {
        AlertDialog(
            onDismissRequest = { showFullScreenDialog = false },
            title = { Text(stringResource(R.string.dialog_title_permission_required)) },
            text = { Text(stringResource(R.string.dialog_msg_permission_full_screen)) },
            confirmButton = {
                TextButton(onClick = {
                    showFullScreenDialog = false
                    if (Build.VERSION.SDK_INT >= 34) {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                            data = Uri.parse("package:$packageName")
                        }
                        context.startActivity(intent)
                    }
                }) { Text(stringResource(R.string.action_grant)) }
            },
            dismissButton = {
                TextButton(onClick = { showFullScreenDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Dashboard(viewModel: DashboardViewModel = viewModel(), settingsViewModel: SettingsViewModel = viewModel(), onSettingsClick: () -> Unit) {
    val context = LocalContext.current
    
    // Collect preset settings
    val quickAdjustPresets by settingsViewModel.quickAdjustPresets.collectAsState()
    val timerPresets by settingsViewModel.timerPresets.collectAsState()

    // --- STATE ---
    var showTimerDialog by remember { mutableStateOf(false) }
    var editingAlarm by remember { mutableStateOf<AlarmItem?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }
    var showDatePickerForAlarm by remember { mutableStateOf<AlarmItem?>(null) }
    var isFabExpanded by remember { mutableStateOf(false) }

    // Group Dialogs
    var groupToEdit by remember { mutableStateOf<AlarmGroup?>(null) }
    var groupToAdjust by remember { mutableStateOf<AlarmGroup?>(null) }
    var groupToDelete by remember { mutableStateOf<AlarmGroup?>(null) }

    // --- DATA FILTERING ---
    // 1. Get the default group (fallback safe)
    val defaultGroup = viewModel.groups.find { it.id == "default" }
        ?: viewModel.groups.find { it.name == "Default" }

    // 2. Split alarms: Default vs Custom Groups
    // Smart sorting: Disabled alarms last, enabled alarms by actual next occurrence time
    val flatAlarms = defaultGroup?.alarms?.sortedWith(
        compareBy<AlarmItem> { !it.isEnabled }
            .thenBy { alarm ->
                if (alarm.isEnabled) {
                    val minTime = if (alarm.skippedUntil > System.currentTimeMillis()) 
                        alarm.skippedUntil 
                    else 
                        System.currentTimeMillis()
                    AlarmUtils.getNextOccurrence(
                        alarm.hour, alarm.minute, alarm.daysOfWeek, 
                        0, // group offset (0 for default group)
                        alarm.temporaryOverrideTime, 
                        alarm.snoozeUntil,
                        minTime
                    )
                } else {
                    Long.MAX_VALUE // Disabled alarms go to end
                }
            }
    ) ?: emptyList()
    
    // Sort custom groups by their earliest alarm's next occurrence
    val customGroups = viewModel.groups
        .filter { it.id != "default" && it.id != defaultGroup?.id }
        .sortedBy { group ->
            // Find the earliest alarm in this group
            group.alarms
                .filter { it.isEnabled }
                .minOfOrNull { alarm ->
                    val minTime = maxOf(
                        System.currentTimeMillis(), 
                        alarm.skippedUntil,
                        group.skippedUntil
                    )
                    AlarmUtils.getNextOccurrence(
                        alarm.hour, alarm.minute, alarm.daysOfWeek, 
                        group.offsetMinutes,
                        alarm.temporaryOverrideTime, 
                        alarm.snoozeUntil,
                        minTime
                    )
                } ?: Long.MAX_VALUE // Groups with no enabled alarms go last
        }

    Scaffold(
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                // Left: Settings
                FloatingActionButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.align(Alignment.BottomStart)
                ) {
                    Icon(androidx.compose.material.icons.Icons.Default.Settings, contentDescription = stringResource(R.string.title_settings))
                }

                // Right: Expandable Action
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    AnimatedVisibility(
                        visible = isFabExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column(horizontalAlignment = Alignment.End) {
                            ExtendedFloatingActionButton(
                                onClick = {
                                    showTimerDialog = true
                                    isFabExpanded = false
                                },
                                modifier = Modifier.padding(bottom = 16.dp),
                                text = { Text(stringResource(R.string.action_add_timer)) },
                                icon = { Icon(androidx.compose.material.icons.Icons.Default.Timer, null) }
                            )

                            ExtendedFloatingActionButton(
                                onClick = {
                                    val now = Calendar.getInstance()
                                    // Use defaults from settings
                                    editingAlarm = settingsViewModel.createDefaultAlarm(
                                        now.get(Calendar.HOUR_OF_DAY),
                                        now.get(Calendar.MINUTE)
                                    )
                                    isCreatingNew = true
                                    isFabExpanded = false
                                },
                                modifier = Modifier.padding(bottom = 16.dp),
                                text = { Text(stringResource(R.string.action_add_alarm)) },
                                icon = { Icon(androidx.compose.material.icons.Icons.Default.Alarm, null) }
                            )
                        }
                    }

                    FloatingActionButton(onClick = { isFabExpanded = !isFabExpanded }) {
                        Icon(
                            if (isFabExpanded) androidx.compose.material.icons.Icons.Default.Close else androidx.compose.material.icons.Icons.Default.Add,
                            contentDescription = stringResource(if (isFabExpanded) R.string.desc_close else R.string.desc_expand)
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // --- ACTIVE TIMERS ---
            if (viewModel.activeTimers.isNotEmpty()) {
                Text(
                    stringResource(R.string.section_active_timers),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.align(Alignment.Start).padding(vertical = 8.dp)
                )
                viewModel.activeTimers.forEach { timer ->
                    val currentTime by viewModel.currentTime.collectAsStateWithLifecycle()
                    val diff = timer.endTime - currentTime
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                val intent = Intent(context, RingActivity::class.java).apply {
                                    putExtra("ALARM_TYPE", "TIMER")
                                    putExtra("ALARM_ID", timer.id)
                                    putExtra("START_TIME", timer.endTime - timer.totalDuration)
                                    setData(Uri.parse("custom://TIMER/${timer.id}"))
                                }
                                context.startActivity(intent)
                            },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if(diff>0) AlarmUtils.formatDuration(context, (diff/1000).toInt()) else stringResource(R.string.timer_ringing), style = MaterialTheme.typography.headlineSmall, color = Color(0xFFE65100))
                            Spacer(Modifier.weight(1f))
                            Button(onClick = { viewModel.stopTimer(timer.id) }) { Text(stringResource(R.string.action_stop)) }
                        }
                    }
                }
            } else {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showTimerDialog = true }
                ) { Text(stringResource(R.string.action_new_timer)) }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- MAIN LIST ---
            LazyColumn(modifier = Modifier.weight(1f)) {

                // 1. UNGROUPED ALARMS
                if (flatAlarms.isNotEmpty()) {
                    items(items = flatAlarms, key = { it.id }) { alarm ->
                        // Helper to check if this is the "Next Alarm" globally
                        val nextAlarmInfo = AlarmUtils.getNextAlarm(context)
                        val isNext = nextAlarmInfo?.timestamp == AlarmUtils.getNextOccurrence(
                            alarm.hour, alarm.minute, alarm.daysOfWeek, 0,
                            alarm.temporaryOverrideTime, alarm.snoozeUntil,
                            if (alarm.skippedUntil > System.currentTimeMillis()) alarm.skippedUntil else System.currentTimeMillis()
                        )

                        AlarmCard(
                            modifier = Modifier.animateItem(),
                            alarm = alarm,
                            groupOffset = 0,
                            groupSkippedUntil = 0L,
                            isNextAlarm = isNext,
                            isSnoozed = alarm.snoozeUntil != null,
                            snoozeUntil = alarm.snoozeUntil,
                            quickAdjustPresets = quickAdjustPresets,
                            onClick = { editingAlarm = alarm; isCreatingNew = false },
                            onToggleGroup = { viewModel.toggleAlarm(alarm, it) },
                            onDelete = { viewModel.deleteAlarm(alarm) },
                            onSkipNext = {
                                val nextRaw = AlarmUtils.getNextOccurrence(
                                    alarm.hour, alarm.minute, alarm.daysOfWeek, 0, alarm.temporaryOverrideTime, alarm.snoozeUntil
                                )
                                val baseNext = AlarmUtils.getNextOccurrence(
                                    alarm.hour, alarm.minute, alarm.daysOfWeek, 0, null, alarm.snoozeUntil
                                )
                                val skipTarget = maxOf(nextRaw, baseNext)
                                val updated = alarm.copy(skippedUntil = skipTarget + 1000)
                                viewModel.saveAlarm(updated, false)
                            },
                            onSkipUntil = { showDatePickerForAlarm = alarm },
                            onClearSkip = {
                                val updated = alarm.copy(skippedUntil = 0L, temporaryOverrideTime = null)
                                viewModel.saveAlarm(updated, false)
                            },
                            onAdjustTime = { mins -> viewModel.adjustAlarmTime(alarm, mins) },
                            onResetTime = { viewModel.resetAlarmAdjustment(alarm) }
                        )
                    }
                } else if (customGroups.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.no_alarms_create_one), color = Color.Gray)
                        }
                    }
                }

                // 2. CUSTOM GROUPS
                items(items = customGroups, key = { "group_${it.id}" }) { group ->
                    GroupCard(
                        modifier = Modifier.animateItem(),
                        group = group,
                        onToggleGroup = { isEnabled ->
                            group.alarms.forEach { a -> viewModel.toggleAlarm(a, isEnabled) }
                        },
                        onAdjust = { groupToAdjust = group },
                        onEdit = { groupToEdit = group },
                        onSkip = { viewModel.skipGroup(group, it) },
                        onDelete = { groupToDelete = group },
                        content = {
                            // Smart sorting for group alarms too
                            val groupAlarms = group.alarms.sortedWith(
                                compareBy<AlarmItem> { !it.isEnabled }
                                    .thenBy { alarm ->
                                        if (alarm.isEnabled) {
                                            val minTime = maxOf(
                                                System.currentTimeMillis(), 
                                                alarm.skippedUntil,
                                                group.skippedUntil
                                            )
                                            AlarmUtils.getNextOccurrence(
                                                alarm.hour, alarm.minute, alarm.daysOfWeek, 
                                                group.offsetMinutes,
                                                alarm.temporaryOverrideTime, 
                                                alarm.snoozeUntil,
                                                minTime
                                            )
                                        } else {
                                            Long.MAX_VALUE
                                        }
                                    }
                            )
                            groupAlarms.forEach { alarm ->
                                AlarmCard(
                                    modifier = Modifier.padding(vertical = 2.dp),
                                    alarm = alarm,
                                    groupOffset = group.offsetMinutes,
                                    groupSkippedUntil = group.skippedUntil,
                                    isNextAlarm = false,
                                    isSnoozed = alarm.snoozeUntil != null,
                                    snoozeUntil = alarm.snoozeUntil,
                                    quickAdjustPresets = quickAdjustPresets,
                                    onClick = { editingAlarm = alarm; isCreatingNew = false },
                                    onToggleGroup = { viewModel.toggleAlarm(alarm, it) },
                                    onDelete = { viewModel.deleteAlarm(alarm) },
                                    onSkipNext = {
                                        val nextRaw = AlarmUtils.getNextOccurrence(
                                            alarm.hour, alarm.minute, alarm.daysOfWeek, group.offsetMinutes, alarm.temporaryOverrideTime, alarm.snoozeUntil
                                        )
                                        val baseNext = AlarmUtils.getNextOccurrence(
                                            alarm.hour, alarm.minute, alarm.daysOfWeek, group.offsetMinutes, null, alarm.snoozeUntil
                                        )
                                        val skipTarget = maxOf(nextRaw, baseNext)
                                        val updated = alarm.copy(skippedUntil = skipTarget + 1000)
                                        viewModel.saveAlarm(updated, false)
                                    },
                                    onSkipUntil = { showDatePickerForAlarm = alarm },
                                    onClearSkip = {
                                        val updated = alarm.copy(skippedUntil = 0L, temporaryOverrideTime = null)
                                        viewModel.saveAlarm(updated, false)
                                    },
                                    onAdjustTime = { viewModel.adjustAlarmTime(alarm, it) },
                                    onResetTime = { viewModel.resetAlarmAdjustment(alarm) }
                                )
                            }
                        }
                    )
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    // --- DIALOGS ---

    // 1. Edit Alarm & Create Group Logic
    if (editingAlarm != null) {
        EditAlarmDialog(
            alarm = editingAlarm!!,
            allGroups = viewModel.groups,
            onDismiss = { editingAlarm = null },
            onSave = { resultAlarm, newGroupName, newGroupColor ->
                if (!newGroupName.isNullOrBlank()) {
                    // Use the new safe ViewModel function
                    val color = newGroupColor ?: 0xFFFFFFFF.toInt()
                    viewModel.saveAlarmWithNewGroup(resultAlarm, newGroupName, color, isCreatingNew)
                } else {
                    viewModel.saveAlarm(resultAlarm, isCreatingNew)
                }
                editingAlarm = null
            }
        )
    }


    // 1. Edit Group Name/Color
    if (groupToEdit != null) {
        EditGroupDialog(
            group = groupToEdit!!,
            onDismiss = { groupToEdit = null },
            onSave = { name, color ->
                if (groupToEdit!!.id.isEmpty()) {
                    // viewModel.createGroup(name, color)
                } else {
                    viewModel.updateGroupDetails(groupToEdit!!, name, color)
                }
                groupToEdit = null
            },
            onDelete = { keepAlarms ->
                if (groupToEdit!!.id.isNotEmpty()) {
                    // keepAlarms=true: move alarms to default group
                    // keepAlarms=false: delete alarms
                    if (keepAlarms) {
                        groupToEdit!!.alarms.forEach { alarm ->
                            val updated = alarm.copy(groupId = "default")
                            viewModel.saveAlarm(updated, false)
                        }
                    }
                    viewModel.deleteGroup(groupToEdit!!, keepAlarms)
                }
                groupToEdit = null
            }
        )
    }

    // 2. Group Time Adjust
    if (groupToAdjust != null) {
        // Find the earliest next occurrence in the group
        val nextTime = groupToAdjust!!.alarms
            .filter { it.isEnabled }
            .minOfOrNull { alarm ->
                AlarmUtils.getNextOccurrence(
                    alarm.hour,
                    alarm.minute,
                    alarm.daysOfWeek,
                    0, // Ignore group offset
                    alarm.temporaryOverrideTime,
                    alarm.snoozeUntil,
                    System.currentTimeMillis()
                )
            } ?: System.currentTimeMillis()

        QuickAdjustDialog(
            quickAdjustPresets = quickAdjustPresets,
            overrideTitle = stringResource(R.string.dialog_title_adjust_group_time),
            currentDisplay = stringResource(R.string.adjust_all_count_alarms, groupToAdjust!!.alarms.count { it.isEnabled }),
            currentNextTime = nextTime, // Pass the calculated next time
            hasActiveOverride = groupToAdjust!!.alarms.any { it.temporaryOverrideTime != null },
            onDismiss = { groupToAdjust = null },
            onAdjust = { mins ->
                // Apply the 6-hour limit in the UI as well
                val clampedMins = mins.coerceIn(-360, 360)
                viewModel.adjustGroupAlarms(groupToAdjust!!, clampedMins)
                groupToAdjust = null
            },
            onReset = {
                viewModel.resetGroupAlarms(groupToAdjust!!)
                groupToAdjust = null
            }
        )
    }

    // Delete Group Dialog
    if (groupToDelete != null) {
        AlertDialog(
            onDismissRequest = { groupToDelete = null },
            title = { Text(stringResource(R.string.delete_group_name, groupToDelete!!.name)) },
            text = { Text(stringResource(R.string.groups_delete_alarms_too)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGroup(groupToDelete!!, keepAlarms = false)
                    groupToDelete = null
                }) { Text(stringResource(R.string.group_delete_all), color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.deleteGroup(groupToDelete!!, keepAlarms = true)
                    groupToDelete = null
                }) { Text(stringResource(R.string.group_delete_keep_alarms)) }
            }
        )
    }

    if (showTimerDialog) {
        EditTimerDialog(timerPresets = timerPresets, onDismiss = { showTimerDialog = false }, onConfirm = { viewModel.startTimer(it); showTimerDialog = false })
    }
    if (showDatePickerForAlarm != null) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePickerForAlarm = null },
            confirmButton = {
                TextButton(onClick = {
                    val date = datePickerState.selectedDateMillis
                    if (date != null) {
                        val alarm = showDatePickerForAlarm!!
                        val updated = alarm.copy(skippedUntil = date)
                        viewModel.saveAlarm(updated, false)
                    }
                    showDatePickerForAlarm = null
                }) { Text(stringResource(R.string.action_save)) }
            }
        ) { DatePicker(state = datePickerState) }
    }
}