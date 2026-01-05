package de.laurik.openalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Use goAsync because we need to suspend to load the DB
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                AlarmRepository.ensureLoaded(context)
                
                // Reschedule all enabled alarms to ensure they match new local time/timezone
                val scheduler = AlarmScheduler(context)
                for (group in AlarmRepository.groups) {
                    for (alarm in group.alarms) {
                        if (alarm.isEnabled) {
                            scheduler.schedule(alarm, group.offsetMinutes)
                        }
                    }
                }

                NotificationRenderer.refreshAll(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}