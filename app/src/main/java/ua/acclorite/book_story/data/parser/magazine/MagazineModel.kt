/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.magazine

import java.time.LocalDate

data class MagazineIssue(
    val title: String,
    val publisher: String,
    val date: LocalDate?,
    val language: String,
    val sections: List<MagazineSection>,
    val opfDir: String,
)

data class MagazineSection(
    val name: String,
    val articles: List<MagazineArticle>,
)

data class MagazineArticle(
    val spineIndex: Int,
    val contentHref: String,
    val title: String,
    val category: String,
    val author: String?,
    val lead: String?,
    val coverImageHref: String?,
)
