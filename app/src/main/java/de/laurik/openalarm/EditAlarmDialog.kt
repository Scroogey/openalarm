package de.laurik.openalarm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditAlarmDialog(
    alarm: AlarmItem,
    allGroups: List<AlarmGroup>,
    onDismiss: () -> Unit,
    onSave: (AlarmItem, String?, Int?) -> Unit
) {
    var hour by remember { mutableIntStateOf(alarm.hour) }
    var minute by remember { mutableIntStateOf(alarm.minute) }
    var label by remember { mutableStateOf(alarm.label) }
    var daysOfWeek by remember { mutableStateOf(alarm.daysOfWeek) }
    var vibration by remember { mutableStateOf(alarm.vibrationEnabled) }
    var currentUriStr by remember { mutableStateOf(alarm.ringtoneUri) }
    var customVolume by remember { mutableStateOf(alarm.customVolume) }
    var fadeInSeconds by remember { mutableIntStateOf(alarm.fadeInSeconds) }
    var ttsMode by remember { mutableStateOf(alarm.ttsMode) }
    var ttsText by remember { mutableStateOf(alarm.ttsText ?: "") }
    var isSingleUse by remember { mutableStateOf(alarm.isSingleUse) }
    var isSelfDestroying by remember { mutableStateOf(alarm.isSelfDestroying) }

    // Ringing Screen Revamp
    var ringingScreenMode by remember { mutableStateOf(alarm.ringingScreenMode) }
    var backgroundType by remember { mutableStateOf(alarm.backgroundType) }
    var backgroundValue by remember { mutableStateOf(alarm.backgroundValue) }

    // Group Selection
    var selectedGroupId by remember { mutableStateOf(alarm.groupId) }
    var isNewGroupMode by remember { mutableStateOf(false) }
    var newGroupName by remember { mutableStateOf("") }
    var groupDropdownExpanded by remember { mutableStateOf(false) }
    var selectedColor by remember { mutableIntStateOf(0xFFE3F2FD.toInt()) }
    val groupColors = listOf(0xFFE3F2FD, 0xFFF3E5F5, 0xFFE8F5E9, 0xFFFFF3E0, 0xFFFFEBEE, 0xFFE0F7FA)

    // Overrides
    var snoozeOverride by remember { mutableStateOf(alarm.snoozeDuration) }
    var autoStopOverride by remember { mutableStateOf(alarm.autoStopDuration) }

    // Snooze Logic
    var isSnoozeEnabled by remember { mutableStateOf(alarm.isSnoozeEnabled) }
    var directSnooze by remember { mutableStateOf(alarm.directSnooze) }
    var maxSnoozes by remember { mutableStateOf(alarm.maxSnoozes) } // null = unlimited
    var snoozePresets by remember { mutableStateOf(alarm.snoozePresets) }

    // Dialogs
    var showSnoozeEdit by remember { mutableStateOf(false) }
    var showAutoStopEdit by remember { mutableStateOf(false) }
    var showMaxSnoozeEdit by remember { mutableStateOf(false) }
    var showBackgroundPicker by remember { mutableStateOf(false) }
    var showSnoozePresetsEdit by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository.getInstance(context) }
    val globalSnooze by settingsRepo.defaultSnooze.collectAsState(initial = 10)
    val globalAutoStop by settingsRepo.defaultAutoStop.collectAsState(initial = 10)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (alarm.id == 0) stringResource(R.string.title_new_alarm) else stringResource(
                                R.string.title_edit_alarm
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                stringResource(R.string.desc_close)
                            )
                        }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize().imePadding()) {
                SmartTimePickerLayout(
                    hour = hour, minute = minute, snapImmediately = true,
                    onTimeChange = { h, m, _ -> hour = h; minute = m }
                ) { wheelContent, numpadContent ->
                    Column(modifier = Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(Modifier.height(24.dp))
                            wheelContent()
                            Spacer(Modifier.height(24.dp))

                            if (numpadContent == null) {
                                Spacer(Modifier.height(16.dp))

                                HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                                AlarmConfigSection(
                                    label = label,
                                    onLabelChange = { label = it },
                                    vibration = vibration,
                                    onVibrationChange = { vibration = it },
                                    ringtoneUri = currentUriStr,
                                    onRingtoneChange = { currentUriStr = it },
                                    customVolume = customVolume,
                                    onVolumeChange = { customVolume = it },
                                    fadeInSeconds = fadeInSeconds,
                                    onFadeInChange = { fadeInSeconds = it },
                                    ttsMode = ttsMode,
                                    onTtsModeChange = { ttsMode = it },
                                    ttsText = ttsText,
                                    onTtsTextChange = { ttsText = it },
                                    isSingleUse = isSingleUse,
                                    onSingleUseChange = {
                                        isSingleUse = it
                                        if (!it) isSelfDestroying = false
                                    },
                                    isSelfDestroying = isSelfDestroying,
                                    onSelfDestroyingChange = { isSelfDestroying = it },
                                    daysOfWeek = daysOfWeek,
                                    onDaysOfWeekChange = { daysOfWeek = it },
                                    isSnoozeEnabled = isSnoozeEnabled,
                                    onSnoozeEnabledChange = { isSnoozeEnabled = it },
                                    snoozeDuration = snoozeOverride,
                                    onSnoozeDurationChange = { showSnoozeEdit = true },
                                    maxSnoozes = maxSnoozes,
                                    onMaxSnoozesChange = { showMaxSnoozeEdit = true },
                                    autoStopDuration = autoStopOverride,
                                    onAutoStopDurationChange = { showAutoStopEdit = true },
                                    directSnooze = directSnooze,
                                    onDirectSnoozeChange = { directSnooze = it },
                                    snoozePresets = snoozePresets,
                                    onSnoozePresetsChange = { showSnoozePresetsEdit = true },
                                    ringingMode = ringingScreenMode,
                                    onRingingModeChange = { ringingScreenMode = it },
                                    backgroundType = backgroundType,
                                    onBackgroundTypeChange = { backgroundType = it; showBackgroundPicker = true },
                                    backgroundValue = backgroundValue,
                                    onBackgroundValueChange = { /* picker handles this */ },
                                    globalSnooze = globalSnooze,
                                    globalAutoStop = globalAutoStop,
                                    showDefaultRingingMode = true
                                )
                                HorizontalDivider()

                                // Group Selector
                                Column(Modifier.padding(12.dp)) {
                                    Text(stringResource(R.string.label_group), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 12.dp))
                                    Spacer(Modifier.height(8.dp))

                                    if (isNewGroupMode) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            OutlinedTextField(
                                                value = newGroupName,
                                                onValueChange = { newGroupName = it },
                                                label = { Text(stringResource(R.string.hint_group_name)) },
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(onClick = { isNewGroupMode = false }) {
                                                Icon(
                                                    Icons.Default.Close,
                                                    stringResource(R.string.action_cancel)
                                                )
                                            }
                                        }
                                        // Color Picker
                                        Row(
                                            Modifier.padding(top = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            groupColors.forEach { colorLong ->
                                                val colorInt = colorLong.toInt()
                                                val isSelected = selectedColor == colorInt
                                                Box(
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(colorInt))
                                                        .border(
                                                            if (isSelected) 3.dp else 1.dp,
                                                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                                            CircleShape
                                                        )
                                                        .clickable { selectedColor = colorInt },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (isSelected) {
                                                        Icon(
                                                            Icons.Default.Check,
                                                            null,
                                                            tint = Color.Black.copy(alpha = 0.6f)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Box {
                                            val currentGroup =
                                                allGroups.find { it.id == selectedGroupId }
                                            val currentGroupName =
                                                if (currentGroup == null || currentGroup.id == "default")
                                                    stringResource(R.string.action_add_to_group)
                                                else
                                                    currentGroup.name

                                            OutlinedButton(
                                                onClick = { groupDropdownExpanded = true },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    currentGroupName,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }

                                            DropdownMenu(
                                                expanded = groupDropdownExpanded,
                                                onDismissRequest = {
                                                    groupDropdownExpanded = false
                                                }
                                            ) {
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.label_no_group)) },
                                                    onClick = {
                                                        selectedGroupId = "default"
                                                        groupDropdownExpanded = false
                                                    }
                                                )
                                                HorizontalDivider()
                                                allGroups.filter { it.id != "default" }
                                                    .forEach { group ->
                                                        DropdownMenuItem(
                                                            text = { Text(group.name) },
                                                            onClick = {
                                                                selectedGroupId = group.id
                                                                groupDropdownExpanded = false
                                                            }
                                                        )
                                                    }
                                                HorizontalDivider()
                                                DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.action_create_new_group)) },
                                                    onClick = {
                                                        isNewGroupMode = true
                                                        groupDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Bottom Actions
                        if (numpadContent != null) {
                            Surface(
                                shadowElevation = 8.dp,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                numpadContent()
                            }
                        } else {
                            Surface(
                                shadowElevation = 8.dp,
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    TextButton(
                                        onClick = onDismiss,
                                        modifier = Modifier.height(56.dp).weight(1f)
                                    ) { Text(stringResource(R.string.action_cancel)) }
                                    Spacer(Modifier.width(16.dp))
                                    Button(
                                        onClick = {
                                            val updated = alarm.copy(
                                                hour = hour,
                                                minute = minute,
                                                label = label,
                                                daysOfWeek = daysOfWeek,
                                                vibrationEnabled = vibration,
                                                ringtoneUri = currentUriStr,
                                                customVolume = customVolume,
                                                fadeInSeconds = fadeInSeconds,
                                                ttsMode = ttsMode,
                                                ttsText = ttsText.ifBlank { null },
                                                groupId = selectedGroupId,
                                                snoozeDuration = snoozeOverride,
                                                autoStopDuration = autoStopOverride,
                                                isSnoozeEnabled = isSnoozeEnabled,
                                                directSnooze = directSnooze,
                                                maxSnoozes = maxSnoozes,
                                                snoozePresets = snoozePresets,
                                                isSingleUse = isSingleUse,
                                                isSelfDestroying = isSelfDestroying,
                                                ringingScreenMode = ringingScreenMode,
                                                backgroundType = backgroundType,
                                                backgroundValue = backgroundValue
                                            )
                                            if (isNewGroupMode && newGroupName.isNotBlank()) {
                                                onSave(
                                                    updated,
                                                    newGroupName,
                                                    selectedColor
                                                )
                                            } else {
                                                onSave(updated, null, null)
                                            }
                                        },
                                        modifier = Modifier.height(56.dp).weight(1f)
                                    ) { Text(stringResource(R.string.action_save)) }
                                }
                            }
                        }
                    }
                }

                // Internal Dialogs
                if (showSnoozeEdit) OverrideInputDialog(
                    stringResource(R.string.dialog_title_snooze_duration),
                    snoozeOverride,
                    globalSnooze,
                    { showSnoozeEdit = false },
                    { snoozeOverride = it; showSnoozeEdit = false })
                
                if (showAutoStopEdit) OverrideInputDialog(
                    stringResource(R.string.dialog_title_auto_stop_timeout),
                    autoStopOverride,
                    globalAutoStop,
                    { showAutoStopEdit = false },
                    { autoStopOverride = it; showAutoStopEdit = false })
                
                if (showMaxSnoozeEdit) {
                    var buffer by remember { mutableStateOf(maxSnoozes?.toString() ?: "") }
                    Dialog(onDismissRequest = { showMaxSnoozeEdit = false }) {
                        Surface(shape = MaterialTheme.shapes.large) {
                            Column(
                                Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    stringResource(R.string.dialog_title_max_snooze_count),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    if (buffer.isEmpty()) stringResource(R.string.label_unlimited) else buffer,
                                    style = MaterialTheme.typography.displayMedium
                                )
                                Spacer(Modifier.height(16.dp))
                                OutlinedButton(onClick = {
                                    showMaxSnoozeEdit = false; maxSnoozes = null
                                }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_set_unlimited)) }
                                IntegratedNumpad(
                                    onInput = { if (buffer.length < 2) buffer += it },
                                    onDelete = {
                                        if (buffer.isNotEmpty()) buffer = buffer.dropLast(1)
                                    },
                                    onConfirm = {
                                        maxSnoozes = buffer.toIntOrNull(); showMaxSnoozeEdit = false
                                    },
                                    onCancel = { showMaxSnoozeEdit = false }
                                )
                            }
                        }
                    }
                }

                if (showSnoozePresetsEdit) {
                    val globalPresets by settingsRepo.defaultSnoozePresets.collectAsState(initial = listOf(5, 10, 15))
                    PresetEditDialog(
                        title = stringResource(R.string.dialog_title_snooze_presets),
                        currentValues = snoozePresets ?: globalPresets,
                        onDismiss = { showSnoozePresetsEdit = false },
                        onConfirm = { snoozePresets = it; showSnoozePresetsEdit = false }
                    )
                }

                if (showBackgroundPicker) {
                    BackgroundConfigDialog(
                        currentType = backgroundType,
                        currentValue = backgroundValue,
                        onDismiss = { showBackgroundPicker = false },
                        onConfirm = { t, v ->
                            backgroundType = t
                            backgroundValue = v
                            showBackgroundPicker = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun OverrideInputDialog(
    title: String,
    currentVal: Int?,
    defaultVal: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit
) {
    var buffer by remember { mutableStateOf(currentVal?.toString() ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))

                if (buffer.isEmpty()) {
                    Text(
                        stringResource(R.string.label_use_global_default, defaultVal),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        stringResource(R.string.label_minutes_fmt, buffer.toIntOrNull() ?: 0),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(Modifier.height(24.dp))

                OutlinedButton(
                    onClick = { onConfirm(null) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(stringResource(R.string.action_reset)) }

                Spacer(Modifier.height(8.dp))

                IntegratedNumpad(
                    onInput = { if (buffer.length < 3) buffer += it },
                    onDelete = { if (buffer.isNotEmpty()) buffer = buffer.dropLast(1) },
                    onConfirm = { onConfirm(buffer.toIntOrNull()) },
                    onCancel = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}