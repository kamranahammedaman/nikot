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
fun AccountScreenForCustomer(viewModel: PharmacyViewModel, isBengali: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    var showEditProfile by remember { mutableStateOf(false) }
    var showPortalSwitchDialog by remember { mutableStateOf(false) }
    var showLocationSettingsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Portal Switch Settings Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.2.dp, DarkAccentGold),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(DarkAccentGold.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = "Switch Portal",
                            tint = DarkAccentGold,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isBengali) "পোর্টাল পরিবর্তন সেটিংস" else "Portal Switch Settings",
                            color = DarkTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = if (isBengali) "স্টাফ বা মার্চেন্ট পোর্টালে যেতে ট্যাপ করুন" else "Switch to Staff or Merchant portal",
                            color = DarkTextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { showPortalSwitchDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkBg),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.2.dp, DarkAccentGold)
                ) {
                    Icon(Icons.Default.SwapHoriz, "switch portal", tint = DarkAccentGold, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isBengali) "🔄 পোর্টাল পরিবর্তন করুন (স্টাফ / মার্চেন্ট)" else "🔄 Switch Portal (Staff / Merchant)",
                        color = DarkAccentGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }
            }
        }

        // Customer Profile Details Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, SleekBorderColor),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isBengali) "কনজিউমার প্রোফাইল বিবরণ" else "Consumer Profile Details",
                    color = DarkAccentGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                DetailRow(label = if (isBengali) "সম্পূর্ণ নাম (Name)" else "Name", value = currentUser.ownerName, isBengali = isBengali)
                DetailRow(label = if (isBengali) "মোবাইল নম্বর" else "Phone Number", value = currentUser.phone, isBengali = isBengali)
                DetailRow(
                    label = if (isBengali) "লোকেশন" else "Location",
                    value = if (currentUser.isLocationSet) currentUser.address.ifBlank { "${currentUser.ward}, ${currentUser.neighborhood}" } else (if (isBengali) "লোকেশন সেট করা হয়নি" else "Location not set"),
                    isBengali = isBengali
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Dedicated Location Settings Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.2.dp, if (currentUser.isLocationSet) SuccessColor.copy(alpha = 0.6f) else DarkAccentGold),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (currentUser.isLocationSet) SuccessColor.copy(alpha = 0.15f) else DarkAccentGold.copy(alpha = 0.15f),
                                    RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (currentUser.isLocationSet) Icons.Filled.LocationOn else Icons.Filled.LocationOff,
                                contentDescription = "Location",
                                tint = if (currentUser.isLocationSet) SuccessColor else DarkAccentGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isBengali) "আমার লোকেশন সেটিংস" else "My Location Settings",
                                color = DarkTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp
                            )
                            Text(
                                text = if (currentUser.isLocationSet) {
                                    if (isBengali) "লোকেশন সক্রিয় আছে" else "Location is set"
                                } else {
                                    if (isBengali) "লোকেশন সেট করা হয়নি" else "Location not set"
                                },
                                color = if (currentUser.isLocationSet) SuccessColor else DarkAccentGold,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (currentUser.isLocationSet) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkBg.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            if (currentUser.address.isNotBlank()) {
                                Text(
                                    text = "${if (isBengali) "সংরক্ষিত ঠিকানা: " else "Saved Address: "}${currentUser.address}",
                                    color = DarkTextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (currentUser.ward.isNotBlank() || currentUser.neighborhood.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${if (isBengali) "এলাকা: " else "Area: "}${currentUser.ward} • ${currentUser.neighborhood}",
                                    color = DarkTextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                } else {
                    Text(
                        text = if (isBengali) 
                            "একাউন্টে নিজে থেকে লোকেশন সেট না করা পর্যন্ত কোনো ডিফল্ট বা অন্য কোনো লোকেশন দেখানো হবে না।" 
                        else 
                            "No default location will be used until you set your own location.",
                        color = DarkTextSecondary,
                        fontSize = 11.5.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showLocationSettingsDialog = true },
                        modifier = Modifier.weight(1f).height(40.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentUser.isLocationSet) DarkSurface else DarkAccentGold,
                            contentColor = if (currentUser.isLocationSet) Color.White else Color.Black
                        ),
                        border = if (currentUser.isLocationSet) BorderStroke(1.dp, DarkAccentGold) else null,
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Filled.EditLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (currentUser.isLocationSet) {
                                if (isBengali) "লোকেশন পরিবর্তন করুন" else "Change Location"
                            } else {
                                if (isBengali) "লোকেশন সেট করুন" else "Set Location"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    if (currentUser.isLocationSet) {
                        OutlinedButton(
                            onClick = {
                                viewModel.clearCustomerLocation()
                            },
                            modifier = Modifier.height(40.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkAccentRed),
                            border = BorderStroke(1.dp, DarkAccentRed.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp)
                        ) {
                            Text(if (isBengali) "রিসেট / মুছুন" else "Reset", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Language toggle controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isBengali) "ভাষা সিলেক্ট করুন:" else "Select Language:",
                color = DarkTextPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
            Row {
                Button(
                    onClick = { viewModel.setLanguage(true) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBengali) DarkAccentRed else DarkSurface,
                        contentColor = if (isBengali) DarkAccentText else DarkTextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("বাংলা", fontSize = 12.sp, fontWeight = if (isBengali) FontWeight.Bold else FontWeight.Normal)
                }
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = { viewModel.setLanguage(false) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isBengali) DarkAccentRed else DarkSurface,
                        contentColor = if (!isBengali) DarkAccentText else DarkTextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("English", fontSize = 12.sp, fontWeight = if (!isBengali) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Theme toggle controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isBengali) "থিম সিলেক্ট করুন:" else "Select Theme:",
                color = DarkTextPrimary,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
            Row {
                Button(
                    onClick = { viewModel.setDarkMode(false) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isDarkModeGlobal) DarkAccentRed else DarkSurface,
                        contentColor = if (!isDarkModeGlobal) DarkAccentText else DarkTextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isBengali) "লাইট" else "Light",
                        fontSize = 12.sp,
                        fontWeight = if (!isDarkModeGlobal) FontWeight.Bold else FontWeight.Normal
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = { viewModel.setDarkMode(true) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDarkModeGlobal) DarkAccentRed else DarkSurface,
                        contentColor = if (isDarkModeGlobal) DarkAccentText else DarkTextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isBengali) "ডার্ক" else "Dark",
                        fontSize = 12.sp,
                        fontWeight = if (isDarkModeGlobal) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Portal Switch Button
        Button(
            onClick = { showPortalSwitchDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.2.dp, DarkAccentGold)
        ) {
            Icon(Icons.Default.SwapHoriz, "switch portal", tint = DarkAccentGold)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isBengali) "🔄 পোর্টাল পরিবর্তন (মার্চেন্ট / স্টাফ / কনজিউমার)" else "🔄 Switch Portal (Merchant / Staff / Consumer)",
                color = DarkAccentGold,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Edit Profile Button
        Button(
            onClick = { showEditProfile = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, SleekBorderColor)
        ) {
            Icon(Icons.Filled.Edit, "edit profile", tint = DarkTextPrimary)
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (isBengali) "প্রোফাইল পরিবর্তন" else "Change Profile", color = DarkTextPrimary, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Logout Button
        Button(
            onClick = { viewModel.logout() },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DarkAccentRed, contentColor = DarkAccentText),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (isBengali) "লগআউট করুন" else "Log Out", fontWeight = FontWeight.Bold, color = DarkAccentText)
        }

        Spacer(modifier = Modifier.height(32.dp))
        PoweredByRoFooter(
            isBengali = isBengali,
            badgeHeight = 18.dp,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )
    }

    if (showEditProfile) {
        CustomerEditProfileDialog(
            viewModel = viewModel,
            isBengali = isBengali,
            onDismiss = { showEditProfile = false }
        )
    }

    if (showPortalSwitchDialog) {
        NikotPortalSwitchDialog(
            viewModel = viewModel,
            isBengali = isBengali,
            onDismiss = { showPortalSwitchDialog = false }
        )
    }

    if (showLocationSettingsDialog) {
        CustomerLocationSettingsDialog(
            viewModel = viewModel,
            isBengali = isBengali,
            onDismiss = { showLocationSettingsDialog = false }
        )
    }
}

