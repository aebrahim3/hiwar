package com.hiwar.app.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.hiwar.app.data.remote.dto.ShareCardDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/*
 * Shareable session card.
 *
 * How it works: the card is drawn into a GraphicsLayer (like recording a drawing),
 * which we can turn into a Bitmap, save as a PNG, and hand to Android's share sheet
 * together with the ready-made text. Needs Compose UI 1.7+ (BOM 2024.09 or newer)
 * and the FileProvider entry from SETUP.md.
 *
 * Usage on the breakdown screen:
 *     val card = ... repository.shareCard(session.id).getOrNull()
 *     card?.let { ShareCardWithButton(it) }
 */

private val Ink = Color(0xFF0F1B2D)
private val Sand = Color(0xFFF4EBDD)
private val Accent = Color(0xFFE8A33D)
private val Muted = Color(0xFFB9C3D1)

@Composable
fun ShareCard(card: ShareCardDto, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Ink, Color(0xFF1E3A5F))))
            .padding(24.dp)
    ) {
        Text("HIWAR × FO", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(12.dp))
        Text(card.headline, color = Sand, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)
        Text(card.label, color = Muted, fontSize = 15.sp)
        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            card.stats.forEach { stat ->
                Column(horizontalAlignment = Alignment.Start) {
                    Text(stat.value, color = Sand, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(stat.label.uppercase(), color = Muted, fontSize = 11.sp, letterSpacing = 1.sp)
                }
            }
        }

        if (card.highlight.isNotBlank()) {
            Spacer(Modifier.height(20.dp))
            Text("HIGHLIGHT", color = Accent, fontSize = 11.sp, letterSpacing = 1.sp)
            Text(card.highlight, color = Sand, fontSize = 15.sp)
        }
        if (card.nextFocus.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text("NEXT FOCUS", color = Accent, fontSize = 11.sp, letterSpacing = 1.sp)
            Text(card.nextFocus, color = Sand, fontSize = 15.sp)
        }
        Spacer(Modifier.height(16.dp))
        Text(card.date, color = Muted, fontSize = 11.sp)
    }
}

/** The card plus a "Share" button that posts it as an image with the share text. */
@Composable
fun ShareCardWithButton(card: ShareCardDto, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ShareCard(
            card,
            Modifier
                .width(340.dp)
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() } // capture what's drawn
                    drawLayer(layer)                                    // and still show it
                }
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            scope.launch {
                val bitmap = layer.toImageBitmap().asAndroidBitmap()
                shareImage(context, bitmap, card.shareText)
            }
        }) { Text("Share") }
    }
}

private suspend fun shareImage(context: Context, bitmap: Bitmap, text: String) {
    val uri = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val file = File(dir, "hiwar-session.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share your session"))
}
