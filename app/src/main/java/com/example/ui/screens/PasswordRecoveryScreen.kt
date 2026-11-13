package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.repository.PasswordRecoveryState
import com.example.ui.VybeViewModel
import com.example.ui.components.VybePrimaryButton
import com.example.ui.components.VybeSecondaryButton
import com.example.ui.components.VybeSubScreenTopBar
import com.example.ui.components.VybeSurfaceCard
import com.example.ui.theme.LocalVybePalette
import com.example.ui.theme.PlusJakartaSansFontFamily
import com.example.ui.theme.VybeTokens
import kotlinx.coroutines.launch

/**
 * Dedicated VYBE Password Recovery Screen.
 *
 * Triggered when the user opens `vybe://reset-password` from the Supabase recovery email
 * (or enters a Supabase recovery token manually).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordRecoveryScreen(viewModel: VybeViewModel) {
  val palette = LocalVybePalette.current
  val recoveryState by viewModel.passwordRecoveryState.collectAsState()
  val scope = rememberCoroutineScope()

  var emailInput by remember { mutableStateOf("") }
  var recoveryTokenInput by remember { mutableStateOf("") }
  var newPassword by remember { mutableStateOf("") }
  var confirmPassword by remember { mutableStateOf("") }
  var showNewPass by remember { mutableStateOf(false) }
  var showConfirmPass by remember { mutableStateOf(false) }
  var inlineError by remember { mutableStateOf<String?>(null) }
  var resendSuccessMsg by remember { mutableStateOf<String?>(null) }
  var isBusy by remember { mutableStateOf(false) }

  LaunchedEffect(recoveryState) {
    when (val s = recoveryState) {
      is PasswordRecoveryState.NeedsTokenVerification -> {
        if (s.emailHint.isNotBlank() && emailInput.isBlank()) emailInput = s.emailHint
      }
      is PasswordRecoveryState.Error -> {
        if (s.emailHint.isNotBlank() && emailInput.isBlank()) emailInput = s.emailHint
      }
      is PasswordRecoveryState.ReadyForNewPassword -> {
        inlineError = null
      }
      else -> {}
    }
  }

  BackHandler {
    viewModel.exitPasswordRecoveryToLogin()
  }

  val fieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = palette.elevated,
    unfocusedContainerColor = palette.elevated,
    focusedBorderColor = palette.accent,
    unfocusedBorderColor = palette.border,
    focusedTextColor = palette.textPrimary,
    unfocusedTextColor = palette.textPrimary
  )

  Scaffold(
    contentWindowInsets = WindowInsets(0.dp),
    containerColor = palette.canvas,
    topBar = {
      VybeSubScreenTopBar(
        title = "Reset Password",
        subtitle = "VYBE Account Recovery",
        onBackClick = { viewModel.exitPasswordRecoveryToLogin() },
        backTestTag = "recovery_back_to_login"
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .navigationBarsPadding()
        .imePadding()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Surface(
        shape = RoundedCornerShape(VybeTokens.Radius.lg),
        color = palette.accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.35f)),
        modifier = Modifier.size(64.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            Icons.Default.LockReset,
            contentDescription = null,
            tint = palette.accent,
            modifier = Modifier.size(32.dp)
          )
        }
      }

      Spacer(Modifier.height(16.dp))

      Text(
        text = "VYBE Account Recovery",
        fontFamily = PlusJakartaSansFontFamily,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = palette.textPrimary
      )

      Spacer(Modifier.height(12.dp))

      when (val state = recoveryState) {
        PasswordRecoveryState.ValidatingLink -> {
          Spacer(Modifier.height(32.dp))
          CircularProgressIndicator(color = palette.accent)
          Spacer(Modifier.height(16.dp))
          Text(
            text = "Validating your recovery link securely with VYBE Auth...",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary
          )
          Spacer(Modifier.height(24.dp))
          TextButton(
            onClick = { viewModel.exitPasswordRecoveryToLogin() },
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("recovery_cancel_validating_login_button")
          ) {
            Text("Cancel & Back to Login", color = palette.accent)
          }
        }

        is PasswordRecoveryState.ReadyForNewPassword,
        is PasswordRecoveryState.UpdatingPassword -> {
          val verifiedEmail = when (state) {
            is PasswordRecoveryState.ReadyForNewPassword -> state.email
            is PasswordRecoveryState.UpdatingPassword -> state.email
            else -> ""
          }
          val isSubmitting = state is PasswordRecoveryState.UpdatingPassword || isBusy

          Surface(
            color = palette.success.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, palette.success.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(VybeTokens.Radius.md),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = palette.success,
                modifier = Modifier.size(20.dp)
              )
              Spacer(Modifier.width(10.dp))
              Text(
                text = if (verifiedEmail.isNotBlank()) "Recovery session verified for $verifiedEmail"
                else "Recovery session verified. Choose a strong new password.",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textPrimary
              )
            }
          }

          Spacer(Modifier.height(16.dp))

          VybeSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
              value = newPassword,
              onValueChange = {
                newPassword = it
                inlineError = null
              },
              label = { Text("New Password") },
              placeholder = { Text("At least 8 characters", color = palette.textMuted) },
              singleLine = true,
              visualTransformation = if (showNewPass) VisualTransformation.None else PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
              trailingIcon = {
                IconButton(onClick = { showNewPass = !showNewPass }) {
                  Icon(
                    imageVector = if (showNewPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (showNewPass) "Hide password" else "Show password",
                    tint = palette.textSecondary
                  )
                }
              },
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              colors = fieldColors,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("recovery_new_password_input")
            )

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
              value = confirmPassword,
              onValueChange = {
                confirmPassword = it
                inlineError = null
              },
              label = { Text("Confirm Password") },
              placeholder = { Text("Re-enter new password", color = palette.textMuted) },
              singleLine = true,
              visualTransformation = if (showConfirmPass) VisualTransformation.None else PasswordVisualTransformation(),
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
              trailingIcon = {
                IconButton(onClick = { showConfirmPass = !showConfirmPass }) {
                  Icon(
                    imageVector = if (showConfirmPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (showConfirmPass) "Hide password" else "Show password",
                    tint = palette.textSecondary
                  )
                }
              },
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              colors = fieldColors,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("recovery_confirm_password_input")
            )

            if (!inlineError.isNullOrBlank()) {
              Spacer(Modifier.height(12.dp))
              Surface(
                color = palette.danger.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, palette.danger.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(VybeTokens.Radius.md),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.padding(12.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = palette.danger)
                  Spacer(Modifier.width(8.dp))
                  Text(
                    text = inlineError!!,
                    color = palette.danger,
                    style = MaterialTheme.typography.bodySmall
                  )
                }
              }
            }

            Spacer(Modifier.height(20.dp))

            VybePrimaryButton(
              text = if (isSubmitting) "Updating Password..." else "Update Password",
              isLoading = isSubmitting,
              enabled = !isSubmitting,
              testTag = "recovery_submit_password_button",
              onClick = {
                if (isSubmitting) return@VybePrimaryButton
                if (newPassword.length < 8) {
                  inlineError = "New password must be at least 8 characters long."
                  return@VybePrimaryButton
                }
                if (newPassword != confirmPassword) {
                  inlineError = "New Password and Confirm Password do not match."
                  return@VybePrimaryButton
                }
                isBusy = true
                inlineError = null
                scope.launch {
                  try {
                    val (ok, msg) = viewModel.submitRecoveredNewPassword(newPassword, confirmPassword)
                    if (!ok) {
                      inlineError = msg
                    }
                  } finally {
                    isBusy = false
                  }
                }
              },
              modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(10.dp))

            VybeSecondaryButton(
              text = "Back to Login",
              enabled = !isSubmitting,
              testTag = "recovery_return_to_login_button",
              onClick = { viewModel.exitPasswordRecoveryToLogin() },
              modifier = Modifier.fillMaxWidth()
            )
          }
        }

        is PasswordRecoveryState.Error,
        is PasswordRecoveryState.NeedsTokenVerification,
        PasswordRecoveryState.Inactive -> {
          val errorText = (state as? PasswordRecoveryState.Error)?.message ?: inlineError

          Text(
            text = "Open the password recovery link sent to your email to verify your session automatically. If your email includes a recovery token, you can also verify it below.",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary
          )

          if (!errorText.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Surface(
              color = palette.danger.copy(alpha = 0.14f),
              border = BorderStroke(1.dp, palette.danger.copy(alpha = 0.35f)),
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = palette.danger)
                Spacer(Modifier.width(8.dp))
                Text(
                  text = errorText,
                  color = palette.danger,
                  style = MaterialTheme.typography.bodySmall
                )
              }
            }
          }

          if (!resendSuccessMsg.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Surface(
              color = palette.success.copy(alpha = 0.14f),
              border = BorderStroke(1.dp, palette.success.copy(alpha = 0.35f)),
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = resendSuccessMsg!!,
                color = palette.textPrimary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp)
              )
            }
          }

          Spacer(Modifier.height(16.dp))

          VybeSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
              value = emailInput,
              onValueChange = {
                emailInput = it
                inlineError = null
              },
              label = { Text("Registered Email") },
              singleLine = true,
              keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              colors = fieldColors,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("recovery_email_input")
            )

            Spacer(Modifier.height(12.dp))

            VybeSecondaryButton(
              text = "Send Fresh Recovery Link",
              enabled = !isBusy,
              testTag = "recovery_send_link_button",
              onClick = {
                val trimmed = emailInput.trim()
                if (trimmed.isBlank() || !trimmed.contains("@")) {
                  inlineError = "Please enter your registered email address."
                  return@VybeSecondaryButton
                }
                isBusy = true
                inlineError = null
                resendSuccessMsg = null
                scope.launch {
                  val (ok, msg) = viewModel.sendPasswordReset(trimmed)
                  isBusy = false
                  if (ok) resendSuccessMsg = msg else inlineError = msg
                }
              },
              modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = palette.border)
            Spacer(Modifier.height(16.dp))

            Text(
              text = "Have a recovery token from your email?",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.SemiBold,
              color = palette.textPrimary
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
              value = recoveryTokenInput,
              onValueChange = {
                recoveryTokenInput = it
                inlineError = null
              },
              label = { Text("Email Recovery Token") },
              placeholder = { Text("Paste token from email if provided", color = palette.textMuted) },
              singleLine = true,
              shape = RoundedCornerShape(VybeTokens.Radius.md),
              colors = fieldColors,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("recovery_token_input")
            )

            Spacer(Modifier.height(12.dp))

            VybePrimaryButton(
              text = "Verify Recovery Token",
              enabled = !isBusy && recoveryTokenInput.isNotBlank(),
              testTag = "recovery_verify_token_button",
              onClick = {
                isBusy = true
                inlineError = null
                scope.launch {
                  val (ok, msg) = viewModel.verifyRecoveryTokenManually(emailInput, recoveryTokenInput)
                  isBusy = false
                  if (!ok) inlineError = msg
                }
              },
              modifier = Modifier.fillMaxWidth()
            )
          }

          Spacer(Modifier.height(14.dp))

          TextButton(
            onClick = { viewModel.exitPasswordRecoveryToLogin() },
            enabled = !isBusy,
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("recovery_return_to_login_button")
          ) {
            Text("Back to Login", fontWeight = FontWeight.SemiBold, color = palette.accent)
          }
        }

        is PasswordRecoveryState.Completed -> {
          Spacer(Modifier.height(24.dp))
          VybeSurfaceCard(
            borderColor = palette.success.copy(alpha = 0.4f),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = palette.success
              )
              Spacer(Modifier.width(12.dp))
              Text(
                text = state.message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = palette.textPrimary
              )
            }
          }
          Spacer(Modifier.height(20.dp))
          VybePrimaryButton(
            text = "Login with New Password",
            testTag = "recovery_completed_login_button",
            onClick = { viewModel.exitPasswordRecoveryToLogin() },
            modifier = Modifier.fillMaxWidth()
          )
        }
      }
    }
  }
}
