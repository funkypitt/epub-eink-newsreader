/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.magazine

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ua.acclorite.book_story.data.parser.magazine.MagazineWebViewClient
import ua.acclorite.book_story.presentation.magazine.MagazineArticleState
import java.io.File

private const val ZOOM_MIN = 70
private const val ZOOM_MAX = 220
private const val ZOOM_STEP = 10

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MagazineArticleContent(
    state: MagazineArticleState,
    onPrev: () -> Unit,
    onTocHome: () -> Unit,
    onAppHome: () -> Unit,
    onNext: () -> Unit,
) {
    val config = LocalConfiguration.current
    val initialZoom = remember(config.screenWidthDp) {
        defaultTextZoomForScreenWidth(config.screenWidthDp)
    }
    var textZoom by remember(initialZoom) { mutableIntStateOf(initialZoom) }

    KeepScreenOnEffect()

    Column(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        MagazineHeaderBar(
            onPrev = onPrev,
            onHome = onTocHome,
            onNext = onNext,
            prevEnabled = state.hasPrev,
            homeEnabled = true,
            nextEnabled = state.hasNext,
            centerText = state.article?.category,
        )

        // Clip the article body strictly to its allotted band so the WebView
        // (or any oversized image inside it) cannot bleed into the header /
        // footer zones, no matter what the chapter's own CSS tries to do.
        Box(modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            when {
                state.isLoading -> CenteredText("Loading…")
                state.errorMessage != null -> CenteredText(state.errorMessage)
                state.chapterHtml != null && state.epubPath != null && state.issue != null ->
                    ChapterWebView(
                        epubPath = state.epubPath,
                        opfDir = state.issue.opfDir,
                        chapterHref = state.article?.contentHref ?: return@Box,
                        html = state.chapterHtml,
                        textZoom = textZoom,
                    )
                else -> CenteredText("No content.")
            }
        }

        MagazineFooterBar(
            onDecrease = { textZoom = (textZoom - ZOOM_STEP).coerceAtLeast(ZOOM_MIN) },
            onAppHome = onAppHome,
            onIncrease = { textZoom = (textZoom + ZOOM_STEP).coerceAtMost(ZOOM_MAX) },
            decreaseEnabled = textZoom > ZOOM_MIN,
            increaseEnabled = textZoom < ZOOM_MAX,
            centerLabel = "$textZoom%",
        )
    }
}

@Composable
private fun ChapterWebView(
    epubPath: String,
    opfDir: String,
    chapterHref: String,
    html: String,
    textZoom: Int,
) {
    var client by remember { mutableStateOf<MagazineWebViewClient?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(epubPath, opfDir) {
        val c = MagazineWebViewClient(File(epubPath), opfDir)
        client = c
        onDispose {
            c.close()
            client = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.builtInZoomControls = false
                    settings.displayZoomControls = false
                    settings.useWideViewPort = false
                    settings.loadWithOverviewMode = true
                    overScrollMode = WebView.OVER_SCROLL_NEVER
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    setBackgroundColor(android.graphics.Color.WHITE)
                    setOnTouchListener { _, _ -> true }
                    webView = this
                }
            },
            update = { wv ->
                val c = client ?: return@AndroidView
                wv.webViewClient = c
                wv.settings.textZoom = textZoom
                val baseUrl = if (opfDir.isEmpty()) {
                    "${MagazineWebViewClient.BASE_URL}/"
                } else {
                    "${MagazineWebViewClient.BASE_URL}/$opfDir/"
                }
                // Reload only when the chapter changes — re-running
                // loadDataWithBaseURL on every recomposition (e.g. textZoom
                // bumps) would jump back to page 1 of the article. We tag the
                // WebView with the currently-loaded URL.
                val targetUrl = baseUrl + chapterHref
                if (wv.tag != targetUrl) {
                    wv.loadDataWithBaseURL(
                        targetUrl,
                        injectArticleMargins(html),
                        "text/html",
                        "UTF-8",
                        null,
                    )
                    wv.scrollTo(0, 0)
                    wv.tag = targetUrl
                }
            },
        )

        // Tap zones: left third = previous column, right third = next column.
        // The injected CSS lays the chapter body out as horizontal columns,
        // each exactly viewport-sized — so paging is just `scrollTo` by
        // viewport-width, which always lands on a column boundary (no
        // half-line at the band edge).
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        webView?.let { wv ->
                            val newX = (wv.scrollX - wv.width).coerceAtLeast(0)
                            wv.scrollTo(newX, 0)
                        }
                    }
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        webView?.let { wv ->
                            if (wv.canScrollHorizontally(1)) {
                                wv.scrollTo(wv.scrollX + wv.width, 0)
                            }
                        }
                    }
            )
        }
    }
}

@Composable
private fun CenteredText(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Discrete defaults tuned for typical phone / large-phone / tablet widths.
 * Returns a textZoom percentage Android's WebView accepts directly.
 */
internal fun defaultTextZoomForScreenWidth(screenWidthDp: Int): Int = when {
    screenWidthDp < 380 -> 100   // compact phones
    screenWidthDp < 480 -> 110   // typical phones (Pixel 10 Pro XL ≈ 412dp)
    screenWidthDp < 700 -> 125   // large phones / small foldables
    else -> 140                  // tablets, e-ink 7"+
}

private val HEAD_CLOSE = Regex("(?i)</head>")

/**
 * Lays the chapter body out as a horizontal series of viewport-sized
 * columns and forces small inner margins. Page-turn navigation then
 * advances by one column-width — content always breaks on line /
 * paragraph boundaries, never mid-line, so the article body cannot
 * leak into the header or footer band. Inspired by epub.js's
 * `flow: "paginated"` and Pluralis' TextPainter pre-pagination.
 *
 * The viewport meta is added so 100vw / 100vh resolve correctly on
 * Android WebView regardless of what the chapter declares.
 *
 * Inserted at the end of <head> so it overrides the producer's
 * `style.css`. Falls back to prepending if the chapter has no <head>.
 */
internal fun injectArticleMargins(html: String): String {
    val style = """
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
        <style>
          html, body {
            margin: 0 !important;
            height: 100% !important;
          }
          html { overflow-y: hidden !important; }
          body {
            padding: 14px 16px !important;
            box-sizing: border-box !important;
            column-width: 100vw !important;
            column-gap: 0 !important;
            column-fill: auto !important;
            height: 100% !important;
            overflow-y: hidden !important;
          }
          img, figure, video {
            max-width: 100% !important;
            height: auto !important;
            break-inside: avoid !important;
            page-break-inside: avoid !important;
          }
          .hero-img img, .img-container img { width: 100% !important; }
          h1, h2, h3, h4, h5, h6, p, blockquote {
            break-inside: avoid !important;
            page-break-inside: avoid !important;
          }
        </style>
    """.trimIndent()
    return if (HEAD_CLOSE.containsMatchIn(html)) {
        HEAD_CLOSE.replaceFirst(html, "$style</head>")
    } else {
        "<head>$style</head>$html"
    }
}
