package com.ytdownloader.app

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Foreground service so downloads keep going when the app is in the background. */
class DownloadService : Service() {
    // The queue loop runs on the main thread so starting, finishing and stopSelf can't interleave.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var worker: Job? = null
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, Notifications.PROGRESS_ID, Notifications.progress(this, DownloadQueue.tasks.value), type)
        if (intent?.action == ACTION_CANCEL) {
            intent.getStringExtra(EXTRA_TASK_ID)?.let(DownloadQueue::cancel)
        }
        if (worker?.isActive != true) {
            worker = scope.launch { drainQueue() }
        }
        return START_NOT_STICKY
    }

    private suspend fun drainQueue() = coroutineScope {
        val ticker = launch {
            while (isActive) {
                Notifications.updateProgress(this@DownloadService, DownloadQueue.tasks.value)
                delay(1000)
            }
        }
        while (true) {
            val next = DownloadQueue.nextQueued() ?: break
            Downloader.run(applicationContext, next.id)
        }
        ticker.cancel()
        ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf(lastStartId)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15 caps dataSync services; stop cleanly instead of crashing.
        DownloadQueue.tasks.value.filter { it.status.isActive }.forEach { DownloadQueue.cancel(it.id) }
        stopSelf()
    }

    override fun onDestroy() {
        DownloadQueue.tasks.value.filter { it.status == Status.RUNNING }.forEach { DownloadQueue.cancel(it.id) }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_CANCEL = "com.ytdownloader.app.CANCEL"
        private const val EXTRA_TASK_ID = "task_id"

        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))

        fun cancelIntent(context: Context, taskId: String): PendingIntent = PendingIntent.getService(
            context,
            taskId.hashCode(),
            Intent(context, DownloadService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_TASK_ID, taskId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
