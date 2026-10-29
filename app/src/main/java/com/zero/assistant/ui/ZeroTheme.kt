package com.zero.assistant.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme

enum class ZeroThemeMode(val persistenceValue: String, val label: String) {
    System("system", "System / Default"),
    Dark("dark", "Dark"),
    Light("light", "Light"),
}

class ZeroThemePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun mode(): ZeroThemeMode = ZeroThemeMode.entries.firstOrNull { it.persistenceValue == preferences.getString(KEY_MODE, ZeroThemeMode.System.persistenceValue) } ?: ZeroThemeMode.System
    fun setMode(mode: ZeroThemeMode) = preferences.edit().putString(KEY_MODE, mode.persistenceValue).apply()

    companion object {
        private const val PREFS = "zero_theme"
        private const val KEY_MODE = "mode"
    }
}

object ZeroGlassTokens {
    val cardShape = RoundedCornerShape(20.dp)
    val capsuleShape = RoundedCornerShape(50)
    val iconSize = 20.dp
    val cardPadding = 16.dp
    val sectionSpacing = 12.dp
    val borderAlpha = 0.16f
}

fun zeroDarkColors() = darkColorScheme(
    primary = Color(0xFF87E4E8),
    onPrimary = Color(0xFF071719),
    secondary = Color(0xFFBFC9D4),
    tertiary = Color(0xFF9CA7FF),
    background = Color(0xFF090B10),
    onBackground = Color(0xFFF3F7FA),
    surface = Color(0xFF10141B),
    onSurface = Color(0xFFF3F7FA),
    surfaceVariant = Color(0xFF1A2029),
    onSurfaceVariant = Color(0xFFB6C0CA),
    error = Color(0xFFFF8D9A),
)

fun zeroLightColors() = lightColorScheme(
    primary = Color(0xFF176E74),
    onPrimary = Color.White,
    secondary = Color(0xFF52606B),
    tertiary = Color(0xFF5864A7),
    background = Color(0xFFF4F7F8),
    onBackground = Color(0xFF172026),
    surface = Color(0xFFFDFEFE),
    onSurface = Color(0xFF172026),
    surfaceVariant = Color(0xFFE8EEF0),
    onSurfaceVariant = Color(0xFF56636A),
    error = Color(0xFFB3261E),
)

@Composable
fun ZeroTheme(mode: ZeroThemeMode, content: @Composable () -> Unit) {
    val useDarkTheme = when (mode) {
        ZeroThemeMode.System -> isSystemInDarkTheme()
        ZeroThemeMode.Dark -> true
        ZeroThemeMode.Light -> false
    }
    MaterialTheme(colorScheme = if (useDarkTheme) zeroDarkColors() else zeroLightColors(), content = content)
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, shape: Shape = ZeroGlassTokens.cardShape, content: @Composable () -> Unit) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF090B10)
    val surface = if (isDark) Color(0xFF151A22).copy(alpha = 0.82f) else Color.White.copy(alpha = 0.82f)
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = ZeroGlassTokens.borderAlpha)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        content = { Column(Modifier.padding(ZeroGlassTokens.cardPadding)) { content() } },
    )
}

@Composable
fun GlassIconButton(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp),
        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)),
        content = content,
    )
}

@Composable
fun ThemeModeSelector(mode: ZeroThemeMode, onModeChange: (ZeroThemeMode) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ZeroThemeMode.entries.forEach { option ->
            FilterChip(
                selected = mode == option,
                onClick = { onModeChange(option) },
                shape = ZeroGlassTokens.capsuleShape,
                label = { Text(option.label) },
            )
        }
    }
}
