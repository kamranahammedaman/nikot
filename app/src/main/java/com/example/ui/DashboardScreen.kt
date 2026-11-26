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
fun DashboardScreen(viewModel: PharmacyViewModel, isBengali: Boolean) {
    val medicines by viewModel.allMedicines.collectAsState()
    val todayBillList by viewModel.todayBills.collectAsState()
    val owner by viewModel.ownerName.collectAsState()
    val pendingStockIns by viewModel.allPendingStockIns.collectAsState()
    val pendingMedicines by viewModel.allPendingMedicines.collectAsState()
    val customerOrders by viewModel.allCustomerOrders.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val expiryAlertMonths by viewModel.expiryAlertMonths.collectAsState()
    val lowStockAlertFactor by viewModel.lowStockAlertFactor.collectAsState()

    val notifications by viewModel.notifications.collectAsState()
    val unreadNotifCount = notifications.count { !it.isRead }

    var activeDetailPane by remember { mutableStateOf<Int?>(null) } // 1: sales detail, 2: bills list, 3: low stock list, 4: expiry list
    var showRefundWorkflowDialog by remember { mutableStateOf(false) }
    var showNotificationsDialog by remember { mutableStateOf(false) }

    val editedStockIns = remember(pendingStockIns) {
        androidx.compose.runtime.mutableStateMapOf<Long, PendingStockIn>().apply {
            pendingStockIns.forEach { put(it.id, it) }
        }
    }

    var showAdminDisconnectWarning by remember { mutableStateOf(false) }
    var staffToDisconnect by remember { mutableStateOf<PharmacyViewModel.UserProfile?>(null) }



    val nonReturnedBills = todayBillList.filter { !it.isReturned }
    val returnedBills = todayBillList.filter { it.isReturned }

    val originalSalesTotal = todayBillList.sumOf { it.totalAmount }
    val returnedSalesTotal = todayBillList.sumOf { it.returnedAmount }
    val todaySalesTotal = originalSalesTotal - returnedSalesTotal

    val salesFormulaStr = if (isBengali) {
        "৳${formatBengaliNumber(String.format("%.0f", originalSalesTotal))} − ৳${formatBengaliNumber(String.format("%.0f", returnedSalesTotal))} = ৳${formatBengaliNumber(String.format("%.0f", todaySalesTotal))}"
    } else {
        "৳${String.format("%.0f", originalSalesTotal)} − ৳${String.format("%.0f", returnedSalesTotal)} = ৳${String.format("%.0f", todaySalesTotal)}"
    }

    val todayBillCount = nonReturnedBills.size

    val lowStockMedicines = medicines.filter { m ->
        val baseLimit = if (m.lowStockThreshold > 0) m.lowStockThreshold else {
            when (m.type) {
                "tablet" -> 50
                "syrup" -> 5
                "ointment" -> 5
                "injection" -> 5
                else -> 5
            }
        }
        val limit = baseLimit * lowStockAlertFactor
        when (m.type) {
            "tablet" -> m.totalPiecesInStock < limit
            "syrup" -> m.bottlesInStock < limit
            "ointment" -> m.tubesInStock < limit
            "injection" -> m.vialsInStock < limit
            else -> m.otherQuantity < limit.toDouble()
        }
    }

    val systemCalendar = Calendar.getInstance()
    val currentMillis = systemCalendar.timeInMillis
    systemCalendar.add(Calendar.MONTH, expiryAlertMonths) // Dynamic month alert period
    val warnMillis = systemCalendar.timeInMillis

    val expiringMedicines = medicines.filter { m ->
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val expTime = sdf.parse(m.expiryDate)?.time ?: Long.MAX_VALUE
            expTime <= warnMillis
        } catch (_: Exception) {
            false
        }
    }

    val todayStr = if (isBengali) {
        formatBengaliDate(System.currentTimeMillis())
    } else {
        SimpleDateFormat("d MMM yyyy", Locale.US).format(Date())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Dashboard Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoLogoHeader()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Owner details
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${if (isBengali) "মালিক: " else "Owner: "}$owner",
                        color = DarkTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(todayStr, color = DarkTextSecondary, fontSize = 11.sp)
                }

                // Notifications button
                Box {
                    IconButton(
                        onClick = { showNotificationsDialog = true },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            tint = DarkAccentGold,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    if (unreadNotifCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-4).dp)
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
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4 Grid cards
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f)) {
                    DashboardCard(
                        title = if (isBengali) "আজকের মোট বিক্রয়" else "Today's Total Sales",
                        value = "৳${if (isBengali) formatBengaliNumber(String.format("%.2f", todaySalesTotal)) else String.format("%.2f", todaySalesTotal)}",
                        icon = Icons.Filled.ArrowDropUp,
                        highlight = DarkAccentGold,
                        subValue = salesFormulaStr,
                        onClick = { activeDetailPane = 1 }
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    DashboardCard(
                        title = if (isBengali) "আজকের মোট বিল সংখ্যা" else "Today's Total Bills",
                        value = if (isBengali) "${formatBengaliNumber(todayBillCount)} টি" + (if (returnedBills.isNotEmpty()) " (${formatBengaliNumber(returnedBills.size)} ফেরত)" else "") else "$todayBillCount Bills" + (if (returnedBills.isNotEmpty()) " (${returnedBills.size} ret)" else ""),
                        icon = Icons.Filled.ListAlt,
                        highlight = Color.Cyan,
                        actionText = if (isBengali) "ফেরত" else "Return",
                        onActionClick = { showRefundWorkflowDialog = true },
                        onClick = { activeDetailPane = 2 }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f)) {
                    Column {
                        DashboardCard(
                            title = if (isBengali) "কম স্টক সতর্কতা" else "Low Stock Alerts",
                            value = if (isBengali) "${formatBengaliNumber(lowStockMedicines.size)} টি" else "${lowStockMedicines.size} Alerts",
                            icon = Icons.Filled.Warning,
                            highlight = DarkAccentRed,
                            onClick = { activeDetailPane = 3 }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurface, RoundedCornerShape(6.dp))
                                .border(1.dp, SleekBorderColor, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { if (lowStockAlertFactor > 1) viewModel.setLowStockAlertFactor(lowStockAlertFactor - 1) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                            Text(
                                text = if (isBengali) "${formatBengaliNumber(lowStockAlertFactor)} গুণ সতর্ক" else "${lowStockAlertFactor}x Limit Alert",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { if (lowStockAlertFactor < 10) viewModel.setLowStockAlertFactor(lowStockAlertFactor + 1) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    Column {
                        DashboardCard(
                            title = if (isBengali) "মেয়াদ শেষ সতর্কতা" else "Expiry Alerts",
                            value = if (isBengali) "${formatBengaliNumber(expiringMedicines.size)} টি" else "${expiringMedicines.size} Alerts",
                            icon = Icons.Filled.Warning,
                            highlight = Color.Magenta,
                            onClick = { activeDetailPane = 4 }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(DarkSurface, RoundedCornerShape(6.dp))
                                .border(1.dp, SleekBorderColor, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { if (expiryAlertMonths > 1) viewModel.setExpiryAlertMonths(expiryAlertMonths - 1) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Decrease", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                            Text(
                                text = if (isBengali) "${formatBengaliNumber(expiryAlertMonths)} মাস সতর্ক" else "$expiryAlertMonths Mon Alert",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { if (expiryAlertMonths < 36) viewModel.setExpiryAlertMonths(expiryAlertMonths + 1) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Increase", tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }

        // --- MULTI-ROLE PENDING STOCK APPROVALS PANEL ---
        if (viewModel.isMerchant(currentUser) && pendingStockIns.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, WarningColor.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.QueryBuilder,
                            contentDescription = "Pending Approval",
                            tint = WarningColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isBengali) "অনুমোদন অপেক্ষমাণ স্টক (${formatBengaliNumber(pendingStockIns.size)})" else "Pending Stock-In Requests (${pendingStockIns.size})",
                            color = DarkTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    pendingStockIns.forEach { pending ->
                        val current = editedStockIns[pending.id] ?: pending
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkBg),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                // Header
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = pending.medicineName,
                                            color = DarkTextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "${if (isBengali) "অনুরোধকারী: " else "By Staff: "}${pending.staffUsername}",
                                            color = DarkTextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    
                                    // Reset button if edited
                                    if (current != pending) {
                                        TextButton(
                                            onClick = { editedStockIns[pending.id] = pending },
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier.height(24.dp)
                                        ) {
                                            Text(if (isBengali) "রিসেট" else "Reset", color = DarkAccentGold, fontSize = 11.sp)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Editable fields row/grid
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    when (pending.medicineType) {
                                        "tablet" -> {
                                            CompactIntegerField(
                                                label = if (isBengali) "বক্স" else "Box",
                                                value = current.addedBoxes,
                                                onValueChange = { val newVal = current.copy(addedBoxes = it); editedStockIns[pending.id] = newVal },
                                                modifier = Modifier.weight(1f)
                                            )
                                            CompactIntegerField(
                                                label = if (isBengali) "পাতা" else "Strip",
                                                value = current.addedStrips,
                                                onValueChange = { val newVal = current.copy(addedStrips = it); editedStockIns[pending.id] = newVal },
                                                modifier = Modifier.weight(1f)
                                            )
                                            CompactIntegerField(
                                                label = if (isBengali) "পিস" else "Pcs",
                                                value = current.addedPcs,
                                                onValueChange = { val newVal = current.copy(addedPcs = it); editedStockIns[pending.id] = newVal },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        "syrup" -> {
                                            CompactIntegerField(
                                                label = if (isBengali) "বোতল" else "Bottle",
                                                value = current.addedBottles,
                                                onValueChange = { val newVal = current.copy(addedBottles = it); editedStockIns[pending.id] = newVal },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        "ointment" -> {
                                            CompactIntegerField(
                                                label = if (isBengali) "টিউব" else "Tube",
                                                value = current.addedTubes,
                                                onValueChange = { val newVal = current.copy(addedTubes = it); editedStockIns[pending.id] = newVal },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        "injection" -> {
                                            CompactIntegerField(
                                                label = if (isBengali) "ভায়াল" else "Vial",
                                                value = current.addedVials,
                                                onValueChange = { val newVal = current.copy(addedVials = it); editedStockIns[pending.id] = newVal },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                        else -> {
                                            CompactDoubleField(
                                                label = if (isBengali) "পিস/পরিমাণ" else "Qty",
                                                value = current.addedOtherQty,
                                                onValueChange = { val newVal = current.copy(addedOtherQty = it ?: 0.0); editedStockIns[pending.id] = newVal },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Prices row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CompactDoubleField(
                                        label = if (isBengali) "ক্রয় মূল্য" else "Buy Price",
                                        value = current.purchasePrice,
                                        onValueChange = { val newVal = current.copy(purchasePrice = it); editedStockIns[pending.id] = newVal },
                                        modifier = Modifier.weight(1f)
                                    )
                                    CompactDoubleField(
                                        label = if (isBengali) "বিক্রয় মূল্য" else "Sell Price",
                                        value = current.salePrice,
                                        onValueChange = { val newVal = current.copy(salePrice = it); editedStockIns[pending.id] = newVal },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Individual Actions Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.rejectPendingStockIn(pending) },
                                        border = BorderStroke(1.dp, DarkAccentRed.copy(alpha = 0.5f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkAccentRed),
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(if (isBengali) "বাতিল" else "Reject", fontSize = 11.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { viewModel.approvePendingStockIn(current) },
                                        colors = ButtonDefaults.buttonColors(containerColor = SuccessColor, contentColor = Color.White),
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(if (isBengali) "অনুমোদন" else "Approve", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                pendingStockIns.forEach { p ->
                                    viewModel.rejectPendingStockIn(p)
                                }
                            },
                            border = BorderStroke(1.dp, DarkAccentRed),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkAccentRed),
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (isBengali) "সব বাতিল করুন" else "Reject All", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                        
                        Button(
                            onClick = {
                                pendingStockIns.forEach { p ->
                                    val edited = editedStockIns[p.id] ?: p
                                    viewModel.approvePendingStockIn(edited)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessColor, contentColor = Color.White),
                            modifier = Modifier.weight(1f).height(40.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (isBengali) "সব একসাথে অনুমোদন" else "Approve All", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        } else if (currentUser.role == "staff" && pendingStockIns.isNotEmpty()) {
            val staffPending = pendingStockIns.filter { it.staffUsername == currentUser.username }
            if (staffPending.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, DarkTextSecondary.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.QueryBuilder,
                                contentDescription = "My Pending Requests",
                                tint = DarkAccentGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBengali) "আমার পেন্ডিং স্টক অনুরোধ (${formatBengaliNumber(staffPending.size)})" else "My Pending Stock-In Requests (${staffPending.size})",
                                color = DarkTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        staffPending.forEach { pending ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkBg)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = pending.medicineName,
                                            color = DarkTextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
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
                                            text = "${if (isBengali) "পরিমাণ: " else "Qty: "}$qtyStr",
                                            color = DarkAccentGold,
                                            fontSize = 11.sp
                                        )
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .background(WarningColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (isBengali) "অপেক্ষমাণ" else "Pending",
                                            color = WarningColor,
                                            fontSize = 10.sp,
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

        // --- MULTI-ROLE PENDING MEDICINE APPROVALS PANEL ---
        if (viewModel.isMerchant(currentUser) && pendingMedicines.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = BorderStroke(1.dp, InfoColor.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.QueryBuilder,
                            contentDescription = "Pending Medicine Approval",
                            tint = InfoColor,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isBengali) "অনুমোদন অপেক্ষমাণ ওষুধ (${formatBengaliNumber(pendingMedicines.size)})" else "Pending Medicine Requests (${pendingMedicines.size})",
                            color = DarkTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    
                    pendingMedicines.forEach { pending ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkBg),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(0.6f)) {
                                        Text(
                                            text = pending.name + (if (pending.dosageStrength.isNotEmpty()) " ${pending.dosageStrength}" else ""),
                                            color = DarkTextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "${pending.genericName} (${pending.type})",
                                            color = DarkTextSecondary,
                                            fontSize = 11.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val qtyStr = when (pending.type) {
                                            "tablet" -> {
                                                val parts = mutableListOf<String>()
                                                if (pending.boxesInStock > 0) parts.add(if (isBengali) "${pending.boxesInStock} বক্স" else "${pending.boxesInStock} Box")
                                                if (pending.stripsInStock > 0) parts.add(if (isBengali) "${pending.stripsInStock} পাতা" else "${pending.stripsInStock} Strip")
                                                if (pending.pcsInStock > 0) parts.add(if (isBengali) "${pending.pcsInStock} পিস" else "${pending.pcsInStock} Pcs")
                                                parts.joinToString(" ")
                                            }
                                            "syrup" -> if (isBengali) "${pending.bottlesInStock} বোতল" else "${pending.bottlesInStock} Bottle"
                                            "ointment" -> if (isBengali) "${pending.tubesInStock} টিউব" else "${pending.tubesInStock} Tube"
                                            "injection" -> if (isBengali) "${pending.vialsInStock} ভায়াল" else "${pending.vialsInStock} Vial"
                                            else -> if (isBengali) "${pending.otherQuantity} পিস" else "${pending.otherQuantity} Pcs"
                                        }
                                        Text(
                                            text = "${if (isBengali) "অনুরোধকৃত স্টক: " else "Requested Stock: "}$qtyStr",
                                            color = DarkAccentGold,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${if (isBengali) "অনুরোধকারী: " else "By Staff: "}${pending.staffUsername}",
                                            color = DarkTextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    
                                    Column(
                                        modifier = Modifier.weight(0.4f),
                                        horizontalAlignment = Alignment.End
                                    ) {
                                        val buyPrice = if (pending.type == "tablet") pending.purchasePriceBox else pending.purchasePriceUnit
                                        val sellPrice = if (pending.type == "tablet") pending.salePriceBox else pending.salePriceUnit
                                        if (buyPrice > 0) {
                                            Text(
                                                text = "${if (isBengali) "ক্রয়: ৳" else "Buy: ৳"}${formatBengaliNumber(String.format("%.1f", buyPrice))}",
                                                color = DarkTextSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                        if (sellPrice > 0) {
                                            Text(
                                                text = "${if (isBengali) "বিক্রয়: ৳" else "Sell: ৳"}${formatBengaliNumber(String.format("%.1f", sellPrice))}",
                                                color = DarkTextSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    if (pending.targetMedicineId == null) SuccessColor.copy(alpha = 0.15f) else InfoColor.copy(alpha = 0.15f),
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = if (pending.targetMedicineId == null) {
                                                    if (isBengali) "নতুন ওষুধ" else "New Entry"
                                                } else {
                                                    if (isBengali) "স্টক আপডেট" else "Stock Update"
                                                },
                                                color = if (pending.targetMedicineId == null) SuccessColor else InfoColor,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.rejectPendingMedicine(pending) },
                                        border = BorderStroke(1.dp, DarkAccentRed.copy(alpha = 0.5f)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkAccentRed),
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(if (isBengali) "বাতিল" else "Reject", fontSize = 11.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { viewModel.approvePendingMedicine(pending) },
                                        colors = ButtonDefaults.buttonColors(containerColor = SuccessColor, contentColor = Color.White),
                                        modifier = Modifier.height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(if (isBengali) "অনুমোদন" else "Approve", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (currentUser.role == "staff" && pendingMedicines.isNotEmpty()) {
            val staffPendingMeds = pendingMedicines.filter { it.staffUsername == currentUser.username }
            if (staffPendingMeds.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, DarkTextSecondary.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.QueryBuilder,
                                contentDescription = "My Pending New Medicines",
                                tint = DarkAccentGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBengali) "ওষুধের পেন্ডিং অনুরোধ (${formatBengaliNumber(staffPendingMeds.size)})" else "My Pending Medicine Requests (${staffPendingMeds.size})",
                                color = DarkTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        staffPendingMeds.forEach { pending ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkBg)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = pending.name + (if (pending.dosageStrength.isNotEmpty()) " ${pending.dosageStrength}" else ""),
                                            color = DarkTextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        val qtyStr = when (pending.type) {
                                            "tablet" -> {
                                                val parts = mutableListOf<String>()
                                                if (pending.boxesInStock > 0) parts.add(if (isBengali) "${pending.boxesInStock} বক্স" else "${pending.boxesInStock} Box")
                                                if (pending.stripsInStock > 0) parts.add(if (isBengali) "${pending.stripsInStock} পাতা" else "${pending.stripsInStock} Strip")
                                                if (pending.pcsInStock > 0) parts.add(if (isBengali) "${pending.pcsInStock} পিস" else "${pending.pcsInStock} Pcs")
                                                parts.joinToString(" ")
                                            }
                                            "syrup" -> if (isBengali) "${pending.bottlesInStock} বোতল" else "${pending.bottlesInStock} Bottle"
                                            "ointment" -> if (isBengali) "${pending.tubesInStock} টিউব" else "${pending.tubesInStock} Tube"
                                            "injection" -> if (isBengali) "${pending.vialsInStock} ভায়াল" else "${pending.vialsInStock} Vial"
                                            else -> if (isBengali) "${pending.otherQuantity} পিস" else "${pending.otherQuantity} Pcs"
                                        }
                                        Text(
                                            text = "${if (isBengali) "परिমাণ: " else "Qty: "}$qtyStr",
                                            color = DarkAccentGold,
                                            fontSize = 11.sp
                                        )
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .background(WarningColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (isBengali) "অপেক্ষমাণ" else "Pending",
                                            color = WarningColor,
                                            fontSize = 10.sp,
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

        // --- MERCHANT PORTAL PENDING PERMISSION REQUESTS ---
        if (viewModel.isMerchant(currentUser)) {
            val permissionRequests by viewModel.permissionRequests.collectAsState()
            val pendingPermissions = permissionRequests.filter { it.adminUsername == currentUser.username && it.status == "Pending" }
            if (pendingPermissions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, DarkAccentGold.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VpnKey,
                                contentDescription = "Security Requests",
                                tint = DarkAccentGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBengali) "স্টাফ সংযোগ অনুরোধ (${formatBengaliNumber(pendingPermissions.size)})" else "Staff Connection Requests (${pendingPermissions.size})",
                                color = DarkTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        pendingPermissions.forEach { req ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkBg),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(0.6f)) {
                                            Text(
                                                text = "${if (isBengali) "স্টাফ:" else "Staff:"} ${req.staffUsername}",
                                                color = DarkTextPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "${if (isBengali) "ফোন:" else "Phone:"} ${req.staffPhone}",
                                                color = DarkTextSecondary,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.weight(0.4f),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            IconButton(
                                                onClick = { viewModel.rejectPermissionRequest(req) },
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .background(DarkAccentRed.copy(alpha = 0.15f), androidx.compose.foundation.shape.CircleShape)
                                            ) {
                                                Icon(Icons.Default.Close, contentDescription = "Reject", tint = DarkAccentRed, modifier = Modifier.size(18.dp))
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            IconButton(
                                                onClick = { viewModel.approvePermissionRequest(req) },
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .background(SuccessColor.copy(alpha = 0.15f), androidx.compose.foundation.shape.CircleShape)
                                            ) {
                                                Icon(Icons.Default.Check, contentDescription = "Approve", tint = SuccessColor, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

        // --- CUSTOMER ORDERS MANAGEMENT PANEL (FOR MERCHANT & STAFF) ---
        if ((viewModel.isMerchant(currentUser) || currentUser.role == "staff") && customerOrders.isNotEmpty()) {
            val pendingOrders = customerOrders.filter { it.status == "Pending" || it.status == "অপেক্ষমাণ" }
            if (pendingOrders.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, InfoColor.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ShoppingCart,
                                contentDescription = "Customer Orders",
                                tint = InfoColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBengali) "গ্রাহকদের নতুন অর্ডার (${formatBengaliNumber(pendingOrders.size)})" else "Pending Customer Orders (${pendingOrders.size})",
                                color = DarkTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        pendingOrders.forEach { order ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkBg),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(modifier = Modifier.weight(0.7f)) {
                                            Text(
                                                text = order.medicineName,
                                                color = DarkTextPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "${if (isBengali) "অর্ডারকারী: " else "Ordered by: "}${order.customerUsername}",
                                                color = DarkTextSecondary,
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                text = "${if (isBengali) "তারিখ: " else "Date: "}${order.dateString}",
                                                color = DarkTextSecondary,
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                text = "${if (isBengali) "ফার্মেসি: " else "Pharmacy: "}${order.pharmacyName}",
                                                color = InfoColor,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Column(
                                            modifier = Modifier.weight(0.3f),
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            Text(
                                                text = "${if (isBengali) "পরিমাণ: " else "Qty: "}${formatBengaliNumber(order.quantity)}",
                                                color = DarkTextPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = "৳${formatBengaliNumber(String.format("%.1f", order.totalPrice))}",
                                                color = DarkAccentGold,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        OutlinedButton(
                                            onClick = { viewModel.updateOrderStatus(order, "Cancelled") },
                                            border = BorderStroke(1.dp, DarkAccentRed.copy(alpha = 0.5f)),
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkAccentRed),
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(if (isBengali) "বাতিল" else "Reject", fontSize = 11.sp)
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Button(
                                            onClick = { viewModel.updateOrderStatus(order, "Confirmed") },
                                            colors = ButtonDefaults.buttonColors(containerColor = SuccessColor, contentColor = Color.White),
                                            modifier = Modifier.height(32.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(if (isBengali) "নিশ্চিত করুন" else "Confirm", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (todayBillCount == 0) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = DarkTextSecondary.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isBengali) "এখনও কোনো বিক্রয়ের তথ্য নেই" else "No sales recorded yet today",
                        color = DarkTextSecondary.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Smart Analytics Suggestion Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface.copy(alpha = 0.8f)),
            border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Psychology, "ai", tint = DarkAccentGold, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isBengali) "নিকট এআই সংক্ষিপ্ত বিশ্লেষণ" else "NIKOT AI Quick Insight",
                        color = DarkTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isBengali) "নিচে আপনার ফার্মেসির মজুদ ও বিক্রয় বিশ্লেষণের জন্য আমাদের এআই অ্যাসিস্ট্যান্টকে সক্রিয় করুন।" else "Activate AI assistant to run dynamic inventory, expiry and restock checks.",
                    color = DarkTextSecondary,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { viewModel.askGeminiPharmacyInsight() },
                    colors = ButtonDefaults.buttonColors(containerColor = DarkAccentRed, contentColor = DarkAccentText),
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(if (isBengali) "বিশ্লেষণ করুন (Ask AI)" else "Analyze with AI", color = DarkAccentText)
                }
                
                val aiLoading by viewModel.aiIsLoading.collectAsState()
                val aiMessage by viewModel.aiResponse.collectAsState()
                
                if (aiLoading || aiMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider(color = Color.LightGray.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = aiMessage,
                        color = DarkTextPrimary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    // Detail Screens overlays via dialogs
    when (activeDetailPane) {
        1 -> TodaySalesDetailsDialog(viewModel, todayBillList, isBengali) { activeDetailPane = null }
        2 -> TodayBillHistoryDialog(viewModel, todayBillList, isBengali) { activeDetailPane = null }
        3 -> PlainMedicinesListDialog(if (isBengali) "কম স্টক সতর্কতা তালিকা" else "Low Stock Medicines", lowStockMedicines, isBengali) { activeDetailPane = null }
        4 -> PlainMedicinesListDialog(if (isBengali) "মেয়াদ শেষ সতর্কতা তালিকা" else "Expiry Alerts", expiringMedicines, isBengali) { activeDetailPane = null }
    }

    if (showRefundWorkflowDialog) {
        RefundWorkflowDialog(viewModel, todayBillList, isBengali) { showRefundWorkflowDialog = false }
    }

    if (showNotificationsDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showNotificationsDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Scaffold(
                containerColor = DarkBg,
                modifier = Modifier.fillMaxSize()
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .background(DarkBg)
                ) {
                    NotificationsScreen(viewModel, isBengali, onBack = { showNotificationsDialog = false })
                }
            }
        }
    }
}
}

