package com.example.data.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.MoodChill
import com.example.ui.theme.MoodFun
import com.example.ui.theme.MoodHappy
import com.example.ui.theme.MoodHealing
import com.example.ui.theme.MoodHeartbreak

enum class MoodType(
  val title: String,
  val subtitle: String,
  val emoji: String,
  val accentColor: Color,
  val vibeTagline: String,
  val safetyGuideline: String
) {
  HEARTBREAK(
    title = "Heartbreak",
    subtitle = "Supportive reflections & solidarity",
    emoji = "💔",
    accentColor = MoodHeartbreak,
    vibeTagline = "A safe space for honesty, healing tears, and silent company.",
    safetyGuideline = "Be kind. Hostility or unwanted romantic pressure is prohibited."
  ),
  HAPPY(
    title = "Happy",
    subtitle = "Celebrations, gratitude & wins",
    emoji = "✨",
    accentColor = MoodHappy,
    vibeTagline = "Share your wins, everyday joys, and wholesome energy.",
    safetyGuideline = "Celebrate together and keep the vibes uplifting!"
  ),
  CHILL(
    title = "Chill",
    subtitle = "Lo-fi beats, thoughts & cozy moments",
    emoji = "☕",
    accentColor = MoodChill,
    vibeTagline = "Low-stimulus zone for slow thoughts, coffee, and calm music.",
    safetyGuideline = "No loud arguments or controversial debates here."
  ),
  HEALING(
    title = "Healing & Beginnings",
    subtitle = "Growth, fresh starts & encouragement",
    emoji = "🌱",
    accentColor = MoodHealing,
    vibeTagline = "Reset your trajectory. Share milestones and small breakthroughs.",
    safetyGuideline = "Encouragement only. Vulnerability is respected."
  ),
  FUN(
    title = "Fun",
    subtitle = "Memes, gaming banter & creative chaos",
    emoji = "🔥",
    accentColor = MoodFun,
    vibeTagline = "Unfiltered laughter, viral Indian memes, and quick challenges.",
    safetyGuideline = "Keep it lighthearted; no harassment masked as jokes."
  )
}
