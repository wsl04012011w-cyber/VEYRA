package com.veyra.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VeyraColors = darkColorScheme(
    primary = Color(0xFFF4F4F5),
    onPrimary = Color(0xFF111113),
    secondary = Color(0xFFB8B8BD),
    onSecondary = Color(0xFF171719),
    background = Color(0xFF0B0B0D),
    onBackground = Color(0xFFF4F4F5),
    surface = Color(0xFF151517),
    onSurface = Color(0xFFF4F4F5),
    surfaceVariant = Color(0xFF202023),
    onSurfaceVariant = Color(0xFFB8B8BD),
    outline = Color(0xFF343438)
)

@Composable
fun VeyraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VeyraColors,
        content = content
    )
}
