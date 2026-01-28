package de.laurik.openalarm

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.Icons
import androidx.compose.ui.draw.clip

@Composable
fun AlarmConfigSection(
    label: String,
    onLabelChange: (String) -> Unit,
    vibration: Boolean,
    onVibrationChange: (Boolean) -> Unit,
    ringtoneUri: String?,
    onRingtoneChange: (String?) -> Unit,
    customVolume: Float?,
    onVolumeChange: (Float?) -> Unit,
    fadeInSeconds: Int,
    onFadeInChange: (Int) -> Unit,
    ttsMode: TtsMode,
    onTtsModeChange: (TtsMode) -> Unit,
    ttsText: String,
    onTtsTextChange: (String) -> Unit,
    isSingleUse: Boolean,
    onSingleUseChange: (Boolean) -> Unit,
    isSelfDestroying: Boolean,
    onSelfDestroyingChange: (Boolean) -> Unit,
    isSnoozeEnabled: Boolean,
    onSnoozeEnabledChange: (Boolean) -> Unit,
    snoozeDuration: Int?, // null = global
    onSnoozeDurationChange: (Int?) -> Unit,
    maxSnoozes: Int?, // null = unlimited
    onMaxSnoozesChange: (Int?) -> Unit,
    autoStopDuration: Int?, // null = global
    onAutoStopDurationChange: (Int?) -> Unit,
    directSnooze: Boolean = false,
    onDirectSnoozeChange: (Boolean) -> Unit,
    snoozePresets: List<Int>?,
    onSnoozePresetsChange: (List<Int>?) -> Unit,
    daysOfWeek: List<Int>,
    onDaysOfWeekChange: (List<Int>) -> Unit,
    ringingMode: RingingScreenMode,
    onRingingModeChange: (RingingScreenMode) -> Unit,
    backgroundType: String,
    onBackgroundTypeChange: (String) -> Unit,
    backgroundValue: String,
    onBackgroundValueChange: (String) -> Unit,

    showRingingMode: Boolean = true,
    showDefaultRingingMode: Boolean = false,
    
    // Help labels for "use default" cases
    globalSnooze: Int = 10,
    globalAutoStop: Int = 10
) {
    val context = LocalContext.current
    val ringtoneTitle = remember(ringtoneUri) { RingtoneUtils.getRingtoneTitle(context, ringtoneUri) }
    val ringtoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == android.app.Activity.RESULT_OK) {
            onRingtoneChange(res.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)?.toString())
        }
    }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        // 1. LABEL
        OutlinedTextField(
            value = label,
            onValueChange = onLabelChange,
            label = { Text(stringResource(R.string.hint_label)) },
            placeholder = { Text(stringResource(R.string.default_alarm_title)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(Modifier.height(16.dp))

        SwipeableDaySelector(
            selectedDays = daysOfWeek,
            onSelectionChanged = { 
                onDaysOfWeekChange(it)
                if (it.isEmpty()) onSingleUseChange(true)
            }
        )
        Spacer(Modifier.height(8.dp))

        ListItem(
            headlineContent = { Text(stringResource(R.string.setting_single_use)) },
            trailingContent = { Switch(checked = isSingleUse, onCheckedChange = onSingleUseChange) }
        )
        AnimatedVisibility(visible = isSingleUse) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_self_destroy)) },
                trailingContent = { Switch(checked = isSelfDestroying, onCheckedChange = onSelfDestroyingChange) }
            )
        }
        HorizontalDivider()

        // VIBRATION
        ListItem(
            headlineContent = { Text(stringResource(R.string.label_vibration)) },
            trailingContent = { Switch(checked = vibration, onCheckedChange = onVibrationChange) }
        )
        HorizontalDivider()

        // 3. AUDIO
        Text(
            stringResource(R.string.label_audio),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        val selectToneTitle = stringResource(R.string.title_select_tone)
        ListItem(
            headlineContent = { Text(stringResource(R.string.label_sound)) },
            supportingContent = { Text(ringtoneTitle) },
            trailingContent = { Text(">") },
            modifier = Modifier.clickable {
                val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, selectToneTitle)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, ringtoneUri?.let { Uri.parse(it) })
                }
                ringtoneLauncher.launch(i)
            }
        )

        // Volume Slider
        Column(modifier = Modifier.padding(top = 12.dp)) {
            Row {
                Text(stringResource(R.string.label_volume_override))
                Spacer(Modifier.weight(1f))
                Text(if (customVolume != null) stringResource(R.string.label_volume_percent, (customVolume * 100).toInt()) else stringResource(R.string.label_system))
            }
            Slider(value = customVolume ?: 0.5f, onValueChange = onVolumeChange, valueRange = 0f..1f)
            if (customVolume != null) {
                TextButton(onClick = { onVolumeChange(null) }, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.action_reset))
                }
            }
        }

        // Fade In
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.label_fade_in), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.weight(1f))
                Switch(checked = fadeInSeconds > 0, onCheckedChange = { onFadeInChange(if (it) 30 else 0) })
            }
            AnimatedVisibility(visible = fadeInSeconds > 0) {
                Column {
                    Text(stringResource(R.string.fmt_duration_s, fadeInSeconds), style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    Slider(value = fadeInSeconds.toFloat(), onValueChange = { onFadeInChange(it.toInt()) }, valueRange = 1f..180f)
                }
            }
        }

        // TTS
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.label_speak_time), style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = ttsMode != TtsMode.NONE,
                    onCheckedChange = { enabled ->
                        onTtsModeChange(if (enabled) TtsMode.ONCE else TtsMode.NONE)
                    }
                )
            }
            
            AnimatedVisibility(visible = ttsMode != TtsMode.NONE) {
                Column {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.label_tts_frequency),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = ttsMode == TtsMode.ONCE,
                            onClick = { onTtsModeChange(TtsMode.ONCE) },
                            label = { Text(stringResource(R.string.tts_once)) },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = ttsMode == TtsMode.EVERY_MINUTE,
                            onClick = { onTtsModeChange(TtsMode.EVERY_MINUTE) },
                            label = { Text(stringResource(R.string.tts_every_min)) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = ttsText,
                        onValueChange = onTtsTextChange,
                        label = { Text(stringResource(R.string.label_custom_tts_text)) },
                        placeholder = { Text(stringResource(R.string.hint_custom_tts_text)) },
                        supportingText = { Text(stringResource(R.string.desc_tts_variables)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 3
                    )
                }
            }
        }
        HorizontalDivider()

        // 4. SNOOZE & TIMEOUT
        Text(stringResource(R.string.settings_alarm_behaviors), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 12.dp))

        ListItem(
            headlineContent = { Text(stringResource(R.string.setting_allow_snooze)) },
            trailingContent = { Switch(checked = isSnoozeEnabled, onCheckedChange = onSnoozeEnabledChange) }
        )
        AnimatedVisibility(visible = isSnoozeEnabled) {
            Column {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.setting_direct_snooze)) },
                    supportingContent = { Text(stringResource(R.string.setting_direct_snooze_subtext)) },
                    trailingContent = { Switch(checked = directSnooze, onCheckedChange = onDirectSnoozeChange) }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.setting_snooze_duration)) },
                    supportingContent = { Text(if (snoozeDuration == null) stringResource(R.string.setting_snooze_duration_subtext_default, globalSnooze) else stringResource(R.string.setting_snooze_duration_subtext_custom, snoozeDuration)) },
                    modifier = Modifier.clickable { onSnoozeDurationChange(snoozeDuration) }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.setting_max_snoozes)) },
                    supportingContent = { Text(if (maxSnoozes == null) stringResource(R.string.setting_max_snoozes_subtext_infinite) else stringResource(R.string.setting_max_snoozes_subtext_custom, maxSnoozes)) },
                    modifier = Modifier.clickable { onMaxSnoozesChange(maxSnoozes) }
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.setting_snooze_presets)) },
                    supportingContent = {
                        val defaultText = stringResource(R.string.setting_snooze_presets_default)
                        val fmtMinutesShort = stringResource(R.string.fmt_minutes_short)
                        Text(if (snoozePresets == null) defaultText else snoozePresets.joinToString(", ") { it.toString() + fmtMinutesShort })
                    },
                    modifier = Modifier.clickable { onSnoozePresetsChange(snoozePresets) }
                )
            }
        }
        
        ListItem(
            headlineContent = { Text(stringResource(R.string.setting_timeout)) },
            supportingContent = { Text(if (autoStopDuration == null) stringResource(R.string.setting_timeout_default, globalAutoStop) else stringResource(R.string.setting_timeout_custom, autoStopDuration)) },
            modifier = Modifier.clickable { onAutoStopDurationChange(autoStopDuration) }
        )

        HorizontalDivider()

        // 5. RINGING SCREEN STYLE
        Text(stringResource(R.string.label_ringing_experience), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 12.dp))

        if (showRingingMode) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_ringing_mode)) },
                supportingContent = { Text(ringingMode.name) },
                modifier = Modifier.clickable {
                    val modes = if (showDefaultRingingMode) {
                        RingingScreenMode.entries.toList()
                    } else {
                        RingingScreenMode.entries.filter { it != RingingScreenMode.DEFAULT }
                    }
                    val currentIndex = modes.indexOf(ringingMode)
                    val next = modes.getOrElse((currentIndex + 1) % modes.size) { modes.first() }
                    onRingingModeChange(next)
                }
            )
        }

        ListItem(
            headlineContent = { Text(stringResource(R.string.setting_alarm_background)) },
            supportingContent = { Text(if (backgroundType == "COLOR") stringResource(R.string.setting_text_solid_color) else stringResource(R.string.setting_text_gradient)) },
            modifier = Modifier.clickable { onBackgroundTypeChange(backgroundType) }
        )
    }
}

@Composable
fun ColorPickerGrid(
    selectedColor: String,
    onColorSelected: (String) -> Unit
) {
    val colors = listOf(
        "#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5", "#2196F3",
        "#03A9F4", "#00BCD4", "#009688", "#4CAF50", "#8BC34A", "#CDDC39",
        "#FFEB3B", "#FFC107", "#FF9800", "#FF5722", "#795548", "#9E9E9E",
        "#607D8B", "#000000", "#FFFFFF"
    )

    Column {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 44.dp),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 240.dp)
        ) {
            items(colors) { colorHex ->
                val color = runCatching { Color(android.graphics.Color.parseColor(colorHex)) }.getOrDefault(Color.Gray)
                val isSelected = selectedColor.equals(colorHex, ignoreCase = true)
                
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                        .clickable { onColorSelected(colorHex) },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BackgroundConfigDialog(
    currentType: String,
    currentValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var type by remember { mutableStateOf(currentType) }
    var color1 by remember { mutableStateOf("") }
    var color2 by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        if (currentType == "COLOR") {
            color1 = currentValue
        } else if (currentType == "GRADIENT") {
            val parts = currentValue.split(",")
            color1 = parts.getOrNull(0)?.trim() ?: "#000000"
            color2 = parts.getOrNull(1)?.trim() ?: "#000000"
        } else {
            color1 = "#000000"
            color2 = "#000000"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.setting_alarm_background_dialog_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = type == "COLOR",
                        onClick = { type = "COLOR" },
                        label = { Text(stringResource(R.string.label_color)) }
                    )
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = type == "GRADIENT",
                        onClick = { type = "GRADIENT" },
                        label = { Text(stringResource(R.string.setting_text_gradient)) }
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(if (type == "COLOR") stringResource(R.string.setting_pick_color) else stringResource(R.string.setting_pick_top_color), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                ColorPickerGrid(
                    selectedColor = color1,
                    onColorSelected = { color1 = it }
                )
                
                if (type == "GRADIENT") {
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.setting_pick_bottom_color), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    ColorPickerGrid(
                        selectedColor = color2,
                        onColorSelected = { color2 = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(type, if (type == "COLOR") color1 else "$color1,$color2") }) {
                Text(stringResource(R.string.button_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.button_cancel)) }
        }
    )
}
