package de.laurik.openalarm

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun RingingBackground(
    type: String,
    value: String,
    content: @Composable BoxScope.() -> Unit
) {
    fun String.toParsedColor(): Color? {
        val trimmed = this.trim()
        if (trimmed.isEmpty()) return null
        val formatted = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
        return runCatching { Color(android.graphics.Color.parseColor(formatted)) }.getOrNull()
    }

    val modifier = when (type) {
        "GRADIENT" -> {
            val colors = value.split(",").mapNotNull { it.toParsedColor() }
            if (colors.size >= 2) {
                Modifier.background(Brush.verticalGradient(colors))
            } else {
                Modifier.background(Color.Black)
            }
        }
        "COLOR" -> {
            val color = value.toParsedColor() ?: Color.Black
            Modifier.background(color)
        }
        else -> Modifier.background(Color.Black)
    }

    Box(modifier.fillMaxSize(), content = content)
}

@Composable
fun EasyModeLayout(
    onSnooze: () -> Unit,
    onStop: () -> Unit,
    snoozePresets: List<Int>? = null
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        // Stop Button (Huge)
        Button(
            onClick = onStop,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
            modifier = Modifier.fillMaxWidth().height(100.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Text(stringResource(R.string.action_stop_caps), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        
        Spacer(Modifier.height(16.dp))

        // Snooze Row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val presets = snoozePresets ?: listOf(5, 10, 15)
            presets.take(3).forEach { mins ->
                Button(
                    onClick = { /* trigger specific snooze */ onSnooze() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                    modifier = Modifier.weight(1f).height(70.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("$mins min", fontSize = 16.sp, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun CleanModeJoystick(
    onSnoozeTrigger: () -> Unit,
    onStopTrigger: () -> Unit
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        val widthPx = constraints.maxWidth.toFloat()
        val paddingHorizontalPx = with(LocalDensity.current) { 48.dp.toPx() }
        val iconSizePx = with(LocalDensity.current) { 48.dp.toPx() }
        
        // Target points relative to center
        val centerToIconDist = (widthPx / 2f) - paddingHorizontalPx - (iconSizePx / 2f)
        
        val snapThreshold = centerToIconDist * 0.7f 
        val verticalLimit = 60f 

        val isSnappedLeft = offsetX < -snapThreshold
        val isSnappedRight = offsetX > snapThreshold

        val animatedX by animateFloatAsState(
            targetValue = if (isSnappedLeft) -centerToIconDist else if (isSnappedRight) centerToIconDist else offsetX,
            animationSpec = spring(stiffness = if (offsetX == 0f) Spring.StiffnessLow else Spring.StiffnessMedium)
        )
        val animatedY by animateFloatAsState(
            targetValue = offsetY,
            animationSpec = spring(stiffness = Spring.StiffnessLow)
        )

        // Joystick Color Interpolation
        val joystickColor = remember(offsetX, isSnappedLeft, isSnappedRight) {
            val progress = (offsetX / centerToIconDist).coerceIn(-1f, 1f)
            if (progress < 0) {
                lerp(Color.White, Color(0xFF2196F3), -progress)
            } else {
                lerp(Color.White, Color(0xFFF44336), progress)
            }
        }

        val joystickSize by animateDpAsState(if (isSnappedLeft || isSnappedRight) 140.dp else 120.dp)
        val innerSize by animateDpAsState(if (isSnappedLeft || isSnappedRight) 96.dp else 80.dp)

        // Guides
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp)
                .padding(bottom = 140.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Notifications,
                null,
                tint = Color(0xFF2196F3).copy(alpha = if (isSnappedLeft) 1f else 0.4f),
                modifier = Modifier.size(if (isSnappedLeft) 56.dp else 48.dp)
            )
            Icon(
                Icons.Default.Close,
                null,
                tint = Color(0xFFF44336).copy(alpha = if (isSnappedRight) 1f else 0.4f),
                modifier = Modifier.size(if (isSnappedRight) 56.dp else 48.dp)
            )
        }

        // Joystick
        Box(
            modifier = Modifier
                .padding(bottom = 104.dp)
                .offset { IntOffset(animatedX.roundToInt(), animatedY.roundToInt()) }
                .size(joystickSize)
                .clip(CircleShape)
                .background(joystickColor.copy(alpha = 0.2f))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY = (offsetY + dragAmount.y).coerceIn(-verticalLimit, verticalLimit)
                        },
                        onDragEnd = {
                            if (offsetX < -snapThreshold) onSnoozeTrigger()
                            else if (offsetX > snapThreshold) onStopTrigger()
                            
                            offsetX = 0f
                            offsetY = 0f
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.size(innerSize),
                shape = CircleShape,
                color = joystickColor,
                shadowElevation = 8.dp
            ) {}
        }
    }
}
