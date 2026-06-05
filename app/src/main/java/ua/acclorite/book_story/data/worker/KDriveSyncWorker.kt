/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.domain.use_case.sync.SyncFromKDriveUseCase
import ua.acclorite.book_story.presentation.library.LibraryScreen
import java.util.concurrent.TimeUnit

private const val TAG = "KDriveSyncWorker"
private const val WORK_NAME = "kdrive_sync"

@HiltWorker
class KDriveSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncFromKDrive: SyncFromKDriveUseCase,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        logI(TAG, "Background sync started")

        return syncFromKDrive().fold(
            onSuccess = { result ->
                logI(TAG, "Background sync complete: ${result.downloaded} new")
                if (result.downloaded > 0) {
                    LibraryScreen.refreshListChannel.trySend(0)
                }
                Result.success()
            },
            onFailure = { e ->
                logE(TAG, "Background sync failed: ${e.message}")
                Result.retry()
            }
        )
    }

    companion object {
        fun schedule(context: Context, intervalHours: Long) {
            val request = PeriodicWorkRequestBuilder<KDriveSyncWorker>(
                intervalHours, TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            logI(TAG, "Scheduled periodic sync every ${intervalHours}h")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            logI(TAG, "Cancelled periodic sync")
        }
    }
}
