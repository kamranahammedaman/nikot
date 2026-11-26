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
fun SuperAdminPortalScreen(viewModel: PharmacyViewModel, isBengali: Boolean) {
    val allProfiles by viewModel.userProfiles.collectAsState()
    val allMedicines by viewModel.allMedicines.collectAsState()
    val allBills by viewModel.allBills.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0: একাউন্টস ও প্যানেল, 1: ট্রাফিক ও মনিটর, 2: গ্লোবাল অ্যানাউন্সমেন্ট
    var searchQuery by remember { mutableStateOf("") }
    var selectedRoleFilter by remember { mutableStateOf("All") } // "All", "admin", "staff", "customer"

    var profileToDelete by remember { mutableStateOf<PharmacyViewModel.UserProfile?>(null) }
    var profileToEdit by remember { mutableStateOf<PharmacyViewModel.UserProfile?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    // Edit fields
    var editPharmacyName by remember { mutableStateOf("") }
    var editOwnerName by remember { mutableStateOf("") }
    var editPhone by remember { mutableStateOf("") }
    var editPassword by remember { mutableStateOf("") }

    // Broadcast fields
    var broadcastTitle by remember { mutableStateOf("") }
    var broadcastMsg by remember { mutableStateOf("") }
    var broadcastSuccessMsg by remember { mutableStateOf("") }

    val context = androidx.compose.ui.platform.LocalContext.current

    // Stats calculations
    val totalAccounts = allProfiles.size
    val totalAdmins = allProfiles.values.count { it.role == "admin" }
    val totalStaff = allProfiles.values.count { it.role == "staff" }
    val totalCustomers = allProfiles.values.count { it.role == "customer" }
    val totalMeds = allMedicines.size
    val totalSalesCount = allBills.size

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color(0xFF9C27B0).copy(alpha = 0.2f), CircleShape)
                                .border(1.dp, Color(0xFFE040FB), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = Color(0xFFE040FB),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (isBengali) "সিস্টেম সুপার এডমিন পোর্টাল" else "Super Admin Control Portal",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isBengali) "মাস্টার ইউজারনেম: admin (পাসওয়ার্ড: 4321)" else "Master Username: admin (Pass: 4321)",
                                color = Color(0xFFCE93D8),
                                fontSize = 11.sp
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.logout() }) {
                        Icon(
                            imageVector = Icons.Default.Logout,
                            contentDescription = "Logout",
                            tint = DarkAccentRed
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkSurface)
            )
        },
        containerColor = DarkBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Metrics Overview Banner
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, Color(0xFF9C27B0).copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isBengali) "সিস্টেম সারসংক্ষেপ ও লাইভ মেট্রিকস" else "System Overview & Live Metrics",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFF00E676), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isBengali) "লাইভ সিঙ্ক চালু" else "Live Sync Online",
                                color = Color(0xFF00E676),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 4 Grid stat cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Card 1: Total Users
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkBg)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = if (isBengali) "মোট ইউজার" else "Total Users",
                                    color = DarkTextSecondary,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "$totalAccounts",
                                    color = Color(0xFFE040FB),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isBengali) "মার্চেন্ট: $totalAdmins | স্টাফ: $totalStaff" else "Merchant: $totalAdmins | Staff: $totalStaff",
                                    color = DarkTextSecondary.copy(alpha = 0.7f),
                                    fontSize = 9.sp
                                )
                            }
                        }

                        // Card 2: Registered Pharmacies
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkBg)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = if (isBengali) "ফার্মেসি প্যানেল" else "Pharmacies",
                                    color = DarkTextSecondary,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "$totalAdmins",
                                    color = DarkAccentGold,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isBengali) "সক্রিয় মার্চেন্ট প্যানেল" else "Active Merchant Panels",
                                    color = DarkTextSecondary.copy(alpha = 0.7f),
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Card 3: Total Medicines
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkBg)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = if (isBengali) "মোট ওষুধ তালিকা" else "Total Medicines",
                                    color = DarkTextSecondary,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "$totalMeds",
                                    color = Color(0xFF00E676),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isBengali) "সিস্টেমে সর্বমোট আইটেম" else "Items in System",
                                    color = DarkTextSecondary.copy(alpha = 0.7f),
                                    fontSize = 9.sp
                                )
                            }
                        }

                        // Card 4: Total Sales Transactions
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkBg)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = if (isBengali) "মোট সেলস মেমো" else "Total Sales Logs",
                                    color = DarkTextSecondary,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "$totalSalesCount",
                                    color = Color(0xFF29B6F6),
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isBengali) "মোট ট্রানজেকশন রসিদ" else "Total Processed Bills",
                                    color = DarkTextSecondary.copy(alpha = 0.7f),
                                    fontSize = 9.sp
                                )
                            }
                        }
                    }
                }
            }

            // Tab Selector
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = DarkSurface,
                contentColor = Color(0xFFE040FB),
                indicator = { tabPositions ->
                    if (activeTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                            color = Color(0xFFE040FB)
                        )
                    }
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .padding(bottom = 16.dp)
            ) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = {
                        Text(
                            text = if (isBengali) "মার্চেন্ট ও প্যানেল তালিকা" else "Merchant Panels",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = {
                        Text(
                            text = if (isBengali) "ট্রাফিক ও মনিটর" else "Traffic Monitor",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = {
                        Text(
                            text = if (isBengali) "গ্লোবাল নোটিশ" else "Broadcast",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }

            when (activeTab) {
                0 -> {
                    // TAB 0: Merchant Panels & Account Management
                    Column {
                        // Search bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(if (isBengali) "ইউজারনেম, ফার্মেসির নাম বা ফোন দিয়ে খুঁজুন..." else "Search by username, pharmacy or phone...", color = DarkTextSecondary) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = DarkTextSecondary) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFE040FB),
                                unfocusedBorderColor = SleekBorderColor,
                                focusedTextColor = DarkTextPrimary,
                                unfocusedTextColor = DarkTextPrimary
                            )
                        )

                        // Role Filter Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val filters = listOf(
                                "All" to if (isBengali) "সব ($totalAccounts)" else "All ($totalAccounts)",
                                "admin" to if (isBengali) "মার্চেন্ট ($totalAdmins)" else "Merchants ($totalAdmins)",
                                "staff" to if (isBengali) "স্টাফ ($totalStaff)" else "Staff ($totalStaff)",
                                "customer" to if (isBengali) "কাস্টমার ($totalCustomers)" else "Customers ($totalCustomers)"
                            )

                            filters.forEach { (roleKey, label) ->
                                val isSel = selectedRoleFilter == roleKey
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (isSel) Color(0xFF9C27B0).copy(alpha = 0.3f) else DarkSurface)
                                        .border(1.dp, if (isSel) Color(0xFFE040FB) else SleekBorderColor, RoundedCornerShape(20.dp))
                                        .clickable { selectedRoleFilter = roleKey }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSel) Color(0xFFE040FB) else DarkTextSecondary
                                    )
                                }
                            }
                        }

                        // Filter profiles
                        val filteredList = allProfiles.values.filter { prof ->
                            val matchesRole = when (selectedRoleFilter) {
                                "All" -> true
                                else -> prof.role == selectedRoleFilter
                            }
                            val q = searchQuery.trim().lowercase()
                            val matchesQuery = q.isEmpty() ||
                                    prof.username.lowercase().contains(q) ||
                                    prof.pharmacyName.lowercase().contains(q) ||
                                    prof.ownerName.lowercase().contains(q) ||
                                    prof.phone.lowercase().contains(q) ||
                                    prof.securityCode.lowercase().contains(q)
                            matchesRole && matchesQuery
                        }

                        if (filteredList.isEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                border = BorderStroke(1.dp, SleekBorderColor)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.PersonOff, contentDescription = null, tint = DarkTextSecondary, modifier = Modifier.size(40.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (isBengali) "কোনো একাউন্ট খুঁজে পাওয়া যায়নি" else "No matching user profiles found",
                                        color = DarkTextSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        } else {
                            filteredList.forEach { profile ->
                                var showPassword by remember { mutableStateOf(false) }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = when (profile.role) {
                                            "admin" -> Color(0xFF9C27B0).copy(alpha = 0.5f)
                                            "staff" -> SuccessColor.copy(alpha = 0.4f)
                                            else -> SleekBorderColor
                                        }
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        // Header Row: Pharmacy Name & Role Badge
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = if (profile.pharmacyName.isNotBlank()) profile.pharmacyName else profile.ownerName,
                                                    color = Color.White,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "ইউজারনেম: @${profile.username}",
                                                    color = Color(0xFFE040FB),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }

                                            // Role Badge
                                            val (roleLabel, roleBg, roleFg) = when (profile.role) {
                                                "admin" -> Triple(if (isBengali) "ফার্মেসি মালিক" else "Shop Owner", Color(0xFF9C27B0).copy(alpha = 0.2f), Color(0xFFE040FB))
                                                "staff" -> Triple(if (isBengali) "কর্মচারী" else "Employee", SuccessColor.copy(alpha = 0.2f), Color(0xFF81C784))
                                                else -> Triple(if (isBengali) "কাস্টমার" else "Customer", Color(0xFF29B6F6).copy(alpha = 0.2f), Color(0xFF4FC3F7))
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .background(roleBg, RoundedCornerShape(8.dp))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(text = roleLabel, color = roleFg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Divider(color = SleekBorderColor.copy(alpha = 0.5f))
                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Details Grid
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Person, contentDescription = null, tint = DarkTextSecondary, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (isBengali) "মালিক/নাম: ${profile.ownerName}" else "Name: ${profile.ownerName}",
                                                    color = DarkTextPrimary,
                                                    fontSize = 12.sp
                                                )
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Phone, contentDescription = null, tint = DarkTextSecondary, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (isBengali) "মোবাইল: ${profile.phone}" else "Phone: ${profile.phone}",
                                                    color = DarkTextPrimary,
                                                    fontSize = 12.sp
                                                )
                                            }

                                            if (profile.securityCode.isNotBlank()) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.Key, contentDescription = null, tint = DarkAccentGold, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = if (isBengali) "সিকিউরিটি কোড: ${profile.securityCode}" else "Security Code: ${profile.securityCode}",
                                                        color = DarkAccentGold,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }

                                            if (profile.linkedAdminUsername.isNotBlank()) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.Link, contentDescription = null, tint = Color(0xFF81C784), modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = if (isBengali) "সংযুক্ত মার্চেন্ট: @${profile.linkedAdminUsername}" else "Linked Merchant: @${profile.linkedAdminUsername}",
                                                        color = Color(0xFF81C784),
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }

                                            // Password view row
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.Lock, contentDescription = null, tint = DarkTextSecondary, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = if (isBengali) "পাসওয়ার্ড: " else "Password: ",
                                                    color = DarkTextSecondary,
                                                    fontSize = 12.sp
                                                )
                                                Text(
                                                    text = if (showPassword) profile.pass else "••••••••",
                                                    color = DarkTextPrimary,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Icon(
                                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = "Toggle password",
                                                    tint = Color(0xFFE040FB),
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clickable { showPassword = !showPassword }
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Action buttons
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            // Edit Button
                                            OutlinedButton(
                                                onClick = {
                                                    profileToEdit = profile
                                                    editPharmacyName = profile.pharmacyName
                                                    editOwnerName = profile.ownerName
                                                    editPhone = profile.phone
                                                    editPassword = profile.pass
                                                    showEditDialog = true
                                                },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(8.dp),
                                                border = BorderStroke(1.dp, Color(0xFFE040FB).copy(alpha = 0.5f)),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE040FB))
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(if (isBengali) "এডিট / পাসওয়ার্ড" else "Edit Account", fontSize = 11.sp)
                                            }

                                            // Delete Admin Panel / Profile Button
                                            Button(
                                                onClick = { profileToDelete = profile },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = DarkAccentRed.copy(alpha = 0.2f), contentColor = DarkAccentRed),
                                                border = BorderStroke(1.dp, DarkAccentRed.copy(alpha = 0.4f))
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp), tint = DarkAccentRed)
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(if (isBengali) "প্যানেল মুছুন" else "Delete Panel", fontSize = 11.sp, color = DarkAccentRed, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // TAB 1: System Traffic & Performance Monitor
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = BorderStroke(1.dp, SleekBorderColor)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = if (isBengali) "লাইভ সিস্টেম ট্রাফিক মনিটর" else "Live System Traffic Monitor",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Column {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(if (isBengali) "ফায়ারবেস রিয়েলটাইম সিঙ্ক ট্রাফিক" else "Firebase Realtime DB Sync", color = DarkTextSecondary, fontSize = 12.sp)
                                            Text("100% (অনলাইন)", color = Color(0xFF00E676), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        LinearProgressIndicator(
                                            progress = 1.0f,
                                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                            color = Color(0xFF00E676),
                                            trackColor = DarkBg
                                        )
                                    }

                                    Column {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(if (isBengali) "প্রিন্টার ও পিওএস ট্রাফিক লোড" else "POS Printer Job Queue Load", color = DarkTextSecondary, fontSize = 12.sp)
                                            Text("স্বাভাবিক (Sleek)", color = Color(0xFF29B6F6), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        LinearProgressIndicator(
                                            progress = 0.25f,
                                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                            color = Color(0xFF29B6F6),
                                            trackColor = DarkBg
                                        )
                                    }

                                    Column {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(if (isBengali) "সিস্টেম সিকিউরিটি ও ডাটাবেস লেটেন্সি" else "Database Latency", color = DarkTextSecondary, fontSize = 12.sp)
                                            Text("12ms (অতি দ্রুত)", color = Color(0xFFE040FB), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        LinearProgressIndicator(
                                            progress = 0.15f,
                                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                            color = Color(0xFFE040FB),
                                            trackColor = DarkBg
                                        )
                                    }
                                }
                            }
                        }

                        // Recent System Logs
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = BorderStroke(1.dp, SleekBorderColor)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = if (isBengali) "সাম্প্রতিক সিস্টেম অ্যাক্টিভিটি লগ" else "Recent System Activity Logs",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                val logsList = listOf(
                                    "সুপার এডমিন লগইন সক্রিয়: @admin",
                                    "সিস্টেম ডাটাবেস রিয়েলটাইম সিঙ্ক কানেক্টেড",
                                    "মোট ফার্মেসি প্যানেল তৈরি: $totalAdmins টি",
                                    "সর্বমোট ওষুধের স্টক ডাটাবেস রেডি: $totalMeds টি"
                                )

                                logsList.forEach { logText ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(logText, color = DarkTextPrimary, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // TAB 2: Global Broadcast Notice
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                        border = BorderStroke(1.dp, Color(0xFF9C27B0).copy(alpha = 0.3f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = if (isBengali) "গ্লোবাল সিস্টেম নোটিশ পাঠান" else "Send Global System Broadcast",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (isBengali) "সকল নিবন্ধিত ফার্মেসি মার্চেন্ট ও স্টাফ প্যানেলে ব্রডকাস্ট নোটিফিকেশন পাঠাতে পারবেন।" else "Broadcast notification to all registered pharmacy merchant & staff panels.",
                                color = DarkTextSecondary,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = broadcastTitle,
                                onValueChange = { broadcastTitle = it },
                                label = { Text(if (isBengali) "নোটিশের শিরোনাম (Title)" else "Broadcast Title") },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFE040FB),
                                    unfocusedBorderColor = SleekBorderColor,
                                    focusedTextColor = DarkTextPrimary,
                                    unfocusedTextColor = DarkTextPrimary
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )

                            OutlinedTextField(
                                value = broadcastMsg,
                                onValueChange = { broadcastMsg = it },
                                label = { Text(if (isBengali) "নোটিশের বার্তা (Message)" else "Message Body") },
                                modifier = Modifier.fillMaxWidth().height(100.dp).padding(bottom = 12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFE040FB),
                                    unfocusedBorderColor = SleekBorderColor,
                                    focusedTextColor = DarkTextPrimary,
                                    unfocusedTextColor = DarkTextPrimary
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )

                            if (broadcastSuccessMsg.isNotEmpty()) {
                                Text(
                                    text = broadcastSuccessMsg,
                                    color = Color(0xFF00E676),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }

                            Button(
                                onClick = {
                                    if (broadcastMsg.isNotBlank()) {
                                        viewModel.addNotification(
                                            titleEn = if (broadcastTitle.isNotBlank()) broadcastTitle else "System Notice",
                                            titleBn = if (broadcastTitle.isNotBlank()) broadcastTitle else "সিস্টেম নোটিশ",
                                            messageEn = broadcastMsg,
                                            messageBn = broadcastMsg,
                                            targetRole = "ALL"
                                        )
                                        broadcastSuccessMsg = if (isBengali) "নোটিশ সফলভাবে পাঠানো হয়েছে!" else "Broadcast notice sent successfully!"
                                        broadcastMsg = ""
                                        broadcastTitle = ""
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(46.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0), contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Campaign, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isBengali) "নোটিশ ব্রডকাস্ট করুন" else "Broadcast Notice", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    profileToDelete?.let { prof ->
        AlertDialog(
            onDismissRequest = { profileToDelete = null },
            title = {
                Text(
                    text = if (isBengali) "মার্চেন্ট প্যানেল মুছে ফেলবেন?" else "Delete Merchant Panel?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isBengali)
                        "আপনি কি নিশ্চিত যে '${prof.pharmacyName.ifEmpty { prof.username }}' (ইউজারনেম: @${prof.username}) একাউন্টটি সিস্টেম থেকে স্থায়ীভাবে মুছে ফেলতে চান? এটি আর ফেরানো যাবে না।"
                    else
                        "Are you sure you want to permanently delete account '${prof.pharmacyName.ifEmpty { prof.username }}' (@${prof.username}) from the system?",
                    color = DarkTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteUserProfile(prof.username)
                        android.widget.Toast.makeText(
                            context,
                            if (isBengali) "'${prof.username}' মুছে ফেলা হয়েছে।" else "'${prof.username}' deleted successfully.",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        profileToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkAccentRed, contentColor = Color.White)
                ) {
                    Text(if (isBengali) "হ্যাঁ, মুছে ফেলুন" else "Yes, Delete")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { profileToDelete = null },
                    border = BorderStroke(1.dp, SleekBorderColor)
                ) {
                    Text(if (isBengali) "বাতিল" else "Cancel", color = DarkTextPrimary)
                }
            },
            containerColor = DarkSurface,
            titleContentColor = Color.White,
            textContentColor = DarkTextSecondary
        )
    }

    // Edit Profile/Password Dialog
    if (showEditDialog && profileToEdit != null) {
        val target = profileToEdit!!
        AlertDialog(
            onDismissRequest = { showEditDialog = false; profileToEdit = null },
            title = {
                Text(
                    text = if (isBengali) "একাউন্ট এডিট / পাসওয়ার্ড পরিবর্তন" else "Edit Account Credentials",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "ইউজারনেম: @${target.username}", color = Color(0xFFE040FB), fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    OutlinedTextField(
                        value = editPharmacyName,
                        onValueChange = { editPharmacyName = it },
                        label = { Text(if (isBengali) "ফার্মেসির নাম" else "Pharmacy Name") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFE040FB), unfocusedBorderColor = SleekBorderColor, focusedTextColor = DarkTextPrimary, unfocusedTextColor = DarkTextPrimary)
                    )

                    OutlinedTextField(
                        value = editOwnerName,
                        onValueChange = { editOwnerName = it },
                        label = { Text(if (isBengali) "মালিকের নাম" else "Owner Name") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFE040FB), unfocusedBorderColor = SleekBorderColor, focusedTextColor = DarkTextPrimary, unfocusedTextColor = DarkTextPrimary)
                    )

                    OutlinedTextField(
                        value = editPhone,
                        onValueChange = { editPhone = it },
                        label = { Text(if (isBengali) "মোবাইল নম্বর" else "Phone Number") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFE040FB), unfocusedBorderColor = SleekBorderColor, focusedTextColor = DarkTextPrimary, unfocusedTextColor = DarkTextPrimary)
                    )

                    OutlinedTextField(
                        value = editPassword,
                        onValueChange = { editPassword = it },
                        label = { Text(if (isBengali) "নতুন পাসওয়ার্ড" else "New Password") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFFE040FB), unfocusedBorderColor = SleekBorderColor, focusedTextColor = DarkTextPrimary, unfocusedTextColor = DarkTextPrimary)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateUserProfileBySuperAdmin(
                            targetUsername = target.username,
                            newPharmacyName = editPharmacyName,
                            newOwnerName = editOwnerName,
                            newPhone = editPhone,
                            newPass = editPassword
                        )
                        android.widget.Toast.makeText(
                            context,
                            if (isBengali) "একাউন্ট আপডেট সফল হয়েছে!" else "Account updated successfully!",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        showEditDialog = false
                        profileToEdit = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9C27B0), contentColor = Color.White)
                ) {
                    Text(if (isBengali) "সংরক্ষণ করুন" else "Save Changes")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showEditDialog = false; profileToEdit = null },
                    border = BorderStroke(1.dp, SleekBorderColor)
                ) {
                    Text(if (isBengali) "বাতিল" else "Cancel", color = DarkTextPrimary)
                }
            },
            containerColor = DarkBg
        )
    }
}

