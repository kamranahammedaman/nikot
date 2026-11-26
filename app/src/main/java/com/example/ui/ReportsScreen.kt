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
fun ReportsScreen(viewModel: PharmacyViewModel, isBengali: Boolean) {
    val reportBills by viewModel.reportBills.collectAsState()
    val reportPeriod by viewModel.reportPeriod.collectAsState()
    
    var showMonthSelectorDialog by remember { mutableStateOf(false) }
    
    val monthList = remember(isBengali) {
        val list = mutableListOf<Pair<String, String>>()
        val cal = Calendar.getInstance()
        for (i in 0 until 24) {
            val y = cal.get(Calendar.YEAR)
            val m = cal.get(Calendar.MONTH) + 1
            val key = "month_${y}_${String.format("%02d", m)}"
            
            val engLabel = when (m) {
                1 -> "January"
                2 -> "February"
                3 -> "March"
                4 -> "April"
                5 -> "May"
                6 -> "June"
                7 -> "July"
                8 -> "August"
                9 -> "September"
                10 -> "October"
                11 -> "November"
                12 -> "December"
                else -> ""
            } + " $y"
            
            val mNameBg = when (m) {
                1 -> "জানুয়ারি"
                2 -> "ফেব্রুয়ারি"
                3 -> "মার্চ"
                4 -> "এপ্রিল"
                5 -> "মে"
                6 -> "জুন"
                7 -> "জুলাই"
                8 -> "আগস্ট"
                9 -> "সেপ্টেম্বর"
                10 -> "অক্টোবর"
                11 -> "নভেম্বর"
                12 -> "ডিসেম্বর"
                else -> ""
            }
            val bgLabel = "$mNameBg ${formatBengaliNumber(y)}"
            
            list.add(key to if (isBengali) bgLabel else engLabel)
            cal.add(Calendar.MONTH, -1)
        }
        list
    }
    val medicines by viewModel.allMedicines.collectAsState()
    val stockInRecords by viewModel.allStockInRecords.collectAsState()
    val expiryAlertMonths by viewModel.expiryAlertMonths.collectAsState()
    val allBills by viewModel.allBills.collectAsState()
    val lowStockAlertFactor by viewModel.lowStockAlertFactor.collectAsState()

    val reportSalesLabel = remember(reportPeriod, isBengali) {
        if (isBengali) {
            when (reportPeriod) {
                "today" -> "আজকের মোট বিক্রয় (৳)"
                "week" -> "এই সপ্তাহের মোট বিক্রয় (৳)"
                "month" -> "এই মাসের মোট বিক্রয় (৳)"
                else -> {
                    if (reportPeriod.startsWith("month_")) {
                        "${getPeriodLabelBangali(reportPeriod)} এর মোট বিক্রয় (৳)"
                    } else {
                        "মোট বিক্রয় (৳)"
                    }
                }
            }
        } else {
            when (reportPeriod) {
                "today" -> "Today's Total Sales (৳)"
                "week" -> "This Week's Total Sales (৳)"
                "month" -> "This Month's Total Sales (৳)"
                else -> {
                    if (reportPeriod.startsWith("month_")) {
                        "${getPeriodLabelEnglish(reportPeriod)} Total Sales (৳)"
                    } else {
                        "Total Sales (৳)"
                    }
                }
            }
        }
    }

    val reportBillsLabel = remember(reportPeriod, isBengali) {
        if (isBengali) {
            when (reportPeriod) {
                "today" -> "আজকের মোট বিল সংখ্যা"
                "week" -> "এই সপ্তাহের মোট বিল সংখ্যা"
                "month" -> "এই মাসের মোট বিল সংখ্যা"
                else -> {
                    if (reportPeriod.startsWith("month_")) {
                        "${getPeriodLabelBangali(reportPeriod)} এর মোট বিল সংখ্যা"
                    } else {
                        "মোট বিল সংখ্যা"
                    }
                }
            }
        } else {
            when (reportPeriod) {
                "today" -> "Today's Total Bills"
                "week" -> "This Week's Total Bills"
                "month" -> "This Month's Total Bills"
                else -> {
                    if (reportPeriod.startsWith("month_")) {
                        "${getPeriodLabelEnglish(reportPeriod)} Total Bills"
                    } else {
                        "Total Bills"
                    }
                }
            }
        }
    }

    val reportTopSellingLabel = remember(reportPeriod, isBengali) {
        if (isBengali) {
            when (reportPeriod) {
                "today" -> "আজকের টপ সেলিং ওষুধ"
                "week" -> "এই সপ্তাহের টপ সেলিং ওষুধ"
                "month" -> "এই মাসের টপ সেলিং ওষুধ"
                else -> {
                    if (reportPeriod.startsWith("month_")) {
                        "${getPeriodLabelBangali(reportPeriod)} এর টপ সেলিং ওষুধ"
                    } else {
                        "টপ সেলিং ওষুধ র‍্যাংকিং"
                    }
                }
            }
        } else {
            when (reportPeriod) {
                "today" -> "Today's Top Selling Medicines"
                "week" -> "This Week's Top Selling Medicines"
                "month" -> "This Month's Top Selling Medicines"
                else -> {
                    if (reportPeriod.startsWith("month_")) {
                        "${getPeriodLabelEnglish(reportPeriod)} Top Selling Medicines"
                    } else {
                        "Top Selling Medicines"
                    }
                }
            }
        }
    }

    var activeTab by remember { mutableStateOf(0) } // 0 = Summary, 1 = Stock, 2 = Stock In, 3 = Sales History
    var stockSearchQuery by remember { mutableStateOf("") }
    var stockStatusTab by remember { mutableStateOf(0) } // 0: Available Stock, 1: Out of Stock (Zero)

    val groupedRecords = remember(stockInRecords, isBengali) {
        stockInRecords.groupBy { r ->
            if (isBengali) {
                formatBengaliDate(r.timestamp)
            } else {
                val sdf = SimpleDateFormat("d MMMM yyyy", Locale.US)
                sdf.format(Date(r.timestamp))
            }
        }
    }

    val sortedGroupedRecords = remember(groupedRecords) {
        groupedRecords.toList().sortedByDescending { (_, records) ->
            records.maxOfOrNull { it.timestamp } ?: 0L
        }
    }

    val salesGroupedByDate = remember(allBills, isBengali) {
        allBills.groupBy { b ->
            if (isBengali) {
                formatBengaliDate(b.timestamp)
            } else {
                val sdf = SimpleDateFormat("d MMMM yyyy", Locale.US)
                sdf.format(Date(b.timestamp))
            }
        }
    }

    val sortedSalesGrouped = remember(salesGroupedByDate) {
        salesGroupedByDate.toList().sortedByDescending { (_, bills) ->
            bills.maxOfOrNull { it.timestamp } ?: 0L
        }
    }

    val totalRevenue = reportBills.sumOf { it.totalAmount - it.returnedAmount }
    val totalBillsCount = reportBills.filter { !it.isReturned }.size
    val totalDiscount = reportBills.sumOf { it.discount }

    var showSalesDetailDialog by remember { mutableStateOf(false) }
    var showBillsListDialog by remember { mutableStateOf(false) }
    var showDiscountsListDialog by remember { mutableStateOf(false) }
    var showProfitsListDialog by remember { mutableStateOf(false) }

    val totalProfit = remember(reportBills, medicines) {
        reportBills.sumOf { bill ->
            val originalItems = viewModel.getBillItemsList(bill)
            val returnedItems = viewModel.getReturnedItemsList(bill)
            
            val originalCost = originalItems.sumOf { item ->
                val med = medicines.find { m -> m.id == item.medicineId }
                if (med != null) {
                    if (med.type == "tablet") {
                        (item.soldBoxes * med.purchasePriceBox) + 
                        (item.soldStrips * med.purchasePriceStrip) + 
                        (item.soldPcs * med.purchasePricePiece)
                    } else {
                        (item.soldBottles + item.soldTubes + item.soldVials + item.soldOtherQty) * med.purchasePriceUnit
                    }
                } else {
                    item.totalPrice * 0.85
                }
            }
            
            val returnedCost = returnedItems.sumOf { item ->
                val med = medicines.find { m -> m.id == item.medicineId }
                if (med != null) {
                    if (med.type == "tablet") {
                        (item.soldBoxes * med.purchasePriceBox) + 
                        (item.soldStrips * med.purchasePriceStrip) + 
                        (item.soldPcs * med.purchasePricePiece)
                    } else {
                        (item.soldBottles + item.soldTubes + item.soldVials + item.soldOtherQty) * med.purchasePriceUnit
                    }
                } else {
                    item.totalPrice * 0.85
                }
            }
            
            val effectiveCost = originalCost - returnedCost
            val netRevenue = bill.totalAmount - bill.returnedAmount
            netRevenue - effectiveCost
        }
    }

    // Calculate top-selling medicines
    val soldCountMap = mutableMapOf<String, Int>()
    reportBills.forEach { bill ->
        val items = viewModel.getBillItemsList(bill)
        items.forEach { item ->
            val count = when (item.type) {
                "tablet" -> (item.soldBoxes * 10) + item.soldStrips + item.soldPcs // approximation
                else -> item.soldBottles + item.soldTubes + item.soldVials + item.soldOtherQty.roundToInt()
            }
            soldCountMap[item.name] = (soldCountMap[item.name] ?: 0) + count
        }
    }
    val topSellingList = soldCountMap.toList().sortedByDescending { it.second }

    // Low stock list sorted ascending
    val lowStockList = medicines.filter { m ->
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
    }.sortedBy { it.totalPiecesInStock }

    // High stock list sorted descending
    val highStockList = medicines.filter { m ->
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
            "tablet" -> m.totalPiecesInStock >= limit
            "syrup" -> m.bottlesInStock >= limit
            "ointment" -> m.tubesInStock >= limit
            "injection" -> m.vialsInStock >= limit
            else -> m.otherQuantity >= limit.toDouble()
        }
    }.sortedByDescending { it.totalPiecesInStock }

    // Expiry list warns
    val systemCalendar = Calendar.getInstance()
    systemCalendar.add(Calendar.MONTH, expiryAlertMonths) // Dynamic warning months from state
    val maxWarningMillis = systemCalendar.timeInMillis

    val expiringList = medicines.filter { m ->
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val t = sdf.parse(m.expiryDate)?.time ?: Long.MAX_VALUE
            t <= maxWarningMillis
        } catch (_: Exception) {
            false
        }
    }.sortedBy { it.expiryDate }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        RoLogoHeader()
        Spacer(modifier = Modifier.height(12.dp))

        // Four tabs header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(DarkSurface)
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val tabTitles = if (isBengali) {
                listOf("সারসংক্ষেপ", "স্টক", "স্টক ইন", "বিক্রয়")
            } else {
                listOf("Summary", "Stock", "Stock In", "Sales")
            }
            
            tabTitles.forEachIndexed { index, title ->
                val isSelected = activeTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) DarkAccentGold else Color.Transparent)
                        .clickable { activeTab = index }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = if (isSelected) Color.Black else DarkTextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (activeTab) {
            0 -> {
                // TAB 1 — সারসংক্ষেপ (Summary)
                // Standard Filter Period selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (isBengali) "তারিখ ফিল্টার:" else "Period Filter:", color = DarkTextSecondary, fontSize = 12.sp)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterTabButton(
                            label = if (isBengali) "আজ" else "Today",
                            selected = reportPeriod == "today",
                            onClick = { viewModel.setReportPeriod("today") }
                        )
                        FilterTabButton(
                            label = if (isBengali) "এই সপ্তাহ" else "This Week",
                            selected = reportPeriod == "week",
                            onClick = { viewModel.setReportPeriod("week") }
                        )
                        FilterTabButton(
                            label = if (isBengali) "এই মাস" else "This Month",
                            selected = reportPeriod == "month",
                            onClick = { viewModel.setReportPeriod("month") }
                        )
                        
                        // Custom month tab button
                        val customMonthLabel = if (reportPeriod.startsWith("month_")) {
                            if (isBengali) getPeriodLabelBangali(reportPeriod) else getPeriodLabelEnglish(reportPeriod)
                        } else {
                            if (isBengali) "মাস নির্বাচন" else "Select Month"
                        }
                        
                        FilterTabButton(
                            label = customMonthLabel,
                            selected = reportPeriod.startsWith("month_"),
                            onClick = { showMonthSelectorDialog = true }
                        )
                    }
                }

                if (showMonthSelectorDialog) {
                    androidx.compose.ui.window.Dialog(onDismissRequest = { showMonthSelectorDialog = false }) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.7f)
                                .padding(16.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = BorderStroke(1.dp, SleekBorderColor)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = if (isBengali) "মাস নির্বাচন করুন" else "Select Month",
                                    color = DarkAccentGold,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 12.dp)
                                )
                                Divider(color = SleekBorderColor, modifier = Modifier.padding(bottom = 8.dp))
                                
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(monthList) { (key, label) ->
                                        val isSelected = reportPeriod == key
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (isSelected) DarkAccentRed.copy(alpha = 0.2f) else Color.Transparent)
                                                .clickable {
                                                    viewModel.setReportPeriod(key)
                                                    showMonthSelectorDialog = false
                                                }
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isSelected) DarkAccentRed else Color.Transparent,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .padding(horizontal = 12.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (isSelected) DarkAccentGold else DarkTextPrimary,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 14.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Selected",
                                                    tint = DarkAccentGold,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { showMonthSelectorDialog = false }) {
                                        Text(if (isBengali) "বাতিল" else "Cancel", color = DarkTextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Indicator metrics cards - 2x2 Clickable Grid
                val reportDiscountLabel = if (isBengali) "মোট দেওয়া ছাড় (৳)" else "Total Discount (৳)"
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(95.dp)
                                .clickable { showSalesDetailDialog = true },
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = BorderStroke(1.dp, DarkAccentGold.copy(alpha = 0.15f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.Center) {
                                Text(reportSalesLabel, color = DarkTextSecondary, fontSize = 11.sp, minLines = 2)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "৳${formatCurrency(totalRevenue, isBengali)}",
                                        color = DarkAccentGold,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.ChevronRight,
                                        contentDescription = "Details",
                                        tint = DarkAccentGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(95.dp)
                                .clickable { showBillsListDialog = true },
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.Center) {
                                Text(reportBillsLabel, color = DarkTextSecondary, fontSize = 11.sp, minLines = 2)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isBengali) "${formatBengaliNumber(totalBillsCount)} টি" else "$totalBillsCount",
                                        color = DarkTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.ChevronRight,
                                        contentDescription = "Details",
                                        tint = DarkTextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(95.dp)
                                .clickable { showDiscountsListDialog = true },
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.Center) {
                                Text(reportDiscountLabel, color = DarkTextSecondary, fontSize = 11.sp, minLines = 2)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "৳${formatCurrency(totalDiscount, isBengali)}",
                                        color = DarkAccentRed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.ChevronRight,
                                        contentDescription = "Details",
                                        tint = DarkAccentRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(95.dp)
                                .clickable { showProfitsListDialog = true },
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = BorderStroke(1.dp, if (totalProfit >= 0) SuccessColor.copy(alpha = 0.2f) else DarkAccentRed.copy(alpha = 0.2f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.Center) {
                                val resolvedReportProfitLabel = if (isBengali) {
                                    when (reportPeriod) {
                                        "today" -> "আজকের মোট লাভ (৳)"
                                        "week" -> "এই সপ্তাহের মোট লাভ (৳)"
                                        "month" -> "এই মাসের মোট লাভ (৳)"
                                        else -> "মোট লাভ (৳)"
                                    }
                                } else {
                                    when (reportPeriod) {
                                        "today" -> "Today's Total Profit (৳)"
                                        "week" -> "This Week's Total Profit (৳)"
                                        "month" -> "This Month's Total Profit (৳)"
                                        else -> "Total Profit (৳)"
                                    }
                                }
                                Text(resolvedReportProfitLabel, color = DarkTextSecondary, fontSize = 11.sp, minLines = 2)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "৳${formatCurrency(totalProfit, isBengali)}",
                                        color = if (totalProfit >= 0) SuccessColor else DarkAccentRed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.ChevronRight,
                                        contentDescription = "Details",
                                        tint = if (totalProfit >= 0) SuccessColor else DarkAccentRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // New Total Stock Value Card
                val totalStockPurchase = remember(medicines) {
                    medicines.sumOf { med ->
                        when (med.type) {
                            "tablet" -> med.purchasePricePiece * med.totalPiecesInStock
                            "syrup" -> med.purchasePriceUnit * med.bottlesInStock
                            "ointment" -> med.purchasePriceUnit * med.tubesInStock
                            "injection" -> med.purchasePriceUnit * med.vialsInStock
                            else -> med.purchasePriceUnit * med.otherQuantity
                        }
                    }
                }

                val totalStockSale = remember(medicines) {
                    medicines.sumOf { med ->
                        when (med.type) {
                            "tablet" -> med.salePricePiece * med.totalPiecesInStock
                            "syrup" -> med.salePriceUnit * med.bottlesInStock
                            "ointment" -> med.salePriceUnit * med.tubesInStock
                            "injection" -> med.salePriceUnit * med.vialsInStock
                            else -> med.salePriceUnit * med.otherQuantity
                        }
                    }
                }

                val potentialProfit = totalStockSale - totalStockPurchase

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface),
                    border = BorderStroke(1.dp, DarkAccentGold.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (isBengali) "মোট স্টক মূল্য" else "Total Stock Value",
                            color = DarkAccentGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isBengali) "মোট স্টক (ক্রয়):" else "Total Stock (Purchase):",
                                color = DarkTextSecondary,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "৳${formatCurrency(totalStockPurchase, isBengali)}",
                                color = DarkTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isBengali) "মোট স্টক (বিক্রয়):" else "Total Stock (Sale):",
                                color = DarkTextSecondary,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "৳${formatCurrency(totalStockSale, isBengali)}",
                                color = DarkTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider(color = Color.LightGray.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (isBengali) "সম্ভাব্য লাভ:" else "Potential Profit:",
                                color = DarkTextSecondary,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "৳${formatCurrency(potentialProfit, isBengali)}",
                                color = if (potentialProfit >= 0) SuccessColor else DarkAccentRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 1. Top Selling rankings
                Text(reportTopSellingLabel, color = DarkAccentGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                if (topSellingList.isEmpty()) {
                    Text(if (isBengali) "কোনো তথ্য উপলব্ধ নেই।" else "No data available.", color = DarkTextSecondary, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                } else {
                    topSellingList.take(5).forEachIndexed { index, pair ->
                        val rankingStr = if (isBengali) formatBengaliNumber(index + 1) else (index + 1).toString()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${rankingStr}. ${pair.first}", color = DarkTextPrimary, fontSize = 13.sp)
                            Text("${if (isBengali) "বিক্রি হয়েছে: " else "Sold estimation: "}${formatBengaliNumber(pair.second)}", color = DarkTextSecondary, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                Divider(color = Color.LightGray.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(18.dp))

                // 2. Low stock list
                Text(if (isBengali) "কম স্টক তালিকা" else "Low Stock List", color = DarkAccentGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                if (lowStockList.isEmpty()) {
                    Text(if (isBengali) "সতর্কতা নেই!" else "No low stock alerts!", color = Color.Green, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                } else {
                    lowStockList.take(5).forEach { med ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(med.fullName, color = DarkTextPrimary, fontSize = 12.sp)
                            Text(formatMedicineStock(med, isBengali), color = DarkAccentRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                Divider(color = Color.LightGray.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(18.dp))

                // 3. High stock list
                Text(if (isBengali) "বেশি স্টক তালিকা" else "High Stock List", color = DarkAccentGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                if (highStockList.isEmpty()) {
                    Text(if (isBengali) "কোনো ঔষধ মজুত নেই।" else "No stocks available.", color = DarkTextSecondary, fontSize = 12.sp)
                } else {
                    highStockList.take(5).forEach { med ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(med.fullName, color = DarkTextPrimary, fontSize = 12.sp)
                            Text(formatMedicineStock(med, isBengali), color = Color.Cyan, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                Divider(color = Color.LightGray.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(18.dp))

                // 4. Expiring and Expiry alert details list
                Text(if (isBengali) "মেয়اد শেষ সতর্কতা" else "Expiry warnings", color = DarkAccentGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                if (expiringList.isEmpty()) {
                    Text(if (isBengali) "সব ওষুধের মেয়াদ ঠিক আছে।" else "All medications safe.", color = Color.Green, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
                } else {
                    expiringList.take(5).forEach { med ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(med.fullName, color = DarkTextPrimary, fontSize = 12.sp)
                            Text(med.expiryDate, color = DarkAccentRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            1 -> {
                // TAB 2 — স্টক (Stock)
                Text(
                    text = if (isBengali) "ফার্মেসীর বর্তমান স্টক রিপোর্ট" else "Pharmacy Current Stock Report",
                    color = DarkAccentGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Search Box
                OutlinedTextField(
                    value = stockSearchQuery,
                    onValueChange = { stockSearchQuery = it },
                    label = { Text(if (isBengali) "ওষুধ খুঁজুন..." else "Search medicine...") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DarkAccentRed,
                        unfocusedBorderColor = DarkTextSecondary,
                        focusedLabelColor = DarkAccentRed,
                        unfocusedLabelColor = DarkTextSecondary
                    ),
                    trailingIcon = {
                        if (stockSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { stockSearchQuery = "" }) {
                                Icon(Icons.Filled.Close, "clear", tint = DarkTextPrimary)
                            }
                        }
                    }
                )

                // Stock status segmented control (মজুদ আছে vs স্টক শেষ)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurface, RoundedCornerShape(10.dp))
                        .border(1.dp, SleekBorderColor, RoundedCornerShape(10.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (stockStatusTab == 0) DarkAccentRed else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { stockStatusTab = 0 }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isBengali) "মজুদ আছে (স্টক)" else "Available Stock",
                            color = if (stockStatusTab == 0) DarkAccentText else DarkTextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(if (stockStatusTab == 1) DarkAccentRed else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { stockStatusTab = 1 }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isBengali) "একেবারেই নাই (স্টক শেষ)" else "Out of Stock (Zero)",
                            color = if (stockStatusTab == 1) DarkAccentText else DarkTextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                val stockFilteredList = medicines.filter {
                    val matchesSearch = it.name.contains(stockSearchQuery, ignoreCase = true) ||
                        it.fullName.contains(stockSearchQuery, ignoreCase = true) ||
                        it.genericName.contains(stockSearchQuery, ignoreCase = true) ||
                        it.company.contains(stockSearchQuery, ignoreCase = true) ||
                        it.barcode == stockSearchQuery.trim()

                    val isOutOfStock = when (it.type) {
                        "tablet" -> it.totalPiecesInStock <= 0
                        "syrup" -> it.bottlesInStock <= 0
                        "ointment" -> it.tubesInStock <= 0
                        "injection" -> it.vialsInStock <= 0
                        else -> it.otherQuantity <= 0.0
                    }

                    val matchesTab = if (stockStatusTab == 0) !isOutOfStock else isOutOfStock

                    matchesSearch && matchesTab
                }.sortedBy { it.name }

                var showDetailedStock by remember { mutableStateOf(false) }
                var expandedMedicines by remember { mutableStateOf(setOf<Long>()) }

                if (stockFilteredList.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isBengali) "বিস্তারিত বিবরণ দেখান" else "Show Details",
                            color = DarkTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Switch(
                            checked = showDetailedStock,
                            onCheckedChange = { showDetailedStock = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DarkAccentGold,
                                checkedTrackColor = DarkAccentGold.copy(alpha = 0.5f),
                                uncheckedThumbColor = DarkTextSecondary,
                                uncheckedTrackColor = DarkCardBg
                            )
                        )
                    }
                }

                if (stockFilteredList.isEmpty()) {
                    Text(
                        text = if (isBengali) "কোনো ওষুধ পাওয়া যায়নি।" else "No medicines found.",
                        color = DarkTextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp)
                    )
                } else {
                    stockFilteredList.forEach { med ->
                        val isDetailActive = showDetailedStock || expandedMedicines.contains(med.id)
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = BorderStroke(1.dp, SleekBorderColor)
                        ) {
                            if (!isDetailActive) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val qtyText = formatMedicineStock(med, isBengali)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "• ${med.fullName} → $qtyText",
                                            color = DarkTextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isBengali) "বিস্তারিত" else "Details",
                                        color = DarkAccentGold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable {
                                                expandedMedicines = expandedMedicines + med.id
                                            }
                                            .background(DarkAccentGold.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            } else {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = med.fullName,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            if (med.genericName.isNotBlank()) {
                                                Text(
                                                    text = med.genericName,
                                                    color = DarkTextSecondary,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (!showDetailedStock) {
                                                Text(
                                                    text = if (isBengali) "সংক্ষিপ্ত" else "Collapse",
                                                    color = DarkTextSecondary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier
                                                        .clickable {
                                                            expandedMedicines = expandedMedicines - med.id
                                                        }
                                                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                            }
                                            
                                            val accentRed = DarkAccentRed
                                            val accentGold = DarkAccentGold
                                            val textSecondary = DarkTextSecondary

                                            val expiryColor = remember(med.expiryDate) {
                                                try {
                                                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                                    val t = sdf.parse(med.expiryDate)?.time ?: Long.MAX_VALUE
                                                    val now = System.currentTimeMillis()
                                                    val diff = t - now
                                                    when {
                                                        diff < 0 -> accentRed
                                                        diff < 30L * 24 * 3600 * 1000 -> accentGold
                                                        else -> SuccessColor
                                                    }
                                                } catch (_: Exception) {
                                                    textSecondary
                                                }
                                            }
                                            
                                            Text(
                                                text = if (isBengali) "মেয়াদ: ${formatBengaliNumber(med.expiryDate)}" else "Exp: ${med.expiryDate}",
                                                color = expiryColor,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .background(expiryColor.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = formatMedicineStock(med, isBengali),
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    
                                    val limit = if (med.lowStockThreshold > 0) med.lowStockThreshold else {
                                        when (med.type) {
                                            "tablet" -> 50
                                            "syrup" -> 5
                                            "ointment" -> 5
                                            "injection" -> 5
                                            else -> 5
                                        }
                                    }
                                    val isLow = when (med.type) {
                                        "tablet" -> med.totalPiecesInStock < limit
                                        "syrup" -> med.bottlesInStock < limit
                                        "ointment" -> med.tubesInStock < limit
                                        "injection" -> med.vialsInStock < limit
                                        else -> med.otherQuantity < limit.toDouble()
                                    }
                                    
                                    if (isLow) {
                                        Text(
                                            text = if (isBengali) "কম স্টক!" else "Low Stock!",
                                            color = DarkAccentRed,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .background(DarkAccentRed.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    } else {
                                        Text(
                                            text = if (isBengali) "পর্যাপ্ত স্টক" else "In Stock",
                                            color = SuccessColor,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .background(SuccessColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                Divider(color = Color.White.copy(alpha = 0.05f))
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val purchaseValue = when (med.type) {
                                        "tablet" -> med.purchasePricePiece * med.totalPiecesInStock
                                        "syrup" -> med.purchasePriceUnit * med.bottlesInStock
                                        "ointment" -> med.purchasePriceUnit * med.tubesInStock
                                        "injection" -> med.purchasePriceUnit * med.vialsInStock
                                        else -> med.purchasePriceUnit * med.otherQuantity
                                    }
                                    
                                    val purchasePriceStr = if (med.type == "tablet") {
                                        if (isBengali) "ক্রয়: ৳${formatCurrency(med.purchasePriceBox, isBengali)}/বক্স" else "Pur: ৳${formatCurrency(med.purchasePriceBox, isBengali)}/Box"
                                    } else {
                                        if (isBengali) "ক্রয়: ৳${formatCurrency(med.purchasePriceUnit, isBengali)}" else "Pur: ৳${formatCurrency(med.purchasePriceUnit, isBengali)}"
                                    }
                                    
                                    val salePriceStr = if (med.type == "tablet") {
                                        if (isBengali) "বিক্রয়: ৳${formatCurrency(med.salePriceBox, isBengali)}/বক্স" else "Sale: ৳${formatCurrency(med.salePriceBox, isBengali)}/Box"
                                    } else {
                                        if (isBengali) "বিক্রয়: ৳${formatCurrency(med.salePriceUnit, isBengali)}" else "Sale: ৳${formatCurrency(med.salePriceUnit, isBengali)}"
                                    }
                                    
                                    Column {
                                        Text(
                                            text = "$purchasePriceStr  |  $salePriceStr",
                                            color = DarkTextSecondary,
                                            fontSize = 11.sp
                                        )
                                    }
                                    
                                    Text(
                                        text = if (isBengali) "স্টক মূল্য: ৳${formatCurrency(purchaseValue, isBengali)}" else "Value: ৳${formatCurrency(purchaseValue, isBengali)}",
                                        color = DarkAccentGold,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        2 -> {
                // TAB 3 — স্টক ইন হিস্ট্রি (Stock In History)
                Text(
                    text = if (isBengali) "স্টক ইন ইতিহাস" else "Stock In History",
                    color = DarkAccentGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                if (sortedGroupedRecords.isEmpty()) {
                    Text(
                        text = if (isBengali) "কোনো স্টক যুক্ত করার ইতিহাস নেই।" else "No stock addition history records found.",
                        color = DarkTextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                } else {
                    sortedGroupedRecords.forEach { (dateStr, records) ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = BorderStroke(1.dp, SleekBorderColor)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = dateStr,
                                    color = DarkAccentGold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                val combinedRecordsForDay = remember(records, medicines) {
                                    records.groupBy { it.medicineId }.map { (medId, list) ->
                                        val first = list.first()
                                        val med = medicines.find { it.id == medId }
                                        if (med != null && med.type == "tablet") {
                                            val sPerBox = if (med.stripsPerBox > 0) med.stripsPerBox else 10
                                            val pPerStrip = if (med.pcsPerStrip > 0) med.pcsPerStrip else 10
                                            var totalPcs = 0
                                            list.forEach { r ->
                                                totalPcs += (r.addedBoxes * sPerBox * pPerStrip) + (r.addedStrips * pPerStrip) + r.addedPcs
                                            }
                                            val finalBoxes = totalPcs / (sPerBox * pPerStrip)
                                            val remPieces = totalPcs % (sPerBox * pPerStrip)
                                            val finalStrips = remPieces / pPerStrip
                                            val finalPcs = totalPcs % pPerStrip
                                            first.copy(
                                                addedBoxes = finalBoxes,
                                                addedStrips = finalStrips,
                                                addedPcs = finalPcs
                                            )
                                        } else if (med != null && med.type == "syrup") {
                                            val totalBot = list.sumOf { it.addedBottles }
                                            first.copy(addedBottles = totalBot)
                                        } else if (med != null && med.type == "ointment") {
                                            val totalTube = list.sumOf { it.addedTubes }
                                            first.copy(addedTubes = totalTube)
                                        } else if (med != null && med.type == "injection") {
                                            val totalVial = list.sumOf { it.addedVials }
                                            first.copy(addedVials = totalVial)
                                        } else {
                                            val totalO = list.sumOf { it.addedOtherQty }
                                            first.copy(addedOtherQty = totalO)
                                        }
                                    }
                                }
                                combinedRecordsForDay.forEach { r ->
                                    val med = medicines.find { it.id == r.medicineId }
                                    val displayName = if (med != null && med.dosageStrength.isNotBlank()) {
                                        "${r.medicineName} ${med.dosageStrength}"
                                    } else {
                                        r.medicineName
                                    }
                                    val qtyText = formatStockInLog(r, isBengali)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "• $displayName → $qtyText",
                                            color = DarkTextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            3 -> {
                // TAB 4 — বিক্রয় হিস্ট্রি (Sales History)
                Text(
                    text = if (isBengali) "বিক্রয় ইতিহাস" else "Sales History",
                    color = DarkAccentGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (sortedSalesGrouped.isEmpty()) {
                    Text(
                        text = if (isBengali) "কোনো বিক্রয়ের ইতিহাস পাওয়া যায়নি।" else "No sales history records found.",
                        color = DarkTextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                } else {
                    sortedSalesGrouped.forEach { (dateStr, billsForDate) ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = BorderStroke(1.dp, SleekBorderColor)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = dateStr,
                                    color = DarkAccentGold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                billsForDate.forEach { bill ->
                                    val items = viewModel.getBillItemsList(bill)
                                    val isReturned = bill.isReturned
                                    
                                    if (isReturned) {
                                        val returnedItems = viewModel.getReturnedItemsList(bill)
                                        val isFullyReturned = bill.returnedAmount >= bill.totalAmount
                                        val returnedLabel = if (isBengali) {
                                            if (isFullyReturned) "পূর্ণ ফেরত" else "আংশিক ফেরত"
                                        } else {
                                            if (isFullyReturned) "Fully Returned" else "Partial Return"
                                        }
                                        val returnedTimeStr = if (bill.returnedTime > 0L) {
                                            val sdf = SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.US)
                                            val formatted = sdf.format(Date(bill.returnedTime))
                                            if (isBengali) formatBengaliNumber(formatted) else formatted
                                        } else {
                                            ""
                                        }

                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            colors = CardDefaults.cardColors(containerColor = DarkAccentRed.copy(alpha = 0.08f)),
                                            border = BorderStroke(1.dp, DarkAccentRed.copy(alpha = 0.3f))
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text(
                                                    text = if (isBengali) "বিল ${bill.billNumber} — $returnedLabel" else "Bill ${bill.billNumber} — $returnedLabel",
                                                    color = DarkAccentRed,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                                if (returnedTimeStr.isNotEmpty()) {
                                                    Text(
                                                        text = if (isBengali) "ফেরত তারিখ ও সময়: $returnedTimeStr" else "Returned DateTime: $returnedTimeStr",
                                                        color = DarkAccentRed.copy(alpha = 0.85f),
                                                        fontSize = 11.sp,
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    )
                                                }
                                                Text(
                                                    text = if (isBengali) "ফেরত টাকা: ৳${formatCurrency(bill.returnedAmount, isBengali)}" else "Refund Amount: ৳${formatCurrency(bill.returnedAmount, isBengali)}",
                                                    color = DarkAccentRed,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                                                )
                                                
                                                Divider(color = Color.White.copy(alpha = 0.05f))
                                                Spacer(modifier = Modifier.height(4.dp))

                                                items.forEach { originalItem ->
                                                    val returnedItem = returnedItems.find { it.medicineId == originalItem.medicineId }
                                                    val originalQtyDesc = formatItemQuantity(originalItem, isBengali)
                                                    val originalPriceText = formatCurrency(originalItem.totalPrice, isBengali)
                                                    
                                                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                                        Text(
                                                            text = "• ${originalItem.name}",
                                                            color = Color.White,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = "    ${if (isBengali) "মূল বিক্রয়: " else "Original Sale: "}$originalQtyDesc — ৳$originalPriceText",
                                                            color = DarkTextSecondary,
                                                            fontSize = 12.sp
                                                        )
                                                        if (returnedItem != null) {
                                                            val returnedQtyDesc = formatItemQuantity(returnedItem, isBengali)
                                                            val returnedPriceText = formatCurrency(returnedItem.totalPrice, isBengali)
                                                            val netPriceText = formatCurrency((originalItem.totalPrice - returnedItem.totalPrice).coerceAtLeast(0.0), isBengali)
                                                            
                                                            Text(
                                                                text = "    ${if (isBengali) "ফেরত: " else "Refunded: "}$returnedQtyDesc — ৳$returnedPriceText",
                                                                color = DarkAccentRed,
                                                                fontSize = 12.sp
                                                            )
                                                            Text(
                                                                text = "    ${if (isBengali) "নেট মোট: " else "Net Total: "}৳$netPriceText",
                                                                color = DarkAccentGold,
                                                                fontWeight = FontWeight.Bold,
                                                                fontSize = 11.sp
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = if (isBengali) "বিল ${bill.billNumber}:" else "Bill ${bill.billNumber}:",
                                                color = DarkTextSecondary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            items.forEach { item ->
                                                val qtyText = if (isBengali) formatBengaliNumber(item.formatDescription) else item.formatDescription
                                                val priceText = formatCurrency(item.totalPrice, isBengali)
                                                Text(
                                                    text = "  • ${item.name} → $qtyText → ৳$priceText",
                                                    color = DarkTextPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.padding(start = 8.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                
                                Spacer(modifier = Modifier.height(6.dp))
                                Divider(color = Color.LightGray.copy(alpha = 0.1f))
                                
                                val dayTotal = billsForDate.sumOf { it.totalAmount - it.returnedAmount }
                                val formattedDayTotal = formatCurrency(dayTotal, isBengali)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Text(
                                        text = if (isBengali) "মোট: ৳$formattedDayTotal" else "Total: ৳$formattedDayTotal",
                                        color = DarkAccentGold,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSalesDetailDialog) {
        ReportSalesDetailDialog(
            reportPeriod = reportPeriod,
            todayBills = reportBills,
            viewModel = viewModel,
            isBengali = isBengali,
            onDismiss = { showSalesDetailDialog = false }
        )
    }

    if (showBillsListDialog) {
        ReportBillsHistoryDialog(
            reportPeriod = reportPeriod,
            todayBills = reportBills,
            viewModel = viewModel,
            isBengali = isBengali,
            onDismiss = { showBillsListDialog = false }
        )
    }

    if (showDiscountsListDialog) {
        ReportDiscountHistoryDialog(
            reportPeriod = reportPeriod,
            todayBills = reportBills,
            viewModel = viewModel,
            isBengali = isBengali,
            onDismiss = { showDiscountsListDialog = false }
        )
    }

    if (showProfitsListDialog) {
        ReportProfitHistoryDialog(
            reportPeriod = reportPeriod,
            todayBills = reportBills,
            medicines = medicines,
            viewModel = viewModel,
            isBengali = isBengali,
            onDismiss = { showProfitsListDialog = false }
        )
    }
}

// --- Detailed Analytics Dialogs for Reports ---

