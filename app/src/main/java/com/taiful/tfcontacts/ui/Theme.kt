package com.taiful.tfcontacts.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val TFBlue = Color(0xFF2F6BFF)
val TFGreen = Color(0xFF16A34A)
val TFRed = Color(0xFFE11D48)
val TFNavy = Color(0xFF0B1430)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FA6FF),
    onPrimary = Color(0xFF00205F),
    primaryContainer = Color(0xFF1C3E99),
    onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFF6EE7B7),
    secondaryContainer = Color(0xFF134E3A),
    onSecondaryContainer = Color(0xFFCFFAE8),
    background = TFNavy,
    onBackground = Color(0xFFE6EAF5),
    surface = TFNavy,
    onSurface = Color(0xFFE6EAF5),
    surfaceVariant = Color(0xFF1A2547),
    onSurfaceVariant = Color(0xFFB7C0DA),
    surfaceContainer = Color(0xFF131D3D),
    surfaceContainerHigh = Color(0xFF1A2547),
    surfaceContainerHighest = Color(0xFF233057),
    surfaceContainerLow = Color(0xFF0F1936),
    outline = Color(0xFF3A4770),
    outlineVariant = Color(0xFF283459),
    error = Color(0xFFFF7A90),
)

private val Light = lightColorScheme(
    primary = TFBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF00184F),
    secondary = Color(0xFF0E9F6E),
    secondaryContainer = Color(0xFFD1FAE5),
    onSecondaryContainer = Color(0xFF053B2A),
    background = Color(0xFFF6F8FD),
    surface = Color(0xFFF6F8FD),
    surfaceVariant = Color(0xFFE6EBF6),
    surfaceContainer = Color(0xFFEDF1FA),
    surfaceContainerHigh = Color(0xFFE6EBF6),
    surfaceContainerHighest = Color(0xFFDDE3F2),
    surfaceContainerLow = Color(0xFFF1F4FB),
    error = TFRed,
)

@Composable
fun TFTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
