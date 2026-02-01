package de.laurik.openalarm

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter



@Composable
fun QuickAdjustDialog(
    quickAdjustPresets: List<Int> = listOf(10, 30, 60),
    overrideTitle: String? = null,
    currentDisplay: String? = null,
    hasActiveOverride: Boolean = false,
    currentNextTime: Long = 0L, // Optional, used for validation
    onDismiss: () -> Unit,
    onAdjust: (Int) -> Unit,
    onReset: () -> Unit
) {
    var isCustomMode by remember { mutableStateOf(false) }
    
    // Custom Input State
    var inputString by remember { mutableStateOf("") }
    var isDelay by remember { mutableStateOf(true) } // true = Delay (+), false = Earlier (-)

    // Validate Input
    val inputMinutes = remember(inputString) {
        if (inputString.isEmpty()) 0
        else {
            val raw = inputString.toLongOrNull() ?: 0L
            val mins = raw % 100
            val hours = raw / 100
            (hours * 60 + mins).toInt()
        }
    }
    
    val duration0s = stringResource(R.string.fmt_duration_0s)
    val context = LocalContext.current
    // Formatting: 105 -> 1h 05m
    val formattedInput = remember(inputMinutes, context, duration0s) {
        if (inputMinutes == 0) duration0s
        else AlarmUtils.formatMinutes(context, inputMinutes)
    }

    val isValid = remember(inputMinutes, isDelay, currentNextTime) {
        if (inputMinutes == 0) false
        else if (inputMinutes > 6 * 60) false // Max 6h constraint
        else if (currentNextTime > 0) {
            val delta = if (isDelay) inputMinutes else -inputMinutes
            // Check if result is in future (allow small buffer)
            val target = currentNextTime + (delta * 60 * 1000L)
            target > System.currentTimeMillis()
        } else true // No base time validation possible (e.g. group)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp), 
            color = MaterialTheme.colorScheme.surface, 
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = overrideTitle ?: stringResource(R.string.title_adjust_next), 
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(Modifier.height(16.dp))

                // TARGET TIME DISPLAY
                val targetDisplay = remember(currentNextTime) {
                    if (currentNextTime <= 0) {
                        "00:00" // Fallback if no time provided
                    } else {
                        Instant.ofEpochMilli(currentNextTime)
                            .atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("HH:mm"))
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = targetDisplay, 
                        style = MaterialTheme.typography.displayMedium, 
                        fontWeight = FontWeight.Bold, 
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    // Show delta if in custom mode or if we have an override
                    if (isCustomMode || hasActiveOverride) {
                        Text(
                            text = if (isCustomMode) {
                                (if (isDelay) "+" else "-") + formattedInput
                            } else {
                                stringResource(R.string.label_active_override)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isDelay || !isCustomMode) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary
                        )
                    } else {
                        // Placeholder to maintain layout stability
                        Text("", style = MaterialTheme.typography.titleMedium)
                    }
                }

                Spacer(Modifier.height(24.dp))
                
                if (!isCustomMode) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.label_delay), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            quickAdjustPresets.forEach { m ->
                                AdjustButton("+${AlarmUtils.formatMinutes(context, m)}") { onAdjust(m) }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Text(stringResource(R.string.label_earlier), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            quickAdjustPresets.forEach { m ->
                                AdjustButton("-${AlarmUtils.formatMinutes(context, m)}") { onAdjust(-m) }
                            }
                        }

                        Spacer(Modifier.height(32.dp))
                        
                        // Custom & Reset
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            if (hasActiveOverride) {
                                TextButton(onClick = onReset, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                                    Text(stringResource(R.string.action_reset))
                                }
                            } else {
                                Spacer(Modifier.width(8.dp))
                            }
                            
                            FilledTonalButton(onClick = { isCustomMode = true }) {
                                Text(stringResource(R.string.action_custom_time))
                            }
                        }
                    }
                } else {
                    // CUSTOM INPUT MODE
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Earlier/Delay Toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically, 
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Text(stringResource(R.string.label_earlier), style = MaterialTheme.typography.labelSmall)
                            Switch(
                                checked = isDelay,
                                onCheckedChange = { isDelay = it },
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Text(stringResource(R.string.label_delay), style = MaterialTheme.typography.labelSmall)
                        }

                        IntegratedNumpad(
                            onInput = { digit -> 
                                if (inputString.length < 4) inputString += digit 
                            },
                            onDelete = { 
                                if (inputString.isNotEmpty()) inputString = inputString.dropLast(1) 
                            },
                            onConfirm = {
                                if (isValid) {
                                    val delta = if (isDelay) inputMinutes else -inputMinutes
                                    onAdjust(delta)
                                }
                            },
                            onCancel = { isCustomMode = false }, // Go back to presets
                            modifier = Modifier.fillMaxWidth().height(280.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AdjustButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        modifier = Modifier.width(80.dp) // Fixed width for alignment
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}