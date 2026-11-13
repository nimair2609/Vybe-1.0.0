package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.data.model.AppThemeMode

enum class VybeExperienceMode {
  SOCIAL,
  GROW
}

object VybeTokens {
  object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
  }

  object Radius {
    val xs = 6.dp
    val sm = 10.dp
    val md = 14.dp
    val lg = 18.dp
    val xl = 22.dp
    val pill = 999.dp
  }

  object Size {
    val touchTargetMin = 48.dp
    val buttonHeight = 48.dp
    val inputHeight = 48.dp
    val topBarHeight = 56.dp
  }
}

@Immutable
data class VybePalette(
  val isDark: Boolean,
  val experienceMode: VybeExperienceMode,
  val canvas: Color,
  val card: Color,
  val elevated: Color,
  val border: Color,
  val textPrimary: Color,
  val textSecondary: Color,
  val textMuted: Color,
  val accent: Color,
  val accentSecondary: Color,
  val success: Color,
  val warning: Color,
  val danger: Color
)

val SocialDarkPalette = VybePalette(
  isDark = true,
  experienceMode = VybeExperienceMode.SOCIAL,
  canvas = SocialDarkBackground,
  card = SocialDarkSurface,
  elevated = SocialDarkElevated,
  border = SocialDarkBorder,
  textPrimary = SocialDarkTextPrimary,
  textSecondary = SocialDarkTextSecondary,
  textMuted = SocialDarkTextMuted,
  accent = VybeSocialVioletBright,
  accentSecondary = VybeGrowBlueBright,
  success = VybeOnlineGreen,
  warning = VybeWarningAmber,
  danger = VybeDanger
)

val SocialLightPalette = VybePalette(
  isDark = false,
  experienceMode = VybeExperienceMode.SOCIAL,
  canvas = SocialLightBackground,
  card = SocialLightSurface,
  elevated = SocialLightElevated,
  border = SocialLightBorder,
  textPrimary = SocialLightTextPrimary,
  textSecondary = SocialLightTextSecondary,
  textMuted = SocialLightTextMuted,
  accent = VybeSocialViolet,
  accentSecondary = VybeGrowBlue,
  success = Color(0xFF16A34A),
  warning = Color(0xFFD97706),
  danger = Color(0xFFDC2626)
)

val GrowLightPalette = VybePalette(
  isDark = false,
  experienceMode = VybeExperienceMode.GROW,
  canvas = GrowLightBackground,
  card = GrowLightSurface,
  elevated = GrowLightElevated,
  border = GrowLightBorder,
  textPrimary = GrowLightTextPrimary,
  textSecondary = GrowLightTextSecondary,
  textMuted = GrowLightTextMuted,
  accent = VybeGrowBlue,
  accentSecondary = VybeGrowBlueBright,
  success = Color(0xFF16A34A),
  warning = Color(0xFFD97706),
  danger = Color(0xFFDC2626)
)

val GrowDarkPalette = VybePalette(
  isDark = true,
  experienceMode = VybeExperienceMode.GROW,
  canvas = GrowDarkBackground,
  card = GrowDarkSurface,
  elevated = GrowDarkElevated,
  border = GrowDarkBorder,
  textPrimary = GrowDarkTextPrimary,
  textSecondary = GrowDarkTextSecondary,
  textMuted = GrowDarkTextMuted,
  accent = Color(0xFF3B82F6),
  accentSecondary = Color(0xFF38BDF8),
  success = VybeOnlineGreen,
  warning = VybeWarningAmber,
  danger = VybeDanger
)

val LocalIsDarkTheme = staticCompositionLocalOf { true }
val LocalVybeExperienceMode = staticCompositionLocalOf { VybeExperienceMode.SOCIAL }
val LocalVybePalette = staticCompositionLocalOf { SocialDarkPalette }

private val SocialDarkColorScheme = darkColorScheme(
  primary = VybeSocialVioletBright,
  onPrimary = Color.White,
  primaryContainer = Color(0xFF2E1065),
  onPrimaryContainer = Color.White,
  secondary = VybeBrandMagenta,
  onSecondary = Color.White,
  secondaryContainer = Color(0xFF831843),
  onSecondaryContainer = Color.White,
  tertiary = VybeGrowBlueBright,
  onTertiary = Color.White,
  background = SocialDarkBackground,
  onBackground = SocialDarkTextPrimary,
  surface = SocialDarkSurface,
  onSurface = SocialDarkTextPrimary,
  surfaceVariant = SocialDarkElevated,
  onSurfaceVariant = SocialDarkTextSecondary,
  surfaceContainerLowest = Color(0xFF05060A),
  surfaceContainerLow = Color(0xFF0D0F16),
  surfaceContainer = SocialDarkSurface,
  surfaceContainerHigh = SocialDarkElevated,
  surfaceContainerHighest = Color(0xFF23293A),
  outline = SocialDarkBorder,
  outlineVariant = Color(0xFF1C2130),
  inverseSurface = Color(0xFFF8FAFC),
  inverseOnSurface = Color(0xFF0F172A),
  error = VybeDanger
)

private val SocialLightColorScheme = lightColorScheme(
  primary = VybeSocialViolet,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFEDE9FE),
  onPrimaryContainer = Color(0xFF2E1065),
  secondary = VybeBrandMagenta,
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFFCE7F3),
  onSecondaryContainer = Color(0xFF831843),
  tertiary = VybeGrowBlue,
  onTertiary = Color.White,
  background = SocialLightBackground,
  onBackground = SocialLightTextPrimary,
  surface = SocialLightSurface,
  onSurface = SocialLightTextPrimary,
  surfaceVariant = SocialLightElevated,
  onSurfaceVariant = SocialLightTextSecondary,
  surfaceContainerLowest = Color.White,
  surfaceContainerLow = Color(0xFFF8FAFC),
  surfaceContainer = Color(0xFFF1F5F9),
  surfaceContainerHigh = Color(0xFFE2E8F0),
  surfaceContainerHighest = Color(0xFFCBD5E1),
  outline = SocialLightBorder,
  outlineVariant = Color(0xFFE2E8F0),
  inverseSurface = Color(0xFF0F172A),
  inverseOnSurface = Color(0xFFF8FAFC),
  error = Color(0xFFDC2626)
)

private val GrowDarkColorScheme = darkColorScheme(
  primary = Color(0xFF3B82F6),
  onPrimary = Color.White,
  primaryContainer = Color(0xFF1E3A8A),
  onPrimaryContainer = Color.White,
  secondary = Color(0xFF38BDF8),
  onSecondary = Color(0xFF082F49),
  tertiary = VybeSocialVioletBright,
  onTertiary = Color.White,
  background = GrowDarkBackground,
  onBackground = GrowDarkTextPrimary,
  surface = GrowDarkSurface,
  onSurface = GrowDarkTextPrimary,
  surfaceVariant = GrowDarkElevated,
  onSurfaceVariant = GrowDarkTextSecondary,
  surfaceContainerLowest = Color(0xFF070B13),
  surfaceContainerLow = Color(0xFF0F1523),
  surfaceContainer = GrowDarkSurface,
  surfaceContainerHigh = GrowDarkElevated,
  surfaceContainerHighest = Color(0xFF263349),
  outline = GrowDarkBorder,
  outlineVariant = Color(0xFF1E293B),
  inverseSurface = Color(0xFFF8FAFC),
  inverseOnSurface = Color(0xFF0F172A),
  error = VybeDanger
)

private val GrowLightColorScheme = lightColorScheme(
  primary = VybeGrowBlue,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFDBEAFE),
  onPrimaryContainer = Color(0xFF1E3A8A),
  secondary = VybeGrowBlueBright,
  onSecondary = Color.White,
  tertiary = VybeSocialViolet,
  onTertiary = Color.White,
  background = GrowLightBackground,
  onBackground = GrowLightTextPrimary,
  surface = GrowLightSurface,
  onSurface = GrowLightTextPrimary,
  surfaceVariant = GrowLightElevated,
  onSurfaceVariant = GrowLightTextSecondary,
  surfaceContainerLowest = Color.White,
  surfaceContainerLow = GrowLightBackground,
  surfaceContainer = GrowLightElevated,
  surfaceContainerHigh = Color(0xFFE2E8F0),
  surfaceContainerHighest = Color(0xFFCBD5E1),
  outline = GrowLightBorder,
  outlineVariant = GrowLightBorder,
  inverseSurface = Color(0xFF0F172A),
  inverseOnSurface = Color(0xFFF8FAFC),
  error = Color(0xFFDC2626)
)

@Composable
fun VybeTheme(
  themeMode: AppThemeMode = AppThemeMode.DARK,
  darkTheme: Boolean = when (themeMode) {
    AppThemeMode.DARK -> true
    AppThemeMode.LIGHT -> false
    AppThemeMode.SYSTEM -> isSystemInDarkTheme()
  },
  experienceMode: VybeExperienceMode = VybeExperienceMode.SOCIAL,
  content: @Composable () -> Unit
) {
  val palette = when (experienceMode) {
    VybeExperienceMode.SOCIAL -> if (darkTheme) SocialDarkPalette else SocialLightPalette
    VybeExperienceMode.GROW -> if (darkTheme) GrowDarkPalette else GrowLightPalette
  }
  val colorScheme = when (experienceMode) {
    VybeExperienceMode.SOCIAL -> if (darkTheme) SocialDarkColorScheme else SocialLightColorScheme
    VybeExperienceMode.GROW -> if (darkTheme) GrowDarkColorScheme else GrowLightColorScheme
  }

  CompositionLocalProvider(
    LocalIsDarkTheme provides darkTheme,
    LocalVybeExperienceMode provides experienceMode,
    LocalVybePalette provides palette
  ) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = Typography,
      content = content
    )
  }
}
