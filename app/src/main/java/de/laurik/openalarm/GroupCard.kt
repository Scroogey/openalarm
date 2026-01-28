package de.laurik.openalarm

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource

@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    group: AlarmGroup,
    onToggleGroup: (Boolean) -> Unit,
    onAdjust: () -> Unit,
    onEdit: () -> Unit,
    onSkipNextAll: () -> Unit,
    onClearSkipAll: () -> Unit,
    onSkipUntilAll: () -> Unit,
    onDelete: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val anyEnabled = group.alarms.any { it.isEnabled }
    val context = LocalContext.current
    var ticker by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            ticker = System.currentTimeMillis()
        }
    }

    // Colors
    val isSystemDark = isSystemInDarkTheme()
    val baseColor = Color(group.colorArgb)
    val isDefaultColor = group.colorArgb == -1

    // New Logic:
    // Light Mode: Pastel filled background (mix with white)
    // Dark/Black Mode: Surface background with colored outline
    val useOutline = isSystemDark && !isDefaultColor

    val cardColor = if (isDefaultColor) {
        MaterialTheme.colorScheme.surfaceVariant
    } else if (useOutline) {
        // Dark mode: Use surface color, will apply border separately
        MaterialTheme.colorScheme.surface
    } else {
        // Light mode: Pastel tint
        Color(ColorUtils.blendARGB(baseColor.toArgb(), Color.White.toArgb(), 0.3f))
    }

    val contentColor = MaterialTheme.colorScheme.onSurface

    // Arrow rotation
    val rotation by animateFloatAsState(targetValue = if (group.isExpanded) 180f else 0f, label = "arrow")

    val emptyListText = stringResource(R.string.alarmlist_empty)
    val nextTimeTemplate = stringResource(R.string.next_time_group)

    // Reactively calculate the summary and next ringing time
    val groupSummary = remember(group.alarms.size, anyEnabled, group.offsetMinutes, group.skippedUntil, ticker) {
        val nextTimes = group.alarms.filter { it.isEnabled }.map { alarm ->
            val minTime = maxOf(ticker, alarm.skippedUntil, group.skippedUntil)
            AlarmUtils.getNextOccurrence(
                alarm.hour, alarm.minute, alarm.daysOfWeek, group.offsetMinutes,
                alarm.temporaryOverrideTime, alarm.snoozeUntil,
                minTime
            )
        }

        val nextTime = nextTimes.minOrNull()
        val nextTimeStr = nextTime?.let {
            if (it > 0) {
                val timeUntil = AlarmUtils.getTimeUntilString(context, it, ticker)
                String.format(nextTimeTemplate, timeUntil)
            } else null
        }

        val alarmList = if (group.alarms.isEmpty()) emptyListText
        else group.alarms.sortedBy { it.hour * 60 + it.minute }
            .joinToString(", ") { String.format("%02d:%02d", it.hour, it.minute) }

        if (nextTimeStr != null) "$nextTimeStr ($alarmList)" else alarmList
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .animateContentSize()
            .then(
                if (useOutline) Modifier.border(
                    width = 2.dp,
                    color = baseColor,
                    shape = RoundedCornerShape(16.dp)
                ) else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column {
            // HEADER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { group.isExpanded = !group.isExpanded },
                        onLongClick = { onEdit() }
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Group Name & Summary
                Column(modifier = Modifier.weight(1f)) {
                    Text(group.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = contentColor)
                    if (!group.isExpanded) {
                        Text(groupSummary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = contentColor.copy(alpha = 0.7f))
                    }
                }



                // Skip Icon
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.AlarmOff,
                        contentDescription = stringResource(R.string.menu_skip_next), // Reuse existing string for content description
                        tint = if (group.skippedUntil > ticker || group.alarms.any { it.isEnabled && it.skippedUntil > ticker }) 
                            MaterialTheme.colorScheme.error 
                        else contentColor
                    )
                    
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        val anySkipped = group.alarms.any { it.isEnabled && it.skippedUntil > ticker } || group.skippedUntil > ticker
                        
                        if (anySkipped) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_clear_skip)) },
                                onClick = { showMenu = false; onClearSkipAll() }
                            )
                        } else {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_skip_next)) },
                                onClick = { showMenu = false; onSkipNextAll() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_skip_until)) },
                            onClick = { showMenu = false; onSkipUntilAll() }
                        )
                    }
                }

                // Switch
                Switch(
                    checked = anyEnabled,
                    onCheckedChange = onToggleGroup,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = contentColor,
                        checkedTrackColor = contentColor.copy(alpha=0.4f),
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.LightGray.copy(alpha=0.4f)
                    ),
                    modifier = Modifier.scale(0.8f)
                )


                // Settings - directly opens edit dialog
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Settings, stringResource(R.string.edit_group), tint = contentColor)
                }

                // Expand Arrow
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.desc_expand),
                    tint = contentColor,
                    modifier = Modifier.rotate(rotation)
                )
            }

            // Adjust Control Row (Visible only when expanded)
            if (group.isExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    OutlinedButton(
                        onClick = onAdjust,
                        border = BorderStroke(2.dp, contentColor.copy(alpha = 0.5f))
                    ) {
                        Text(
                            stringResource(R.string.adjust_group_time),
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = 12.sp,
                            color = contentColor
                        )
                    }
                }
            }

            // EXPANDED CONTENT
            if (group.isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 8.dp)
                ) {
                    content()
                }
            }
        }
    }
}