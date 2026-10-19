package com.hiwar.app.ui.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hiwar.app.data.HiwarRepository
import com.hiwar.app.data.remote.dto.ProgressDto
import com.hiwar.app.di.AppContainer
import com.hiwar.app.ui.common.ErrorBox
import com.hiwar.app.ui.common.InfoCard
import com.hiwar.app.ui.common.LoadingBox
import com.hiwar.app.ui.common.SectionTitle
import com.hiwar.app.ui.common.TopBar
import com.hiwar.app.ui.common.formatDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProgressState(val loading: Boolean = true, val progress: ProgressDto? = null, val error: String? = null)

class ProgressViewModel(private val repository: HiwarRepository) : ViewModel() {
    private val _state = MutableStateFlow(ProgressState())
    val state = _state.asStateFlow()

    /** includeMock = true shows sample sessions too: handy while you have no real ones yet. */
    fun load(includeMock: Boolean = false) {
        viewModelScope.launch {
            repository.progress(includeMock)
                .onSuccess { _state.value = ProgressState(loading = false, progress = it) }
                .onFailure { _state.value = ProgressState(loading = false, error = it.message) }
        }
    }
}

@Composable
fun ProgressScreen(onBack: () -> Unit, onOpenSession: (Int) -> Unit) {
    val vm: ProgressViewModel = viewModel { ProgressViewModel(AppContainer.repository) }
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.load() }

    Column(Modifier.fillMaxSize()) {
        TopBar("Progress", onBack = onBack)
        when {
            state.loading -> LoadingBox()
            state.error != null -> ErrorBox(state.error!!, onRetry = { vm.load() })
            state.progress != null -> ProgressBody(state.progress!!, onOpenSession, onShowSample = { vm.load(includeMock = true) })
        }
    }
}

@Composable
private fun ProgressBody(p: ProgressDto, onOpenSession: (Int) -> Unit, onShowSample: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        if (p.totalSessions == 0) {
            InfoCard {
                Text("No finished sessions yet", fontWeight = FontWeight.SemiBold)
                Text("Your stats appear after your first call with Fo.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onShowSample) { Text("Show sample data") }
            }
        }

        // ---- big numbers
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("🔥 ${p.currentStreakDays}", "day streak", Modifier.weight(1f))
            StatTile("${p.totalMinutes}", "minutes spoken", Modifier.weight(1f))
            StatTile("${p.totalSessions}", "sessions", Modifier.weight(1f))
        }
        Text(
            "This week: ${p.sessionsThisWeek} sessions · ${p.minutesThisWeek} min · best streak ${p.longestStreakDays} days",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
        )

        // ---- improving?
        p.trend?.let { t ->
            SectionTitle("Are you improving?")
            InfoCard {
                Text("Mistakes per session", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text("${t.earlyAvgMistakes} → ${t.recentAvgMistakes}", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (t.recentAvgMistakes < t.earlyAvgMistakes) "Fewer mistakes than when you started 👏" else "Keep going: consistency wins",
                    fontSize = 13.sp,
                )
            }
        }

        // ---- last 7 days bar chart
        SectionTitle("Minutes, last 7 days")
        val maxMinutes = (p.last7Days.maxOfOrNull { it.minutes } ?: 0).coerceAtLeast(1)
        Row(
            Modifier.fillMaxWidth().height(140.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            p.last7Days.forEach { d ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    if (d.minutes > 0) Text("${d.minutes}", fontSize = 11.sp)
                    Box(
                        Modifier.width(22.dp)
                            .height((100f * d.minutes / maxMinutes).coerceAtLeast(3f).dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (d.minutes > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Text(formatDay(d.date).take(3), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // ---- mistakes by category
        val categories = p.mistakesByCategory.filterValues { it > 0 }.entries.sortedByDescending { it.value }
        if (categories.isNotEmpty()) {
            SectionTitle("What you get wrong most")
            val maxCount = categories.maxOf { it.value }
            categories.forEach { (name, count) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(name.replaceFirstChar { it.uppercase() }, modifier = Modifier.width(110.dp), fontSize = 14.sp)
                    Box(Modifier.weight(1f).height(16.dp)) {
                        Box(
                            Modifier.fillMaxHeight().fillMaxWidth(count.toFloat() / maxCount)
                                .clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.secondary)
                        )
                    }
                    Text("$count", modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ---- mistakes per session line
        if (p.timeline.size >= 2) {
            SectionTitle("Mistakes per session")
            val lineColor = MaterialTheme.colorScheme.primary
            val gridColor = MaterialTheme.colorScheme.surfaceVariant
            MistakesLine(p.timeline.map { it.mistakes }, lineColor, gridColor)
        }

        // ---- recent sessions
        if (p.timeline.isNotEmpty()) {
            SectionTitle("Recent sessions")
            p.timeline.takeLast(5).reversed().forEach { t ->
                InfoCard(onClick = { onOpenSession(t.sessionId) }, modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(t.label, fontWeight = FontWeight.SemiBold)
                    Text("${formatDay(t.date)} · ${t.minutes} min · ${t.mistakes} corrections", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        p.nextFocus?.takeIf { it.isNotBlank() }?.let {
            SectionTitle("Focus for next time")
            InfoCard { Text(it, fontWeight = FontWeight.SemiBold) }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(14.dp)
    ) {
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MistakesLine(values: List<Int>, lineColor: Color, gridColor: Color) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val stepX = if (values.size > 1) size.width / (values.size - 1) else 0f
        fun point(i: Int) = Offset(i * stepX, size.height - size.height * values[i] / max)
        drawRoundRect(gridColor, size = Size(size.width, size.height), cornerRadius = CornerRadius(12f, 12f), alpha = 0.4f)
        for (i in 1 until values.size) drawLine(lineColor, point(i - 1), point(i), strokeWidth = 6f)
        values.indices.forEach { drawCircle(lineColor, radius = 9f, center = point(it)) }
    }
}
