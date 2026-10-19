package com.hiwar.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hiwar.app.data.HiwarRepository
import com.hiwar.app.data.remote.dto.ProgressDto
import com.hiwar.app.data.remote.dto.ScenarioDto
import com.hiwar.app.data.remote.dto.ScheduleDto
import com.hiwar.app.di.AppContainer
import com.hiwar.app.ui.common.ChoiceChips
import com.hiwar.app.ui.common.ErrorBox
import com.hiwar.app.ui.common.InfoCard
import com.hiwar.app.ui.common.SectionTitle
import com.hiwar.app.ui.common.formatInstant
import com.hiwar.app.ui.theme.HiwarInk
import com.hiwar.app.ui.theme.HiwarNavy
import com.hiwar.app.ui.theme.HiwarSaffron
import com.hiwar.app.ui.theme.HiwarSand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeData(
    val scenarios: List<ScenarioDto> = emptyList(),
    val progress: ProgressDto? = null,
    val schedules: List<ScheduleDto> = emptyList(),
    val error: String? = null,
)

class HomeViewModel(private val repository: HiwarRepository) : ViewModel() {
    private val _data = MutableStateFlow(HomeData())
    val data = _data.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val scenarios = repository.scenarios()
            val progress = repository.progress()
            val schedules = repository.schedules()
            _data.value = HomeData(
                scenarios = scenarios.getOrDefault(emptyList()),
                progress = progress.getOrNull(),
                schedules = schedules.getOrDefault(emptyList()).filter { it.active && it.nextRunAt != null }
                    .sortedBy { it.nextRunAt },
                error = scenarios.exceptionOrNull()?.message,
            )
        }
    }
}

private const val FREE = "__free__"

@Composable
fun HomeScreen(
    onStart: (topic: String?, level: String, minutes: Int, scenario: String?) -> Unit,
    onOpenSchedules: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenProgress: () -> Unit,
) {
    val vm: HomeViewModel = viewModel { HomeViewModel(AppContainer.repository) }
    val data by vm.data.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }

    var scenario by remember { mutableStateOf(FREE) }
    var level by remember { mutableStateOf("beginner") }
    var minutes by remember { mutableIntStateOf(5) }
    var topic by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // ---- header
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Hiwar", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("حوار · speak with Fo", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            data.progress?.let { p ->
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(HiwarSaffron).padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("🔥 ${p.currentStreakDays}", color = HiwarInk, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("day streak", color = HiwarInk, fontSize = 11.sp)
                }
            }
        }

        data.error?.let { ErrorBox(it, onRetry = vm::refresh) }

        // ---- start a session
        Spacer(Modifier.height(20.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.linearGradient(listOf(HiwarInk, HiwarNavy)))
                .padding(20.dp)
        ) {
            Text("Practice now", color = HiwarSand, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Fo will call your phone", color = HiwarSand.copy(alpha = 0.7f), fontSize = 13.sp)
        }

        SectionTitle("Scene")
        ChoiceChips(
            options = listOf(FREE to "💬 Free conversation") + data.scenarios.map { it.id to "${iconFor(it.icon)} ${it.title}" },
            selected = scenario,
            onSelect = { scenario = it },
        )
        data.scenarios.firstOrNull { it.id == scenario }?.let {
            Text(it.description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }

        OutlinedTextField(
            value = topic,
            onValueChange = { topic = it },
            label = { Text(if (scenario == FREE) "Topic (optional)" else "Extra detail for the scene (optional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )

        SectionTitle("Level")
        ChoiceChips(
            options = listOf("beginner" to "Beginner", "intermediate" to "Intermediate", "advanced" to "Advanced"),
            selected = level, onSelect = { level = it },
        )

        SectionTitle("Length")
        ChoiceChips(
            options = listOf(3, 5, 10, 15, 20).map { it to "$it min" },
            selected = minutes, onSelect = { minutes = it },
        )

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onStart(topic, level, minutes, scenario.takeIf { it != FREE }) },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
        ) { Text("📞  Call me now", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }

        // ---- upcoming
        SectionTitle("Upcoming")
        if (data.schedules.isEmpty()) {
            InfoCard(onClick = onOpenSchedules) {
                Text("No sessions scheduled yet")
                Text("Tap to set up daily or weekly calls", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        } else {
            data.schedules.take(3).forEach { s ->
                InfoCard(onClick = onOpenSchedules, modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(s.title, fontWeight = FontWeight.SemiBold)
                    Text("${formatInstant(s.nextRunAt)} · ${s.durationMinutes} min", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
        }

        // ---- navigation
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onOpenSchedules, modifier = Modifier.weight(1f)) { Text("Schedule") }
            OutlinedButton(onClick = onOpenLibrary, modifier = Modifier.weight(1f)) { Text("Saved") }
            OutlinedButton(onClick = onOpenProgress, modifier = Modifier.weight(1f)) { Text("Progress") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

fun iconFor(icon: String): String = when (icon) {
    "restaurant" -> "🍽️"
    "taxi" -> "🚕"
    "shopping" -> "🛍️"
    "medical" -> "🩺"
    "hotel" -> "🏨"
    "map" -> "🧭"
    "people" -> "👋"
    "work" -> "💼"
    else -> "🎭"
}
