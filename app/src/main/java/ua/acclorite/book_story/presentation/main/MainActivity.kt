/*
 * Book's Story — free and open-source Material You eBook reader.
 * Copyright (C) 2024-2026 Acclorite
 * SPDX-License-Identifier: GPL-3.0-only
 */

@file:Suppress("UnusedVariable", "unused")

package ua.acclorite.book_story.presentation.main

import android.annotation.SuppressLint
import android.content.Intent
import android.database.CursorWindow
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import ua.acclorite.book_story.R
import ua.acclorite.book_story.data.settings.SettingsManager
import ua.acclorite.book_story.presentation.browse.BrowseModel
import ua.acclorite.book_story.presentation.browse.BrowseScreen
import ua.acclorite.book_story.presentation.library.LibraryModel
import ua.acclorite.book_story.presentation.library.LibraryScreen
import ua.acclorite.book_story.presentation.navigator.NavigatorItem
import ua.acclorite.book_story.presentation.navigator.StackEvent
import ua.acclorite.book_story.presentation.open_book.OpenIntentScreen
import ua.acclorite.book_story.presentation.settings.SettingsModel
import ua.acclorite.book_story.presentation.start.StartScreen
import ua.acclorite.book_story.ui.common.components.navigation_bar.NavigationBar
import ua.acclorite.book_story.ui.common.components.navigation_rail.NavigationRail
import ua.acclorite.book_story.ui.common.helpers.ProvideSettings
import ua.acclorite.book_story.ui.main.MainActivityKeyboardManager
import ua.acclorite.book_story.ui.navigator.Navigator
import ua.acclorite.book_story.ui.navigator.NavigatorTabs
import ua.acclorite.book_story.ui.navigator.rememberNavigator
import ua.acclorite.book_story.ui.settings.SettingsEffects
import ua.acclorite.book_story.ui.theme.BookStoryTheme
import ua.acclorite.book_story.ui.theme.Transitions
import java.lang.reflect.Field
import javax.inject.Inject


@SuppressLint("DiscouragedPrivateApi")
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var settings: SettingsManager
    private val settingsModel: SettingsModel by viewModels()

    companion object {
        /**
         * Carries URIs from [onNewIntent] (warm starts) into the running
         * Composition so the navigator can push [OpenIntentScreen] without
         * recreating the activity. Conflated to keep only the most recent.
         */
        val newIntentUriChannel: Channel<String> = Channel(Channel.CONFLATED)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Update the activity's intent so subsequent restarts/recompositions
        // see the new payload.
        setIntent(intent)
        intent.takeIf { it.action == Intent.ACTION_VIEW }
            ?.data
            ?.toString()
            ?.let { newIntentUriChannel.trySend(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition {
            !settings.initialized.value || !settingsModel.initialized.value
        }

        super.onCreate(savedInstanceState)

        // Bigger Cursor size for Room
        try {
            val field: Field = CursorWindow::class.java.getDeclaredField("sCursorWindowSize")
            field.isAccessible = true
            field.set(null, 100 * 1024 * 1024)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Edge to edge
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Keep the display awake for the entire app lifetime while foregrounded.
        // Reading on e-ink devices makes Android's default screen timeout
        // hostile — applied at the activity window so every screen inherits.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // If launched via Android's "Open with" sheet (Telegram, Files, etc.)
        // capture the URI now so the navigator can route to OpenIntentScreen
        // instead of the regular Library/Start landing.
        val initialIntentUri = intent
            ?.takeIf { it.action == Intent.ACTION_VIEW }
            ?.data
            ?.toString()

        setContent {
            // Initializing Screen Models
            val libraryModel = hiltViewModel<LibraryModel>()
            val browseModel = hiltViewModel<BrowseModel>()

            SettingsEffects(
                effects = settingsModel.effects
            )

            ProvideSettings(settings) {
                val tabs = persistentListOf(
                    NavigatorItem(
                        screen = LibraryScreen,
                        title = R.string.library_screen,
                        tooltip = R.string.library_content_desc,
                        selectedIcon = R.drawable.library_screen_filled,
                        unselectedIcon = R.drawable.library_screen_outlined
                    ),
                    NavigatorItem(
                        screen = BrowseScreen,
                        title = R.string.browse_screen,
                        tooltip = R.string.browse_content_desc,
                        selectedIcon = R.drawable.browse_screen_filled,
                        unselectedIcon = R.drawable.browse_screen_outlined
                    )
                )

                MainActivityKeyboardManager()

                if (settings.initialized.collectAsStateWithLifecycle().value) {
                    BookStoryTheme(
                        theme = settings.theme.value,
                        isDark = settings.darkTheme.value.isDark(),
                        isPureDark = settings.pureDark.value.isPureDark(this),
                        themeContrast = settings.themeContrast.value
                    ) {
                        val initialScreen = when {
                            initialIntentUri != null ->
                                OpenIntentScreen(uriString = initialIntentUri)
                            settings.showStartScreen.value -> StartScreen
                            else -> LibraryScreen
                        }
                        // Same VM the Navigator composable resolves internally
                        // (viewModels<Navigator> is keyed to the activity), so
                        // we can push onto the running stack from here.
                        val navigator = rememberNavigator(initialScreen = initialScreen)
                        LaunchedEffect(navigator) {
                            newIntentUriChannel.receiveAsFlow().collect { uri ->
                                navigator.push(
                                    targetScreen = OpenIntentScreen(uriString = uri),
                                )
                            }
                        }
                        Navigator(
                            initialScreen = initialScreen,
                            transitionSpec = { lastEvent ->
                                when (lastEvent) {
                                    StackEvent.DEFAULT -> {
                                        Transitions.SlidingTransitionIn
                                            .togetherWith(Transitions.SlidingTransitionOut)
                                    }

                                    StackEvent.POP -> {
                                        Transitions.BackSlidingTransitionIn
                                            .togetherWith(Transitions.BackSlidingTransitionOut)
                                    }
                                }
                            },
                            contentKey = {
                                when (it) {
                                    LibraryScreen, BrowseScreen -> "tabs"
                                    else -> it
                                }
                            },
                            backHandlerEnabled = { it != StartScreen }
                        ) { screen ->
                            when (screen) {
                                LibraryScreen, BrowseScreen -> {
                                    NavigatorTabs(
                                        currentTab = screen,
                                        transitionSpec = {
                                            Transitions.FadeTransitionIn
                                                .togetherWith(Transitions.FadeTransitionOut)
                                        },
                                        navigationBar = {
                                            NavigationBar(
                                                tabs = tabs,
                                                showLabels = settings.showNavigationLabels.value
                                            )
                                        },
                                        navigationRail = {
                                            NavigationRail(
                                                tabs = tabs,
                                                showLabels = settings.showNavigationLabels.value
                                            )
                                        }
                                    ) { tab ->
                                        tab.Content()
                                    }
                                }

                                else -> {
                                    screen.Content()
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        cacheDir.deleteRecursively()
        super.onDestroy()
    }
}
