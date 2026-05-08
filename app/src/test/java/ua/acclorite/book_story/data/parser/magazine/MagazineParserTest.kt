/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.magazine

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MagazineParserTest {

    private val parser = MagazineParser()

    private fun fixture(name: String): File =
        File("src/test/resources/fixtures/$name").also {
            assertTrue("Missing fixture: ${it.absolutePath}", it.exists())
        }

    // --- Spec §1.7 acceptance: per-fixture invariants ---

    @Test
    fun `nzz fixture parses with 22 articles all carrying cover images`() {
        val issue = parser.parse(fixture("2026-04-24-nzz.epub"))
        assertNotNull(issue); issue!!
        assertEquals("fr", issue.language)
        assertEquals("Neue Zürcher Zeitung", issue.publisher)
        assertEquals(LocalDate.of(2026, 4, 24), issue.date)

        val articles = issue.sections.flatMap { it.articles }
        assertEquals(22, articles.size)
        assertTrue(articles.all { it.title.isNotBlank() })
        assertTrue(articles.all { it.category.isNotBlank() })
        assertEquals(22, articles.count { it.coverImageHref != null })
        assertEquals(22, articles.count { it.lead != null })
    }

    @Test
    fun `24heures fixture has 9 articles all without images`() {
        val issue = parser.parse(fixture("2026-04-25-24heures.epub"))
        assertNotNull(issue); issue!!
        val articles = issue.sections.flatMap { it.articles }
        assertEquals(9, articles.size)
        assertEquals(9, articles.count { it.coverImageHref == null })
        assertEquals(9, articles.count { it.lead != null })
    }

    @Test
    fun `courrier fixture has 6 articles all with images`() {
        val issue = parser.parse(fixture("2026-04-25-courrier.epub"))
        assertNotNull(issue); issue!!
        val articles = issue.sections.flatMap { it.articles }
        assertEquals(6, articles.size)
        assertEquals(6, articles.count { it.coverImageHref != null })
    }

    @Test
    fun `economist fixture has 73 articles with at least 72 cover images`() {
        val issue = parser.parse(fixture("2026-04-25-economist.epub"))
        assertNotNull(issue); issue!!
        assertEquals("en", issue.language)
        val articles = issue.sections.flatMap { it.articles }
        assertEquals(73, articles.size)
        val withImage = articles.count { it.coverImageHref != null }
        assertTrue("expected ≥72 cover images, got $withImage", withImage >= 72)
        val withLead = articles.count { it.lead != null }
        assertTrue("expected ≥69 leads, got $withLead", withLead >= 69)
    }

    @Test
    fun `economist Leaders section groups 5 consecutive articles`() {
        val issue = parser.parse(fixture("2026-04-25-economist.epub"))!!
        val leaders = issue.sections.firstOrNull { it.name.equals("Leaders", ignoreCase = true) }
        assertNotNull("Leaders section missing", leaders)
        assertEquals(5, leaders!!.articles.size)
    }

    @Test
    fun `articles are listed in spine order across all fixtures`() {
        listOf(
            "2026-04-24-nzz.epub",
            "2026-04-25-24heures.epub",
            "2026-04-25-courrier.epub",
            "2026-04-25-economist.epub",
        ).forEach { name ->
            val issue = parser.parse(fixture(name))!!
            val indices = issue.sections.flatMap { it.articles }.map { it.spineIndex }
            assertEquals("spine indices not strictly ascending in $name",
                indices, indices.sorted())
            assertTrue("duplicate spine indices in $name",
                indices.distinct().size == indices.size)
        }
    }

    // --- canParse on synthetic counter-example ---

    @Test
    fun `canParse returns false on a generic ePub without toc-cat`() {
        val tmp = File.createTempFile("plain-fiction", ".epub")
        tmp.deleteOnExit()
        writeMinimalEpub(
            tmp,
            navBody = """
                <nav epub:type="toc"><ol>
                  <li><a href="ch1.xhtml">Chapter 1</a></li>
                  <li><a href="ch2.xhtml">Chapter 2</a></li>
                </ol></nav>
            """.trimIndent(),
            chapters = mapOf(
                "ch1.xhtml" to "<html><body><p>Once upon a time…</p></body></html>",
                "ch2.xhtml" to "<html><body><p>The end.</p></body></html>",
            ),
        )
        assertFalse(parser.canParse(tmp))
        assertNull(parser.parse(tmp))
    }

    @Test
    fun `canParse returns true via signature A on synthetic magazine`() {
        val tmp = File.createTempFile("synthetic-mag", ".epub")
        tmp.deleteOnExit()
        writeMinimalEpub(
            tmp,
            navBody = """
                <nav epub:type="toc"><ol>
                  <li><a href="ch1.xhtml">
                    <span class="toc-cat">News</span>
                    <span class="toc-title">Headline one</span>
                  </a></li>
                </ol></nav>
            """.trimIndent(),
            chapters = mapOf(
                "ch1.xhtml" to """
                    <html><body>
                      <p class="category">News</p>
                      <h1>Headline one</h1>
                      <p class="lead">A short lead.</p>
                      <p>Body</p>
                    </body></html>
                """.trimIndent(),
            ),
        )
        assertTrue(parser.canParse(tmp))
    }

    // --- Image-cascade branches (unit-level) ---

    @Test
    fun `cascade branch 1 picks div hero-img img`() {
        val doc = Jsoup.parse("""
            <html><body>
              <div class="hero-img"><img src="hero.jpg"/></div>
              <div class="img-container"><img src="other.jpg"/></div>
              <p><img src="body.jpg"/></p>
            </body></html>
        """.trimIndent(), Parser.xmlParser())
        assertEquals("hero.jpg", parser.extractCoverImage(doc))
    }

    @Test
    fun `cascade branch 2 falls back to img-container when no hero-img`() {
        val doc = Jsoup.parse("""
            <html><body>
              <div class="img-container"><img src="container.jpg"/></div>
              <p><img src="body.jpg"/></p>
            </body></html>
        """.trimIndent(), Parser.xmlParser())
        assertEquals("container.jpg", parser.extractCoverImage(doc))
    }

    @Test
    fun `cascade branch 3 takes first body img skipping blockquote`() {
        val doc = Jsoup.parse("""
            <html><body>
              <blockquote><img src="quote.jpg"/></blockquote>
              <p><img src="body.jpg"/></p>
            </body></html>
        """.trimIndent(), Parser.xmlParser())
        assertEquals("body.jpg", parser.extractCoverImage(doc))
    }

    @Test
    fun `cascade branch 4 returns null when no images`() {
        val doc = Jsoup.parse(
            "<html><body><p>plain text only</p></body></html>",
            Parser.xmlParser(),
        )
        assertNull(parser.extractCoverImage(doc))
    }

    // --- Helpers ---

    private fun writeMinimalEpub(
        out: File,
        navBody: String,
        chapters: Map<String, String>,
    ) {
        val opf = buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">""")
            appendLine("""<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">""")
            appendLine("""<dc:identifier id="uid">urn:test</dc:identifier>""")
            appendLine("""<dc:title>Test</dc:title>""")
            appendLine("""<dc:language>en</dc:language>""")
            appendLine("""</metadata>""")
            appendLine("""<manifest>""")
            appendLine("""<item id="toc" href="toc.xhtml" media-type="application/xhtml+xml" properties="nav"/>""")
            chapters.keys.forEachIndexed { i, href ->
                appendLine("""<item id="ch$i" href="$href" media-type="application/xhtml+xml"/>""")
            }
            appendLine("""</manifest>""")
            appendLine("""<spine>""")
            appendLine("""<itemref idref="toc"/>""")
            chapters.keys.indices.forEach { appendLine("""<itemref idref="ch$it"/>""") }
            appendLine("""</spine>""")
            appendLine("""</package>""")
        }
        val toc = """<?xml version="1.0" encoding="UTF-8"?>
            |<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
            |<head><title>TOC</title></head>
            |<body>
            |$navBody
            |</body></html>
        """.trimMargin()

        ZipOutputStream(out.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write("application/epub+zip".toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write(
                """<?xml version="1.0"?>
                |<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
                |<rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles>
                |</container>
                """.trimMargin().toByteArray()
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(opf.toByteArray())
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("OEBPS/toc.xhtml"))
            zip.write(toc.toByteArray())
            zip.closeEntry()

            chapters.forEach { (href, body) ->
                zip.putNextEntry(ZipEntry("OEBPS/$href"))
                zip.write(body.toByteArray())
                zip.closeEntry()
            }
        }

        // Sanity: zip is non-empty
        ByteArrayOutputStream().use {
            assertTrue(out.length() > 0)
        }
    }
}
