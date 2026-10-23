package com.ytdownloader.app

import android.content.Context
import android.net.Uri
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

object Links {
    private val urlRegex = Regex("""https?://\S+""")

    /** Pulls the link out of shared text such as "Check this out https://youtu.be/…". */
    fun extract(text: String): String? {
        val t = text.trim()
        urlRegex.find(t)?.let { return it.value.trimEnd('.', ',', ')', '"', '\'') }
        return if (t.isNotEmpty() && ' ' !in t && '.' in t) "https://$t" else null
    }

    /** A watch link that also carries &list=… could mean either the one video or the whole playlist. */
    fun hasVideoAndPlaylist(url: String): Boolean = runCatching {
        val uri = Uri.parse(url)
        uri.getQueryParameter("list") != null &&
            (uri.getQueryParameter("v") != null || uri.host.orEmpty().endsWith("youtu.be"))
    }.getOrDefault(false)
}

object InfoFetcher {
    suspend fun fetch(context: Context, url: String, singleOnly: Boolean): MediaInfo = withContext(Dispatchers.IO) {
        Engine.awaitReady()
        // -J prints one JSON document for a video or a whole playlist; --flat-playlist keeps
        // playlists fast by not resolving every entry.
        val request = YoutubeDLRequest(url)
            .addOption("-J")
            .addOption("--flat-playlist")
            .addOption(if (singleOnly) "--no-playlist" else "--yes-playlist")
        Cookies.applyTo(request, context, Settings.current.extraArgs)
        val out = YoutubeDL.execute(request, null, false, null).out
        parse(url, JSONObject(out))
    }

    private fun parse(url: String, json: JSONObject): MediaInfo {
        val entries = json.optJSONArray("entries")
        if (json.str("_type") == "playlist" || entries != null) {
            val list = (0 until (entries?.length() ?: 0)).mapNotNull { i ->
                val e = entries!!.optJSONObject(i) ?: return@mapNotNull null
                MediaEntry(index = i + 1, title = e.str("title") ?: e.str("id") ?: "Item ${i + 1}", durationSec = e.seconds("duration"))
            }
            val firstEntry = entries?.optJSONObject(0)
            return MediaInfo(
                url = url,
                title = json.str("title") ?: "Playlist",
                uploader = json.str("uploader") ?: json.str("channel"),
                thumbnail = json.thumbnail() ?: firstEntry?.thumbnail(),
                durationSec = null,
                isPlaylist = true,
                entries = list,
                videoHeights = emptyList(),
            )
        }
        val formats = json.optJSONArray("formats")
        val heights = (0 until (formats?.length() ?: 0))
            .mapNotNull { formats!!.optJSONObject(it) }
            .filter { it.str("vcodec") != "none" }
            .map { it.optInt("height", 0) }
            .filter { it > 0 }
            .distinct()
            .sortedDescending()
        return MediaInfo(
            url = url,
            title = json.str("title") ?: "Video",
            uploader = json.str("uploader") ?: json.str("channel"),
            thumbnail = json.thumbnail(),
            durationSec = json.seconds("duration"),
            isPlaylist = false,
            entries = emptyList(),
            videoHeights = heights,
        )
    }

    private fun JSONObject.str(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun JSONObject.seconds(key: String): Int? =
        optDouble(key).takeIf { !it.isNaN() && it > 0 }?.toInt()

    private fun JSONObject.thumbnail(): String? {
        str("thumbnail")?.let { return it }
        val thumbs = optJSONArray("thumbnails") ?: return null
        // yt-dlp lists thumbnails from worst to best.
        for (i in thumbs.length() - 1 downTo 0) {
            thumbs.optJSONObject(i)?.str("url")?.let { return it }
        }
        return null
    }
}
