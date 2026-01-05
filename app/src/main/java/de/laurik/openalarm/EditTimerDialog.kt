package de.laurik.openalarm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.res.stringResource

@Composable
fun EditTimerDialog(timerPresets: List<Int> = listOf(10, 15, 30), onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var hour by remember { mutableIntStateOf(0) }
    var minute by remember { mutableIntStateOf(15) }
    var second by remember { mutableIntStateOf(0) }

    var snapNext by remember { mutableStateOf(true) }
    // NEW: Trigger state
    var updateTrigger by remember { mutableLongStateOf(0L) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Surface(modifier = Modifier.fillMaxWidth().clickable(enabled = false) {}, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp), color = MaterialTheme.colorScheme.surface) {
                SmartTimePickerLayout(
                    hour = hour, minute = minute, seconds = second,
                    updateTrigger = updateTrigger, // Pass trigger
                    snapImmediately = snapNext,
                    onTimeChange = { h, m, s -> hour = h; minute = m; second = s; snapNext = true }
                ) { wheelContent, numpadContent ->

                    Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.width(40.dp).height(4.dp).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(2.dp)))
                        Spacer(Modifier.height(24.dp))
                        Text(stringResource(R.string.title_new_timer), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(16.dp))

                        wheelContent()
                        Spacer(Modifier.height(24.dp))

                        if (numpadContent == null) {
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                timerPresets.forEach { m ->
                                    OutlinedButton(
                                        onClick = {
                                            snapNext = false // Enable Animation
                                            hour = 0; minute = m; second = 0
                                            updateTrigger++ // FIRE COMMAND
                                        },
                                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                                    ) { Text(stringResource(R.string.action_add_minutes, m), fontSize = 16.sp, color = MaterialTheme.colorScheme.primary) }
                                }
                            }
                            Spacer(Modifier.height(32.dp))
                        }

                        if (numpadContent != null) numpadContent()
                        else {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                                Button(onClick = { onConfirm((hour * 3600) + (minute * 60) + second) }, modifier = Modifier.fillMaxWidth(0.5f)) { Text(stringResource(R.string.action_start), fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
            }
        }
    }
}