package com.omnitask.ai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun OmniTaskTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) {
        darkColorScheme(
            primary = Color(0xFF7C4DFF),
            onPrimary = Color.White,
            secondary = Color(0xFF00E5FF),
            tertiary = Color(0xFFFF4081),
            background = Color(0xFF0B0B14),
            surface = Color(0xFF14141F),
            surfaceVariant = Color(0xFF1E1E2E),
            onSurface = Color(0xFFE8E8F0),
            onSurfaceVariant = Color(0xFF9E9EB3)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF6200EE),
            secondary = Color(0xFF00A6B8),
            tertiary = Color(0xFFD81B60),
            background = Color(0xFFF7F6FC),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE9E7F5)
        )
    }
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content
    )
}
