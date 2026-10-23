package com.ytdownloader.app

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/** Runs one [DownloadTask]: yt-dlp into a private work folder, then [Saver] into Download/. */
object Downloader {

    suspend fun run(context: Context, taskId: String) = withContext(Dispatchers.IO) {
        val task = DownloadQueue.get(taskId) ?: return@withContext
        if (task.status != Status.QUEUED) return@withContext
        val workDir = File(context.cacheDir, "work/${task.id}")
        workDir.deleteRecursively()
        workDir.mkdirs()
        DownloadQueue.update(task.id) { it.copy(status = Status.RUNNING, progress = 0f, detail = "Starting…") }
        try {
            Engine.awaitReady()
            val tracker = ProgressTracker(task.id, task.itemCount)
            try {
                YoutubeDL.execute(buildRequest(context, task, workDir), task.id, false) { _, _, line -> tracker.onLine(line) }
            } catch (e: YoutubeDLException) {
                // Playlists run with --ignore-errors, so private/removed videos fail without losing the rest.
                if (outputs(workDir, task.format).isEmpty()) throw e
            }
            DownloadQueue.checkNotCancelled(task.id)

            val files = outputs(workDir, task.format)
            if (files.isEmpty()) error("yt-dlp finished without producing a ${task.format.label} file")
            val output = Saver.save(context, task, files) { step ->
                DownloadQueue.update(task.id) { if (it.status == Status.RUNNING) it.copy(progress = 1f, detail = step) else it }
            }
            DownloadQueue.update(task.id) {
                it.copy(
                    status = Status.DONE,
                    progress = 1f,
                    detail = "",
                    output = output,
                    skippedItems = (task.itemCount - files.size).coerceAtLeast(0),
                )
            }
        } catch (e: YoutubeDL.CanceledException) {
            markCancelled(task.id)
        } catch (e: CancelledByUser) {
            markCancelled(task.id)
        } catch (e: CancellationException) {
            YoutubeDL.destroyProcessById(task.id)
            markCancelled(task.id)
            throw e
        } catch (e: Exception) {
            DownloadQueue.update(task.id) { it.copy(status = Status.FAILED, detail = "", error = Errors.friendly(e)) }
        } finally {
            workDir.deleteRecursively()
        }
        DownloadQueue.get(task.id)?.let { Notifications.finished(context, it) }
    }

    private fun markCancelled(id: String) =
        DownloadQueue.update(id) { it.copy(status = Status.CANCELLED, detail = "Cancelled") }

    private fun buildRequest(context: Context, task: DownloadTask, workDir: File): YoutubeDLRequest {
        val name = task.nameTemplate.ifBlank { Options.DEFAULT_NAME_TEMPLATE }
        val template = if (task.isPlaylist) "%(playlist_index)03d - $name" else name
        return YoutubeDLRequest(task.url).apply {
            addOption("-P", workDir.absolutePath)
            addOption("-o", "$template.%(ext)s")
            addOption("--trim-filenames", 160)
            addOption("--no-mtime")
            if (task.isPlaylist) {
                addOption("--yes-playlist")
                addOption("--ignore-errors")
                task.playlistItems?.let { addOption("--playlist-items", it.joinToString(",")) }
            } else {
                addOption("--no-playlist")
            }
            when (task.format) {
                MediaFormat.MP3 -> {
                    addOption("-f", "bestaudio/best")
                    addOption("-x")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "${task.quality}K")
                    if (task.embedMetadata) {
                        addOption("--embed-thumbnail")
                        addOption("--convert-thumbnails", "jpg")
                    }
                }
                MediaFormat.MP4 -> {
                    // Highest resolution up to the chosen one, preferring H.264/AAC so every phone can play it.
                    val res = if (task.quality == Options.BEST) "res" else "res:${task.quality}"
                    addOption("-S", "$res,vcodec:h264,acodec:aac")
                    addOption("--merge-output-format", "mp4")
                    addOption("--remux-video", "mp4")
                }
            }
            if (task.embedMetadata) addOption("--embed-metadata")
            Cookies.applyTo(this, context, task.extraArgs)
        }
    }

    private fun outputs(dir: File, format: MediaFormat): List<File> =
        dir.walkTopDown()
            .filter { it.isFile && it.extension.equals(format.ext, ignoreCase = true) }
            .sortedBy { it.name }
            .toList()

    /** Turns yt-dlp's console output into overall progress across every playlist item. */
    private class ProgressTracker(private val taskId: String, itemCount: Int) {
        private var item = 1
        private var total = itemCount.coerceAtLeast(1)
        private var lastFraction = -1f
        private var lastDetail = ""

        fun onLine(line: String) {
            itemRegex.find(line)?.let { m ->
                item = m.groupValues[1].toInt()
                total = m.groupValues[2].toInt().coerceAtLeast(1)
                publish(0f, label("Starting"))
                return
            }
            percentRegex.find(line)?.let { m ->
                val pct = m.groupValues[1].toFloat().coerceIn(0f, 100f)
                publish(pct / 100f, label("Downloading ${pct.roundToInt()}%"))
                return
            }
            phaseOf(line)?.let { publish(1f, label(it)) }
        }

        private fun label(text: String) = if (total > 1) "Item $item of $total · $text" else text

        private fun publish(itemFraction: Float, detail: String) {
            val overall = ((item - 1) + itemFraction) / total
            if (detail == lastDetail && abs(overall - lastFraction) < 0.005f) return
            lastDetail = detail
            lastFraction = overall
            DownloadQueue.update(taskId) {
                if (it.status == Status.RUNNING) it.copy(progress = overall, detail = detail) else it
            }
        }

        private fun phaseOf(line: String): String? = when {
            line.startsWith("[ExtractAudio]") -> "Converting to MP3"
            line.startsWith("[Merger]") -> "Merging video and audio"
            line.startsWith("[VideoRemuxer]") || line.startsWith("[VideoConvertor]") -> "Converting to MP4"
            line.startsWith("[EmbedThumbnail]") || line.startsWith("[Metadata]") -> "Adding tags"
            else -> null
        }

        companion object {
            private val itemRegex = Regex("""Downloading (?:item|video) (\d+) of (\d+)""")
            private val percentRegex = Regex("""\[download]\s+(\d+(?:\.\d+)?)%""")
        }
    }
}
