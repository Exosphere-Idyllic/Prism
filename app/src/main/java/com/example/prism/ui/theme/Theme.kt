package com.example.prism.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.graphics.Brush

private val DarkColorScheme = darkColorScheme(
    primary = AppAccent,
    secondary = AppAccentSoft,
    background = AppBgDarkest,
    surface = AppSurface,
    onPrimary = AppTextPrimary,
    onSecondary = AppTextPrimary,
    onBackground = AppTextPrimary,
    onSurface = AppTextPrimary
)

private val LightColorScheme = DarkColorScheme

val AppBackgroundBrush: Brush = Brush.verticalGradient(
    colors = listOf(AppBgDeep, AppBgDeeper, AppBgDarkest)
)

val PlayerBackgroundBrush: Brush = Brush.verticalGradient(
    colors = listOf(AppBgTop, AppBgBottom)
)

@Composable
fun PrismTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
