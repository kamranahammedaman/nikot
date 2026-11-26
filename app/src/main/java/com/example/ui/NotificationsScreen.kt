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
fun NotificationsScreen(
    viewModel: PharmacyViewModel, 
    isBengali: Boolean,
    onBack: (() -> Unit)? = null
) {
    val notifications by viewModel.notifications.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val currentUser by viewModel.currentUser.collectAsState()
    var showStaffRequestDialog by remember { mutableStateOf(false) }
    var showStaffDisconnectWarning by remember { mutableStateOf(false) }
    var staffReqPharmacyName by remember { mutableStateOf("") }
    var staffReqOwnerName by remember { mutableStateOf("") }
    var staffReqSecurityCode by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = if (isBengali) "নোটিফিকেশন সমূহ" else "All Notifications",
                    color = DarkTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            if (notifications.any { !it.isRead }) {
                TextButton(
                    onClick = {
                        notifications.forEach { notif ->
                            if (!notif.isRead) {
                                viewModel.markNotificationAsRead(notif)
                            }
                        }
                        android.widget.Toast.makeText(context, if (isBengali) "সব নোটিফিকেশন পড়া হয়েছে!" else "All notifications marked as read!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(
                        text = if (isBengali) "সব পড়া হয়েছে" else "Mark all read",
                        color = DarkAccentGold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // STAFF PHARMACY CONNECTION PANEL
        if (currentUser.role == "staff") {
            val permissionRequests by viewModel.permissionRequests.collectAsState()
            val myRequests = permissionRequests.filter { it.staffUsername == currentUser.username }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (currentUser.linkedAdminUsername.isNotEmpty()) SuccessColor.copy(alpha = 0.4f) else DarkAccentGold.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

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
                                "আপনি বর্তমানে কোনো মার্চেন্ট ফার্মেসীর সাথে যুক্ত নন। ফার্মেসীর তথ্য দেখতে ও পরিচালনা করতে অনুগ্রহ করে মার্চেন্টের অনুমতি চেয়ে আবেদন করুন।"
                            } else {
                                "You are not currently linked to any merchant pharmacy. Please apply for permission to gain access and manage pharmacy data."
                            },
                            color = DarkTextSecondary,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (currentUser.linkedAdminUsername.isEmpty()) {
                        Button(
                            onClick = { showStaffRequestDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DarkAccentRed,
                                contentColor = DarkAccentText
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBengali) "অনুমতির জন্য আবেদন করুন" else "Apply for Access",
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

                    // WARNING DIALOG FOR STAFF TO DISCONNECT
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

                    if (myRequests.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (isBengali) "আপনার আবেদনসমূহ:" else "Your Requests:",
                            color = DarkTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        myRequests.sortedByDescending { it.timestamp }.take(3).forEach { req ->
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
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (notifications.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = "No notifications",
                        tint = DarkTextSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isBengali) "কোনো নোটিফিকেশন পাওয়া যায়নি" else "No notifications yet",
                        color = DarkTextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(notifications.size) { index ->
                    val notif = notifications[index]
                    val isUnread = !notif.isRead

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isUnread) {
                                    viewModel.markNotificationAsRead(notif)
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isUnread) DarkSurface.copy(alpha = 0.85f) else DarkSurface.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isUnread) DarkAccentGold.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.05f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // Status Dot & Bell Icon
                            Box(modifier = Modifier.padding(top = 2.dp)) {
                                Icon(
                                    imageVector = if (isUnread) Icons.Filled.NotificationsActive else Icons.Outlined.Notifications,
                                    contentDescription = "Notification Bell",
                                    tint = if (isUnread) DarkAccentGold else DarkTextSecondary,
                                    modifier = Modifier.size(22.dp)
                                )
                                if (isUnread) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 2.dp, y = (-2).dp)
                                            .size(8.dp)
                                            .background(DarkAccentRed, androidx.compose.foundation.shape.CircleShape)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Details
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isBengali) notif.titleBn else notif.titleEn,
                                        color = if (isUnread) DarkTextPrimary else DarkTextSecondary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    
                                    val timeText = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(notif.timestamp))
                                    Text(
                                        text = timeText,
                                        color = DarkTextSecondary.copy(alpha = 0.7f),
                                        fontSize = 11.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = if (isBengali) notif.messageBn else notif.messageEn,
                                    color = if (isUnread) DarkTextPrimary.copy(alpha = 0.9f) else DarkTextSecondary.copy(alpha = 0.8f),
                                    fontSize = 13.sp,
                                    lineHeight = 17.sp
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                val dateText = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(notif.timestamp))
                                Text(
                                    text = dateText,
                                    color = DarkTextSecondary.copy(alpha = 0.5f),
                                    fontSize = 10.sp
                                )
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
}

