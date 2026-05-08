/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.magazine

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import ua.acclorite.book_story.core.log.logE
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import javax.inject.Inject

private const val TAG = "MagazineParser"

/**
 * Parses ePubs produced by the in-house "magazine" generator: the 4 reference
 * fixtures (NZZ, 24heures, Courrier, Economist) all share the same OEBPS layout
 * and the same `toc-cat` / `toc-title` / `toc-author` markup convention.
 *
 * `canParse` is fast: it stops at signature A (nav-structured) when possible
 * and only falls back to a full chapter scan if the nav looks unconventional.
 */
class MagazineParser @Inject constructor() {

    fun canParse(file: File): Boolean = openZip(file)?.use { zip ->
        runCatching { detect(zip) != null }.getOrDefault(false)
    } ?: false

    fun parse(file: File): MagazineIssue? = openZip(file)?.use { zip ->
        runCatching { parseInternal(zip) }
            .onFailure { logE(TAG, "parse failed: ${it.message}") }
            .getOrNull()
    }

    private fun openZip(file: File): ZipFile? =
        if (file.exists() && file.canRead()) ZipFile(file) else null

    // --- Detection ---

    private enum class Mode { NAV_STRUCTURED, CONTENT_STRUCTURED }

    private fun detect(zip: ZipFile): Mode? {
        val ctx = readEpubContext(zip) ?: return null
        // Signature A — nav has both toc-cat and toc-title spans
        ctx.navHref?.let { href ->
            val nav = ctx.readResource(zip, href) ?: return@let
            val doc = Jsoup.parse(nav, Parser.xmlParser())
            if (doc.selectFirst("span.toc-cat") != null &&
                doc.selectFirst("span.toc-title") != null
            ) return Mode.NAV_STRUCTURED
        }
        // Signature B — ≥50% chapters carry <p class="category"> AND <h1>
        val chapters = ctx.contentChapters(zip)
        if (chapters.isEmpty()) return null
        val structured = chapters.count { (_, doc) ->
            doc.selectFirst("p.category") != null && doc.selectFirst("h1") != null
        }
        return if (structured * 2 >= chapters.size) Mode.CONTENT_STRUCTURED else null
    }

    // --- Parsing ---

    private fun parseInternal(zip: ZipFile): MagazineIssue? {
        val ctx = readEpubContext(zip) ?: return null
        val mode = detect(zip) ?: return null

        val title = ctx.opf.select("metadata > dc|title").text().trim()
        val publisher = ctx.opf.select("metadata > dc|publisher").text().trim()
        val language = ctx.opf.select("metadata > dc|language").text().trim()
        val date = parseDate(ctx.opf.select("metadata > dc|date").text().trim())

        val articles = when (mode) {
            Mode.NAV_STRUCTURED -> parseFromNav(zip, ctx)
            Mode.CONTENT_STRUCTURED -> parseFromContent(zip, ctx)
        }
        if (articles.isEmpty()) return null

        return MagazineIssue(
            title = title,
            publisher = publisher,
            date = date,
            language = language,
            sections = groupBySection(articles),
            opfDir = ctx.opfDir,
        )
    }

    private fun parseFromNav(zip: ZipFile, ctx: EpubContext): List<MagazineArticle> {
        val navHref = ctx.navHref ?: return emptyList()
        val navText = ctx.readResource(zip, navHref) ?: return emptyList()
        val nav = Jsoup.parse(navText, Parser.xmlParser())

        // Build href -> spineIndex map (1-based positions ignored — we keep raw index)
        val spineHrefIndex = ctx.spineHrefs
            .withIndex()
            .associate { (idx, href) -> href to idx }

        val rows = nav.select("nav[epub|type=toc] li > a, nav.toc li > a, nav li > a")
            .ifEmpty { nav.select("li > a") }

        return rows.mapNotNull { a ->
            val href = a.attr("href").substringBefore('#').trim()
            if (href.isBlank()) return@mapNotNull null
            val title = a.selectFirst("span.toc-title")?.text()?.trim().orEmpty()
            val category = a.selectFirst("span.toc-cat")?.text()?.trim().orEmpty()
            val author = a.selectFirst("span.toc-author")?.text()?.trim()?.takeUnless { it.isBlank() }
            if (title.isBlank() || category.isBlank()) return@mapNotNull null

            val (lead, cover) = readChapterMeta(zip, ctx, href)

            MagazineArticle(
                spineIndex = spineHrefIndex[href] ?: -1,
                contentHref = href,
                title = title,
                category = category,
                author = author,
                lead = lead,
                coverImageHref = cover,
            )
        }
    }

    private fun parseFromContent(zip: ZipFile, ctx: EpubContext): List<MagazineArticle> {
        return ctx.contentChapters(zip).mapIndexedNotNull { _, (item, doc) ->
            val title = doc.selectFirst("h1")?.text()?.trim().orEmpty()
            val category = doc.selectFirst("p.category")?.text()?.trim().orEmpty()
            if (title.isBlank() || category.isBlank()) return@mapIndexedNotNull null
            val author = doc.selectFirst("p.author")?.text()?.trim()?.takeUnless { it.isBlank() }
            val lead = doc.selectFirst("p.lead")?.text()?.trim()?.takeUnless { it.isBlank() }
            val cover = extractCoverImage(doc)
            MagazineArticle(
                spineIndex = ctx.spineHrefs.indexOf(item.href),
                contentHref = item.href,
                title = title,
                category = category,
                author = author,
                lead = lead,
                coverImageHref = cover,
            )
        }
    }

    private fun readChapterMeta(
        zip: ZipFile,
        ctx: EpubContext,
        href: String,
    ): Pair<String?, String?> {
        val text = ctx.readResource(zip, href) ?: return null to null
        val doc = Jsoup.parse(text, Parser.xmlParser())
        val lead = doc.selectFirst("p.lead")?.text()?.trim()?.takeUnless { it.isBlank() }
        val cover = extractCoverImage(doc)
        return lead to cover
    }

    private fun groupBySection(articles: List<MagazineArticle>): List<MagazineSection> {
        if (articles.isEmpty()) return emptyList()
        val sections = mutableListOf<MagazineSection>()
        var current = mutableListOf<MagazineArticle>()
        var currentName = articles.first().category
        for (article in articles) {
            if (article.category == currentName) {
                current += article
            } else {
                sections += MagazineSection(currentName, current.toList())
                current = mutableListOf(article)
                currentName = article.category
            }
        }
        sections += MagazineSection(currentName, current.toList())
        return sections
    }

    // --- Image cascade ---

    internal fun extractCoverImage(doc: Document): String? {
        doc.selectFirst("div.hero-img img")?.attr("src")?.takeIf { it.isNotBlank() }
            ?.let { return it }
        doc.selectFirst("div.img-container img")?.attr("src")?.takeIf { it.isNotBlank() }
            ?.let { return it }
        val firstBodyImg = doc.select("body img").firstOrNull { img ->
            img.parents().none { it.tagName().equals("blockquote", ignoreCase = true) }
        }
        return firstBodyImg?.attr("src")?.takeIf { it.isNotBlank() }
    }

    // --- ePub context ---

    private data class ManifestItem(
        val id: String,
        val href: String,
        val mediaType: String,
        val properties: String,
    )

    private data class EpubContext(
        val opf: Document,
        val opfDir: String,
        val manifest: Map<String, ManifestItem>,
        val spineHrefs: List<String>,
        val navHref: String?,
    ) {
        fun resolveZipPath(href: String): String =
            if (opfDir.isEmpty()) href else "$opfDir/$href"

        fun readResource(zip: ZipFile, href: String): String? {
            val entry: ZipEntry = zip.getEntry(resolveZipPath(href)) ?: return null
            return zip.getInputStream(entry).bufferedReader().use { it.readText() }
        }

        fun contentChapters(zip: ZipFile): List<Pair<ManifestItem, Document>> {
            return spineHrefs
                .mapNotNull { href -> manifest.values.firstOrNull { it.href == href } }
                .filter { it.mediaType.contains("xhtml", true) || it.mediaType.contains("html", true) }
                .filter { it.id != "toc" && it.id != "title-page" && !it.properties.contains("nav") }
                .mapNotNull { item ->
                    val text = readResource(zip, item.href) ?: return@mapNotNull null
                    item to Jsoup.parse(text, Parser.xmlParser())
                }
        }
    }

    private fun readEpubContext(zip: ZipFile): EpubContext? {
        val containerEntry = zip.getEntry("META-INF/container.xml") ?: return null
        val containerXml = zip.getInputStream(containerEntry).bufferedReader().use { it.readText() }
        val opfPath = Jsoup.parse(containerXml, Parser.xmlParser())
            .selectFirst("rootfile[full-path]")
            ?.attr("full-path")
            ?: return null
        val opfDir = opfPath.substringBeforeLast('/', "")
        val opfEntry = zip.getEntry(opfPath) ?: return null
        val opfText = zip.getInputStream(opfEntry).bufferedReader().use { it.readText() }
        val opf = Jsoup.parse(opfText, Parser.xmlParser())

        val manifest = opf.select("manifest > item").associate { item ->
            val id = item.attr("id")
            id to ManifestItem(
                id = id,
                href = item.attr("href"),
                mediaType = item.attr("media-type"),
                properties = item.attr("properties"),
            )
        }
        val spineHrefs = opf.select("spine > itemref").mapNotNull { ref ->
            manifest[ref.attr("idref")]?.href
        }
        val navHref = manifest.values.firstOrNull { "nav" in it.properties }?.href
        return EpubContext(opf, opfDir, manifest, spineHrefs, navHref)
    }

    private fun parseDate(raw: String): LocalDate? {
        if (raw.isBlank()) return null
        return try {
            // Accept YYYY-MM-DD or longer ISO forms (e.g. dcterms:modified)
            LocalDate.parse(raw.take(10))
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
