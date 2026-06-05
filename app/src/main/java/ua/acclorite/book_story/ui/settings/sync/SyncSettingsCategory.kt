/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

@file:Suppress("FunctionName")

package ua.acclorite.book_story.ui.settings.sync

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import ua.acclorite.book_story.ui.common.components.common.StyledText
import ua.acclorite.book_story.ui.settings.sync.components.SyncEnabledOption
import ua.acclorite.book_story.ui.settings.sync.components.SyncIntervalOption
import ua.acclorite.book_story.ui.settings.sync.components.SyncNowOption
import ua.acclorite.book_story.ui.settings.sync.components.SyncPasswordOption
import ua.acclorite.book_story.ui.settings.sync.components.SyncShareLinkOption
import ua.acclorite.book_story.ui.settings.sync.components.SyncUrlOption
import ua.acclorite.book_story.ui.settings.sync.components.SyncUsernameOption

fun LazyListScope.SyncSettingsCategory(
    topPadding: Dp = 16.dp,
    bottomPadding: Dp = 16.dp
) {
    item {
        Spacer(modifier = Modifier.height((topPadding - 8.dp).coerceAtLeast(0.dp)))
    }

    item {
        SyncEnabledOption()
    }

    item {
        SyncShareLinkOption()
    }

    item {
        SyncIntervalOption()
    }

    item {
        SyncNowOption()
    }

    item {
        HorizontalDivider(Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
    }

    item {
        StyledText(
            text = "WebDAV (alternative)",
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }

    item {
        SyncUrlOption()
    }

    item {
        SyncUsernameOption()
    }

    item {
        SyncPasswordOption()
    }

    item {
        Spacer(modifier = Modifier.height(bottomPadding))
    }
}
