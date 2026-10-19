package com.hiwar.app.ui.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiwar.app.data.HiwarRepository
import com.hiwar.app.data.remote.dto.SessionDto
import com.hiwar.app.data.remote.dto.StartSessionRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Drives the live session: start it, follow it while Fo is on the call, report when it's done. */
sealed interface SessionUiState {
    data object Idle : SessionUiState
    data object Starting : SessionUiState
    data class Live(val session: SessionDto) : SessionUiState      // show session.progress
    data class Finished(val session: SessionDto) : SessionUiState  // open the breakdown
    data class Error(val message: String) : SessionUiState
}

class PracticeViewModel(private val repository: HiwarRepository) : ViewModel() {

    private val _state = MutableStateFlow<SessionUiState>(SessionUiState.Idle)
    val state: StateFlow<SessionUiState> = _state.asStateFlow()
    private var watching: Job? = null
    private var currentId: Int? = null

    /** If a (scheduled) session is already running when the app opens, follow it. */
    fun resumeIfRunning() {
        if (_state.value != SessionUiState.Idle) return
        viewModelScope.launch {
            repository.activeSession().getOrNull()?.let { watch(it.id) }
        }
    }

    /** scenario = id from repository.scenarios(), or null for free conversation. */
    fun start(topic: String?, level: String, minutes: Int, scenario: String?, emailReport: Boolean = false) {
        _state.value = SessionUiState.Starting
        viewModelScope.launch {
            repository.startSession(
                StartSessionRequest(topic = topic?.ifBlank { null }, scenario = scenario, level = level, durationMinutes = minutes, emailReport = emailReport)
            )
                .onSuccess { watch(it.id) }
                .onFailure { error ->
                    // A session is already running (e.g. you left the call screen): reopen it instead of failing.
                    val running = repository.activeSession().getOrNull()
                    if (running != null) watch(running.id)
                    else _state.value = SessionUiState.Error(error.message ?: "Couldn't start the session")
                }
        }
    }

    private fun watch(sessionId: Int) {
        currentId = sessionId
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

    /** Cancel the running session on the backend and go back to idle. */
    fun cancel() {
        val id = currentId
        watching?.cancel()
        _state.value = SessionUiState.Idle
        currentId = null
        if (id != null) viewModelScope.launch { repository.cancelSession(id) }
    }

    fun reset() {
        watching?.cancel()
        currentId = null
        _state.value = SessionUiState.Idle
    }
}
