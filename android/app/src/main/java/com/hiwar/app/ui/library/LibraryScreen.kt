package com.hiwar.app.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hiwar.app.data.HiwarRepository
import com.hiwar.app.data.remote.dto.SessionDto
import com.hiwar.app.di.AppContainer
import com.hiwar.app.ui.common.EmptyBox
import com.hiwar.app.ui.common.ErrorBox
import com.hiwar.app.ui.common.InfoCard
import com.hiwar.app.ui.common.LoadingBox
import com.hiwar.app.ui.common.TopBar
import com.hiwar.app.ui.common.formatInstant
import com.hiwar.app.ui.common.levelLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LibraryState(
    val loading: Boolean = true,
    val savedOnly: Boolean = true,
    val sessions: List<SessionDto> = emptyList(),
    val error: String? = null,
)

class LibraryViewModel(private val repository: HiwarRepository) : ViewModel() {
    private val _state = MutableStateFlow(LibraryState())
    val state = _state.asStateFlow()

    fun load(savedOnly: Boolean = _state.value.savedOnly) {
        _state.value = _state.value.copy(loading = true, savedOnly = savedOnly, error = null)
        viewModelScope.launch {
            (if (savedOnly) repository.savedSessions() else repository.history())
                .onSuccess { list ->
                    _state.value = _state.value.copy(loading = false, sessions = list.filter { it.status == "complete" || it.status == "failed" })
                }
                .onFailure { _state.value = _state.value.copy(loading = false, error = it.message) }
        }
    }
}

@Composable
fun LibraryScreen(onBack: () -> Unit, onOpen: (Int) -> Unit) {
    val vm: LibraryViewModel = viewModel { LibraryViewModel(AppContainer.repository) }
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.load() }

    Column(Modifier.fillMaxSize()) {
        TopBar("Your sessions", onBack = onBack)
        Row(Modifier.padding(horizontal = 20.dp)) {
            FilterChip(selected = state.savedOnly, onClick = { vm.load(true) }, label = { Text("Saved") })
            Spacer(Modifier.padding(start = 8.dp))
            FilterChip(selected = !state.savedOnly, onClick = { vm.load(false) }, label = { Text("All") })
        }
        when {
            state.loading -> LoadingBox()
            state.error != null -> ErrorBox(state.error!!, onRetry = { vm.load() })
            state.sessions.isEmpty() -> EmptyBox(
                if (state.savedOnly) "No saved sessions yet.\nSay “yes” when Fo asks to save at the end of a call."
                else "No sessions yet. Start one from the home screen."
            )
            else -> LazyColumn(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                items(state.sessions, key = { it.id }) { s ->
                    SessionRow(s, onClick = { onOpen(s.id) })
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun SessionRow(s: SessionDto, onClick: () -> Unit) {
    InfoCard(onClick = onClick) {
        Text(formatInstant(s.startedAt), fontWeight = FontWeight.SemiBold)
        Text(
            listOfNotNull(
                if (s.scenario != null) "Role-play" else (s.topic ?: "Free conversation"),
                "${s.durationMinutes} min",
                levelLabel(s.level),
                if (s.mock) "sample" else null,
            ).joinToString(" · "),
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
        )
        if (s.status == "failed") {
            Text(s.progress ?: "Failed", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        } else {
            s.breakdown?.let { b ->
                Text(
                    "${b.mistakes.size} corrections · ${b.highlights.size} highlights",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.tertiary,
                )
                if (b.summary.isNotBlank()) {
                    Text(b.summary, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
