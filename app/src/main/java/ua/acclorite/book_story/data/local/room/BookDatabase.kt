/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.data.local.room

import android.app.Application
import androidx.room.Database
import androidx.room.RoomDatabase
import ua.acclorite.book_story.data.local.dto.BookEntity
import java.io.File

@Database(
    entities = [
        BookEntity::class,
    ],
    version = 18,
    exportSchema = true
)
abstract class BookDatabase : RoomDatabase() {
    abstract val bookDao: BookDao
}

@Suppress("ClassName")
object DatabaseHelper {

    /**
     * Books directory with extracted text content is no longer used by the magazine-only
     * reader; keep the cleanup helper so we still purge old data on startup.
     */
    fun removeBooksDir(application: Application) {
        val booksDir = File(application.filesDir, "books")
        if (booksDir.exists()) {
            booksDir.deleteRecursively()
        }
    }
}
