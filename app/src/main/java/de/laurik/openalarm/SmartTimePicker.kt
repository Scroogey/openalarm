package de.laurik.openalarm

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SmartTimePickerLayout(
    hour: Int, minute: Int, seconds: Int? = null,
    updateTrigger: Long = 0L, // NEW COMMAND KEY
    snapImmediately: Boolean,
    onTimeChange: (Int, Int, Int) -> Unit,
    content: @Composable (
        wheelContent: @Composable () -> Unit,
        numpadContent: @Composable (() -> Unit)?
    ) -> Unit
) {
    var editingColumn by remember { mutableStateOf(TimeColumn.NONE) }
    var inputBuffer by remember { mutableStateOf("") }
    
    BackHandler(enabled = editingColumn != TimeColumn.NONE) {
        editingColumn = TimeColumn.NONE
        inputBuffer = ""
    }

    // Helper to find next column for auto-advance
    fun advanceColumn() {
        val next = when (editingColumn) {
            TimeColumn.HOUR -> TimeColumn.MINUTE
            TimeColumn.MINUTE -> if (seconds != null) TimeColumn.SECOND else TimeColumn.NONE
            TimeColumn.SECOND -> TimeColumn.NONE
            else -> TimeColumn.NONE
        }
        editingColumn = next
        inputBuffer = ""
    }

    // Input Logic (Same as before)
    fun applyInput(key: String) {
        val maxDigit1 = if (editingColumn == TimeColumn.HOUR) 2 else 5
        val maxTotal = if (editingColumn == TimeColumn.HOUR) 23 else 59

        val inputStr = if (key == "00") "00" else key

        // Smart Advance (Single Digit)
        if (inputBuffer.isEmpty() && key != "00") {
            val num = inputStr.toIntOrNull() ?: 0
            if (num > maxDigit1) {
                when (editingColumn) {
                    TimeColumn.HOUR -> onTimeChange(num, minute, seconds ?: 0)
                    TimeColumn.MINUTE -> onTimeChange(hour, num, seconds ?: 0)
                    TimeColumn.SECOND -> onTimeChange(hour, minute, num)
                    else -> {}
                }
                advanceColumn()
                return
            }
        }

        // Standard Buffer
        val newBuffer = (inputBuffer + inputStr).take(2)
        inputBuffer = newBuffer
        val parsedNum = newBuffer.toIntOrNull() ?: 0

        when (editingColumn) {
            TimeColumn.HOUR -> onTimeChange(parsedNum.coerceIn(0, maxTotal), minute, seconds ?: 0)
            TimeColumn.MINUTE -> onTimeChange(hour, parsedNum.coerceIn(0, maxTotal), seconds ?: 0)
            TimeColumn.SECOND -> onTimeChange(hour, minute, parsedNum.coerceIn(0, maxTotal))
            else -> {}
        }

        if (newBuffer.length == 2) advanceColumn()
    }

    fun applyDelete() {
        if (inputBuffer.isNotEmpty()) {
            inputBuffer = inputBuffer.dropLast(1)
            val num = inputBuffer.toIntOrNull() ?: 0
            when (editingColumn) {
                TimeColumn.HOUR -> onTimeChange(num, minute, seconds ?: 0)
                TimeColumn.MINUTE -> onTimeChange(hour, num, seconds ?: 0)
                TimeColumn.SECOND -> onTimeChange(hour, minute, num)
                else -> {}
            }
        } else {
            when (editingColumn) {
                TimeColumn.HOUR -> onTimeChange(0, minute, seconds ?: 0)
                TimeColumn.MINUTE -> onTimeChange(hour, 0, seconds ?: 0)
                TimeColumn.SECOND -> onTimeChange(hour, minute, 0)
                else -> {}
            }
        }
    }

    val wheels = @Composable {
        WheelTimePicker(
            hour = hour, minute = minute, seconds = seconds,
            activeColumn = editingColumn,
            inputBuffer = inputBuffer,
            // Trigger update when Parent says so (Presets) OR when Numpad changes column
            updateTrigger = updateTrigger + editingColumn.ordinal,
            snapImmediately = editingColumn != TimeColumn.NONE || snapImmediately,
            onTimeChange = { h, m, s -> if (editingColumn == TimeColumn.NONE) onTimeChange(h, m, s) },
            onColumnClick = { col ->
                editingColumn = if (editingColumn == col) TimeColumn.NONE else col
                inputBuffer = ""
            }
        )
    }

    val numpad = if (editingColumn != TimeColumn.NONE) {
        @Composable {
            IntegratedNumpad(
                onInput = { applyInput(it) },
                onDelete = { applyDelete() },
                onConfirm = { editingColumn = TimeColumn.NONE; inputBuffer = "" },
                onCancel = { editingColumn = TimeColumn.NONE; inputBuffer = "" },
                modifier = Modifier.fillMaxWidth().height(280.dp)
            )
        }
    } else null

    content(wheels, numpad)
}