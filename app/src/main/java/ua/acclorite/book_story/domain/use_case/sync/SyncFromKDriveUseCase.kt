/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.domain.use_case.sync

import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.domain.repository.SyncRepository
import ua.acclorite.book_story.domain.repository.SyncResult
import javax.inject.Inject

private const val TAG = "SyncFromKDrive"

class SyncFromKDriveUseCase @Inject constructor(
    private val syncRepository: SyncRepository
) {

    suspend operator fun invoke(): Result<SyncResult> {
        logI(TAG, "Starting kDrive sync.")
        return syncRepository.syncFromWebDav().also {
            it.onSuccess { result ->
                logI(TAG, "Sync succeeded: ${result.downloaded} new, ${result.skipped} skipped, ${result.failed} failed.")
            }
            it.onFailure { e ->
                logE(TAG, "Sync failed: ${e.message}")
            }
        }
    }
}
