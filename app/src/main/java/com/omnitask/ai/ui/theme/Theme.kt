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
            primary = Color(0xFF8AB4F8),
            secondary = Color(0xFF81C995),
            tertiary = Color(0xFFF28B82),
            background = Color(0xFF17181C),
            surface = Color(0xFF1F2025),
            surfaceVariant = Color(0xFF2B2D34)
        )
    } else {
        lightColorScheme(
            primary = Color(0xFF1A73E8),
            secondary = Color(0xFF188038),
            tertiary = Color(0xFFD93025),
            background = Color(0xFFF6F8FC),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFE8EAED)
        )
    }
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content
    )
}
