package com.ytdownloader.app

import android.net.Uri
import java.util.UUID

data class MediaEntry(
    /** 1-based position in the playlist, as yt-dlp's --playlist-items expects. */
    val index: Int,
    val title: String,
    val durationSec: Int?,
)

data class MediaInfo(
    val url: String,
    val title: String,
    val uploader: String?,
    val thumbnail: String?,
    val durationSec: Int?,
    val isPlaylist: Boolean,
    val entries: List<MediaEntry>,
    /** Video heights this single video is actually available in, highest first. */
    val videoHeights: List<Int>,
)

enum class Status {
    QUEUED, RUNNING, DONE, FAILED, CANCELLED;

    val isActive get() = this == QUEUED || this == RUNNING
}

data class SavedOutput(
    /** Null when several loose files were saved; there is no single file to open. */
    val uri: Uri?,
    val name: String,
    val mime: String,
    val location: String,
    val fileCount: Int,
)

/** A download request plus its live state. Settings are copied in so later changes don't affect it. */
data class DownloadTask(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val title: String,
    val thumbnail: String?,
    val format: MediaFormat,
    val quality: Int,
    val isPlaylist: Boolean,
    /** Selected playlist positions, or null for everything. */
    val playlistItems: List<Int>?,
    val itemCount: Int,
    val zipOutput: Boolean,
    val folderName: String,
    val nameTemplate: String,
    val embedMetadata: Boolean,
    val extraArgs: String,
    val status: Status = Status.QUEUED,
    val progress: Float = 0f,
    val detail: String = "",
    val error: String? = null,
    val skippedItems: Int = 0,
    val output: SavedOutput? = null,
)

object Errors {
    const val LOGIN_REQUIRED = "YouTube wants you to sign in to confirm you're not a bot. " +
        "Tap \"Sign in to YouTube\", log in once, then try again."

    /** YouTube's bot check, or age/member-restricted videos: all fixed by passing login cookies. */
    fun needsLogin(message: String?): Boolean {
        val m = message.orEmpty()
        return m.contains("not a bot") || m.contains("Sign in to confirm") || m.contains("--cookies") ||
            m == LOGIN_REQUIRED
    }

    /** yt-dlp dumps warnings and tracebacks to stderr; surface the line that explains the failure. */
    fun friendly(e: Throwable): String {
        if (needsLogin(e.message)) return LOGIN_REQUIRED
        val lines = e.message.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
        val line = lines.firstOrNull { it.startsWith("ERROR:") }?.removePrefix("ERROR:")?.trim()
            ?: lines.lastOrNull()
            ?: e.javaClass.simpleName
        return line.take(300)
    }
}

fun formatDuration(totalSec: Int): String {
    val h = totalSec / 3600
    val m = totalSec % 3600 / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
