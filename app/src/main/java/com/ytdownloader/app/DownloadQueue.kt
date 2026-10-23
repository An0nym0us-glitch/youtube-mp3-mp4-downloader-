package com.ytdownloader.app

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CancelledByUser : Exception("Cancelled")

/** In-memory list of downloads, newest first. [DownloadService] works through it oldest first. */
object DownloadQueue {
    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    val hasActive get() = _tasks.value.any { it.status.isActive }

    fun add(context: Context, task: DownloadTask) {
        _tasks.update { listOf(task) + it }
        DownloadService.start(context)
    }

    fun get(id: String) = _tasks.value.firstOrNull { it.id == id }

    fun nextQueued() = _tasks.value.lastOrNull { it.status == Status.QUEUED }

    fun update(id: String, transform: (DownloadTask) -> DownloadTask) =
        _tasks.update { list -> list.map { if (it.id == id) transform(it) else it } }

    fun cancel(id: String) {
        val task = get(id) ?: return
        if (!task.status.isActive) return
        update(id) { it.copy(status = Status.CANCELLED, detail = "Cancelled") }
        if (task.status == Status.RUNNING) YoutubeDL.destroyProcessById(id)
    }

    fun checkNotCancelled(id: String) {
        if (get(id)?.status == Status.CANCELLED) throw CancelledByUser()
    }

    fun retry(context: Context, id: String) {
        update(id) {
            it.copy(status = Status.QUEUED, progress = 0f, detail = "", error = null, skippedItems = 0, output = null)
        }
        DownloadService.start(context)
    }

    fun remove(id: String) = _tasks.update { list -> list.filterNot { it.id == id && !it.status.isActive } }

    fun clearFinished() = _tasks.update { list -> list.filter { it.status.isActive } }
}
