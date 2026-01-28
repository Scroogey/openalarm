package de.laurik.openalarm

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import de.laurik.openalarm.utils.AppLogger
import java.io.File

/**
 * Screen for viewing application logs.
 * Provides functionality to view, search, clear, and share logs.
 */
@Composable
fun LogViewerScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val logger = remember { AppLogger(context) }

    // State for logs and search
    var logText by remember { mutableStateOf(logger.getLogContent()) }
    var searchText by remember { mutableStateOf("") }
    var filteredLogs by remember { mutableStateOf(logger.getLogContent()) }

    // Update filtered logs when search text changes
    LaunchedEffect(searchText, logText) {
        filteredLogs = if (searchText.isEmpty()) {
            logText
        } else {
            logText.split("\n")
                .filter { it.contains(searchText, ignoreCase = true) }
                .joinToString("\n")
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.desc_back))
                }
                Text(
                    stringResource(R.string.log_viewer_title),
                    style = MaterialTheme.typography.headlineMedium
                )
            }

            // Search bar
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text(stringResource(R.string.log_search_placeholder)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { searchText = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = null)
                        }
                    }
                }
            )

            // Log content with scroll
            Box(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (filteredLogs.isEmpty()) stringResource(R.string.log_no_entries) else filteredLogs,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                )
            }

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = {
                        logText = logger.getLogContent()
                        filteredLogs = if (searchText.isEmpty()) logText else ""
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.log_refresh))
                }

                Spacer(modifier = Modifier.width(8.dp))

                val logNoEntries = stringResource(R.string.log_no_entries)
                OutlinedButton(
                    onClick = {
                        logger.clearLogs()
                        logText = logNoEntries
                        filteredLogs = if (searchText.isEmpty()) logText else ""
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.log_clear))
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        val file = File(context.filesDir, "best_alarm_logs.txt")
                        if (file.exists()) {
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.provider",
                                file
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share logs"))
                        } else {
                            // Show a toast if file doesn't exist
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.log_share))
                }
            }
        }
    }
}
