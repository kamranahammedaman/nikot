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
fun NikotPortalSwitchDialog(
    viewModel: PharmacyViewModel,
    isBengali: Boolean,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    var selectedCustomization by remember { mutableStateOf<String?>(null) } // null, "admin_custom", "staff_custom"

    var adminPharmacyName by remember { mutableStateOf(currentUser.pharmacyName.ifBlank { "নিকট ফার্মেসী" }) }
    var staffPharmacyName by remember { mutableStateOf(currentUser.pharmacyName.ifBlank { "নিকট ফার্মেসী" }) }
    var staffOwnerName by remember { mutableStateOf(currentUser.ownerName.ifBlank { currentUser.username }) }
    var staffSecurityCode by remember { mutableStateOf(currentUser.securityCode) }
    var errorMsg by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.5.dp, DarkAccentGold),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Logo and Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoLogoHeader(height = 30.dp)
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = DarkTextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (isBengali) "পোর্টাল পরিবর্তন / সুইচ করুন" else "Switch & Enter Portal",
                    color = DarkTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = if (isBengali) "যে কোনো সময় আপনি কনজিউমার, স্টাফ বা মার্চেন্ট পোর্টালে সহজেই যেতে পারবেন" else "You can switch freely between Consumer, Staff, and Merchant portals anytime",
                    color = DarkTextSecondary,
                    fontSize = 11.5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                if (selectedCustomization == null) {
                    // Option 1: Consumer Portal
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clickable {
                                viewModel.switchPortalDirectly("customer")
                                onDismiss()
                                android.widget.Toast.makeText(
                                    context,
                                    if (isBengali) "কনজিউমার পোর্টালে সফলভাবে প্রবেশ করেছেন!" else "Switched to Consumer Portal!",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (currentUser.role == "customer") Color(0xFF0288D1).copy(alpha = 0.15f) else DarkBg
                        ),
                        border = BorderStroke(
                            width = if (currentUser.role == "customer") 1.8.dp else 1.dp,
                            color = if (currentUser.role == "customer") Color(0xFF0288D1) else SleekBorderColor
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(Color(0xFF0288D1).copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ShoppingCart,
                                    contentDescription = "Consumer",
                                    tint = Color(0xFF0288D1),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (isBengali) "কনজিউমার পোর্টাল" else "Consumer Portal",
                                        color = DarkTextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (currentUser.role == "customer") {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isBengali) "● বর্তমান" else "● Active",
                                            color = Color(0xFF0288D1),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    text = if (isBengali) "ওষুধ অনুসন্ধান, কার্ট ও অনলাইন অর্ডার" else "Search meds, cart & online order",
                                    color = DarkTextSecondary,
                                    fontSize = 11.5.sp
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = DarkTextSecondary
                            )
                        }
                    }

                    // Option 2: Staff Portal
                    val (canStaff, staffBlockReason) = viewModel.canSwitchToPortal("staff", currentUser)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clickable {
                                if (canStaff) {
                                    viewModel.switchPortalDirectly("staff")
                                    onDismiss()
                                    android.widget.Toast.makeText(
                                        context,
                                        if (isBengali) "স্টাফ পোর্টালে সফলভাবে প্রবেশ করেছেন!" else "Switched to Staff Portal!",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    android.widget.Toast.makeText(
                                        context,
                                        staffBlockReason,
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                !canStaff -> DarkBg.copy(alpha = 0.45f)
                                currentUser.role == "staff" -> Color(0xFF2E7D32).copy(alpha = 0.15f)
                                else -> DarkBg
                            }
                        ),
                        border = BorderStroke(
                            width = if (currentUser.role == "staff") 1.8.dp else 1.dp,
                            color = when {
                                !canStaff -> SleekBorderColor.copy(alpha = 0.35f)
                                currentUser.role == "staff" -> Color(0xFF2E7D32)
                                else -> SleekBorderColor
                            }
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        if (canStaff) Color(0xFF2E7D32).copy(alpha = 0.2f) else DarkSurface,
                                        RoundedCornerShape(10.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (canStaff) Icons.Default.People else Icons.Default.Lock,
                                    contentDescription = "Staff",
                                    tint = if (canStaff) Color(0xFF2E7D32) else DarkTextSecondary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (isBengali) "স্টাফ পোর্টাল" else "Staff Portal",
                                        color = if (canStaff) DarkTextPrimary else DarkTextSecondary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (currentUser.role == "staff") {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isBengali) "● বর্তমান" else "● Active",
                                            color = Color(0xFF2E7D32),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else if (!canStaff) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isBengali) "🔒 বন্ধ" else "🔒 Locked",
                                            color = DarkAccentRed,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    text = if (!canStaff) {
                                        if (isBengali) "মার্চেন্ট একাউন্ট স্টাফ পোর্টালে যেতে পারবে না" else "Merchant account cannot switch to Staff"
                                    } else {
                                        if (isBengali) "ফার্মেসী কর্মী, সেলস বিল ও স্টক ইন" else "Pharmacy employee, billing & stock"
                                    },
                                    color = DarkTextSecondary.copy(alpha = if (canStaff) 1f else 0.7f),
                                    fontSize = 11.5.sp
                                )
                            }
                            Icon(
                                imageVector = if (canStaff) Icons.Default.ChevronRight else Icons.Default.Block,
                                contentDescription = null,
                                tint = if (canStaff) DarkTextSecondary else DarkAccentRed.copy(alpha = 0.7f)
                            )
                        }
                    }

                    // Option 3: Merchant Portal
                    val (canMerchant, merchantBlockReason) = viewModel.canSwitchToPortal("admin", currentUser)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .clickable {
                                if (!canMerchant) {
                                    android.widget.Toast.makeText(
                                        context,
                                        merchantBlockReason,
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                } else if (currentUser.role == "customer" || currentUser.pharmacyName.isBlank()) {
                                    adminPharmacyName = currentUser.pharmacyName.ifBlank { "" }
                                    selectedCustomization = "admin_custom"
                                } else {
                                    viewModel.switchPortalDirectly("admin", currentUser.pharmacyName)
                                    onDismiss()
                                    android.widget.Toast.makeText(
                                        context,
                                        if (isBengali) "মার্চেন্ট পোর্টালে সফলভাবে প্রবেশ করেছেন!" else "Switched to Merchant Portal!",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                !canMerchant -> DarkBg.copy(alpha = 0.45f)
                                currentUser.role == "admin" || currentUser.role == "merchant" -> DarkAccentRed.copy(alpha = 0.15f)
                                else -> DarkBg
                            }
                        ),
                        border = BorderStroke(
                            width = if (currentUser.role == "admin" || currentUser.role == "merchant") 1.8.dp else 1.dp,
                            color = when {
                                !canMerchant -> SleekBorderColor.copy(alpha = 0.35f)
                                currentUser.role == "admin" || currentUser.role == "merchant" -> DarkAccentRed
                                else -> SleekBorderColor
                            }
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(
                                        if (canMerchant) DarkAccentRed.copy(alpha = 0.2f) else DarkSurface,
                                        RoundedCornerShape(10.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (canMerchant) Icons.Default.Storefront else Icons.Default.Lock,
                                    contentDescription = "Merchant",
                                    tint = if (canMerchant) DarkAccentRed else DarkTextSecondary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (isBengali) "মার্চেন্ট পোর্টাল" else "Merchant Portal",
                                        color = if (canMerchant) DarkTextPrimary else DarkTextSecondary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (currentUser.role == "admin" || currentUser.role == "merchant") {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isBengali) "● বর্তমান" else "● Active",
                                            color = DarkAccentRed,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else if (!canMerchant) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (isBengali) "🔒 বন্ধ" else "🔒 Locked",
                                            color = DarkAccentRed,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    text = if (!canMerchant) {
                                        if (isBengali) "স্টাফ একাউন্ট মার্চেন্ট পোর্টাল তৈরি করতে পারবে না" else "Staff account cannot switch to Merchant"
                                    } else {
                                        if (isBengali) "ফার্মেসী মালিক, পূর্ণ কন্ট্রোল ও রিপোর্ট" else "Pharmacy owner, full dashboard & reports"
                                    },
                                    color = DarkTextSecondary.copy(alpha = if (canMerchant) 1f else 0.7f),
                                    fontSize = 11.5.sp
                                )
                            }
                            Icon(
                                imageVector = if (canMerchant) Icons.Default.ChevronRight else Icons.Default.Block,
                                contentDescription = null,
                                tint = if (canMerchant) DarkTextSecondary else DarkAccentRed.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else if (selectedCustomization == "admin_custom") {
                    // Pharmacy Name Input when switching to Merchant Portal
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .background(DarkAccentRed.copy(alpha = 0.2f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Storefront,
                                contentDescription = "Merchant",
                                tint = DarkAccentRed,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (isBengali) "মার্চেন্ট পোর্টাল পরিবর্তন" else "Switch to Merchant Portal",
                            color = DarkTextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isBengali) "মার্চেন্ট পোর্টালে যেতে আপনার ফার্মেসীর নাম প্রদান করুন:" else "Enter your pharmacy name to switch to Merchant Portal:",
                            color = DarkTextSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
                        )

                        OutlinedTextField(
                            value = adminPharmacyName,
                            onValueChange = {
                                adminPharmacyName = it
                                errorMsg = ""
                            },
                            label = { Text(if (isBengali) "ফার্মেসীর নাম (Pharmacy Name)" else "Pharmacy Name") },
                            placeholder = { Text(if (isBengali) "যেমন: সেবা ফার্মেসী, লাজ ফার্মা..." else "e.g. Popular Pharmacy") },
                            leadingIcon = {
                                Icon(Icons.Default.LocalPharmacy, contentDescription = null, tint = DarkAccentRed)
                            },
                            textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DarkAccentRed,
                                unfocusedBorderColor = DarkTextSecondary.copy(alpha = 0.5f),
                                focusedLabelColor = DarkAccentRed,
                                unfocusedLabelColor = DarkTextSecondary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        if (errorMsg.isNotEmpty()) {
                            Text(
                                text = errorMsg,
                                color = DarkAccentRed,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    selectedCustomization = null
                                    errorMsg = ""
                                },
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, SleekBorderColor)
                            ) {
                                Text(if (isBengali) "পিছনে" else "Back", color = DarkTextSecondary)
                            }

                            Button(
                                onClick = {
                                    if (adminPharmacyName.trim().isEmpty()) {
                                        errorMsg = if (isBengali) "অনুগ্রহ করে আপনার ফার্মেসীর নাম লিখুন!" else "Please enter your pharmacy name!"
                                    } else {
                                        viewModel.switchPortalDirectly("admin", adminPharmacyName.trim())
                                        onDismiss()
                                        android.widget.Toast.makeText(
                                            context,
                                            if (isBengali) "মার্চেন্ট পোর্টালে সফলভাবে প্রবেশ করেছেন!" else "Switched to Merchant Portal!",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                },
                                modifier = Modifier.weight(1.5f).height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DarkAccentRed),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isBengali) "পোর্টালে প্রবেশ" else "Enter Portal",
                                    color = DarkAccentText,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String, isBengali: Boolean) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(text = label, color = DarkTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text(text = value, color = DarkTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Divider(color = Color.White.copy(alpha = 0.05f))
    }
}

@Composable
fun CustomerEditProfileDialog(
    viewModel: PharmacyViewModel,
    isBengali: Boolean,
    onDismiss: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()

    var name by remember { mutableStateOf(currentUser.ownerName) }
    var address by remember { mutableStateOf(currentUser.address) }
    var phone by remember { mutableStateOf(currentUser.phone) }
    var password by remember { mutableStateOf(currentUser.pass) }
    var role by remember { mutableStateOf(currentUser.role) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, SleekBorderColor),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text(
                    text = if (isBengali) "গ্রাহক প্রোফাইল ও ক্যাটাগরি পরিবর্তন" else "Edit Profile & Category",
                    color = DarkAccentGold,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (isBengali) "আপনার নাম (Name)" else "Your Name") },
                    textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DarkAccentRed,
                        unfocusedBorderColor = DarkTextSecondary.copy(alpha = 0.5f),
                        focusedLabelColor = DarkAccentRed,
                        unfocusedLabelColor = DarkTextSecondary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text(if (isBengali) "ঠিকানা (Address)" else "Address") },
                    textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DarkAccentRed,
                        unfocusedBorderColor = DarkTextSecondary.copy(alpha = 0.5f),
                        focusedLabelColor = DarkAccentRed,
                        unfocusedLabelColor = DarkTextSecondary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(if (isBengali) "ফোন নম্বর (Phone)" else "Phone Number") },
                    textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DarkAccentRed,
                        unfocusedBorderColor = DarkTextSecondary.copy(alpha = 0.5f),
                        focusedLabelColor = DarkAccentRed,
                        unfocusedLabelColor = DarkTextSecondary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (isBengali) "নিরাপত্তা পাসওয়ার্ড (Password)" else "Password") },
                    textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DarkAccentRed,
                        unfocusedBorderColor = DarkTextSecondary.copy(alpha = 0.5f),
                        focusedLabelColor = DarkAccentRed,
                        unfocusedLabelColor = DarkTextSecondary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(if (isBengali) "বাতিল" else "Cancel", color = DarkTextSecondary) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = DarkAccentRed, contentColor = DarkAccentText),
                        onClick = {
                            viewModel.updateProfile(
                                name = currentUser.pharmacyName,
                                addr = address,
                                phone = phone,
                                owner = name,
                                pass = password,
                                openTime = currentUser.openTime,
                                closeTime = currentUser.closeTime,
                                role = role
                            )
                            onDismiss()
                        }
                    ) {
                        Text(if (isBengali) "সংরক্ষণ করুন" else "Save", color = DarkAccentText)
                    }
                }
            }
        }
    }
}

@Composable
fun CustomerLocationSettingsDialog(
    viewModel: PharmacyViewModel,
    isBengali: Boolean,
    onDismiss: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()
    var selectedWardIndex by remember {
        val wIdx = sylhetAreas.indexOfFirst { it.ward == currentUser.ward || it.wardEn == currentUser.ward }
        mutableStateOf(if (wIdx >= 0) wIdx else 0)
    }
    var selectedNeighborhoodIndex by remember {
        val activeWard = sylhetAreas.getOrElse(selectedWardIndex) { sylhetAreas[0] }
        val nIdx = activeWard.neighborhoods.indexOfFirst { it.name == currentUser.neighborhood || it.nameEn == currentUser.neighborhood }
        mutableStateOf(if (nIdx >= 0) nIdx else 0)
    }
    var manualAddressText by remember { mutableStateOf(currentUser.address) }
    var lat by remember { mutableStateOf(currentUser.latitude) }
    var lng by remember { mutableStateOf(currentUser.longitude) }
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, SleekBorderColor),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = DarkAccentGold,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isBengali) "সেটিংস: স্থায়ী লোকেশন সেট করুন" else "Settings: Set Location",
                        color = DarkAccentGold,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = if (isBengali) "এখানে সেট করা লোকেশন ডিফল্ট হিসেবে আপনার প্রোফাইলে ও ওষুধ খোঁজার জন্য ব্যবহার করা হবে।" else "This location will be saved to your profile and used for pharmacy search.",
                    color = DarkTextSecondary,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Current Saved Location Status
                if (currentUser.isLocationSet) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(8.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${if (isBengali) "বর্তমান সেভ করা লোকেশন: " else "Currently saved: "}${currentUser.address}",
                            color = DarkAccentGold,
                            fontSize = 11.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF261D1D), RoundedCornerShape(8.dp))
                            .border(1.dp, DarkAccentGold.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = if (isBengali) "⚠️ বর্তমানে কোনো লোকেশন সেট করা নেই। নিচে আপনার নিজস্ব ঠিকানা লিখুন অথবা এলাকা বেছে নিয়ে সেভ করুন।" else "⚠️ No location is currently set. Enter your address or choose your area below and save.",
                            color = DarkAccentGold,
                            fontSize = 11.5.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Live GPS button
                Button(
                    onClick = {
                        try {
                            val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
                            val lastKnown = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                                ?: locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                            if (lastKnown != null) {
                                lat = lastKnown.latitude
                                lng = lastKnown.longitude
                                if (manualAddressText.isBlank()) {
                                    manualAddressText = "লাইভ জিপিএস লোকেশন (${String.format("%.4f", lat)}, ${String.format("%.4f", lng)})"
                                }
                                android.widget.Toast.makeText(context, if (isBengali) "জিপিএস লোকেশন পাওয়া গেছে!" else "GPS Location acquired!", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                android.widget.Toast.makeText(context, if (isBengali) "জিপিএস সিগন্যাল মেলেনি। ম্যানুয়াল ঠিকানা লিখুন।" else "GPS not available. Please type address manually.", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: SecurityException) {
                            android.widget.Toast.makeText(context, if (isBengali) "লোকেশন পারমিশন নেই।" else "Location permission required.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DarkBg),
                    border = BorderStroke(1.dp, InfoColor.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(Icons.Filled.MyLocation, contentDescription = null, tint = InfoColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isBengali) "📍 বর্তমান ডিভাইস জিপিএস (GPS) লোকেশন নিন" else "📍 Get Device GPS Location", color = InfoColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Manual Address Input Field
                OutlinedTextField(
                    value = manualAddressText,
                    onValueChange = { manualAddressText = it },
                    label = { Text(if (isBengali) "ঠিকানা লিখুন (বাড়ি/রোড/এলাকা)" else "Type Address (House/Road/Area)") },
                    textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DarkAccentRed,
                        unfocusedBorderColor = DarkTextSecondary.copy(alpha = 0.5f),
                        focusedLabelColor = DarkAccentRed,
                        unfocusedLabelColor = DarkTextSecondary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = Color.White.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isBengali) "অথবা সিলেট সিটি এরিয়া নির্বাচন করুন:" else "Or Select Sylhet City Area:",
                    color = Color.White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                var showWardMenu by remember { mutableStateOf(false) }
                var showNeighborhoodMenu by remember { mutableStateOf(false) }

                // Ward selector
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .clickable { showWardMenu = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isBengali) sylhetAreas[selectedWardIndex].ward else sylhetAreas[selectedWardIndex].wardEn,
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = showWardMenu,
                        onDismissRequest = { showWardMenu = false },
                        modifier = Modifier.background(DarkSurface)
                    ) {
                        sylhetAreas.forEachIndexed { idx, area ->
                            DropdownMenuItem(
                                text = { Text(if (isBengali) area.ward else area.wardEn, color = Color.White, fontSize = 12.sp) },
                                onClick = {
                                    selectedWardIndex = idx
                                    selectedNeighborhoodIndex = 0
                                    showWardMenu = false
                                    val nh = area.neighborhoods[0]
                                    lat = nh.lat
                                    lng = nh.lon
                                    if (manualAddressText.trim().isEmpty() || manualAddressText.contains("সিলেট")) {
                                        manualAddressText = "সিলেট সিটি কর্পোরেশন, ${area.ward}, ${nh.name}"
                                    }
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Neighborhood selector
                val activeWard = sylhetAreas[selectedWardIndex]
                val currentNeighborhood = activeWard.neighborhoods.getOrElse(selectedNeighborhoodIndex) { activeWard.neighborhoods[0] }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .clickable { showNeighborhoodMenu = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isBengali) currentNeighborhood.name else currentNeighborhood.nameEn,
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = showNeighborhoodMenu,
                        onDismissRequest = { showNeighborhoodMenu = false },
                        modifier = Modifier.background(DarkSurface)
                    ) {
                        activeWard.neighborhoods.forEachIndexed { idx, nh ->
                            DropdownMenuItem(
                                text = { Text(if (isBengali) nh.name else nh.nameEn, color = Color.White, fontSize = 12.sp) },
                                onClick = {
                                    selectedNeighborhoodIndex = idx
                                    showNeighborhoodMenu = false
                                    lat = nh.lat
                                    lng = nh.lon
                                    manualAddressText = "সিলেট সিটি কর্পোরেশন, ${activeWard.ward}, ${nh.name}"
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(if (isBengali) "বাতিল" else "Cancel", color = DarkTextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = DarkAccentRed, contentColor = DarkAccentText),
                        onClick = {
                            val activeWardItem = sylhetAreas[selectedWardIndex]
                            val activeNhItem = activeWardItem.neighborhoods.getOrElse(selectedNeighborhoodIndex) { activeWardItem.neighborhoods[0] }
                            val finalAddress = if (manualAddressText.trim().isNotEmpty()) {
                                manualAddressText.trim()
                            } else {
                                "সিলেট সিটি কর্পোরেশন, ${activeWardItem.ward}, ${activeNhItem.name}"
                            }
                            val finalLat = if (lat != 0.0) lat else activeNhItem.lat
                            val finalLon = if (lng != 0.0) lng else activeNhItem.lon
                            viewModel.updateCustomerLocation(
                                addr = finalAddress,
                                ward = activeWardItem.ward,
                                neighborhood = activeNhItem.name,
                                lat = finalLat,
                                lon = finalLon
                            )
                            android.widget.Toast.makeText(
                                context,
                                if (isBengali) "লোকেশন সফলভাবে সেটিংসে সংরক্ষণ করা হয়েছে!" else "Location successfully saved in Settings!",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            onDismiss()
                        }
                    ) {
                        Text(if (isBengali) "সেটিংসে সেভ করুন" else "Save in Settings", color = DarkAccentText)
                    }
                }
            }
        }
    }
}

