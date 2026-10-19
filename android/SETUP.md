# Hiwar Android app: setup

The whole app is written: home, live call, breakdown (with share card), schedules + editor,
saved sessions, and progress. You create the Android Studio project, add a few lines of
config, and copy these files in. About 15 minutes.

## 1. Create the project

Android Studio → **New Project → Empty Activity**
- Name: `Hiwar`
- Package name: `com.hiwar.app`
- Save location: `C:\Users\User\AndroidStudioProjects\Hiwar\android`
- Language: Kotlin, Build configuration language: Kotlin DSL
- Minimum SDK: API 26 or higher

Let it finish syncing once before changing anything.

## 2. Copy the code in

From this folder, copy everything under `app/src/main/` into the new project's `app/src/main/`,
letting it **overwrite** `MainActivity.kt` and `ui/theme/Theme.kt`.

```
app/src/main/java/com/hiwar/app/
  MainActivity.kt                    starts the app
  di/NetworkModule.kt                AppContainer: Retrofit + repository (BASE_URL is here)
  data/HiwarRepository.kt            the only class screens talk to
  data/remote/HiwarApi.kt            one function per backend endpoint
  data/remote/dto/Dtos.kt            data classes matching the backend JSON
  ui/AppNav.kt                       screen list + back stack
  ui/theme/Theme.kt                  colours (overwrites the template's)
  ui/common/Components.kt            shared UI pieces and date helpers
  ui/home/HomeScreen.kt              scene picker, level, length, "Call me now", upcoming
  ui/practice/PracticeViewModel.kt   starts a session and follows it
  ui/practice/LiveSessionScreen.kt   pulsing "Fo is calling" screen
  ui/session/SessionDetailScreen.kt  breakdown, save toggle, transcript, share card
  ui/schedules/SchedulesScreen.kt    schedule list, pause/resume, calendar status, delete
  ui/schedules/ScheduleEditorScreen.kt  new schedule form (time/date pickers, calendar switch)
  ui/library/LibraryScreen.kt        saved / all sessions
  ui/progress/ProgressScreen.kt      streak, minutes, charts, recent sessions
  ui/share/ShareCard.kt              shareable card image + share sheet
app/src/main/res/xml/network_security_config.xml
app/src/main/res/xml/file_paths.xml
```

The template's `ui/theme/Color.kt` and `Type.kt` can stay; they're just unused.

## 3. Gradle (`app/build.gradle.kts`)

Add the serialization plugin to the `plugins { }` block. Use the **same version as `kotlin`**
in `gradle/libs.versions.toml` (e.g. `2.1.0`):

```kotlin
plugins {
    // ...keep the existing lines...
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.0"   // <- your kotlin version
}
```

Add these to `dependencies { }`:

```kotlin
implementation("com.squareup.retrofit2:retrofit:2.11.0")
implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
implementation("com.squareup.okhttp3:okhttp:4.12.0")
implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
```

Compose, Material 3 and core-ktx are already in the template. The share card needs Compose
BOM `2024.09.00` or newer (any recent template has it). Click **Sync Now**.

## 4. Manifest (`app/src/main/AndroidManifest.xml`)

Above `<application>`:
```xml
<uses-permission android:name="android.permission.INTERNET" />
```

Add this attribute to the `<application ...>` tag:
```xml
android:networkSecurityConfig="@xml/network_security_config"
```

And inside `<application>` (for the share card):
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

## 5. Run it

1. Start the backend: `uvicorn app.main:app --reload --host 0.0.0.0 --port 8000`
2. Run the app on the **emulator**. It talks to `http://10.0.2.2:8000/` (your computer).
3. Tap **Call me now**. You should see the live screen, then your breakdown.

**Real phone instead of the emulator?** In `di/NetworkModule.kt` set `BASE_URL` to
`http://YOUR-PC-LAN-IP:8000/` (find it with `ipconfig`), and add that IP as another
`<domain>` in `network_security_config.xml`. Phone and PC must be on the same Wi-Fi.

## If the build fails

Paste the **first** red error from the Build panel. The usual suspects:
- `Unresolved reference: serialization` → the serialization plugin line is missing or its version
  doesn't match your Kotlin version.
- `Unresolved reference: rememberGraphicsLayer` → Compose BOM is older than 2024.09; bump
  `composeBom` in `libs.versions.toml`.
- `Cleartext HTTP traffic not permitted` at runtime → the manifest attribute from step 4 is missing.
