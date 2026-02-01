package de.laurik.openalarm

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.documentfile.provider.DocumentFile
import de.laurik.openalarm.utils.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object CustomRingtoneRepository {
    private const val TAG = "CustomRingtoneRepo"
    private val scope = CoroutineScope(Dispatchers.IO)
    
    val customRingtones = mutableStateListOf<CustomRingtoneEntity>()
    private val previewRotationIndices = mutableMapOf<String, Int>()
    private var isLoaded = false

    suspend fun ensureLoaded(context: Context) {
        if (isLoaded) return
        val db = AppDatabase.getDatabase(context).alarmDao()
        val loaded = db.getAllCustomRingtones()
        withContext(Dispatchers.Main) {
            customRingtones.clear()
            customRingtones.addAll(loaded)
            isLoaded = true
        }
    }

    fun addCustomRingtone(context: Context, ringtone: CustomRingtoneEntity) {
        // Take persistable permission
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(Uri.parse(ringtone.uri), flags)
        } catch (e: Exception) {
            AppLogger(context).e(TAG, "Failed to take persistable permission for ${ringtone.uri}", e)
        }

        val index = customRingtones.indexOfFirst { it.id == ringtone.id }
        if (index != -1) {
            customRingtones[index] = ringtone
        } else {
            customRingtones.add(ringtone)
        }
        
        scope.launch {
            AppDatabase.getDatabase(context).alarmDao().insertCustomRingtone(ringtone)
        }
    }

    fun deleteCustomRingtone(context: Context, ringtone: CustomRingtoneEntity) {
        customRingtones.remove(ringtone)
        // Release permission? Android doesn't strictly require releasing, but we could.
        scope.launch {
            AppDatabase.getDatabase(context).alarmDao().deleteCustomRingtone(ringtone)
        }
    }

    suspend fun resolveRingtoneUri(context: Context, alarm: AlarmItem): Uri? {
        val uriStr = alarm.ringtoneUri ?: return null
        if (!uriStr.startsWith("openalarm://custom/")) return Uri.parse(uriStr)

        val id = uriStr.removePrefix("openalarm://custom/")
        val customRt = AppDatabase.getDatabase(context).alarmDao().getCustomRingtone(id) ?: return null
        val rootUri = Uri.parse(customRt.uri)

        // Check if it's a folder or file
        return if (isFolder(context, rootUri)) {
            val documentFile = DocumentFile.fromTreeUri(context, rootUri)
            if (documentFile != null) resolveFolderRingtone(context, alarm, customRt, documentFile)
            else null
        } else {
            rootUri
        }
    }

    fun isFolder(context: Context, uri: Uri): Boolean {
        val uriString = uri.toString()
        // SAF heuristic: tree URIs contain "/tree/", document URIs contain "/document/"
        if (uriString.contains("/tree/")) return true
        if (uriString.contains("/document/")) return false
        
        return try {
            // Last resort check, but be careful as this might throw on non-tree URIs
            DocumentFile.fromTreeUri(context, uri)?.isDirectory ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun getTrackCount(context: Context, uri: Uri): Int {
        return try {
            val documentFile = DocumentFile.fromTreeUri(context, uri)
            documentFile?.listFiles()?.count { file ->
                val type = file.type ?: ""
                type.startsWith("audio/") || 
                file.name?.lowercase()?.let { name ->
                    name.endsWith(".mp3") || name.endsWith(".ogg") || name.endsWith(".wav") || name.endsWith(".m4a") || name.endsWith(".flac")
                } == true
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }

    suspend fun resolvePreviewUri(context: Context, rt: CustomRingtoneEntity): Uri? {
        val rootUri = Uri.parse(rt.uri)
        return if (isFolder(context, rootUri)) {
            val documentFile = DocumentFile.fromTreeUri(context, rootUri)
            if (documentFile == null) return null
            
            val files = documentFile.listFiles().filter { it.type?.startsWith("audio/") == true }.sortedBy { it.name }
            if (files.isEmpty()) return null
            
            when (rt.mode) {
                CustomRingtoneSelectionMode.RANDOM -> files.random().uri
                CustomRingtoneSelectionMode.ROTATING -> {
                    val currentIndex = previewRotationIndices.getOrDefault(rt.id, 0)
                    val index = currentIndex % files.size
                    val selected = files[index].uri
                    previewRotationIndices[rt.id] = (index + 1) % files.size
                    selected
                }
                else -> {
                    files.first().uri
                }
            }
        } else {
            rootUri
        }
    }

    private suspend fun resolveFolderRingtone(
        context: Context,
        alarm: AlarmItem,
        customRt: CustomRingtoneEntity,
        folder: DocumentFile
    ): Uri? {
        val files = folder.listFiles().filter { file ->
            val type = file.type ?: ""
            type.startsWith("audio/") || 
            file.name?.lowercase()?.let { name ->
                name.endsWith(".mp3") || name.endsWith(".ogg") || name.endsWith(".wav") || name.endsWith(".m4a") || name.endsWith(".flac")
            } == true
        }.sortedBy { it.name }

        if (files.isEmpty()) return null

        return when (customRt.mode) {
            CustomRingtoneSelectionMode.SINGLE -> files.firstOrNull()?.uri
            CustomRingtoneSelectionMode.RANDOM -> files.random().uri
            CustomRingtoneSelectionMode.ROTATING -> {
                val index = alarm.ringtoneRotationIndex % files.size
                val selected = files[index].uri
                
                // Update the alarm's index for NEXT time
                val nextIndex = (index + 1) % files.size
                scope.launch {
                    val updated = alarm.copy(ringtoneRotationIndex = nextIndex)
                    AlarmRepository.updateAlarm(context, updated)
                }
                selected
            }
        }
    }
}
