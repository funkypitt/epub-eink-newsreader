/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.magazine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.acclorite.book_story.data.parser.magazine.MagazineParser
import ua.acclorite.book_story.domain.repository.BookRepository
import ua.acclorite.book_story.domain.service.FileProvider
import ua.acclorite.book_story.domain.use_case.book.GetBookUseCase
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject

@HiltViewModel
class MagazineArticleModel @Inject constructor(
    private val getBook: GetBookUseCase,
    private val fileProvider: FileProvider,
    private val magazineParser: MagazineParser,
    private val bookRepository: BookRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MagazineArticleState())
    val state = _state.asStateFlow()

    private var loadedBookId: Int? = null
    private var loadedPath: String? = null

    fun loadFromLibrary(bookId: Int, articleHref: String) {
        viewModelScope.launch {
            if (loadedBookId != bookId || _state.value.issue == null) {
                val book = getBook(bookId)
                if (book == null) {
                    _state.update { MagazineArticleState(isLoading = false, errorMessage = "Book #$bookId not found.") }
                    return@launch
                }
                val rawFile = withContext(Dispatchers.IO) {
                    fileProvider.getFileFromBook(book).getOrNull()?.rawFile
                }
                if (rawFile == null) {
                    _state.update {
                        MagazineArticleState(isLoading = false, errorMessage = "Could not access ePub file.")
                    }
                    return@launch
                }
                val issue = withContext(Dispatchers.IO) { magazineParser.parse(rawFile) }
                if (issue == null) {
                    _state.update {
                        MagazineArticleState(
                            isLoading = false,
                            errorMessage = "This ePub is not a magazine.",
                        )
                    }
                    return@launch
                }
                _state.update { it.copy(issue = issue, epubPath = rawFile.absolutePath) }
                loadedBookId = bookId
                loadedPath = null
            }
            selectArticle(articleHref)
        }
    }

    fun loadFromPath(epubPath: String, articleHref: String) {
        viewModelScope.launch {
            if (loadedPath != epubPath || _state.value.issue == null) {
                val rawFile = File(epubPath)
                if (!rawFile.exists()) {
                    _state.update {
                        MagazineArticleState(isLoading = false, errorMessage = "File not found: $epubPath")
                    }
                    return@launch
                }
                val issue = withContext(Dispatchers.IO) { magazineParser.parse(rawFile) }
                if (issue == null) {
                    _state.update {
                        MagazineArticleState(isLoading = false, errorMessage = "This ePub is not a magazine.")
                    }
                    return@launch
                }
                _state.update { it.copy(issue = issue, epubPath = rawFile.absolutePath) }
                loadedPath = epubPath
                loadedBookId = null
            }
            selectArticle(articleHref)
        }
    }

    fun goToOffset(offset: Int) {
        val s = _state.value
        val articles = s.issue?.sections?.flatMap { it.articles } ?: return
        val newIndex = (s.articleIndex + offset).coerceIn(0, articles.lastIndex)
        if (newIndex == s.articleIndex) return
        selectArticle(articles[newIndex].contentHref)
    }

    private fun selectArticle(articleHref: String) {
        viewModelScope.launch {
            val s = _state.value
            val issue = s.issue ?: return@launch
            val epubPath = s.epubPath ?: return@launch
            val articles = issue.sections.flatMap { it.articles }
            val idx = articles.indexOfFirst { it.contentHref == articleHref }
            if (idx == -1) {
                _state.update { it.copy(isLoading = false, errorMessage = "Article not found.") }
                return@launch
            }
            val article = articles[idx]
            val html = withContext(Dispatchers.IO) {
                runCatching { readChapterHtml(File(epubPath), issue.opfDir, article.contentHref) }
                    .getOrNull()
            }
            _state.update {
                it.copy(
                    isLoading = false,
                    article = article,
                    articleIndex = idx,
                    totalArticles = articles.size,
                    chapterHtml = html,
                    errorMessage = if (html == null) "Could not read article." else null,
                )
            }
            // Library mode only: remember last-read article for the highlight feature.
            loadedBookId?.let { bookId ->
                val book = getBook(bookId) ?: return@let
                if (book.currentArticleHref != articleHref) {
                    bookRepository.updateBook(book.copy(currentArticleHref = articleHref))
                }
            }
        }
    }

    private fun readChapterHtml(epubFile: File, opfDir: String, href: String): String {
        ZipFile(epubFile).use { zip ->
            val zipPath = if (opfDir.isEmpty()) href else "$opfDir/$href"
            val entry = zip.getEntry(zipPath) ?: error("Entry not found: $zipPath")
            return zip.getInputStream(entry).bufferedReader().use { it.readText() }
        }
    }
}
