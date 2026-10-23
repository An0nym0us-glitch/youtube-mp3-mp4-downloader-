package com.ytdownloader.app.ui

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.ytdownloader.app.SavedOutput

object Intents {
    fun open(context: Context, output: SavedOutput) {
        val uri = output.uri ?: return openDownloads(context)
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, output.mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(view)
        } catch (e: ActivityNotFoundException) {
            // Not every phone has a ZIP viewer; the Downloads app always exists.
            openDownloads(context)
        }
    }

    fun share(context: Context, output: SavedOutput) {
        val uri = output.uri ?: return
        val send = Intent(Intent.ACTION_SEND)
            .setType(output.mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(send, output.name))
    }

    fun openDownloads(context: Context) {
        try {
            context.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Open your Files app → Download", Toast.LENGTH_LONG).show()
        }
    }
}
