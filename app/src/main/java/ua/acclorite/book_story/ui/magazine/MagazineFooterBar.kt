/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.magazine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val MagazineFooterHeight: Dp = 48.dp
private val DividerWidth: Dp = 1.dp

/**
 * Permanent footer bar shared by the magazine TOC and article views.
 *
 * Three equally sized zones: decrease font, app-home, increase font. The
 * home button is the only way to escape back to the library now that body
 * taps are reserved for page-turning.
 *
 * Pass null to [onDecrease] / [onIncrease] when font sizing doesn't apply
 * (e.g. on the TOC where there's no article body to scale).
 */
@Composable
fun MagazineFooterBar(
    onDecrease: (() -> Unit)?,
    onAppHome: () -> Unit,
    onIncrease: (() -> Unit)?,
    decreaseEnabled: Boolean = true,
    increaseEnabled: Boolean = true,
    centerLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(MagazineFooterHeight)
            .background(MaterialTheme.colorScheme.surfaceContainer),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FooterZone(
            icon = Icons.Filled.Remove,
            contentDescription = "Smaller text",
            enabled = onDecrease != null && decreaseEnabled,
            onClick = { onDecrease?.invoke() },
            modifier = Modifier.weight(1f),
        )
        VerticalDivider()
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(onClick = onAppHome),
            contentAlignment = Alignment.Center,
        ) {
            if (centerLabel != null) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Home, contentDescription = "App home")
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = centerLabel,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Icon(Icons.Filled.Home, contentDescription = "App home")
            }
        }
        VerticalDivider()
        FooterZone(
            icon = Icons.Filled.Add,
            contentDescription = "Bigger text",
            enabled = onIncrease != null && increaseEnabled,
            onClick = { onIncrease?.invoke() },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FooterZone(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (enabled) LocalContentColor.current
               else LocalContentColor.current.copy(alpha = 0.38f)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint)
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
