package com.ytdownloader.app

import android.content.Context
import android.webkit.CookieManager
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * YouTube cookies captured from the in-app sign-in, written as a Netscape cookies.txt for yt-dlp.
 * Being signed in is what gets past YouTube's "Sign in to confirm you're not a bot" check.
 */
object Cookies {
    private const val YOUTUBE_URL = "https://www.youtube.com"
    private val LOGIN_COOKIES = setOf("SAPISID", "__Secure-3PAPISID", "LOGIN_INFO", "SID")

    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    private fun file(context: Context) = File(context.applicationContext.filesDir, "cookies.txt")

    fun init(context: Context) {
        _signedIn.value = file(context).isFile
    }

    /** Saves the WebView's YouTube cookies. Returns false if the user hasn't actually signed in. */
    fun export(context: Context): Boolean {
        val manager = CookieManager.getInstance()
        manager.flush()
        val pairs = manager.getCookie(YOUTUBE_URL).orEmpty()
            .split(';')
            .mapNotNull { part ->
                val t = part.trim()
                val eq = t.indexOf('=')
                if (eq <= 0) null else t.substring(0, eq) to t.substring(eq + 1)
            }
        if (pairs.none { it.first in LOGIN_COOKIES }) return false
        // CookieManager doesn't expose expiry; YouTube refreshes these cookies on use anyway.
        val expiry = System.currentTimeMillis() / 1000 + 365L * 24 * 3600
        val text = buildString {
            append("# Netscape HTTP Cookie File\n")
            pairs.forEach { (name, value) -> append(".youtube.com\tTRUE\t/\tTRUE\t$expiry\t$name\t$value\n") }
        }
        file(context).writeText(text)
        _signedIn.value = true
        return true
    }

    fun signOut(context: Context) {
        file(context).delete()
        CookieManager.getInstance().apply {
            removeAllCookies(null)
            flush()
        }
        _signedIn.value = false
    }

    /** Adds the login cookies (if any) and the user's extra yt-dlp options to a request. */
    fun applyTo(request: YoutubeDLRequest, context: Context, extraArgs: String) {
        val cookies = file(context)
        if (cookies.isFile) request.addOption("--cookies", cookies.absolutePath)
        val extra = parseArgs(extraArgs)
        if (extra.isNotEmpty()) request.addCommands(extra)
    }

    private val argRegex = Regex(""""([^"]*)"|'([^']*)'|(\S+)""")

    fun parseArgs(text: String): List<String> =
        argRegex.findAll(text).map { m -> m.groupValues.drop(1).first { it.isNotEmpty() } }.toList()
}
