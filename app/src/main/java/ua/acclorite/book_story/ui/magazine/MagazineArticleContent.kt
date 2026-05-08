/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.magazine

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ua.acclorite.book_story.data.parser.magazine.MagazineWebViewClient
import ua.acclorite.book_story.presentation.magazine.MagazineArticleState
import java.io.File

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MagazineArticleContent(
    state: MagazineArticleState,
    onPrev: () -> Unit,
    onHome: () -> Unit,
    onNext: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        MagazineHeaderBar(
            onPrev = onPrev,
            onHome = onHome,
            onNext = onNext,
            prevEnabled = state.hasPrev,
            homeEnabled = true,
            nextEnabled = state.hasNext,
            centerText = state.article?.category,
        )

        Box(modifier = Modifier.fillMaxSize().fillMaxWidth()) {
            when {
                state.isLoading -> CenteredText("Loading…")
                state.errorMessage != null -> CenteredText(state.errorMessage)
                state.chapterHtml != null && state.epubPath != null && state.issue != null ->
                    ChapterWebView(
                        epubPath = state.epubPath,
                        opfDir = state.issue.opfDir,
                        chapterHref = state.article?.contentHref ?: return@Box,
                        html = state.chapterHtml,
                    )
                else -> CenteredText("No content.")
            }
        }
    }
}

@Composable
private fun ChapterWebView(
    epubPath: String,
    opfDir: String,
    chapterHref: String,
    html: String,
) {
    var client by remember { mutableStateOf<MagazineWebViewClient?>(null) }

    DisposableEffect(epubPath, opfDir) {
        val c = MagazineWebViewClient(File(epubPath), opfDir)
        client = c
        onDispose {
            c.close()
            client = null
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                settings.useWideViewPort = false
                settings.loadWithOverviewMode = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        },
        update = { webView ->
            val c = client ?: return@AndroidView
            webView.webViewClient = c
            val baseUrl = if (opfDir.isEmpty()) {
                "${MagazineWebViewClient.BASE_URL}/"
            } else {
                "${MagazineWebViewClient.BASE_URL}/$opfDir/"
            }
            webView.loadDataWithBaseURL(baseUrl + chapterHref, html, "text/html", "UTF-8", null)
        },
    )
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
