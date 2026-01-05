package de.laurik.openalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // 1. Go Async: Tells the system "We are doing background work, don't kill this receiver yet"
            val pendingResult = goAsync()

            // 2. Launch Coroutine
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    // 3. Load Data (Suspends here, non-blocking)
                    AlarmRepository.ensureLoaded(context)

                    // 4. Reschedule all enabled alarms
                    // Now that data is loaded, we can iterate the Repository safely
                    val scheduler = AlarmScheduler(context)
                    for (group in AlarmRepository.groups) {
                        for (alarm in group.alarms) {
                            if (alarm.isEnabled) {
                                scheduler.schedule(alarm, group.offsetMinutes)
                            }
                        }
                    }
                    scheduler.scheduleNotificationUpdate()
                } finally {
                    // 5. Finish: Tells the system "We are done, you can kill this receiver now"
                    pendingResult.finish()
                }
            }
        }
    }
}