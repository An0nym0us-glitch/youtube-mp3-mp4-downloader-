package com.ytdownloader.app

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Copies finished files into the public Download folder, either packed in one ZIP or as loose files. */
object Saver {
    private const val ZIP_MIME = "application/zip"

    fun save(context: Context, task: DownloadTask, files: List<File>, onStep: (String) -> Unit): SavedOutput {
        val folder = cleanFolder(task.folderName)
        val location = "${Environment.DIRECTORY_DOWNLOADS}/$folder"
        if (task.zipOutput) {
            val zipName = "${cleanName(task.title)} (${task.format.label}).zip"
            val uri = writeDownload(context, zipName, ZIP_MIME, folder) { out ->
                ZipOutputStream(out.buffered()).use { zip ->
                    // MP3/MP4 are already compressed; storing them keeps zipping fast.
                    zip.setLevel(Deflater.NO_COMPRESSION)
                    files.forEachIndexed { i, file ->
                        DownloadQueue.checkNotCancelled(task.id)
                        onStep("Packing ZIP ${i + 1}/${files.size}")
                        zip.putNextEntry(ZipEntry(file.name))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            return SavedOutput(uri, zipName, ZIP_MIME, location, files.size)
        }
        var last: Uri? = null
        files.forEachIndexed { i, file ->
            DownloadQueue.checkNotCancelled(task.id)
            onStep("Saving ${i + 1}/${files.size}")
            last = writeDownload(context, file.name, task.format.mime, folder) { out ->
                file.inputStream().use { it.copyTo(out) }
            }
        }
        val single = files.size == 1
        return SavedOutput(
            uri = if (single) last else null,
            name = if (single) files.first().name else "${files.size} files",
            mime = task.format.mime,
            location = location,
            fileCount = files.size,
        )
    }

    private fun writeDownload(
        context: Context,
        displayName: String,
        mime: String,
        folder: String,
        write: (OutputStream) -> Unit,
    ): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$folder")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("Couldn't create $displayName in Downloads")
            try {
                val out = resolver.openOutputStream(uri) ?: throw IOException("Couldn't write $displayName")
                out.use(write)
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (e: Throwable) {
                resolver.delete(uri, null, null)
                throw e
            }
            return uri
        }

        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), folder)
        if (!dir.isDirectory && !dir.mkdirs()) {
            throw IOException("Can't write to ${dir.path}. Allow the storage permission and try again.")
        }
        val target = uniqueFile(dir, displayName)
        try {
            target.outputStream().use(write)
        } catch (e: Throwable) {
            target.delete()
            throw e
        }
        MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf(mime), null)
        return FileProvider.getUriForFile(context, "${context.packageName}.files", target)
    }

    private fun uniqueFile(dir: File, name: String): File {
        var file = File(dir, name)
        var n = 1
        while (file.exists()) {
            file = File(dir, "${name.substringBeforeLast('.')} ($n).${name.substringAfterLast('.')}")
            n++
        }
        return file
    }

    private val illegalChars = Regex("""[\\/:*?"<>|\p{Cntrl}]""")

    fun cleanName(name: String): String =
        name.replace(illegalChars, "_").trim().trim('.').take(120).ifBlank { "download" }

    /** Allows nested folders like "Music/YT" but nothing that escapes Download/. */
    fun cleanFolder(folder: String): String =
        folder.split('/')
            .filter { it.trim().trim('.').isNotEmpty() }
            .joinToString("/") { cleanName(it) }
            .ifBlank { Options.DEFAULT_FOLDER }
}
