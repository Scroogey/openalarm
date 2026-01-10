package de.laurik.openalarm

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    ) {
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.desc_back))
            }
            Text(stringResource(R.string.title_about_settings), style = MaterialTheme.typography.headlineMedium)
        }

        HorizontalDivider()

        // The library data is loaded here
        val libraries by produceLibraries(R.raw.aboutlibraries)

        // LibrariesContainer fills the remaining space in the Column
        LibrariesContainer(
            libraries = libraries,
            modifier = Modifier.fillMaxSize()
        )
    }
}