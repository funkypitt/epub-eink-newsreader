/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.presentation.open_book

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ua.acclorite.book_story.core.log.logE
import ua.acclorite.book_story.data.parser.magazine.MagazineParser
import java.io.File
import javax.inject.Inject

private const val TAG = "OpenIntent"

sealed class OpenIntentTarget {
    data class Magazine(val epubPath: String) : OpenIntentTarget()
    data class Failed(val reason: String) : OpenIntentTarget()
}

@HiltViewModel
class OpenIntentModel @Inject constructor(
    private val application: Application,
    private val magazineParser: MagazineParser,
) : ViewModel() {

    private val _target = MutableSharedFlow<OpenIntentTarget>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val target = _target.asSharedFlow()

    private var handled = false

    fun handle(context: Context, uriString: String) {
        if (handled) return
        handled = true
        viewModelScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val uri = Uri.parse(uriString)
                    val file = copyUriToImports(context, uri)
                        ?: return@runCatching OpenIntentTarget.Failed("Could not read the file.")
                    if (!magazineParser.canParse(file)) {
                        return@runCatching OpenIntentTarget.Failed(
                            "This ePub is not a supported magazine."
                        )
                    }
                    OpenIntentTarget.Magazine(file.absolutePath)
                }.getOrElse {
                    logE(TAG, "Failed to handle intent: ${it.message}")
                    OpenIntentTarget.Failed("Could not open the file.")
                }
            }
            _target.tryEmit(outcome)
        }
    }

    /**
     * Copies the content of [uri] into the app's `filesDir/imports/` so the
     * magazine reader can open it via plain [File] APIs after the calling
     * app's URI grant expires. Filename comes from `OpenableColumns.DISPLAY_NAME`
     * when available, otherwise a timestamp.
     */
    private fun copyUriToImports(context: Context, uri: Uri): File? {
        val displayName = queryDisplayName(context, uri) ?: "imported-${System.currentTimeMillis()}.epub"
        val safeName = displayName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val importsDir = File(application.filesDir, "imports").apply { mkdirs() }
        val target = File(importsDir, safeName)
        return runCatching {
            context.contentResolver.openInputStream(uri).use { input ->
                if (input == null) error("openInputStream returned null for $uri")
                target.outputStream().use { output -> input.copyTo(output) }
            }
            target
        }.getOrNull()
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()
    }
}
