/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.SharedFlow
import ua.acclorite.book_story.presentation.settings.SettingsEffect

@Composable
fun SettingsEffects(effects: SharedFlow<SettingsEffect>) {
    LaunchedEffect(effects) {
        effects.collect { _ ->
            // No effects to handle in magazine-only build.
        }
    }
}
