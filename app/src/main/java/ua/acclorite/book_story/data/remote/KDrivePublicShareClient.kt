/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import okhttp3.OkHttpClient
import okhttp3.Request
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.logI
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "KDrivePublicShare"
private const val BASE = "https://kdrive.infomaniak.com"

data class KDriveShareConfig(
    val driveId: String,
    val linkUuid: String,
    val folderId: Int
)

@Singleton
class KDrivePublicShareClient @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    fun parseShareUrl(url: String): Pair<String, String>? {
        val regex = Regex("""/app/share/(\d+)/([a-z0-9-]+)""")
        val match = regex.find(url) ?: return null
        return match.groupValues[1] to match.groupValues[2]
    }

    suspend fun init(driveId: String, linkUuid: String): Result<KDriveShareConfig> =
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("$BASE/2/app/$driveId/share/$linkUuid/init")
                    .get()
                    .build()
                val body = executeAndRead(request)
                val root = json.parseToJsonElement(body).jsonObject
                val data = root["data"]?.jsonObject
                    ?: throw IOException("No data in init response")
                val right = data["right"]?.jsonPrimitive?.content
                if (right != "public") throw IOException("Share is not public (right=$right)")
                val folderId = data["file_id"]?.jsonPrimitive?.int
                    ?: throw IOException("No file_id in init response")
                logI(TAG, "Init OK: driveId=$driveId folderId=$folderId")
                KDriveShareConfig(driveId, linkUuid, folderId)
            }.also {
                it.onFailure { e -> logE(TAG, "init failed: ${e.message}") }
            }
        }

    suspend fun listFiles(
        config: KDriveShareConfig
    ): Result<List<WebDavFile>> = withContext(Dispatchers.IO) {
        runCatching {
            val allFiles = mutableListOf<WebDavFile>()
            var cursor: String? = null
            var hasMore = true

            while (hasMore) {
                val url = buildString {
                    append("$BASE/3/app/${config.driveId}/share/${config.linkUuid}")
                    append("/files/${config.folderId}/files")
                    append("?order_by=last_modified_at&order=desc&limit=50")
                    if (cursor != null) append("&cursor=$cursor")
                }
                val request = Request.Builder().url(url).get().build()
                val body = executeAndRead(request)
                val root = json.parseToJsonElement(body).jsonObject
                val dataArray = root["data"]?.jsonArray
                    ?: throw IOException("No data array in list response")

                for (item in dataArray) {
                    val obj = item.jsonObject
                    val name = obj["name"]?.jsonPrimitive?.content ?: continue
                    val fileId = obj["id"]?.jsonPrimitive?.int ?: continue
                    val size = obj["size"]?.jsonPrimitive?.long ?: 0L
                    val type = obj["type"]?.jsonPrimitive?.content ?: "file"
                    allFiles.add(
                        WebDavFile(
                            name = name,
                            href = fileId.toString(),
                            size = size,
                            isDirectory = type == "dir"
                        )
                    )
                }

                hasMore = root["has_more"]?.jsonPrimitive?.boolean ?: false
                cursor = root["cursor"]?.jsonPrimitive?.content
                if (cursor == null) hasMore = false
            }

            logI(TAG, "Listed ${allFiles.size} files")
            allFiles
        }.also {
            it.onFailure { e -> logE(TAG, "listFiles failed: ${e.message}") }
        }
    }

    suspend fun downloadFile(
        config: KDriveShareConfig,
        fileId: String,
        destination: File
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "$BASE/2/app/${config.driveId}/share/${config.linkUuid}/files/$fileId/download"
            val request = Request.Builder().url(url).get().build()
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

    private fun executeAndRead(request: Request): String {
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            throw IOException("HTTP ${response.code}: ${response.message}")
        }
        return response.body?.string() ?: throw IOException("Empty response body")
    }
}
