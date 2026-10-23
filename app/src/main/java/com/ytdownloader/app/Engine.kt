package com.ytdownloader.app

import android.app.Application
import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Owns the bundled yt-dlp + Python + ffmpeg runtime: unpacking it on first launch and keeping yt-dlp fresh. */
object Engine {
    sealed interface State {
        data object Preparing : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Preparing)
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val updateLock = Mutex()

    fun start(app: Application) {
        scope.launch {
            try {
                YoutubeDL.init(app)
                FFmpeg.init(app)
                _state.value = State.Ready
            } catch (e: Exception) {
                _state.value = State.Failed(Errors.friendly(e))
                return@launch
            }
            val due = System.currentTimeMillis() - Settings.lastUpdateCheck > Options.UPDATE_INTERVAL_MS
            if (Settings.current.autoUpdate && due && !DownloadQueue.hasActive) {
                runCatching { update(app) }
            }
        }
    }

    suspend fun awaitReady() {
        val s = state.first { it != State.Preparing }
        if (s is State.Failed) throw IllegalStateException("Download engine failed to start: ${s.message}")
    }

    /** YouTube changes often; a fresh yt-dlp is usually the fix when downloads start failing. */
    suspend fun update(context: Context): String = withContext(Dispatchers.IO) {
        awaitReady()
        updateLock.withLock {
            val app = context.applicationContext
            val status = YoutubeDL.updateYoutubeDL(app)
            Settings.lastUpdateCheck = System.currentTimeMillis()
            val v = version(app)
            if (status == YoutubeDL.UpdateStatus.DONE) "yt-dlp updated to $v" else "yt-dlp is up to date ($v)"
        }
    }

    fun version(context: Context): String =
        YoutubeDL.versionName(context) ?: YoutubeDL.version(context) ?: "bundled"
}
