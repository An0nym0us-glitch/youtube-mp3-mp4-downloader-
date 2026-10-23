package com.ytdownloader.app

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class MediaFormat(val ext: String, val label: String, val mime: String) {
    MP3("mp3", "MP3", "audio/mpeg"),
    MP4("mp4", "MP4", "video/mp4"),
}

/** The choices offered in the UI. Defaults live in [Prefs]; nothing here is forced on a download. */
object Options {
    const val BEST = 0
    val AUDIO_BITRATES = listOf(320, 256, 192, 128)
    val VIDEO_HEIGHTS = listOf(BEST, 2160, 1440, 1080, 720, 480, 360)
    const val DEFAULT_FOLDER = "YT Downloader"
    const val DEFAULT_NAME_TEMPLATE = "%(title)s"
    const val UPDATE_INTERVAL_MS = 24 * 60 * 60 * 1000L

    fun heightLabel(height: Int) = if (height == BEST) "Best" else "${height}p"
    fun qualityLabel(format: MediaFormat, quality: Int) =
        if (format == MediaFormat.MP3) "$quality kbps" else heightLabel(quality)
}

data class Prefs(
    val format: MediaFormat = MediaFormat.MP3,
    val audioKbps: Int = Options.AUDIO_BITRATES.first(),
    val videoHeight: Int = Options.BEST,
    val zipOutput: Boolean = true,
    val folderName: String = Options.DEFAULT_FOLDER,
    val nameTemplate: String = Options.DEFAULT_NAME_TEMPLATE,
    val embedMetadata: Boolean = true,
    val autoUpdate: Boolean = true,
    /** Raw yt-dlp options appended to every run, for power users and quick workarounds. */
    val extraArgs: String = "",
)

/** User preferences, persisted in SharedPreferences and observable from Compose. */
object Settings {
    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(Prefs())
    val state: StateFlow<Prefs> = _state.asStateFlow()
    val current: Prefs get() = _state.value

    fun init(context: Context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _state.value = read()
    }

    fun update(transform: (Prefs) -> Prefs) {
        val next = transform(_state.value)
        _state.value = next
        write(next)
    }

    var lastUpdateCheck: Long
        get() = prefs.getLong(KEY_LAST_UPDATE, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_UPDATE, value) }

    private fun read(): Prefs {
        val d = Prefs()
        return Prefs(
            format = MediaFormat.entries.firstOrNull { it.name == prefs.getString(KEY_FORMAT, null) } ?: d.format,
            audioKbps = prefs.getInt(KEY_AUDIO, d.audioKbps),
            videoHeight = prefs.getInt(KEY_VIDEO, d.videoHeight),
            zipOutput = prefs.getBoolean(KEY_ZIP, d.zipOutput),
            folderName = prefs.getString(KEY_FOLDER, null) ?: d.folderName,
            nameTemplate = prefs.getString(KEY_TEMPLATE, null) ?: d.nameTemplate,
            embedMetadata = prefs.getBoolean(KEY_METADATA, d.embedMetadata),
            autoUpdate = prefs.getBoolean(KEY_AUTO_UPDATE, d.autoUpdate),
            extraArgs = prefs.getString(KEY_EXTRA_ARGS, null) ?: d.extraArgs,
        )
    }

    private fun write(p: Prefs) = prefs.edit {
        putString(KEY_FORMAT, p.format.name)
        putInt(KEY_AUDIO, p.audioKbps)
        putInt(KEY_VIDEO, p.videoHeight)
        putBoolean(KEY_ZIP, p.zipOutput)
        putString(KEY_FOLDER, p.folderName)
        putString(KEY_TEMPLATE, p.nameTemplate)
        putBoolean(KEY_METADATA, p.embedMetadata)
        putBoolean(KEY_AUTO_UPDATE, p.autoUpdate)
        putString(KEY_EXTRA_ARGS, p.extraArgs)
    }

    private const val KEY_FORMAT = "format"
    private const val KEY_AUDIO = "audio_kbps"
    private const val KEY_VIDEO = "video_height"
    private const val KEY_ZIP = "zip_output"
    private const val KEY_FOLDER = "folder_name"
    private const val KEY_TEMPLATE = "name_template"
    private const val KEY_METADATA = "embed_metadata"
    private const val KEY_AUTO_UPDATE = "auto_update"
    private const val KEY_EXTRA_ARGS = "extra_args"
    private const val KEY_LAST_UPDATE = "last_update_check"
}
