@file:OptIn(ExperimentalMaterial3Api::class)

package com.taiful.tfcontacts.ui

import android.content.Intent
import android.media.MediaPlayer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.taiful.tfcontacts.call.CallRecorder
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun RecordingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val items = remember(tick) { CallRecorder.list(ctx) }
    var query by remember { mutableStateOf("") }
    val shown = remember(items, query) {
        if (query.isBlank()) items else items.filter { it.name.contains(query, true) || it.number.contains(query.trim()) }
    }
    var playing by remember { mutableStateOf<File?>(null) }
    var paused by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val player = remember { MediaPlayer() }
    var toDelete by remember { mutableStateOf<CallRecorder.Item?>(null) }

    DisposableEffect(Unit) { onDispose { try { player.release() } catch (_: Exception) {} } }
    LaunchedEffect(playing, paused) {
        while (playing != null && !paused) {
            progress = try { if (player.duration > 0) player.currentPosition.toFloat() / player.duration else 0f } catch (_: Exception) { 0f }
            delay(300)
        }
    }

    fun play(f: File) {
        try {
            if (playing == f) {
                if (player.isPlaying) { player.pause(); paused = true } else { player.start(); paused = false }
                return
            }
            player.reset()
            player.setDataSource(f.absolutePath)
            player.setOnCompletionListener { playing = null; progress = 0f }
            player.prepare()
            player.start()
            playing = f; paused = false
        } catch (_: Exception) {
            playing = null
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Call recordings (${items.size})") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(
                query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                placeholder = { Text("Search by name or number") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true, shape = RoundedCornerShape(28.dp)
            )
            if (shown.isEmpty()) Text("No recordings yet", Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.file.name }) { r ->
                    val isThis = playing == r.file
                    Surface(
                        color = if (isThis) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Column(Modifier.padding(start = 6.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton({ play(r.file) }) {
                                    Icon(if (isThis && !paused) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play", tint = MaterialTheme.colorScheme.primary)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(r.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        listOf(r.number, relativeTime(r.time), "%.1f MB".format(r.file.length() / 1048576f)).filter { it.isNotBlank() }.joinToString(" • "),
                                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton({
                                    try {
                                        val uri = FileProvider.getUriForFile(ctx, "com.taiful.tfcontacts.files", r.file)
                                        ctx.startActivity(
                                            Intent.createChooser(
                                                Intent(Intent.ACTION_SEND).setType("audio/mp4").putExtra(Intent.EXTRA_STREAM, uri)
                                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Share recording"
                                            )
                                        )
                                    } catch (_: Exception) {
                                    }
                                }) { Icon(Icons.Filled.Share, "Share / save") }
                                IconButton({ toDelete = r }) { Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
                            }
                            if (isThis) {
                                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp))
                            }
                        }
                    }
                }
                item { Spacer(Modifier.width(1.dp).padding(bottom = 40.dp)) }
            }
        }
    }

    toDelete?.let { r ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete this recording?") },
            text = { Text("${r.name} — ${relativeTime(r.time)}") },
            confirmButton = {
                TextButton({
                    if (playing == r.file) { try { player.stop() } catch (_: Exception) {}; playing = null }
                    r.file.delete(); toDelete = null; tick++
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ toDelete = null }) { Text("Cancel") } }
        )
    }
}
