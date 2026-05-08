/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ua.acclorite.book_story.domain.use_case.permission.GrantPersistableUriPermissionUseCase
import ua.acclorite.book_story.domain.use_case.permission.ReleasePersistableUriPermissionUseCase
import ua.acclorite.book_story.domain.use_case.settings.UpdateLanguageUseCase
import javax.inject.Inject

@HiltViewModel
class SettingsModel @Inject constructor(
    private val updateLanguageUseCase: UpdateLanguageUseCase,
    private val grantPersistableUriPermissionUseCase: GrantPersistableUriPermissionUseCase,
    private val releasePersistableUriPermissionUseCase: ReleasePersistableUriPermissionUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsState())
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<SettingsEffect>()
    val effects = _effects.asSharedFlow()

    private val _initialized = MutableStateFlow(true)
    val initialized = _initialized.asStateFlow()

    fun onEvent(event: SettingsEvent) {
        viewModelScope.launch {
            when (event) {
                is SettingsEvent.OnUpdateLanguage -> {
                    updateLanguageUseCase(event.language)
                }

                is SettingsEvent.OnGrantPersistableUriPermission -> {
                    grantPersistableUriPermissionUseCase(event.uri)
                }

                is SettingsEvent.OnReleasePersistableUriPermission -> {
                    releasePersistableUriPermissionUseCase(event.uri)
                }
            }
        }
    }
}
