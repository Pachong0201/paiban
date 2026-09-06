package com.gongwen.paiban.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF8B1A1A),   // 党政红
    secondary = Color(0xFF44546A),
    tertiary = Color(0xFF6B6B6B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFE08A8A),
    secondary = Color(0xFFA6B4C8),
)

@Composable
fun PaibanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
