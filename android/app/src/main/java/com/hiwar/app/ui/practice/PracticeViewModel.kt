package com.hiwar.app.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiwar.app.data.HiwarRepository
import com.hiwar.app.data.remote.dto.SessionDto
import com.hiwar.app.data.remote.dto.StartSessionRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the live session screen: start a session, follow it while Fo is on
 * the call, then show the breakdown. The schedule and saved-library screens are
 * simpler: call the repository once and put the Result in state.
 */
sealed interface SessionUiState {
    data object Idle : SessionUiState
    data class Live(val session: SessionDto) : SessionUiState      // show session.progress
    data class Finished(val session: SessionDto) : SessionUiState  // show breakdown, transcript, save toggle
    data class Error(val message: String) : SessionUiState
}

@HiltViewModel
class PracticeViewModel @Inject constructor(
    private val repository: HiwarRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<SessionUiState>(SessionUiState.Idle)
    val state: StateFlow<SessionUiState> = _state.asStateFlow()
    private var watching: Job? = null

    init {
        // If a scheduled session is already running when the app opens, jump straight to it.
        viewModelScope.launch {
            repository.activeSession().getOrNull()?.let { watch(it.id) }
        }
    }

    /** Pass a scenario id (from repository.scenarios()) for a role-play, or null for free conversation. */
    fun start(topic: String?, level: String, minutes: Int, scenario: String? = null) {
        viewModelScope.launch {
            repository.startSession(
                StartSessionRequest(topic = topic?.ifBlank { null }, scenario = scenario, level = level, durationMinutes = minutes)
            )
                .onSuccess { watch(it.id) }
                .onFailure { _state.value = SessionUiState.Error(it.message ?: "Couldn't start the session") }
        }
    }

    /** Also use this to open a past session (e.g. from history). */
    fun watch(sessionId: Int) {
        watching?.cancel()
        watching = viewModelScope.launch {
            repository.watchSession(sessionId).collect { result ->
                result
                    .onSuccess { s ->
                        _state.value = when (s.status) {
                            "complete" -> SessionUiState.Finished(s)
                            "failed" -> SessionUiState.Error(s.progress ?: "The session failed")
                            else -> SessionUiState.Live(s)
                        }
                    }
                    // A network blip mid-call shouldn't wipe the live screen; keep the last state.
                    .onFailure { if (_state.value !is SessionUiState.Live) _state.value = SessionUiState.Error(it.message ?: "Network error") }
            }
        }
    }

    /** Change your mind after the call: save or unsave from the breakdown screen. */
    fun setSaved(saved: Boolean) {
        val current = (_state.value as? SessionUiState.Finished)?.session ?: return
        viewModelScope.launch {
            repository.setSaved(current.id, saved).onSuccess { _state.value = SessionUiState.Finished(it) }
        }
    }

    fun reset() {
        watching?.cancel()
        _state.value = SessionUiState.Idle
    }
}
