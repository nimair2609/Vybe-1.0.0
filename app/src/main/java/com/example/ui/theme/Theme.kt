package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
  primary = VybeViolet,
  onPrimary = Color.White,
  primaryContainer = VybeVioletDark,
  onPrimaryContainer = Color.White,
  secondary = VybeMagenta,
  onSecondary = Color.White,
  secondaryContainer = Color(0xFF831843),
  onSecondaryContainer = Color.White,
  tertiary = VybeCyan,
  onTertiary = Color.Black,
  background = ObsidianBackground,
  onBackground = ObsidianTextPrimary,
  surface = ObsidianSurface,
  onSurface = ObsidianTextPrimary,
  surfaceVariant = ObsidianSurfaceElevated,
  onSurfaceVariant = ObsidianTextSecondary,
  outline = ObsidianBorder,
  error = VybeDanger
)

private val LightColorScheme = lightColorScheme(
  primary = VybeVioletDark,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFDDD6FE),
  onPrimaryContainer = Color(0xFF2E1065),
  secondary = VybeMagenta,
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFFCE7F3),
  onSecondaryContainer = Color(0xFF831843),
  tertiary = VybeCyan,
  onTertiary = Color.Black,
  background = LightBackground,
  onBackground = LightTextPrimary,
  surface = LightSurface,
  onSurface = LightTextPrimary,
  surfaceVariant = LightSurfaceElevated,
  onSurfaceVariant = LightTextSecondary,
  outline = LightBorder,
  error = VybeDanger
)

@Composable
fun VybeTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content
  )
}
