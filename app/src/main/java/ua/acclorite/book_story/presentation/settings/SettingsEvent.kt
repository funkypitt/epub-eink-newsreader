/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.settings

import androidx.compose.runtime.Immutable
import ua.acclorite.book_story.core.language.Language

@Immutable
sealed class SettingsEvent {
    data class OnUpdateLanguage(
        val language: Language
    ) : SettingsEvent()

    data class OnGrantPersistableUriPermission(
        val uri: String
    ) : SettingsEvent()

    data class OnReleasePersistableUriPermission(
        val uri: String
    ) : SettingsEvent()
}
