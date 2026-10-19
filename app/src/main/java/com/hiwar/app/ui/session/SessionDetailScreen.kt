package com.hiwar.app.ui.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hiwar.app.data.HiwarRepository
import com.hiwar.app.data.remote.dto.MistakeDto
import com.hiwar.app.data.remote.dto.SessionDto
import com.hiwar.app.data.remote.dto.ShareCardDto
import com.hiwar.app.di.AppContainer
import com.hiwar.app.ui.common.ErrorBox
import com.hiwar.app.ui.common.InfoCard
import com.hiwar.app.ui.common.LoadingBox
import com.hiwar.app.ui.common.SectionTitle
import com.hiwar.app.ui.common.Tag
import com.hiwar.app.ui.common.TopBar
import com.hiwar.app.ui.common.formatInstant
import com.hiwar.app.ui.common.levelLabel
import com.hiwar.app.ui.share.ShareCardWithButton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SessionDetailState(
    val loading: Boolean = true,
    val session: SessionDto? = null,
    val card: ShareCardDto? = null,
    val error: String? = null,
)

class SessionDetailViewModel(private val repository: HiwarRepository, private val sessionId: Int) : ViewModel() {
    private val _state = MutableStateFlow(SessionDetailState())
    val state = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            repository.session(sessionId)
                .onSuccess { s ->
                    val card = if (s.status == "complete") repository.shareCard(sessionId).getOrNull() else null
                    _state.value = SessionDetailState(loading = false, session = s, card = card)
                }
                .onFailure { _state.value = SessionDetailState(loading = false, error = it.message) }
        }
    }

    fun setSaved(saved: Boolean) {
        viewModelScope.launch {
            repository.setSaved(sessionId, saved).onSuccess { _state.value = _state.value.copy(session = it) }
        }
    }
}

@Composable
fun SessionDetailScreen(sessionId: Int, onBack: () -> Unit) {
    val vm: SessionDetailViewModel = viewModel(key = "session-$sessionId") {
        SessionDetailViewModel(AppContainer.repository, sessionId)
    }
    val state by vm.state.collectAsState()
    LaunchedEffect(sessionId) { vm.load() }

    Column(Modifier.fillMaxSize()) {
        TopBar("Your breakdown", onBack = onBack)
        when {
            state.loading && state.session == null -> LoadingBox()
            state.error != null -> ErrorBox(state.error!!, onRetry = vm::load)
            state.session != null -> SessionBody(state.session!!, state.card, onSavedChange = vm::setSaved)
        }
    }
}

@Composable
private fun SessionBody(s: SessionDto, card: ShareCardDto?, onSavedChange: (Boolean) -> Unit) {
    val b = s.breakdown
    var showTranscript by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Text(
            listOfNotNull(card?.label ?: s.topic, "${s.durationMinutes} min", levelLabel(s.level)).joinToString(" · "),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
        )
        Text(formatInstant(s.startedAt), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)

        // Save toggle (Fo asked on the call; you can change your mind here)
        InfoCard(modifier = Modifier.padding(top = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Saved to your library", fontWeight = FontWeight.SemiBold)
                    Text(
                        when (s.saved) { true -> "You said yes on the call"; false -> "You said no on the call"; null -> "You didn't answer on the call" },
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                    )
                }
                Switch(checked = s.saved == true, onCheckedChange = onSavedChange)
            }
        }

        if (b == null) {
            Text("No breakdown for this session.", modifier = Modifier.padding(top = 24.dp))
            return@Column
        }

        if (b.summary.isNotBlank()) {
            SectionTitle("Summary")
            Text(b.summary)
        }
        if (b.fluency.isNotBlank()) {
            SectionTitle("Fluency")
            Text(b.fluency)
        }

        if (b.mistakes.isNotEmpty()) {
            SectionTitle("Mistakes · ${b.mistakes.size}")
            b.mistakes.forEach { MistakeCard(it); Spacer(Modifier.height(8.dp)) }
        }

        if (b.highlights.isNotEmpty()) {
            SectionTitle("Highlights")
            b.highlights.forEach { Bullet("✅", it) }
        }
        if (b.improvements.isNotEmpty()) {
            SectionTitle("To improve")
            b.improvements.forEach { Bullet("🎯", it) }
        }
        if (b.vocabulary.isNotEmpty()) {
            SectionTitle("Vocabulary")
            b.vocabulary.forEach { v ->
                InfoCard(modifier = Modifier.padding(bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(v.word, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.padding(start = 12.dp))
                        Text(v.meaning, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (v.note.isNotBlank()) Text(v.note, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (b.nextFocus.isNotBlank()) {
            SectionTitle("Focus for next time")
            InfoCard { Text(b.nextFocus, fontWeight = FontWeight.SemiBold) }
        }

        if (!s.transcript.isNullOrBlank()) {
            SectionTitle("Transcript")
            TextButton(onClick = { showTranscript = !showTranscript }) {
                Text(if (showTranscript) "Hide transcript" else "Show transcript")
            }
            if (showTranscript) {
                InfoCard {
                    s.transcript!!.lines().filter { it.isNotBlank() }.forEach { line ->
                        val isFo = line.startsWith("Fo:")
                        Text(
                            line,
                            fontSize = 15.sp,
                            color = if (isFo) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                }
            }
        }

        if (card != null) {
            SectionTitle("Share")
            ShareCardWithButton(card, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun MistakeCard(m: MistakeDto) {
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tag(m.category)
        }
        Spacer(Modifier.height(8.dp))
        Text(m.said, fontSize = 18.sp, color = MaterialTheme.colorScheme.error, textDecoration = TextDecoration.LineThrough)
        Text(m.correction, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.tertiary)
        if (m.explanation.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(m.explanation, fontSize = 14.sp)
        }
        if (m.example.isNotBlank()) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Example: ${m.example}", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Bullet(icon: String, text: String) {
    Row(Modifier.padding(vertical = 4.dp)) {
        Text(icon, modifier = Modifier.padding(end = 8.dp))
        Text(text)
    }
}
