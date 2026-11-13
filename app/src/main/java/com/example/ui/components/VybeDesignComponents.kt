package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun VybeBrandWordmark(
  isGrowMode: Boolean = false,
  showSubLabel: Boolean = false,
  modifier: Modifier = Modifier
) {
  val palette = LocalVybePalette.current
  Column(modifier = modifier) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      // Stylized Violet-Blue V Emblem + YBE
      Text(
        text = "V",
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Black,
        fontSize = 22.sp,
        color = if (isGrowMode) VybeGrowBlue else VybeSocialVioletBright,
        letterSpacing = (-0.5).sp
      )
      Text(
        text = "YBE",
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 21.sp,
        color = palette.textPrimary,
        letterSpacing = 1.sp
      )
      if (isGrowMode) {
        Spacer(modifier = Modifier.width(6.dp))
        Surface(
          color = VybeGrowBlue.copy(alpha = 0.14f),
          shape = RoundedCornerShape(VybeTokens.Radius.xs),
          border = BorderStroke(1.dp, VybeGrowBlue.copy(alpha = 0.3f))
        ) {
          Text(
            text = "Grow",
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = palette.accent,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
          )
        }
      }
    }
    if (showSubLabel) {
      Text(
        text = if (isGrowMode) "LEARN • COLLABORATE • GROW" else "CONNECT • EXPRESS • GROW",
        fontFamily = InterFontFamily,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        color = palette.textMuted,
        letterSpacing = 1.1.sp
      )
    }
  }
}

@Composable
fun VybeModeSwitch(
  currentMode: VybeExperienceMode,
  onModeChange: (VybeExperienceMode) -> Unit,
  modifier: Modifier = Modifier
) {
  val palette = LocalVybePalette.current
  Surface(
    color = palette.elevated,
    shape = RoundedCornerShape(VybeTokens.Radius.pill),
    border = BorderStroke(1.dp, palette.border),
    modifier = modifier
      .heightIn(min = 40.dp)
      .testTag("vybe_mode_switch")
  ) {
    Row(
      modifier = Modifier.padding(3.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      listOf(
        VybeExperienceMode.SOCIAL to "Social",
        VybeExperienceMode.GROW to "Grow"
      ).forEach { (mode, label) ->
        val selected = currentMode == mode
        val bg by animateColorAsState(
          targetValue = when {
            selected && mode == VybeExperienceMode.SOCIAL -> VybeSocialViolet
            selected && mode == VybeExperienceMode.GROW -> VybeGrowBlue
            else -> Color.Transparent
          },
          animationSpec = tween(220),
          label = "modeBg"
        )
        val textColor by animateColorAsState(
          targetValue = if (selected) Color.White else palette.textSecondary,
          animationSpec = tween(220),
          label = "modeText"
        )
        Box(
          contentAlignment = Alignment.Center,
          modifier = Modifier
            .clip(RoundedCornerShape(VybeTokens.Radius.pill))
            .background(bg)
            .clickable { onModeChange(mode) }
            .padding(horizontal = 16.dp, vertical = 7.dp)
            .testTag("mode_switch_${mode.name.lowercase()}")
        ) {
          Text(
            text = label,
            fontFamily = PlusJakartaSansFontFamily,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = textColor,
            maxLines = 1
          )
        }
      }
    }
  }
}

@Composable
fun VybeSubScreenTopBar(
  title: String,
  subtitle: String? = null,
  onBackClick: () -> Unit,
  actions: @Composable RowScope.() -> Unit = {},
  backTestTag: String = "subscreen_back_button"
) {
  val palette = LocalVybePalette.current
  Surface(
    color = palette.canvas,
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(modifier = Modifier.statusBarsPadding()) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = VybeTokens.Size.topBarHeight)
          .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = onBackClick,
          modifier = Modifier
            .size(48.dp)
            .testTag(backTestTag)
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = palette.textPrimary
          )
        }
        Column(
          modifier = Modifier
            .weight(1f)
            .padding(horizontal = 4.dp)
        ) {
          Text(
            text = title,
            fontFamily = PlusJakartaSansFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = palette.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          if (!subtitle.isNullOrBlank()) {
            Text(
              text = subtitle,
              fontFamily = InterFontFamily,
              fontSize = 12.sp,
              color = palette.textSecondary,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          content = actions
        )
      }
      HorizontalDivider(thickness = 0.8.dp, color = palette.border)
    }
  }
}

@Composable
fun VybeNotificationBellButton(
  unreadCount: Int,
  onClick: () -> Unit,
  testTag: String = "home_notifications_button"
) {
  val palette = LocalVybePalette.current
  Box(contentAlignment = Alignment.Center) {
    IconButton(
      onClick = onClick,
      modifier = Modifier
        .size(48.dp)
        .testTag(testTag)
    ) {
      Icon(
        imageVector = Icons.Outlined.Notifications,
        contentDescription = "Notifications",
        tint = palette.textPrimary
      )
    }
    if (unreadCount > 0) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
          .align(Alignment.TopEnd)
          .padding(top = 6.dp, end = 6.dp)
          .sizeIn(minWidth = 17.dp, minHeight = 17.dp)
          .clip(CircleShape)
          .background(palette.danger)
          .padding(horizontal = 4.dp)
      ) {
        Text(
          text = if (unreadCount > 99) "99+" else unreadCount.toString(),
          color = Color.White,
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold
        )
      }
    }
  }
}

@Composable
fun VybeSurfaceCard(
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  cornerRadius: Dp = VybeTokens.Radius.lg,
  borderColor: Color? = null,
  containerColor: Color? = null,
  contentPadding: PaddingValues = PaddingValues(16.dp),
  content: @Composable ColumnScope.() -> Unit
) {
  val palette = LocalVybePalette.current
  val shape = RoundedCornerShape(cornerRadius)
  val cardModifier = modifier
    .clip(shape)
    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)

  Surface(
    color = containerColor ?: palette.card,
    contentColor = palette.textPrimary,
    shape = shape,
    border = BorderStroke(1.dp, borderColor ?: palette.border),
    tonalElevation = 0.dp,
    modifier = cardModifier
  ) {
    Column(
      modifier = Modifier.padding(contentPadding),
      content = content
    )
  }
}

@Composable
fun VybeSearchBar(
  query: String,
  onQueryChange: (String) -> Unit,
  placeholder: String,
  onClear: () -> Unit = { onQueryChange("") },
  modifier: Modifier = Modifier,
  testTag: String = "vybe_search_bar"
) {
  val palette = LocalVybePalette.current
  Surface(
    color = palette.elevated,
    shape = RoundedCornerShape(VybeTokens.Radius.md),
    border = BorderStroke(1.dp, palette.border),
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = VybeTokens.Size.inputHeight)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = Icons.Default.Search,
        contentDescription = null,
        tint = palette.textMuted,
        modifier = Modifier.size(20.dp)
      )
      Spacer(modifier = Modifier.width(10.dp))
      Box(modifier = Modifier.weight(1f)) {
        if (query.isEmpty()) {
          Text(
            text = placeholder,
            fontSize = 14.sp,
            color = palette.textMuted
          )
        }
        BasicTextField(
          value = query,
          onValueChange = onQueryChange,
          singleLine = true,
          textStyle = TextStyle(
            color = palette.textPrimary,
            fontSize = 14.sp,
            fontFamily = InterFontFamily
          ),
          cursorBrush = SolidColor(palette.accent),
          modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
        )
      }
      if (query.isNotEmpty()) {
        IconButton(
          onClick = onClear,
          modifier = Modifier.size(32.dp)
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Clear search",
            tint = palette.textSecondary,
            modifier = Modifier.size(16.dp)
          )
        }
      }
    }
  }
}

@Composable
fun VybeUnderlineTabs(
  tabs: List<String>,
  selectedIndex: Int,
  onTabSelected: (Int) -> Unit,
  modifier: Modifier = Modifier,
  testTagPrefix: String = "vybe_tab"
) {
  val palette = LocalVybePalette.current
  Column(modifier = modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState())
        .padding(horizontal = 12.dp),
      horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
      tabs.forEachIndexed { index, label ->
        val selected = index == selectedIndex
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier
            .clickable { onTabSelected(index) }
            .padding(top = 10.dp)
            .testTag("${testTagPrefix}_${label.lowercase().replace(" ", "_")}")
        ) {
          Text(
            text = label,
            fontFamily = PlusJakartaSansFontFamily,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) palette.textPrimary else palette.textMuted
          )
          Spacer(modifier = Modifier.height(8.dp))
          Box(
            modifier = Modifier
              .width(36.dp)
              .height(2.5.dp)
              .clip(RoundedCornerShape(VybeTokens.Radius.pill))
              .background(if (selected) palette.accent else Color.Transparent)
          )
        }
      }
    }
    HorizontalDivider(thickness = 0.8.dp, color = palette.border)
  }
}

@Composable
fun VybePrimaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  isLoading: Boolean = false,
  icon: ImageVector? = null,
  containerColor: Color? = null,
  testTag: String? = null
) {
  val palette = LocalVybePalette.current
  Button(
    onClick = onClick,
    enabled = enabled && !isLoading,
    colors = ButtonDefaults.buttonColors(
      containerColor = containerColor ?: palette.accent,
      contentColor = Color.White,
      disabledContainerColor = palette.elevated,
      disabledContentColor = palette.textMuted
    ),
    shape = RoundedCornerShape(VybeTokens.Radius.md),
    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    modifier = modifier
      .heightIn(min = VybeTokens.Size.buttonHeight)
      .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
  ) {
    if (isLoading) {
      CircularProgressIndicator(
        color = Color.White,
        strokeWidth = 2.dp,
        modifier = Modifier.size(18.dp)
      )
      Spacer(modifier = Modifier.width(8.dp))
    } else if (icon != null) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(18.dp)
      )
      Spacer(modifier = Modifier.width(8.dp))
    }
    Text(
      text = text,
      fontFamily = PlusJakartaSansFontFamily,
      fontWeight = FontWeight.Bold,
      fontSize = 14.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
fun VybeSecondaryButton(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  icon: ImageVector? = null,
  testTag: String? = null
) {
  val palette = LocalVybePalette.current
  OutlinedButton(
    onClick = onClick,
    enabled = enabled,
    border = BorderStroke(1.dp, palette.border),
    colors = ButtonDefaults.outlinedButtonColors(
      containerColor = palette.elevated,
      contentColor = palette.textPrimary
    ),
    shape = RoundedCornerShape(VybeTokens.Radius.md),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    modifier = modifier
      .heightIn(min = VybeTokens.Size.buttonHeight)
      .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
  ) {
    if (icon != null) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = palette.textPrimary,
        modifier = Modifier.size(18.dp)
      )
      Spacer(modifier = Modifier.width(8.dp))
    }
    Text(
      text = text,
      fontFamily = PlusJakartaSansFontFamily,
      fontWeight = FontWeight.SemiBold,
      fontSize = 14.sp,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis
    )
  }
}

@Composable
fun VybeEmptyState(
  icon: ImageVector,
  title: String,
  subtitle: String,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
  modifier: Modifier = Modifier
) {
  val palette = LocalVybePalette.current
  VybeSurfaceCard(
    modifier = modifier.fillMaxWidth(),
    contentPadding = PaddingValues(24.dp)
  ) {
    Column(
      modifier = Modifier.fillMaxWidth(),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Box(
        modifier = Modifier
          .size(56.dp)
          .clip(RoundedCornerShape(VybeTokens.Radius.lg))
          .background(palette.accent.copy(alpha = 0.14f))
          .border(1.dp, palette.accent.copy(alpha = 0.3f), RoundedCornerShape(VybeTokens.Radius.lg)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = palette.accent,
          modifier = Modifier.size(28.dp)
        )
      }
      Spacer(modifier = Modifier.height(14.dp))
      Text(
        text = title,
        fontFamily = PlusJakartaSansFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = palette.textPrimary,
        textAlign = TextAlign.Center
      )
      Spacer(modifier = Modifier.height(6.dp))
      Text(
        text = subtitle,
        fontSize = 13.sp,
        color = palette.textSecondary,
        textAlign = TextAlign.Center,
        lineHeight = 18.sp
      )
      if (!actionLabel.isNullOrBlank() && onAction != null) {
        Spacer(modifier = Modifier.height(16.dp))
        VybePrimaryButton(
          text = actionLabel,
          onClick = onAction
        )
      }
    }
  }
}

@Composable
fun VybeProgressRing(
  progress: Float,
  label: String,
  modifier: Modifier = Modifier,
  size: Dp = 76.dp,
  strokeWidth: Dp = 7.dp
) {
  val palette = LocalVybePalette.current
  val clamped = progress.coerceIn(0f, 1f)
  Box(
    modifier = modifier.size(size),
    contentAlignment = Alignment.Center
  ) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
      drawArc(
        color = palette.border,
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        style = stroke
      )
      drawArc(
        brush = Brush.sweepGradient(listOf(VybeGrowBlue, VybeGrowBlueBright, VybeSocialVioletBright)),
        startAngle = -90f,
        sweepAngle = 360f * clamped,
        useCenter = false,
        style = stroke
      )
    }
    Text(
      text = label,
      fontFamily = PlusJakartaSansFontFamily,
      fontWeight = FontWeight.ExtraBold,
      fontSize = 16.sp,
      color = palette.textPrimary
    )
  }
}

@Composable
fun VybeActivityMiniBars(
  values: List<Float>,
  modifier: Modifier = Modifier
) {
  val palette = LocalVybePalette.current
  Row(
    modifier = modifier.height(34.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    verticalAlignment = Alignment.Bottom
  ) {
    values.forEachIndexed { idx, value ->
      val ratio = value.coerceIn(0.12f, 1f)
      val barColor = if (idx == values.lastIndex) palette.accent else palette.accent.copy(alpha = 0.42f)
      Box(
        modifier = Modifier
          .width(7.dp)
          .fillMaxHeight(ratio)
          .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
          .background(barColor)
      )
    }
  }
}
