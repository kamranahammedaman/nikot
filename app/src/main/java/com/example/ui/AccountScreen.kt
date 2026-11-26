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
fun AccountScreen(viewModel: PharmacyViewModel, isBengali: Boolean) {
    val context = LocalContext.current
    val pName by viewModel.pharmacyName.collectAsState()
    val pAddr by viewModel.pharmacyAddress.collectAsState()
    val pPhone by viewModel.pharmacyPhone.collectAsState()
    val ownerName by viewModel.ownerName.collectAsState()
    val password by viewModel.ownerPassword.collectAsState()
    val lat by viewModel.mapLatitude.collectAsState()
    val lng by viewModel.mapLongitude.collectAsState()
    val openTime by viewModel.pharmacyOpenTime.collectAsState()
    val closeTime by viewModel.pharmacyCloseTime.collectAsState()

    val currentUser by viewModel.currentUser.collectAsState()
    val allProfiles by viewModel.userProfiles.collectAsState()
    val permissionRequests by viewModel.permissionRequests.collectAsState()

    val allBills by viewModel.allBills.collectAsState()
    val pendingStockIns by viewModel.allPendingStockIns.collectAsState()
    val pendingMedicines by viewModel.allPendingMedicines.collectAsState()

    var showEditProfile by remember { mutableStateOf(false) }
    var showMapPicker by remember { mutableStateOf(false) }

    // Merchant Staff Option State
    var showStaffDialog by remember { mutableStateOf(false) }
    var showAdminDisconnectWarning by remember { mutableStateOf(false) }
    var staffToDisconnect by remember { mutableStateOf<PharmacyViewModel.UserProfile?>(null) }
    var showAddStaffMemberDialog by remember { mutableStateOf(false) }
    var newStaffName by remember { mutableStateOf("") }
    var newStaffUsername by remember { mutableStateOf("") }
    var newStaffPhone by remember { mutableStateOf("") }
    var newStaffPass by remember { mutableStateOf("") }
    var newStaffSecCode by remember { mutableStateOf("") }
    var addStaffError by remember { mutableStateOf("") }

    // Staff Pharmacy Add Option State
    var showStaffAddPharmacyDialog by remember { mutableStateOf(false) }
    var showPharmacyConnectionDialog by remember { mutableStateOf(false) }
    var showStaffRequestDialog by remember { mutableStateOf(false) }
    var showStaffDisconnectWarning by remember { mutableStateOf(false) }
    var staffReqPharmacyName by remember { mutableStateOf("") }
    var staffReqOwnerName by remember { mutableStateOf("") }
    var staffReqSecurityCode by remember { mutableStateOf("") }
    var showPortalSwitchDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoLogoHeader()

            Row(verticalAlignment = Alignment.CenterVertically) {

                if (viewModel.isMerchant(currentUser)) {
                    Button(
                        onClick = { showStaffDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkSurface,
                            contentColor = DarkAccentGold
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, DarkAccentGold.copy(alpha = 0.3f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = "Staff Members",
                            modifier = Modifier.size(16.dp),
                            tint = DarkAccentGold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isBengali) "স্টাফ" else "Staff",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkTextPrimary
                        )
                    }
                } else if (currentUser.role == "staff") {
                    Button(
                        onClick = { showStaffAddPharmacyDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DarkSurface,
                            contentColor = SuccessColor
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, SuccessColor.copy(alpha = 0.3f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalPharmacy,
                            contentDescription = "Pharmacy Link",
                            modifier = Modifier.size(16.dp),
                            tint = SuccessColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isBengali) "ফার্মেসী এড" else "Pharmacy Add",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkTextPrimary
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

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
                            text = if (isBengali) "কনজিউমার, স্টাফ বা মার্চেন্ট পোর্টালে যেতে ট্যাপ করুন" else "Switch to Consumer, Staff, or Merchant portal",
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
                        text = if (isBengali) "🔄 পোর্টাল পরিবর্তন করুন" else "🔄 Switch Portal",
                        color = DarkAccentGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }
            }
        }

        // Staff Pharmacy Status and Add Card
        if (currentUser.role == "staff") {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(
                    1.2.dp,
                    if (currentUser.linkedAdminUsername.isNotBlank()) SuccessColor else DarkAccentGold
                ),
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
                                .background(
                                    (if (currentUser.linkedAdminUsername.isNotBlank()) SuccessColor else DarkAccentGold).copy(alpha = 0.15f),
                                    RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (currentUser.linkedAdminUsername.isNotBlank()) Icons.Default.CheckCircle else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (currentUser.linkedAdminUsername.isNotBlank()) SuccessColor else DarkAccentGold,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isBengali) "ফার্মেসী সংযোগ স্ট্যাটাস" else "Pharmacy Connection Status",
                                color = DarkTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = if (currentUser.linkedAdminUsername.isNotBlank()) {
                                    if (isBengali) "সংযুক্ত: ${currentUser.pharmacyName} (সকল পেইজে পূর্ণ এক্সেস সক্রিয়)" else "Linked: ${currentUser.pharmacyName} (Full access active)"
                                } else {
                                    if (isBengali) "কোনো ফার্মেসী যুক্ত নেই — পেইজগুলো নিষ্ক্রিয়" else "No pharmacy linked — pages are inactive"
                                },
                                color = if (currentUser.linkedAdminUsername.isNotBlank()) Color(0xFF81C784) else DarkAccentGold,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    if (currentUser.linkedAdminUsername.isBlank()) {
                        Button(
                            onClick = { showStaffAddPharmacyDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessColor),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Storefront, "add pharmacy", tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBengali) "➕ ফার্মেসী এড করুন" else "➕ Add Pharmacy",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                        }
                    } else {
                        Button(
                            onClick = { showStaffDisconnectWarning = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DarkAccentRed.copy(alpha = 0.15f),
                                contentColor = DarkAccentRed
                            ),
                            border = BorderStroke(1.dp, DarkAccentRed.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Close, "disconnect pharmacy", tint = DarkAccentRed, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBengali) "ফার্মেসী পরিবর্তন / সংযোগ বিচ্ছিন্ন করুন" else "Change / Disconnect Pharmacy",
                                color = DarkAccentRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        // Profile brief
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(pName, color = DarkTextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                    val roleLabel = when {
                        viewModel.isMerchant(currentUser) -> if (isBengali) "মার্চেন্ট" else "Merchant"
                        currentUser.role == "staff" -> if (isBengali) "স্টাফ" else "Staff"
                        else -> if (isBengali) "কনজিউমার" else "Consumer"
                    }
                    val roleBadgeColor = when {
                        viewModel.isMerchant(currentUser) -> DarkAccentRed
                        currentUser.role == "staff" -> SuccessColor
                        else -> InfoColor
                    }
                    Box(
                        modifier = Modifier
                            .background(roleBadgeColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                            .border(1.dp, roleBadgeColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = roleLabel,
                            color = roleBadgeColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(pAddr, color = DarkTextSecondary, fontSize = 12.sp)
                Text(pPhone, color = DarkTextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.AccessTime,
                        contentDescription = "Hours",
                        tint = DarkAccentGold,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isBengali) "খোলা: $openTime — বন্ধ: $closeTime" else "Open: $openTime — Close: $closeTime",
                        color = DarkTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = Color.LightGray.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(12.dp))
                Text("${if (isBengali) "মালিকের নাম: " else "Owner Name: "}$ownerName", color = DarkTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                val displaySecCode = currentUser.securityCode.ifBlank { viewModel.generateUniqueSecurityCode(currentUser.role) }
                Text("${if (isBengali) "সিকিউরিটি কোড: " else "Security Code: "}$displaySecCode", color = DarkAccentGold, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Logo configuration section
        val logoBase64 = LocalUploadedLogo.current
        val imagePickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                viewModel.uploadLogoFromUri(uri)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Image, "logo_settings", tint = DarkAccentRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isBengali) "লগো কনফিগারেশন" else "Logo Settings",
                        color = DarkTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = if (isBengali) "বর্তমান লগো:" else "Current Logo:",
                            color = DarkTextSecondary,
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        RoLogoHeader(height = 36.dp)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { imagePickerLauncher.launch("image/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkAccentRed, contentColor = DarkAccentText)
                        ) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = "Upload")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isBengali) "লোগো আপলোড করুন" else "Upload Logo",
                                fontSize = 12.sp,
                                color = DarkAccentText
                            )
                        }

                        if (logoBase64 != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { viewModel.removeLogo() }
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete logo", tint = Color.Red)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Coordinates visual indicators
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.MyLocation, "gps", tint = DarkAccentRed)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isBengali) "📍 জিপিএস কোঅর্ডিনেট" else "📍 GPS Location Coordinates", color = DarkTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Latitude: $lat", color = DarkTextSecondary, fontSize = 12.sp)
                Text("Longitude: $lng", color = DarkTextSecondary, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { showMapPicker = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkAccentRed, contentColor = DarkAccentText)
                ) {
                    Text(if (isBengali) "Google Map লোকেশন ও পিন সিলেক্ট করুন" else "Pick Location on Map", color = DarkAccentText)
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
            Text(if (isBengali) "ভাষা সিলেক্ট করুন:" else "Select Language:", color = DarkTextPrimary, fontWeight = FontWeight.Bold)
            Row {
                Button(
                    onClick = { viewModel.setLanguage(true) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBengali) DarkAccentRed else DarkSurface,
                        contentColor = if (isBengali) DarkAccentText else DarkTextPrimary
                    )
                ) {
                    Text("বাংলা", color = if (isBengali) DarkAccentText else DarkTextPrimary)
                }
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = { viewModel.setLanguage(false) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isBengali) DarkAccentRed else DarkSurface,
                        contentColor = if (!isBengali) DarkAccentText else DarkTextPrimary
                    )
                ) {
                    Text("English", color = if (!isBengali) DarkAccentText else DarkTextPrimary)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Theme toggle controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (isBengali) "থিম সিলেক্ট করুন:" else "Select Theme:", color = DarkTextPrimary, fontWeight = FontWeight.Bold)
            Row {
                Button(
                    onClick = { viewModel.setDarkMode(false) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (!isDarkModeGlobal) DarkAccentRed else DarkSurface,
                        contentColor = if (!isDarkModeGlobal) DarkAccentText else DarkTextPrimary
                    )
                ) {
                    Text(
                        text = if (isBengali) "লাইট থিম" else "Light Theme",
                        color = if (!isDarkModeGlobal) DarkAccentText else DarkTextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = { viewModel.setDarkMode(true) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDarkModeGlobal) DarkAccentRed else DarkSurface,
                        contentColor = if (isDarkModeGlobal) DarkAccentText else DarkTextPrimary
                    )
                ) {
                    Text(
                        text = if (isBengali) "ব্ল্যাক থিম" else "Black Theme",
                        color = if (isDarkModeGlobal) DarkAccentText else DarkTextPrimary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Action Button edit profile
        Button(
            onClick = { showEditProfile = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DarkSurface)
        ) {
            Icon(Icons.Filled.Edit, "edit profile")
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (isBengali) "মালিক ও ফার্মেসির বিবরণ পরিবর্তন" else "Change Profile & Password", color = DarkTextPrimary)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Spacer(modifier = Modifier.height(8.dp))

        // Logout Action trigger
        Button(
            onClick = { viewModel.logout() },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            colors = ButtonDefaults.buttonColors(containerColor = DarkAccentRed, contentColor = DarkAccentText)
        ) {
            Text(if (isBengali) "লগআউট বাটন" else "Log Out", fontWeight = FontWeight.Bold, color = DarkAccentText)
        }

        Spacer(modifier = Modifier.height(32.dp))
        PoweredByRoFooter(
            isBengali = isBengali,
            badgeHeight = 18.dp,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )
    }

    if (showStaffDialog) {
        val connectedStaff = allProfiles.values.filter { it.role == "staff" && it.linkedAdminUsername == currentUser.username }
        var selectedStaffForDetails by remember { mutableStateOf<PharmacyViewModel.UserProfile?>(null) }
        
        Dialog(onDismissRequest = { showStaffDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, SuccessColor.copy(alpha = 0.4f))
            ) {
                if (selectedStaffForDetails == null) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = "Connected Staff",
                                    tint = SuccessColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isBengali) "সংযুক্ত স্টাফ সদস্যবৃন্দ (${formatBengaliNumber(connectedStaff.size)})" else "Connected Staff Members (${connectedStaff.size})",
                                    color = DarkTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                            IconButton(onClick = { showStaffDialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = DarkTextPrimary)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        // Button to add staff directly
                        Button(
                            onClick = {
                                newStaffName = ""
                                newStaffUsername = ""
                                newStaffPhone = ""
                                newStaffPass = ""
                                newStaffSecCode = viewModel.generateUniqueSecurityCode("staff")
                                addStaffError = ""
                                showAddStaffMemberDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SuccessColor,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(42.dp)
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBengali) "+ নতুন স্টাফ সদস্য যোগ করুন" else "+ Add New Staff Member",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val pendingForThisMerchant = permissionRequests.filter { it.adminUsername == currentUser.username && it.status == "Pending" }
                        if (pendingForThisMerchant.isNotEmpty()) {
                            Text(
                                text = if (isBengali) "অনুমোদন অপেক্ষমাণ আবেদনসমূহ (${formatBengaliNumber(pendingForThisMerchant.size)}):" else "Pending Join Requests (${pendingForThisMerchant.size}):",
                                color = DarkAccentGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            pendingForThisMerchant.forEach { req ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = DarkBg),
                                    border = BorderStroke(1.dp, DarkAccentGold.copy(alpha = 0.4f))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = "${if (isBengali) "স্টাফ ইউজারনেম:" else "Staff:"} @${req.staffUsername}",
                                            color = DarkTextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        if (req.staffPhone.isNotBlank()) {
                                            Text(
                                                text = "${if (isBengali) "ফোন:" else "Phone:"} ${req.staffPhone}",
                                                color = DarkTextSecondary,
                                                fontSize = 11.5.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextButton(
                                                onClick = { viewModel.rejectPermissionRequest(req) }
                                            ) {
                                                Text(if (isBengali) "প্রত্যাখ্যান" else "Reject", color = DarkAccentRed, fontSize = 12.sp)
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Button(
                                                onClick = { viewModel.approvePermissionRequest(req) },
                                                colors = ButtonDefaults.buttonColors(containerColor = SuccessColor, contentColor = Color.White),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                            ) {
                                                Text(if (isBengali) "অনুমোদন করুন" else "Approve", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Divider(color = Color.White.copy(alpha = 0.08f))
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (connectedStaff.isEmpty()) {
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (isBengali) "কোনো স্টাফ সদস্য সংযুক্ত নেই।" else "No staff members linked.",
                                    color = DarkTextSecondary,
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(connectedStaff) { staff ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable { selectedStaffForDetails = staff },
                                        colors = CardDefaults.cardColors(containerColor = DarkBg),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(0.6f)) {
                                                Text(
                                                    text = staff.ownerName.ifBlank { staff.username },
                                                    color = DarkTextPrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = "${if (isBengali) "ইউজারনেম:" else "Username:"} @${staff.username}",
                                                    color = DarkTextSecondary,
                                                    fontSize = 12.sp
                                                )
                                                Text(
                                                    text = "${if (isBengali) "ফোন:" else "Phone:"} ${staff.phone}",
                                                    color = DarkTextSecondary,
                                                    fontSize = 12.sp
                                                )
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.ChevronRight,
                                                    contentDescription = "View Details",
                                                    tint = DarkTextSecondary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                
                                                Button(
                                                    onClick = {
                                                        staffToDisconnect = staff
                                                        showAdminDisconnectWarning = true
                                                    },
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = DarkAccentRed.copy(alpha = 0.15f),
                                                        contentColor = DarkAccentRed
                                                    ),
                                                    shape = RoundedCornerShape(8.dp),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Close,
                                                        contentDescription = "Remove Staff",
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val staff = selectedStaffForDetails!!
                    val staffBills = allBills.filter { it.staffUsername == staff.username }
                    val staffPendingStockIns = pendingStockIns.filter { it.staffUsername == staff.username }
                    val staffPendingMeds = pendingMedicines.filter { it.staffUsername == staff.username }

                    val todayBillsForStaff = staffBills.filter { isToday(it.timestamp) }
                    val totalMoneyToday = todayBillsForStaff.sumOf { it.totalAmount }
                    val totalBillsCountToday = todayBillsForStaff.size

                    val activities = remember(staffBills, staffPendingStockIns, staffPendingMeds) {
                        val list = mutableListOf<ActivityLog>()
                        staffBills.forEach { list.add(ActivityLog.Sale(it)) }
                        staffPendingStockIns.forEach { list.add(ActivityLog.StockIn(it)) }
                        staffPendingMeds.forEach { list.add(ActivityLog.NewMed(it)) }
                        list.sortedByDescending { it.timestamp }
                    }

                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { selectedStaffForDetails = null },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowBack,
                                        contentDescription = "Back",
                                        tint = DarkTextPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isBengali) "স্টাফ কার্যক্রম ও নিরীক্ষা" else "Staff Audit & Logs",
                                    color = DarkTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                            IconButton(onClick = { showStaffDialog = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = DarkTextPrimary)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = DarkBg),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = staff.ownerName.ifBlank { staff.username },
                                    color = DarkTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "${if (isBengali) "ইউজারনেম:" else "Username:"} @${staff.username} | ${if (isBengali) "ফোন:" else "Phone:"} ${staff.phone}",
                                    color = DarkTextSecondary,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = if (isBengali) "আজকের মোট বিক্রয়" else "Today's Total Sales",
                                            color = DarkTextSecondary,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = "৳${formatBengaliNumber(String.format("%.1f", totalMoneyToday))}",
                                            color = SuccessColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = if (isBengali) "আজকের মোট মেমো" else "Today's Invoices",
                                            color = DarkTextSecondary,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = "${formatBengaliNumber(totalBillsCountToday)} টি",
                                            color = DarkAccentGold,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (isBengali) "আজকের সকল কাজ ও ইতিহাস" else "All Actions & History Today",
                            color = DarkTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (activities.isEmpty()) {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isBengali) "আজ কোনো কার্যক্রম নেই।" else "No activities recorded today.",
                                    color = DarkTextSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(activities) { log ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = DarkBg.copy(alpha = 0.5f)),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.03f))
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(0.7f)
                                                ) {
                                                    val (icon, tint, title) = when (log) {
                                                        is ActivityLog.Sale -> Triple(Icons.Default.ShoppingCart, SuccessColor, if (isBengali) "ওষুধ বিক্রয় (বিল #${log.bill.billNumber})" else "Medicine Sale (Bill #${log.bill.billNumber})")
                                                        is ActivityLog.StockIn -> Triple(Icons.Default.QueryBuilder, WarningColor, if (isBengali) "স্টক ইন অনুরোধ (অপেক্ষমাণ)" else "Stock-In Request (Pending)")
                                                        is ActivityLog.NewMed -> Triple(Icons.Default.AddCircle, InfoColor, if (isBengali) "নতুন ওষুধ অনুরোধ (অপেক্ষমাণ)" else "New Medicine Request (Pending)")
                                                    }
                                                    Icon(
                                                        imageVector = icon,
                                                        contentDescription = null,
                                                        tint = tint,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = title,
                                                        color = DarkTextPrimary,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 12.sp,
                                                        maxLines = 1
                                                    )
                                                }
                                                Text(
                                                    text = formatDateTime(log.timestamp),
                                                    color = DarkTextSecondary,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.weight(0.3f),
                                                    textAlign = TextAlign.End
                                                )
                                            }
                                            
                                            Spacer(modifier = Modifier.height(4.dp))
                                            
                                            when (log) {
                                                is ActivityLog.Sale -> {
                                                    val itemsList = viewModel.getBillItemsList(log.bill)
                                                    val itemsText = itemsList.joinToString(", ") { "${it.name} (${it.formatDescription})" }
                                                    Text(
                                                        text = itemsText,
                                                        color = DarkTextSecondary,
                                                        fontSize = 11.sp
                                                    )
                                                    Text(
                                                        text = "${if (isBengali) "মোট বিক্রয়মূল্য: ৳" else "Total Amount: ৳"}${formatBengaliNumber(String.format("%.1f", log.bill.totalAmount))}",
                                                        color = SuccessColor,
                                                        fontWeight = FontWeight.Medium,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                                is ActivityLog.StockIn -> {
                                                    val pending = log.pending
                                                    val qtyStr = when (pending.medicineType) {
                                                        "tablet" -> {
                                                            val parts = mutableListOf<String>()
                                                            if (pending.addedBoxes > 0) parts.add(if (isBengali) "${pending.addedBoxes} বক্স" else "${pending.addedBoxes} Box")
                                                            if (pending.addedStrips > 0) parts.add(if (isBengali) "${pending.addedStrips} পাতা" else "${pending.addedStrips} Strip")
                                                            if (pending.addedPcs > 0) parts.add(if (isBengali) "${pending.addedPcs} পিস" else "${pending.addedPcs} Pcs")
                                                            parts.joinToString(" ")
                                                        }
                                                        "syrup" -> if (isBengali) "${pending.addedBottles} বোতল" else "${pending.addedBottles} Bottle"
                                                        "ointment" -> if (isBengali) "${pending.addedTubes} টিউব" else "${pending.addedTubes} Tube"
                                                        "injection" -> if (isBengali) "${pending.addedVials} ভায়াল" else "${pending.addedVials} Vial"
                                                        else -> if (isBengali) "${pending.addedOtherQty} পিস" else "${pending.addedOtherQty} Pcs"
                                                    }
                                                    Text(
                                                        text = "${if (isBengali) "ওষুধ:" else "Medicine:"} ${pending.medicineName}",
                                                        color = DarkTextSecondary,
                                                        fontSize = 11.sp
                                                    )
                                                    Text(
                                                        text = "${if (isBengali) "পরিমাণ:" else "Quantity:"} $qtyStr",
                                                        color = DarkAccentGold,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                                is ActivityLog.NewMed -> {
                                                    val pending = log.pending
                                                    Text(
                                                        text = "${if (isBengali) "ওষুধ:" else "Medicine:"} ${pending.name} (${pending.dosageStrength})",
                                                        color = DarkTextSecondary,
                                                        fontSize = 11.sp
                                                    )
                                                    Text(
                                                        text = "${if (isBengali) "কোম্পানি:" else "Company:"} ${pending.company} | ${if (isBengali) "জেনেরিক:" else "Generic:"} ${pending.genericName}",
                                                        color = DarkTextSecondary,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddStaffMemberDialog) {
        Dialog(onDismissRequest = { showAddStaffMemberDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, SuccessColor.copy(alpha = 0.6f))
            ) {
                Column(
                    modifier = Modifier
                        .padding(18.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.PersonAdd,
                                contentDescription = null,
                                tint = SuccessColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBengali) "নতুন স্টাফ যুক্ত করুন" else "Add New Staff Member",
                                color = DarkTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        IconButton(
                            onClick = { showAddStaffMemberDialog = false },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = DarkTextSecondary)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = newStaffName,
                        onValueChange = { newStaffName = it; addStaffError = "" },
                        label = { Text(if (isBengali) "স্টাফের পূর্ণ নাম" else "Staff Full Name") },
                        placeholder = { Text(if (isBengali) "যেমন: মোঃ রহিম উদ্দিন" else "e.g. Md Rahim", color = DarkTextSecondary.copy(alpha = 0.4f)) },
                        leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = SuccessColor) },
                        textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SuccessColor,
                            unfocusedBorderColor = SleekBorderColor,
                            focusedLabelColor = SuccessColor,
                            unfocusedLabelColor = DarkTextSecondary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = newStaffUsername,
                        onValueChange = { newStaffUsername = it.filter { c -> c.isLetterOrDigit() || c == '_' }; addStaffError = "" },
                        label = { Text(if (isBengali) "লগইন ইউজারনেম (Username)" else "Login Username") },
                        placeholder = { Text("e.g. rahim_staff", color = DarkTextSecondary.copy(alpha = 0.4f)) },
                        leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null, tint = SuccessColor) },
                        textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SuccessColor,
                            unfocusedBorderColor = SleekBorderColor,
                            focusedLabelColor = SuccessColor,
                            unfocusedLabelColor = DarkTextSecondary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = newStaffPhone,
                        onValueChange = { newStaffPhone = it; addStaffError = "" },
                        label = { Text(if (isBengali) "মোবাইল নম্বর" else "Phone Number") },
                        placeholder = { Text("01700-000000", color = DarkTextSecondary.copy(alpha = 0.4f)) },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = SuccessColor) },
                        textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SuccessColor,
                            unfocusedBorderColor = SleekBorderColor,
                            focusedLabelColor = SuccessColor,
                            unfocusedLabelColor = DarkTextSecondary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = newStaffPass,
                        onValueChange = { newStaffPass = it; addStaffError = "" },
                        label = { Text(if (isBengali) "লগইন পাসওয়ার্ড" else "Login Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = SuccessColor) },
                        textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SuccessColor,
                            unfocusedBorderColor = SleekBorderColor,
                            focusedLabelColor = SuccessColor,
                            unfocusedLabelColor = DarkTextSecondary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    OutlinedTextField(
                        value = newStaffSecCode,
                        onValueChange = { newStaffSecCode = it; addStaffError = "" },
                        label = { Text(if (isBengali) "স্টাফ সিকিউরিটি কোড (আলাদা কোড)" else "Staff Security Code") },
                        leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null, tint = DarkAccentGold) },
                        textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DarkAccentGold,
                            unfocusedBorderColor = SleekBorderColor,
                            focusedLabelColor = DarkAccentGold,
                            unfocusedLabelColor = DarkTextSecondary
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    if (addStaffError.isNotEmpty()) {
                        Text(
                            text = addStaffError,
                            color = DarkAccentRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAddStaffMemberDialog = false }) {
                            Text(if (isBengali) "বাতিল" else "Cancel", color = DarkTextSecondary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newStaffName.trim().isEmpty() || newStaffUsername.trim().isEmpty() || newStaffPass.trim().isEmpty()) {
                                    addStaffError = if (isBengali) "অনুগ্রহ করে নাম, ইউজারনেম ও পাসওয়ার্ড পূরণ করুন" else "Please enter name, username, and password"
                                    return@Button
                                }
                                viewModel.addStaffMember(
                                    name = newStaffName.trim(),
                                    username = newStaffUsername.trim(),
                                    phone = newStaffPhone.trim(),
                                    pass = newStaffPass.trim(),
                                    customSecurityCode = newStaffSecCode.trim().ifEmpty { null }
                                ) { success, msg ->
                                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                                    if (success) {
                                        showAddStaffMemberDialog = false
                                    } else {
                                        addStaffError = msg
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessColor, contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isBengali) "স্টাফ যুক্ত করুন" else "Add Staff", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showAdminDisconnectWarning && staffToDisconnect != null) {
        AlertDialog(
            onDismissRequest = {
                showAdminDisconnectWarning = false
                staffToDisconnect = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = DarkAccentRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isBengali) "স্টাফ অপসারণের সতর্কতা!" else "Remove Staff Warning!",
                        color = DarkTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Text(
                    text = if (isBengali) {
                        "আপনি কি নিশ্চিত যে আপনি স্টাফ সদস্য \"${staffToDisconnect?.ownerName?.ifBlank { staffToDisconnect?.username ?: "" }}\" কে আপনার ফার্মেসী থেকে অপসারণ করতে চান?\n\n⚠️ এর ফলে তিনি আর আপনার ফার্মেসীর স্টক, বিক্রয় ও রিপোর্ট অ্যাক্সেস করতে পারবেন না।"
                    } else {
                        "Are you sure you want to remove staff member \"${staffToDisconnect?.ownerName?.ifBlank { staffToDisconnect?.username ?: "" }}\" from your pharmacy?\n\n⚠️ As a result, they will no longer be able to access your pharmacy's stock, sales, and reports."
                    },
                    color = DarkTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        staffToDisconnect?.username?.let { username ->
                            viewModel.disconnectStaff(username)
                        }
                        showAdminDisconnectWarning = false
                        staffToDisconnect = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkAccentRed, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (isBengali) "অপসারণ নিশ্চিত করুন" else "Confirm Removal", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAdminDisconnectWarning = false
                        staffToDisconnect = null
                    }
                ) {
                    Text(if (isBengali) "বাতিল" else "Cancel", color = DarkTextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showStaffAddPharmacyDialog) {
        StaffAddPharmacyDialog(
            viewModel = viewModel,
            isBengali = isBengali,
            onDismiss = { showStaffAddPharmacyDialog = false }
        )
    }

    if (showPharmacyConnectionDialog) {
        val myRequests = permissionRequests.filter { it.staffUsername == currentUser.username }
        Dialog(onDismissRequest = { showPharmacyConnectionDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (currentUser.linkedAdminUsername.isNotEmpty()) SuccessColor.copy(alpha = 0.4f) else DarkAccentGold.copy(alpha = 0.4f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (currentUser.linkedAdminUsername.isNotEmpty()) Icons.Filled.CheckCircle else Icons.Filled.Info,
                                contentDescription = "Connection Status",
                                tint = if (currentUser.linkedAdminUsername.isNotEmpty()) SuccessColor else DarkAccentGold,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBengali) "ফার্মেসী সংযোগ স্ট্যাটাস" else "Pharmacy Connection Status",
                                color = DarkTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                        IconButton(onClick = { showPharmacyConnectionDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = DarkTextPrimary)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    if (currentUser.linkedAdminUsername.isNotEmpty()) {
                        Text(
                            text = if (isBengali) {
                                "আপনি সফলভাবে **${currentUser.pharmacyName}** (মালিক: ${currentUser.ownerName}) এর সাথে যুক্ত আছেন।"
                            } else {
                                "You are successfully linked to **${currentUser.pharmacyName}** (Owner: ${currentUser.ownerName})."
                            },
                            color = DarkTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isBengali) {
                                "এখন আপনি এই ফার্মেসীর স্টক, বিক্রয় ও রিপোর্ট পুরোপুরি নিয়ন্ত্রণ করতে পারবেন।"
                            } else {
                                "You can now fully manage stock, sales, and reports for this pharmacy."
                            },
                            color = DarkTextSecondary,
                            fontSize = 12.sp
                        )
                    } else {
                        Text(
                            text = if (isBengali) {
                                "আপনি বর্তমানে কোনো মার্চেন্ট ফার্মেসীর সাথে যুক্ত নন। ফার্মেসীর নাম ও সিকিউরিটি কোড দিয়ে যুক্ত হয়ে সকল পেইজের পূর্ণ এক্সেস নিন।"
                            } else {
                                "You are not currently linked to any merchant pharmacy. Connect with pharmacy name and security code to gain full access."
                            },
                            color = DarkTextSecondary,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (currentUser.linkedAdminUsername.isEmpty()) {
                        Button(
                            onClick = {
                                showPharmacyConnectionDialog = false
                                showStaffAddPharmacyDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SuccessColor,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Storefront,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBengali) "ফার্মেসী এড করুন" else "Add Pharmacy",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SuccessColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .border(1.dp, SuccessColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Column {
                                Text(
                                    text = if (isBengali) {
                                        "🔒 আপনি ইতিমধ্যে একটি সক্রিয় সংযোগে আছেন। অন্য ফার্মেসীর সাথে যুক্ত হতে পারবেন না।"
                                    } else {
                                        "🔒 You are currently in an active connection. You cannot link with another pharmacy simultaneously."
                                    },
                                    color = Color(0xFF81C784),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = { showStaffDisconnectWarning = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = DarkAccentRed.copy(alpha = 0.15f),
                                        contentColor = DarkAccentRed
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Exit Pharmacy",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isBengali) "বর্তমান ফার্মেসী থেকে বাহির হোন" else "Exit Current Pharmacy",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    if (myRequests.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isBengali) "আপনার আবেদনসমূহ:" else "Your Requests:",
                            color = DarkTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(myRequests.sortedByDescending { it.timestamp }) { req ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .background(DarkBg, RoundedCornerShape(8.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(0.7f)) {
                                        Text(
                                            text = req.pharmacyName,
                                            color = DarkTextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "${if (isBengali) "মালিক:" else "Owner:"} ${req.ownerName}",
                                            color = DarkTextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    val (statusText, statusColor) = when (req.status) {
                                        "Pending" -> Pair(if (isBengali) "অপেক্ষমাণ" else "Pending", DarkAccentGold)
                                        "Approved" -> Pair(if (isBengali) "অনুমোদিত" else "Approved", SuccessColor)
                                        else -> Pair(if (isBengali) "প্রত্যাখ্যাত" else "Rejected", DarkAccentRed)
                                    }
                                    Text(
                                        text = statusText,
                                        color = statusColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier
                                            .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showStaffRequestDialog) {
        AlertDialog(
            onDismissRequest = { showStaffRequestDialog = false },
            title = {
                Text(
                    text = if (isBengali) "ফার্মেসী নিয়ন্ত্রণ অনুমতি অনুরোধ" else "Request Pharmacy Control Access",
                    color = DarkTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (isBengali) "যে ফার্মেসীটি আপনি পরিচালনা করতে চান তার সঠিক নাম, মালিকের নাম ও মার্চেন্টের সিকিউরিটি কোড দিন।" else "Provide the exact Pharmacy Name, Owner Name, and Merchant's Security Code for the pharmacy you wish to manage.",
                        color = DarkTextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    OutlinedTextField(
                        value = staffReqPharmacyName,
                        onValueChange = { staffReqPharmacyName = it },
                        label = { Text(if (isBengali) "ফার্মেসীর নাম" else "Pharmacy Name") },
                        leadingIcon = { Icon(Icons.Default.LocalPharmacy, contentDescription = null, tint = DarkAccentRed) },
                        textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DarkAccentRed,
                            unfocusedBorderColor = SleekBorderColor,
                            focusedLabelColor = DarkAccentRed,
                            unfocusedLabelColor = DarkTextSecondary
                        )
                    )
                    OutlinedTextField(
                        value = staffReqOwnerName,
                        onValueChange = { staffReqOwnerName = it },
                        label = { Text(if (isBengali) "মালিকের নাম" else "Owner Name") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = DarkAccentRed) },
                        textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DarkAccentRed,
                            unfocusedBorderColor = SleekBorderColor,
                            focusedLabelColor = DarkAccentRed,
                            unfocusedLabelColor = DarkTextSecondary
                        )
                    )
                    OutlinedTextField(
                        value = staffReqSecurityCode,
                        onValueChange = { staffReqSecurityCode = it },
                        label = { Text(if (isBengali) "মার্চেন্টের সিকিউরিটি কোড" else "Merchant Security Code") },
                        placeholder = { Text(if (isBengali) "যেমন: #ROPH-XXXX" else "e.g. #ROPH-XXXX", color = DarkTextSecondary.copy(alpha = 0.5f)) },
                        leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null, tint = DarkAccentGold) },
                        textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DarkAccentGold,
                            unfocusedBorderColor = SleekBorderColor,
                            focusedLabelColor = DarkAccentGold,
                            unfocusedLabelColor = DarkTextSecondary
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (staffReqPharmacyName.isNotEmpty() && staffReqOwnerName.isNotEmpty() && staffReqSecurityCode.isNotEmpty()) {
                            viewModel.sendPermissionRequest(
                                staffReqPharmacyName.trim(),
                                staffReqOwnerName.trim(),
                                staffReqSecurityCode.trim()
                            ) { success, msg ->
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                            }
                            showStaffRequestDialog = false
                            staffReqPharmacyName = ""
                            staffReqOwnerName = ""
                            staffReqSecurityCode = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessColor, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (isBengali) "অনুরোধ পাঠান" else "Send Request", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStaffRequestDialog = false }) {
                    Text(if (isBengali) "বাতিল" else "Cancel", color = DarkTextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showStaffDisconnectWarning) {
        AlertDialog(
            onDismissRequest = { showStaffDisconnectWarning = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = DarkAccentRed,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isBengali) "সংযোগ বিচ্ছিন্ন করার সতর্কতা!" else "Disconnect Connection Warning!",
                        color = DarkTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Text(
                    text = if (isBengali) {
                        "আপনি কি নিশ্চিত যে আপনি এই ফার্মেসী থেকে বের হতে চান?\n\n⚠️ এর ফলে আপনি আর এই ফার্মেসীর স্টক, বিক্রয় বা রিপোর্ট পরিচালনা করতে পারবেন না।"
                    } else {
                        "Are you sure you want to disconnect from this pharmacy?\n\n⚠️ As a result, you will lose all access to its stock, sales, and reports."
                    },
                    color = DarkTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.disconnectStaff(currentUser.username)
                        showStaffDisconnectWarning = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkAccentRed, contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (isBengali) "হ্যাঁ, বের হয়ে যান" else "Confirm Exit", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showStaffDisconnectWarning = false }
                ) {
                    Text(if (isBengali) "বাতিল" else "Cancel", color = DarkTextSecondary)
                }
            },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showEditProfile) {
        EditProfileDialog(
            viewModel = viewModel,
            isBengali = isBengali,
            onDismiss = { showEditProfile = false }
        )
    }

    if (showMapPicker) {
        LiveLocationMapDialog(
            isBengali = isBengali,
            currLat = lat,
            currLng = lng,
            onLocationSelected = { selectLat, selectLng ->
                viewModel.updateLatLng(selectLat, selectLng)
                showMapPicker = false
            },
            onDismiss = { showMapPicker = false }
        )
    }

    if (showPortalSwitchDialog) {
        NikotPortalSwitchDialog(
            viewModel = viewModel,
            isBengali = isBengali,
            onDismiss = { showPortalSwitchDialog = false }
        )
    }
}

