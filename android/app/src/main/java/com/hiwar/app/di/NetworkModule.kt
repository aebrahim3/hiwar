package com.hiwar.app.di

import com.hiwar.app.data.HiwarRepository
import com.hiwar.app.data.remote.HiwarApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds the network layer once for the whole app (instead of Hilt).
 * Think of it as the app's toolbox: screens ask it for the repository.
 */
object AppContainer {

    /**
     * Where the backend lives. Must end with "/".
     * - Android emulator: http://10.0.2.2:8000/  (10.0.2.2 = your computer)
     * - Real phone on the same Wi-Fi: http://YOUR-PC-LAN-IP:8000/ (also add that IP to network_security_config.xml)
     * - Real phone anywhere: an ngrok https URL
     */
    const val BASE_URL = "http://10.0.2.2:8000/"

    private val json = Json {
        ignoreUnknownKeys = true // backend can add fields without breaking the app
        explicitNulls = false
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    private val api: HiwarApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(HiwarApi::class.java)

    val repository = HiwarRepository(api)
}
