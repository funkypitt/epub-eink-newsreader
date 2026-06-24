/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.core.helpers

import org.jsoup.Jsoup

/**
 * Strips any HTML/XML markup that survives as *literal text* inside a string.
 *
 * Jsoup's `Element.text()` already drops child elements, so a well-formed
 * `<h1><em>Foo</em></h1>` yields "Foo". But some producers double-escape inline
 * markup in titles/metadata (`<dc:title>The &lt;em&gt;Economist&lt;/em&gt;</dc:title>`).
 * The XML parser decodes `&lt;em&gt;` back to the literal characters `<em>`, so
 * the extracted text reads "The <em>Economist</em>" and shows the tags verbatim
 * in the library and reader. Running that text through the HTML parser once more
 * removes the now-literal tags. A stray "<" not starting a tag (e.g. "a < b") is
 * left untouched by the lenient HTML5 parser.
 */
fun String.stripMarkup(): String = Jsoup.parse(this).text()
