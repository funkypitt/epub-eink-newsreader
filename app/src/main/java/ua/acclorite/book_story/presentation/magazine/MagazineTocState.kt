/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.magazine

import androidx.compose.runtime.Immutable
import ua.acclorite.book_story.data.parser.magazine.MagazineIssue

@Immutable
data class MagazineTocState(
    val isLoading: Boolean = true,
    val issue: MagazineIssue? = null,
    val epubPath: String? = null,
    val currentArticleHref: String? = null,
    val errorMessage: String? = null,
)
