package de.laurik.openalarm

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.time.LocalTime

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AlarmCard(
    modifier: Modifier = Modifier,
    alarm: AlarmItem,
    groupOffset: Int,
    groupSkippedUntil: Long,
    isNextAlarm: Boolean,
    isSnoozed: Boolean,
    snoozeUntil: Long?,
    quickAdjustPresets: List<Int> = listOf(10, 30, 60),
    onClick: () -> Unit,
    onToggleGroup: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipUntil: () -> Unit,
    onClearSkip: () -> Unit,
    onAdjustTime: (Int) -> Unit,
    onResetTime: () -> Unit
) {
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    var showAdjustDialog by remember { mutableStateOf(false) }

    // --- 1. CALCULATION LOGIC ---
    val now = System.currentTimeMillis()

    // Status Flags
    val isSkipped = !isSnoozed && alarm.isEnabled && (alarm.skippedUntil > now || groupSkippedUntil > now)

    // Effective Min Time (If skipped, look further into future)
    val minTime = if (isSkipped) maxOf(now, alarm.skippedUntil, groupSkippedUntil) else now

    // The actual next ring time (Calculated)
    val (effectiveNext, normalNext, isEffectivelySkipped) = remember(alarm, groupOffset, minTime, alarm.temporaryOverrideTime, alarm.snoozeUntil, isSkipped) {
        val calculatedEffectiveNext = AlarmUtils.getNextOccurrence(
            alarm.hour, alarm.minute, alarm.daysOfWeek, groupOffset,
            alarm.temporaryOverrideTime, alarm.snoozeUntil,
            minTimestamp = minTime
        )

        val calculatedNormalNext = AlarmUtils.getNextOccurrence(
            alarm.hour, alarm.minute, alarm.daysOfWeek, groupOffset,
            alarm.temporaryOverrideTime, alarm.snoozeUntil,
            minTimestamp = now
        )

        val calculatedIsEffectivelySkipped = isSkipped && calculatedEffectiveNext > calculatedNormalNext
        Triple(calculatedEffectiveNext, calculatedNormalNext, calculatedIsEffectivelySkipped)
    }

    // --- 2. DISPLAY TIME LOGIC (Fixing the Visual Bugs) ---

    // A. Does a specific SINGLE override exist? (Use effectiveNext check to respect skip/minTime logic)
    val activeOverride = if (alarm.temporaryOverrideTime != null && alarm.temporaryOverrideTime == effectiveNext)
        alarm.temporaryOverrideTime
    else null

    // B. Base Time (Original HH:MM)
    val baseTime = LocalTime.of(alarm.hour, alarm.minute)

    val labelNext = stringResource(R.string.label_next)
    // C. Calculate the "Big Display" time
    val (displayTimeBig, displayTimeSmall, labelPrefix) = remember(activeOverride, groupOffset, baseTime, labelNext) {
        val formatter = DateTimeFormatter.ofPattern("HH:mm")

        if (activeOverride != null) {
            // CASE 1: Single Override Active (e.g. "Adjusted")
            val ot = java.time.Instant.ofEpochMilli(activeOverride).atZone(java.time.ZoneId.systemDefault()).toLocalTime()
            Triple(ot.format(formatter), "(${baseTime.format(formatter)})", labelNext)
        } else if (groupOffset != 0) {
            // CASE 2: Group Shift Active (e.g. -10m)
            val shifted = baseTime.plusMinutes(groupOffset.toLong())
            Triple(shifted.format(formatter), "(${baseTime.format(formatter)})", null)
        } else {
            // CASE 3: Standard
            Triple(baseTime.format(formatter), null, null)
        }
    }

    val repeatText = remember(alarm.daysOfWeek) { AlarmUtils.getRepeatText(context, alarm.daysOfWeek) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showMenu = true }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isNextAlarm || isSnoozed) MaterialTheme.colorScheme.primaryContainer 
            else if (alarm.isEnabled) MaterialTheme.colorScheme.surfaceContainerHigh 
            else MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(if (isNextAlarm || isSnoozed && alarm.isEnabled) 4.dp else if (alarm.isEnabled) 1.dp else 0.dp)
    ) {
        Box {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {

                // --- CLICKABLE TIME SECTION ---
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { if (alarm.isEnabled) showAdjustDialog = true }
                ) {
                    if (labelPrefix != null) {
                        Text(text = labelPrefix, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        text = displayTimeBig,
                        fontSize = 42.sp,
                        fontWeight = FontWeight.Light,
                        color = if (alarm.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (displayTimeSmall != null) {
                        Text(text = displayTimeSmall, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // --- INFO SECTION ---
                Column(modifier = Modifier.weight(1f)) {
                    if (alarm.label.isNotEmpty()) {
                        Text(alarm.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = if (alarm.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    if (repeatText != null) {
                        Text(repeatText, style = MaterialTheme.typography.bodySmall, color = if (alarm.isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            val days = listOf(
                                stringResource(R.string.day_mon_short),
                                stringResource(R.string.day_tue_short),
                                stringResource(R.string.day_wed_short),
                                stringResource(R.string.day_thu_short),
                                stringResource(R.string.day_fri_short),
                                stringResource(R.string.day_sat_short),
                                stringResource(R.string.day_sun_short)
                            )
                            days.forEachIndexed { index, letter ->
                                DayCircle(letter, alarm.daysOfWeek.contains(index + 1), alarm.isEnabled)
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))

                    if (!alarm.isEnabled) {
                         Text(stringResource(R.string.status_disabled), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        AlarmStatusTicker(effectiveNext, isSnoozed, isEffectivelySkipped)
                    }
                }

                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.NotificationsOff,
                        contentDescription = stringResource(R.string.menu_skip_next),
                        tint = if (isSkipped) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(checked = alarm.isEnabled, onCheckedChange = onToggleGroup)
            }

            // --- MENU ---
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                if (isSkipped) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.menu_clear_skip)) }, onClick = { showMenu = false; onClearSkip() })
                } else {
                    DropdownMenuItem(text = { Text(stringResource(R.string.menu_skip_next)) }, onClick = { showMenu = false; onSkipNext() })
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.menu_skip_until)) }, onClick = { showMenu = false; onSkipUntil() })
                HorizontalDivider()
                DropdownMenuItem(text = { Text(stringResource(R.string.menu_remove), color = Color.Red) }, onClick = { showMenu = false; onDelete() })
            }
        }
    }

    if (showAdjustDialog) {
        QuickAdjustDialog(
            quickAdjustPresets = quickAdjustPresets,
            currentNextTime = effectiveNext,
            hasActiveOverride = activeOverride != null,
            onDismiss = { showAdjustDialog = false },
            onAdjust = { onAdjustTime(it); showAdjustDialog = false },
            onReset = { onResetTime(); showAdjustDialog = false }
        )
    }
}

@Composable
fun AlarmStatusTicker(effectiveNext: Long, isSnoozed: Boolean, isEffectivelySkipped: Boolean) {
    val context = LocalContext.current
    var trigger by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            trigger = System.currentTimeMillis()
        }
    }

    val statusText = remember(effectiveNext, isEffectivelySkipped, trigger) {
        if (isEffectivelySkipped) {
            AlarmUtils.formatSkippedUntil(context, effectiveNext)
        } else {
            AlarmUtils.getTimeUntilString(context, effectiveNext, trigger)
        }
    }

    if (isEffectivelySkipped) {
        Text(
            text = stringResource(R.string.status_skipped_until, statusText),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
        )
    } else {
        val txt = if (isSnoozed) stringResource(R.string.status_snoozed_format, statusText) else stringResource(R.string.status_ringing_in, statusText)
        Text(
            text = txt,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSnoozed) FontWeight.Bold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun DayCircle(letter: String, isActive: Boolean, alarmEnabled: Boolean) {
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f) // Dynamic gray
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f)

    val borderColor = if (isActive && alarmEnabled) activeColor else inactiveColor
    Box(
        modifier = Modifier.size(18.dp).border(1.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            letter, 
            fontSize = 10.sp, 
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal, 
            color = if (isActive && alarmEnabled) activeColor else textColor
        )
    }
}