package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.data.model.AppThemeMode

val LocalIsDarkTheme = staticCompositionLocalOf { true }

private val DarkColorScheme = darkColorScheme(
  primary = VybeBrandViolet,
  onPrimary = Color.White,
  primaryContainer = VybeVioletDark,
  onPrimaryContainer = Color.White,
  secondary = VybeBrandMagenta,
  onSecondary = Color.White,
  secondaryContainer = Color(0xFF831843),
  onSecondaryContainer = Color.White,
  tertiary = VybeBrandCyan,
  onTertiary = Color.Black,
  background = ObsidianBackground,
  onBackground = ObsidianTextPrimary,
  surface = ObsidianSurface,
  onSurface = ObsidianTextPrimary,
  surfaceVariant = ObsidianSurfaceElevated,
  onSurfaceVariant = ObsidianTextSecondary,
  surfaceContainerLowest = Color(0xFF05070E),
  surfaceContainerLow = Color(0xFF0A0E1A),
  surfaceContainer = Color(0xFF0F1629),
  surfaceContainerHigh = Color(0xFF161F33),
  surfaceContainerHighest = Color(0xFF1E293B),
  outline = ObsidianBorder,
  outlineVariant = Color(0xFF1B2338),
  inverseSurface = Color(0xFFF1F5F9),
  inverseOnSurface = Color(0xFF0F172A),
  error = VybeDanger
)

private val LightColorScheme = lightColorScheme(
  primary = VybeVioletDark,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFEDE9FE),
  onPrimaryContainer = Color(0xFF2E1065),
  secondary = VybeBrandMagenta,
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFFCE7F3),
  onSecondaryContainer = Color(0xFF831843),
  tertiary = Color(0xFF0284C7),
  onTertiary = Color.White,
  background = LightBackground,
  onBackground = LightTextPrimary,
  surface = LightSurface,
  onSurface = LightTextPrimary,
  surfaceVariant = LightSurfaceElevated,
  onSurfaceVariant = LightTextSecondary,
  surfaceContainerLowest = Color.White,
  surfaceContainerLow = Color(0xFFF8FAFC),
  surfaceContainer = Color(0xFFF1F5F9),
  surfaceContainerHigh = Color(0xFFE2E8F0),
  surfaceContainerHighest = Color(0xFFCBD5E1),
  outline = LightBorder,
  outlineVariant = Color(0xFFE2E8F0),
  inverseSurface = Color(0xFF0F172A),
  inverseOnSurface = Color(0xFFF8FAFC),
  error = VybeDanger
)

@Composable
fun VybeTheme(
  themeMode: AppThemeMode = AppThemeMode.DARK,
  darkTheme: Boolean = when (themeMode) {
    AppThemeMode.DARK -> true
    AppThemeMode.LIGHT -> false
    AppThemeMode.SYSTEM -> isSystemInDarkTheme()
  },
  content: @Composable () -> Unit
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      content = content
    )
  }
}
