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
fun SyncShareLinkOption() {
    val settings = LocalSettings.current
    val showDialog = remember { mutableStateOf(false) }
    val currentValue = settings.kdriveShareLink.value

    TextSettingOption(
        title = stringResource(id = R.string.sync_share_link_option),
        currentValue = if (currentValue.isBlank()) {
            stringResource(id = R.string.sync_share_link_option_desc)
        } else {
            currentValue.substringAfterLast("/").take(12) + "..."
        },
        onClick = { showDialog.value = true }
    )

    if (showDialog.value) {
        DialogWithTextField(
            initialValue = settings.kdriveShareLink.lastValue,
            onDismiss = { showDialog.value = false },
            onAction = { settings.kdriveShareLink.update(it.trim()) }
        )
    }
}
