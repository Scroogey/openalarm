package de.laurik.openalarm

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.DialogProperties
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomRingtoneManagerDialog(
    onDismiss: () -> Unit,
    onRingtoneSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val ringtones = CustomRingtoneRepository.customRingtones
    var ringtoneToDelete by remember { mutableStateOf<CustomRingtoneEntity?>(null) }
    
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var playingUri by remember { mutableStateOf<String?>(null) }

    fun stopPreview() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        playingUri = null
    }

    fun playPreview(uriString: String) {
        stopPreview()
        try {
            val uri = Uri.parse(uriString)
            // If it's an openalarm URI, we need to resolve it first
            // But for preview in this dialog, we can just resolve it directly if it's already a content URI
            // Actually, we should use the resolver to handle folders too
            
            // Resolution might be async, so let's launch it
            // For simplicity in this UI, if it's a folder, we might just play the first file
            
            // Actually, let's keep it simple: if it's a content URI (file or tree), play it
            // If it is a tree, we need to pick a file.
            
            val intentUri = if (uriString.startsWith("openalarm://custom/")) {
                // We'd need a coroutine here. Let's use rememberCoroutineScope
                null 
            } else uri

            if (intentUri != null) {
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(context, intentUri)
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    prepare()
                    start()
                    playingUri = uriString
                    setOnCompletionListener { stopPreview() }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val scope = rememberCoroutineScope()

    fun playResolved(rt: CustomRingtoneEntity) {
        scope.launch {
            val resolved = CustomRingtoneRepository.resolvePreviewUri(context, rt)
            
            resolved?.let {
                withContext(Dispatchers.Main) {
                    stopPreview()
                    try {
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(context, it)
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ALARM)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .build()
                            )
                            prepare()
                            start()
                            playingUri = rt.id
                            setOnCompletionListener { stopPreview() }
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            stopPreview()
        }
    }
    
    LaunchedEffect(Unit) {
        CustomRingtoneRepository.ensureLoaded(context)
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val name = DocumentFile.fromSingleUri(context, it)?.name ?: context.getString(R.string.label_custom_file)
            CustomRingtoneRepository.addCustomRingtone(context, CustomRingtoneEntity(
                name = name,
                uri = it.toString(),
                mode = CustomRingtoneSelectionMode.SINGLE
            ))
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            val name = DocumentFile.fromTreeUri(context, it)?.name ?: context.getString(R.string.label_custom_folder)
            CustomRingtoneRepository.addCustomRingtone(context, CustomRingtoneEntity(
                name = name,
                uri = it.toString(),
                mode = CustomRingtoneSelectionMode.ROTATING // Default to rotating for folders
            ))
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
                topBar = {
                    LargeTopAppBar(
                        title = { 
                            Text(
                                stringResource(R.string.title_custom_ringtone_manager),
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.ArrowBack, stringResource(R.string.desc_back))
                            }
                        },
                        scrollBehavior = scrollBehavior,
                        expandedHeight = 300.dp
                    )
                },
                bottomBar = {
                    Surface(tonalElevation = 2.dp) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { filePicker.launch(arrayOf("audio/*")) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Add, null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.action_add_file))
                            }
                            Button(
                                onClick = { folderPicker.launch(null) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Add, null)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.action_add_folder))
                            }
                        }
                    }
                }
            ) { padding ->
                Column(Modifier.padding(padding).fillMaxSize()) {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        if (ringtones.isEmpty()) {
                            item {
                                Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        stringResource(R.string.msg_no_custom_ringtones),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(ringtones) { rt ->
                                RingtoneListItem(
                                    rt = rt,
                                    isPlaying = playingUri == rt.id,
                                    onSelect = { onRingtoneSelected("openalarm://custom/${rt.id}") },
                                    onDelete = { ringtoneToDelete = rt },
                                    onPlayPreview = { 
                                        if (playingUri == rt.id) stopPreview()
                                        else playResolved(rt)
                                    },
                                    onUpdateMode = { newMode ->
                                        CustomRingtoneRepository.addCustomRingtone(context, rt.copy(mode = newMode))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (ringtoneToDelete != null) {
        AlertDialog(
            onDismissRequest = { ringtoneToDelete = null },
            title = { Text(stringResource(R.string.dialog_title_careful)) },
            text = { Text(stringResource(R.string.dialog_msg_delete_custom_ringtone, ringtoneToDelete?.name ?: "")) },
            confirmButton = {
                TextButton(onClick = {
                    ringtoneToDelete?.let { CustomRingtoneRepository.deleteCustomRingtone(context, it) }
                    ringtoneToDelete = null
                }) { Text(stringResource(R.string.desc_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { ringtoneToDelete = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
fun RingtoneListItem(
    rt: CustomRingtoneEntity,
    isPlaying: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onPlayPreview: () -> Unit,
    onUpdateMode: (CustomRingtoneSelectionMode) -> Unit
) {
    val context = LocalContext.current
    val isFolder = CustomRingtoneRepository.isFolder(context, Uri.parse(rt.uri))
    var trackCount by remember { mutableIntStateOf(0) }
    
    if (isFolder) {
        LaunchedEffect(rt.uri) {
            withContext(Dispatchers.IO) {
                trackCount = CustomRingtoneRepository.getTrackCount(context, Uri.parse(rt.uri))
            }
        }
    }

    val typeLabel = if (isFolder) {
        val res = context.resources
        res.getQuantityString(R.plurals.fmt_folder_and_tracks, trackCount, trackCount)
    } else ""
    
    ListItem(
        modifier = Modifier.clickable { onSelect() },
        headlineContent = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(rt.name, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                Spacer(Modifier.width(8.dp))
                Text(typeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        supportingContent = {
            if (isFolder) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        when(rt.mode) {
                            CustomRingtoneSelectionMode.RANDOM -> stringResource(R.string.label_mode_random)
                            CustomRingtoneSelectionMode.ROTATING -> stringResource(R.string.label_mode_rotating)
                            else -> stringResource(R.string.label_mode_single)
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(Modifier.width(8.dp))
                    // Loop through modes for folders
                    Text(
                        stringResource(R.string.action_change),
                        modifier = Modifier.clickable {
                            val next = if (rt.mode == CustomRingtoneSelectionMode.ROTATING) CustomRingtoneSelectionMode.RANDOM else CustomRingtoneSelectionMode.ROTATING
                            onUpdateMode(next)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Text(stringResource(R.string.label_mode_single), style = MaterialTheme.typography.bodySmall)
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPlayPreview) {
                    Icon(
                        if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.desc_preview),
                        tint = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, stringResource(R.string.action_delete_ringtone), tint = MaterialTheme.colorScheme.error)
                }
            }
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}
