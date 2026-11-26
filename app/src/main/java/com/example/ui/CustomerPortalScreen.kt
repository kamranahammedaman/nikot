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
@android.annotation.SuppressLint("MissingPermission")
fun CustomerPortalScreen(viewModel: PharmacyViewModel, isBengali: Boolean) {
    val medicines by viewModel.allMedicines.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val customerOrders by viewModel.allCustomerOrders.collectAsState()
    val customerCart by viewModel.customerCart.collectAsState()
    val userProfiles by viewModel.userProfiles.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val notifications by viewModel.notifications.collectAsState()
    val unreadNotifCount = notifications.count { !it.isRead }

    // Active customer tab: 0 = "ওষুধ ও ম্যাপ" / "Meds & Map", 1 = "আমার কার্ট" / "My Cart", 2 = "আমার অর্ডার" / "My Orders"
    var activeTab by remember { mutableStateOf(0) }

    var searchQuery by remember { mutableStateOf("") }
    
    // Shared Location Coordinates & Location Dialog Control
    var showLocationSettingsDialog by remember { mutableStateOf(false) }

    var userLatitude by remember { 
        mutableStateOf<Double?>(if (currentUser.latitude != 0.0) currentUser.latitude else null) 
    }
    var userLongitude by remember { 
        mutableStateOf<Double?>(if (currentUser.longitude != 0.0) currentUser.longitude else null) 
    }
    var isSharingLocation by remember { mutableStateOf(currentUser.isLocationSet) }
    
    var selectedWardIndex by remember { mutableStateOf(0) }
    var selectedNeighborhoodIndex by remember { mutableStateOf(0) }

    // Synchronize coordinates when currentUser settings change
    LaunchedEffect(currentUser) {
        if (currentUser.latitude != 0.0 && currentUser.longitude != 0.0) {
            userLatitude = currentUser.latitude
            userLongitude = currentUser.longitude
            isSharingLocation = true
        } else if (!currentUser.isLocationSet) {
            userLatitude = null
            userLongitude = null
            isSharingLocation = false
        }
    }

    // Selected medicine for locator details
    var selectedLocatorMedicine by remember { mutableStateOf<Medicine?>(null) }
    // Selected pharmacy to pinpoint / order
    var chosenPharmacyForOrder by remember { mutableStateOf<SimulatedPharmacy?>(null) }
    var orderQuantity by remember { mutableStateOf(1) }
    var showOrderDialog by remember { mutableStateOf(false) }

    // Add to Cart States
    var showAddToCartDialog by remember { mutableStateOf(false) }
    var medicineToAddToCart by remember { mutableStateOf<Medicine?>(null) }
    var addToCartQty by remember { mutableStateOf(1) }
    var showEditProfileDialog by remember { mutableStateOf(false) }

    // Device location manager logic
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseGranted = permissions[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (fineGranted || coarseGranted) {
            try {
                val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
                val lastKnown = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(android.location.LocationManager.NETWORK_PROVIDER)
                if (lastKnown != null) {
                    userLatitude = lastKnown.latitude
                    userLongitude = lastKnown.longitude
                    isSharingLocation = true
                    android.widget.Toast.makeText(context, if (isBengali) "লাইভ GPS লোকেশন সক্রিয় হয়েছে!" else "Live GPS Location activated!", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(context, if (isBengali) "GPS লোকেশন পাওয়া যায়নি। সেটিংসে লোকেশন নির্ধারণ করুন।" else "GPS not found. Please set location in Settings.", android.widget.Toast.LENGTH_LONG).show()
                }
            } catch (e: SecurityException) {
                android.widget.Toast.makeText(context, if (isBengali) "লোকেশন পারমিশন সমস্যা।" else "Location permission issue.", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            android.widget.Toast.makeText(context, if (isBengali) "লোকেশন পারমিশন মেলেনি। সেটিংসে গিয়ে লোকেশন নির্ধারণ করুন।" else "Permission Denied. Please set location in Settings.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    // Deduplicated and grouped medicine search results
    // প্রতিটি ওষুধ একবারই প্রদর্শিত হবে এবং সব ফার্মেসির স্টক/অফার অন্তর্ভুক্ত থাকবে
    val groupedMedicines = remember(searchQuery, medicines) {
        if (searchQuery.trim().isEmpty()) {
            emptyList<CustomerGroupedMedicine>()
        } else {
            val q = searchQuery.trim()
            val matches = medicines.filter {
                it.name.contains(q, ignoreCase = true) ||
                it.fullName.contains(q, ignoreCase = true) ||
                it.genericName.contains(q, ignoreCase = true)
            }
            matches.groupBy {
                "${it.name.trim().lowercase()}__${it.dosageStrength.trim().lowercase()}__${it.type.trim().lowercase()}"
            }.map { (key, group) ->
                val first = group.first()
                CustomerGroupedMedicine(
                    groupKey = key,
                    name = first.name,
                    dosageStrength = first.dosageStrength,
                    genericName = first.genericName,
                    type = first.type,
                    salePriceBox = group.map { it.salePriceBox }.firstOrNull { it > 0 } ?: first.salePriceBox,
                    salePriceStrip = group.map { it.salePriceStrip }.firstOrNull { it > 0 } ?: first.salePriceStrip,
                    salePricePiece = group.map { it.salePricePiece }.firstOrNull { it > 0 } ?: first.salePricePiece,
                    salePriceUnit = group.map { it.salePriceUnit }.firstOrNull { it > 0 } ?: first.salePriceUnit,
                    pharmacyOfferings = group
                )
            }
        }
    }

    val currentLat: Double? = if (userLatitude != null && userLatitude != 0.0) userLatitude else if (currentUser.latitude != 0.0) currentUser.latitude else null
    val currentLon: Double? = if (userLongitude != null && userLongitude != 0.0) userLongitude else if (currentUser.longitude != 0.0) currentUser.longitude else null

    // Distance computation helper using accurate coordinate shifts (returns null if user has not set location)
    fun calculateDiffDistance(pLat: Double, pLon: Double): Double? {
        val uLat = currentLat ?: return null
        val uLon = currentLon ?: return null
        val latDiff = pLat - uLat
        val lonDiff = pLon - uLon
        val roughDist = Math.sqrt(latDiff * latDiff + lonDiff * lonDiff) * 111.0f // roughly 111km per degree
        return if (roughDist <= 0.05) 0.15 else roughDist
    }

    // Formatted list of nearby pharmacies in Sylhet City dynamically loaded from Firebase users
    val nearbyPharmacies = remember(userProfiles, medicines, userLatitude, userLongitude, isSharingLocation) {
        userProfiles.values.filter { it.role == "admin" }.map { profile ->
            val pLat = profile.latitude
            val pLng = profile.longitude
            val isAvailable = medicines.any { med ->
                med.pharmacyOwnerUsername == profile.username && (
                    when (med.type.lowercase().trim()) {
                        "tablet" -> med.totalPiecesInStock > 0
                        "syrup" -> med.bottlesInStock > 0
                        "ointment" -> med.tubesInStock > 0
                        "injection" -> med.vialsInStock > 0
                        else -> med.otherQuantity > 0.0
                    }
                )
            }
            SimulatedPharmacy(
                name = profile.pharmacyName,
                nameEn = profile.pharmacyName,
                lat = pLat,
                lLng = pLng,
                stockAvailable = isAvailable,
                baseDistanceKm = calculateDiffDistance(pLat, pLng) ?: 0.0,
                description = profile.address,
                descriptionEn = profile.address,
                ownerUsername = profile.username
            )
        }
    }

    fun getPharmacyForMedicine(medicine: Medicine): SimulatedPharmacy {
        val ownerUser = viewModel.userProfiles.value[medicine.pharmacyOwnerUsername] ?: PharmacyViewModel.UserProfile(
            username = medicine.pharmacyOwnerUsername,
            pharmacyName = if (medicine.pharmacyOwnerUsername == "admin") "আমার ফার্মেসী" else medicine.pharmacyOwnerUsername,
            address = "সিলেট সিটি, বাংলাদেশ",
            phone = "+৮৮০ ১৭০০-০০০০০০",
            ownerName = medicine.pharmacyOwnerUsername,
            pass = "1234",
            role = "admin"
        )
        val isAvailable = when (medicine.type) {
            "tablet" -> medicine.totalPiecesInStock > 0
            "syrup" -> medicine.bottlesInStock > 0
            "ointment" -> medicine.tubesInStock > 0
            "injection" -> medicine.vialsInStock > 0
            else -> medicine.otherQuantity > 0.0
        }
        
        val pLat = ownerUser.latitude
        val pLng = ownerUser.longitude

        return SimulatedPharmacy(
            name = ownerUser.pharmacyName,
            nameEn = ownerUser.pharmacyName,
            lat = pLat,
            lLng = pLng,
            stockAvailable = isAvailable,
            baseDistanceKm = calculateDiffDistance(pLat, pLng) ?: 0.0,
            description = ownerUser.address,
            descriptionEn = ownerUser.address,
            ownerUsername = ownerUser.username
        )
    }

    val cartItemsGroupedByPharmacy = remember(customerCart, medicines) {
        val cartMedicinesAndQty = customerCart.mapNotNull { (medId, qty) ->
            val med = medicines.find { it.id == medId }
            if (med != null) Pair(med, qty) else null
        }
        if (cartMedicinesAndQty.isEmpty()) {
            emptyMap<SimulatedPharmacy, List<Pair<Medicine, Int>>>()
        } else {
            // Find unique demands by brand name & dosage strength (case-insensitive)
            val uniqueDemands = cartMedicinesAndQty.groupBy { (med, _) ->
                med.name.lowercase().trim() to med.dosageStrength.lowercase().trim()
            }.map { (key, list) ->
                val repMedicine = list.first().first
                val totalDemandedQty = list.sumOf { it.second }
                Pair(repMedicine, totalDemandedQty)
            }

            // Find all pharmacies that carry at least one of these unique medicines
            val pharmacyOwnerUsernames = medicines.filter { med ->
                uniqueDemands.any { (demMed, _) ->
                    med.name.equals(demMed.name, ignoreCase = true) &&
                    med.dosageStrength.equals(demMed.dosageStrength, ignoreCase = true)
                }
            }.map { it.pharmacyOwnerUsername }.distinct()

            // Map each pharmacy to its list of matching items
            pharmacyOwnerUsernames.mapNotNull { username ->
                val profile = viewModel.userProfiles.value[username] ?: PharmacyViewModel.UserProfile(
                    username = username,
                    pharmacyName = if (username == "admin") "আমার ফার্মেসী" else username,
                    address = "সিলেট সিটি, বাংলাদেশ",
                    phone = "+৮৮০ ১৭০০-০০০০০০",
                    ownerName = username,
                    pass = "1234",
                    role = "admin"
                )
                if (profile != null) {
                    // Find any medicine from this pharmacy to generate its pharmacy details
                    val medForPharmacy = medicines.find { it.pharmacyOwnerUsername == username }
                    if (medForPharmacy != null) {
                        val pharmacy = getPharmacyForMedicine(medForPharmacy)
                        
                        val items = uniqueDemands.mapNotNull { (repMed, demandedQty) ->
                            val pharmacyMed = medicines.find { med ->
                                med.pharmacyOwnerUsername == username &&
                                med.name.equals(repMed.name, ignoreCase = true) &&
                                med.dosageStrength.equals(repMed.dosageStrength, ignoreCase = true)
                            } ?: Medicine(
                                type = repMed.type,
                                name = repMed.name,
                                dosageStrength = repMed.dosageStrength,
                                company = repMed.company,
                                genericName = repMed.genericName,
                                barcode = repMed.barcode,
                                expiryDate = repMed.expiryDate,
                                pharmacyOwnerUsername = username,
                                purchasePriceBox = repMed.purchasePriceBox,
                                salePriceBox = repMed.salePriceBox,
                                stripsPerBox = repMed.stripsPerBox,
                                pcsPerStrip = repMed.pcsPerStrip,
                                boxesInStock = 0,
                                stripsInStock = 0,
                                pcsInStock = 0,
                                purchasePriceUnit = repMed.purchasePriceUnit,
                                salePriceUnit = repMed.salePriceUnit
                            )
                            val stock = when (pharmacyMed.type) {
                                "tablet" -> pharmacyMed.totalPiecesInStock
                                "syrup" -> pharmacyMed.bottlesInStock
                                "ointment" -> pharmacyMed.tubesInStock
                                "injection" -> pharmacyMed.vialsInStock
                                else -> pharmacyMed.otherQuantity.toInt()
                            }
                            if (stock > 0 || username == repMed.pharmacyOwnerUsername) {
                                Pair(pharmacyMed, demandedQty)
                            } else {
                                null
                            }
                        }

                        if (items.isNotEmpty()) {
                            Pair(pharmacy, items)
                        } else {
                            null
                        }
                    } else null
                } else null
            }.toMap()
        }
    }

    var showPortalSwitchDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(GlassFillStrong)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RoLogoHeader()
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(DarkAccentRed.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .border(1.dp, DarkAccentRed.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (isBengali) "কনজিউমার পোর্টাল" else "Consumer Portal",
                            color = DarkAccentRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                GlassIconButton(
                    onClick = { showEditProfileDialog = true },
                    icon = Icons.Filled.Edit,
                    contentDescription = if (isBengali) "প্রোফাইল পরিবর্তন" else "Edit Profile",
                    tint = DarkTextSecondary,
                    size = 36.dp
                )
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color.Transparent,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(GlassFillStrong, RoundedCornerShape(26.dp))
                    .border(1.dp, GlassBorder, RoundedCornerShape(26.dp))
            ) {
                val tabs = listOf(
                    Triple(if (isBengali) "ওষুধ খুঁজুন" else "Search Meds", Icons.Filled.Search, Icons.Outlined.Search),
                    Triple(if (isBengali) "আমার কার্ট" else "My Cart", Icons.Filled.ShoppingCart, Icons.Outlined.ShoppingCart),
                    Triple(if (isBengali) "অর্ডার ইতিহাস" else "Orders", Icons.Filled.ListAlt, Icons.Outlined.ListAlt),
                    Triple(if (isBengali) "নোটিফিকেশন" else "Notifications", Icons.Filled.Notifications, Icons.Outlined.Notifications),
                    Triple(if (isBengali) "সেটিংস" else "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
                )

                tabs.forEachIndexed { index, (label, filledIcon, outlinedIcon) ->
                    val finalLabel = if (index == 1) {
                        if (isBengali) "আমার কার্ট (${formatBengaliNumber(customerCart.size)})" else "My Cart (${customerCart.size})"
                    } else if (index == 2) {
                        val count = customerOrders.filter { it.customerUsername == currentUser.username }.size
                        if (isBengali) "অর্ডার হিস্ট্রি (${formatBengaliNumber(count)})" else "My Orders (${count})"
                    } else {
                        label
                    }
                    NavigationBarItem(
                        selected = activeTab == index,
                        onClick = { activeTab = index },
                        label = { Text(finalLabel, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                        icon = {
                            Box {
                                Icon(
                                    imageVector = if (activeTab == index) filledIcon else outlinedIcon,
                                    contentDescription = label
                                )
                                if (index == 3 && unreadNotifCount > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .offset(x = 6.dp, y = (-6).dp)
                                            .size(16.dp)
                                            .background(DarkAccentRed, androidx.compose.foundation.shape.CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (isBengali) formatBengaliNumber(unreadNotifCount) else unreadNotifCount.toString(),
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = if (isDarkModeGlobal) Color.Black else Color.White,
                            unselectedIconColor = DarkTextSecondary.copy(alpha = 0.6f),
                            selectedTextColor = DarkTextPrimary,
                            unselectedTextColor = DarkTextSecondary.copy(alpha = 0.5f),
                            indicatorColor = DarkAccentRed
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(GlassBackgroundGradient)
        ) {
            // TAB PANELS INTERCHANGING
            if (activeTab == 0) {
                // PANEL 1: SELECTION & MEDICINE SEARCH MAP RETRIEVER
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    Spacer(modifier = Modifier.height(10.dp))

                    // Consumer Location Status Card
                    val hasLocation = currentUser.isLocationSet || (userLatitude != null && userLatitude != 0.0)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showLocationSettingsDialog = true },
                        colors = CardDefaults.cardColors(
                            containerColor = if (hasLocation) Color(0xFF1E2620) else Color(0xFF261D1D)
                        ),
                        border = BorderStroke(1.dp, if (hasLocation) SuccessColor.copy(alpha = 0.5f) else DarkAccentGold.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Icon(
                                    imageVector = if (hasLocation) Icons.Filled.LocationOn else Icons.Filled.LocationOff,
                                    contentDescription = "Location",
                                    tint = if (hasLocation) SuccessColor else DarkAccentGold,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = if (hasLocation) {
                                            if (currentUser.address.isNotBlank()) currentUser.address
                                            else if (currentUser.ward.isNotBlank()) "${currentUser.ward}, ${currentUser.neighborhood}"
                                            else if (isBengali) "লাইভ GPS লোকেশন সক্রিয়" else "Live GPS Location Active"
                                        } else {
                                            if (isBengali) "লোকেশন সেট করা হয়নি" else "Location Not Set"
                                        },
                                        color = DarkTextPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (hasLocation) {
                                            if (isBengali) "আপনার নিজস্ব লোকেশন অনুযায়ী ফলাফল দেখানো হচ্ছে" else "Showing results based on your set location"
                                        } else {
                                            if (isBengali) "একাউন্টে লোকেশন সেট করুন (অন্য কোনো লোকেশন দেখানো হবে না)" else "Set your location in Account (no other location used)"
                                        },
                                        color = DarkTextSecondary,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (hasLocation) SuccessColor.copy(alpha = 0.15f) else DarkAccentGold.copy(alpha = 0.15f),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (hasLocation) (if (isBengali) "পরিবর্তন" else "Change") else (if (isBengali) "সেট করুন" else "Set"),
                                    color = if (hasLocation) Color(0xFF81C784) else DarkAccentGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text(if (isBengali) "কাঙ্ক্ষিত ওষুধের নাম দিয়ে খুঁজুন..." else "Type medicine or chemical name...") },
                        leadingIcon = { Icon(Icons.Filled.ListAlt, contentDescription = "Search icon") },
                        textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DarkAccentRed,
                            unfocusedBorderColor = DarkTextSecondary.copy(alpha = 0.5f),
                            focusedLabelColor = DarkAccentRed,
                            unfocusedLabelColor = DarkTextSecondary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (searchQuery.trim().isEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = "Search Prompt Icon",
                                    tint = DarkTextSecondary.copy(alpha = 0.6f),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = if (isBengali) "ওষুধের নাম বা জেনেরিক নাম লিখে খুঁজুন" else "Search medicines by name or generic formula",
                                    color = DarkTextSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (groupedMedicines.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (isBengali) "দুঃখিত, কোনো ওষুধ খুঁজে পাওয়া যায়নি!" else "No matching items found!",
                                            color = DarkTextSecondary,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            } else {
                                items(groupedMedicines.size) { index ->
                                    val medGroup = groupedMedicines[index]
                                    CustomerMedicineCard(
                                        medGroup = medGroup,
                                        isBengali = isBengali,
                                        userProfiles = userProfiles,
                                        currentLat = currentLat,
                                        currentLon = currentLon,
                                        onAddToCart = { targetMed ->
                                            medicineToAddToCart = targetMed
                                            addToCartQty = 1
                                            showAddToCartDialog = true
                                        },
                                        onOrderNow = { targetMed, targetPharmacy ->
                                            selectedLocatorMedicine = targetMed
                                            chosenPharmacyForOrder = targetPharmacy
                                            orderQuantity = 1
                                            showOrderDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (activeTab == 1) {
                // PANEL 3: CUSTOMER CART VIEW
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = if (isBengali) "আমার কার্ট তালিকা (ফার্মেসি অনুযায়ী বিভক্ত):" else "My Cart (Grouped by Sourced Pharmacies):",
                        color = DarkTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (customerCart.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Filled.ShoppingCart,
                                    contentDescription = null,
                                    tint = DarkTextSecondary,
                                    modifier = Modifier.size(54.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (isBengali) "আপনার কার্ট বর্তমানে খালি রয়েছে!\nওষুধের তালিকা থেকে ওষুধ কার্টে যুক্ত করুন।" else "Your cart is empty!\nAdd medicines from the search list.",
                                    color = DarkTextSecondary,
                                    fontSize = 13.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    } else if (cartItemsGroupedByPharmacy.isEmpty()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = DarkAccentRed,
                                    modifier = Modifier.size(54.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = if (isBengali) 
                                        "আপনার কার্টের ওষুধগুলো বর্তমানে ডাটাবেজে পাওয়া যাচ্ছে না অথবা আউট অব স্টক রয়েছে!\nঅনুগ্রহ করে কার্ট খালি করে আবার নতুন ওষুধ যুক্ত করুন।"
                                    else 
                                        "The medicines in your cart are currently not found in the database or are out of stock!\nPlease clear your cart and add them again.",
                                    color = DarkTextSecondary,
                                    fontSize = 13.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        viewModel.clearCustomerCart()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkAccentRed),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = if (isBengali) "কার্ট খালি করুন" else "Clear Cart",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }
                    } else {
                        var expandedPharmacyMapName by remember { mutableStateOf<String?>(null) }

                        val sortedGroupedCart = remember(cartItemsGroupedByPharmacy, isSharingLocation, userLatitude, userLongitude) {
                            cartItemsGroupedByPharmacy.toList().sortedWith(
                                compareByDescending<Pair<SimulatedPharmacy, List<Pair<Medicine, Int>>>> { (pharmacy, items) ->
                                    val isFullySatisfied = items.all { (med, qty) ->
                                        val stock = when (med.type) {
                                            "tablet" -> med.totalPiecesInStock
                                            "syrup" -> med.bottlesInStock
                                            "ointment" -> med.tubesInStock
                                            "injection" -> med.vialsInStock
                                            else -> med.otherQuantity.toInt()
                                        }
                                        stock >= qty
                                    }
                                    isFullySatisfied
                                }.thenBy { (pharmacy, _) ->
                                    calculateDiffDistance(pharmacy.lat, pharmacy.lLng) ?: 999999.0
                                }
                            )
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {

                            sortedGroupedCart.forEach { (pharmacy, items) ->
                                val pharmacyDistance = calculateDiffDistance(pharmacy.lat, pharmacy.lLng)

                                val isMapOpen = expandedPharmacyMapName == pharmacy.name

                                val pharmacyAllInStock = items.all { (med, qty) ->
                                    val stock = when (med.type) {
                                        "tablet" -> med.totalPiecesInStock
                                        "syrup" -> med.bottlesInStock
                                        "ointment" -> med.tubesInStock
                                        "injection" -> med.vialsInStock
                                        else -> med.otherQuantity.toInt()
                                    }
                                    stock >= qty
                                }

                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                        border = BorderStroke(1.5.dp, SuccessColor.copy(alpha = 0.3f)),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            // Pharmacy Header
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                Column(modifier = Modifier.weight(0.7f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(10.dp)
                                                                .background(if (pharmacyAllInStock) SuccessColor else DarkAccentRed, RoundedCornerShape(50.dp))
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = if (isBengali) pharmacy.name else pharmacy.nameEn,
                                                            color = DarkTextPrimary,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .background(
                                                                    color = if (pharmacyAllInStock) SuccessColor.copy(alpha = 0.15f) else DarkAccentRed.copy(alpha = 0.15f),
                                                                    shape = RoundedCornerShape(4.dp)
                                                                )
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = if (pharmacyAllInStock) {
                                                                    if (isBengali) "পূর্ণ স্টক" else "Full Stock"
                                                                } else {
                                                                    if (isBengali) "কম স্টক" else "Low Stock"
                                                                },
                                                                color = if (pharmacyAllInStock) SuccessColor else DarkAccentRed,
                                                                fontSize = 9.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = if (isBengali) pharmacy.description else pharmacy.descriptionEn,
                                                        color = DarkTextSecondary,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                                Column(horizontalAlignment = Alignment.End) {
                                                    if (pharmacyDistance != null) {
                                                        Text(
                                                            text = "${if (isBengali) "দূরত্ব: " else "Dist: "}${formatBengaliNumber(String.format("%.2f", pharmacyDistance))} কি.মি.",
                                                            color = InfoColor,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 12.sp
                                                        )
                                                    } else {
                                                        Text(
                                                            text = if (isBengali) "দূরত্ব: সেট নেই" else "Dist: Not set",
                                                            color = DarkTextSecondary.copy(alpha = 0.7f),
                                                            fontSize = 11.sp
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))
                                            Divider(color = Color.White.copy(alpha = 0.05f))
                                            Spacer(modifier = Modifier.height(8.dp))

                                            // Medicines from this pharmacy
                                            items.forEach { (med, qty) ->
                                                val stock = when (med.type) {
                                                    "tablet" -> med.totalPiecesInStock
                                                    "syrup" -> med.bottlesInStock
                                                    "ointment" -> med.tubesInStock
                                                    "injection" -> med.vialsInStock
                                                    else -> med.otherQuantity.toInt()
                                                }
                                                val hasEnoughStock = stock >= qty

                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(0.7f)) {
                                                        Text(
                                                            text = med.name + (if (med.dosageStrength.isNotEmpty()) " ${med.dosageStrength}" else ""),
                                                            color = DarkTextPrimary,
                                                            fontWeight = FontWeight.Medium,
                                                            fontSize = 13.sp
                                                        )
                                                        Text(
                                                            text = "${if (isBengali) "পরিমাণ: " else "Qty: "}${med.formatQuantityString(qty, isBengali)}",
                                                            color = DarkTextSecondary,
                                                            fontSize = 11.sp
                                                        )

                                                        // Stock availability status message
                                                        if (!hasEnoughStock) {
                                                            Text(
                                                                text = if (isBengali) "[ স্টকে আছে ${med.formatQuantityString(stock, isBengali)} ]" else "[ In stock: ${med.formatQuantityString(stock, isBengali)} ]",
                                                                color = DarkAccentRed,
                                                                fontSize = 10.5.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }

                                                        val priceHint = if (med.type == "tablet") {
                                                            if (isBengali) {
                                                                "বক্স: ৳${formatBengaliNumber(String.format("%.1f", med.salePriceBox))} | পাতা: ৳${formatBengaliNumber(String.format("%.1f", med.salePriceStrip))} | পিস: ৳${formatBengaliNumber(String.format("%.1f", med.salePricePiece))}"
                                                            } else {
                                                                "Box: ৳${String.format("%.1f", med.salePriceBox)} | Strip: ৳${String.format("%.1f", med.salePriceStrip)} | Pc: ৳${String.format("%.1f", med.salePricePiece)}"
                                                            }
                                                        } else {
                                                            if (isBengali) {
                                                                "৳${formatBengaliNumber(String.format("%.1f", med.salePriceUnit))} / পিস"
                                                            } else {
                                                                "৳${String.format("%.1f", med.salePriceUnit)} / Pc"
                                                            }
                                                        }
                                                        Text(
                                                            text = priceHint,
                                                            color = DarkTextSecondary.copy(alpha = 0.7f),
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.weight(0.3f),
                                                        horizontalArrangement = Arrangement.End
                                                    ) {
                                                        Text(
                                                            text = "৳${formatBengaliNumber(String.format("%.1f", med.calculatePriceForQuantity(qty)))}",
                                                            color = DarkAccentGold,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        IconButton(
                                                            onClick = {
                                                                // Remove all cart entries matching this name and dosage strength
                                                                val matchingCartIds = customerCart.keys.filter { cartId ->
                                                                    val m = medicines.find { it.id == cartId }
                                                                    m != null && m.name.equals(med.name, ignoreCase = true) && m.dosageStrength.equals(med.dosageStrength, ignoreCase = true)
                                                                }
                                                                matchingCartIds.forEach { id ->
                                                                    viewModel.removeFromCustomerCart(id)
                                                                }
                                                            },
                                                            modifier = Modifier.size(24.dp)
                                                        ) {
                                                            Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = DarkAccentRed, modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))
                                            Divider(color = Color.White.copy(alpha = 0.05f))
                                            Spacer(modifier = Modifier.height(8.dp))

                                            // Subtotal for this pharmacy
                                            val groupTotal = items.sumOf { (med, qty) -> med.calculatePriceForQuantity(qty) }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = if (isBengali) "ফার্মেসির মোট বিল:" else "Pharmacy Subtotal:",
                                                    color = DarkTextPrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                Text(
                                                    text = "৳${formatBengaliNumber(String.format("%.1f", groupTotal))}",
                                                    color = DarkAccentGold,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(12.dp))

                                            // Pharmacy Map Toggle & Online Order Button Row
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // Map Toggle
                                                OutlinedButton(
                                                    onClick = {
                                                        expandedPharmacyMapName = if (isMapOpen) null else pharmacy.name
                                                    },
                                                    border = BorderStroke(1.dp, InfoColor.copy(alpha = 0.5f)),
                                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = InfoColor),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.weight(1f).height(32.dp),
                                                    contentPadding = PaddingValues(0.dp)
                                                ) {
                                                    Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = if (isMapOpen) {
                                                            if (isBengali) "ম্যাপ লুকান" else "Hide Map"
                                                        } else {
                                                            if (isBengali) "লোকেশন ম্যাপ" else "View Location"
                                                        },
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }

                                                // Online Order Placer
                                                Button(
                                                    onClick = {
                                                        viewModel.placeGroupedOrder(items, if (isBengali) pharmacy.name else pharmacy.nameEn)
                                                        android.widget.Toast.makeText(
                                                            context,
                                                            if (isBengali) "অনলাইন অর্ডারটি সফলভাবে সম্পন্ন হয়েছে!" else "Online order placed successfully!",
                                                            android.widget.Toast.LENGTH_LONG
                                                        ).show()
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = SuccessColor, contentColor = Color.White),
                                                    shape = RoundedCornerShape(8.dp),
                                                    modifier = Modifier.weight(1.2f).height(32.dp),
                                                    contentPadding = PaddingValues(0.dp)
                                                ) {
                                                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = if (isBengali) "অনলাইন অর্ডার করুন" else "Order Online",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }

                                            // Map section
                                            if (isMapOpen) {
                                                Spacer(modifier = Modifier.height(12.dp))
                                                if (currentLat != null && currentLon != null) {
                                                    Card(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(130.dp),
                                                        colors = CardDefaults.cardColors(containerColor = DarkBg),
                                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                                            val w = size.width
                                                            val h = size.height

                                                            // Draw simulated map streets grid
                                                            val colGrid = Color(0xFF303030)
                                                            for (x in 0..w.toInt() step 30) {
                                                                drawLine(colGrid, start = androidx.compose.ui.geometry.Offset(x.toFloat(), 0f), end = androidx.compose.ui.geometry.Offset(x.toFloat(), h))
                                                            }
                                                            for (y in 0..h.toInt() step 30) {
                                                                drawLine(colGrid, start = androidx.compose.ui.geometry.Offset(0f, y.toFloat()), end = androidx.compose.ui.geometry.Offset(w, y.toFloat()))
                                                            }

                                                            // Draw major roads
                                                            drawLine(Color(0xFF4E3629), start = androidx.compose.ui.geometry.Offset(0f, h/2), end = androidx.compose.ui.geometry.Offset(w, h/2), strokeWidth = 6f)
                                                            drawLine(Color(0xFF4E3629), start = androidx.compose.ui.geometry.Offset(w/2, 0f), end = androidx.compose.ui.geometry.Offset(w/2, h), strokeWidth = 6f)

                                                            // User Marker (Blue)
                                                            val centerOffset = androidx.compose.ui.geometry.Offset(w/2, h/2)
                                                            drawCircle(InfoColor.copy(alpha = 0.2f), radius = 20f, center = centerOffset)
                                                            drawCircle(InfoColor, radius = 4f, center = centerOffset)

                                                            // Pharmacy Marker (Green)
                                                            val latDiff = pharmacy.lat - currentLat
                                                            val lngDiff = pharmacy.lLng - currentLon
                                                            val pX = (w / 2) + (lngDiff * 4000).toFloat().coerceIn(-w/2 + 20f, w/2 - 20f)
                                                            val pY = (h / 2) - (latDiff * 4000).toFloat().coerceIn(-h/2 + 20f, h/2 - 20f)
                                                            val pOffset = androidx.compose.ui.geometry.Offset(pX, pY)

                                                            drawLine(InfoColor, start = centerOffset, end = pOffset, strokeWidth = 2f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5f, 5f), 0f))
                                                            drawCircle(SuccessColor.copy(alpha = 0.3f), radius = 12f, center = pOffset)
                                                            drawCircle(SuccessColor, radius = 5f, center = pOffset)
                                                        }
                                                    }
                                                } else {
                                                    Card(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        colors = CardDefaults.cardColors(containerColor = DarkBg),
                                                        border = BorderStroke(1.dp, DarkAccentGold.copy(alpha = 0.3f)),
                                                        shape = RoundedCornerShape(8.dp)
                                                    ) {
                                                        Column(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(14.dp),
                                                            horizontalAlignment = Alignment.CenterHorizontally
                                                        ) {
                                                            Icon(Icons.Filled.LocationOff, null, tint = DarkAccentGold, modifier = Modifier.size(24.dp))
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Text(
                                                                text = if (isBengali) "একাউন্টে কোনো লোকেশন সেট নেই" else "Location not set in Account",
                                                                color = DarkTextPrimary,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 12.sp
                                                            )
                                                            Text(
                                                                text = if (isBengali) "ম্যাপে দূরত্ব ও সঠিক রুট দেখতে সেটিংসে গিয়ে নিজের লোকেশন সেট করুন।" else "Set your location in Settings to view route and distance on map.",
                                                                color = DarkTextSecondary,
                                                                fontSize = 10.5.sp,
                                                                textAlign = TextAlign.Center
                                                            )
                                                            Spacer(modifier = Modifier.height(8.dp))
                                                            Button(
                                                                onClick = { showLocationSettingsDialog = true },
                                                                colors = ButtonDefaults.buttonColors(containerColor = DarkAccentGold, contentColor = Color.Black),
                                                                shape = RoundedCornerShape(8.dp),
                                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                                                modifier = Modifier.height(34.dp)
                                                            ) {
                                                                Text(if (isBengali) "📍 লোকেশন সেট করুন" else "📍 Set Location", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
            } else if (activeTab == 2) {
                // PANEL 2: COMPLETED ORDER HISTORIES
                val userOrders = customerOrders.filter { it.customerUsername == currentUser.username }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = if (isBengali) "আমার অতীত ও বর্তমান অর্ডারসমূহ:" else "History of Placed Orders:",
                        color = DarkTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (userOrders.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isBengali) "আপনি এখনও কোনো ওষুধের অর্ডার করেননি!" else "No placed orders found! Search medicines and tap order.",
                                        color = DarkTextSecondary,
                                        fontSize = 13.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            items(userOrders.size) { index ->
                                val ord = userOrders[index]

                                val statusColor = when (ord.status) {
                                    "Confirmed" -> SuccessColor
                                    "Cancelled" -> DarkAccentRed
                                    else -> WarningColor // Pending
                                }

                                val displayedStatusStr = when (ord.status) {
                                    "Confirmed" -> if (isBengali) "নিশ্চিত" else "Confirmed"
                                    "Cancelled" -> if (isBengali) "বাতিল" else "Cancelled"
                                    else -> if (isBengali) "অপেক্ষমাণ" else "Pending"
                                }

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Column {
                                                Text(
                                                    text = ord.medicineName,
                                                    color = DarkTextPrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 15.sp
                                                )
                                                Text(
                                                    text = "${if (isBengali) "ফার্মেসি: " else "Pharmacy: "}${ord.pharmacyName}",
                                                    color = InfoColor,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "${if (isBengali) "তারিখ: " else "Date: "}${ord.dateString}",
                                                    color = DarkTextSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .background(statusColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = displayedStatusStr,
                                                    color = statusColor,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))
                                        Divider(color = Color.White.copy(alpha = 0.05f))
                                        Spacer(modifier = Modifier.height(8.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${if (isBengali) "পরিমাণ: " else "Qty: "}${formatBengaliNumber(ord.quantity)} পিস",
                                                color = DarkTextSecondary,
                                                fontSize = 12.sp
                                            )
                                            
                                            Text(
                                                text = "৳${formatBengaliNumber(String.format("%.1f", ord.totalPrice))}",
                                                color = DarkAccentGold,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }

                                        // Cancel order if Pending
                                        if (ord.status == "Pending") {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            OutlinedButton(
                                                onClick = {
                                                    viewModel.deleteCustomerOrder(ord)
                                                    android.widget.Toast.makeText(context, if (isBengali) "অর্ডারটি সফলভাবে বাতিল করা হয়েছে!" else "Order has been cancelled!", android.widget.Toast.LENGTH_SHORT).show()
                                                },
                                                border = BorderStroke(1.dp, DarkAccentRed.copy(alpha = 0.3f)),
                                                colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkAccentRed),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth().height(32.dp),
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text(
                                                    text = if (isBengali) "অর্ডার বাতিল করুন" else "Cancel Order Request",
                                                    fontSize = 11.sp,
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
            } else if (activeTab == 3) {
                NotificationsScreen(viewModel = viewModel, isBengali = isBengali)
            } else {
                AccountScreenForCustomer(viewModel = viewModel, isBengali = isBengali)
            }
        }
    }

    // CONFIRM MEDICINE ORDERING DIALOG DIALOGUE
    if (showOrderDialog && chosenPharmacyForOrder != null && selectedLocatorMedicine != null) {
        val med = selectedLocatorMedicine!!
        val pharm = chosenPharmacyForOrder!!
        val unitPrice = med.salePriceUnit
        val calcTotalPrice = unitPrice * orderQuantity

        Dialog(onDismissRequest = { showOrderDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = if (isBengali) "📦 ওষুধ অর্ডারের বিবরণ" else "📦 Confirm Medicine Order",
                        color = DarkTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "${if (isBengali) "ওষুধের নাম: " else "Medicine: "}${med.name}",
                        color = DarkTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${if (isBengali) "ফার্মেসি: " else "Pharmacy: "}${if (isBengali) pharm.name else pharm.nameEn}",
                        color = InfoColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${if (isBengali) "ইউনিট মূল্য: " else "Unit Price: "}৳${formatBengaliNumber(String.format("%.1f", unitPrice))}",
                        color = DarkTextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Divider(color = Color.White.copy(alpha = 0.05f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // QUANTITY SELECTION ACTION ROW
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isBengali) "অর্ডারের পরিমাণ:" else "Order Quantity:",
                            color = DarkTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { if (orderQuantity > 1) orderQuantity-- },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                modifier = Modifier.size(32.dp),
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text("-", color = DarkTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }

                            Text(
                                text = formatBengaliNumber(orderQuantity),
                                modifier = Modifier.padding(horizontal = 14.dp),
                                color = DarkTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )

                            Button(
                                onClick = { if (orderQuantity < 100) orderQuantity++ },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                modifier = Modifier.size(32.dp),
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text("+", color = DarkTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isBengali) "সর্বমোট মূল্য:" else "Total Price:",
                            color = DarkTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "৳${formatBengaliNumber(String.format("%.1f", calcTotalPrice))}",
                            color = DarkAccentGold,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = { showOrderDialog = false },
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkTextSecondary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(if (isBengali) "বাতিল" else "Cancel", fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Button(
                            onClick = {
                                viewModel.placeOrder(
                                    medicine = med,
                                    qty = orderQuantity,
                                    pharmacyName = if (isBengali) pharm.name else pharm.nameEn,
                                    totalPrice = calcTotalPrice
                                )
                                showOrderDialog = false
                                android.widget.Toast.makeText(
                                    context,
                                    if (isBengali) "অর্ডার সফল হয়েছে! আপনার হিস্ট্রিতে দেখতে পাবেন।" else "Order placed successfully! Verified in history.",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessColor, contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(if (isBengali) "অর্ডার নিশ্চিত করুন" else "Place Order", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // ADD TO CART QUANTITY SELECTION DIALOG
    if (showAddToCartDialog && medicineToAddToCart != null) {
        val med = medicineToAddToCart!!
        val unitPrice = med.salePriceUnit

        var boxesSelected by remember(med.id) { mutableStateOf(0) }
        var stripsSelected by remember(med.id) { mutableStateOf(0) }
        var piecesSelected by remember(med.id) { mutableStateOf(1) } // Default to 1 piece

        val totalQtySelected = if (med.type == "tablet") {
            val pcsPerBox = med.stripsPerBox * med.pcsPerStrip
            (boxesSelected * pcsPerBox) + (stripsSelected * med.pcsPerStrip) + piecesSelected
        } else {
            addToCartQty
        }

        val calcTotalPrice = if (med.type == "tablet") {
            (boxesSelected * med.salePriceBox) + (stripsSelected * med.salePriceStrip) + (piecesSelected * med.salePricePiece)
        } else {
            unitPrice * addToCartQty
        }

        Dialog(onDismissRequest = { showAddToCartDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = if (isBengali) "🛒 কার্টে ওষুধ যুক্ত করুন" else "🛒 Add Medicine to Cart",
                        color = DarkTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "${if (isBengali) "ওষুধের নাম: " else "Medicine: "}${med.name}",
                        color = DarkTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    val sourcePharmacy = getPharmacyForMedicine(med)
                    Text(
                        text = "${if (isBengali) "উৎস ফার্মেসি: " else "Sourced Pharmacy: "}${if (isBengali) sourcePharmacy.name else sourcePharmacy.nameEn}",
                        color = InfoColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    
                    val priceLabel = if (med.type == "tablet") {
                        if (isBengali) {
                            "বক্স: ৳${formatBengaliNumber(String.format("%.1f", med.salePriceBox))} | পাতা: ৳${formatBengaliNumber(String.format("%.1f", med.salePriceStrip))} | পিস: ৳${formatBengaliNumber(String.format("%.1f", med.salePricePiece))}"
                        } else {
                            "Box: ৳${String.format("%.1f", med.salePriceBox)} | Strip: ৳${String.format("%.1f", med.salePriceStrip)} | Pc: ৳${String.format("%.1f", med.salePricePiece)}"
                        }
                    } else {
                        "${if (isBengali) "ইউনিট মূল্য: " else "Unit Price: "}৳${formatBengaliNumber(String.format("%.1f", unitPrice))}"
                    }
                    Text(
                        text = priceLabel,
                        color = DarkTextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Divider(color = Color.White.copy(alpha = 0.05f))
                    Spacer(modifier = Modifier.height(14.dp))

                    if (med.type == "tablet") {
                        // Display packing info
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkBg),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = if (isBengali) {
                                        "📦 প্যাকিং তথ্য: ১ বক্স = ${formatBengaliNumber(med.stripsPerBox)} পাতা | ১ পাতা = ${formatBengaliNumber(med.pcsPerStrip)} পিস"
                                    } else {
                                        "📦 Packing: 1 Box = ${med.stripsPerBox} Strips | 1 Strip = ${med.pcsPerStrip} Pcs"
                                    },
                                    color = InfoColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Selected Items Breakdown Summary
                        Text(
                            text = if (isBengali) "কার্ট সংযোজন পরিমাণ:" else "Adding Quantity Breakdown:",
                            color = DarkTextSecondary,
                            fontSize = 11.sp
                        )
                        Text(
                            text = med.formatQuantityString(totalQtySelected, isBengali),
                            color = DarkAccentGold,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // 1. Box Selector
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (isBengali) "বক্স (Box)" else "Box",
                                    color = DarkTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isBengali) "৳${formatBengaliNumber(String.format("%.1f", med.salePriceBox))} / বক্স" else "৳${String.format("%.1f", med.salePriceBox)} / Box",
                                    color = DarkTextSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { if (boxesSelected > 0) boxesSelected-- },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                    modifier = Modifier.size(28.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("-", color = DarkTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }

                                Text(
                                    text = formatBengaliNumber(boxesSelected),
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    color = DarkTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )

                                Button(
                                    onClick = { if (boxesSelected < 50) boxesSelected++ },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                    modifier = Modifier.size(28.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("+", color = DarkTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }

                        // 2. Strip Selector
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (isBengali) "পাতা (Strip)" else "Strip",
                                    color = DarkTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isBengali) "৳${formatBengaliNumber(String.format("%.1f", med.salePriceStrip))} / পাতা" else "৳${String.format("%.1f", med.salePriceStrip)} / Strip",
                                    color = DarkTextSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { if (stripsSelected > 0) stripsSelected-- },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                    modifier = Modifier.size(28.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("-", color = DarkTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }

                                Text(
                                    text = formatBengaliNumber(stripsSelected),
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    color = DarkTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )

                                Button(
                                    onClick = { if (stripsSelected < 100) stripsSelected++ },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                    modifier = Modifier.size(28.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("+", color = DarkTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }

                        // 3. Piece Selector
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (isBengali) "পিস (Piece)" else "Piece",
                                    color = DarkTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (isBengali) "৳${formatBengaliNumber(String.format("%.1f", med.salePricePiece))} / পিস" else "৳${String.format("%.1f", med.salePricePiece)} / Pc",
                                    color = DarkTextSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { if (piecesSelected > 0) piecesSelected-- },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                    modifier = Modifier.size(28.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("-", color = DarkTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }

                                Text(
                                    text = formatBengaliNumber(piecesSelected),
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    color = DarkTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )

                                Button(
                                    onClick = { if (piecesSelected < 500) piecesSelected++ },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                    modifier = Modifier.size(28.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("+", color = DarkTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                            }
                        }
                    } else {
                        // Standard Single Quantity Selection for other medicines (Syrup, Ointment, Injection, etc.)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isBengali) "পরিমাণ নির্ধারণ:" else "Select Quantity:",
                                color = DarkTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { if (addToCartQty > 1) addToCartQty-- },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                    modifier = Modifier.size(32.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("-", color = DarkTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }

                                Text(
                                    text = formatBengaliNumber(addToCartQty),
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    color = DarkTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )

                                Button(
                                    onClick = { if (addToCartQty < 100) addToCartQty++ },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f)),
                                    modifier = Modifier.size(32.dp),
                                    contentPadding = PaddingValues(0.dp),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("+", color = DarkTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isBengali) "মোট মূল্য:" else "Subtotal Price:",
                            color = DarkTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "৳${formatBengaliNumber(String.format("%.1f", calcTotalPrice))}",
                            color = DarkAccentGold,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = { showAddToCartDialog = false },
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkTextSecondary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(if (isBengali) "বাতিল" else "Cancel", fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Button(
                            onClick = {
                                if (totalQtySelected <= 0) {
                                    android.widget.Toast.makeText(
                                        context,
                                        if (isBengali) "অনুগ্রহ করে অন্তত ১ পিস ওষুধ নির্বাচন করুন।" else "Please select at least 1 piece of medicine.",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    viewModel.addToCustomerCart(med.id, totalQtySelected)
                                    showAddToCartDialog = false
                                    android.widget.Toast.makeText(
                                        context,
                                        if (isBengali) "${med.name} কার্টে যোগ করা হয়েছে!" else "${med.name} added to cart!",
                                        android.widget.Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessColor, contentColor = Color.White),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text(if (isBengali) "কার্টে যোগ করুন" else "Add to Cart", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showEditProfileDialog) {
        CustomerEditProfileDialog(
            viewModel = viewModel,
            isBengali = isBengali,
            onDismiss = { showEditProfileDialog = false }
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

data class SimulatedPharmacy(
    val name: String,
    val nameEn: String,
    val lat: Double,
    val lLng: Double,
    val stockAvailable: Boolean,
    val baseDistanceKm: Double,
    val description: String,
    val descriptionEn: String,
    val ownerUsername: String = ""
)

data class CustomerGroupedMedicine(
    val groupKey: String,
    val name: String,
    val dosageStrength: String,
    val genericName: String,
    val type: String,
    val salePriceBox: Double,
    val salePriceStrip: Double,
    val salePricePiece: Double,
    val salePriceUnit: Double,
    val pharmacyOfferings: List<Medicine>
)

