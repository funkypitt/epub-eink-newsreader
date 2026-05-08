/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.magazine

import android.content.Context
import android.os.Parcelable
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import kotlinx.parcelize.Parcelize
import okio.Buffer
import java.io.File
import java.util.zip.ZipFile

/**
 * Coil model + fetcher for images packaged inside a magazine ePub.
 *
 * Reads the requested entry from the zip on demand instead of extracting the
 * whole archive to disk. Plug into a Coil [ImageLoader] via [Factory].
 */
@Parcelize
data class EpubAssetKey(
    val epubPath: String,
    val opfDir: String,
    val href: String,
) : Parcelable

class EpubAssetFetcher(
    private val key: EpubAssetKey,
    private val context: Context,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val file = File(key.epubPath)
        ZipFile(file).use { zip ->
            val zipPath = if (key.opfDir.isEmpty()) key.href else "${key.opfDir}/${key.href}"
            val entry = zip.getEntry(zipPath) ?: error("Asset not found: $zipPath")
            val bytes = zip.getInputStream(entry).use { it.readBytes() }
            return SourceResult(
                source = ImageSource(Buffer().apply { write(bytes) }, context),
                mimeType = guessMimeType(key.href),
                dataSource = DataSource.DISK,
            )
        }
    }

    class Factory : Fetcher.Factory<EpubAssetKey> {
        override fun create(
            data: EpubAssetKey,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher = EpubAssetFetcher(data, options.context)
    }

    private fun guessMimeType(href: String): String? = when (href.substringAfterLast('.').lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        else -> null
    }
}
