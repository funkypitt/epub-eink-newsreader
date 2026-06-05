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
import ua.acclorite.book_story.data.remote.KDrivePublicShareClient
import ua.acclorite.book_story.data.remote.WebDavClient
import ua.acclorite.book_story.data.remote.WebDavFile
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
    private val kdriveClient: KDrivePublicShareClient,
    private val settingsManager: SettingsManager,
    private val bookRepository: BookRepository,
    private val getBookFromFile: GetBookFromFileUseCase,
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
            if (result.isSuccess && importDownloadedEpub(destination)) {
                downloaded++
            } else {
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
            if (result.isSuccess && importDownloadedEpub(destination)) {
                downloaded++
            } else {
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
        val localFiles = syncDir.listFiles()?.map { it.name.lowercase() }?.toSet() ?: emptySet()
        return existingNames + localFiles
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
