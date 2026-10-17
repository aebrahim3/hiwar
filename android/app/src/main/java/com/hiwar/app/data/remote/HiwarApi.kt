package com.hiwar.app.data.remote

import com.hiwar.app.data.remote.dto.ActiveSessionDto
import com.hiwar.app.data.remote.dto.CreateScheduleRequest
import com.hiwar.app.data.remote.dto.HealthDto
import com.hiwar.app.data.remote.dto.ProgressDto
import com.hiwar.app.data.remote.dto.ScenarioDto
import com.hiwar.app.data.remote.dto.ShareCardDto
import com.hiwar.app.data.remote.dto.SaveRequest
import com.hiwar.app.data.remote.dto.ScheduleActiveRequest
import com.hiwar.app.data.remote.dto.ScheduleDto
import com.hiwar.app.data.remote.dto.SessionDto
import com.hiwar.app.data.remote.dto.StartSessionRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** One function per backend endpoint. Retrofit writes the HTTP code for us. */
interface HiwarApi {

    @GET("health")
    suspend fun health(): HealthDto

    @GET("scenarios")
    suspend fun scenarios(): List<ScenarioDto>

    @GET("progress")
    suspend fun progress(@Query("include_mock") includeMock: Boolean = false): ProgressDto

    // --- sessions ---

    @POST("sessions")
    suspend fun startSession(@Body body: StartSessionRequest): SessionDto

    @GET("sessions")
    suspend fun sessions(@Query("saved") savedOnly: Boolean = false): List<SessionDto>

    /** `session` is null when nothing is running. */
    @GET("sessions/active")
    suspend fun activeSession(): ActiveSessionDto

    @GET("sessions/{id}")
    suspend fun session(@Path("id") id: Int): SessionDto

    @GET("sessions/{id}/share")
    suspend fun shareCard(@Path("id") id: Int): ShareCardDto

    @PATCH("sessions/{id}")
    suspend fun setSaved(@Path("id") id: Int, @Body body: SaveRequest): SessionDto

    // --- schedules ---

    @POST("schedules")
    suspend fun createSchedule(@Body body: CreateScheduleRequest): ScheduleDto

    @GET("schedules")
    suspend fun schedules(): List<ScheduleDto>

    @PATCH("schedules/{id}")
    suspend fun setScheduleActive(@Path("id") id: Int, @Body body: ScheduleActiveRequest): ScheduleDto

    @POST("schedules/{id}/calendar")
    suspend fun addScheduleToCalendar(@Path("id") id: Int): ScheduleDto

    @DELETE("schedules/{id}")
    suspend fun deleteSchedule(@Path("id") id: Int): Response<Unit>
}
