/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.open_book

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ua.acclorite.book_story.data.parser.magazine.MagazineParser
import ua.acclorite.book_story.domain.service.FileProvider
import ua.acclorite.book_story.domain.use_case.book.GetBookUseCase
import java.io.File
import javax.inject.Inject

sealed class OpenBookTarget {
    abstract val bookId: Int

    /**
     * @param resumeArticleHref the last article the user was reading, or null
     * to land on the table of contents.
     */
    data class Magazine(
        override val bookId: Int,
        val resumeArticleHref: String? = null,
    ) : OpenBookTarget()

    data class Unsupported(override val bookId: Int) : OpenBookTarget()
}

@HiltViewModel
class OpenBookModel @Inject constructor(
    private val getBook: GetBookUseCase,
    private val fileProvider: FileProvider,
    private val magazineParser: MagazineParser,
) : ViewModel() {

    /**
     * Resolves the target for [bookId]. Stateless on purpose: this ViewModel
     * is scoped to the Activity (the navigator provides no per-screen
     * ViewModelStoreOwner), so any cached/replayed state would leak between
     * successive opens and route a later book to a previously opened one.
     */
    suspend fun decide(bookId: Int): OpenBookTarget {
        val book = getBook(bookId)
        val isMagazine = book?.let {
            withContext(Dispatchers.IO) {
                runCatching {
                    val rawFile = resolveFile(it.filePath) ?: return@runCatching false
                    magazineParser.canParse(rawFile)
                }.getOrDefault(false)
            }
        } ?: false
        return if (isMagazine) OpenBookTarget.Magazine(
            bookId = bookId,
            resumeArticleHref = book?.currentArticleHref?.takeUnless { it.isBlank() },
        ) else OpenBookTarget.Unsupported(bookId)
    }

    private fun resolveFile(filePath: String): File? {
        val file = File(filePath)
        if (file.exists() && file.canRead()) return file
        return fileProvider.getFileFromBook(
            ua.acclorite.book_story.domain.model.library.Book.default.copy(filePath = filePath)
        ).getOrNull()?.rawFile
    }
}
