package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.VybeViewModel
import com.example.ui.components.VybeSubScreenTopBar
import com.example.ui.theme.LocalVybePalette

/**
 * Legacy route wrapper that delegates directly to the single authoritative
 * GrowCircleWorkspace implementation so Circle UI and state remain unified.
 */
@Composable
fun CircleScreen(viewModel: VybeViewModel) {
  BackHandler {
    viewModel.navigateBack()
  }

  val palette = LocalVybePalette.current

  Scaffold(
    contentWindowInsets = WindowInsets(0.dp),
    containerColor = palette.canvas,
    topBar = {
      VybeSubScreenTopBar(
        title = "VYBE Circle",
        subtitle = "Private circles & professional peer networks",
        onBackClick = { viewModel.navigateBack() },
        backTestTag = "circle_back_button"
      )
    }
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
        .navigationBarsPadding()
    ) {
      GrowCircleWorkspace(viewModel = viewModel)
    }
  }
}
