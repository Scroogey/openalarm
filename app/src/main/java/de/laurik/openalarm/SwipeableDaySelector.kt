package de.laurik.openalarm

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.floor

@Composable
fun SwipeableDaySelector(
    selectedDays: List<Int>,
    enabled: Boolean = true,
    onSelectionChanged: (List<Int>) -> Unit
) {
    // 1=Mon ... 7=Sun
    val daysData = listOf(
        Triple(1, stringResource(R.string.day_mon_short), stringResource(R.string.day_monday)),
        Triple(2, stringResource(R.string.day_tue_short), stringResource(R.string.day_tuesday)),
        Triple(3, stringResource(R.string.day_wed_short), stringResource(R.string.day_wednesday)),
        Triple(4, stringResource(R.string.day_thu_short), stringResource(R.string.day_thursday)),
        Triple(5, stringResource(R.string.day_fri_short), stringResource(R.string.day_friday)),
        Triple(6, stringResource(R.string.day_sat_short), stringResource(R.string.day_saturday)),
        Triple(7, stringResource(R.string.day_sun_short), stringResource(R.string.day_sunday))
    )

    // Layout State
    var componentWidth by remember { mutableFloatStateOf(0f) }

    // Drag State
    var isDragging by remember { mutableStateOf(false) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var currentDragIndex by remember { mutableIntStateOf(-1) }

    // Logic State
    var initialSelectionSnapshot by remember { mutableStateOf(emptyList<Int>()) }
    var isPaintSelecting by remember { mutableStateOf(true) } // true = turning ON, false = turning OFF

    val view = LocalView.current

    // CRITICAL FIX: Capture the latest state so the gesture detectors see updates
    val currentSelectedDays by rememberUpdatedState(selectedDays)
    val currentOnSelectionChanged by rememberUpdatedState(onSelectionChanged)

    /**
     * Logic to determine the new list based on drag range
     */
    fun calculatePaintSelection(start: Int, end: Int, snapshot: List<Int>, paintOn: Boolean): List<Int> {
        if (start == -1 || end == -1) return snapshot

        val rangeStart = minOf(start, end)
        val rangeEnd = maxOf(start, end)

        val result = snapshot.toMutableList()

        for (i in rangeStart..rangeEnd) {
            val dayIso = daysData[i].first
            if (paintOn) {
                if (!result.contains(dayIso)) result.add(dayIso)
            } else {
                result.remove(dayIso)
            }
        }
        return result.sorted()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .padding(horizontal = 16.dp)
            .onGloballyPositioned { componentWidth = it.size.width.toFloat() }
            // SPLIT GESTURES: Use separate pointerInputs to avoid interference,
            // but use the `current...` variables to access state.
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures(
                    onTap = { offset ->
                        if (componentWidth <= 0) return@detectTapGestures
                        val itemWidth = componentWidth / 7
                        val index = floor(offset.x / itemWidth).toInt().coerceIn(0, 6)
                        val dayIso = daysData[index].first

                        // USE LATEST STATE
                        val list = currentSelectedDays

                        val newList = if (list.contains(dayIso)) {
                            list - dayIso
                        } else {
                            list + dayIso
                        }
                        currentOnSelectionChanged(newList.sorted())
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    }
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        if (componentWidth <= 0) return@detectDragGestures
                        isDragging = true

                        val itemWidth = componentWidth / 7
                        dragStartIndex = floor(offset.x / itemWidth).toInt().coerceIn(0, 6)
                        currentDragIndex = dragStartIndex

                        // USE LATEST STATE FOR SNAPSHOT
                        initialSelectionSnapshot = currentSelectedDays

                        // Determine Mode (Paint ON vs Paint OFF) based on the first item touched
                        val startDayIso = daysData[dragStartIndex].first
                        isPaintSelecting = !initialSelectionSnapshot.contains(startDayIso)

                        // Apply immediately
                        val draft = calculatePaintSelection(
                            dragStartIndex,
                            currentDragIndex,
                            initialSelectionSnapshot,
                            isPaintSelecting
                        )
                        currentOnSelectionChanged(draft)

                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    },
                    onDrag = { change, _ ->
                        if (componentWidth <= 0) return@detectDragGestures
                        val itemWidth = componentWidth / 7
                        val newIndex = floor(change.position.x / itemWidth).toInt().coerceIn(0, 6)

                        if (newIndex != currentDragIndex) {
                            currentDragIndex = newIndex

                            val draft = calculatePaintSelection(
                                dragStartIndex,
                                currentDragIndex,
                                initialSelectionSnapshot,
                                isPaintSelecting
                            )
                            currentOnSelectionChanged(draft)

                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    },
                    onDragEnd = {
                        isDragging = false
                        dragStartIndex = -1
                        currentDragIndex = -1
                        // API 27+ VIRTUAL_KEY, else LONG_PRESS
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    },
                    onDragCancel = {
                        // Revert to snapshot
                        currentOnSelectionChanged(initialSelectionSnapshot)
                        isDragging = false
                        dragStartIndex = -1
                        currentDragIndex = -1
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            daysData.forEach { (isoDay, label, _) ->
                val isSelected = selectedDays.contains(isoDay)

                // Visuals
                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    } else Color(0xFFEEEEEE),
                    animationSpec = tween(300),
                    label = "color"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) {
                        if (enabled) Color.White else Color.White.copy(alpha = 0.6f)
                    } else {
                        if (enabled) Color.Black else Color.Black.copy(alpha = 0.3f)
                    },
                    animationSpec = tween(300),
                    label = "text"
                )
                val scale by animateFloatAsState(
                    targetValue = if (isSelected) 1.1f else 1.0f,
                    animationSpec = tween(200),
                    label = "scale"
                )

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(backgroundColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = textColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}