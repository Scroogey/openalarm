package de.laurik.openalarm

import android.annotation.SuppressLint
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewModelScope
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onClose: () -> Unit
) {
    val scrollState = rememberScrollState()

    // State Collection
    val themeMode by viewModel.themeMode.collectAsState()


    val timerRingtone by viewModel.timerRingtone.collectAsState()
    val timerVolume by viewModel.timerVolume.collectAsState()
    val timerVibration by viewModel.timerVibration.collectAsState()
    val timerTtsEnabled by viewModel.timerTtsEnabled.collectAsState()
    val timerTtsText by viewModel.timerTtsText.collectAsState()

    // Dialog States
    var showNotifyBeforeDialog by remember { mutableStateOf(false) }
    var currentSubScreen by remember { mutableStateOf<String?>(null) } // null, "DEFAULT_ALARM"
    var showImportConfirm by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    
    // Back Handler for sub-screens
    androidx.activity.compose.BackHandler(enabled = currentSubScreen != null) {
        currentSubScreen = null
    }

    // Timer Ringtone Picker
    val timerRingtoneLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            if (res.resultCode == android.app.Activity.RESULT_OK) {
                val uri =
                    res.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                viewModel.setTimerRingtone(uri?.toString())
            }
        }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        if (currentSubScreen == null) {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.desc_back)
                        )
                    }
                    Text(
                        stringResource(R.string.title_settings),
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
                HorizontalDivider()

                Column(Modifier.verticalScroll(scrollState).padding(16.dp)) {

                    // Presets
                    Text(
                        stringResource(R.string.section_presets),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))

                    val quickAdjustPresets by viewModel.quickAdjustPresets.collectAsState()
                    val timerPresets by viewModel.timerPresets.collectAsState()
                    var showAdjustEdit by remember { mutableStateOf(false) }
                    var showTimerEdit by remember { mutableStateOf(false) }
                    val context = LocalContext.current

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.label_quick_adjust_buttons)) },
                        supportingContent = {
                            Text(quickAdjustPresets.joinToString {
                                AlarmUtils.formatMinutes(
                                    context,
                                    it
                                )
                            })
                        },
                        modifier = Modifier.clickable { showAdjustEdit = true }
                    )

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.label_timer_presets)) },
                        supportingContent = {
                            Text(timerPresets.joinToString {
                                AlarmUtils.formatMinutes(
                                    context,
                                    it
                                )
                            })
                        },
                        modifier = Modifier.clickable { showTimerEdit = true }
                    )

                    HorizontalDivider(Modifier.padding(vertical = 16.dp))

                    // --- ALARM SETTINGS ---
                    Text(
                        stringResource(R.string.header_alarm_settings),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))

                    val notifyBeforeEnabled by viewModel.notifyBeforeEnabled.collectAsState()
                    val notifyBeforeMinutes by viewModel.notifyBeforeMinutes.collectAsState()

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.label_notify_before)) },
                        supportingContent = { Text(stringResource(R.string.desc_notify_before)) },
                        trailingContent = {
                            Switch(
                                checked = notifyBeforeEnabled,
                                onCheckedChange = { viewModel.setNotifyBeforeEnabled(it) }
                            )
                        }
                    )

                    AnimatedVisibility(visible = notifyBeforeEnabled) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.label_notification_time)) },
                            supportingContent = {
                                Text(
                                    AlarmUtils.formatMinutes(
                                        context,
                                        notifyBeforeMinutes
                                    )
                                )
                            },
                            modifier = Modifier.clickable { showNotifyBeforeDialog = true }
                        )
                    }

                    val ringingMode by viewModel.defaultRingingMode.collectAsState()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.label_ringing_mode)) },
                        supportingContent = {
                            val txt = when (ringingMode) {
                                RingingScreenMode.EASY -> stringResource(R.string.mode_easy)
                                RingingScreenMode.CLEAN -> stringResource(R.string.mode_clean)
                                else -> stringResource(R.string.mode_clean)
                            }
                            Text(txt)
                        },
                        modifier = Modifier.clickable {
                            val next =
                                if (ringingMode == RingingScreenMode.CLEAN) RingingScreenMode.EASY else RingingScreenMode.CLEAN
                            viewModel.setDefaultRingingMode(next)
                        }
                    )

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.title_default_alarm_settings)) },
                        supportingContent = { Text(stringResource(R.string.desc_default_alarm_settings)) },
                        modifier = Modifier.clickable { currentSubScreen = "DEFAULT_ALARM" }
                    )

                    HorizontalDivider(Modifier.padding(vertical = 16.dp))

                    // --- TIMER SETTINGS ---
                    Text(
                        stringResource(R.string.header_timer_settings),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))

                    // Ringtone
                    val ringtoneTitle = remember(timerRingtone) {
                        RingtoneUtils.getRingtoneTitle(
                            context,
                            timerRingtone
                        )
                    }
                    val selectToneTitle = stringResource(R.string.title_select_tone)
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.label_timer_ringtone)) },
                        supportingContent = { Text(ringtoneTitle) },
                        modifier = Modifier.clickable {
                            val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_TYPE,
                                    RingtoneManager.TYPE_ALARM
                                )
                                val existing =
                                    if (timerRingtone != null) Uri.parse(timerRingtone) else null
                                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
                                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, selectToneTitle)
                            }
                            timerRingtoneLauncher.launch(i)
                        }
                    )

                    // Vibration

                    val timerVibration by viewModel.timerVibration.collectAsState()
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.label_vibration)) },
                        trailingContent = {
                            Switch(
                                checked = timerVibration,
                                onCheckedChange = { isChecked ->
                                    viewModel.setTimerVibration(isChecked)
                                }
                            )
                        }
                    )


                    // Auto-Stop
                    val timerAutoStop by viewModel.defaultTimerAutoStop.collectAsState()
                    var showTimerTimeoutDialog by remember { mutableStateOf(false) }

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.label_default_timeout)) },
                        supportingContent = {
                            Text(
                                stringResource(
                                    R.string.label_minutes_fmt,
                                    timerAutoStop
                                )
                            )
                        },
                        modifier = Modifier.clickable { showTimerTimeoutDialog = true }
                    )

                    val timerAdjustPresets by viewModel.timerAdjustPresets.collectAsState()
                    var showAdjustPresetsEdit by remember { mutableStateOf(false) }
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.label_timer_adjust_presets)) },
                        supportingContent = { Text(timerAdjustPresets.joinToString { "+${it / 60}m" }) },
                        modifier = Modifier.clickable { showAdjustPresetsEdit = true }
                    )

                    // Volume
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.label_timer_volume),
                                modifier = Modifier.padding(start = 16.dp)
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${(timerVolume * 100).toInt()}%",
                                modifier = Modifier.padding(end = 16.dp)
                            )
                        }
                        Slider(
                            value = timerVolume,
                            onValueChange = { viewModel.setTimerVolume(it) },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    // TTS
                    var tempTtsText by remember(timerTtsText) { mutableStateOf(timerTtsText) }
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.label_timer_tts)) },
                        trailingContent = {
                            Switch(
                                checked = timerTtsEnabled,
                                onCheckedChange = { viewModel.setTimerTts(it, tempTtsText) }
                            )
                        }
                    )
                    AnimatedVisibility(visible = timerTtsEnabled) {
                        OutlinedTextField(
                            value = tempTtsText,
                            onValueChange = {
                                tempTtsText = it
                                viewModel.setTimerTts(true, it)
                            },
                            label = { Text(stringResource(R.string.label_timer_tts_text)) },
                            placeholder = { Text(stringResource(R.string.hint_timer_tts_text)) },
                            modifier = Modifier.fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    HorizontalDivider(Modifier.padding(vertical = 16.dp))

                    // --- Appearance ---

                    Text(
                        stringResource(R.string.header_appearance),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))

                    // Logic:
                    // Dark Mode Switch:
                    // - On: Sets theme to DARK (or preserves BLACK if it was already BLACK).
                    // - Off: Sets theme to LIGHT.
                    // Black Mode Switch:
                    // - On: Sets theme to BLACK.
                    // - Off: Sets theme to DARK.

                    val themeMode by viewModel.themeMode.collectAsState()
                    val isPureBlack by viewModel.isPureBlack.collectAsState()
                    val isSystem = themeMode == AppThemeMode.SYSTEM
                    val isDark = themeMode == AppThemeMode.DARK

                    // Effective Dark State for visual feedback
                    val effectivelyInDark = when (themeMode) {
                        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
                        AppThemeMode.DARK -> true
                        else -> false
                    }

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.label_follow_system)) },
                        trailingContent = {
                            Switch(
                                checked = isSystem,
                                onCheckedChange = { checked ->
                                    if (checked) viewModel.setThemeMode(AppThemeMode.SYSTEM)
                                    else viewModel.setThemeMode(AppThemeMode.LIGHT)
                                }
                            )
                        }
                    )

                    if (!isSystem) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.label_dark_mode)) },
                            trailingContent = {
                                Switch(
                                    checked = isDark,
                                    onCheckedChange = { checked ->
                                        viewModel.setThemeMode(if (checked) AppThemeMode.DARK else AppThemeMode.LIGHT)
                                    }
                                )
                            }
                        )
                    }

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.label_pure_black)) },
                        supportingContent = { Text(stringResource(R.string.settings_black_mode_subtext)) },
                        trailingContent = {
                            Switch(
                                checked = isPureBlack,
                                onCheckedChange = { viewModel.setPureBlack(it) }
                            )
                        }
                    )

                    HorizontalDivider(Modifier.padding(vertical = 16.dp))

                    // --- SYSTEM ---
                    Text(
                        stringResource(R.string.settings_section_advanced),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.setting_view_logs)) },
                        supportingContent = { Text(stringResource(R.string.setting_view_logs_subtext)) },
                        modifier = Modifier.clickable { currentSubScreen = "LOG_VIEWER" }
                    )

                    HorizontalDivider(Modifier.padding(vertical = 16.dp))

                    // --- BACKUP ---
                    Text(
                        stringResource(R.string.settings_section_backup),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))

                    val exportSuccessText = stringResource(R.string.export_successful)
                    val exportFailedText = stringResource(R.string.export_failed)
                    val exportLauncher =
                        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
                            uri?.let {
                                val outputStream = context.contentResolver.openOutputStream(it)
                                if (outputStream != null) {
                                    viewModel.viewModelScope.launch {
                                        val success = viewModel.exportBackup(outputStream)
                                        if (success) {
                                            android.widget.Toast.makeText(
                                                context,
                                                exportSuccessText,
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            android.widget.Toast.makeText(
                                                context,
                                                exportFailedText,
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            }
                        }

                    val importLauncher =
                        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                            uri?.let {
                                pendingImportUri = it
                                showImportConfirm = true
                            }
                        }

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.setting_export)) },
                        supportingContent = { Text(stringResource(R.string.setting_export_subtext)) },
                        modifier = Modifier.clickable {
                            exportLauncher.launch("openalarm_backup_${System.currentTimeMillis()}.json")
                        }
                    )

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.setting_import)) },
                        supportingContent = { Text(stringResource(R.string.setting_import_subtext)) },
                        modifier = Modifier.clickable {
                            importLauncher.launch("application/json")
                        }
                    )

                    HorizontalDivider(Modifier.padding(vertical = 16.dp))

                    // --- About/Credits ---
                    Text(
                        stringResource(R.string.section_about),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_headline_libraries)) },
                        supportingContent = { Text(stringResource(R.string.settings_supporting_libraries)) },
                        modifier = Modifier.clickable {
                            currentSubScreen = "OPEN_SOURCE_LIBRARIES"
                        }
                    )

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_headline_license)) },
                        supportingContent = { Text(stringResource(R.string.settings_supporting_license)) },
                        modifier = Modifier.clickable {
                            currentSubScreen = "LICENSE"
                        } // TODO

                    )

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.settings_headline_author)) },
                        supportingContent = { Text(stringResource(R.string.settings_supporting_author)) },
                    )

                    // --- DIALOGS ---
                    if (showAdjustEdit) {
                        PresetEditDialog(
                            title = stringResource(R.string.title_edit_adjust_presets),
                            currentValues = quickAdjustPresets,
                            onDismiss = { showAdjustEdit = false },
                            onConfirm = {
                                viewModel.setQuickAdjustPresets(it)
                                showAdjustEdit = false
                            }
                        )
                    }
                    if (showTimerEdit) {
                        PresetEditDialog(
                            title = stringResource(R.string.title_edit_timer_presets),
                            currentValues = timerPresets,
                            onDismiss = { showTimerEdit = false },
                            onConfirm = {
                                viewModel.setTimerPresets(it)
                                showTimerEdit = false
                            }
                        )
                    }
                    if (showTimerTimeoutDialog) {
                        NumpadInputDialog(
                            title = stringResource(R.string.title_timer_auto_stop),
                            initialValue = timerAutoStop,
                            onDismiss = { showTimerTimeoutDialog = false },
                            onConfirm = {
                                viewModel.setDefaultTimerAutoStop(it); showTimerTimeoutDialog =
                                false
                            }
                        )
                    }
                    if (showNotifyBeforeDialog) {
                        NumpadInputDialog(
                            title = stringResource(R.string.title_notify_before),
                            initialValue = notifyBeforeMinutes,
                            onDismiss = { showNotifyBeforeDialog = false },
                            onConfirm = {
                                viewModel.setNotifyBeforeMinutes(it); showNotifyBeforeDialog = false
                            }
                        )
                    }
                    if (showAdjustPresetsEdit) {
                        PresetEditDialog(
                            title = stringResource(R.string.label_timer_adjust_presets),
                            currentValues = timerAdjustPresets.map { it / 60 },
                            onDismiss = { showAdjustPresetsEdit = false },
                            onConfirm = {
                                viewModel.setTimerAdjustPresets(it.map { it * 60 })
                                showAdjustPresetsEdit = false
                            }
                        )
                    }

                    if (showImportConfirm) {
                        ImportConfirmationDialog(
                            onDismiss = {
                                showImportConfirm = false
                                pendingImportUri = null
                            },
                            onConfirm = {
                                showImportConfirm = false
                                pendingImportUri?.let { uri ->
                                    val inputStream = context.contentResolver.openInputStream(uri)
                                    if (inputStream != null) {
                                        viewModel.viewModelScope.launch {
                                            val success = viewModel.importBackup(inputStream)
                                            if (success) {
                                                android.widget.Toast.makeText(
                                                    context,
                                                    R.string.toast_backup_import_success,
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            } else {
                                                android.widget.Toast.makeText(
                                                    context,
                                                    R.string.toast_backup_import_failed,
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                            pendingImportUri = null
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        } else if (currentSubScreen == "DEFAULT_ALARM") {
            DefaultAlarmSettingsScreen(
                viewModel = viewModel,
                onBack = { currentSubScreen = null }
            )
        } else if (currentSubScreen == "OPEN_SOURCE_LIBRARIES") {
            AboutScreen(
                onBack = { currentSubScreen = null }
            )
        } else if (currentSubScreen == "LICENSE") {
            LicenseScreen(
                onBack = { currentSubScreen = null }
            )
        } else if (currentSubScreen == "LOG_VIEWER") {
            Dialog(
                onDismissRequest = { currentSubScreen = null },
                properties = DialogProperties(usePlatformDefaultWidth = false),
                content = {
                    LogViewerScreen(onBack = { currentSubScreen = null })
                }
            )
        }
    }
}

@Composable
fun NumpadInputDialog(
    title: String,
    initialValue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var buffer by remember { mutableStateOf(initialValue.toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))

                Text(
                    text = AlarmUtils.formatMinutes(LocalContext.current, buffer.toIntOrNull() ?: 0),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(24.dp))

                IntegratedNumpad(
                    onInput = { if (buffer.length < 3) buffer += it },
                    onDelete = { if (buffer.isNotEmpty()) buffer = buffer.dropLast(1) },
                    onConfirm = { onConfirm(buffer.toIntOrNull() ?: 10) },
                    onCancel = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun PresetEditDialog(
    title: String,
    currentValues: List<Int>,
    onDismiss: () -> Unit,
    onConfirm: (List<Int>) -> Unit
) {
    val count = currentValues.size.coerceAtLeast(1)
    val states = remember(currentValues) { 
        currentValues.map { mutableStateOf(it.toString()) } 
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                states.forEachIndexed { index, state ->
                    OutlinedTextField(
                        value = state.value,
                        onValueChange = { if (it.all { c -> c.isDigit() }) state.value = it },
                        label = { Text(stringResource(R.string.label_button_n, index + 1)) },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val newValues = states.mapNotNull { it.value.toIntOrNull() }
                if (newValues.size == count) onConfirm(newValues)
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
fun ImportConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var acknowledged by remember { mutableStateOf(false) }
    var timeLeft by remember { mutableStateOf(3) }
    val isTimerRunning = timeLeft > 0

    LaunchedEffect(Unit) {
        while (timeLeft > 0) {
            delay(1000)
            timeLeft--
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_careful)) },
        text = {
            Column {
                Text(stringResource(R.string.dialog_msg_import_warning))
                Spacer(Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { acknowledged = !acknowledged }
                ) {
                    Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it })
                    Text(stringResource(R.string.label_understand_data_loss))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = acknowledged && !isTimerRunning,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                if (isTimerRunning) {
                    Text(stringResource(R.string.label_wait_seconds, timeLeft))
                } else {
                    Text(stringResource(R.string.action_delete_and_import))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@SuppressLint("LocalContextResourcesRead")
@Composable
fun LicenseScreen(
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.desc_back))
            }
            Text(stringResource(R.string.title_license_settings), style = MaterialTheme.typography.headlineMedium)
        }

        val context = LocalContext.current
        val scrollState = rememberScrollState()
        HorizontalDivider()
        val text = context.resources
            .openRawResource(R.raw.license)
            .bufferedReader()
            .readLines()
            .joinToString("\n") { it.trimStart()}
        Text(
            text,
            modifier = Modifier
                .verticalScroll(scrollState)
                .fillMaxSize()
                .padding(16.dp),
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun AboutScreen(
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.desc_back))
            }
            Text(stringResource(R.string.title_open_source_libraries_settings), style = MaterialTheme.typography.headlineMedium)
        }

        HorizontalDivider()

        val libraries by produceLibraries(R.raw.aboutlibraries)

        LibrariesContainer(
            libraries = libraries,
            modifier = Modifier.fillMaxSize()
        )
    }
}