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
fun EditProfileDialog(
    viewModel: PharmacyViewModel,
    isBengali: Boolean,
    onDismiss: () -> Unit
) {
    val currentUser by viewModel.currentUser.collectAsState()
    val pN by viewModel.pharmacyName.collectAsState()
    val pA by viewModel.pharmacyAddress.collectAsState()
    val pP by viewModel.pharmacyPhone.collectAsState()
    val oN by viewModel.ownerName.collectAsState()
    val oPw by viewModel.ownerPassword.collectAsState()
    val pOpen by viewModel.pharmacyOpenTime.collectAsState()
    val pClose by viewModel.pharmacyCloseTime.collectAsState()

    var name by remember { mutableStateOf(pN) }
    var phone by remember { mutableStateOf(pP) }
    var owner by remember { mutableStateOf(oN) }
    var password by remember { mutableStateOf("") }
    var openTime by remember { mutableStateOf(pOpen) }
    var closeTime by remember { mutableStateOf(pClose) }
    var role by remember { mutableStateOf(currentUser.role) }
    var securityCode by remember { mutableStateOf(currentUser.securityCode) }

    // Ward & Neighborhood Index
    var selectedWardIndex by remember {
        val idx = sylhetAreas.indexOfFirst { it.ward == currentUser.ward || it.wardEn == currentUser.ward }
        mutableStateOf(if (idx != -1) idx else 0)
    }
    var selectedNeighborhoodIndex by remember {
        val activeWard = sylhetAreas.getOrElse(selectedWardIndex) { sylhetAreas[0] }
        val idx = activeWard.neighborhoods.indexOfFirst { it.name == currentUser.neighborhood || it.nameEn == currentUser.neighborhood }
        mutableStateOf(if (idx != -1) idx else 0)
    }

    var lat by remember { mutableStateOf(if (currentUser.latitude != 0.0) currentUser.latitude else 24.9015) }
    var lng by remember { mutableStateOf(if (currentUser.longitude != 0.0) currentUser.longitude else 91.8683) }
    var showMapPickerInDialog by remember { mutableStateOf(false) }

    var currentStep by remember { mutableStateOf(1) } // 1, 2, 3, 4

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                Text(
                    text = if (isBengali) "ফার্মেসি ও মালিক তথ্য সংশোধন" else "Edit Information & Passcode",
                    color = DarkAccentGold,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Custom Gorgeous Step Indicator Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (step in 1..4) {
                        val isActive = currentStep == step
                        val isCompleted = currentStep > step
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { currentStep = step }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .background(
                                        color = if (isActive) DarkAccentRed else if (isCompleted) SuccessColor else Color.White.copy(alpha = 0.05f),
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isActive) DarkAccentRed else if (isCompleted) SuccessColor else Color.White.copy(alpha = 0.2f),
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isCompleted) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Done",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                } else {
                                    Text(
                                        text = if (isBengali) {
                                            when(step) {
                                                1 -> "১"
                                                2 -> "২"
                                                3 -> "৩"
                                                else -> "৪"
                                            }
                                        } else step.toString(),
                                        color = if (isActive || isCompleted) Color.White else DarkTextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (step < 4) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .width(20.dp)
                                        .height(2.dp)
                                        .background(if (isCompleted) SuccessColor else Color.White.copy(alpha = 0.1f))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                        }
                    }
                }

                // Step Contents
                when (currentStep) {
                    1 -> {
                        // ১ম পাতায়: ফার্মেসীর নাম, মালিকের নাম, মোবাইল
                        Text(
                            text = if (isBengali) "১ম পাতা: সাধারণ মালিক ও ফার্মেসি তথ্য" else "Page 1: General Profile Details",
                            color = DarkTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(if (isBengali) "ফার্মেসির নাম" else "Pharmacy Name") },
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
                            value = owner,
                            onValueChange = { owner = it },
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
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text(if (isBengali) "মোবাইল নম্বর" else "Mobile Number") },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = DarkAccentRed) },
                            textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DarkAccentRed,
                                unfocusedBorderColor = SleekBorderColor,
                                focusedLabelColor = DarkAccentRed,
                                unfocusedLabelColor = DarkTextSecondary
                            )
                        )
                    }
                    2 -> {
                        // ২য় পাতায়: লোকেশন ও গুগল ম্যাপ কানেকশন
                        Text(
                            text = if (isBengali) "২য় পাতা: সিলেট সিটি লোকেশন ও ম্যাপ" else "Page 2: Sylhet City Location & Map",
                            color = DarkTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        var showWardMenu by remember { mutableStateOf(false) }
                        var showNeighborhoodMenu by remember { mutableStateOf(false) }

                        // Ward Dropdown
                        Text(
                            text = if (isBengali) "ওয়ার্ড নম্বর নির্বাচন করুন:" else "Select Ward Number:",
                            color = DarkTextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                .border(1.dp, SleekBorderColor, RoundedCornerShape(8.dp))
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
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showWardMenu,
                                onDismissRequest = { showWardMenu = false },
                                modifier = Modifier.background(DarkSurface).fillMaxWidth(0.8f).height(250.dp)
                            ) {
                                sylhetAreas.forEachIndexed { idx, area ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (isBengali) area.ward else area.wardEn,
                                                color = Color.White,
                                                fontSize = 13.sp
                                            )
                                        },
                                        onClick = {
                                            selectedWardIndex = idx
                                            selectedNeighborhoodIndex = 0
                                            showWardMenu = false
                                            val nh = area.neighborhoods[0]
                                            lat = nh.lat
                                            lng = nh.lon
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Neighborhood Dropdown
                        Text(
                            text = if (isBengali) "পাড়া / এলাকার নাম নির্বাচন করুন:" else "Select Neighborhood / Area:",
                            color = DarkTextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                .border(1.dp, SleekBorderColor, RoundedCornerShape(8.dp))
                                .clickable { showNeighborhoodMenu = true }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            val activeWard = sylhetAreas[selectedWardIndex]
                            val currentNeighborhood = activeWard.neighborhoods.getOrElse(selectedNeighborhoodIndex) { activeWard.neighborhoods[0] }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isBengali) currentNeighborhood.name else currentNeighborhood.nameEn,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showNeighborhoodMenu,
                                onDismissRequest = { showNeighborhoodMenu = false },
                                modifier = Modifier.background(DarkSurface).fillMaxWidth(0.8f)
                            ) {
                                activeWard.neighborhoods.forEachIndexed { idx, nh ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = if (isBengali) nh.name else nh.nameEn,
                                                color = Color.White,
                                                fontSize = 13.sp
                                            )
                                        },
                                        onClick = {
                                            selectedNeighborhoodIndex = idx
                                            showNeighborhoodMenu = false
                                            lat = nh.lat
                                            lng = nh.lon
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Accurate GPS Map Picker Button
                        Button(
                            onClick = { showMapPickerInDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessColor, contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(40.dp)
                        ) {
                            Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isBengali) "গুগল ম্যাপে পিন করুন" else "Pin on Google Map",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Preview of Location Coordinates & Full Resolved Address
                        val fullAddressText = if (isBengali) {
                            "সিলেট সিটি কর্পোরেশন, ${sylhetAreas[selectedWardIndex].ward}, ${sylhetAreas[selectedWardIndex].neighborhoods.getOrElse(selectedNeighborhoodIndex) { sylhetAreas[selectedWardIndex].neighborhoods[0] }.name}"
                        } else {
                            "Sylhet City Corporation, ${sylhetAreas[selectedWardIndex].wardEn}, ${sylhetAreas[selectedWardIndex].neighborhoods.getOrElse(selectedNeighborhoodIndex) { sylhetAreas[selectedWardIndex].neighborhoods[0] }.nameEn}"
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = if (isBengali) "স্থানাঙ্ক ও ঠিকানা বিবরণী:" else "Coordinates & Resolved Address:",
                                    color = DarkTextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = fullAddressText,
                                    color = DarkAccentGold,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Lat: ${String.format("%.5f", lat)} | Lng: ${String.format("%.5f", lng)}",
                                    color = DarkTextPrimary,
                                    fontSize = 11.sp,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                )
                            }
                        }
                    }
                    3 -> {
                        // ৩য় পাতায়: ফার্মেরসী ওপেন ও অফ টাইম
                        Text(
                            text = if (isBengali) "৩য় পাতা: ফার্মেসী খোলার ও বন্ধের সময়সূচী" else "Page 3: Business Operating Hours",
                            color = DarkTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        OutlinedTextField(
                            value = openTime,
                            onValueChange = { openTime = it },
                            label = { Text(if (isBengali) "খোলার সময়" else "Open Time") },
                            leadingIcon = { Icon(Icons.Default.AccessTime, contentDescription = null, tint = DarkAccentRed) },
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
                            value = closeTime,
                            onValueChange = { closeTime = it },
                            label = { Text(if (isBengali) "বন্ধের সময়" else "Close Time") },
                            leadingIcon = { Icon(Icons.Default.AccessTimeFilled, contentDescription = null, tint = DarkAccentRed) },
                            textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DarkAccentRed,
                                unfocusedBorderColor = SleekBorderColor,
                                focusedLabelColor = DarkAccentRed,
                                unfocusedLabelColor = DarkTextSecondary
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick Hours Presets
                        Text(
                            text = if (isBengali) "সহজ টাইম প্রিসেট সেট করুন:" else "Quick Operation Presets:",
                            color = DarkTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val preset1Label = if (isBengali) "২৪ ঘন্টা" else "24 Hours"
                            val preset2Label = if (isBengali) "সকাল ৯ - রাত ১১" else "9 AM - 11 PM"
                            val preset3Label = if (isBengali) "সকাল ৮ - রাত ১০" else "8 AM - 10 PM"

                            listOf(
                                Triple(preset1Label, "12:00 AM", "11:59 PM"),
                                Triple(preset2Label, "09:00 AM", "11:00 PM"),
                                Triple(preset3Label, "08:00 AM", "10:00 PM")
                            ).forEach { (label, start, end) ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                        .border(1.dp, SleekBorderColor, RoundedCornerShape(6.dp))
                                        .clickable {
                                            openTime = start
                                            closeTime = end
                                        }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        color = DarkTextPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                    4 -> {
                        // ৪র্থ পাতায়: পাসওয়ার্ড ও ক্যাটাগরি আপডেট
                        Text(
                            text = if (isBengali) "৪র্থ পাতা: পাসওয়ার্ড পরিবর্তন ও ইউজার ক্যাটাগরি" else "Page 4: Passcode & User Role Category",
                            color = DarkTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(if (isBengali) "নতুন সিকিউরিটি পাসওয়ার্ড" else "New Owner Passcode") },
                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = DarkAccentRed) },
                            textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            placeholder = { Text(if (isBengali) "অপরিবর্তিত রাখতে খালি রাখুন" else "Keep empty to stay unchanged", fontSize = 11.sp, color = DarkTextSecondary) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DarkAccentRed,
                                unfocusedBorderColor = SleekBorderColor,
                                focusedLabelColor = DarkAccentRed,
                                unfocusedLabelColor = DarkTextSecondary
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = if (isBengali) "ব্যবহারকারী ক্যাটাগরি (Category)" else "User Category",
                            color = DarkTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val rolesList = listOf("admin", "staff", "customer")
                            rolesList.forEach { r ->
                                val label = when (r) {
                                    "admin" -> if (isBengali) "মার্চেন্ট" else "Merchant"
                                    "staff" -> if (isBengali) "স্টাফ" else "Staff"
                                    else -> if (isBengali) "কাস্টমার" else "Customer"
                                }
                                val isSelected = role == r
                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { role = r },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) DarkAccentRed.copy(alpha = 0.2f) else DarkSurface
                                    ),
                                    border = BorderStroke(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) DarkAccentRed else SleekBorderColor
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) DarkTextPrimary else DarkTextSecondary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = if (isBengali) "💡 আপনি যে কোনো সময় আপনার ভূমিকা (মার্চেন্ট, স্টাফ বা কাস্টমার) পরিবর্তন করতে পারেন।" else "💡 You can change your role (Merchant, Staff, or Customer) anytime.",
                            color = DarkAccentGold,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        if (role == "admin") {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (isBengali) "সিকিউরিটি কোড (স্টাফ পারমিশন কোড)" else "Security Code (For Staff Permission)",
                                color = DarkAccentGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            OutlinedTextField(
                                value = securityCode,
                                onValueChange = { securityCode = it },
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
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Multi-page navigation buttons at the bottom
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back/Cancel Button
                    if (currentStep > 1) {
                        TextButton(
                            onClick = { currentStep -= 1 },
                            colors = ButtonDefaults.textButtonColors(contentColor = DarkTextSecondary)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isBengali) "পূর্ববর্তী" else "Previous")
                        }
                    } else {
                        TextButton(onClick = onDismiss) {
                            Text(if (isBengali) "বাতিল" else "Cancel", color = DarkTextSecondary)
                        }
                    }

                    // Next/Save Button
                    if (currentStep < 4) {
                        Button(
                            onClick = { currentStep += 1 },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkAccentRed, contentColor = DarkAccentText),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (isBengali) "পরবর্তী" else "Next", color = DarkAccentText)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp), tint = DarkAccentText)
                        }
                    } else {
                        Button(
                            onClick = {
                                val finalAddressText = if (isBengali) {
                                    "সিলেট সিটি কর্পোরেশন, ${sylhetAreas[selectedWardIndex].ward}, ${sylhetAreas[selectedWardIndex].neighborhoods.getOrElse(selectedNeighborhoodIndex) { sylhetAreas[selectedWardIndex].neighborhoods[0] }.name}"
                                } else {
                                    "Sylhet City Corporation, ${sylhetAreas[selectedWardIndex].wardEn}, ${sylhetAreas[selectedWardIndex].neighborhoods.getOrElse(selectedNeighborhoodIndex) { sylhetAreas[selectedWardIndex].neighborhoods[0] }.nameEn}"
                                }
                                viewModel.updateProfile(
                                    name = name,
                                    addr = finalAddressText,
                                    phone = phone,
                                    owner = owner,
                                    pass = password,
                                    openTime = openTime,
                                    closeTime = closeTime,
                                    role = role,
                                    ward = sylhetAreas[selectedWardIndex].ward,
                                    neighborhood = sylhetAreas[selectedWardIndex].neighborhoods.getOrElse(selectedNeighborhoodIndex) { sylhetAreas[selectedWardIndex].neighborhoods[0] }.name,
                                    latitude = lat,
                                    longitude = lng,
                                    securityCode = securityCode
                                )
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessColor, contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(if (isBengali) "সংরক্ষণ করুন" else "Save Changes", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showMapPickerInDialog) {
        LiveLocationMapDialog(
            isBengali = isBengali,
            currLat = lat,
            currLng = lng,
            onLocationSelected = { selectLat, selectLng ->
                lat = selectLat
                lng = selectLng
                showMapPickerInDialog = false
            },
            onDismiss = { showMapPickerInDialog = false }
        )
    }
}

/**
 * Real, live OpenStreetMap picker (osmdroid — free, no API key) with pinch-zoom/pan,
 * tap-to-place-pin, and a GPS "use my location" button via FusedLocationProviderClient.
 * Replaces the old canvas grid-tap simulation.
 */
@OptIn(ExperimentalPermissionsApi::class)
@SuppressLint("MissingPermission")
@Composable
fun LiveLocationMapDialog(
    isBengali: Boolean,
    currLat: Double,
    currLng: Double,
    onLocationSelected: (Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    val hasInitialLocation = currLat != 0.0 || currLng != 0.0
    val initialPoint = remember { GeoPoint(if (hasInitialLocation) currLat else 24.8949, if (hasInitialLocation) currLng else 91.8687) }
    var selectLat by remember { mutableStateOf(initialPoint.latitude) }
    var selectLng by remember { mutableStateOf(initialPoint.longitude) }
    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var markerRef by remember { mutableStateOf<Marker?>(null) }

    DisposableEffect(Unit) {
        onDispose { mapViewRef?.onDetach() }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(GlassBackgroundGradient)) {

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    OsmConfiguration.getInstance().userAgentValue = ctx.packageName
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(15.0)
                        controller.setCenter(initialPoint)

                        val marker = Marker(this).apply {
                            position = initialPoint
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            title = if (isBengali) "নির্বাচিত অবস্থান" else "Selected location"
                        }
                        overlays.add(marker)
                        markerRef = marker

                        val eventsReceiver = object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                selectLat = p.latitude
                                selectLng = p.longitude
                                marker.position = p
                                invalidate()
                                return true
                            }
                            override fun longPressHelper(p: GeoPoint): Boolean = false
                        }
                        overlays.add(0, MapEventsOverlay(eventsReceiver))

                        mapViewRef = this
                    }
                }
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(onClick = onDismiss, icon = Icons.Filled.ArrowBack, contentDescription = "Back", tint = DarkTextPrimary)
                GlassPanel(modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp)) {
                    Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = DarkTextSecondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (isBengali) "ম্যাপে ট্যাপ করে অবস্থান বেছে নিন" else "Tap the map to set a location",
                            color = DarkTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 18.dp, bottom = 180.dp)
            ) {
                GlassIconButton(
                    onClick = {
                        if (locationPermissionState.status.isGranted) {
                            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                            try {
                                fusedClient.lastLocation.addOnSuccessListener { location ->
                                    if (location != null) {
                                        selectLat = location.latitude
                                        selectLng = location.longitude
                                        val p = GeoPoint(location.latitude, location.longitude)
                                        markerRef?.position = p
                                        mapViewRef?.controller?.animateTo(p)
                                        mapViewRef?.invalidate()
                                    }
                                }
                            } catch (e: SecurityException) { /* permission was revoked mid-flight */ }
                        } else {
                            locationPermissionState.launchPermissionRequest()
                        }
                    },
                    icon = Icons.Filled.MyLocation,
                    contentDescription = if (isBengali) "আমার অবস্থান" else "Use my location",
                    tint = if (locationPermissionState.status.isGranted) DarkAccentGold else DarkTextSecondary
                )
            }

            GlassPanel(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                strong = true
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(36.dp)
                            .height(4.dp)
                            .background(DarkTextSecondary.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("LAT: ${String.format("%.5f", selectLat)}", color = DarkAccentGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("LNG: ${String.format("%.5f", selectLng)}", color = DarkAccentGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                            Text(if (isBengali) "বাতিল" else "Cancel")
                        }
                        Button(
                            onClick = { onLocationSelected(selectLat, selectLng) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkAccentRed, contentColor = DarkAccentText)
                        ) {
                            Text(if (isBengali) "নিশ্চিত করুন" else "Confirm location", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

data class SylhetArea(
    val ward: String,
    val wardEn: String,
    val neighborhoods: List<SylhetNeighborhood>
)

data class SylhetNeighborhood(
    val name: String,
    val nameEn: String,
    val lat: Double,
    val lon: Double
)

val sylhetAreas = listOf(
    SylhetArea(
        ward = "ওয়ার্ড নং ১",
        wardEn = "Ward No. 1",
        neighborhoods = listOf(
            SylhetNeighborhood("দরগাহ মহল্লা", "Dargah Mohalla", 24.9010, 91.8655),
            SylhetNeighborhood("মিরের ময়দান", "Mirer Moydan", 24.8985, 91.8610),
            SylhetNeighborhood("পায়রা", "Payra", 24.9020, 91.8625),
            SylhetNeighborhood("চৌহাট্টা", "Chowhatta", 24.8970, 91.8690),
            SylhetNeighborhood("রাজার গলি", "Rajar Goli", 24.9030, 91.8640)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ২",
        wardEn = "Ward No. 2",
        neighborhoods = listOf(
            SylhetNeighborhood("কাষ্টঘর", "Kastoghor", 24.8875, 91.8710),
            SylhetNeighborhood("মহাজনপট্টি", "Mahajonpotti", 24.8860, 91.8700),
            SylhetNeighborhood("মাছুদিঘীর পার", "Machudighir Par", 24.8890, 91.8740),
            SylhetNeighborhood("নয়াসড়ক (আংশিক)", "Nayasarak (Part)", 24.8935, 91.8725)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ৩",
        wardEn = "Ward No. 3",
        neighborhoods = listOf(
            SylhetNeighborhood("মুন্সিপাড়া", "Munsipara", 24.8965, 91.8635),
            SylhetNeighborhood("কাজলশাহ", "Kajolshah", 24.8960, 91.8570),
            SylhetNeighborhood("ভাতালিয়া", "Bhatalia", 24.8930, 91.8590),
            SylhetNeighborhood("রিকাবীবাজার", "Rikabibazar", 24.8950, 91.8600)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ৪",
        wardEn = "Ward No. 4",
        neighborhoods = listOf(
            SylhetNeighborhood("আম্বরখানা", "Amberkhana", 24.9015, 91.8683),
            SylhetNeighborhood("বড়বাজার", "Borbazar", 24.9055, 91.8715),
            SylhetNeighborhood("হাউজিং এস্টেট", "Housing Estate", 24.9060, 91.8660),
            SylhetNeighborhood("দরগাবাড়ী", "Dargabari", 24.9005, 91.8670),
            SylhetNeighborhood("বিমানবন্দর রোড (আংশিক)", "Airport Road (Part)", 24.9120, 91.8650)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ৫",
        wardEn = "Ward No. 5",
        neighborhoods = listOf(
            SylhetNeighborhood("চৌকিদেখি", "Chowkidekhi", 24.9140, 91.8750),
            SylhetNeighborhood("বাদামবাগিচা", "Badambagicha", 24.9160, 91.8730),
            SylhetNeighborhood("রায়নগর (আংশিক)", "Raynagar (Part)", 24.8980, 91.8790),
            SylhetNeighborhood("খাসদবীর", "Khasdobir", 24.9125, 91.8710)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ৬",
        wardEn = "Ward No. 6",
        neighborhoods = listOf(
            SylhetNeighborhood("চৌকিদেখি (আংশিক)", "Chowkidekhi (Part)", 24.9150, 91.8770),
            SylhetNeighborhood("বড়বাজার (আংশিক)", "Borbazar (Part)", 24.9070, 91.8730),
            SylhetNeighborhood("লেকভিউ", "Lakeview", 24.9180, 91.8790)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ৭",
        wardEn = "Ward No. 7",
        neighborhoods = listOf(
            SylhetNeighborhood("বনকলাপাড়া", "Bonkolapara", 24.9115, 91.8590),
            SylhetNeighborhood("পশ্চিম পীরমহল্লা", "West Pirmoholla", 24.9100, 91.8615),
            SylhetNeighborhood("ফাজিলচিশত", "Fazilchist", 24.9075, 91.8600),
            SylhetNeighborhood("সুবিদবাজার", "Subidbazar", 24.9045, 91.8570)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ৮",
        wardEn = "Ward No. 8",
        neighborhoods = listOf(
            SylhetNeighborhood("পাঠানটুলা", "Pathantula", 24.9010, 91.8480),
            SylhetNeighborhood("মদিনা মার্কেট", "Madina Market", 24.8995, 91.8450),
            SylhetNeighborhood("কালিবাড়ি", "Kalibari", 24.9040, 91.8430),
            SylhetNeighborhood("কানিশাইল", "Kanishail", 24.8915, 91.8485)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ৯",
        wardEn = "Ward No. 9",
        neighborhoods = listOf(
            SylhetNeighborhood("বাগবাড়ি", "Bagbari", 24.8960, 91.8490),
            SylhetNeighborhood("পাঠানটুলা (আংশিক)", "Pathantula (Part)", 24.9000, 91.8465),
            SylhetNeighborhood("সুরমা আবাসিক এলাকা", "Surma Residential Area", 24.8940, 91.8440)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ১০",
        wardEn = "Ward No. 10",
        neighborhoods = listOf(
            SylhetNeighborhood("ঘাসিটুলা", "Ghasitula", 24.8870, 91.8495),
            SylhetNeighborhood("কানিশাইল (আংশিক)", "Kanishail (Part)", 24.8900, 91.8475),
            SylhetNeighborhood("কলাপাড়া", "Kolapara", 24.8850, 91.8480),
            SylhetNeighborhood("লতিফপুর", "Latifpur", 24.8810, 91.8450)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ১১",
        wardEn = "Ward No. 11",
        neighborhoods = listOf(
            SylhetNeighborhood("সুবিদবাজার (আংশিক)", "Subidbazar (Part)", 24.9055, 91.8550),
            SylhetNeighborhood("লন্ডনপাড়া", "Londonpara", 24.9080, 91.8530),
            SylhetNeighborhood("নোয়াপাড়া", "Noapara", 24.9110, 91.8510)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ১২",
        wardEn = "Ward No. 12",
        neighborhoods = listOf(
            SylhetNeighborhood("শেখঘাট", "Shekhghat", 24.8890, 91.8570),
            SylhetNeighborhood("কায়েস্থরাইল", "Kayesthorail", 24.8810, 91.8590),
            SylhetNeighborhood("খুলিয়াপাড়া", "Khuliapara", 24.8860, 91.8550)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ১৩",
        wardEn = "Ward No. 13",
        neighborhoods = listOf(
            SylhetNeighborhood("তালতলা", "Taltola", 24.8875, 91.8630),
            SylhetNeighborhood("জামেয়া মাদানিয়া এলাকা", "Jamia Madania Area", 24.8845, 91.8615),
            SylhetNeighborhood("কাজিরবাজার", "Kazirbazar", 24.8830, 91.8580)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ১৪",
        wardEn = "Ward No. 14",
        neighborhoods = listOf(
            SylhetNeighborhood("জিন্দাবাজার", "Zindabazar", 24.8949, 91.8687),
            SylhetNeighborhood("জল্লারপাড়", "Jollarpar", 24.8915, 91.8650),
            SylhetNeighborhood("পুরান লেন", "Puran Lane", 24.8935, 91.8670),
            SylhetNeighborhood("লালদিঘীর পার", "Laldighir Par", 24.8890, 91.8720)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ১৫",
        wardEn = "Ward No. 15",
        neighborhoods = listOf(
            SylhetNeighborhood("নয়াসড়ক", "Nayasarak", 24.8940, 91.8720),
            SylhetNeighborhood("কুমারপাড়া", "Kumarpara", 24.8955, 91.8755),
            SylhetNeighborhood("বারুতখানা", "Barutkhana", 24.8930, 91.8705),
            SylhetNeighborhood("চারাদিঘীর পার", "Charadighir Par", 24.8910, 91.8735)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ১৬",
        wardEn = "Ward No. 16",
        neighborhoods = listOf(
            SylhetNeighborhood("নয়াসড়ক (আংশিক)", "Nayasarak (Part)", 24.8938, 91.8718),
            SylhetNeighborhood("জেল রোড", "Jail Road", 24.8915, 91.8750),
            SylhetNeighborhood("ধোপাদিঘীর পার", "Dhopadighir Par", 24.8885, 91.8760)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ১৭",
        wardEn = "Ward No. 17",
        neighborhoods = listOf(
            SylhetNeighborhood("কাজীটুলা", "Kazitula", 24.8990, 91.8745),
            SylhetNeighborhood("রায়নগর", "Raynagar", 24.8980, 91.8790),
            SylhetNeighborhood("ঝরনারপাড়", "Jhornarpar", 24.9010, 91.8770)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ১৮",
        wardEn = "Ward No. 18",
        neighborhoods = listOf(
            SylhetNeighborhood("মিরাবাজার", "Mirabazar", 24.8945, 91.8795),
            SylhetNeighborhood("ঝেরঝেরি পাড়া", "Zherzheripara", 24.8960, 91.8820),
            SylhetNeighborhood("রায়নগর (আংশিক)", "Raynagar (Part)", 24.8975, 91.8800)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ১৯",
        wardEn = "Ward No. 19",
        neighborhoods = listOf(
            SylhetNeighborhood("রায়নগর (পূর্ব)", "Raynagar (East)", 24.8970, 91.8840),
            SylhetNeighborhood("দপ্তরীপাড়া", "Doptoripara", 24.8990, 91.8830),
            SylhetNeighborhood("রাজবাড়ি", "Rajbari", 24.8950, 91.8860)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ২০",
        wardEn = "Ward No. 20",
        neighborhoods = listOf(
            SylhetNeighborhood("টিলাগড়", "Tilagor", 24.8970, 91.8950),
            SylhetNeighborhood("দেবপুর", "Debpur", 24.8990, 91.8980),
            SylhetNeighborhood("শিবগঞ্জ (আংশিক)", "Shibgonj (Part)", 24.8940, 91.8885)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ২১",
        wardEn = "Ward No. 21",
        neighborhoods = listOf(
            SylhetNeighborhood("শিবগঞ্জ", "Shibgonj", 24.8935, 91.8870),
            SylhetNeighborhood("সোনাপাড়া", "Sonapara", 24.8915, 91.8890),
            SylhetNeighborhood("গোপালটিলা", "Gopaltila", 24.8955, 91.8910)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ২২",
        wardEn = "Ward No. 22",
        neighborhoods = listOf(
            SylhetNeighborhood("উপশহর (ব্লক-এ, বি, সি, ডি)", "Uposhahar (Block A, B, C, D)", 24.8870, 91.8850),
            SylhetNeighborhood("शाहजालाल उपशहर", "Shahjalal Uposhahar", 24.8890, 91.8830)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ২৩",
        wardEn = "Ward No. 23",
        neighborhoods = listOf(
            SylhetNeighborhood("মেন্দিবাগ", "Mendibag", 24.8850, 91.8790),
            SylhetNeighborhood("কুশিঘাট", "Kushighat", 24.8810, 91.8880),
            SylhetNeighborhood("চালিবন্দর", "Chalibandor", 24.8870, 91.8770)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ২৪",
        wardEn = "Ward No. 24",
        neighborhoods = listOf(
            SylhetNeighborhood("কুশিঘাট (আংশিক)", "Kushighat (Part)", 24.8800, 91.8890),
            SylhetNeighborhood("তের রতন", "Teroroton", 24.8840, 91.8930),
            SylhetNeighborhood("গাজী বোরহান উদ্দিন মাজার এলাকা", "Gazi Borhan Uddin Mazar", 24.8750, 91.8950)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ২৫",
        wardEn = "Ward No. 25",
        neighborhoods = listOf(
            SylhetNeighborhood("কদমতলী", "Kadamtali", 24.8770, 91.8690),
            SylhetNeighborhood("দক্ষিণ সুরমা", "Dakshin Surma", 24.8730, 91.8650),
            SylhetNeighborhood("মোমিনখলা", "Mominkhola", 24.8745, 91.8710),
            SylhetNeighborhood("ভার্থখলা", "Bharthkhola", 24.8785, 91.8700)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ২৬",
        wardEn = "Ward No. 26",
        neighborhoods = listOf(
            SylhetNeighborhood("কদমতলী (আংশিক)", "Kadamtali (Part)", 24.8765, 91.8680),
            SylhetNeighborhood("রেলওয়ে স্টেশন এলাকা", "Railway Station Area", 24.8720, 91.8695),
            SylhetNeighborhood("ঝালোপাড়া", "Jhalopara", 24.8790, 91.8630)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ২৭",
        wardEn = "Ward No. 27",
        neighborhoods = listOf(
            SylhetNeighborhood("আলিয়া মাদ্রাসা এলাকা (দক্ষিণ সুরমা)", "Alia Madrasa Area (South)", 24.8700, 91.8600),
            SylhetNeighborhood("হোগলাকান্দি", "Hoglakandi", 24.8650, 91.8580),
            SylhetNeighborhood("পাঠানপাড়া", "Pathanpara", 24.8680, 91.8630)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ২৮",
        wardEn = "Ward No. 28",
        neighborhoods = listOf(
            SylhetNeighborhood("বরইকান্দি", "Boroikandi", 24.8650, 91.8650),
            SylhetNeighborhood("চান্দাই", "Chandai", 24.8600, 91.8700)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ২৯",
        wardEn = "Ward No. 29",
        neighborhoods = listOf(
            SylhetNeighborhood("পিরিজপুর", "Pirijpur", 24.8580, 91.8800),
            SylhetNeighborhood("ধরাধরপুর", "Dhoradhorpur", 24.8530, 91.8840)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ৩০",
        wardEn = "Ward No. 30",
        neighborhoods = listOf(
            SylhetNeighborhood("টুকের বাজার", "Tuker Bazar", 24.9180, 91.8100),
            SylhetNeighborhood("শেখপাড়া", "Shekhpara", 24.9150, 91.8150)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ৩১",
        wardEn = "Ward No. 31",
        neighborhoods = listOf(
            SylhetNeighborhood("শাহপরান", "Shahporan", 24.9080, 91.9200),
            SylhetNeighborhood("নয়াবাজার", "Nayabazar", 24.9050, 91.9240)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ৩২",
        wardEn = "Ward No. 32",
        neighborhoods = listOf(
            SylhetNeighborhood("টুলটিকর", "Tultikor", 24.8900, 91.9050),
            SylhetNeighborhood("বালুচর (আংশিক)", "Baluchor (Part)", 24.9080, 91.8880)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ৩৩",
        wardEn = "Ward No. 33",
        neighborhoods = listOf(
            SylhetNeighborhood("খাদিমনগর", "Khadimnagar", 24.9350, 91.9300),
            SylhetNeighborhood("মেজরটিলা (আংশিক)", "Majortila (Part)", 24.9050, 91.9120)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ৩৪",
        wardEn = "Ward No. 34",
        neighborhoods = listOf(
            SylhetNeighborhood("শাহপরাণ উপশহর", "Shahporan Uposhahar", 24.9120, 91.9220),
            SylhetNeighborhood("বাহুবল", "Bahubal", 24.9150, 91.9250)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ৩৫",
        wardEn = "Ward No. 35",
        neighborhoods = listOf(
            SylhetNeighborhood("চামেলীবাগ", "Chamelibag", 24.9020, 91.9180),
            SylhetNeighborhood("ইসলামপুর", "Islampur", 24.8990, 91.9150)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ৩৬",
        wardEn = "Ward No. 36",
        neighborhoods = listOf(
            SylhetNeighborhood("জালালাবাদ", "Jalalabad", 24.9310, 91.8410),
            SylhetNeighborhood("নয়াবাজার", "Nayabazar", 24.9250, 91.8450)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ৩৭",
        wardEn = "Ward No. 37",
        neighborhoods = listOf(
            SylhetNeighborhood("আখালিয়া", "Akhalia", 24.9080, 91.8350),
            SylhetNeighborhood("কুমারগাঁও", "Kumargaon", 24.9040, 91.8150)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ৩৮",
        wardEn = "Ward No. 38",
        neighborhoods = listOf(
            SylhetNeighborhood("তেমুখী", "Temukhi", 24.9090, 91.8050),
            SylhetNeighborhood("মিরেরগাঁও", "Mirergaon", 24.9130, 91.8010)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ৩৯",
        wardEn = "Ward No. 39",
        neighborhoods = listOf(
            SylhetNeighborhood("টুকের বাজার (আংশিক)", "Tuker Bazar (Part)", 24.9200, 91.8080),
            SylhetNeighborhood("হাইটেক পার্ক এলাকা", "Hi-Tech Park Area", 24.9150, 91.7950)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ৪০",
        wardEn = "Ward No. 40",
        neighborhoods = listOf(
            SylhetNeighborhood("দক্ষিণ সুরমা (বাকি অংশ)", "Dakshin Surma (Rest)", 24.8650, 91.8500),
            SylhetNeighborhood("লালমাটিয়া", "Lalmatia", 24.8590, 91.8520)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ৪১",
        wardEn = "Ward No. 41",
        neighborhoods = listOf(
            SylhetNeighborhood("গালিমপুর", "Galimpur", 24.8450, 91.8600),
            SylhetNeighborhood("তেলিবাজার", "Telibazar", 24.8400, 91.8550)
        )
    ),
    SylhetArea(
        ward = "ওয়ার্ড নং ৪২",
        wardEn = "Ward No. 42",
        neighborhoods = listOf(
            SylhetNeighborhood("পারাইরচক", "Parairchok", 24.8510, 91.8900),
            SylhetNeighborhood("কদমতলী বাইপাস এলাকা", "Kadamtali Bypass Area", 24.8560, 91.8820)
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class)
