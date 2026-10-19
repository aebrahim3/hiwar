package com.hiwar.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// These mirror the backend's JSON exactly (see the backend README).
// Optional fields have defaults, so a missing field never crashes parsing.

// ---------------------------------------------------------------- sessions

@Serializable
data class StartSessionRequest(
    val topic: String? = null,
    val scenario: String? = null,              // role-play id from GET /scenarios, or null

    val language: String = "Arabic",
    val level: String = "beginner",            // beginner | intermediate | advanced
    @SerialName("duration_minutes") val durationMinutes: Int = 10,
    @SerialName("email_report") val emailReport: Boolean = false, // also email the report (always in the app)
)

@Serializable
data class SessionDto(
    val id: Int,
    @SerialName("schedule_id") val scheduleId: Int? = null,
    val status: String,                         // calling | in_call | summarizing | complete | failed
    val progress: String? = null,               // live status line to show while it runs
    val topic: String? = null,
    val scenario: String? = null,
    val language: String,
    val level: String,
    @SerialName("duration_minutes") val durationMinutes: Int,
    @SerialName("started_at") val startedAt: String,
    @SerialName("ended_at") val endedAt: String? = null,
    val transcript: String? = null,
    val breakdown: BreakdownDto? = null,
    val saved: Boolean? = null,                 // null = you didn't answer on the call
    val mock: Boolean = false,                  // true = fake session from before Fo sign-in
) {
    val isFinished: Boolean get() = status == "complete" || status == "failed"
}

@Serializable
data class BreakdownDto(
    val summary: String = "",
    val fluency: String = "",                    // pace, hesitations, switching to English
    val mistakes: List<MistakeDto> = emptyList(),
    val highlights: List<String> = emptyList(),
    val improvements: List<String> = emptyList(),
    val vocabulary: List<VocabDto> = emptyList(),
    @SerialName("next_focus") val nextFocus: String = "",
)

@Serializable
data class MistakeDto(
    val said: String = "",
    val correction: String = "",
    val category: String = "other",              // grammar | vocabulary | pronunciation | word order | other
    val explanation: String = "",
    val example: String = "",                    // another correct sentence using the same rule
)

@Serializable
data class VocabDto(
    val word: String = "",
    val meaning: String = "",
    val note: String = "",
)

@Serializable
data class ActiveSessionDto(val session: SessionDto? = null)

@Serializable
data class SaveRequest(val saved: Boolean)

// ---------------------------------------------------------------- schedules

@Serializable
data class CreateScheduleRequest(
    val title: String = "Speaking practice",
    val topic: String? = null,
    val scenario: String? = null,
    @SerialName("add_to_calendar") val addToCalendar: Boolean = false, // Fo adds it to Google Calendar
    @SerialName("email_report") val emailReport: Boolean = false,      // Fo also emails each report
    val language: String = "Arabic",
    val level: String = "beginner",
    @SerialName("duration_minutes") val durationMinutes: Int = 10,
    val frequency: String = "daily",            // once | daily | weekly | every_n_days
    val weekdays: List<Int> = emptyList(),      // 0=Mon ... 6=Sun, weekly only
    @SerialName("interval_days") val intervalDays: Int = 1,  // every_n_days only
    @SerialName("time_of_day") val timeOfDay: String = "19:00",
    val timezone: String = java.util.TimeZone.getDefault().id,
    @SerialName("start_date") val startDate: String? = null, // "2026-10-20"; defaults to today
)

@Serializable
data class ScheduleDto(
    val id: Int,
    val title: String,
    val topic: String? = null,
    val scenario: String? = null,
    val language: String,
    val level: String,
    @SerialName("duration_minutes") val durationMinutes: Int,
    val frequency: String,
    val weekdays: List<Int> = emptyList(),
    @SerialName("interval_days") val intervalDays: Int = 1,
    @SerialName("time_of_day") val timeOfDay: String,
    val timezone: String,
    @SerialName("start_date") val startDate: String,
    val active: Boolean,
    @SerialName("next_run_at") val nextRunAt: String? = null, // UTC ISO; null when paused/finished
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("calendar_status") val calendarStatus: String? = null, // null | adding | added | failed
    @SerialName("calendar_note") val calendarNote: String? = null,     // Fo's reply or the error
)

@Serializable
data class ScheduleActiveRequest(val active: Boolean)

// ---------------------------------------------------------------- scenarios

@Serializable
data class ScenarioDto(
    val id: String,
    val title: String,
    val icon: String = "",          // restaurant | taxi | shopping | medical | hotel | map | people | work
    val description: String = "",
)

// ---------------------------------------------------------------- progress

@Serializable
data class ProgressDto(
    @SerialName("total_sessions") val totalSessions: Int = 0,
    @SerialName("total_minutes") val totalMinutes: Int = 0,
    @SerialName("current_streak_days") val currentStreakDays: Int = 0,
    @SerialName("longest_streak_days") val longestStreakDays: Int = 0,
    @SerialName("sessions_this_week") val sessionsThisWeek: Int = 0,
    @SerialName("minutes_this_week") val minutesThisWeek: Int = 0,
    @SerialName("mistakes_by_category") val mistakesByCategory: Map<String, Int> = emptyMap(),
    @SerialName("last_7_days") val last7Days: List<DayMinutesDto> = emptyList(),
    val timeline: List<TimelineEntryDto> = emptyList(),   // oldest first
    val trend: TrendDto? = null,                          // null until you have 4+ sessions
    @SerialName("next_focus") val nextFocus: String? = null,
)

@Serializable
data class DayMinutesDto(val date: String, val minutes: Int)

@Serializable
data class TimelineEntryDto(
    @SerialName("session_id") val sessionId: Int,
    val date: String,
    val minutes: Int,
    val mistakes: Int,
    val highlights: Int = 0,
    @SerialName("by_category") val byCategory: Map<String, Int> = emptyMap(),
    val label: String = "",
)

@Serializable
data class TrendDto(
    @SerialName("early_avg_mistakes") val earlyAvgMistakes: Double,
    @SerialName("recent_avg_mistakes") val recentAvgMistakes: Double,
)

// ---------------------------------------------------------------- share card

@Serializable
data class ShareCardDto(
    @SerialName("session_id") val sessionId: Int,
    val headline: String,                    // "10 min of Arabic with Fo"
    val label: String,                       // scenario title or topic
    val date: String,
    val stats: List<StatDto> = emptyList(),  // Minutes, Corrections, Streak
    val highlight: String = "",
    @SerialName("next_focus") val nextFocus: String = "",
    @SerialName("share_text") val shareText: String,   // ready to post
)

@Serializable
data class StatDto(val label: String, val value: String)

@Serializable
data class HealthDto(
    val ok: Boolean,
    @SerialName("mock_fo") val mockFo: Boolean,
    val language: String,
)
