/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ua.acclorite.book_story.data.settings.SettingsManager
import ua.acclorite.book_story.domain.use_case.sync.SyncFromKDriveUseCase
import ua.acclorite.book_story.presentation.library.LibraryScreen
import javax.inject.Inject

data class SyncState(
    val isSyncing: Boolean = false,
    val lastResultMessage: String? = null
)

@HiltViewModel
class SyncModel @Inject constructor(
    private val syncFromKDrive: SyncFromKDriveUseCase,
    private val settingsManager: SettingsManager
) : ViewModel() {

    private val _state = MutableStateFlow(SyncState())
    val state = _state.asStateFlow()

    fun syncNow() {
        if (_state.value.isSyncing) return

        viewModelScope.launch {
            _state.update { it.copy(isSyncing = true, lastResultMessage = null) }

            syncFromKDrive().fold(
                onSuccess = { result ->
                    settingsManager.kdriveSyncLastTime.update(System.currentTimeMillis())
                    if (result.downloaded > 0) {
                        LibraryScreen.refreshListChannel.trySend(0)
                    }
                    _state.update {
                        it.copy(
                            isSyncing = false,
                            lastResultMessage = "${result.downloaded} new, ${result.skipped} skipped" +
                                    if (result.failed > 0) ", ${result.failed} failed" else ""
                        )
                    }
                },
                onFailure = { e ->
                    _state.update {
                        it.copy(
                            isSyncing = false,
                            lastResultMessage = "Failed: ${e.message}"
                        )
                    }
                }
            )
        }
    }
}
