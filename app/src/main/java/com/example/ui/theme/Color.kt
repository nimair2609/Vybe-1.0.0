package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// VYBE 2.0 Official Brand Identity Palette
val VybeSocialViolet = Color(0xFF7C3AED)
val VybeSocialVioletBright = Color(0xFF8B5CF6)
val VybeGrowBlue = Color(0xFF2563EB)
val VybeGrowBlueBright = Color(0xFF0284C7)
val VybeGrowTeal = Color(0xFF0EA5E9)

// Proprietary V-Heart Pulse Reaction Palette
val VybePulseBlueStart = Color(0xFF38BDF8)
val VybePulseIndigoMid = Color(0xFF6366F1)
val VybePulsePurpleEnd = Color(0xFFA855F7)
val VybePulseVioletGlow = Color(0xFF8B5CF6)

// Backward-compatible brand aliases used across modules
val VybeBrandCyan = VybeGrowBlueBright
val VybeBrandBlue = VybeGrowBlue
val VybeBrandViolet = VybeSocialVioletBright
val VybeBrandMagenta = Color(0xFFD946EF)

val VybeViolet = VybeSocialVioletBright
val VybeVioletDark = Color(0xFF6D28D9)
val VybeMagenta = VybeBrandMagenta
val VybeCyan = VybeBrandCyan
val VybeElectricCyan = VybeBrandCyan

val VybeGradient = Brush.horizontalGradient(
  listOf(VybeSocialViolet, VybeSocialVioletBright, VybeGrowBlue)
)

// Mood Zone Signature Colors
val MoodHeartbreak = Color(0xFF818CF8)
val VybeHeartbreakRed = Color(0xFFEF4444)
val MoodHappy = Color(0xFFFBBF24)
val MoodChill = Color(0xFF34D399)
val MoodHealing = Color(0xFF2DD4BF)
val MoodFun = Color(0xFFF43F5E)

// VYBE Social Dark (Editorial Obsidian Default)
val SocialDarkBackground = Color(0xFF090A0F)
val SocialDarkSurface = Color(0xFF12151E)
val SocialDarkElevated = Color(0xFF191D29)
val SocialDarkBorder = Color(0xFF23293A)
val SocialDarkTextPrimary = Color(0xFFF8FAFC)
val SocialDarkTextSecondary = Color(0xFFA1ACBE)
val SocialDarkTextMuted = Color(0xFF64748B)

// VYBE Social Light
val SocialLightBackground = Color(0xFFF8FAFC)
val SocialLightSurface = Color(0xFFFFFFFF)
val SocialLightElevated = Color(0xFFF1F5F9)
val SocialLightBorder = Color(0xFFE2E8F0)
val SocialLightTextPrimary = Color(0xFF0F172A)
val SocialLightTextSecondary = Color(0xFF475569)
val SocialLightTextMuted = Color(0xFF94A3B8)

// VYBE Grow Light (Clean Professional Slate)
val GrowLightBackground = Color(0xFFF6F8FC)
val GrowLightSurface = Color(0xFFFFFFFF)
val GrowLightElevated = Color(0xFFEEF2F7)
val GrowLightBorder = Color(0xFFE2E8F0)
val GrowLightTextPrimary = Color(0xFF0F172A)
val GrowLightTextSecondary = Color(0xFF475569)
val GrowLightTextMuted = Color(0xFF64748B)

// VYBE Grow Dark
val GrowDarkBackground = Color(0xFF0B101B)
val GrowDarkSurface = Color(0xFF111827)
val GrowDarkElevated = Color(0xFF1E293B)
val GrowDarkBorder = Color(0xFF263349)
val GrowDarkTextPrimary = Color(0xFFF8FAFC)
val GrowDarkTextSecondary = Color(0xFF94A3B8)
val GrowDarkTextMuted = Color(0xFF64748B)

// Legacy Surface Aliases mapped cleanly to VYBE 2.0 Tokens
val ObsidianBackground = SocialDarkBackground
val ObsidianSurface = SocialDarkSurface
val ObsidianSurfaceElevated = SocialDarkElevated
val ObsidianBorder = SocialDarkBorder
val ObsidianTextPrimary = SocialDarkTextPrimary
val ObsidianTextSecondary = SocialDarkTextSecondary
val ObsidianTextMuted = SocialDarkTextMuted

val LightBackground = SocialLightBackground
val LightSurface = SocialLightSurface
val LightSurfaceElevated = SocialLightElevated
val LightBorder = SocialLightBorder
val LightTextPrimary = SocialLightTextPrimary
val LightTextSecondary = SocialLightTextSecondary
val LightTextMuted = SocialLightTextMuted

// Status & Verification Colors
val VybeVerifiedGold = Color(0xFFFFB703)
val VybeVerifiedBlue = Color(0xFF38BDF8)
val VybeOnlineGreen = Color(0xFF22C55E)
val VybeWarningAmber = Color(0xFFF59E0B)
val VybeDanger = Color(0xFFEF4444)
