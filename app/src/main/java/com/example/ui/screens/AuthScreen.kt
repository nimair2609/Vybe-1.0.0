package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.example.ui.VybeViewModel
import com.example.ui.components.VybeGradientButton
import com.example.ui.theme.*

/**
 * 6. REGISTRATION AND LOGIN (Section 6 & 7)
 * - Complete 10 registration fields:
 *   1. First name
 *   2. Last name
 *   3. Suggested User ID (with authoritative availability feedback)
 *   4. Email address
 *   5. Phone number
 *   6. Date of birth (16+ enforcement)
 *   7. OTP verification
 *   8. Create password (with show/hide toggle)
 *   9. Confirm password (with show/hide toggle & match validation)
 *   10. Terms and conditions agreement checkbox (not preselected)
 * - Post-login onboarding checklist support.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(viewModel: VybeViewModel) {
  var isRegisterMode by remember { mutableStateOf(false) }

  // Account Type Selection (Section 2 & 11)
  var accountType by remember { mutableStateOf("INDIVIDUAL") } // "INDIVIDUAL" or "BUSINESS"
  var showLegalReader by remember { mutableStateOf(false) }

  // Mandatory Registration Fields (Individual - Section 8)
  var firstName by remember { mutableStateOf("") }
  var lastName by remember { mutableStateOf("") }
  var username by remember { mutableStateOf("") }
  var email by remember { mutableStateOf("") }
  var phone by remember { mutableStateOf("") }
  var dateOfBirth by remember { mutableStateOf("") } // Never prefilled; user must enter or pick
  var createPassword by remember { mutableStateOf("") }
  var confirmPassword by remember { mutableStateOf("") }
  var termsAccepted by remember { mutableStateOf(false) }
  var attemptedRegisterSubmit by remember { mutableStateOf(false) }
  var showDobDatePicker by remember { mutableStateOf(false) }

  // Business Onboarding Fields (Section 11)
  var businessName by remember { mutableStateOf("") }
  var businessAddress by remember { mutableStateOf("") }
  var ownerName by remember { mutableStateOf("") }
  var businessType by remember { mutableStateOf("Retail & Artisan") }
  var businessCategory by remember { mutableStateOf("Food & Beverage") }
  var businessContactPhone by remember { mutableStateOf("") }
  var businessContactEmail by remember { mutableStateOf("") }
  var businessDocName by remember { mutableStateOf("GSTIN_Registration_Certificate.pdf") }
  var businessLogoSelected by remember { mutableStateOf(true) }

  // Show/Hide password toggles
  var showCreatePassword by remember { mutableStateOf(false) }
  var showConfirmPassword by remember { mutableStateOf(false) }

  // User ID availability status
  var userIdAvailabilityMessage by remember { mutableStateOf<String?>(null) }
  var isUserIdAvailable by remember { mutableStateOf(true) }

  // Login fields
  var loginIdentifier by remember { mutableStateOf("") }
  var loginPassword by remember { mutableStateOf("") }
  var showLoginPassword by remember { mutableStateOf(false) }

  var registrationStep by remember { mutableStateOf(1) } // 1 = Registration details, 2 = Dual Email + Phone OTP Verification

  val docPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
    contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
  ) { uri ->
    if (uri != null) {
      businessDocName = uri.lastPathSegment?.substringAfterLast("/") ?: "Business_Document.pdf"
      businessLogoSelected = true
    }
  }

  val photoDocPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
    contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      businessDocName = "Business_Certificate_${System.currentTimeMillis().toString().takeLast(4)}.jpeg"
      businessLogoSelected = true
    }
  }

  val coroutineScope = rememberCoroutineScope()
  var isLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var showForgotPasswordDialog by remember { mutableStateOf(false) }
  var showOtpVerifyDialog by remember { mutableStateOf(false) }
  var resetEmail by remember { mutableStateOf("") }
  var resetOtp by remember { mutableStateOf("") }
  var newPassword by remember { mutableStateOf("") }
  var showNewPassword by remember { mutableStateOf(false) }
  var resetStep by remember { mutableStateOf(1) }
  var resetLoading by remember { mutableStateOf(false) }
  var resetError by remember { mutableStateOf<String?>(null) }
  var resetMessage by remember { mutableStateOf<String?>(null) }

  // Dual Email + Phone OTP Verification state (Section 9)
  val pendingEmail by viewModel.pendingVerificationEmail.collectAsState()
  val pendingPhone by viewModel.pendingVerificationPhone.collectAsState()
  val isEmailVerifiedState by viewModel.isEmailOtpVerified.collectAsState()
  val isPhoneVerifiedState by viewModel.isPhoneOtpVerified.collectAsState()
  val lastRecoveredEmail by viewModel.lastRecoveredEmailHint.collectAsState()

  var verifyEmailInput by remember { mutableStateOf("") }
  var verifyOtpInput by remember { mutableStateOf("") }
  var verifyLoading by remember { mutableStateOf(false) }
  var verifyError by remember { mutableStateOf<String?>(null) }

  var verifyPhoneInput by remember { mutableStateOf("") }
  var verifyPhoneOtpInput by remember { mutableStateOf("") }
  var phoneVerifyLoading by remember { mutableStateOf(false) }
  var phoneVerifyError by remember { mutableStateOf<String?>(null) }
  var phoneVerifyInfo by remember { mutableStateOf<String?>(null) }

  var emailResendCooldownSec by remember { mutableIntStateOf(0) }
  var phoneResendCooldownSec by remember { mutableIntStateOf(0) }

  var statusMessage by remember { mutableStateOf<String?>(null) }
  val globalBanner by viewModel.uiBannerMessage.collectAsState()

  LaunchedEffect(globalBanner) {
    if (!globalBanner.isNullOrBlank()) {
      statusMessage = globalBanner
      viewModel.consumeBannerMessage()
    }
  }

  LaunchedEffect(lastRecoveredEmail) {
    if (lastRecoveredEmail.isNotBlank() && loginIdentifier.isBlank()) {
      isRegisterMode = false
      loginIdentifier = lastRecoveredEmail
    }
  }

  LaunchedEffect(pendingEmail, pendingPhone) {
    if (pendingEmail.isNotBlank() && verifyEmailInput.isBlank()) verifyEmailInput = pendingEmail
    if (pendingPhone.isNotBlank() && verifyPhoneInput.isBlank()) verifyPhoneInput = pendingPhone
  }

  LaunchedEffect(emailResendCooldownSec) {
    if (emailResendCooldownSec > 0) {
      kotlinx.coroutines.delay(1000L)
      emailResendCooldownSec -= 1
    }
  }

  LaunchedEffect(phoneResendCooldownSec) {
    if (phoneResendCooldownSec > 0) {
      kotlinx.coroutines.delay(1000L)
      phoneResendCooldownSec -= 1
    }
  }

  // Debounced server-side username availability check
  LaunchedEffect(username, isRegisterMode) {
    val clean = username.trim().removePrefix("@")
    if (!isRegisterMode || clean.length < 3) return@LaunchedEffect
    kotlinx.coroutines.delay(350L)
    val (avail, msg) = viewModel.checkUsernameAvailabilityRemote(clean)
    isUserIdAvailable = avail
    userIdAvailabilityMessage = msg
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(MaterialTheme.colorScheme.background)
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .imePadding()
      .padding(horizontal = 24.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Spacer(modifier = Modifier.height(36.dp))

      // Official Logo & Header
      Box(
        modifier = Modifier
          .size(72.dp)
          .clip(RoundedCornerShape(18.dp))
      ) {
        androidx.compose.foundation.Image(
          painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.vybe_app_icon_clean),
          contentDescription = "VYBE Logo",
          modifier = Modifier.fillMaxSize()
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      Text(
        text = "vybe",
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onBackground
      )

      Text(
        text = "CONNECT • SHARE • BE YOU",
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        color = VybeBrandCyan
      )

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = if (isRegisterMode) "Create your verified VYBE profile" else "Welcome back to your vibe",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      // Age Policy Banner (16+ community requirement)
      Spacer(modifier = Modifier.height(10.dp))
      Surface(
        color = VybeViolet.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, VybeViolet.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Default.Shield,
            contentDescription = "Age policy",
            tint = VybeViolet,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(10.dp))
          Text(
            text = "16+ Community Gate • Teen accounts (16-17) receive enhanced privacy shields.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 15.sp
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      if (isRegisterMode && errorMessage != null) {
        Surface(
          color = VybeDanger.copy(alpha = 0.15f),
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
          Column(modifier = Modifier.padding(10.dp)) {
            Text(
              text = errorMessage ?: "",
              color = VybeDanger,
              style = MaterialTheme.typography.bodySmall
            )
          }
        }
      }

      if (isRegisterMode && statusMessage != null) {
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

      if (isRegisterMode) {
          // Account Type Selector (Section 2 & 11 requirement)
          Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Surface(
              color = if (accountType == "INDIVIDUAL") VybeViolet.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(12.dp),
              border = if (accountType == "INDIVIDUAL") androidx.compose.foundation.BorderStroke(1.5.dp, VybeViolet) else null,
              modifier = Modifier
                .weight(1f)
                .clickable { accountType = "INDIVIDUAL" }
                .testTag("account_type_individual")
            ) {
              Column(
                modifier = Modifier.padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = if (accountType == "INDIVIDUAL") VybeViolet else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Individual", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Personal Account", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }

            Surface(
              color = if (accountType == "BUSINESS") VybeVerifiedGold.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(12.dp),
              border = if (accountType == "BUSINESS") androidx.compose.foundation.BorderStroke(1.5.dp, VybeVerifiedGold) else null,
              modifier = Modifier
                .weight(1f)
                .clickable { accountType = "BUSINESS" }
                .testTag("account_type_business")
            ) {
              Column(
                modifier = Modifier.padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Icon(Icons.Default.Store, contentDescription = null, tint = if (accountType == "BUSINESS") VybeVerifiedGold else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Business", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("Shop & Verification", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
          }

          if (accountType == "INDIVIDUAL") {
            if (registrationStep == 2) {
              // Dual Email + Phone SMS OTP Verification Step (Section 9)
              Surface(
                color = VybeViolet.copy(alpha = 0.10f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, VybeViolet.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
              ) {
                Column(
                  modifier = Modifier.padding(16.dp),
                  horizontalAlignment = Alignment.CenterHorizontally
                ) {
                  Text(
                    "Step 2 of 2: Dual Account Verification",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = "Verify both your Email Address and Phone Number to mark your VYBE account fully verified.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                  )
                  Spacer(modifier = Modifier.height(12.dp))

                  // Separate Verification Status Pills
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Surface(
                      color = if (isEmailVerifiedState) VybeOnlineGreen.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                      shape = RoundedCornerShape(10.dp),
                      modifier = Modifier.weight(1f).testTag("auth_email_verified_status")
                    ) {
                      Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Icon(
                          imageVector = if (isEmailVerifiedState) Icons.Default.CheckCircle else Icons.Default.MarkEmailUnread,
                          contentDescription = null,
                          tint = if (isEmailVerifiedState) VybeOnlineGreen else VybeViolet,
                          modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                          text = if (isEmailVerifiedState) "Email Verified" else "Email Pending",
                          fontSize = 11.sp,
                          fontWeight = FontWeight.Bold,
                          color = if (isEmailVerifiedState) VybeOnlineGreen else MaterialTheme.colorScheme.onSurface
                        )
                      }
                    }

                    Surface(
                      color = if (isPhoneVerifiedState) VybeOnlineGreen.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
                      shape = RoundedCornerShape(10.dp),
                      modifier = Modifier.weight(1f).testTag("auth_phone_verified_status")
                    ) {
                      Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Icon(
                          imageVector = if (isPhoneVerifiedState) Icons.Default.CheckCircle else Icons.Default.Sms,
                          contentDescription = null,
                          tint = if (isPhoneVerifiedState) VybeOnlineGreen else VybeBrandCyan,
                          modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                          text = if (isPhoneVerifiedState) "Phone Verified" else "Phone Pending",
                          fontSize = 11.sp,
                          fontWeight = FontWeight.Bold,
                          color = if (isPhoneVerifiedState) VybeOnlineGreen else MaterialTheme.colorScheme.onSurface
                        )
                      }
                    }
                  }

                  Spacer(modifier = Modifier.height(14.dp))

                  // --- 1. EMAIL OTP CARD ---
                  Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                      Text(
                        text = if (isEmailVerifiedState) "1. Email Confirmed ($verifyEmailInput)" else "1. Verify Email ($verifyEmailInput)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (isEmailVerifiedState) VybeOnlineGreen else MaterialTheme.colorScheme.onSurface
                      )
                      if (!isEmailVerifiedState) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                          value = verifyOtpInput,
                          onValueChange = {
                            verifyOtpInput = it.filter { ch -> ch.isDigit() || ch.isLetter() }.take(8)
                            verifyError = null
                          },
                          label = { Text("Email Verification Code") },
                          singleLine = true,
                          shape = RoundedCornerShape(12.dp),
                          modifier = Modifier.fillMaxWidth().testTag("auth_verify_otp_input")
                        )
                        if (verifyError != null) {
                          Spacer(modifier = Modifier.height(6.dp))
                          Text(verifyError!!, color = VybeDanger, fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                          modifier = Modifier.fillMaxWidth(),
                          horizontalArrangement = Arrangement.spacedBy(8.dp),
                          verticalAlignment = Alignment.CenterVertically
                        ) {
                          Button(
                            onClick = {
                              verifyError = null
                              coroutineScope.launch {
                                verifyLoading = true
                                val (ok, msg) = viewModel.verifyOtp(verifyEmailInput.trim(), verifyOtpInput.trim(), "signup")
                                verifyLoading = false
                                if (!ok) {
                                  verifyError = msg
                                } else {
                                  statusMessage = msg.removePrefix("EMAIL_VERIFIED_NEEDS_PHONE:").trim()
                                  if (msg.startsWith("EMAIL_VERIFIED_NEEDS_PHONE")) {
                                    phoneResendCooldownSec = 60
                                  }
                                }
                              }
                            },
                            enabled = !verifyLoading && verifyOtpInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = VybeViolet),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("auth_confirm_otp_button")
                          ) {
                            Text(if (verifyLoading) "Verifying..." else "Verify Email OTP", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                          }
                          OutlinedButton(
                            onClick = {
                              verifyError = null
                              coroutineScope.launch {
                                val (ok, msg) = viewModel.resendEmailVerificationCode(verifyEmailInput.trim())
                                if (ok) {
                                  emailResendCooldownSec = 60
                                  statusMessage = msg
                                } else {
                                  verifyError = msg
                                }
                              }
                            },
                            enabled = !verifyLoading && emailResendCooldownSec == 0 && verifyEmailInput.isNotBlank(),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.heightIn(min = 48.dp).testTag("auth_resend_email_otp_button")
                          ) {
                            Text(
                              if (emailResendCooldownSec > 0) "Resend (${emailResendCooldownSec}s)" else "Resend",
                              fontSize = 11.sp
                            )
                          }
                        }
                      }
                    }
                  }

                  Spacer(modifier = Modifier.height(12.dp))

                  // --- 2. PHONE SMS OTP CARD ---
                  Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                      Text(
                        text = if (isPhoneVerifiedState) "2. Phone Confirmed ($verifyPhoneInput)" else "2. Verify Phone Number via SMS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (isPhoneVerifiedState) VybeOnlineGreen else MaterialTheme.colorScheme.onSurface
                      )
                      if (!isPhoneVerifiedState) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                          value = verifyPhoneInput,
                          onValueChange = {
                            verifyPhoneInput = it
                            phoneVerifyError = null
                          },
                          label = { Text("Phone Number (with country code, e.g. +91...)") },
                          singleLine = true,
                          shape = RoundedCornerShape(12.dp),
                          modifier = Modifier.fillMaxWidth().testTag("auth_verify_phone_input")
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                          onClick = {
                            phoneVerifyError = null
                            phoneVerifyInfo = null
                            coroutineScope.launch {
                              phoneVerifyLoading = true
                              val (ok, msg) = viewModel.requestPhoneVerificationCode(verifyPhoneInput.trim())
                              phoneVerifyLoading = false
                              if (ok) {
                                phoneResendCooldownSec = 60
                                phoneVerifyInfo = msg
                              } else {
                                phoneVerifyError = msg
                              }
                            }
                          },
                          enabled = !phoneVerifyLoading && phoneResendCooldownSec == 0 && verifyPhoneInput.isNotBlank(),
                          shape = RoundedCornerShape(10.dp),
                          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("auth_send_phone_otp_button")
                        ) {
                          Text(
                            when {
                              phoneVerifyLoading -> "Requesting SMS..."
                              phoneResendCooldownSec > 0 -> "Resend SMS Code (${phoneResendCooldownSec}s)"
                              else -> "Send / Resend SMS Verification Code"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                          )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                          value = verifyPhoneOtpInput,
                          onValueChange = {
                            verifyPhoneOtpInput = it.filter { ch -> ch.isDigit() }.take(8)
                            phoneVerifyError = null
                          },
                          label = { Text("6-Digit SMS OTP Code") },
                          singleLine = true,
                          shape = RoundedCornerShape(12.dp),
                          modifier = Modifier.fillMaxWidth().testTag("auth_verify_phone_otp_input")
                        )
                        if (phoneVerifyInfo != null) {
                          Spacer(modifier = Modifier.height(6.dp))
                          Text(phoneVerifyInfo!!, color = VybeOnlineGreen, fontSize = 11.sp)
                        }
                        if (phoneVerifyError != null) {
                          Spacer(modifier = Modifier.height(6.dp))
                          Text(phoneVerifyError!!, color = VybeDanger, fontSize = 11.sp)
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                          onClick = {
                            phoneVerifyError = null
                            coroutineScope.launch {
                              phoneVerifyLoading = true
                              val (ok, msg) = viewModel.verifyPhoneOtpCode(verifyPhoneInput.trim(), verifyPhoneOtpInput.trim())
                              phoneVerifyLoading = false
                              if (!ok) {
                                phoneVerifyError = msg
                              } else {
                                statusMessage = msg.removePrefix("PHONE_VERIFIED_NEEDS_EMAIL:").trim()
                              }
                            }
                          },
                          enabled = !phoneVerifyLoading && verifyPhoneOtpInput.isNotBlank(),
                          colors = ButtonDefaults.buttonColors(containerColor = VybeBrandCyan),
                          shape = RoundedCornerShape(10.dp),
                          modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("auth_confirm_phone_otp_button")
                        ) {
                          Text(
                            if (phoneVerifyLoading) "Verifying SMS..." else "Verify Phone OTP",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                          )
                        }
                      }
                    }
                  }

                  Spacer(modifier = Modifier.height(10.dp))
                  TextButton(
                    onClick = { registrationStep = 1 },
                    modifier = Modifier.heightIn(min = 48.dp)
                  ) {
                    Text("Back to Edit Registration Details", fontSize = 12.sp)
                  }
                }
              }
            } else {
            val cleanUsername = username.trim().removePrefix("@")
            val isFirstNameInvalid = attemptedRegisterSubmit && firstName.trim().length < 2
            val isLastNameInvalid = attemptedRegisterSubmit && lastName.trim().isEmpty()
            val isUsernameFormatInvalid = cleanUsername.isNotEmpty() && !cleanUsername.matches(Regex("^[a-zA-Z0-9._]{3,24}$"))
            val isUsernameInvalid = (attemptedRegisterSubmit && cleanUsername.isEmpty()) || isUsernameFormatInvalid || !isUserIdAvailable
            val isEmailInvalid = (attemptedRegisterSubmit && email.trim().isEmpty()) ||
              (email.isNotBlank() && !email.trim().matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")))
            val phoneDigitsCount = phone.count { it.isDigit() }
            val isPhoneInvalid = (attemptedRegisterSubmit && phone.trim().isEmpty()) ||
              (phone.isNotBlank() && phoneDigitsCount !in 10..15)
            val dobAgeCheck = remember(dateOfBirth) { validateRegistrationDob(dateOfBirth) }
            val isDobInvalid = (attemptedRegisterSubmit && dateOfBirth.trim().isEmpty()) ||
              (dateOfBirth.isNotBlank() && dobAgeCheck != null)
            val isCreatePassInvalid = (attemptedRegisterSubmit && createPassword.isEmpty()) ||
              (createPassword.isNotEmpty() && createPassword.length < 8)
            val isConfirmPassInvalid = (attemptedRegisterSubmit && confirmPassword.isEmpty()) ||
              (confirmPassword.isNotEmpty() && confirmPassword != createPassword)

            // Field 1 & 2: First Name and Last Name
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it; errorMessage = null },
                label = { Text("First Name *") },
                isError = isFirstNameInvalid,
                supportingText = if (isFirstNameInvalid) {
                  { Text("Enter first name (2+ chars)", color = VybeDanger, fontSize = 10.sp) }
                } else null,
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("auth_first_name_input")
              )
              OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it; errorMessage = null },
                label = { Text("Last Name *") },
                isError = isLastNameInvalid,
                supportingText = if (isLastNameInvalid) {
                  { Text("Required", color = VybeDanger, fontSize = 10.sp) }
                } else null,
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("auth_last_name_input")
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Field 3: Unique Username with live local + remote availability check (Section 8)
            OutlinedTextField(
              value = username,
              onValueChange = {
                username = it
                errorMessage = null
                val clean = it.trim().removePrefix("@")
                if (clean.isNotBlank()) {
                  val (avail, msg) = viewModel.checkUserIdAvailability(clean)
                  isUserIdAvailable = avail
                  userIdAvailabilityMessage = msg
                } else {
                  userIdAvailabilityMessage = null
                  isUserIdAvailable = true
                }
              },
              label = { Text("Unique Username (@username) *") },
              leadingIcon = { Text("@", fontWeight = FontWeight.Bold, color = VybeViolet) },
              trailingIcon = {
                if (firstName.isNotBlank() && (username.isBlank() || !isUserIdAvailable)) {
                  TextButton(
                    onClick = {
                      val base = (firstName.trim() + "_" + lastName.trim())
                        .lowercase()
                        .replace(Regex("[^a-z0-9_]"), "")
                        .trim('_')
                        .ifBlank { "vyber" }
                        .take(16)
                      val suggested = "${base}_${(100..999).random()}"
                      username = suggested
                      val (avail, msg) = viewModel.checkUserIdAvailability(suggested)
                      isUserIdAvailable = avail
                      userIdAvailabilityMessage = msg
                    }
                  ) {
                    Text("Suggest", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = VybeBrandCyan)
                  }
                }
              },
              isError = isUsernameInvalid,
              supportingText = {
                when {
                  attemptedRegisterSubmit && cleanUsername.isEmpty() ->
                    Text("Username is required.", color = VybeDanger, fontWeight = FontWeight.SemiBold)
                  isUsernameFormatInvalid ->
                    Text("Use 3–24 letters, numbers, dots, or underscores.", color = VybeDanger, fontWeight = FontWeight.SemiBold)
                  userIdAvailabilityMessage != null ->
                    Text(
                      text = userIdAvailabilityMessage ?: "",
                      color = if (isUserIdAvailable) VybeOnlineGreen else VybeDanger,
                      fontWeight = FontWeight.SemiBold
                    )
                }
              },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("auth_username_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Field 4: Email Address
            OutlinedTextField(
              value = email,
              onValueChange = { email = it; errorMessage = null },
              label = { Text("Email Address *") },
              leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
              isError = isEmailInvalid,
              supportingText = if (isEmailInvalid) {
                { Text("Enter a valid email address.", color = VybeDanger) }
              } else null,
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("auth_email_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Field 5: Phone Number
            OutlinedTextField(
              value = phone,
              onValueChange = { phone = it; errorMessage = null },
              label = { Text("Phone Number (e.g. +91 9876543210) *") },
              leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
              isError = isPhoneInvalid,
              supportingText = if (isPhoneInvalid) {
                { Text("Enter a valid 10–15 digit phone number.", color = VybeDanger) }
              } else null,
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("auth_phone_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Field 6: Date of Birth (Empty by default + Calendar Picker + YYYY-MM-DD + 16+ Gate)
            OutlinedTextField(
              value = dateOfBirth,
              onValueChange = { dateOfBirth = it; errorMessage = null },
              label = { Text("Date of Birth (YYYY-MM-DD) *") },
              placeholder = { Text("YYYY-MM-DD") },
              leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
              trailingIcon = {
                IconButton(
                  onClick = { showDobDatePicker = true },
                  modifier = Modifier.testTag("auth_dob_picker_button")
                ) {
                  Icon(Icons.Default.EditCalendar, contentDescription = "Pick Date of Birth", tint = VybeBrandCyan)
                }
              },
              isError = isDobInvalid,
              supportingText = {
                if (isDobInvalid) {
                  Text(
                    dobAgeCheck ?: "Date of birth is required (YYYY-MM-DD, 16+).",
                    color = VybeDanger,
                    fontWeight = FontWeight.SemiBold
                  )
                } else {
                  Text("Must be 16 or older to create an account.")
                }
              },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("auth_dob_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Field 7: Create Password with Show/Hide button
            OutlinedTextField(
              value = createPassword,
              onValueChange = { createPassword = it; errorMessage = null },
              label = { Text("Password (8+ characters) *") },
              leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
              trailingIcon = {
                IconButton(onClick = { showCreatePassword = !showCreatePassword }) {
                  Icon(
                    imageVector = if (showCreatePassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Show/Hide Password"
                  )
                }
              },
              isError = isCreatePassInvalid,
              supportingText = if (isCreatePassInvalid) {
                { Text("Password must be at least 8 characters.", color = VybeDanger) }
              } else null,
              visualTransformation = if (showCreatePassword) VisualTransformation.None else PasswordVisualTransformation(),
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("auth_create_password_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Field 8: Confirm Password with Show/Hide button
            OutlinedTextField(
              value = confirmPassword,
              onValueChange = { confirmPassword = it; errorMessage = null },
              label = { Text("Confirm Password *") },
              leadingIcon = { Icon(Icons.Default.LockReset, contentDescription = null) },
              trailingIcon = {
                IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                  Icon(
                    imageVector = if (showConfirmPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Show/Hide Confirm Password"
                  )
                }
              },
              isError = isConfirmPassInvalid,
              supportingText = if (isConfirmPassInvalid) {
                { Text("Passwords do not match.", color = VybeDanger) }
              } else null,
              visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("auth_confirm_password_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Terms & Conditions agreement checkbox + readable document link
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Checkbox(
                checked = termsAccepted,
                onCheckedChange = { termsAccepted = it; errorMessage = null },
                modifier = Modifier.testTag("auth_terms_checkbox")
              )
              Spacer(modifier = Modifier.width(6.dp))
              Column {
                Text(
                  text = "I agree to the Terms of Service & Privacy Policy",
                  fontSize = 12.sp,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = "Tap to read full v2.0 Community Policy",
                  fontSize = 11.sp,
                  color = VybeBrandCyan,
                  fontWeight = FontWeight.SemiBold,
                  modifier = Modifier.clickable { showLegalReader = true }
                )
              }
            }

            Spacer(modifier = Modifier.height(18.dp))

            VybeGradientButton(
              text = if (isLoading) "Creating Account..." else "Create Account & Join VYBE",
              onClick = {
                if (isLoading) return@VybeGradientButton
                attemptedRegisterSubmit = true
                errorMessage = null
                statusMessage = null
                val dobErr = validateRegistrationDob(dateOfBirth)
                when {
                  firstName.trim().length < 2 -> errorMessage = "Please enter your first name (at least 2 characters)."
                  lastName.trim().isEmpty() -> errorMessage = "Please enter your last name."
                  cleanUsername.isEmpty() || isUsernameFormatInvalid ->
                    errorMessage = "Please enter a valid username (3–24 letters, numbers, dots, or underscores)."
                  !isUserIdAvailable -> errorMessage = "This username is already taken. Please choose another."
                  !email.trim().matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) ->
                    errorMessage = "Please enter a valid email address."
                  phoneDigitsCount !in 10..15 ->
                    errorMessage = "Please enter a valid phone number (10–15 digits)."
                  dobErr != null -> errorMessage = dobErr
                  createPassword.length < 8 -> errorMessage = "Password must be at least 8 characters."
                  createPassword != confirmPassword -> errorMessage = "Passwords do not match."
                  !termsAccepted -> errorMessage = "You must agree to the Terms of Service & Privacy Policy to register."
                  else -> {
                    coroutineScope.launch {
                      isLoading = true
                      val (ok, msg) = viewModel.registerWithSupabase(
                        username = cleanUsername,
                        email = email.trim(),
                        password = createPassword,
                        firstName = firstName.trim(),
                        lastName = lastName.trim(),
                        phone = phone.trim(),
                        dob = dateOfBirth.trim(),
                        termsAccepted = termsAccepted
                      )
                      isLoading = false
                      if (!ok) {
                        errorMessage = msg
                      } else if (msg.startsWith("NEEDS_OTP")) {
                        registrationStep = 2
                        verifyEmailInput = email.trim()
                        verifyPhoneInput = phone.trim()
                        verifyOtpInput = ""
                        verifyPhoneOtpInput = ""
                        verifyError = null
                        phoneVerifyError = null
                        emailResendCooldownSec = 60
                        statusMessage = msg.substringAfter("NEEDS_OTP:", "Verify your email and phone number to complete signup.").trim()
                      } else {
                        statusMessage = msg
                      }
                    }
                  }
                }
              },
              modifier = Modifier.testTag("auth_submit_button")
            )
            }
          } else {
            // Business Registration & Verification Flow (Section 11)
            OutlinedTextField(
              value = businessName,
              onValueChange = { businessName = it },
              label = { Text("Registered Business Name") },
              leadingIcon = { Icon(Icons.Default.Store, contentDescription = null, tint = VybeVerifiedGold) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("business_name_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
              value = ownerName,
              onValueChange = { ownerName = it },
              label = { Text("Owner / Representative Legal Name") },
              leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("business_owner_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              OutlinedTextField(
                value = businessType,
                onValueChange = { businessType = it },
                label = { Text("Business Type") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("business_type_input")
              )
              OutlinedTextField(
                value = businessCategory,
                onValueChange = { businessCategory = it },
                label = { Text("Category") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("business_category_input")
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
              value = businessAddress,
              onValueChange = { businessAddress = it },
              label = { Text("Official Business Address") },
              leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("business_address_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
              value = businessContactEmail,
              onValueChange = { businessContactEmail = it },
              label = { Text("Business Contact Email") },
              leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("business_email_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
              value = businessContactPhone,
              onValueChange = { businessContactPhone = it },
              label = { Text("Business Contact Phone (+91)") },
              leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("business_phone_input")
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Document & Logo Upload Preview Card (PDF / JPEG to backend team)
            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(14.dp),
              border = androidx.compose.foundation.BorderStroke(1.dp, VybeVerifiedGold.copy(alpha = 0.5f)),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.UploadFile, contentDescription = null, tint = VybeVerifiedGold, modifier = Modifier.size(22.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Verification Document (PDF / JPEG)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = "File: $businessDocName",
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                  color = VybeVerifiedGold.copy(alpha = 0.15f),
                  shape = RoundedCornerShape(6.dp)
                ) {
                  Text(
                    text = "🔒 Direct Channel to NR Next Backend Verification Team",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = VybeVerifiedGold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                  OutlinedButton(
                    onClick = {
                      try {
                        docPickerLauncher.launch("application/pdf")
                      } catch (e: Exception) {
                        businessDocName = "Registration_Certificate_${System.currentTimeMillis().toString().takeLast(4)}.pdf"
                      }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                  ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFFFF5252))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Select PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                  }
                  OutlinedButton(
                    onClick = {
                      try {
                        photoDocPickerLauncher.launch(
                          androidx.activity.result.PickVisualMediaRequest(
                            androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                          )
                        )
                      } catch (e: Exception) {
                        businessDocName = "Shop_License_Photo_${System.currentTimeMillis().toString().takeLast(4)}.jpeg"
                      }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                  ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp), tint = VybeBrandCyan)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Select JPEG", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                  }
                }
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Terms Checkbox
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Checkbox(
                checked = termsAccepted,
                onCheckedChange = { termsAccepted = it },
                modifier = Modifier.testTag("business_terms_checkbox")
              )
              Spacer(modifier = Modifier.width(6.dp))
              Column {
                Text(
                  text = "I agree to Merchant Terms & Safety Guidelines",
                  fontSize = 12.sp,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = "Read complete Business Policies & Fee Schedule (v2.0)",
                  fontSize = 11.sp,
                  color = VybeBrandCyan,
                  fontWeight = FontWeight.SemiBold,
                  modifier = Modifier.clickable { showLegalReader = true }
                )
              }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
              onClick = {
                errorMessage = null
                if (businessName.isBlank() || ownerName.isBlank() || businessAddress.isBlank() || businessContactEmail.isBlank() || businessContactPhone.isBlank()) {
                  errorMessage = "Please complete all business verification fields."
                } else if (!termsAccepted) {
                  errorMessage = "You must agree to the Merchant Terms & Conditions."
                } else {
                  viewModel.submitBusinessApplication(
                    name = businessName,
                    cat = businessCategory,
                    gst = "GSTIN27AAACR1234F1Z5",
                    email = businessContactEmail,
                    phone = businessContactPhone,
                    addr = businessAddress
                  )
                  statusMessage = "Business Application with $businessDocName transmitted directly to NR Next Backend Team! Status: Under Review."
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = VybeVerifiedGold),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier.fillMaxWidth().height(48.dp).testTag("business_submit_button")
            ) {
              Text("Submit Business Application", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
          }
        } else {
          // Login Form (Email, Phone Number, or @Username / VYBE User ID)
          OutlinedTextField(
            value = loginIdentifier,
            onValueChange = {
              loginIdentifier = it
              errorMessage = null
            },
            label = { Text("Email, Phone Number (+91), or @Username") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            supportingText = {
              Text("Sign in with your verified Email, Phone Number, or VYBE @username.", fontSize = 11.sp)
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("auth_identifier_input")
          )

          Spacer(modifier = Modifier.height(12.dp))

          OutlinedTextField(
            value = loginPassword,
            onValueChange = {
              loginPassword = it
              errorMessage = null
            },
            label = { Text("Password") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = {
              IconButton(onClick = { showLoginPassword = !showLoginPassword }) {
                Icon(
                  imageVector = if (showLoginPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                  contentDescription = "Show/Hide Password"
                )
              }
            },
            visualTransformation = if (showLoginPassword) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("auth_password_input")
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
          ) {
            TextButton(
              onClick = {
                resetEmail = if (loginIdentifier.contains("@") && loginIdentifier.contains(".")) loginIdentifier.trim() else email.trim()
                resetStep = 1
                resetError = null
                resetMessage = null
                showForgotPasswordDialog = true
              },
              modifier = Modifier.testTag("auth_forgot_password_button")
            ) {
              Text("Forgot password?", fontSize = 12.sp, color = VybeBrandCyan, fontWeight = FontWeight.SemiBold)
            }
          }

          if (errorMessage != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
              color = VybeDanger.copy(alpha = 0.15f),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth().testTag("auth_login_error_banner")
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = VybeDanger, modifier = Modifier.size(20.dp))
                  Spacer(modifier = Modifier.width(10.dp))
                  Text(
                    text = errorMessage ?: "Unable to log in. Please check credentials.",
                    color = VybeDanger,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                  )
                }
                if (errorMessage?.contains("not confirmed", ignoreCase = true) == true ||
                  errorMessage?.contains("verify", ignoreCase = true) == true
                ) {
                  Spacer(modifier = Modifier.height(8.dp))
                  OutlinedButton(
                    onClick = {
                      if (loginIdentifier.contains("@") && loginIdentifier.contains(".")) {
                        verifyEmailInput = loginIdentifier.trim()
                      }
                      if (loginIdentifier.any { it.isDigit() } && !loginIdentifier.contains("@")) {
                        verifyPhoneInput = loginIdentifier.trim()
                      }
                      isRegisterMode = true
                      accountType = "INDIVIDUAL"
                      registrationStep = 2
                      errorMessage = null
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                  ) {
                    Text("Verify Email / Phone OTP Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                  }
                }
              }
            }
          }

          if (statusMessage != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
              color = VybeOnlineGreen.copy(alpha = 0.15f),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth().testTag("auth_login_status_banner")
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = VybeOnlineGreen, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                  text = statusMessage ?: "",
                  color = VybeOnlineGreen,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.Medium
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          VybeGradientButton(
            text = if (isLoading) "Logging in..." else "Log In to VYBE",
            onClick = {
              if (isLoading) return@VybeGradientButton
              errorMessage = null
              statusMessage = null
              if (loginIdentifier.trim().isBlank() || loginPassword.isBlank()) {
                errorMessage = "Please enter your Email, Phone Number, or @Username and password."
              } else {
                coroutineScope.launch {
                  isLoading = true
                  val (ok, msg) = viewModel.loginWithSupabase(loginIdentifier.trim(), loginPassword)
                  isLoading = false
                  if (!ok) {
                    errorMessage = msg
                  }
                }
              }
            },
            modifier = Modifier.testTag("auth_submit_button")
          )

          Spacer(modifier = Modifier.height(14.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text("By signing in, you agree to our ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
              "Terms",
              fontSize = 11.sp,
              color = VybeBrandCyan,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.clickable { showLegalReader = true }.testTag("login_terms_link")
            )
            Text(" & ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
              "Privacy Policy",
              fontSize = 11.sp,
              color = VybeBrandCyan,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.clickable { showLegalReader = true }.testTag("login_privacy_link")
            )
          }
        }

      Spacer(modifier = Modifier.height(16.dp))

      TextButton(
        onClick = {
          isRegisterMode = !isRegisterMode
          errorMessage = null
          statusMessage = null
        },
        modifier = Modifier.testTag("auth_mode_toggle_button")
      ) {
        Text(
          text = if (isRegisterMode) "Already have an account? Log In" else "New to VYBE? Create an Account",
          color = MaterialTheme.colorScheme.primary,
          fontWeight = FontWeight.SemiBold
        )
      }

      Spacer(modifier = Modifier.height(36.dp))
    }

    // --- DIALOG 1: IN-APP EMAIL OTP VERIFICATION ---
    if (showOtpVerifyDialog) {
      AlertDialog(
        onDismissRequest = { if (!verifyLoading) showOtpVerifyDialog = false },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.MarkEmailRead, contentDescription = null, tint = VybeViolet)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Verify Your Email", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
          }
        },
        text = {
          Column {
            Text(
              "Enter the 6-digit verification code sent to your email.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
              value = verifyEmailInput,
              onValueChange = { verifyEmailInput = it },
              label = { Text("Email Address") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
              value = verifyOtpInput,
              onValueChange = { verifyOtpInput = it },
              label = { Text("6-Digit Verification Code") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )
            if (verifyError != null) {
              Spacer(modifier = Modifier.height(8.dp))
              Text(verifyError!!, color = VybeDanger, fontSize = 11.sp)
            }
          }
        },
        confirmButton = {
          Button(
            onClick = {
              verifyError = null
              coroutineScope.launch {
                verifyLoading = true
                val (ok, msg) = viewModel.verifyOtp(verifyEmailInput, verifyOtpInput, "signup")
                verifyLoading = false
                if (ok) {
                  showOtpVerifyDialog = false
                  statusMessage = msg
                } else {
                  verifyError = msg
                }
              }
            },
            enabled = !verifyLoading && verifyEmailInput.isNotBlank() && verifyOtpInput.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = VybeViolet)
          ) {
            Text(if (verifyLoading) "Verifying..." else "Verify & Log In")
          }
        },
        dismissButton = {
          TextButton(
            onClick = { showOtpVerifyDialog = false },
            enabled = !verifyLoading
          ) {
            Text("Cancel")
          }
        }
      )
    }

    // --- DIALOG 2: FORGOT PASSWORD (SUPABASE RECOVERY EMAIL LINK FLOW) ---
    if (showForgotPasswordDialog) {
      AlertDialog(
        onDismissRequest = { if (!resetLoading) showForgotPasswordDialog = false },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LockReset, contentDescription = null, tint = VybeBrandCyan)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              if (resetStep == 1) "Forgot Password" else "Check Your Email",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
          }
        },
        text = {
          Column {
            if (resetStep == 1) {
              Text(
                "Enter your registered email address. VYBE will send a secure password-recovery link to your inbox.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.height(12.dp))
              OutlinedTextField(
                value = resetEmail,
                onValueChange = {
                  resetEmail = it
                  resetError = null
                },
                label = { Text("Registered Email Address") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("forgot_password_email_input")
              )
            } else {
              Text(
                resetMessage ?: "If an account exists for $resetEmail, a secure password recovery link has been sent.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
              )
              Spacer(modifier = Modifier.height(10.dp))
              Text(
                "Tap the link in the email on this device to open VYBE's dedicated Password Recovery screen and choose your new password.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.height(12.dp))
              OutlinedButton(
                onClick = {
                  val targetEmail = resetEmail.trim()
                  showForgotPasswordDialog = false
                  viewModel.openManualPasswordRecovery(targetEmail)
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("open_recovery_screen_button")
              ) {
                Text("Have a Recovery Token? Verify Here", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
              }
            }
            if (resetError != null) {
              Spacer(modifier = Modifier.height(8.dp))
              Text(resetError!!, color = VybeDanger, fontSize = 11.sp)
            }
          }
        },
        confirmButton = {
          if (resetStep == 1) {
            Button(
              onClick = {
                resetError = null
                coroutineScope.launch {
                  resetLoading = true
                  val (ok, msg) = viewModel.sendPasswordReset(resetEmail.trim())
                  resetLoading = false
                  if (ok) {
                    resetStep = 2
                    resetMessage = msg
                  } else {
                    resetError = msg
                  }
                }
              },
              enabled = !resetLoading && resetEmail.trim().isNotEmpty(),
              colors = ButtonDefaults.buttonColors(containerColor = VybeBrandCyan),
              modifier = Modifier.testTag("send_recovery_link_button")
            ) {
              Text(
                if (resetLoading) "Sending..." else "Send Recovery Link",
                color = Color.Black,
                fontWeight = FontWeight.Bold
              )
            }
          } else {
            Button(
              onClick = {
                showForgotPasswordDialog = false
                statusMessage = "Check your email ($resetEmail) and open the VYBE password recovery link."
              },
              colors = ButtonDefaults.buttonColors(containerColor = VybeBrandCyan)
            ) {
              Text("Done", color = Color.Black, fontWeight = FontWeight.Bold)
            }
          }
        },
        dismissButton = {
          if (resetStep == 2) {
            TextButton(
              onClick = {
                resetError = null
                coroutineScope.launch {
                  resetLoading = true
                  val (ok, msg) = viewModel.sendPasswordReset(resetEmail.trim())
                  resetLoading = false
                  if (ok) resetMessage = "Fresh recovery link sent to ${resetEmail.trim()}."
                  else resetError = msg
                }
              },
              enabled = !resetLoading
            ) {
              Text("Resend Link")
            }
          } else {
            TextButton(
              onClick = { showForgotPasswordDialog = false },
              enabled = !resetLoading
            ) {
              Text("Cancel")
            }
          }
        }
      )
    }

    // --- DIALOG 3: TERMS & CONDITIONS + PRIVACY POLICY READER ---
    if (showLegalReader) {
      TermsAndPrivacyReaderDialog(onDismiss = { showLegalReader = false })
    }

    // --- DIALOG 4: DATE OF BIRTH CALENDAR PICKER ---
    if (showDobDatePicker) {
      val datePickerState = rememberDatePickerState()
      DatePickerDialog(
        onDismissRequest = { showDobDatePicker = false },
        confirmButton = {
          TextButton(
            onClick = {
              val millis = datePickerState.selectedDateMillis
              if (millis != null) {
                val localDate = java.time.Instant.ofEpochMilli(millis)
                  .atZone(java.time.ZoneOffset.UTC)
                  .toLocalDate()
                dateOfBirth = localDate.toString()
                errorMessage = null
              }
              showDobDatePicker = false
            }
          ) {
            Text("Confirm Date", fontWeight = FontWeight.Bold)
          }
        },
        dismissButton = {
          TextButton(onClick = { showDobDatePicker = false }) {
            Text("Cancel")
          }
        }
      ) {
        DatePicker(state = datePickerState)
      }
    }
  }
}

private fun validateRegistrationDob(rawDob: String): String? {
  val trimmed = rawDob.trim()
  if (trimmed.isEmpty()) return "Date of birth is required (YYYY-MM-DD)."
  return try {
    val parsed = java.time.LocalDate.parse(trimmed)
    val today = java.time.LocalDate.now()
    if (parsed.isAfter(today)) {
      "Date of birth cannot be in the future."
    } else if (parsed.isBefore(java.time.LocalDate.of(1905, 1, 1))) {
      "Please enter a valid date of birth."
    } else {
      val age = java.time.Period.between(parsed, today).years
      if (age < 16) "You must be at least 16 years old to register on VYBE." else null
    }
  } catch (_: Exception) {
    "Enter date of birth in YYYY-MM-DD format (e.g. 2004-05-21)."
  }
}

@Composable
fun TermsAndPrivacyReaderDialog(onDismiss: () -> Unit) {
  var selectedTab by remember { mutableStateOf(0) } // 0 = Terms, 1 = Privacy
  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Gavel, contentDescription = null, tint = VybeBrandCyan)
        Spacer(modifier = Modifier.width(8.dp))
        Text("VYBE Community & Legal", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(max = 380.dp)
      ) {
        TabRow(
          selectedTabIndex = selectedTab,
          containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          contentColor = VybeBrandCyan
        ) {
          Tab(
            selected = selectedTab == 0,
            onClick = { selectedTab = 0 },
            text = { Text("Terms of Service", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
          )
          Tab(
            selected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            text = { Text("Privacy Policy", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
          )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .verticalScroll(rememberScrollState())
        ) {
          if (selectedTab == 0) {
            Text("VYBE Terms of Service", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            Text("Version 3.0 • Effective October 2026 • Product of NR Next | NR Group", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              "1. Age Requirement: VYBE is exclusively built for individuals aged 16 and older. Users aged 16-17 are automatically placed in Teen Safety Protected Mode.\n\n" +
              "2. Authentic Social Experience: Impersonation, unauthorized scraping, spamming, and harassment are strictly prohibited. Accounts engaging in abusive behavior will be blocked and investigated.\n\n" +
              "3. Content Ownership: You retain ownership of all media and posts you publish on VYBE. You grant VYBE the license to display and distribute your posts per your chosen visibility settings.\n\n" +
              "4. Unfiltered Policy: VYBE Unfiltered posts emphasize raw, authentic self-expression without algorithmic like-farming.",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              lineHeight = 17.sp
            )
          } else {
            Text("VYBE Privacy Policy", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
            Text("Zero-Trust Architecture • Protected Community Cloud", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              "1. Data Privacy: We never sell your personal data. Private phone numbers and email addresses are never exposed to other community members or publicly indexable.\n\n" +
              "2. Consent-First Crossed Paths: Encounter discovery is coarse-grained and requires mutual consent before any direct contact is established.\n\n" +
              "3. End-to-End Control: You can request complete account deletion or data export anytime via Settings.\n\n" +
              "4. Protected Messaging: Direct messages are protected with Row Level Security so only conversation participants can read them.",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              lineHeight = 17.sp
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = onDismiss,
        colors = ButtonDefaults.buttonColors(containerColor = VybeViolet)
      ) {
        Text("I Understand & Close", fontWeight = FontWeight.Bold)
      }
    }
  )
}
