package com.hiwar.app.ui.schedules

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hiwar.app.data.HiwarRepository
import com.hiwar.app.data.remote.dto.ScheduleDto
import com.hiwar.app.di.AppContainer
import com.hiwar.app.ui.common.EmptyBox
import com.hiwar.app.ui.common.ErrorBox
import com.hiwar.app.ui.common.InfoCard
import com.hiwar.app.ui.common.LoadingBox
import com.hiwar.app.ui.common.TopBar
import com.hiwar.app.ui.common.WEEKDAY_SHORT
import com.hiwar.app.ui.common.formatDay
import com.hiwar.app.ui.common.formatInstant
import com.hiwar.app.ui.common.levelLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SchedulesState(
    val loading: Boolean = true,
    val schedules: List<ScheduleDto> = emptyList(),
    val error: String? = null,
)

class SchedulesViewModel(private val repository: HiwarRepository) : ViewModel() {
    private val _state = MutableStateFlow(SchedulesState())
    val state = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            repository.schedules()
                .onSuccess { _state.value = SchedulesState(loading = false, schedules = it) }
                .onFailure { _state.value = SchedulesState(loading = false, error = it.message) }
            // While Fo is adding a calendar event, check again shortly so the status updates.
            if (_state.value.schedules.any { it.calendarStatus == "adding" }) {
                delay(4_000)
                refresh()
            }
        }
    }

    fun setActive(s: ScheduleDto, active: Boolean) = act {
        if (active) repository.resumeSchedule(s.id) else repository.pauseSchedule(s.id)
    }

    fun delete(s: ScheduleDto) = act { repository.deleteSchedule(s.id) }

    fun addToCalendar(s: ScheduleDto) = act { repository.addToCalendar(s.id) }

    private fun act(block: suspend () -> Result<*>) {
        viewModelScope.launch {
            block().exceptionOrNull()?.let { _state.value = _state.value.copy(error = it.message) }
            refresh()
        }
    }
}

@Composable
fun SchedulesScreen(onBack: () -> Unit, onNew: () -> Unit) {
    val vm: SchedulesViewModel = viewModel { SchedulesViewModel(AppContainer.repository) }
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }

    Column(Modifier.fillMaxSize()) {
        TopBar("Schedules", onBack = onBack, action = { TextButton(onClick = onNew) { Text("+ New") } })
        state.error?.let { ErrorBox(it, onRetry = vm::refresh) }
        when {
            state.loading -> LoadingBox()
            state.schedules.isEmpty() -> EmptyBox("No schedules yet.\nTap “+ New” to have Fo call you daily or weekly.")
            else -> LazyColumn(Modifier.padding(horizontal = 20.dp)) {
                items(state.schedules, key = { it.id }) { s ->
                    ScheduleCard(
                        s,
                        onToggle = { vm.setActive(s, it) },
                        onDelete = { vm.delete(s) },
                        onCalendar = { vm.addToCalendar(s) },
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

fun describeRepeat(s: ScheduleDto): String = when (s.frequency) {
    "once" -> "Once on ${formatDay(s.startDate)}"
    "daily" -> "Every day"
    "weekly" -> "Every " + s.weekdays.joinToString(", ") { WEEKDAY_SHORT.getOrElse(it) { "?" } }
    else -> "Every ${s.intervalDays} days"
} + " at ${s.timeOfDay}"

@Composable
private fun ScheduleCard(s: ScheduleDto, onToggle: (Boolean) -> Unit, onDelete: () -> Unit, onCalendar: () -> Unit) {
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(describeRepeat(s), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text(
                    "${s.durationMinutes} min · ${levelLabel(s.level)}" + (s.scenario?.let { " · role-play" } ?: s.topic?.let { " · $it" } ?: ""),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                )
            }
            Switch(checked = s.active, onCheckedChange = onToggle)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (s.active && s.nextRunAt != null) "Next call: ${formatInstant(s.nextRunAt)}" else "Paused",
            fontSize = 13.sp, fontWeight = FontWeight.Medium,
        )

        // Google Calendar status (Fo adds the event)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
            val (label, canRetry) = when (s.calendarStatus) {
                "added" -> "📅 On your Google Calendar" to false
                "adding" -> "📅 Fo is adding it to your calendar…" to false
                "failed" -> "📅 Couldn't add to calendar" to true
                else -> "📅 Not on your calendar" to true
            }
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            if (canRetry) TextButton(onClick = onCalendar) { Text(if (s.calendarStatus == "failed") "Retry" else "Add") }
        }
        if (s.calendarStatus == "failed" && !s.calendarNote.isNullOrBlank()) {
            Text(s.calendarNote.orEmpty(), fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
        }
        TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
    }
}
