/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.magazine

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import ua.acclorite.book_story.ui.common.helpers.LocalActivity

/**
 * Keeps the screen awake while the host composable is on screen by setting
 * [WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON] on the activity window.
 * Mounted on the magazine TOC and article views — Android's default screen
 * timeout is too aggressive for reading.
 */
@Composable
fun KeepScreenOnEffect() {
    val activity = LocalActivity.current
    DisposableEffect(activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
