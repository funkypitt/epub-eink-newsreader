/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.magazine

import android.annotation.SuppressLint
import android.util.Base64
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
    var chromeVisible by remember(state.article?.contentHref) {
        mutableStateOf(false)
    }

    KeepScreenOnEffect()

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            when {
                state.isLoading -> CenteredText("Loading…")
                state.errorMessage != null -> CenteredText(state.errorMessage)
                state.epubPath != null && state.article != null ->
                    EpubJsArticleView(
                        epubPath = state.epubPath,
                        chapterHref = state.article.contentHref,
                        textZoomPercent = textZoom,
                        onTapCenter = { chromeVisible = !chromeVisible },
                    )
                else -> CenteredText("No content.")
            }
        }

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

/**
 * Hosts the bundled `assets/epubjs/reader.html` in a WebView. epub.js
 * paginates the chapter natively via `flow: "paginated"` — no scroll
 * math, no line-alignment hacks, no half-cut letters: it lays the body
 * out in iframe-rendered columns and swaps which column is shown on
 * `rendition.next()`.
 *
 * The whole ePub is shipped to JS as a base64 string (same approach
 * funky-openlib's e-ink reader uses); epub.js then displays the
 * requested chapter by href.
 *
 * Three Compose tap zones overlay the WebView:
 * - left  → previous page in the article
 * - centre → toggle the chrome overlay (handled in the parent)
 * - right → next page
 *
 * Page-turn is delegated to `rendition.next()` / `rendition.prev()`
 * via `evaluateJavascript`. Font size from the footer is mapped onto
 * `setFontSize(px)` (50% — 200% mapped to 12 — 28px).
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EpubJsArticleView(
    epubPath: String,
    chapterHref: String,
    textZoomPercent: Int,
    onTapCenter: () -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageLoaded by remember { mutableStateOf(false) }
    var bookLoaded by remember(epubPath) { mutableStateOf(false) }
    var debugStatus by remember { mutableStateOf<String?>(null) }

    val epubBase64 = remember(epubPath) {
        runCatching {
            val bytes = File(epubPath).readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        }.getOrNull()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                // Enable Chrome DevTools remote debugging — connect the
                // device by USB then open chrome://inspect/#devices in
                // Chrome on the host to step through the live WebView.
                WebView.setWebContentsDebuggingEnabled(true)
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    // epub.js loads the chapter inside a sandboxed iframe and
                    // needs both flags on to render images / inline styles
                    // packaged inside the ePub. funky-openlib uses the same
                    // setup successfully.
                    settings.allowFileAccessFromFileURLs = true
                    settings.allowUniversalAccessFromFileURLs = true
                    settings.builtInZoomControls = false
                    settings.displayZoomControls = false
                    // funky-openlib uses useWideViewPort=true for epub.js;
                    // with false the iframe ends up sized against the 980px
                    // legacy viewport and the rendered page falls outside
                    // the visible area.
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = false
                    // funky-openlib also forces software rendering for
                    // epub.js's iframes — hardware acceleration causes the
                    // iframe content not to paint on some chipsets.
                    setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                    overScrollMode = WebView.OVER_SCROLL_NEVER
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    setBackgroundColor(android.graphics.Color.WHITE)
                    setOnTouchListener { _, _ -> true }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            pageLoaded = true
                        }
                    }
                    // Forward every console.log / .warn / .error from the
                    // WebView into Android's logcat under the "MagazineJS"
                    // tag, plus surface the most recent line on screen.
                    webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                            val level = when (message.messageLevel()) {
                                ConsoleMessage.MessageLevel.ERROR,
                                ConsoleMessage.MessageLevel.WARNING -> Log.WARN
                                else -> Log.INFO
                            }
                            Log.println(
                                level,
                                "MagazineJS",
                                "${message.message()} (${message.sourceId()}:${message.lineNumber()})",
                            )
                            debugStatus = message.message()
                            return true
                        }
                    }
                    addJavascriptInterface(
                        MagazineReaderBridge(
                            readyCallback = { debugStatus = null },
                            errorCallback = { debugStatus = "[error] $it" },
                            traceCallback = { debugStatus = it },
                        ),
                        "MagazineReader",
                    )
                    loadUrl("file:///android_asset/epubjs/reader.html")
                    webView = this
                }
            },
            update = { wv ->
                if (pageLoaded && !bookLoaded && epubBase64 != null) {
                    val js = "loadBook(${quoteJs(epubBase64)}, ${quoteJs(chapterHref)});"
                    wv.evaluateJavascript(js, null)
                    bookLoaded = true
                } else if (pageLoaded && bookLoaded) {
                    // Article changed within the same loaded ePub.
                    wv.evaluateJavascript("goToHref(${quoteJs(chapterHref)});", null)
                }
                if (pageLoaded) {
                    // Map textZoomPercent (~70-220%) onto a font-size in px.
                    val fontPx = (12 + (textZoomPercent - 70) * 16 / 150).coerceIn(10, 40)
                    wv.evaluateJavascript("setFontSize($fontPx);", null)
                }
            },
        )

        // Debug status overlay — shows the most recent reader.html trace
        // (or the most recent JS console message) so a hang is visible
        // without logcat. Auto-clears once the article successfully
        // displays (onReady fires).
        debugStatus?.let { status ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xCC000000))
                    .padding(8.dp)
            ) {
                Text(
                    text = status,
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        // Three tap-zone thirds: left = prev page, centre = toggle chrome,
        // right = next page. Always above the WebView in z-order.
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { webView?.evaluateJavascript("goPrev();", null) }
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
                    ) { webView?.evaluateJavascript("goNext();", null) }
            )
        }
    }
}

/** Bridge for callbacks from reader.html into Kotlin. */
private class MagazineReaderBridge(
    private val readyCallback: () -> Unit,
    private val errorCallback: (String) -> Unit,
    private val traceCallback: (String) -> Unit,
) {
    @JavascriptInterface
    fun onReady(payload: String) {
        readyCallback()
    }

    @JavascriptInterface
    fun onRelocated(cfi: String) {
        // Position tracking placeholder — could persist last-page-cfi here.
    }

    @JavascriptInterface
    fun onError(message: String) {
        errorCallback(message)
    }

    /** Stage trace from reader.html — consumed by the on-screen debug overlay. */
    @JavascriptInterface
    fun onTrace(message: String) {
        traceCallback(message)
    }
}

/** Properly JSON-escape a string for embedding in an evaluateJavascript call. */
private fun quoteJs(value: String): String {
    val sb = StringBuilder(value.length + 2)
    sb.append('"')
    for (c in value) {
        when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            ' ' -> sb.append("\\u2028")
            ' ' -> sb.append("\\u2029")
            else -> if (c.code < 0x20) {
                sb.append("\\u%04x".format(c.code))
            } else {
                sb.append(c)
            }
        }
    }
    sb.append('"')
    return sb.toString()
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
 * Returns a textZoom percentage that the Compose layer hands to the JS side.
 */
internal fun defaultTextZoomForScreenWidth(screenWidthDp: Int): Int = when {
    screenWidthDp < 380 -> 100
    screenWidthDp < 480 -> 110
    screenWidthDp < 700 -> 125
    else -> 140
}
