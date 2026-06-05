/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.repository

import android.app.Application
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.data.remote.WebDavClient
import ua.acclorite.book_story.data.settings.SettingsManager
import ua.acclorite.book_story.domain.model.file.File as DomainFile
import ua.acclorite.book_story.domain.repository.BookRepository
import ua.acclorite.book_story.domain.repository.SyncRepository
import ua.acclorite.book_story.domain.repository.SyncResult
import ua.acclorite.book_story.domain.use_case.book.AddBookUseCase
import ua.acclorite.book_story.domain.use_case.file_system.GetBookFromFileUseCase
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SyncRepository"

@Singleton
class SyncRepositoryImpl @Inject constructor(
    private val application: Application,
    private val webDavClient: WebDavClient,
    private val settingsManager: SettingsManager,
    private val bookRepository: BookRepository,
    private val getBookFromFile: GetBookFromFileUseCase,
    private val addBook: AddBookUseCase
) : SyncRepository {

    private val syncDir: File
        get() = File(application.filesDir, "synced").apply { mkdirs() }

    override suspend fun syncFromWebDav(): Result<SyncResult> = withContext(Dispatchers.IO) {
        runCatching {
            val url = settingsManager.kdriveSyncUrl.lastValue
            val username = settingsManager.kdriveSyncUsername.lastValue
            val password = settingsManager.kdriveSyncPassword.lastValue

            if (url.isBlank() || username.isBlank() || password.isBlank()) {
                throw IllegalStateException("WebDAV credentials not configured")
            }

            logI(TAG, "Starting sync from $url")

            val remoteFiles = webDavClient.listFiles(url, username, password).getOrThrow()
            val epubs = remoteFiles.filter {
                !it.isDirectory
                        && it.name.endsWith(".epub", ignoreCase = true)
                        && !it.name.contains("_blacklisted_")
            }

            logI(TAG, "Found ${epubs.size} remote EPUBs")

            val existingBooks = bookRepository.searchBooks("").getOrDefault(emptyList())
            val existingNames = existingBooks.map { book ->
                book.filePath.substringAfterLast('/').lowercase()
            }.toSet()

            val localFiles = syncDir.listFiles()?.map { it.name.lowercase() }?.toSet() ?: emptySet()
            val knownNames = existingNames + localFiles

            val newEpubs = epubs.filter { remote ->
                remote.name.lowercase() !in knownNames
            }

            logI(TAG, "${newEpubs.size} new EPUBs to download")

            var downloaded = 0
            var failed = 0

            for (remote in newEpubs) {
                val destination = File(syncDir, remote.name)
                val result = webDavClient.downloadFile(
                    remote.href, username, password, destination
                )

                if (result.isSuccess) {
                    val importResult = importDownloadedEpub(destination)
                    if (importResult) {
                        downloaded++
                    } else {
                        failed++
                    }
                } else {
                    failed++
                }
            }

            logI(TAG, "Sync complete: $downloaded downloaded, ${epubs.size - newEpubs.size} skipped, $failed failed")

            SyncResult(
                downloaded = downloaded,
                skipped = epubs.size - newEpubs.size,
                failed = failed
            )
        }.also {
            it.onFailure { e -> logE(TAG, "Sync failed: ${e.message}") }
        }
    }

    private suspend fun importDownloadedEpub(file: File): Boolean {
        val domainFile = DomainFile(
            name = file.name,
            uri = Uri.fromFile(file).toString(),
            path = file.absolutePath,
            size = file.length(),
            lastModified = file.lastModified(),
            isDirectory = false
        )
        val parsed = getBookFromFile(domainFile) ?: return false
        addBook(parsed.first, parsed.second)
        return true
    }
}
