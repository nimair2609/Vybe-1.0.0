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
@Composable
fun AuthScreen(viewModel: VybeViewModel) {
  var isRegisterMode by remember { mutableStateOf(false) }

  // Account Type Selection (Section 2 & 11)
  var accountType by remember { mutableStateOf("INDIVIDUAL") } // "INDIVIDUAL" or "BUSINESS"
  var showLegalReader by remember { mutableStateOf(false) }

  // 10 Registration Fields (Individual)
  var firstName by remember { mutableStateOf("") }
  var lastName by remember { mutableStateOf("") }
  var username by remember { mutableStateOf("") }
  var email by remember { mutableStateOf("") }
  var phone by remember { mutableStateOf("") }
  var dateOfBirth by remember { mutableStateOf("2004-03-15") }
  var createPassword by remember { mutableStateOf("") }
  var confirmPassword by remember { mutableStateOf("") }
  var termsAccepted by remember { mutableStateOf(false) } // Not preselected per Section 2 & 6

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
  var verifyEmailInput by remember { mutableStateOf("") }
  var verifyOtpInput by remember { mutableStateOf("") }
  var verifyLoading by remember { mutableStateOf(false) }
  var verifyError by remember { mutableStateOf<String?>(null) }
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

      // Supabase Connection Status Banner
      Spacer(modifier = Modifier.height(10.dp))
      Surface(
        color = if (viewModel.isSupabaseConfigured) VybeOnlineGreen.copy(alpha = 0.12f) else Color(0xFFFFB300).copy(alpha = 0.12f),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
          1.dp,
          if (viewModel.isSupabaseConfigured) VybeOnlineGreen.copy(alpha = 0.35f) else Color(0xFFFFB300).copy(alpha = 0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = if (viewModel.isSupabaseConfigured) Icons.Default.CloudDone else Icons.Default.CloudQueue,
            contentDescription = "Supabase Status",
            tint = if (viewModel.isSupabaseConfigured) VybeOnlineGreen else Color(0xFFFFB300),
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = if (viewModel.isSupabaseConfigured) "Supabase Auth & Database Live" else "Developer Mode • Connect SUPABASE_URL in Secrets for Live Auth",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (viewModel.isSupabaseConfigured) VybeOnlineGreen else Color(0xFFFFB300)
          )
        }
      }

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

      if (errorMessage != null) {
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
            if (errorMessage?.contains("confirm", ignoreCase = true) == true || errorMessage?.contains("not confirmed", ignoreCase = true) == true) {
              Spacer(modifier = Modifier.height(6.dp))
              TextButton(
                onClick = {
                  verifyEmailInput = if (loginIdentifier.contains("@")) loginIdentifier else email
                  showOtpVerifyDialog = true
                },
                contentPadding = PaddingValues(0.dp)
              ) {
                Text("Enter Verification Code ->", color = VybeBrandCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
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
            // Field 1 & 2: First Name and Last Name
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it },
                label = { Text("First Name") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("auth_first_name_input")
              )
              OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it },
                label = { Text("Last Name") },
                singleLine = true,
                modifier = Modifier.weight(1f).testTag("auth_last_name_input")
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Field 3: Suggested User ID with live availability check (Section 6)
            OutlinedTextField(
              value = username,
              onValueChange = {
                username = it
                if (it.isNotBlank()) {
                  val (avail, msg) = viewModel.checkUserIdAvailability(it)
                  isUserIdAvailable = avail
                  userIdAvailabilityMessage = msg
                } else {
                  userIdAvailabilityMessage = null
                }
              },
              label = { Text("Unique User ID (@username)") },
              leadingIcon = { Text("@", fontWeight = FontWeight.Bold, color = VybeViolet) },
              supportingText = {
                if (userIdAvailabilityMessage != null) {
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
              onValueChange = { email = it },
              label = { Text("Email Address") },
              leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("auth_email_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Field 5: Phone Number
            OutlinedTextField(
              value = phone,
              onValueChange = { phone = it },
              label = { Text("Phone Number (+91)") },
              leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("auth_phone_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Field 6: Date of Birth
            OutlinedTextField(
              value = dateOfBirth,
              onValueChange = { dateOfBirth = it },
              label = { Text("Date of Birth (YYYY-MM-DD)") },
              leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
              supportingText = { Text("Must be 16 or older to create an account.") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("auth_dob_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Field 8: Create Password with Show/Hide button
            OutlinedTextField(
              value = createPassword,
              onValueChange = { createPassword = it },
              label = { Text("Create Password") },
              leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
              trailingIcon = {
                IconButton(onClick = { showCreatePassword = !showCreatePassword }) {
                  Icon(
                    imageVector = if (showCreatePassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Show/Hide Password"
                  )
                }
              },
              visualTransformation = if (showCreatePassword) VisualTransformation.None else PasswordVisualTransformation(),
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("auth_create_password_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Field 9: Confirm Password with Show/Hide button
            OutlinedTextField(
              value = confirmPassword,
              onValueChange = { confirmPassword = it },
              label = { Text("Confirm Password") },
              leadingIcon = { Icon(Icons.Default.LockReset, contentDescription = null) },
              trailingIcon = {
                IconButton(onClick = { showConfirmPassword = !showConfirmPassword }) {
                  Icon(
                    imageVector = if (showConfirmPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "Show/Hide Confirm Password"
                  )
                }
              },
              visualTransformation = if (showConfirmPassword) VisualTransformation.None else PasswordVisualTransformation(),
              singleLine = true,
              modifier = Modifier.fillMaxWidth().testTag("auth_confirm_password_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Field 10: Terms & Conditions agreement checkbox + readable document link
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Checkbox(
                checked = termsAccepted,
                onCheckedChange = { termsAccepted = it },
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
                errorMessage = null
                statusMessage = null
                if (firstName.isBlank() || lastName.isBlank() || username.isBlank() || email.isBlank() || phone.isBlank()) {
                  errorMessage = "Please fill in all 10 registration details."
                } else if (!termsAccepted) {
                  errorMessage = "You must agree to the Terms of Service & Privacy Policy to register."
                } else if (createPassword != confirmPassword) {
                  errorMessage = "Passwords do not match."
                } else if (createPassword.length < 6) {
                  errorMessage = "Password must be at least 6 characters."
                } else if (!isUserIdAvailable) {
                  errorMessage = "This User ID is already taken. Please choose another."
                } else {
                  coroutineScope.launch {
                    isLoading = true
                    val (ok, msg) = viewModel.registerWithSupabase(
                      username = username,
                      email = email,
                      password = createPassword,
                      firstName = firstName,
                      lastName = lastName,
                      phone = phone,
                      dob = dateOfBirth,
                      termsAccepted = termsAccepted
                    )
                    isLoading = false
                    if (!ok) {
                      errorMessage = msg
                    } else {
                      statusMessage = msg
                    }
                  }
                }
              },
              modifier = Modifier.testTag("auth_submit_button")
            )
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

            // Document & Logo Upload Preview Card
            Surface(
              color = MaterialTheme.colorScheme.surfaceVariant,
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(Icons.Default.UploadFile, contentDescription = null, tint = VybeVerifiedGold, modifier = Modifier.size(20.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text("Verification Document Attached:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(businessDocName, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Logo attached: official_shop_mark.png", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                  val app = com.example.data.model.BusinessApplication(
                    id = "bapp_${System.currentTimeMillis()}",
                    ownerId = "u_pending_merchant",
                    businessName = businessName,
                    category = businessCategory,
                    registrationNumberOrGst = "GSTIN27AAACR1234F1Z5",
                    contactEmail = businessContactEmail,
                    contactPhone = businessContactPhone,
                    address = businessAddress,
                    status = com.example.data.model.BusinessVerificationStatus.PENDING,
                    submittedAt = "Just now",
                    reviewerNotes = "Application submitted. Awaiting NR Next Safety audit."
                  )
                  viewModel.submitBusinessApplication(
                    name = businessName,
                    cat = businessCategory,
                    gst = "GSTIN27AAACR1234F1Z5",
                    email = businessContactEmail,
                    phone = businessContactPhone,
                    addr = businessAddress
                  )
                  statusMessage = "Business Verification Application Submitted! Status: Under Review by NR Next Safety team."
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
          // Login Form
          OutlinedTextField(
            value = loginIdentifier,
            onValueChange = { loginIdentifier = it },
            label = { Text("Username, Email or Mobile (+91)") },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("auth_identifier_input")
          )

          Spacer(modifier = Modifier.height(12.dp))

          OutlinedTextField(
            value = loginPassword,
            onValueChange = { loginPassword = it },
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
                resetEmail = if (loginIdentifier.contains("@")) loginIdentifier else email
                resetStep = 1
                resetError = null
                resetMessage = null
                showForgotPasswordDialog = true
              }
            ) {
              Text("Forgot password?", fontSize = 12.sp, color = VybeBrandCyan, fontWeight = FontWeight.SemiBold)
            }
          }

          Spacer(modifier = Modifier.height(24.dp))

          VybeGradientButton(
            text = if (isLoading) "Logging in..." else "Log In to VYBE",
            onClick = {
              errorMessage = null
              statusMessage = null
              if (loginIdentifier.isBlank() || loginPassword.isBlank()) {
                errorMessage = "Please enter your email and password."
              } else {
                coroutineScope.launch {
                  isLoading = true
                  val (ok, msg) = viewModel.loginWithSupabase(loginIdentifier, loginPassword)
                  isLoading = false
                  if (!ok) {
                    errorMessage = msg
                  }
                }
              }
            },
            modifier = Modifier.testTag("auth_submit_button")
          )
          Spacer(modifier = Modifier.height(8.dp))
          TextButton(
            onClick = {
              verifyEmailInput = if (loginIdentifier.contains("@")) loginIdentifier else email
              verifyError = null
              showOtpVerifyDialog = true
            },
            modifier = Modifier.fillMaxWidth()
          ) {
            Icon(Icons.Default.MarkEmailRead, contentDescription = null, modifier = Modifier.size(16.dp), tint = VybeViolet)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Have a confirmation code? Verify Email", fontSize = 12.sp, color = VybeViolet, fontWeight = FontWeight.SemiBold)
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
              "Enter the 6-digit confirmation code sent to your email by Supabase.",
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

    // --- DIALOG 2: FORGOT & RESET PASSWORD ---
    if (showForgotPasswordDialog) {
      AlertDialog(
        onDismissRequest = { if (!resetLoading) showForgotPasswordDialog = false },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LockReset, contentDescription = null, tint = VybeBrandCyan)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              if (resetStep == 1) "Reset Password" else "Enter New Password",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
          }
        },
        text = {
          Column {
            if (resetStep == 1) {
              Text(
                "Enter your registered email address. We will send a 6-digit recovery code.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.height(12.dp))
              OutlinedTextField(
                value = resetEmail,
                onValueChange = { resetEmail = it },
                label = { Text("Email Address") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
              )
            } else {
              Text(
                "A reset code was sent to . Enter the code and your new password.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.height(12.dp))
              OutlinedTextField(
                value = resetOtp,
                onValueChange = { resetOtp = it },
                label = { Text("6-Digit Reset Code") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
              )
              Spacer(modifier = Modifier.height(10.dp))
              OutlinedTextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("New Password (min 6 chars)") },
                singleLine = true,
                visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                  IconButton(onClick = { showNewPassword = !showNewPassword }) {
                    Icon(
                      imageVector = if (showNewPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                      contentDescription = null
                    )
                  }
                },
                modifier = Modifier.fillMaxWidth()
              )
            }
            if (resetError != null) {
              Spacer(modifier = Modifier.height(8.dp))
              Text(resetError!!, color = VybeDanger, fontSize = 11.sp)
            }
            if (resetMessage != null) {
              Spacer(modifier = Modifier.height(8.dp))
              Text(resetMessage!!, color = VybeOnlineGreen, fontSize = 11.sp)
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
                  val (ok, msg) = viewModel.sendPasswordReset(resetEmail)
                  resetLoading = false
                  if (ok) {
                    resetStep = 2
                    resetMessage = msg
                  } else {
                    resetError = msg
                  }
                }
              },
              enabled = !resetLoading && resetEmail.isNotBlank(),
              colors = ButtonDefaults.buttonColors(containerColor = VybeBrandCyan)
            ) {
              Text(if (resetLoading) "Sending..." else "Send Reset Code", color = Color.Black, fontWeight = FontWeight.Bold)
            }
          } else {
            Button(
              onClick = {
                resetError = null
                coroutineScope.launch {
                  resetLoading = true
                  val (ok, msg) = viewModel.resetPasswordWithOtp(resetEmail, resetOtp, newPassword)
                  resetLoading = false
                  if (ok) {
                    showForgotPasswordDialog = false
                    statusMessage = msg
                  } else {
                    resetError = msg
                  }
                }
              },
              enabled = !resetLoading && resetOtp.isNotBlank() && newPassword.length >= 6,
              colors = ButtonDefaults.buttonColors(containerColor = VybeBrandCyan)
            ) {
              Text(if (resetLoading) "Updating..." else "Confirm New Password", color = Color.Black, fontWeight = FontWeight.Bold)
            }
          }
        },
        dismissButton = {
          TextButton(
            onClick = { showForgotPasswordDialog = false },
            enabled = !resetLoading
          ) {
            Text("Cancel")
          }
        }
      )
    }
  }
}
