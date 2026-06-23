/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

package ua.acclorite.book_story.ui.library

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ua.acclorite.book_story.presentation.library.LibraryEvent
import ua.acclorite.book_story.ui.common.components.common.LazyColumnWithScrollbar
import ua.acclorite.book_story.ui.common.components.modal_bottom_sheet.ModalBottomSheet
import ua.acclorite.book_story.ui.settings.library.sort.LibrarySortSubcategory

@Composable
fun LibrarySortBottomSheet(
    dismissDialog: (LibraryEvent.OnDismissDialog) -> Unit
) {
    ModalBottomSheet(
        modifier = Modifier.fillMaxWidth(),
        onDismissRequest = {
            dismissDialog(LibraryEvent.OnDismissDialog)
        },
        sheetGesturesEnabled = true
    ) {
        LazyColumnWithScrollbar(modifier = Modifier.fillMaxWidth()) {
            LibrarySortSubcategory(
                showTitle = false,
                showDivider = false
            )
        }
    }
}
