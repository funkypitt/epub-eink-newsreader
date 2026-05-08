/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.magazine

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile

/**
 * Resolves images, CSS, and other assets referenced by a chapter directly
 * from the magazine ePub zip — no extraction to disk.
 *
 * Pair with `WebView.loadDataWithBaseURL("$BASE_URL/$opfDir/", chapterHtml, ...)`
 * so relative hrefs in the chapter ("img-001.jpg", "style.css") resolve under
 * [BASE_URL] and land in [shouldInterceptRequest].
 */
class MagazineWebViewClient(
    epubFile: File,
    private val opfDir: String,
) : WebViewClient() {

    private val zip: ZipFile = ZipFile(epubFile)
    private val zipLock = Any()

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest,
    ): WebResourceResponse? {
        val uri = request.url ?: return null
        if (uri.host != HOST) return null

        val zipPath = uri.path.orEmpty().removePrefix("/").ifBlank { return null }
        return try {
            synchronized(zipLock) {
                val entry = zip.getEntry(zipPath) ?: return null
                val bytes = zip.getInputStream(entry).use { it.readBytes() }
                WebResourceResponse(
                    guessMimeType(zipPath),
                    "UTF-8",
                    ByteArrayInputStream(bytes),
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    fun close() {
        try {
            zip.close()
        } catch (_: Exception) {
            // best effort
        }
    }

    private fun guessMimeType(path: String): String =
        when (path.substringAfterLast('.').lowercase()) {
            "html", "htm", "xhtml" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "woff" -> "font/woff"
            "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            else -> "application/octet-stream"
        }

    companion object {
        const val SCHEME = "https"
        const val HOST = "magazine.local"
        const val BASE_URL = "$SCHEME://$HOST"
    }
}
