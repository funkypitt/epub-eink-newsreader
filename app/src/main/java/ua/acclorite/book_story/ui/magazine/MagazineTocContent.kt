/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.magazine

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import ua.acclorite.book_story.data.parser.magazine.EpubAssetFetcher
import ua.acclorite.book_story.data.parser.magazine.EpubAssetKey
import ua.acclorite.book_story.data.parser.magazine.MagazineArticle
import ua.acclorite.book_story.data.parser.magazine.MagazineIssue
import ua.acclorite.book_story.presentation.magazine.MagazineTocState

private val CARD_HEIGHT: Dp = 140.dp
private val SEPARATOR_HEIGHT: Dp = 1.dp
private val INDICATOR_HEIGHT: Dp = 32.dp
private val VERTICAL_PADDING: Dp = 8.dp

@Composable
fun MagazineTocContent(
    state: MagazineTocState,
    onArticleClick: (MagazineArticle) -> Unit,
    onHome: () -> Unit,
    onAppHome: () -> Unit,
) {
    val context = LocalContext.current

    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding()
    ) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            TocBody(state, context, onArticleClick, onHome)
        }
        MagazineFooterBar(
            onDecrease = null,        // no body text to scale on the TOC
            onAppHome = onAppHome,
            onIncrease = null,
        )
    }
}

@Composable
private fun TocBody(
    state: MagazineTocState,
    context: android.content.Context,
    onArticleClick: (MagazineArticle) -> Unit,
    onHome: () -> Unit,
) {
    when {
        state.isLoading -> CenteredText("Loading…")
        state.errorMessage != null -> CenteredText(state.errorMessage)
        state.issue != null && state.epubPath != null -> {
            val imageLoader = remember(state.epubPath) {
                ImageLoader.Builder(context)
                    .components { add(EpubAssetFetcher.Factory()) }
                    .build()
            }
            MagazineTocPager(
                issue = state.issue,
                epubPath = state.epubPath,
                imageLoader = imageLoader,
                currentArticleHref = state.currentArticleHref,
                onArticleClick = onArticleClick,
                onHome = onHome,
            )
        }
        else -> CenteredText("Nothing to display.")
    }
}

@Composable
private fun MagazineTocPager(
    issue: MagazineIssue,
    epubPath: String,
    imageLoader: ImageLoader,
    currentArticleHref: String?,
    onArticleClick: (MagazineArticle) -> Unit,
    onHome: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val available = maxHeight - MagazineHeaderHeight - INDICATOR_HEIGHT - (VERTICAL_PADDING * 2)
        val itemPlusSeparator = CARD_HEIGHT + SEPARATOR_HEIGHT
        val itemsPerPage = computeItemsPerPage(available, itemPlusSeparator)

        val allArticles = remember(issue) { issue.sections.flatMap { it.articles } }
        val pages = remember(allArticles, itemsPerPage) { allArticles.chunked(itemsPerPage) }

        val initialPage = remember(pages, currentArticleHref) {
            if (currentArticleHref == null) 0
            else pages.indexOfFirst { p -> p.any { it.contentHref == currentArticleHref } }
                .coerceAtLeast(0)
        }
        val pagerState = rememberPagerState(
            initialPage = initialPage,
            pageCount = { pages.size.coerceAtLeast(1) },
        )
        val scope = rememberCoroutineScope()
        val centerLabel = issue.title.ifBlank {
            listOfNotNull(
                issue.publisher.takeIf { it.isNotBlank() },
                issue.date?.toString(),
            ).joinToString(" — ")
        }

        Column(modifier = Modifier.fillMaxSize()) {
            MagazineHeaderBar(
                onPrev = {
                    if (pagerState.currentPage > 0) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    }
                },
                onHome = onHome,
                onNext = {
                    if (pagerState.currentPage < pages.lastIndex) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                prevEnabled = pagerState.currentPage > 0,
                homeEnabled = true,
                nextEnabled = pagerState.currentPage < pages.lastIndex,
                centerText = centerLabel,
            )
            Spacer(Modifier.height(VERTICAL_PADDING))

            if (pages.isNotEmpty()) {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = false,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                ) { pageIdx ->
                    Column {
                        pages[pageIdx].forEachIndexed { idx, article ->
                            ArticleCard(
                                article = article,
                                epubPath = epubPath,
                                opfDir = issue.opfDir,
                                imageLoader = imageLoader,
                                isActive = article.contentHref == currentArticleHref,
                                onClick = { onArticleClick(article) },
                            )
                            if (idx < pages[pageIdx].lastIndex) {
                                HorizontalDivider(thickness = SEPARATOR_HEIGHT)
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            Spacer(Modifier.height(VERTICAL_PADDING))
            PageIndicator(
                current = pagerState.currentPage + 1,
                total = pages.size,
                modifier = Modifier.height(INDICATOR_HEIGHT),
            )
        }
    }
}

@Composable
fun PageIndicator(
    current: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    if (total <= 1) {
        Spacer(modifier = modifier.fillMaxWidth())
        return
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$current / $total",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ArticleCard(
    article: MagazineArticle,
    epubPath: String,
    opfDir: String,
    imageLoader: ImageLoader,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CARD_HEIGHT)
            .let { if (isActive) it.border(BorderStroke(2.dp, borderColor)) else it }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        ) {
            Text(
                text = article.category,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = article.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            article.author?.takeIf { it.isNotBlank() }?.let { author ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = author,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (article.coverImageHref != null) {
            val request = remember(article.coverImageHref, epubPath, opfDir) {
                ImageRequest.Builder(context)
                    .data(EpubAssetKey(epubPath = epubPath, opfDir = opfDir, href = article.coverImageHref))
                    .build()
            }
            AsyncImage(
                model = request,
                imageLoader = imageLoader,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(CARD_HEIGHT - 24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
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

internal fun computeItemsPerPage(available: Dp, itemPlusSeparator: Dp): Int {
    if (itemPlusSeparator.value <= 0f) return 1
    return (available.value / itemPlusSeparator.value).toInt().coerceAtLeast(1)
}
