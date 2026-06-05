/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.settings.sync.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ua.acclorite.book_story.R
import ua.acclorite.book_story.presentation.settings.SyncModel
import ua.acclorite.book_story.ui.common.components.common.StyledText
import ua.acclorite.book_story.ui.common.helpers.LocalSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SyncNowOption(
    syncModel: SyncModel = hiltViewModel()
) {
    val settings = LocalSettings.current
    val syncState = syncModel.state.collectAsStateWithLifecycle()
    val lastSyncTime = settings.kdriveSyncLastTime.value

    Row(
        modifier = Modifier
            .clickable(enabled = !syncState.value.isSyncing) {
                syncModel.syncNow()
            }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            StyledText(
                text = if (syncState.value.isSyncing) {
                    stringResource(id = R.string.sync_in_progress)
                } else {
                    stringResource(id = R.string.sync_now_option)
                },
                style = MaterialTheme.typography.titleMedium.copy(
                    color = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            StyledText(
                text = syncState.value.lastResultMessage
                    ?: formatLastSync(lastSyncTime),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }

        if (syncState.value.isSyncing) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        }
    }
}

@Composable
private fun formatLastSync(timestamp: Long): String {
    return if (timestamp == 0L) {
        stringResource(id = R.string.sync_never)
    } else {
        stringResource(
            id = R.string.sync_last_time,
            SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(timestamp))
        )
    }
}
