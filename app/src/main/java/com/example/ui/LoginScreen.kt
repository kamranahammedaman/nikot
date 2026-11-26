package com.example.ui

import android.graphics.BitmapFactory
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import com.example.data.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

// ---- Real scanner & live map support (CameraX + ML Kit, osmdroid + FusedLocation) ----
import android.Manifest
import android.annotation.SuppressLint
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.LocationServices
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import org.osmdroid.config.Configuration as OsmConfiguration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.MapEventsOverlay
import java.util.concurrent.Executors

// Theme Toggle State
@Composable
fun LoginScreen(viewModel: PharmacyViewModel, isBengali: Boolean) {
    var isSignUpMode by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    // Login states
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    // Signup states
    var signupUsername by remember { mutableStateOf("") }
    var signupPharmacyName by remember { mutableStateOf("") }
    var signupAddress by remember { mutableStateOf("") }
    var signupPhone by remember { mutableStateOf("") }
    var signupOwnerName by remember { mutableStateOf("") }
    var signupPassword by remember { mutableStateOf("") }
    var isSignupPasswordVisible by remember { mutableStateOf(false) }
    var signupRole by remember { mutableStateOf("customer") } // "admin", "staff", "customer"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Quick Controls Header (Language, Theme & Server toggle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Language selector button
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = GlassFillStrong),
                border = BorderStroke(1.dp, GlassBorder),
                modifier = Modifier.clickable { viewModel.toggleLanguage() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Language,
                        contentDescription = "Language",
                        tint = DarkAccentGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isBengali) "ENG" else "বাংলা",
                        color = DarkTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Theme switch button
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = GlassFillStrong),
                border = BorderStroke(1.dp, GlassBorder),
                modifier = Modifier.clickable { viewModel.toggleDarkMode() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isDarkModeGlobal) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = "Theme",
                        tint = DarkAccentGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isDarkModeGlobal) {
                            if (isBengali) "লাইট" else "Light"
                        } else {
                            if (isBengali) "ডার্ক" else "Dark"
                        },
                        color = DarkTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        RoLogoHeader(height = 90.dp)
        Spacer(modifier = Modifier.height(16.dp))

        if (isSignUpMode) {
            Text(
                text = if (isBengali) "নতুন একাউন্ট রেজিস্ট্রেশন" else "New Account Registration",
                color = DarkTextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            Text(
                text = if (isBengali) "প্রয়োজনীয় তথ্য দিয়ে আপনার একাউন্ট নিবন্ধন করুন" else "Register your account with required details",
                color = DarkTextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp, start = 8.dp, end = 8.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Conditional views based on isSignUpMode
        if (!isSignUpMode) {
            // Login Fields
            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    errorMsg = ""
                },
                label = { Text(if (isBengali) "ইউজারনেম / ফার্মেসীর নাম / ব্যক্তির নাম" else "Username / Pharmacy Name / Person Name") },
                placeholder = { Text(if (isBengali) "ইউজারনেম, ফার্মেসীর নাম বা ব্যক্তির নাম দিন" else "Enter username, pharmacy name, or person name", color = DarkTextSecondary.copy(alpha = 0.5f), fontSize = 12.sp) },
                textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DarkAccentRed,
                    unfocusedBorderColor = DarkTextSecondary,
                    focusedLabelColor = DarkAccentRed,
                    unfocusedLabelColor = DarkTextSecondary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    errorMsg = ""
                },
                label = { Text(if (isBengali) "পাসওয়ার্ড (Password)" else "Password") },
                textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                            tint = DarkTextSecondary
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DarkAccentRed,
                    unfocusedBorderColor = DarkTextSecondary,
                    focusedLabelColor = DarkAccentRed,
                    unfocusedLabelColor = DarkTextSecondary
                ),
                shape = RoundedCornerShape(12.dp)
            )
        } else {
            // Signup Fields
            OutlinedTextField(
                value = signupOwnerName,
                onValueChange = { signupOwnerName = it },
                label = { Text(if (isBengali) "আপনার পূর্ণ নাম (Full Name)" else "Your Full Name") },
                textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DarkAccentRed,
                    unfocusedBorderColor = DarkTextSecondary,
                    focusedLabelColor = DarkAccentRed,
                    unfocusedLabelColor = DarkTextSecondary
                ),
                shape = RoundedCornerShape(12.dp)
            )



            OutlinedTextField(
                value = signupUsername,
                onValueChange = { signupUsername = it },
                label = { Text(if (isBengali) "ইউজারনেম (Username - লগইন করার জন্য)" else "Username (For login)") },
                textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DarkAccentRed,
                    unfocusedBorderColor = DarkTextSecondary,
                    focusedLabelColor = DarkAccentRed,
                    unfocusedLabelColor = DarkTextSecondary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            OutlinedTextField(
                value = signupPhone,
                onValueChange = { signupPhone = it },
                label = { Text(if (isBengali) "মোবাইল নম্বর (Phone Number)" else "Phone Number") },
                textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DarkAccentRed,
                    unfocusedBorderColor = DarkTextSecondary,
                    focusedLabelColor = DarkAccentRed,
                    unfocusedLabelColor = DarkTextSecondary
                ),
                shape = RoundedCornerShape(12.dp)
            )



            OutlinedTextField(
                value = signupPassword,
                onValueChange = { signupPassword = it },
                label = { Text(if (isBengali) "পাসওয়ার্ড (Password)" else "Password") },
                textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                visualTransformation = if (isSignupPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isSignupPasswordVisible = !isSignupPasswordVisible }) {
                        Icon(
                            imageVector = if (isSignupPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (isSignupPasswordVisible) "Hide password" else "Show password",
                            tint = DarkTextSecondary
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DarkAccentRed,
                    unfocusedBorderColor = DarkTextSecondary,
                    focusedLabelColor = DarkAccentRed,
                    unfocusedLabelColor = DarkTextSecondary
                ),
                shape = RoundedCornerShape(12.dp)
            )
        }

        if (errorMsg.isNotEmpty()) {
            Text(
                text = errorMsg,
                color = DarkAccentRed,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 8.dp),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Main Submit Action Button
        Button(
            onClick = {
                if (!isSignUpMode) {
                    val (loginSuccess, loginError) = viewModel.loginWithResult(username, password)
                    if (loginSuccess) {
                        errorMsg = ""
                    } else {
                        // Check if direct cloud query finds the user across devices
                        viewModel.loginAsync(username, password) { cloudOk, cloudErr ->
                            if (cloudOk) {
                                errorMsg = ""
                            } else {
                                errorMsg = if (cloudErr.isNotBlank()) cloudErr else (if (isBengali) "ভুল ইউজারনেম, ফার্মেসীর নাম বা ব্যক্তির নাম অথবা পাসওয়ার্ড!" else "Incorrect username, pharmacy name, person name, or password!")
                            }
                        }
                    }
                } else {
                    if (signupUsername.trim().isEmpty() || signupPassword.trim().isEmpty() || signupOwnerName.trim().isEmpty()) {
                        errorMsg = if (isBengali) "নাম, ইউজারনেম এবং পাসওয়ার্ড পূরণ করুন!" else "Please fill name, username and password!"
                    } else {
                        val chosenRole = if (signupRole == "merchant") "admin" else signupRole
                        val ok = viewModel.signUp(
                            username = signupUsername,
                            pharmName = signupPharmacyName,
                            addr = signupAddress,
                            ph = signupPhone,
                            ownName = signupOwnerName,
                            pass = signupPassword,
                            role = chosenRole
                        )
                        if (ok) {
                            errorMsg = ""
                        } else {
                            errorMsg = if (isBengali) "ইউজারনেম আগে থেকেই নিবন্ধিত!" else "Username is already registered!"
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DarkAccentRed, contentColor = DarkAccentText),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = if (isSignUpMode) {
                    if (isBengali) "রেজিস্ট্রেশন সম্পন্ন করুন" else "Complete Registration"
                } else {
                    if (isBengali) "লগইন করুন" else "Log In"
                },
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = DarkAccentText
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Toggle text link below the button
        Text(
            text = if (isSignUpMode) {
                if (isBengali) "ইতিমধ্যে একাউন্ট আছে? লগইন করুন" else "Already have an account? Log In"
            } else {
                if (isBengali) "নতুন একাউন্ট খুলতে চান? এখানে ক্লিক করুন" else "Need an account? Sign Up Here"
            },
            color = DarkAccentGold,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable {
                    isSignUpMode = !isSignUpMode
                    errorMsg = ""
                }
                .padding(6.dp),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(28.dp))
        PoweredByRoFooter(
            isBengali = isBengali,
            badgeHeight = 18.dp,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )
    }
}

fun Modifier.scaleLogo(scale: Float): Modifier = this.padding(16.dp)

