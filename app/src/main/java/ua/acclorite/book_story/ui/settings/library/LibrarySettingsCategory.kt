/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

@file:Suppress("FunctionName")

package ua.acclorite.book_story.ui.settings.library

import androidx.compose.foundation.lazy.LazyListScope
import ua.acclorite.book_story.ui.settings.library.display.LibraryDisplaySubcategory

fun LazyListScope.LibrarySettingsCategory() {
    LibraryDisplaySubcategory(
        showDivider = false
    )
}
