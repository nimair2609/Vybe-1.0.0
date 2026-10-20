package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.VybeViewModel
import com.example.ui.components.VybeGradientButton
import com.example.ui.theme.*

@Composable
fun AuthScreen(viewModel: VybeViewModel) {
  var isRegisterMode by remember { mutableStateOf(false) }

  // Form fields
  var username by remember { mutableStateOf("") }
  var emailOrPhone by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var dateOfBirth by remember { mutableStateOf("2003-08-14") } // Default age ~23
  var otpCode by remember { mutableStateOf("") }
  var showOtpStep by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var statusMessage by remember { mutableStateOf<String?>(null) }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .padding(horizontal = 24.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Spacer(modifier = Modifier.height(40.dp))

      // Official Logo & Header
      Box(
        modifier = Modifier
          .size(76.dp)
          .clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
      ) {
        androidx.compose.foundation.Image(
          painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.vybe_app_icon_clean),
          contentDescription = "VYBE Logo",
          modifier = Modifier.fillMaxSize()
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      Text(
        text = "vybe",
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onBackground
      )

      Spacer(modifier = Modifier.height(2.dp))

      Text(
        text = "CONNECT • SHARE • BE YOU",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        color = VybeBrandCyan
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = if (isRegisterMode) "Create your verified VYBE profile" else "Welcome back to your vibe",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      // Age Policy Banner
      Spacer(modifier = Modifier.height(18.dp))
      Surface(
        color = VybeViolet.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, VybeViolet.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = "Age policy",
            tint = VybeViolet,
            modifier = Modifier.size(24.dp)
          )
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = "16+ Community & Safety Gate",
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "VYBE strictly requires members to be aged 16 and above. Teen accounts (16-17) receive enhanced privacy protection.",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              lineHeight = 15.sp
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      if (errorMessage != null) {
        Surface(
          color = VybeDanger.copy(alpha = 0.15f),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
          Text(
            text = errorMessage ?: "",
            color = VybeDanger,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(10.dp)
          )
        }
      }

      if (statusMessage != null) {
        Surface(
          color = VybeOnlineGreen.copy(alpha = 0.15f),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
          Text(
            text = statusMessage ?: "",
            color = VybeOnlineGreen,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(10.dp)
          )
        }
      }

      if (!showOtpStep) {
        if (isRegisterMode) {
          OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Unique Username") },
            leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("auth_username_input")
          )

          Spacer(modifier = Modifier.height(12.dp))

          OutlinedTextField(
            value = dateOfBirth,
            onValueChange = { dateOfBirth = it },
            label = { Text("Date of Birth (YYYY-MM-DD)") },
            leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
            supportingText = { Text("Required to verify 16+ eligibility") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("auth_dob_input")
          )

          Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedTextField(
          value = emailOrPhone,
          onValueChange = { emailOrPhone = it },
          label = { Text(if (isRegisterMode) "Email or Mobile (+91)" else "Username, Email or Mobile") },
          leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("auth_identifier_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
          value = password,
          onValueChange = { password = it },
          label = { Text("Password") },
          leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
          visualTransformation = PasswordVisualTransformation(),
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("auth_password_input")
        )

        Spacer(modifier = Modifier.height(24.dp))

        VybeGradientButton(
          text = if (isRegisterMode) "Verify & Create Account" else "Log In to VYBE",
          onClick = {
            errorMessage = null
            if (isRegisterMode) {
              if (username.isBlank() || emailOrPhone.isBlank()) {
                errorMessage = "Please enter all required registration details."
              } else {
                showOtpStep = true
                statusMessage = "6-digit OTP code sent to $emailOrPhone (Enter 123456 to verify)"
              }
            } else {
              val success = viewModel.login(emailOrPhone, password)
              if (!success) errorMessage = "Invalid credentials."
            }
          },
          modifier = Modifier.testTag("auth_submit_button")
        )
      } else {
        // OTP Step
        Text(
          text = "Enter 6-Digit OTP",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = "We sent a temporary verification code to $emailOrPhone",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
          value = otpCode,
          onValueChange = { otpCode = it },
          label = { Text("OTP Code (e.g. 123456)") },
          leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("auth_otp_input")
        )

        Spacer(modifier = Modifier.height(20.dp))

        VybeGradientButton(
          text = "Confirm & Launch Profile",
          onClick = {
            if (otpCode == "123456" || otpCode.length >= 4) {
              val (ok, msg) = viewModel.register(username, emailOrPhone, dateOfBirth)
              if (!ok) {
                errorMessage = msg
                showOtpStep = false
              }
            } else {
              errorMessage = "Incorrect OTP code. Try entering 123456."
            }
          },
          modifier = Modifier.testTag("auth_otp_confirm_button")
        )

        Spacer(modifier = Modifier.height(10.dp))

        TextButton(onClick = { showOtpStep = false }) {
          Text("Back to Edit Details")
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      TextButton(
        onClick = {
          isRegisterMode = !isRegisterMode
          errorMessage = null
          statusMessage = null
          showOtpStep = false
        },
        modifier = Modifier.testTag("auth_mode_toggle_button")
      ) {
        Text(
          text = if (isRegisterMode) "Already have an account? Log In" else "New to VYBE? Create an Account",
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.SemiBold
        )
      }

      Spacer(modifier = Modifier.height(40.dp))
    }
  }
}
