/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.magazine

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ua.acclorite.book_story.data.parser.magazine.MagazineWebViewClient
import ua.acclorite.book_story.presentation.magazine.MagazineArticleState
import java.io.File

private const val ZOOM_MIN = 70
private const val ZOOM_MAX = 220
private const val ZOOM_STEP = 10

/**
 * Height of the opaque white band drawn over the WebView's bottom edge.
 * Hides the half-cut line that would otherwise sit there because we
 * vertical-scroll an arbitrarily-tall document inside a viewport-sized
 * WebView. Combined with a matching reduction in the page-turn step, the
 * masked line reappears in full as the first line of the next page —
 * cheap analogue to Pluralis' TextPainter pre-pagination.
 *
 * 36dp comfortably covers one line at our default text zooms (100–140%
 * with body line-height ≈ 1.6 × 16-20px ≈ 26-32px).
 */
private val EDGE_FADE: Dp = 36.dp

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
    var chromeVisible by remember(state.article?.contentHref) {
        mutableStateOf(false)
    }

    KeepScreenOnEffect()

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        // Body fills the whole screen (minus system insets) so the article
        // text is never sharing space with the chrome — the previous design's
        // central pain point.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .clipToBounds()
        ) {
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
                        onTapCenter = { chromeVisible = !chromeVisible },
                    )
                else -> CenteredText("No content.")
            }
        }

        // Chrome overlay: appears on a centre tap, disappears on the next.
        // Header chevrons step between articles in spine order; footer +/-
        // adjusts text zoom and home pops back to the library. Both share
        // the same chromeVisible flag — there's no need to keep the font
        // controls permanently on screen.
        if (chromeVisible) {
            MagazineHeaderBar(
                onPrev = onPrev,
                onHome = onTocHome,
                onNext = onNext,
                prevEnabled = state.hasPrev,
                homeEnabled = true,
                nextEnabled = state.hasNext,
                centerText = state.article?.category,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
            )
            MagazineFooterBar(
                onDecrease = { textZoom = (textZoom - ZOOM_STEP).coerceAtLeast(ZOOM_MIN) },
                onAppHome = onAppHome,
                onIncrease = { textZoom = (textZoom + ZOOM_STEP).coerceAtMost(ZOOM_MAX) },
                decreaseEnabled = textZoom > ZOOM_MIN,
                increaseEnabled = textZoom < ZOOM_MAX,
                centerLabel = "$textZoom%",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            )
        }
    }
}

@Composable
private fun ChapterWebView(
    epubPath: String,
    opfDir: String,
    chapterHref: String,
    html: String,
    textZoom: Int,
    onTapCenter: () -> Unit,
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
                    // JS is enabled solely for our own injected paginator
                    // (see prepareChapterHtml) which aligns scroll to whole
                    // lines. Producer chapters don't run scripts.
                    settings.javaScriptEnabled = true
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
                        prepareChapterHtml(html),
                        "text/html",
                        "UTF-8",
                        null,
                    )
                    wv.scrollTo(0, 0)
                    wv.tag = targetUrl
                }
            },
        )

        // Tap zones: three equal thirds.
        //   left   → previous page (line-aligned scroll, JS-driven)
        //   centre → toggle chrome (header / footer overlays)
        //   right  → next page (line-aligned scroll, JS-driven)
        //
        // The JS paginator (injected in prepareChapterHtml) reads the
        // computed body line-height and snaps scrollY to a whole-line
        // multiple — no mid-line cuts at either edge of a page.
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        webView?.evaluateJavascript("window.__mr_page(-1)", null)
                    }
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTapCenter,
                    )
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        webView?.evaluateJavascript("window.__mr_page(1)", null)
                    }
            )
        }

        // Opaque white band over the WebView's bottom edge — hides the
        // partial line that the viewport-sized scroll inevitably cuts.
        // Drawn after the tap zones so it's visually on top, but the
        // tap zones are functionally below the band's height anyway
        // (they cover the full Box; clicks at the band's pixels just
        // page-turn, which is correct).
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(EDGE_FADE)
                .background(Color.White)
        )
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
 * The Economist generator emits a malformed drop-cap pattern that browsers
 * render as a literal `<` followed by `b>L</b>` plain-text — easy to
 * mistake for a UI bug (looks like the back-arrow icon overlapping the
 * first line). The intended structure is `<span class="drop-cap">L</span>`,
 * so we collapse the broken form back to that.
 *
 * Source: `<span class="drop-cap"><</span>b>L</b>` — the outer span ends
 * at the literal `<`, leaking the inner `<b>` as text.
 */
private val BROKEN_DROP_CAP = Regex(
    """<span class="drop-cap"><</span>b>([A-Za-z])</b>"""
)

/**
 * Repairs known producer-side HTML defects and injects:
 *
 * 1. A small override `<style>` block at the end of `<head>` so the
 *    producer's `style.css` doesn't leave the article body without
 *    sane margins or with images that overflow the viewport. The
 *    body's line-height is forced to a fixed multiplier so the JS
 *    paginator can read a stable per-line value.
 * 2. A `<script>` defining `window.__mr_page(direction)` that
 *    advances scroll by `(viewport - 1 line)` *snapped to a whole
 *    multiple of line-height*. This is what eliminates the half-cut
 *    line at the page edges — every page boundary is on a line
 *    boundary by construction.
 */
internal fun prepareChapterHtml(html: String): String {
    val repaired = BROKEN_DROP_CAP.replace(html) { match ->
        """<span class="drop-cap">${match.groupValues[1]}</span>"""
    }
    val style = """
        <style>
          html, body { margin: 0 !important; }
          body {
            /* No vertical padding — keeps line tops at integer multiples
               of line-height so the JS paginator's snap math is exact. */
            padding: 0 16px !important;
            box-sizing: border-box !important;
            line-height: 1.6 !important;
          }
          img, figure, video { max-width: 100% !important; height: auto !important; }
          .hero-img img, .img-container img { width: 100% !important; }
        </style>
    """.trimIndent()
    val script = """
        <script>
        (function () {
          function lineHeightPx() {
            var bs = getComputedStyle(document.body);
            var raw = bs.lineHeight;
            var fs = parseFloat(bs.fontSize) || 16;
            if (!raw || raw === 'normal') return fs * 1.2;
            var n = parseFloat(raw);
            if (!isFinite(n) || n <= 0) return fs * 1.2;
            // line-height as a length (e.g. "25.6px"): use directly.
            // line-height as a unitless multiplier (e.g. "1.6"): multiply
            // by font-size. This second branch was the bug — parseFloat
            // returned 1.6 and the whole alignment treated that as pixels.
            return raw.indexOf('px') !== -1 ? n : n * fs;
          }
          window.__mr_page = function (direction) {
            var lh = lineHeightPx();
            var vh = window.innerHeight;
            // Step: viewport minus one line, snapped DOWN to a whole-line
            // multiple so the next page starts exactly at a line top.
            var step = Math.floor((vh - lh) / lh) * lh;
            if (step < lh) step = lh;
            var max = Math.max(
              0,
              document.documentElement.scrollHeight - vh
            );
            var target = Math.max(0, Math.min(max, window.scrollY + direction * step));
            // Snap to a line top: with body padding-top:0, line tops are
            // at integer multiples of lh.
            target = Math.round(target / lh) * lh;
            window.scrollTo(0, target);
          };
        })();
        </script>
    """.trimIndent()
    val injection = "$style\n$script"
    return if (HEAD_CLOSE.containsMatchIn(repaired)) {
        HEAD_CLOSE.replaceFirst(repaired, "$injection</head>")
    } else {
        "<head>$injection</head>$repaired"
    }
}
