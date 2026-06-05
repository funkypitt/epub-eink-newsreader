/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.logI
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "WebDavClient"

data class WebDavFile(
    val name: String,
    val href: String,
    val size: Long,
    val isDirectory: Boolean
)

@Singleton
class WebDavClient @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun listFiles(
        url: String,
        username: String,
        password: String
    ): Result<List<WebDavFile>> = withContext(Dispatchers.IO) {
        runCatching {
            val normalizedUrl = url.trimEnd('/') + "/"
            val propfindBody = """
                <?xml version="1.0" encoding="utf-8"?>
                <d:propfind xmlns:d="DAV:">
                    <d:prop>
                        <d:resourcetype/>
                        <d:getcontentlength/>
                        <d:displayname/>
                    </d:prop>
                </d:propfind>
            """.trimIndent()

            val request = Request.Builder()
                .url(normalizedUrl)
                .method("PROPFIND", propfindBody.toRequestBody("application/xml".toMediaType()))
                .header("Authorization", Credentials.basic(username, password))
                .header("Depth", "1")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful && response.code != 207) {
                throw IOException("WebDAV PROPFIND failed: ${response.code} ${response.message}")
            }

            val body = response.body?.string()
                ?: throw IOException("Empty PROPFIND response")

            parseMultiStatus(body, normalizedUrl)
        }.also {
            it.onFailure { e -> logE(TAG, "listFiles failed: ${e.message}") }
            it.onSuccess { files -> logI(TAG, "Listed ${files.size} files") }
        }
    }

    suspend fun downloadFile(
        fileUrl: String,
        username: String,
        password: String,
        destination: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(fileUrl)
                .get()
                .header("Authorization", Credentials.basic(username, password))
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("Download failed: ${response.code} ${response.message}")
            }

            response.body?.byteStream()?.use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw IOException("Empty response body")

            logI(TAG, "Downloaded ${destination.name} (${destination.length()} bytes)")
            destination
        }.also {
            it.onFailure { e ->
                logE(TAG, "Download failed: ${e.message}")
                destination.delete()
            }
        }
    }

    private fun parseMultiStatus(xml: String, baseUrl: String): List<WebDavFile> {
        val doc = Jsoup.parse(xml, "", org.jsoup.parser.Parser.xmlParser())
        val responses = doc.select("d|response, D|response, response")

        return responses.mapNotNull { response ->
            val href = response.selectFirst("d|href, D|href, href")?.text() ?: return@mapNotNull null
            val isCollection = response.select(
                "d|resourcetype > d|collection, D|resourcetype > D|collection, resourcetype > collection"
            ).isNotEmpty()
            val contentLength = response.selectFirst(
                "d|getcontentlength, D|getcontentlength, getcontentlength"
            )?.text()?.toLongOrNull() ?: 0L
            val displayName = response.selectFirst(
                "d|displayname, D|displayname, displayname"
            )?.text()

            val name = displayName
                ?: href.trimEnd('/').substringAfterLast('/')
            val fullUrl = if (href.startsWith("http")) href
            else baseUrl.trimEnd('/').substringBefore(
                java.net.URI(baseUrl).path
            ) + href

            if (fullUrl.trimEnd('/') == baseUrl.trimEnd('/')) return@mapNotNull null

            WebDavFile(
                name = name,
                href = fullUrl,
                size = contentLength,
                isDirectory = isCollection
            )
        }
    }
}
