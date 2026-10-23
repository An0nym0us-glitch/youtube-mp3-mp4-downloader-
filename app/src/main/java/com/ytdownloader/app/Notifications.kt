package com.ytdownloader.app

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifications {
    const val PROGRESS_ID = 1
    private const val CHANNEL_PROGRESS = "progress"
    private const val CHANNEL_FINISHED = "finished"

    fun createChannels(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannelsCompat(
            listOf(
                NotificationChannelCompat.Builder(CHANNEL_PROGRESS, NotificationManagerCompat.IMPORTANCE_LOW)
                    .setName("Download progress")
                    .build(),
                NotificationChannelCompat.Builder(CHANNEL_FINISHED, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName("Finished downloads")
                    .build(),
            )
        )
    }

    fun progress(context: Context, tasks: List<DownloadTask>): Notification {
        val running = tasks.firstOrNull { it.status == Status.RUNNING }
        val queued = tasks.count { it.status == Status.QUEUED }
        val text = listOfNotNull(running?.detail?.takeIf { it.isNotEmpty() }, if (queued > 0) "$queued queued" else null)
        return NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(running?.title ?: "Preparing downloads…")
            .setContentText(text.joinToString(" · "))
            .setProgress(100, ((running?.progress ?: 0f) * 100).toInt(), running == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp(context))
            .apply { if (running != null) addAction(0, "Cancel", DownloadService.cancelIntent(context, running.id)) }
            .build()
    }

    fun updateProgress(context: Context, tasks: List<DownloadTask>) {
        if (!canNotify(context)) return
        NotificationManagerCompat.from(context).notify(PROGRESS_ID, progress(context, tasks))
    }

    fun finished(context: Context, task: DownloadTask) {
        val (title, text) = when (task.status) {
            Status.DONE -> "Downloaded ${task.format.label}" to "${task.title}\nSaved to ${task.output?.location}"
            Status.FAILED -> "Download failed" to "${task.title}\n${task.error}"
            else -> return
        }
        if (!canNotify(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_FINISHED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(task.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        NotificationManagerCompat.from(context).notify(task.id.hashCode(), notification)
    }

    private fun canNotify(context: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )
}
