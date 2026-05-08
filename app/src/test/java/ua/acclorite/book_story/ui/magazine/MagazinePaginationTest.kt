/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.magazine

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class MagazinePaginationTest {

    /**
     * Targets §2.10: "Calcul itemsPerPage correct sur 3 tailles d'écran simulées".
     * Mirrors the layout constants in [MagazineTocContent].
     */
    private val cardPlusSeparator = 140.dp + 1.dp
    private val chrome = 48.dp + 32.dp + (8.dp * 2) // 3-zone header + indicator + 2× vertical padding

    @Test
    fun `compact phone 360x640 fits 3 cards`() {
        val available = 640.dp - chrome
        val n = computeItemsPerPage(available, cardPlusSeparator)
        assertEquals(3, n)
    }

    @Test
    fun `medium phone 480x800 fits 4 cards`() {
        val available = 800.dp - chrome
        val n = computeItemsPerPage(available, cardPlusSeparator)
        assertEquals(4, n)
    }

    @Test
    fun `tablet 800x1280 fits 8 cards`() {
        val available = 1280.dp - chrome
        val n = computeItemsPerPage(available, cardPlusSeparator)
        assertEquals(8, n)
    }

    @Test
    fun `tiny screen still yields at least one card`() {
        val n = computeItemsPerPage(20.dp, cardPlusSeparator)
        assertEquals(1, n)
    }

    @Test
    fun `degenerate denominator falls back to one card`() {
        val n = computeItemsPerPage(640.dp, 0.dp)
        assertEquals(1, n)
    }

    // §"default font sizes calculated according to optimal readability for screen size"
    @Test
    fun `default text zoom by screen width is bracketed`() {
        assertEquals(100, defaultTextZoomForScreenWidth(360)) // compact phone
        assertEquals(110, defaultTextZoomForScreenWidth(412)) // Pixel 10 Pro XL
        assertEquals(125, defaultTextZoomForScreenWidth(600)) // large phone / small foldable
        assertEquals(140, defaultTextZoomForScreenWidth(840)) // tablet / e-ink 7"+
    }

    // Chapter HTML preparation is gone — epub.js now renders chapters via
    // its `flow: paginated` mode, sidestepping the entire WebView-scroll
    // problem the previous prepareChapterHtml was trying to work around.
    // See assets/epubjs/reader.html and EpubJsArticleView in
    // MagazineArticleContent.kt.
}
