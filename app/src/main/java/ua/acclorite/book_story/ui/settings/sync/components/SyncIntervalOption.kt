/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.settings.sync.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import ua.acclorite.book_story.R
import ua.acclorite.book_story.ui.common.components.settings.SliderWithTitle
import ua.acclorite.book_story.ui.common.helpers.LocalSettings

@Composable
fun SyncIntervalOption() {
    val settings = LocalSettings.current
    val context = LocalContext.current

    SliderWithTitle(
        value = settings.kdriveSyncIntervalHours.value to "h",
        title = stringResource(id = R.string.sync_interval_option),
        fromValue = 1,
        toValue = 24
    ) { newValue ->
        settings.kdriveSyncIntervalHours.update(newValue)
        if (settings.kdriveSyncEnabled.lastValue) {
            updateSyncSchedule(context, true, newValue.toLong())
        }
    }
}
