package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.repository.PasswordRecoveryState
import com.example.ui.VybeViewModel
import kotlinx.coroutines.launch

/**
 * Dedicated VYBE Password Recovery Screen.
 *
 * Triggered when the user opens `vybe://reset-password` from the Supabase recovery email
 * (or enters a Supabase recovery token manually).
 *
 * Enforces:
 * 1. Validates recovery session securely against Supabase Auth before allowing password entry.
 * 2. Never invents a fake 6-digit OTP when Supabase sent a recovery link.
 * 3. Displays New Password and Confirm Password with >= 8 char & match validation.
 * 4. Updates password via Supabase Auth, terminates recovery session, and returns cleanly to Login.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordRecoveryScreen(viewModel: VybeViewModel) {
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

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Reset Password", fontWeight = FontWeight.Bold) },
        navigationIcon = {
          IconButton(
            onClick = { viewModel.exitPasswordRecoveryToLogin() },
            modifier = Modifier.testTag("recovery_back_to_login")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Login")
          }
        }
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 24.dp, vertical = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        modifier = Modifier.size(64.dp)
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            Icons.Default.LockReset,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(34.dp)
          )
        }
      }

      Spacer(Modifier.height(16.dp))

      Text(
        text = "VYBE Account Recovery",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
      )

      Spacer(Modifier.height(8.dp))

      when (val state = recoveryState) {
        PasswordRecoveryState.ValidatingLink -> {
          Spacer(Modifier.height(32.dp))
          CircularProgressIndicator()
          Spacer(Modifier.height(16.dp))
          Text(
            text = "Validating your recovery link securely with VYBE Auth...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(Modifier.height(24.dp))
          TextButton(
            onClick = { viewModel.exitPasswordRecoveryToLogin() },
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("recovery_cancel_validating_login_button")
          ) {
            Text("Cancel & Back to Login")
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
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(12.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
              )
              Spacer(Modifier.width(10.dp))
              Text(
                text = if (verifiedEmail.isNotBlank()) "Recovery session verified for $verifiedEmail"
                else "Recovery session verified. Choose a strong new password.",
                style = MaterialTheme.typography.bodySmall
              )
            }
          }

          Spacer(Modifier.height(20.dp))

          OutlinedTextField(
            value = newPassword,
            onValueChange = {
              newPassword = it
              inlineError = null
            },
            label = { Text("New Password") },
            placeholder = { Text("At least 8 characters") },
            singleLine = true,
            visualTransformation = if (showNewPass) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
              IconButton(onClick = { showNewPass = !showNewPass }) {
                Icon(
                  imageVector = if (showNewPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                  contentDescription = if (showNewPass) "Hide password" else "Show password"
                )
              }
            },
            shape = RoundedCornerShape(14.dp),
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
            placeholder = { Text("Re-enter new password") },
            singleLine = true,
            visualTransformation = if (showConfirmPass) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
              IconButton(onClick = { showConfirmPass = !showConfirmPass }) {
                Icon(
                  imageVector = if (showConfirmPass) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                  contentDescription = if (showConfirmPass) "Hide password" else "Show password"
                )
              }
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("recovery_confirm_password_input")
          )

          if (!inlineError.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Surface(
              color = MaterialTheme.colorScheme.errorContainer,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  Icons.Default.ErrorOutline,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(8.dp))
                Text(
                  text = inlineError!!,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  style = MaterialTheme.typography.bodySmall
                )
              }
            }
          }

          Spacer(Modifier.height(22.dp))

          Button(
            onClick = {
              if (isSubmitting) return@Button
              if (newPassword.length < 8) {
                inlineError = "New password must be at least 8 characters long."
                return@Button
              }
              if (newPassword != confirmPassword) {
                inlineError = "New Password and Confirm Password do not match."
                return@Button
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
            enabled = !isSubmitting,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 52.dp)
              .testTag("recovery_submit_password_button")
          ) {
            if (isSubmitting) {
              CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary
              )
              Spacer(Modifier.width(10.dp))
              Text("Updating Password...")
            } else {
              Text("Update Password", fontWeight = FontWeight.Bold)
            }
          }

          Spacer(Modifier.height(12.dp))

          OutlinedButton(
            onClick = { viewModel.exitPasswordRecoveryToLogin() },
            enabled = !isSubmitting,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .testTag("recovery_return_to_login_button")
          ) {
            Text("Back to Login", fontWeight = FontWeight.SemiBold)
          }
        }

        is PasswordRecoveryState.Error,
        is PasswordRecoveryState.NeedsTokenVerification,
        PasswordRecoveryState.Inactive -> {
          val errorText = (state as? PasswordRecoveryState.Error)?.message ?: inlineError

          Text(
            text = "Open the password recovery link sent to your email to verify your session automatically. If your email includes a recovery token, you can also verify it below.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          if (!errorText.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Surface(
              color = MaterialTheme.colorScheme.errorContainer,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text(
                  text = errorText,
                  color = MaterialTheme.colorScheme.onErrorContainer,
                  style = MaterialTheme.typography.bodySmall
                )
              }
            }
          }

          if (!resendSuccessMsg.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Surface(
              color = MaterialTheme.colorScheme.secondaryContainer,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = resendSuccessMsg!!,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp)
              )
            }
          }

          Spacer(Modifier.height(18.dp))

          OutlinedTextField(
            value = emailInput,
            onValueChange = {
              emailInput = it
              inlineError = null
            },
            label = { Text("Registered Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("recovery_email_input")
          )

          Spacer(Modifier.height(12.dp))

          OutlinedButton(
            onClick = {
              val trimmed = emailInput.trim()
              if (trimmed.isBlank() || !trimmed.contains("@")) {
                inlineError = "Please enter your registered email address."
                return@OutlinedButton
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
            enabled = !isBusy,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .testTag("recovery_send_link_button")
          ) {
            Text("Send Fresh Recovery Link")
          }

          Spacer(Modifier.height(20.dp))
          HorizontalDivider()
          Spacer(Modifier.height(16.dp))

          Text(
            text = "Have a recovery token from your email?",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Start)
          )
          Spacer(Modifier.height(8.dp))

          OutlinedTextField(
            value = recoveryTokenInput,
            onValueChange = {
              recoveryTokenInput = it
              inlineError = null
            },
            label = { Text("Email Recovery Token") },
            placeholder = { Text("Paste token from email if provided") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("recovery_token_input")
          )

          Spacer(Modifier.height(12.dp))

          Button(
            onClick = {
              isBusy = true
              inlineError = null
              scope.launch {
                val (ok, msg) = viewModel.verifyRecoveryTokenManually(emailInput, recoveryTokenInput)
                isBusy = false
                if (!ok) inlineError = msg
              }
            },
            enabled = !isBusy && recoveryTokenInput.isNotBlank(),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .testTag("recovery_verify_token_button")
          ) {
            Text("Verify Recovery Token")
          }

          Spacer(Modifier.height(16.dp))

          TextButton(
            onClick = { viewModel.exitPasswordRecoveryToLogin() },
            enabled = !isBusy,
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("recovery_return_to_login_button")
          ) {
            Text("Back to Login", fontWeight = FontWeight.SemiBold)
          }
        }

        is PasswordRecoveryState.Completed -> {
          Spacer(Modifier.height(24.dp))
          Surface(
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(16.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
              )
              Spacer(Modifier.width(12.dp))
              Text(
                text = state.message,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
              )
            }
          }
          Spacer(Modifier.height(20.dp))
          Button(
            onClick = { viewModel.exitPasswordRecoveryToLogin() },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 52.dp)
              .testTag("recovery_completed_login_button")
          ) {
            Text("Login with New Password", fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}
