package com.taiful.tfcontacts.ui

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import kotlinx.coroutines.flow.MutableStateFlow

val TFBlue = Color(0xFF2F6BFF)
val TFGreen = Color(0xFF16A34A)
val TFRed = Color(0xFFE11D48)
val TFNavy = Color(0xFF0B1430)

/** The colour themes the user can pick in Settings → Appearance. */
enum class AppTheme(val label: String, val seed: Color, val accent: Color) {
    OCEAN("Ocean Blue", Color(0xFF2563EB), Color(0xFF0EA5E9)),
    TEAL("Teal", Color(0xFF0F8F80), Color(0xFF14B8A6)),
    EMERALD("Emerald", Color(0xFF15803D), Color(0xFF22C55E)),
    INDIGO("Indigo", Color(0xFF4F46E5), Color(0xFF818CF8)),
    PLUM("Plum", Color(0xFF7C3AED), Color(0xFFC084FC)),
    ROSE("Rose", Color(0xFFDB2777), Color(0xFFF472B6)),
    AMBER("Amber", Color(0xFFB45309), Color(0xFFF59E0B)),
    SLATE("Graphite", Color(0xFF475569), Color(0xFF94A3B8)),
}

enum class ThemeMode(val label: String) { LIGHT("Light"), DARK("Dark"), AUTO("Auto") }

/** Saved appearance settings, shared by every screen (including the call screen). */
object ThemePrefs {
    val theme = MutableStateFlow(AppTheme.OCEAN)
    val mode = MutableStateFlow(ThemeMode.LIGHT)
    /** Incoming calls: false = small banner at the top, true = full screen. */
    val fullScreenIncoming = MutableStateFlow(false)
    private var loaded = false

    private fun p(c: Context) = c.getSharedPreferences("tf", Context.MODE_PRIVATE)

    fun load(c: Context) {
        if (loaded) return
        loaded = true
        val p = p(c)
        theme.value = AppTheme.entries.firstOrNull { it.name == p.getString("theme", null) } ?: AppTheme.OCEAN
        mode.value = ThemeMode.entries.firstOrNull { it.name == p.getString("mode", null) } ?: ThemeMode.LIGHT
        fullScreenIncoming.value = p.getBoolean("fullIncoming", false)
    }

    fun setTheme(c: Context, t: AppTheme) { theme.value = t; p(c).edit().putString("theme", t.name).apply() }
    fun setMode(c: Context, m: ThemeMode) { mode.value = m; p(c).edit().putString("mode", m.name).apply() }
    fun setFullScreenIncoming(c: Context, on: Boolean) { fullScreenIncoming.value = on; p(c).edit().putBoolean("fullIncoming", on).apply() }
}

private fun mix(a: Color, b: Color, t: Float) = lerp(a, b, t)

fun schemeFor(t: AppTheme, dark: Boolean): ColorScheme {
    val s = t.seed
    return if (!dark) {
        val base = Color(0xFFFFFFFF)
        lightColorScheme(
            primary = s,
            onPrimary = Color.White,
            primaryContainer = mix(base, s, 0.16f),
            onPrimaryContainer = mix(s, Color.Black, 0.55f),
            secondary = t.accent,
            onSecondary = Color.White,
            secondaryContainer = mix(base, t.accent, 0.20f),
            onSecondaryContainer = mix(t.accent, Color.Black, 0.6f),
            background = mix(base, s, 0.035f),
            onBackground = Color(0xFF14181F),
            surface = mix(base, s, 0.035f),
            onSurface = Color(0xFF14181F),
            surfaceVariant = mix(base, s, 0.10f),
            onSurfaceVariant = Color(0xFF545B68),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = mix(base, s, 0.05f),
            surfaceContainer = mix(base, s, 0.07f),
            surfaceContainerHigh = mix(base, s, 0.10f),
            surfaceContainerHighest = mix(base, s, 0.14f),
            outline = mix(Color(0xFF8A919E), s, 0.15f),
            outlineVariant = mix(base, s, 0.18f),
            error = Color(0xFFDC2626),
        )
    } else {
        val base = Color(0xFF0F1218)
        val p = mix(s, Color.White, 0.35f)
        darkColorScheme(
            primary = p,
            onPrimary = mix(s, Color.Black, 0.7f),
            primaryContainer = mix(base, s, 0.45f),
            onPrimaryContainer = mix(Color.White, s, 0.1f),
            secondary = t.accent,
            onSecondary = Color.Black,
            secondaryContainer = mix(base, t.accent, 0.30f),
            onSecondaryContainer = Color(0xFFE8EAF0),
            background = mix(base, s, 0.06f),
            onBackground = Color(0xFFE6E9EF),
            surface = mix(base, s, 0.06f),
            onSurface = Color(0xFFE6E9EF),
            surfaceVariant = mix(base, s, 0.16f),
            onSurfaceVariant = Color(0xFFB4BAC6),
            surfaceContainerLowest = mix(base, s, 0.03f),
            surfaceContainerLow = mix(base, s, 0.09f),
            surfaceContainer = mix(base, s, 0.12f),
            surfaceContainerHigh = mix(base, s, 0.17f),
            surfaceContainerHighest = mix(base, s, 0.24f),
            outline = mix(Color(0xFF6B7280), s, 0.2f),
            outlineVariant = mix(base, s, 0.25f),
            error = Color(0xFFF87171),
        )
    }
}

@Composable
fun isAppDark(): Boolean {
    val mode by ThemePrefs.mode.collectAsState()
    return when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AUTO -> isSystemInDarkTheme()
    }
}

@Composable
fun TFTheme(forceDark: Boolean? = null, content: @Composable () -> Unit) {
    val theme by ThemePrefs.theme.collectAsState()
    val dark = forceDark ?: isAppDark()
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val w = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(colorScheme = schemeFor(theme, dark), content = content)
}
