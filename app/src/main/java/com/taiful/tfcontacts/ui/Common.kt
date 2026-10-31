package com.taiful.tfcontacts.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taiful.tfcontacts.data.SourceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PhotoCache {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun clear() = cache.evictAll()

    fun load(context: Context, uri: String, maxPx: Int): Bitmap? {
        val key = "$uri@$maxPx"
        cache.get(key)?.let { return it }
        return try {
            val u = Uri.parse(uri)
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o) }
            var s = 1
            while (o.outWidth / (s * 2) >= maxPx && o.outHeight / (s * 2) >= maxPx) s *= 2
            val bmp = context.contentResolver.openInputStream(u)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s })
            }
            bmp?.also { cache.put(key, it) }
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
fun rememberPhoto(uri: String?, maxPx: Int = 160): ImageBitmap? {
    val context = LocalContext.current
    val state by produceState<ImageBitmap?>(null, uri, maxPx) {
        value = if (uri == null) null else withContext(Dispatchers.IO) { PhotoCache.load(context, uri, maxPx)?.asImageBitmap() }
    }
    return state
}

private val avatarColors = listOf(
    Color(0xFF2F6BFF), Color(0xFF7C3AED), Color(0xFFDB2777), Color(0xFFEA580C),
    Color(0xFF0D9488), Color(0xFF16A34A), Color(0xFF0284C7), Color(0xFFCA8A04)
)

fun avatarColor(name: String): Color = avatarColors[(name.hashCode() and 0x7fffffff) % avatarColors.size]

fun initials(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return "?"
    val first = words[0].firstCodePointString()
    val second = if (words.size > 1) words.last().firstCodePointString() else ""
    return (first + second).uppercase()
}

private fun String.firstCodePointString(): String {
    if (isEmpty()) return ""
    val cp = codePointAt(0)
    return String(Character.toChars(cp))
}

@Composable
fun Avatar(name: String, photoUri: String?, size: Dp, maxPx: Int = 160, modifier: Modifier = Modifier) {
    val bmp = rememberPhoto(photoUri, maxPx)
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(avatarColor(name), avatarColor(name).copy(alpha = 0.7f)))),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(bmp, null, Modifier.size(size), contentScale = ContentScale.Crop)
        } else {
            Text(
                initials(name), color = Color.White, fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.38f).sp, maxLines = 1
            )
        }
    }
}

@Composable
fun SourceBadge(kind: SourceKind, label: String) {
    val (bg, fg) = when (kind) {
        SourceKind.SIM -> Color(0xFFF59E0B).copy(alpha = 0.18f) to Color(0xFFD97706)
        SourceKind.GOOGLE -> Color(0xFF3B82F6).copy(alpha = 0.18f) to Color(0xFF3B82F6)
        SourceKind.PHONE -> Color(0xFF10B981).copy(alpha = 0.18f) to Color(0xFF10B981)
        SourceKind.OTHER -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
        Text(
            label, color = fg, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}

fun relativeTime(ms: Long): String {
    val now = System.currentTimeMillis()
    val d = now - ms
    val min = d / 60000
    val cal = java.util.Calendar.getInstance()
    val today = cal.apply { set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0) }.timeInMillis
    val time = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(ms))
    return when {
        min < 1 -> "Just now"
        min < 60 -> "$min min ago"
        ms >= today -> "Today $time"
        ms >= today - 86400000L -> "Yesterday $time"
        else -> java.text.SimpleDateFormat("dd MMM, hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(ms))
    }
}

fun durationText(sec: Long): String {
    if (sec <= 0) return ""
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
