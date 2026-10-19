package com.hiwar.app.ui.practice

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hiwar.app.ui.theme.HiwarInk
import com.hiwar.app.ui.theme.HiwarNavy
import com.hiwar.app.ui.theme.HiwarSaffron
import com.hiwar.app.ui.theme.HiwarSand

/** Shown while Fo is calling you. Pulsing rings + Fo's live status line. */
@Composable
fun LiveSessionScreen(state: SessionUiState, onDone: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse), label = "scale",
    )

    val (title, detail) = when (state) {
        SessionUiState.Starting, SessionUiState.Idle -> "Starting" to "Asking Fo to call you…"
        is SessionUiState.Live -> when (state.session.status) {
            "calling" -> "Fo is calling" to (state.session.progress ?: "Your phone should ring soon")
            "in_call" -> "On the call" to (state.session.progress ?: "Speak in Arabic. Take your time.")
            "summarizing" -> "Wrapping up" to (state.session.progress ?: "Fo is writing your breakdown")
            else -> state.session.status to (state.session.progress ?: "")
        }
        is SessionUiState.Finished -> "Done" to "Opening your breakdown…"
        is SessionUiState.Error -> "Something went wrong" to state.message
    }
    val isError = state is SessionUiState.Error

    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(HiwarInk, HiwarNavy))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (!isError) {
                    Box(Modifier.size(200.dp).scale(scale).clip(CircleShape).background(HiwarSaffron.copy(alpha = 0.15f)))
                    Box(Modifier.size(150.dp).scale(scale * 0.95f).clip(CircleShape).background(HiwarSaffron.copy(alpha = 0.25f)))
                }
                Box(
                    Modifier.size(104.dp).clip(CircleShape).background(if (isError) MaterialTheme.colorScheme.error else HiwarSaffron),
                    contentAlignment = Alignment.Center,
                ) { Text(if (isError) "!" else "Fo", color = HiwarInk, fontSize = 32.sp, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(40.dp))
            Text(title, color = HiwarSand, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(detail, color = HiwarSand.copy(alpha = 0.75f), textAlign = TextAlign.Center)

            if (state is SessionUiState.Live) {
                Spacer(Modifier.height(24.dp))
                val s = state.session
                Text(
                    "${s.durationMinutes} min · ${s.level}" + (s.scenario?.let { " · role-play" } ?: s.topic?.let { " · $it" } ?: ""),
                    color = HiwarSand.copy(alpha = 0.5f), fontSize = 13.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Fo gives your feedback in English at the end, then asks if you want to save it.",
                    color = HiwarSand.copy(alpha = 0.5f), fontSize = 12.sp, textAlign = TextAlign.Center,
                )
            }
            if (isError) {
                Spacer(Modifier.height(24.dp))
                Button(onClick = onDone) { Text("Back") }
            }
        }
    }
}
