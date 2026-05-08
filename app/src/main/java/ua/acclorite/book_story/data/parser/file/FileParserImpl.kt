/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.parser.file

import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.data.model.file.CachedFile
import ua.acclorite.book_story.domain.model.library.Book
import javax.inject.Inject

private const val TAG = "FileParser"

class FileParserImpl @Inject constructor(
    private val epubFileParser: EpubFileParser,
) : FileParser {

    override suspend fun parse(cachedFile: CachedFile): Book? {
        if (!cachedFile.canAccess()) {
            logE(TAG, "File does not exist or no read access is granted.")
            return null
        }

        val fileFormat = ".${cachedFile.name.substringAfterLast(".")}".lowercase().trim()
        return when (fileFormat) {
            ".epub" -> epubFileParser.parse(cachedFile)
            else -> {
                logE(TAG, "Unsupported file format \"$fileFormat\" — magazine reader only handles .epub.")
                null
            }
        }
    }
}
