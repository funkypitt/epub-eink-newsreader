/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

@file:Suppress("RemoveExplicitTypeArguments")

package ua.acclorite.book_story.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ua.acclorite.book_story.core.data.CoreData
import ua.acclorite.book_story.core.language.Language
import ua.acclorite.book_story.core.language.LanguageUtils
import ua.acclorite.book_story.core.log.logI
import ua.acclorite.book_story.data.local.data_store.DataStore
import ua.acclorite.book_story.data.settings.model.Setting
import ua.acclorite.book_story.presentation.browse.model.BrowseLayout
import ua.acclorite.book_story.presentation.browse.model.BrowseSortOrder
import ua.acclorite.book_story.presentation.library.model.LibraryLayout
import ua.acclorite.book_story.presentation.library.model.LibrarySortOrder
import ua.acclorite.book_story.presentation.library.model.LibraryTitlePosition
import ua.acclorite.book_story.ui.theme.model.DarkTheme
import ua.acclorite.book_story.ui.theme.model.PureDark
import ua.acclorite.book_story.ui.theme.model.Theme
import ua.acclorite.book_story.ui.theme.model.ThemeContrast
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SettingsManager"

@Suppress("UNCHECKED_CAST")
@Singleton
class SettingsManager @Inject constructor(
    private val dataStore: DataStore
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val settingsCount = AtomicInteger(0)
    private val initializedSettingsCount = AtomicInteger(0)

    private val _initialized = MutableStateFlow(false)
    val initialized = _initialized.asStateFlow()


    /* ------ Settings --------------------------- */
    /* ------ General ---------------------------- */
    val language = setting<Language, String>(
        key = stringPreferencesKey("language"),
        default = LanguageUtils.findLanguage(
            languages = CoreData.languages,
            locale = Locale.getDefault(),
            defaultLanguage = CoreData.defaultLanguage
        ),
        serialize = { it.locale.toLanguageTag() },
        deserialize = { languageTag ->
            val locale = Locale.forLanguageTag(languageTag)
            LanguageUtils.findLanguage(
                languages = CoreData.languages,
                locale = locale,
                defaultLanguage = CoreData.defaultLanguage
            )
        }
    )
    val theme = setting<Theme, String>(
        key = stringPreferencesKey("theme"), default = Theme.entries().first(),
        serialize = { it.name }, deserialize = { Theme.valueOf(it) }
    )
    val darkTheme = setting<DarkTheme, String>(
        key = stringPreferencesKey("dark_theme"), default = DarkTheme.OFF,
        serialize = { it.name }, deserialize = { DarkTheme.valueOf(it) }
    )
    val pureDark = setting<PureDark, String>(
        key = stringPreferencesKey("pure_dark"), default = PureDark.OFF,
        serialize = { it.name }, deserialize = { PureDark.valueOf(it) }
    )
    val absoluteDark = setting<Boolean, Boolean>(
        key = booleanPreferencesKey("absolute_dark"), default = false
    )
    val themeContrast = setting<ThemeContrast, String>(
        key = stringPreferencesKey("theme_contrast"), default = ThemeContrast.STANDARD,
        serialize = { it.name }, deserialize = { ThemeContrast.valueOf(it) }
    )
    val showStartScreen = setting<Boolean, Boolean>(
        key = booleanPreferencesKey("guide"), default = true
    )
    val doublePressExit = setting<Boolean, Boolean>(
        key = booleanPreferencesKey("double_press_exit"), default = false
    )
    val showNavigationLabels = setting<Boolean, Boolean>(
        key = booleanPreferencesKey("show_navigation_labels"), default = true
    )

    /* ------ Library ---------------------------- */
    val libraryLayout = setting<LibraryLayout, String>(
        key = stringPreferencesKey("library_layout"), default = LibraryLayout.GRID,
        serialize = { it.name }, deserialize = { LibraryLayout.valueOf(it) }
    )
    val libraryAutoGridSize = setting<Boolean, Boolean>(
        key = booleanPreferencesKey("library_auto_grid_size"), default = true
    )
    val libraryGridSize = setting<Int, Int>(
        key = intPreferencesKey("library_grid_size"), default = 0
    )
    val libraryTitlePosition = setting<LibraryTitlePosition, String>(
        key = stringPreferencesKey("library_title_position"), default = LibraryTitlePosition.BELOW,
        serialize = { it.name }, deserialize = { LibraryTitlePosition.valueOf(it) }
    )
    val libraryShowReadButton = setting<Boolean, Boolean>(
        key = booleanPreferencesKey("library_show_read_button"), default = true
    )
    val libraryShowProgress = setting<Boolean, Boolean>(
        key = booleanPreferencesKey("library_show_progress"), default = true
    )
    val libraryShowBookCount = setting<Boolean, Boolean>(
        key = booleanPreferencesKey("library_show_book_count"), default = true
    )
    val librarySortOrder = setting<LibrarySortOrder, String>(
        // NAME descending = reverse-chronological for date-prefixed filenames
        // (e.g. 2026-04-25-economist.epub) — newspapers land newest-first.
        key = stringPreferencesKey("library_sort_order"), default = LibrarySortOrder.NAME,
        serialize = { it.name }, deserialize = { LibrarySortOrder.valueOf(it) }
    )
    val librarySortOrderDescending = setting<Boolean, Boolean>(
        key = booleanPreferencesKey("library_sort_order_descending"), default = true
    )

    /* ------ Browse ----------------------------- */
    val browseLayout = setting<BrowseLayout, String>(
        key = stringPreferencesKey("browse_layout"), default = BrowseLayout.LIST,
        serialize = { it.name }, deserialize = { BrowseLayout.valueOf(it) }
    )
    val browseAutoGridSize = setting<Boolean, Boolean>(
        key = booleanPreferencesKey("browse_auto_grid_size"), default = true
    )
    val browseGridSize = setting<Int, Int>(
        key = intPreferencesKey("browse_grid_size"), default = 0
    )
    val browseSortOrder = setting<BrowseSortOrder, String>(
        key = stringPreferencesKey("browse_sort_order"), default = BrowseSortOrder.LAST_MODIFIED,
        serialize = { it.name }, deserialize = { BrowseSortOrder.valueOf(it) }
    )
    val browseSortOrderDescending = setting<Boolean, Boolean>(
        key = booleanPreferencesKey("browse_sort_order_descending"), default = true
    )
    val browseIncludedFilterItems = setting<List<String>, Set<String>>(
        key = stringSetPreferencesKey("browse_included_filter_items"), default = emptyList(),
        serialize = { it.toSet() }, deserialize = { it.toList() }
    )
    val browsePinnedPaths = setting<List<String>, Set<String>>(
        key = stringSetPreferencesKey("browse_pinned_paths"), default = emptyList(),
        serialize = { it.toSet() }, deserialize = { it.toList() }
    )
    /* - - - - - - - - - - - - - - - - - - - - - - */


    private fun <T, P> setting(
        key: Preferences.Key<P>,
        default: T,
        serialize: (T) -> P = { it as P },
        deserialize: (P) -> T = { it as T }
    ): Setting<T, P> {
        settingsCount.incrementAndGet()

        return Setting<T, P>(
            key = key,
            default = default,
            setSetting = {
                scope.launch {
                    logI(TAG, "Updating setting: [${key.name}].")
                    dataStore.putData(key, it)
                }
            },
            serialize = serialize,
            deserialize = deserialize
        ).also { setting ->
            scope.launch {
                setting.init(dataStore.getNullableData<P>(key))
                logI(TAG, "Successfully initialized setting: [${key.name}].")
                initializeSetting()
            }
        }
    }

    private fun initializeSetting() {
        if (initializedSettingsCount.incrementAndGet() == settingsCount.get()) {
            logI(TAG, "Successfully initialized all $settingsCount settings.")
            _initialized.update { true }
        }
    }
}
