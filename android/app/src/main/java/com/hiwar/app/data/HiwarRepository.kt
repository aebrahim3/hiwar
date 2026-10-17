package com.hiwar.app.data

import com.hiwar.app.data.remote.HiwarApi
import com.hiwar.app.data.remote.dto.CreateScheduleRequest
import com.hiwar.app.data.remote.dto.SaveRequest
import com.hiwar.app.data.remote.dto.ScheduleActiveRequest
import com.hiwar.app.data.remote.dto.SessionDto
import com.hiwar.app.data.remote.dto.StartSessionRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only class ViewModels talk to. It hides Retrofit and turns network
 * failures into readable messages, so screens just handle Result success/failure.
 */
@Singleton
class HiwarRepository @Inject constructor(private val api: HiwarApi) {

    suspend fun health() = call { api.health() }

    suspend fun scenarios() = call { api.scenarios() }

    /** Pass includeMock = true while building the progress screen before you have real sessions. */
    suspend fun progress(includeMock: Boolean = false) = call { api.progress(includeMock) }

    suspend fun shareCard(sessionId: Int) = call { api.shareCard(sessionId) }

    // --- sessions ---

    suspend fun startSession(request: StartSessionRequest) = call { api.startSession(request) }

    suspend fun history() = call { api.sessions(savedOnly = false) }

    suspend fun savedSessions() = call { api.sessions(savedOnly = true) }

    suspend fun session(id: Int) = call { api.session(id) }

    suspend fun setSaved(id: Int, saved: Boolean) = call { api.setSaved(id, SaveRequest(saved)) }

    /** The session happening right now, or null. Call when the app opens to resume the live screen. */
    suspend fun activeSession(): Result<SessionDto?> = call { api.activeSession().session }

    /**
     * Live status of a session. Emits every few seconds and stops once it's
     * complete or failed. Sessions last as long as the call (10+ minutes), so
     * collect this in a ViewModel, not a composable.
     */
    fun watchSession(id: Int, everyMillis: Long = 4_000): Flow<Result<SessionDto>> = flow {
        while (true) {
            val result = call { api.session(id) }
            emit(result)
            if (result.getOrNull()?.isFinished == true) break
            delay(everyMillis)
        }
    }

    // --- schedules ---

    suspend fun createSchedule(request: CreateScheduleRequest) = call { api.createSchedule(request) }

    suspend fun schedules() = call { api.schedules() }

    suspend fun pauseSchedule(id: Int) = call { api.setScheduleActive(id, ScheduleActiveRequest(false)) }

    suspend fun resumeSchedule(id: Int) = call { api.setScheduleActive(id, ScheduleActiveRequest(true)) }

    /** Add an existing schedule to Google Calendar (or retry after a failure). Fo does the work. */
    suspend fun addToCalendar(id: Int) = call { api.addScheduleToCalendar(id) }

    suspend fun deleteSchedule(id: Int) = call {
        val response = api.deleteSchedule(id)
        if (!response.isSuccessful) throw HttpException(response)
    }

    private suspend fun <T> call(block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: HttpException) {
            val detail = e.response()?.errorBody()?.string()
                ?.let { Regex("\"detail\"\\s*:\\s*\"([^\"]*)\"").find(it)?.groupValues?.get(1) }
            Result.failure(Exception(detail ?: "Server error ${e.code()}"))
        } catch (e: IOException) {
            Result.failure(Exception("Can't reach the Hiwar server. Is the backend running?"))
        }
}
