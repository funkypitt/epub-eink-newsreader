/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.magazine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Toc
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val MagazineHeaderHeight: Dp = 48.dp
private val DividerWidth: Dp = 1.dp

/**
 * Permanent 3-zone header at the top of every magazine screen.
 *
 * Sits on top of the existing tap-zone / gesture navigation so taps on the
 * left and right halves of the body still turn pages.
 */
@Composable
fun MagazineHeaderBar(
    onPrev: () -> Unit,
    onHome: () -> Unit,
    onNext: () -> Unit,
    prevEnabled: Boolean,
    homeEnabled: Boolean,
    nextEnabled: Boolean,
    centerText: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(MagazineHeaderHeight)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderZone(
            icon = Icons.Filled.ChevronLeft,
            contentDescription = "Previous",
            enabled = prevEnabled,
            onClick = onPrev,
            modifier = Modifier.weight(1f),
        )
        VerticalDivider()
        HeaderZone(
            icon = Icons.AutoMirrored.Filled.Toc,
            contentDescription = "Table of contents",
            enabled = homeEnabled,
            onClick = onHome,
            modifier = Modifier.weight(1f),
            label = centerText,
        )
        VerticalDivider()
        HeaderZone(
            icon = Icons.Filled.ChevronRight,
            contentDescription = "Next",
            enabled = nextEnabled,
            onClick = onNext,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HeaderZone(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    Box(modifier = modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
        if (label != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClick, enabled = enabled) {
                    Icon(imageVector = icon, contentDescription = contentDescription)
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            IconButton(onClick = onClick, enabled = enabled) {
                Icon(imageVector = icon, contentDescription = contentDescription)
            }
        }
    }
}

@Composable
private fun VerticalDivider() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(DividerWidth)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}
