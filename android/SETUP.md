# Hiwar Android: networking layer setup

Drop these files into a new Compose project (Empty Activity, package `com.hiwar.app`;
if you use a different package, update the `package` lines and imports).

```
app/src/main/java/com/hiwar/app/
  data/remote/dto/Dtos.kt      data classes matching the backend JSON
  data/remote/HiwarApi.kt      Retrofit interface, one function per endpoint
  data/HiwarRepository.kt      what ViewModels call; returns Result<T>, follows live sessions
  di/NetworkModule.kt          Hilt: builds Json, OkHttp, Retrofit
  ui/practice/PracticeViewModel.kt   live session screen: start, follow the call, show breakdown, save
  ui/share/ShareCard.kt        share card composable + "Share" button (image + text to X etc.)
app/src/main/res/xml/network_security_config.xml
app/src/main/res/xml/file_paths.xml               lets the share sheet read the card image
```

## 1. Gradle (`app/build.gradle.kts`)

Versions below are known to work together; newer ones are fine if they match your Kotlin version.

```kotlin
plugins {
    // ...existing plugins
    id("org.jetbrains.kotlin.plugin.serialization") version "<your Kotlin version>"
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    defaultConfig {
        // Emulator -> your computer. On a real phone use your LAN IP or ngrok URL. Must end with "/".
        buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8000/\"")
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
}
```

In the project-level `build.gradle.kts`, declare the Hilt and KSP plugins with `apply false`
(same as your other Hilt projects).

## 2. Manifest

```xml
<uses-permission android:name="android.permission.INTERNET" />

<application
    android:name=".HiwarApp"
    android:networkSecurityConfig="@xml/network_security_config"
    ... >
```

For the share card, add this inside `<application>`:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```
`ShareCard.kt` needs Compose UI 1.7+ (Compose BOM 2024.09.00 or newer) for `rememberGraphicsLayer`.

`network_security_config.xml` allows plain HTTP only to `10.0.2.2` and `localhost`.
For a real phone on Wi-Fi, add your computer's LAN IP there too.

## 3. Application class and Activity

```kotlin
@HiltAndroidApp
class HiwarApp : Application()
```

Put `@AndroidEntryPoint` on `MainActivity`, then use `hiltViewModel<PracticeViewModel>()` in Compose.

## 4. Check it works

Start the backend (`uvicorn app.main:app --host 0.0.0.0 --port 8000`), run the app on the emulator,
and call `repository.health()` once. Logcat (tag `okhttp.OkHttpClient`) shows the request and JSON.

## Screens to build

1. **Home**: "Start a session now" with a scenario picker (`repository.scenarios()`, plus a
   "Free conversation" option that sends `scenario = null`), level and length; plus your upcoming
   schedules and the current streak (`repository.progress()`).
   `PracticeViewModel` is created here and jumps to the live screen if a session is already running.
2. **Live session**: show `session.progress` and a call animation while status is
   `calling / in_call / summarizing`. Sessions last as long as the call, so keep this simple.
3. **Breakdown**: summary, fluency, mistakes (said → correction, with category, explanation and an
   extra example; group or colour by `category`), highlights, improvements, vocabulary,
   "next focus", the transcript, and a save toggle (`PracticeViewModel.setSaved`).
   Older sessions may have empty `fluency`/`vocabulary`/`nextFocus`; hide empty sections.
   Add the share card at the bottom: `repository.shareCard(id)` → `ShareCardWithButton(card)`.
4. **Schedule editor**: title, scenario or topic, level, length, frequency (once / daily / weekly /
   every N days), weekday chips for weekly, a time picker, a date picker for once, and an
   "Add to Google Calendar" switch (`addToCalendar`). Send `timeOfDay` as "HH:mm".
   In the schedule list, show `calendarStatus` (adding / added / failed + `calendarNote`), with a
   retry button for failed ones (`repository.addToCalendar(id)`).
5. **Library**: `repository.savedSessions()`; tap one to open its breakdown.
6. **Progress**: `repository.progress()`. Big numbers for streak, total minutes and sessions; a bar
   chart from `last7Days`; mistakes by category (bars from `mistakesByCategory`); a line of mistakes per
   session from `timeline`; and, once `trend` exists, "Mistakes per session: 4.5 → 2.0".
   Use `progress(includeMock = true)` while building it, before you have real sessions.

## Things to know
- Errors come back as `Result.failure` with a readable message (the backend's `detail`, or
  "Can't reach the Hiwar server" when it's down).
- `CreateScheduleRequest.timezone` defaults to the phone's timezone, so "19:00" means 7pm where you are.
- `next_run_at` is UTC; convert it for display:
  `Instant.parse(nextRunAt).atZone(ZoneId.systemDefault())`.
- `saved == null` means you didn't answer Fo's save question on the call; show the toggle as off.
