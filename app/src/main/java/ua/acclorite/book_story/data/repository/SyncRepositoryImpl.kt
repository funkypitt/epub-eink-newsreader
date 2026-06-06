/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.repository

import android.app.Application
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import ua.acclorite.book_story.R
import ua.acclorite.book_story.core.CoverImage
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.core.ui.UIText
import ua.acclorite.book_story.data.remote.KDrivePublicShareClient
import ua.acclorite.book_story.data.remote.WebDavClient
import ua.acclorite.book_story.data.remote.WebDavFile
import ua.acclorite.book_story.data.settings.SettingsManager
import ua.acclorite.book_story.domain.model.library.Book
import ua.acclorite.book_story.domain.repository.BookRepository
import ua.acclorite.book_story.domain.repository.SyncRepository
import ua.acclorite.book_story.domain.repository.SyncResult
import ua.acclorite.book_story.domain.use_case.book.AddBookUseCase
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SyncRepository"

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val application: Application,
    private val webDavClient: WebDavClient,
    private val kdriveClient: KDrivePublicShareClient,
    private val settingsManager: SettingsManager,
    private val bookRepository: BookRepository,
    private val addBook: AddBookUseCase
) : SyncRepository {

    private val syncDir: File
        get() = File(application.filesDir, "synced").apply { mkdirs() }

    override suspend fun syncFromWebDav(): Result<SyncResult> = withContext(Dispatchers.IO) {
        runCatching {
            val shareLink = settingsManager.kdriveShareLink.lastValue
            if (shareLink.isNotBlank()) {
                syncViaPublicShare(shareLink)
            } else {
                syncViaWebDav()
            }
        }.also {
            it.onFailure { e -> logE(TAG, "Sync failed: ${e.message}") }
        }
    }

    private suspend fun syncViaPublicShare(shareLink: String): SyncResult {
        val (driveId, linkUuid) = kdriveClient.parseShareUrl(shareLink)
            ?: throw IllegalStateException("Invalid kDrive share link")

        logI(TAG, "Starting sync via public share: driveId=$driveId")

        val config = kdriveClient.init(driveId, linkUuid).getOrThrow()
        val remoteFiles = kdriveClient.listFiles(config).getOrThrow()
        val epubs = filterEpubs(remoteFiles)

        logI(TAG, "Found ${epubs.size} remote EPUBs")

        val knownNames = getKnownNames()
        val newEpubs = epubs.filter { it.name.lowercase() !in knownNames }

        logI(TAG, "${newEpubs.size} new EPUBs to download")

        var downloaded = 0
        var failed = 0

        for (remote in newEpubs) {
            val destination = File(syncDir, remote.name)
            val result = kdriveClient.downloadFile(config, remote.href, destination)
            if (result.isSuccess && importEpubFile(destination)) {
                downloaded++
            } else {
                destination.delete()
                failed++
            }
        }

        logI(TAG, "Sync complete: $downloaded downloaded, ${epubs.size - newEpubs.size} skipped, $failed failed")
        return SyncResult(downloaded, epubs.size - newEpubs.size, failed)
    }

    private suspend fun syncViaWebDav(): SyncResult {
        val url = settingsManager.kdriveSyncUrl.lastValue
        val username = settingsManager.kdriveSyncUsername.lastValue
        val password = settingsManager.kdriveSyncPassword.lastValue

        if (url.isBlank() || username.isBlank() || password.isBlank()) {
            throw IllegalStateException("WebDAV credentials not configured")
        }

        logI(TAG, "Starting sync from $url")

        val remoteFiles = webDavClient.listFiles(url, username, password).getOrThrow()
        val epubs = filterEpubs(remoteFiles)

        logI(TAG, "Found ${epubs.size} remote EPUBs")

        val knownNames = getKnownNames()
        val newEpubs = epubs.filter { it.name.lowercase() !in knownNames }

        logI(TAG, "${newEpubs.size} new EPUBs to download")

        var downloaded = 0
        var failed = 0

        for (remote in newEpubs) {
            val destination = File(syncDir, remote.name)
            val result = webDavClient.downloadFile(remote.href, username, password, destination)
            if (result.isSuccess && importEpubFile(destination)) {
                downloaded++
            } else {
                destination.delete()
                failed++
            }
        }

        logI(TAG, "Sync complete: $downloaded downloaded, ${epubs.size - newEpubs.size} skipped, $failed failed")
        return SyncResult(downloaded, epubs.size - newEpubs.size, failed)
    }

    private fun filterEpubs(files: List<WebDavFile>): List<WebDavFile> = files.filter {
        !it.isDirectory
                && it.name.endsWith(".epub", ignoreCase = true)
                && !it.name.contains("_blacklisted_")
    }

    private suspend fun getKnownNames(): Set<String> {
        val existingBooks = bookRepository.searchBooks("").getOrDefault(emptyList())
        val existingNames = existingBooks.map { it.filePath.substringAfterLast('/').lowercase() }.toSet()
        return existingNames
    }

    /**
     * Parse EPUB metadata and cover directly via ZipFile, bypassing the
     * CachedFile/ContentResolver path which fails for file:// URIs.
     */
    private suspend fun importEpubFile(file: File): Boolean {
        return try {
            val (book, cover) = withContext(Dispatchers.IO) { parseEpubDirect(file) }
                ?: return false
            addBook(book, cover)
            logI(TAG, "Imported ${file.name}")
            true
        } catch (e: Exception) {
            logE(TAG, "Import failed for ${file.name}: ${e.message}")
            false
        }
    }

    private fun parseEpubDirect(file: File): Pair<Book, CoverImage?>? {
        if (!file.exists() || !file.canRead()) return null

        return ZipFile(file).use { zip ->
            val opfEntry = zip.entries().asSequence().find { entry ->
                entry.name.endsWith(".opf", ignoreCase = true)
            } ?: return null

            val opfContent = zip.getInputStream(opfEntry).bufferedReader().use { it.readText() }
            val doc = Jsoup.parse(opfContent, Parser.xmlParser())

            val title = doc.select("metadata > dc|title").text().trim().ifBlank {
                file.nameWithoutExtension
            }
            val author = doc.select("metadata > dc|creator").text().trim().let {
                if (it.isBlank()) UIText.StringResource(R.string.unknown_author)
                else UIText.StringValue(it)
            }
            val description = Jsoup.parse(
                doc.select("metadata > dc|description").text()
            ).text().ifBlank { null }

            val book = Book(
                title = title,
                author = author,
                description = description,
                scrollIndex = 0,
                scrollOffset = 0,
                progress = 0f,
                filePath = file.absolutePath,
                lastOpened = null,
                coverImage = null
            )

            val cover = extractCover(zip, doc)
            book to cover
        }
    }

    private fun extractCover(zip: ZipFile, doc: org.jsoup.nodes.Document): CoverImage? {
        val coverHref = doc.select("metadata > meta[name=cover]").attr("content").let { coverId ->
            if (coverId.isNotBlank()) {
                doc.select("manifest > item[id=$coverId]").attr("href").takeIf { it.isNotBlank() }
            } else null
        } ?: doc.select("manifest > item[media-type*=image]").firstOrNull()?.attr("href")
        ?: return null

        val decoded = URLDecoder.decode(coverHref, StandardCharsets.UTF_8.name())
        val entry = zip.entries().asSequence().firstOrNull { it.name.endsWith(decoded) }
            ?: return null
        val bytes = zip.getInputStream(entry).readBytes()
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}
