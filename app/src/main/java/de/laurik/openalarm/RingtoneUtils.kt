package de.laurik.openalarm

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri

object RingtoneUtils {
    fun getRingtoneTitle(context: Context, uriString: String?): String {
        if (uriString == null) return "Default Alarm"
        return try {
            val uri = Uri.parse(uriString)
            val ringtone = RingtoneManager.getRingtone(context, uri)
            ringtone?.getTitle(context) ?: "Unknown"
        } catch (e: Exception) {
            "Custom Tone"
        }
    }
}