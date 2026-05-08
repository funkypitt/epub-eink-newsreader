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

    @Test
    fun `prepareChapterHtml inserts override style at end of head`() {
        val html = """<html><head><link rel="stylesheet" href="style.css"/></head><body><p>x</p></body></html>"""
        val out = prepareChapterHtml(html)
        // Override style sits *after* the producer's link so it wins specificity ties.
        val styleIdx = out.indexOf("padding: 24px")
        val linkIdx = out.indexOf("style.css")
        val headCloseIdx = out.indexOf("</head>")
        assert(linkIdx in 0 until styleIdx) { "override must come after producer style.css" }
        assert(styleIdx in 0 until headCloseIdx) { "override must be inside <head>" }
    }

    @Test
    fun `prepareChapterHtml prepends head when missing`() {
        val html = "<body><p>headless</p></body>"
        val out = prepareChapterHtml(html)
        assert(out.startsWith("<head>")) { "should fall back to prepending <head>" }
        assert("padding: 24px" in out)
    }

    @Test
    fun `prepareChapterHtml repairs Economist's broken drop-cap markup`() {
        // Real fragment from chapter-04.xhtml of the Economist fixture: the
        // outer drop-cap span ends at a literal `<`, leaking <b>L</b> as
        // plain text. Browsers render the `<` as a huge first letter and
        // `b>L</b>` as visible text next to it.
        val broken = """<p class="first"><span class="drop-cap"><</span>b>L</b>argely because Donald Trump</p>"""
        val out = prepareChapterHtml(broken)
        assert("""<span class="drop-cap">L</span>""" in out) {
            "expected repaired drop-cap; got: $out"
        }
        assert("""<span class="drop-cap"><""" !in out) {
            "broken pattern still present after repair"
        }
        // The plain-text 'b>' leak is gone.
        assert("b>L</b>" !in out) { "leftover <b> tag-as-text not stripped" }
    }
}
