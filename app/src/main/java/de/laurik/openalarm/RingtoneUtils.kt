package de.laurik.openalarm

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import kotlinx.coroutines.runBlocking
import de.laurik.openalarm.CustomRingtoneRepository

object RingtoneUtils {
    fun getRingtoneTitle(context: Context, uriString: String?): String {
        if (uriString == null) return context.getString(R.string.fmt_system_ringtone, context.getString(R.string.default_alarm_title))
        
        if (uriString.startsWith("openalarm://custom/")) {
            val id = uriString.removePrefix("openalarm://custom/")
            return runBlocking {
                val rt = AppDatabase.getDatabase(context).alarmDao().getCustomRingtone(id)
                if (rt != null) {
                    val uri = Uri.parse(rt.uri)
                    val isFolder = CustomRingtoneRepository.isFolder(context, uri)
                    val type = if (isFolder) {
                        val count = CustomRingtoneRepository.getTrackCount(context, uri)
                        context.resources.getQuantityString(R.plurals.fmt_folder_and_tracks, count, count)
                    } else ""
                    context.getString(R.string.fmt_custom_ringtone, rt.name, type)
                } else context.getString(R.string.label_unknown_custom)
            }
        }

        return try {
            val uri = Uri.parse(uriString)
            val ringtone = RingtoneManager.getRingtone(context, uri)
            val title = ringtone?.getTitle(context) ?: context.getString(R.string.label_unknown)
            context.getString(R.string.fmt_system_ringtone, title)
        } catch (e: Exception) {
            context.getString(R.string.fmt_system_ringtone_custom)
        }
    }
}