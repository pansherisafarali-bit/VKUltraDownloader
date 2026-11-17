package com.vkultra.downloader

import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import dev.ffmpegkit_maintained.ytdlp.YtDlp
import dev.ffmpegkit_maintained.ytdlp.YtDlpRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.util.Locale

private val Bg = Color(0xFF090B12)
private val Panel = Color(0xFF151A27)
private val Accent = Color(0xFF8B7CFF)
private val Muted = Color(0xFF9BA3B7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { YtDlp.init(this) }
        val shared = intent?.getStringExtra(IntentExtraText) ?: intent?.getStringExtra("android.intent.extra.TEXT")
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Panel)) {
                DownloaderScreen(shared.orEmpty(), onDownload = { url, format -> startDownload(url, format) })
            }
        }
    }

    private fun startDownload(url: String, format: String) {
        lifecycleScope.launch {
            val safeUrl = url.trim()
            if (!safeUrl.startsWith("https://") && !safeUrl.startsWith("http://")) return@launch
            val tempDir = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "work").apply { mkdirs() }
            val request = YtDlpRequest(safeUrl)
                .setOutputTemplate(File(tempDir, "%(title).180B [%(id)s].%(ext)s").absolutePath)
                .addOption("--no-playlist")
                .addOption("--continue")
                .addOption("--newline")
                .addOption("--no-mtime")
                .addOption("-f", format)
                .addOption("--merge-output-format", "mp4")
            try {
                YtDlp.executeAsync(request) { progress, _, line ->
                    if (progress >= 100 || line.contains("100%", ignoreCase = true)) {
                        lifecycleScope.launch(Dispatchers.IO) { publishLatest(tempDir) }
                    }
                }
            } catch (_: Exception) {
                // Error state is intentionally kept local; no credentials or URL are sent to a server.
            }
        }
    }

    private fun publishLatest(dir: File) {
        val file = dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }
            ?.maxByOrNull { it.lastModified() } ?: return
        val mime = when (file.extension.lowercase(Locale.ROOT)) {
            "mp4", "m4v" -> "video/mp4"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            else -> "video/*"
        }
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, mime)
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/VK Ultra")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = contentResolver
        val uri: Uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { out -> FileInputStream(file).use { input -> input.copyTo(out) } }
        values.clear(); values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    companion object { const val IntentExtraText = "shared_text" }
}

@Composable
private fun DownloaderScreen(sharedText: String, onDownload: (String, String) -> Unit) {
    var url by remember { mutableStateOf(sharedText) }
    var quality by remember { mutableStateOf("bestvideo*+bestaudio/best") }
    var message by remember { mutableStateOf("Вставь ссылку VK Видео") }
    val options = listOf(
        "Максимум" to "bestvideo*+bestaudio/best",
        "4K / лучшее" to "bestvideo[height<=2160]+bestaudio/best/best",
        "1080p" to "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best",
        "720p" to "bestvideo[height<=720]+bestaudio/best[height<=720]/best",
        "480p" to "bestvideo[height<=480]+bestaudio/best[height<=480]/best",
        "360p" to "bestvideo[height<=360]+bestaudio/best[height<=360]/best"
    )
    Column(Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Accent.copy(alpha = .18f), shape = RoundedCornerShape(16.dp), modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Text("V", color = Accent, fontSize = 25.sp, fontWeight = FontWeight.Black) }
            }
            Spacer(Modifier.width(12.dp))
            Column { Text("VK ULTRA", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Black); Text("VIDEO DOWNLOADER", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp) }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Скачивай без ограничений по длине", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)
            Text("Длинные видео • выбор качества • сохранение в Галерею", color = Muted, fontSize = 13.sp)
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("ССЫЛКА НА ВИДЕО", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                OutlinedTextField(value = url, onValueChange = { url = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("https://vk.com/video...") }, minLines = 2, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Accent, unfocusedBorderColor = Muted.copy(alpha = .3f)))
                Text("КАЧЕСТВО", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                options.forEach { (label, value) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RadioButton(selected = quality == value, onClick = { quality = value }, colors = RadioButtonDefaults.colors(selectedColor = Accent))
                        Text(label, color = Color.White, modifier = Modifier.weight(1f))
                        if (value == options.first().second) Text("MAX", color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Button(onClick = { if (url.isBlank()) message = "Сначала вставь ссылку" else { message = "Загрузка отправлена движку"; onDownload(url, quality) } }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                    Text("НАЧАТЬ СКАЧИВАНИЕ", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Text(message, color = Muted, fontSize = 12.sp)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("СОХРАНЕНИЕ", color = Color.White, fontWeight = FontWeight.Bold)
                Text("Готовые видео помещаются в Галерею → Альбомы → VK Ultra", color = Muted, fontSize = 13.sp)
                Text("Файл сохраняется без повторного перекодирования. Итоговое качество зависит от оригинала VK.", color = Muted, fontSize = 12.sp)
            }
        }
        Text("Версия 1.0.0 • Бесплатный проект • Без рекламы", color = Muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}
