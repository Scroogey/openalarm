package de.laurik.openalarm

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
fun DefaultAlarmSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()

    // State Collection (Defaults from Repository)
    val vibration by viewModel.defVibrationEnabled.collectAsState()
    val ringtoneUri by viewModel.defRingtoneUri.collectAsState()
    val customVolume by viewModel.defCustomVolume.collectAsState()
    val fadeIn by viewModel.defFadeInSeconds.collectAsState()
    val ttsMode by viewModel.defTtsMode.collectAsState()
    val isSingleUse by viewModel.defIsSingleUse.collectAsState()
    val isSelfDestroying by viewModel.defIsSelfDestroying.collectAsState()
    val daysOfWeek by viewModel.defDaysOfWeek.collectAsState()
    val isSnoozeEnabled by viewModel.defIsSnoozeEnabled.collectAsState()
    val directSnooze by viewModel.defDirectSnooze.collectAsState()
    val maxSnoozes by viewModel.defMaxSnoozes.collectAsState()
    
    val globalSnooze by viewModel.defaultSnooze.collectAsState()
    val globalAutoStop by viewModel.defaultAutoStop.collectAsState()
    val snoozePresets by viewModel.defaultSnoozePresets.collectAsState()
    
    // Ringing Screen Defaults
    val ringingMode by viewModel.defaultRingingMode.collectAsState()
    // For now we'll use placeholder states for these until we add them to SettingsRepo if needed
    var bgType by remember { mutableStateOf("COLOR") }
    var bgValue by remember { mutableStateOf("0xFF000000") }
    var showBackgroundPicker by remember { mutableStateOf(false) }

    var showSnoozeEdit by remember { mutableStateOf(false) }
    var showMaxSnoozeEdit by remember { mutableStateOf(false) }
    var showAutoStopEdit by remember { mutableStateOf(false) }
    var showSnoozePresetsEdit by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.desc_back))
                }
                Text(stringResource(R.string.title_default_alarm_settings), style = MaterialTheme.typography.headlineMedium)
            }
            HorizontalDivider()

            Column(Modifier.verticalScroll(scrollState)) {
                AlarmConfigSection(
                    label = stringResource(R.string.label_default),
                    onLabelChange = { /* Default label usually empty */ },
                    vibration = vibration,
                    onVibrationChange = { viewModel.setDefVibration(it) },
                    ringtoneUri = ringtoneUri,
                    onRingtoneChange = { viewModel.setDefRingtone(it) },
                    customVolume = customVolume,
                    onVolumeChange = { viewModel.setDefVolume(it) },
                    fadeInSeconds = fadeIn,
                    onFadeInChange = { viewModel.setDefFadeIn(it) },
                    ttsMode = ttsMode,
                    onTtsModeChange = { viewModel.setDefTtsMode(it) },
                    isSingleUse = isSingleUse,
                    onSingleUseChange = { viewModel.setDefSingleUse(it) },
                    isSelfDestroying = isSelfDestroying,
                    onSelfDestroyingChange = { viewModel.setDefSelfDestroy(it) },
                    daysOfWeek = daysOfWeek,
                    onDaysOfWeekChange = { viewModel.setDefDaysOfWeek(it) },
                    isSnoozeEnabled = isSnoozeEnabled,
                    onSnoozeEnabledChange = { viewModel.setDefSnoozeEnabled(it) },
                    snoozeDuration = globalSnooze, 
                    onSnoozeDurationChange = { showSnoozeEdit = true },
                    maxSnoozes = maxSnoozes,
                    onMaxSnoozesChange = { showMaxSnoozeEdit = true },
                    autoStopDuration = globalAutoStop,
                    onAutoStopDurationChange = { showAutoStopEdit = true },
                    directSnooze = directSnooze,
                    onDirectSnoozeChange = { viewModel.setDefDirectSnooze(it) },
                    snoozePresets = snoozePresets,
                    onSnoozePresetsChange = { showSnoozePresetsEdit = true },
                    ringingMode = ringingMode,
                    onRingingModeChange = { viewModel.setDefaultRingingMode(it) },
                    backgroundType = bgType,
                    onBackgroundTypeChange = { showBackgroundPicker = true },
                    backgroundValue = bgValue,
                    onBackgroundValueChange = { /* picker handles this */ },
                    showRingingMode = false
                )
                
                Spacer(Modifier.height(32.dp))
            }

            if (showBackgroundPicker) {
                BackgroundConfigDialog(
                    currentType = bgType,
                    currentValue = bgValue,
                    onDismiss = { showBackgroundPicker = false },
                    onConfirm = { t, v ->
                        bgType = t
                        bgValue = v
                        showBackgroundPicker = false
                    }
                )
            }
        }
        if (showSnoozeEdit) OverrideInputDialog(
            stringResource(R.string.dialog_title_default_snooze),
            globalSnooze,
            10,
            { showSnoozeEdit = false },
            { it?.let { viewModel.setDefaultSnooze(it) }; showSnoozeEdit = false }
        )
        if (showAutoStopEdit) OverrideInputDialog(
            stringResource(R.string.dialog_title_default_auto_stop),
            globalAutoStop,
            10,
            { showAutoStopEdit = false },
            { it?.let { viewModel.setDefaultAutoStop(it) }; showAutoStopEdit = false }
        )
        if (showMaxSnoozeEdit) OverrideInputDialog(
            stringResource(R.string.dialog_title_default_max_snoozes),
            maxSnoozes ?: 0,
            0,
            { showMaxSnoozeEdit = false },
            { viewModel.setDefMaxSnoozes(if (it == 0) null else it); showMaxSnoozeEdit = false }
        )
        if (showSnoozePresetsEdit) PresetEditDialog(
            stringResource(R.string.dialog_title_default_snooze_presets),
            snoozePresets,
            { showSnoozePresetsEdit = false },
            { viewModel.setDefaultSnoozePresets(it); showSnoozePresetsEdit = false }
        )
    }
}
