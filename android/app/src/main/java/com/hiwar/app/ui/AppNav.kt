package com.hiwar.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hiwar.app.di.AppContainer
import com.hiwar.app.ui.home.HomeScreen
import com.hiwar.app.ui.library.LibraryScreen
import com.hiwar.app.ui.practice.LiveSessionScreen
import com.hiwar.app.ui.practice.PracticeViewModel
import com.hiwar.app.ui.practice.SessionUiState
import com.hiwar.app.ui.progress.ProgressScreen
import com.hiwar.app.ui.schedules.ScheduleEditorScreen
import com.hiwar.app.ui.schedules.SchedulesScreen
import com.hiwar.app.ui.session.SessionDetailScreen

/** Every screen in the app. A simple back stack of these replaces a navigation library. */
sealed interface Screen {
    data object Home : Screen
    data object Live : Screen
    data class SessionDetail(val sessionId: Int) : Screen
    data object Schedules : Screen
    data object NewSchedule : Screen
    data object Library : Screen
    data object Progress : Screen
}

@Composable
fun HiwarAppNav() {
    val backStack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val current = backStack.last()

    fun go(screen: Screen) { backStack.add(screen) }
    fun back() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
    fun replaceTop(screen: Screen) { backStack[backStack.lastIndex] = screen }

    BackHandler(enabled = backStack.size > 1) { back() }

    // One practice view model for the whole app, so a running call survives screen changes.
    val practice: PracticeViewModel = viewModel { PracticeViewModel(AppContainer.repository) }
    val practiceState by practice.state.collectAsState()

    // On launch: if a scheduled session is already running, jump to the live screen.
    LaunchedEffect(Unit) { practice.resumeIfRunning() }
    LaunchedEffect(practiceState) {
        when (val s = practiceState) {
            is SessionUiState.Live, SessionUiState.Starting -> if (current != Screen.Live) go(Screen.Live)
            is SessionUiState.Finished -> {
                if (current == Screen.Live) replaceTop(Screen.SessionDetail(s.session.id)) else go(Screen.SessionDetail(s.session.id))
                practice.reset()
            }
            else -> Unit
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.safeDrawingPadding()) {
            when (val screen = current) {
                Screen.Home -> HomeScreen(
                    onStart = { topic, level, minutes, scenario -> practice.start(topic, level, minutes, scenario) },
                    onOpenSchedules = { go(Screen.Schedules) },
                    onOpenLibrary = { go(Screen.Library) },
                    onOpenProgress = { go(Screen.Progress) },
                )
                Screen.Live -> LiveSessionScreen(
                    state = practiceState,
                    onDone = { practice.reset(); back() },
                )
                is Screen.SessionDetail -> SessionDetailScreen(sessionId = screen.sessionId, onBack = ::back)
                Screen.Schedules -> SchedulesScreen(onBack = ::back, onNew = { go(Screen.NewSchedule) })
                Screen.NewSchedule -> ScheduleEditorScreen(onBack = ::back, onSaved = ::back)
                Screen.Library -> LibraryScreen(onBack = ::back, onOpen = { go(Screen.SessionDetail(it)) })
                Screen.Progress -> ProgressScreen(onBack = ::back, onOpenSession = { go(Screen.SessionDetail(it)) })
            }
        }
    }
}
