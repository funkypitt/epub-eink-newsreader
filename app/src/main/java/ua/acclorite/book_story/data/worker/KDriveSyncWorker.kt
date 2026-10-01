/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.core.log.logI
import java.util.concurrent.TimeUnit

private const val TAG = "KDriveSyncWorker"
private const val WORK_NAME = "kdrive_sync"

@HiltWorker
class KDriveSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val autoSync: KDriveAutoSync,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        logI(TAG, "Background sync started")

        val outcome = autoSync.run() ?: return Result.success() // one already running
        return outcome.fold(
            onSuccess = { result ->
                logI(TAG, "Background sync complete: ${result.downloaded} new")
                Result.success()
            },
            onFailure = { e ->
                logE(TAG, "Background sync failed: ${e.message}")
                Result.retry()
            }
        )
    }

    companion object {
        /** Settings changed: (re)schedule with the new interval. */
        fun schedule(context: Context, intervalHours: Long) =
            enqueue(context, intervalHours, ExistingPeriodicWorkPolicy.UPDATE)

        /**
         * App start: make sure the periodic sync exists (it is lost on a fresh
         * install or a data clear) without restarting its timer.
         */
        fun ensureScheduled(context: Context, intervalHours: Long) =
            enqueue(context, intervalHours, ExistingPeriodicWorkPolicy.KEEP)

        private fun enqueue(context: Context, intervalHours: Long, policy: ExistingPeriodicWorkPolicy) {
            val request = PeriodicWorkRequestBuilder<KDriveSyncWorker>(
                intervalHours, TimeUnit.HOURS
            ).setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, policy, request)
            logI(TAG, "Periodic sync every ${intervalHours}h ($policy)")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            logI(TAG, "Cancelled periodic sync")
        }
    }
}
