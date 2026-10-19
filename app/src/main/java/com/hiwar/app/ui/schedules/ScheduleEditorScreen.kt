package com.hiwar.app.ui.schedules

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hiwar.app.data.HiwarRepository
import com.hiwar.app.data.remote.dto.CreateScheduleRequest
import com.hiwar.app.data.remote.dto.ScenarioDto
import com.hiwar.app.di.AppContainer
import com.hiwar.app.ui.common.ChoiceChips
import com.hiwar.app.ui.common.EmailReportSwitch
import com.hiwar.app.ui.common.SectionTitle
import com.hiwar.app.ui.common.TopBar
import com.hiwar.app.ui.common.WEEKDAY_SHORT
import com.hiwar.app.ui.home.iconFor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class ScheduleEditorViewModel(private val repository: HiwarRepository) : ViewModel() {
    private val _scenarios = MutableStateFlow<List<ScenarioDto>>(emptyList())
    val scenarios = _scenarios.asStateFlow()
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun load() {
        viewModelScope.launch { repository.scenarios().onSuccess { _scenarios.value = it } }
    }

    fun save(request: CreateScheduleRequest, onSaved: () -> Unit) {
        _saving.value = true
        _error.value = null
        viewModelScope.launch {
            repository.createSchedule(request)
                .onSuccess { onSaved() }
                .onFailure { _error.value = it.message }
            _saving.value = false
        }
    }
}

private const val FREE = "__free__"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorScreen(onBack: () -> Unit, onSaved: () -> Unit) {
    val vm: ScheduleEditorViewModel = viewModel { ScheduleEditorViewModel(AppContainer.repository) }
    val scenarios by vm.scenarios.collectAsState()
    val saving by vm.saving.collectAsState()
    val error by vm.error.collectAsState()
    LaunchedEffect(Unit) { vm.load() }

    var title by remember { mutableStateOf("Evening Arabic") }
    var scenario by remember { mutableStateOf(FREE) }
    var topic by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("beginner") }
    var minutes by remember { mutableIntStateOf(10) }
    var frequency by remember { mutableStateOf("daily") }
    var weekdays by remember { mutableStateOf(setOf(0, 2, 4)) }
    var intervalDays by remember { mutableIntStateOf(2) }
    var hour by remember { mutableIntStateOf(19) }
    var minute by remember { mutableIntStateOf(0) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var addToCalendar by remember { mutableStateOf(true) }
    var emailReport by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopBar("New schedule", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            SectionTitle("Scene")
            ChoiceChips(
                options = listOf(FREE to "💬 Free conversation") + scenarios.map { it.id to "${iconFor(it.icon)} ${it.title}" },
                selected = scenario, onSelect = { scenario = it },
            )
            OutlinedTextField(
                topic, { topic = it }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text(if (scenario == FREE) "Topic (optional)" else "Extra detail (optional)") },
            )

            SectionTitle("Level")
            ChoiceChips(
                listOf("beginner" to "Beginner", "intermediate" to "Intermediate", "advanced" to "Advanced"),
                level,
            ) { level = it }

            SectionTitle("Length")
            ChoiceChips(listOf(3, 5, 10, 15, 20, 30).map { it to "$it min" }, minutes) { minutes = it }

            SectionTitle("Repeat")
            ChoiceChips(
                listOf("once" to "Once", "daily" to "Daily", "weekly" to "Weekly", "every_n_days" to "Every few days"),
                frequency,
            ) { frequency = it }

            when (frequency) {
                "weekly" -> Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    WEEKDAY_SHORT.forEachIndexed { i, d ->
                        FilterChip(
                            selected = i in weekdays,
                            onClick = { weekdays = if (i in weekdays) weekdays - i else weekdays + i },
                            label = { Text(d.take(2), fontSize = 12.sp) },
                        )
                    }
                }
                "every_n_days" -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                    OutlinedButton(onClick = { if (intervalDays > 2) intervalDays-- }) { Text("−") }
                    Text("Every $intervalDays days", modifier = Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.SemiBold)
                    OutlinedButton(onClick = { if (intervalDays < 30) intervalDays++ }) { Text("+") }
                }
            }

            SectionTitle("When")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showTime = true }) { Text("🕒  %02d:%02d".format(hour, minute)) }
                OutlinedButton(onClick = { showDate = true }) {
                    Text(if (frequency == "once") "📅  $date" else "Starts $date")
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 20.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Add to Google Calendar", fontWeight = FontWeight.SemiBold)
                    Text("Fo creates the event for you", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = addToCalendar, onCheckedChange = { addToCalendar = it })
            }

            EmailReportSwitch(emailReport) { emailReport = it }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }

            Spacer(Modifier.height(20.dp))
            Button(
                enabled = !saving && title.isNotBlank() && (frequency != "weekly" || weekdays.isNotEmpty()),
                onClick = {
                    vm.save(
                        CreateScheduleRequest(
                            title = title.trim(),
                            topic = topic.ifBlank { null },
                            scenario = scenario.takeIf { it != FREE },
                            addToCalendar = addToCalendar,
                            emailReport = emailReport,
                            level = level,
                            durationMinutes = minutes,
                            frequency = frequency,
                            weekdays = if (frequency == "weekly") weekdays.sorted() else emptyList(),
                            intervalDays = if (frequency == "every_n_days") intervalDays else 1,
                            timeOfDay = "%02d:%02d".format(hour, minute),
                            startDate = date.toString(),
                        ),
                        onSaved,
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
            ) { Text(if (saving) "Saving…" else "Save schedule") }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showTime) {
        val timeState = rememberTimePickerState(initialHour = hour, initialMinute = minute)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = { TextButton(onClick = { hour = timeState.hour; minute = timeState.minute; showTime = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
            text = { TimePicker(state = timeState) },
        )
    }
    if (showDate) {
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = dateState) }
    }
}
