package com.ytdownloader.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {
    var link by mutableStateOf("")
        private set
    var info by mutableStateOf<MediaInfo?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var wholePlaylist by mutableStateOf(true)
        private set
    var selected by mutableStateOf(emptySet<Int>())
        private set
    var format by mutableStateOf(Settings.current.format)
    var audioKbps by mutableIntStateOf(Settings.current.audioKbps)
    var videoHeight by mutableIntStateOf(Settings.current.videoHeight)

    private var fetchJob: Job? = null

    val hasVideoAndPlaylist: Boolean
        get() = Links.extract(link)?.let(Links::hasVideoAndPlaylist) == true

    /** A single video offers only the resolutions it really has; playlists get the general list. */
    val videoChoices: List<Int>
        get() = info?.videoHeights?.takeIf { it.isNotEmpty() }?.let { listOf(Options.BEST) + it }
            ?: Options.VIDEO_HEIGHTS

    fun onLinkChange(value: String) {
        link = value
        fetchJob?.cancel()
        loading = false
        info = null
        error = null
    }

    fun onShared(text: String) {
        onLinkChange(text.trim())
        fetch()
    }

    fun updateWholePlaylist(value: Boolean) {
        wholePlaylist = value
        if (info != null || loading) fetch()
    }

    fun toggle(index: Int) {
        selected = if (index in selected) selected - index else selected + index
    }

    fun selectAll(all: Boolean) {
        selected = if (all) info?.entries?.map { it.index }?.toSet().orEmpty() else emptySet()
    }

    fun fetch() {
        val url = Links.extract(link) ?: run {
            error = "Paste a YouTube video or playlist link"
            return
        }
        fetchJob?.cancel()
        info = null
        error = null
        loading = true
        val singleOnly = Links.hasVideoAndPlaylist(url) && !wholePlaylist
        fetchJob = viewModelScope.launch {
            val self = coroutineContext.job
            try {
                val result = InfoFetcher.fetch(getApplication(), url, singleOnly)
                info = result
                selected = result.entries.map { it.index }.toSet()
                val defaultHeight = Settings.current.videoHeight
                videoHeight = if (defaultHeight in videoChoices) defaultHeight else Options.BEST
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = Errors.friendly(e)
            } finally {
                if (fetchJob === self) loading = false
            }
        }
    }

    fun download(): Boolean {
        val current = info ?: return false
        val items = current.entries.map { it.index }.filter { it in selected }
        if (current.isPlaylist && items.isEmpty()) {
            error = "Select at least one video"
            return false
        }
        val s = Settings.current
        DownloadQueue.add(
            getApplication(),
            DownloadTask(
                url = current.url,
                title = current.title,
                thumbnail = current.thumbnail,
                format = format,
                quality = if (format == MediaFormat.MP3) audioKbps else videoHeight,
                isPlaylist = current.isPlaylist,
                playlistItems = items.takeIf { current.isPlaylist && it.size < current.entries.size },
                itemCount = if (current.isPlaylist) items.size else 1,
                zipOutput = s.zipOutput,
                folderName = s.folderName,
                nameTemplate = s.nameTemplate,
                embedMetadata = s.embedMetadata,
                extraArgs = s.extraArgs,
            )
        )
        onLinkChange("")
        return true
    }
}
