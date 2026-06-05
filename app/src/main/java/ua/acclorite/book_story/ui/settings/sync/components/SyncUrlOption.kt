/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.settings.sync.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import ua.acclorite.book_story.R
import ua.acclorite.book_story.ui.common.components.dialog.DialogWithTextField
import ua.acclorite.book_story.ui.common.helpers.LocalSettings

@Composable
fun SyncUrlOption() {
    val settings = LocalSettings.current
    val showDialog = remember { mutableStateOf(false) }

    TextSettingOption(
        title = stringResource(id = R.string.sync_url_option),
        currentValue = settings.kdriveSyncUrl.value.ifBlank {
            stringResource(id = R.string.sync_url_option_desc)
        },
        onClick = { showDialog.value = true }
    )

    if (showDialog.value) {
        DialogWithTextField(
            initialValue = settings.kdriveSyncUrl.lastValue,
            onDismiss = { showDialog.value = false },
            onAction = { settings.kdriveSyncUrl.update(it.trim()) }
        )
    }
}
