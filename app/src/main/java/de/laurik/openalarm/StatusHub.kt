package de.laurik.openalarm

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// 1. Define the specific events that can happen
sealed class StatusEvent {
    object Idle : StatusEvent() // Nothing happening

    data class Ringing(
        val id: Int,
        val type: String
    ) : StatusEvent()

    data class Stopped(
        val id: Int,
        val type: String
    ) : StatusEvent()

    data class Snoozed(
        val id: Int,
        val type: String,
        val untilTime: Long
    ) : StatusEvent()

    data class Extended(
        val id: Int,
        val type: String,
        val newEndTime: Long
    ) : StatusEvent()

    data class Timeout(
        val id: Int,
        val type: String
    ) : StatusEvent()
}

// 2. The Hub
object StatusHub {
    var lastEvent by mutableStateOf<StatusEvent>(StatusEvent.Idle)
        private set

    // Simple helper to send events
    fun trigger(event: StatusEvent) {
        lastEvent = event
    }
}