package com.zero.assistant.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.core.view.WindowCompat

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
    secondary = Color(0xFFF2F4F8),
    tertiary = Color(0xFF9CA7FF),
    background = Color(0xFF090A0F),
    onBackground = Color(0xFFF3F7FA),
    surface = Color(0xFF131620),
    onSurface = Color(0xFFF3F7FA),
    surfaceVariant = Color(0xFF1A2029),
    onSurfaceVariant = Color(0xFFF2F4F8),
    error = Color(0xFFFF8D9A),
)

fun zeroLightColors() = lightColorScheme(
    primary = Color.Black,
    onPrimary = Color.White,
    secondary = Color.Black,
    onSecondary = Color.White,
    tertiary = Color.Black,
    background = Color.White,
    onBackground = Color.Black,
    surface = Color.White,
    onSurface = Color.Black,
    surfaceVariant = Color.White,
    onSurfaceVariant = Color.Black,
    primaryContainer = Color.White,
    onPrimaryContainer = Color.Black,
    secondaryContainer = Color.White,
    onSecondaryContainer = Color.Black,
    outline = Color.Black,
    outlineVariant = Color.Black,
    error = Color.Black,
)

@Composable
fun ZeroTheme(mode: ZeroThemeMode, content: @Composable () -> Unit) {
    val useDarkTheme = when (mode) {
        ZeroThemeMode.System -> isSystemInDarkTheme()
        ZeroThemeMode.Dark -> true
        ZeroThemeMode.Light -> false
    }
    val view = LocalView.current
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        val background = (if (useDarkTheme) Color(0xFF090A0F) else Color.White).toArgb()
        window.decorView.setBackgroundColor(background)
        window.statusBarColor = background
        window.navigationBarColor = background
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !useDarkTheme
            isAppearanceLightNavigationBars = !useDarkTheme
        }
    }
    MaterialTheme(colorScheme = if (useDarkTheme) zeroDarkColors() else zeroLightColors(), content = content)
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        val next = current.baseContext
        if (next === current) break
        current = next
    }
    return current as? Activity
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, shape: Shape = ZeroGlassTokens.cardShape, content: @Composable () -> Unit) {
    val isDark = MaterialTheme.colorScheme.background == Color(0xFF090A0F)
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
                modifier = Modifier.weight(1f),
                shape = ZeroGlassTokens.capsuleShape,
                label = { Text(option.name, maxLines = 1, softWrap = false) },
            )
        }
    }
}
