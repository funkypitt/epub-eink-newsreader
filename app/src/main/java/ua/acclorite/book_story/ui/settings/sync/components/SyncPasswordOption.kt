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
fun SyncPasswordOption() {
    val settings = LocalSettings.current
    val showDialog = remember { mutableStateOf(false) }
    val hasPassword = settings.kdriveSyncPassword.value.isNotBlank()

    TextSettingOption(
        title = stringResource(id = R.string.sync_password_option),
        currentValue = if (hasPassword) "•".repeat(8) else "...",
        onClick = { showDialog.value = true }
    )

    if (showDialog.value) {
        DialogWithTextField(
            initialValue = settings.kdriveSyncPassword.lastValue,
            onDismiss = { showDialog.value = false },
            onAction = { settings.kdriveSyncPassword.update(it) }
        )
    }
}
