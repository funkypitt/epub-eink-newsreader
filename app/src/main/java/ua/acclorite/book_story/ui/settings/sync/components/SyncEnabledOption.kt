/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.settings.sync.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import ua.acclorite.book_story.R
import ua.acclorite.book_story.data.worker.KDriveSyncWorker
import ua.acclorite.book_story.ui.common.components.settings.SwitchWithTitle
import ua.acclorite.book_story.ui.common.helpers.LocalSettings

@Composable
fun SyncEnabledOption() {
    val settings = LocalSettings.current
    val context = LocalContext.current

    SwitchWithTitle(
        selected = settings.kdriveSyncEnabled.value,
        title = stringResource(id = R.string.sync_enabled_option),
        description = stringResource(id = R.string.sync_enabled_option_desc)
    ) {
        val newValue = !settings.kdriveSyncEnabled.lastValue
        settings.kdriveSyncEnabled.update(newValue)
        updateSyncSchedule(context, newValue, settings.kdriveSyncIntervalHours.lastValue.toLong())
    }
}

internal fun updateSyncSchedule(context: Context, enabled: Boolean, intervalHours: Long) {
    if (enabled && intervalHours > 0) {
        KDriveSyncWorker.schedule(context, intervalHours)
    } else {
        KDriveSyncWorker.cancel(context)
    }
}
