@file:OptIn(ExperimentalMaterial3Api::class)

package com.ytdownloader.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ytdownloader.app.DownloadQueue
import com.ytdownloader.app.DownloadTask
import com.ytdownloader.app.Engine
import com.ytdownloader.app.Errors
import com.ytdownloader.app.MainViewModel
import com.ytdownloader.app.MediaEntry
import com.ytdownloader.app.MediaFormat
import com.ytdownloader.app.MediaInfo
import com.ytdownloader.app.Options
import com.ytdownloader.app.Prefs
import com.ytdownloader.app.Settings
import com.ytdownloader.app.Status
import com.ytdownloader.app.formatDuration
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val tasks by DownloadQueue.tasks.collectAsStateWithLifecycle()
    val engine by Engine.state.collectAsStateWithLifecycle()
    val settings by Settings.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var updating by remember { mutableStateOf(false) }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    LaunchedEffect(Unit) {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }.filter { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) permissions.launch(needed.toTypedArray())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("YT Downloader") },
                actions = {
                    IconButton(
                        enabled = !updating,
                        onClick = {
                            updating = true
                            scope.launch {
                                val message = try {
                                    if (DownloadQueue.hasActive) "Wait for downloads to finish, then update"
                                    else Engine.update(context)
                                } catch (e: Exception) {
                                    "Update failed: ${Errors.friendly(e)}"
                                }
                                updating = false
                                snackbar.showSnackbar(message)
                            }
                        },
                    ) {
                        if (updating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.Refresh, contentDescription = "Update yt-dlp")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (engine != Engine.State.Ready) item { EngineBanner(engine) }
            item { LinkCard(vm) }
            vm.info?.let { info ->
                item {
                    InfoCard(vm, info, settings) {
                        scope.launch { snackbar.showSnackbar("Added to downloads") }
                    }
                }
                if (info.isPlaylist) {
                    item { PlaylistHeader(vm, info) }
                    items(info.entries, key = { "entry-${it.index}" }) { entry ->
                        EntryRow(entry, entry.index in vm.selected) { vm.toggle(entry.index) }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Downloads", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    if (tasks.any { !it.status.isActive }) {
                        TextButton(onClick = DownloadQueue::clearFinished) { Text("Clear finished") }
                    }
                }
            }
            if (tasks.isEmpty()) {
                item {
                    Text(
                        "Nothing yet. Tip: in the YouTube app tap Share → YT Downloader.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(tasks, key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    onCancel = { DownloadQueue.cancel(task.id) },
                    onRetry = { DownloadQueue.retry(context, task.id) },
                    onRemove = { DownloadQueue.remove(task.id) },
                    onOpen = { task.output?.let { Intents.open(context, it) } },
                    onShare = { task.output?.let { Intents.share(context, it) } },
                )
            }
        }
    }

    if (showSettings) SettingsSheet(settings) { showSettings = false }
}

@Composable
private fun EngineBanner(state: Engine.State) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state) {
                is Engine.State.Failed -> Text(
                    "Download engine failed to start: ${state.message}",
                    color = MaterialTheme.colorScheme.error,
                )
                else -> {
                    Text("Setting up the download engine… (only slow on first launch)")
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun LinkCard(vm: MainViewModel) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = vm.link,
                onValueChange = vm::onLinkChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("YouTube video or playlist link") },
                singleLine = true,
                trailingIcon = {
                    if (vm.link.isNotEmpty()) {
                        IconButton(onClick = { vm.onLinkChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { vm.fetch() }),
            )
            if (vm.hasVideoAndPlaylist) {
                Text("This link points to a video inside a playlist:", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !vm.wholePlaylist,
                        onClick = { vm.updateWholePlaylist(false) },
                        label = { Text("Only this video") },
                    )
                    FilterChip(
                        selected = vm.wholePlaylist,
                        onClick = { vm.updateWholePlaylist(true) },
                        label = { Text("Whole playlist") },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        clipboard.getText()?.text?.takeIf { it.isNotBlank() }?.let {
                            vm.onLinkChange(it.trim())
                            vm.fetch()
                        }
                    },
                ) { Text("Paste") }
                Button(
                    modifier = Modifier.weight(1f),
                    enabled = !vm.loading && vm.link.isNotBlank(),
                    onClick = vm::fetch,
                ) { Text("Fetch") }
            }
            if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            vm.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (Errors.needsLogin(it)) {
                    Button(modifier = Modifier.fillMaxWidth(), onClick = { Intents.signIn(context) }) {
                        Text("Sign in to YouTube")
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(vm: MainViewModel, info: MediaInfo, settings: Prefs, onQueued: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column {
            if (info.thumbnail != null) {
                AsyncImage(
                    model = info.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                )
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    info.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(
                    if (info.isPlaylist) "Playlist · ${info.entries.size} videos" else null,
                    info.uploader,
                    info.durationSec?.let(::formatDuration),
                ).joinToString(" · ")
                if (meta.isNotEmpty()) {
                    Text(meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    MediaFormat.entries.forEachIndexed { i, f ->
                        SegmentedButton(
                            selected = vm.format == f,
                            onClick = { vm.format = f },
                            shape = SegmentedButtonDefaults.itemShape(i, MediaFormat.entries.size),
                        ) { Text(if (f == MediaFormat.MP3) "MP3 · audio" else "MP4 · video") }
                    }
                }

                Text("Quality", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (vm.format == MediaFormat.MP3) {
                        Options.AUDIO_BITRATES.forEach { kbps ->
                            FilterChip(
                                selected = vm.audioKbps == kbps,
                                onClick = { vm.audioKbps = kbps },
                                label = { Text(Options.qualityLabel(MediaFormat.MP3, kbps)) },
                            )
                        }
                    } else {
                        vm.videoChoices.forEach { height ->
                            FilterChip(
                                selected = vm.videoHeight == height,
                                onClick = { vm.videoHeight = height },
                                label = { Text(Options.heightLabel(height)) },
                            )
                        }
                    }
                }

                val count = if (info.isPlaylist) vm.selected.size else 1
                val noun = if (count == 1) "item" else "items"
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = count > 0,
                    onClick = { if (vm.download()) onQueued() },
                ) {
                    Text("Download $count $noun as ${vm.format.label}" + if (settings.zipOutput) " (ZIP)" else "")
                }
            }
        }
    }
}

@Composable
private fun PlaylistHeader(vm: MainViewModel, info: MediaInfo) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${vm.selected.size} of ${info.entries.size} selected",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { vm.selectAll(true) }) { Text("All") }
        TextButton(onClick = { vm.selectAll(false) }) { Text("None") }
    }
}

@Composable
private fun EntryRow(entry: MediaEntry, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(
            "${entry.index}. ${entry.title}",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        entry.durationSec?.let {
            Spacer(Modifier.width(8.dp))
            Text(formatDuration(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TaskCard(
    task: DownloadTask,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
) {
    val context = LocalContext.current
    OutlinedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AsyncImage(
                model = task.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(96.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(task.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${task.format.label} · ${Options.qualityLabel(task.format, task.quality)}" +
                        if (task.isPlaylist) " · ${task.itemCount} items" else "",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    statusText(task),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (task.status == Status.FAILED) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                if (task.status == Status.RUNNING) {
                    LinearProgressIndicator(progress = { task.progress }, modifier = Modifier.fillMaxWidth())
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (task.status) {
                        Status.QUEUED, Status.RUNNING -> TextButton(onClick = onCancel) { Text("Cancel") }
                        Status.DONE -> {
                            TextButton(onClick = onOpen) { Text(if (task.output?.uri != null) "Open" else "Show folder") }
                            if (task.output?.uri != null) TextButton(onClick = onShare) { Text("Share") }
                        }
                        Status.FAILED, Status.CANCELLED -> {
                            TextButton(onClick = onRetry) { Text("Retry") }
                            if (Errors.needsLogin(task.error)) {
                                TextButton(onClick = { Intents.signIn(context) }) { Text("Sign in") }
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (!task.status.isActive) {
                        IconButton(onClick = onRemove) { Icon(Icons.Default.Delete, contentDescription = "Remove from list") }
                    }
                }
            }
        }
    }
}

private fun statusText(task: DownloadTask): String = when (task.status) {
    Status.QUEUED -> "Waiting…"
    Status.RUNNING -> task.detail.ifEmpty { "Starting…" }
    Status.DONE -> buildString {
        val out = task.output
        append("Saved ${out?.name} to ${out?.location}")
        if (task.skippedItems > 0) append(" · ${task.skippedItems} unavailable item(s) skipped")
    }
    Status.FAILED -> task.error ?: "Failed"
    Status.CANCELLED -> "Cancelled"
}
