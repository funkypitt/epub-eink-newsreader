/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.worker

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.data.settings.SettingsManager
import ua.acclorite.book_story.domain.repository.SyncResult
import ua.acclorite.book_story.domain.use_case.sync.SyncFromKDriveUseCase
import ua.acclorite.book_story.presentation.library.LibraryScreen
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "KDriveAutoSync"

/** Opening the app syncs again only if the last sync is older than this. */
private const val OPENING_MIN_GAP_MS = 2 * 60 * 1000L

/**
 * The one place a kDrive sync runs from: background worker, app opening,
 * pull-to-refresh. Never two at once.
 */
@Singleton
class KDriveAutoSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncFromKDrive: SyncFromKDriveUseCase,
    private val settings: SettingsManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    /** Failures of a sync the user asked for (pull-to-refresh), to be shown. */
    private val _failures = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val failures = _failures.asSharedFlow()

    /**
     * App opened: make sure the background sync is scheduled, then sync if
     * automatic sync is on and the last one is not recent.
     */
    fun onAppOpened() {
        scope.launch {
            settings.initialized.first { it }
            if (!settings.kdriveSyncEnabled.lastValue || !isConfigured()) return@launch
            val hours = settings.kdriveSyncIntervalHours.lastValue.toLong()
            if (hours > 0) KDriveSyncWorker.ensureScheduled(context, hours)
            val age = System.currentTimeMillis() - settings.kdriveSyncLastTime.lastValue
            if (age < OPENING_MIN_GAP_MS) return@launch
            run()
        }
    }

    /** Pull-to-refresh: sync now whenever a server or share link is set. */
    fun onPullToRefresh() {
        scope.launch {
            settings.initialized.first { it }
            if (!isConfigured()) return@launch
            run()?.onFailure { _failures.tryEmit(it.message ?: "") }
        }
    }

    /** Background worker. Null when another sync is already running. */
    suspend fun run(): Result<SyncResult>? {
        if (!mutex.tryLock()) {
            logI(TAG, "Sync already running, skipped.")
            return null
        }
        _isSyncing.value = true
        try {
            return syncFromKDrive().onSuccess { result ->
                settings.kdriveSyncLastTime.update(System.currentTimeMillis())
                if (result.downloaded > 0) LibraryScreen.refreshListChannel.trySend(0)
            }
        } finally {
            _isSyncing.value = false
            mutex.unlock()
        }
    }

    private fun isConfigured() =
        settings.kdriveShareLink.lastValue.isNotBlank() || settings.kdriveSyncUrl.lastValue.isNotBlank()
}
