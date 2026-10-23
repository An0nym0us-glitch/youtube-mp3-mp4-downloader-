@file:OptIn(ExperimentalMaterial3Api::class)

package com.ytdownloader.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ytdownloader.app.Cookies
import com.ytdownloader.app.Engine
import com.ytdownloader.app.MediaFormat
import com.ytdownloader.app.Options
import com.ytdownloader.app.Prefs
import com.ytdownloader.app.Saver
import com.ytdownloader.app.Settings

@Composable
fun SettingsSheet(settings: Prefs, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val version = remember { runCatching { Engine.version(context) }.getOrDefault("unknown") }
    val signedIn by Cookies.signedIn.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("YouTube account", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (signedIn) "Signed in. Downloads use your session."
                        else "Sign in if YouTube asks you to confirm you're not a bot",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (signedIn) {
                    OutlinedButton(onClick = { Cookies.signOut(context) }) { Text("Sign out") }
                } else {
                    Button(onClick = { Intents.signIn(context) }) { Text("Sign in") }
                }
            }

            SwitchRow(
                title = "Pack into a ZIP",
                subtitle = "One .zip per video or playlist. Turn off to save loose files.",
                checked = settings.zipOutput,
            ) { v -> Settings.update { it.copy(zipOutput = v) } }

            SwitchRow(
                title = "Embed tags & cover art",
                subtitle = "Title, artist and thumbnail inside the file",
                checked = settings.embedMetadata,
            ) { v -> Settings.update { it.copy(embedMetadata = v) } }

            SwitchRow(
                title = "Auto-update yt-dlp",
                subtitle = "Checks once a day. Keeps YouTube downloads working.",
                checked = settings.autoUpdate,
            ) { v -> Settings.update { it.copy(autoUpdate = v) } }

            OutlinedTextField(
                value = settings.folderName,
                onValueChange = { v -> Settings.update { it.copy(folderName = v) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Save folder") },
                supportingText = { Text("Download/${Saver.cleanFolder(settings.folderName)}") },
            )

            OutlinedTextField(
                value = settings.nameTemplate,
                onValueChange = { v -> Settings.update { it.copy(nameTemplate = v) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("File name template") },
                supportingText = { Text("yt-dlp template, e.g. %(title)s or %(uploader)s - %(title)s") },
            )

            OutlinedTextField(
                value = settings.extraArgs,
                onValueChange = { v -> Settings.update { it.copy(extraArgs = v) } },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Extra yt-dlp options (advanced)") },
                supportingText = { Text("Added to every request, e.g. --extractor-args \"youtube:player_client=tv\"") },
            )

            ChoiceRow("Default format", MediaFormat.entries, settings.format, { it.label }) { v ->
                Settings.update { it.copy(format = v) }
            }
            ChoiceRow(
                "Default MP3 quality",
                Options.AUDIO_BITRATES,
                settings.audioKbps,
                { Options.qualityLabel(MediaFormat.MP3, it) },
            ) { v -> Settings.update { it.copy(audioKbps = v) } }
            ChoiceRow("Default MP4 quality", Options.VIDEO_HEIGHTS, settings.videoHeight, Options::heightLabel) { v ->
                Settings.update { it.copy(videoHeight = v) }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "yt-dlp $version",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { Settings.update { Prefs() } }) { Text("Reset to defaults") }
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> ChoiceRow(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(selected = option == selected, onClick = { onSelect(option) }, label = { Text(label(option)) })
            }
        }
    }
}
