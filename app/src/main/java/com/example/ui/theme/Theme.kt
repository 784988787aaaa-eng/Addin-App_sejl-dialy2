package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Navy900 = Color(0xFF141E2E)
val Navy950 = Color(0xFF0E1622)
val BrandBlue = Color(0xFF2563EB)
val BrandGreen = Color(0xFF10A36A)
val BrandRose = Color(0xFFE11D48)
val SurfaceBg = Color(0xFFF3F6FB)

private val SmartTradeColorScheme = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    secondary = BrandGreen,
    onSecondary = Color.White,
    tertiary = BrandRose,
    background = SurfaceBg,
    surface = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF1E293B)
)

@Composable
fun SmartTradeBookTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SmartTradeColorScheme,
        content = content
    )
}
