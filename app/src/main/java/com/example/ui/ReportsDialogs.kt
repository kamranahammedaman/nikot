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
fun ReportSalesDetailDialog(
    reportPeriod: String,
    todayBills: List<Bill>,
    viewModel: PharmacyViewModel,
    isBengali: Boolean,
    onDismiss: () -> Unit
) {
    val soldSummary = remember(todayBills) {
        val qtyBoxMap = mutableMapOf<Long, Int>()
        val qtyStripMap = mutableMapOf<Long, Int>()
        val qtyPcsMap = mutableMapOf<Long, Int>()
        val qtyUnitMap = mutableMapOf<Long, Double>()
        val typeMap = mutableMapOf<Long, String>()
        val fullNameMap = mutableMapOf<Long, String>()
        val priceMap = mutableMapOf<Long, Double>()

        todayBills.forEach { bill ->
            val originalItems = viewModel.getBillItemsList(bill)
            originalItems.forEach { item ->
                val id = item.medicineId
                fullNameMap[id] = item.name
                typeMap[id] = item.type
                priceMap[id] = (priceMap[id] ?: 0.0) + item.totalPrice
                if (item.type == "tablet") {
                    qtyBoxMap[id] = (qtyBoxMap[id] ?: 0) + item.soldBoxes
                    qtyStripMap[id] = (qtyStripMap[id] ?: 0) + item.soldStrips
                    qtyPcsMap[id] = (qtyPcsMap[id] ?: 0) + item.soldPcs
                } else {
                    val qty = item.soldBottles + item.soldTubes + item.soldVials + item.soldOtherQty
                    qtyUnitMap[id] = (qtyUnitMap[id] ?: 0.0) + qty
                }
            }

            val returnedItems = viewModel.getReturnedItemsList(bill)
            returnedItems.forEach { item ->
                val id = item.medicineId
                val currentPrice = priceMap[id] ?: 0.0
                priceMap[id] = (currentPrice - item.totalPrice).coerceAtLeast(0.0)
                if (item.type == "tablet") {
                    qtyBoxMap[id] = ((qtyBoxMap[id] ?: 0) - item.soldBoxes).coerceAtLeast(0)
                    qtyStripMap[id] = ((qtyStripMap[id] ?: 0) - item.soldStrips).coerceAtLeast(0)
                    qtyPcsMap[id] = ((qtyPcsMap[id] ?: 0) - item.soldPcs).coerceAtLeast(0)
                } else {
                    val qty = item.soldBottles + item.soldTubes + item.soldVials + item.soldOtherQty
                    qtyUnitMap[id] = ((qtyUnitMap[id] ?: 0.0) - qty).coerceAtLeast(0.0)
                }
            }
        }

        fullNameMap.map { (id, name) ->
            val t = typeMap[id] ?: "other"
            val qtyStr = if (t == "tablet") {
                val b = qtyBoxMap[id] ?: 0
                val s = qtyStripMap[id] ?: 0
                val p = qtyPcsMap[id] ?: 0
                val parts = mutableListOf<String>()
                if (b > 0) parts.add(if (isBengali) "$b বক্স" else "$b Box")
                if (s > 0) parts.add(if (isBengali) "$s পাতা" else "$s Strip")
                if (p > 0) parts.add(if (isBengali) "$p পিস" else "$p Pcs")
                if (parts.isEmpty()) (if (isBengali) "০ পিস" else "0 Pcs") else parts.joinToString(" ")
            } else {
                val q = qtyUnitMap[id] ?: 0.0
                val formattedVal = if (q % 1.0 == 0.0) q.toInt().toString() else String.format("%.1f", q)
                val localizedVal = if (isBengali) formatBengaliNumber(formattedVal) else formattedVal
                val unitName = when (t) {
                    "syrup" -> if (isBengali) "বোতল" else "Bottle"
                    "ointment" -> if (isBengali) "টিউব" else "Tube"
                    "injection" -> if (isBengali) "ভায়াল" else "Vial"
                    else -> if (isBengali) "পিস" else "Pcs"
                }
                "$localizedVal $unitName"
            }
            SoldSummaryItem(
                medicineId = id,
                fullName = name,
                quantityDescription = qtyStr,
                totalSales = priceMap[id] ?: 0.0
            )
        }.filter { it.totalSales > 0.0 || !it.quantityDescription.contains("০") }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isBengali) "বিক্রিত ওষুধের তালিকা" else "Sold Medicines List",
                            color = DarkAccentGold,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isBengali) "ফিল্টার: ${getPeriodLabelBangali(reportPeriod)}" else "Period: ${getPeriodLabelEnglish(reportPeriod)}",
                            color = DarkTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, "close", tint = DarkTextPrimary)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                if (soldSummary.isEmpty()) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            if (isBengali) "এই সময়ে কোনো ওষুধ বিক্রি হয়নি।" else "No medicines sold in this period.",
                            color = DarkTextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(soldSummary) { sItem ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkBg)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(0.7f)) {
                                        Text(sItem.fullName, color = DarkTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (isBengali) "মোট পরিমাণ: ${sItem.quantityDescription}" else "Qty: ${sItem.quantityDescription}",
                                            color = DarkTextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Text(
                                        text = "৳${formatCurrency(sItem.totalSales, isBengali)}",
                                        color = DarkAccentGold,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
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

@Composable
fun ReportBillsHistoryDialog(
    reportPeriod: String,
    todayBills: List<Bill>,
    viewModel: PharmacyViewModel,
    isBengali: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isBengali) "সব বিলের তালিকা" else "All Invoices List",
                            color = DarkAccentGold,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isBengali) "ফিল্টার: ${getPeriodLabelBangali(reportPeriod)}" else "Period: ${getPeriodLabelEnglish(reportPeriod)}",
                            color = DarkTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, "close", tint = DarkTextPrimary)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                if (todayBills.isEmpty()) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            if (isBengali) "এই সময়ে কোনো বিল তৈরি করা হয়নি।" else "No bills generated in this period.",
                            color = DarkTextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(todayBills) { bill ->
                            var isExpanded by remember { mutableStateOf(false) }
                            val items = viewModel.getBillItemsList(bill)
                            val timeStr = if (isBengali) {
                                val datePart = formatBengaliDate(bill.timestamp)
                                val timePart = formatBengaliTime(bill.timestamp)
                                "$datePart, $timePart"
                            } else {
                                SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.US).format(Date(bill.timestamp))
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { isExpanded = !isExpanded },
                                colors = CardDefaults.cardColors(containerColor = DarkBg)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    val isReturned = bill.isReturned
                                    val returnedItems = if (isReturned) viewModel.getReturnedItemsList(bill) else emptyList()
                                    val isFullyReturned = bill.returnedAmount >= bill.totalAmount
                                    val returnedLabel = if (isBengali) {
                                        if (isFullyReturned) "পূর্ণ ফেরত" else "আংশিক ফেরত"
                                    } else {
                                        if (isFullyReturned) "Fully Returned" else "Partial Return"
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(0.6f)) {
                                            Text(
                                                text = if (isReturned) "${bill.billNumber} — $returnedLabel" else bill.billNumber,
                                                color = if (isReturned) DarkAccentRed else DarkAccentGold,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(timeStr, color = DarkTextSecondary, fontSize = 11.sp)
                                        }
                                        val displayAmt = if (isReturned) bill.totalAmount - bill.returnedAmount else bill.totalAmount
                                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(0.4f)) {
                                            Text(
                                                text = "৳${formatCurrency(displayAmt, isBengali)}",
                                                color = if (isReturned) DarkAccentGold else DarkTextPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            if (isReturned) {
                                                Text(
                                                    text = if (isBengali) "সমন্বয়কৃত" else "Adjusted",
                                                    color = DarkAccentGold,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                    if (isExpanded) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Divider(color = Color.LightGray.copy(alpha = 0.1f))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        items.forEach { item ->
                                            val returnedItem = returnedItems.find { it.medicineId == item.medicineId }
                                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                                Text(
                                                    text = "${item.name} — ${item.formatDescription} — ৳${formatCurrency(item.totalPrice, isBengali)}",
                                                    color = DarkTextPrimary,
                                                    fontSize = 13.sp
                                                )
                                                if (returnedItem != null) {
                                                    val retQtyText = formatItemQuantity(returnedItem, isBengali)
                                                    if (retQtyText != "0" && retQtyText != "০") {
                                                        Text(
                                                            text = if (isBengali) {
                                                                "ফেরত: $retQtyText — ৳${formatCurrency(returnedItem.totalPrice, true)}"
                                                            } else {
                                                                "Returned: $retQtyText — $${formatCurrency(returnedItem.totalPrice, false)}"
                                                            },
                                                            color = DarkAccentRed,
                                                            fontSize = 11.sp,
                                                            modifier = Modifier.padding(start = 8.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        if (bill.discount > 0.0) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Divider(color = Color.White.copy(alpha = 0.05f))
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = if (isBengali) "ছাড়: ৳${formatCurrency(bill.discount, true)}" else "Discount: ৳${formatCurrency(bill.discount, false)}",
                                                color = DarkAccentRed,
                                                fontSize = 12.sp,
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
        }
    }
}

@Composable
fun ReportDiscountHistoryDialog(
    reportPeriod: String,
    todayBills: List<Bill>,
    viewModel: PharmacyViewModel,
    isBengali: Boolean,
    onDismiss: () -> Unit
) {
    val billsWithDiscount = remember(todayBills) {
        todayBills.filter { it.discount > 0.0 }
    }
    
    val totalDiscountCalculated = remember(billsWithDiscount) {
        billsWithDiscount.sumOf { it.discount }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isBengali) "প্রদত্ত ছাড়ের বিবরণ" else "Discounts History",
                            color = DarkAccentGold,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isBengali) "ফিল্টার: ${getPeriodLabelBangali(reportPeriod)}" else "Period: ${getPeriodLabelEnglish(reportPeriod)}",
                            color = DarkTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, "close", tint = DarkTextPrimary)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkBg),
                    border = BorderStroke(1.dp, DarkAccentRed.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isBengali) "সর্বমোট ছাড় দেওয়া হয়েছে:" else "Total Discount applied:",
                            color = DarkTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "৳${formatCurrency(totalDiscountCalculated, isBengali)}",
                            color = DarkAccentRed,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (billsWithDiscount.isEmpty()) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            if (isBengali) "এই সময়ে প্রদত্ত কোনো ছাড়ের তথ্য নেই।" else "No discounts offered in this period.",
                            color = DarkTextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(billsWithDiscount) { bill ->
                            val timeStr = if (isBengali) {
                                val datePart = formatBengaliDate(bill.timestamp)
                                val timePart = formatBengaliTime(bill.timestamp)
                                "$datePart, $timePart"
                            } else {
                                SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.US).format(Date(bill.timestamp))
                            }
                            
                            val originalAmt = bill.totalAmount + bill.discount - bill.returnedAmount

                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkBg)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column {
                                            Text(
                                                text = bill.billNumber,
                                                color = DarkAccentGold,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(timeStr, color = DarkTextSecondary, fontSize = 11.sp)
                                        }
                                        Text(
                                            text = "৳${formatCurrency(bill.discount, isBengali)}",
                                            color = DarkAccentRed,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (isBengali) "মূল পরিমাণ (ছাড় ছাড়া):" else "Original Amount (w/o discount):",
                                            color = DarkTextSecondary,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "৳${formatCurrency(originalAmt, isBengali)}",
                                            color = DarkTextPrimary,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (isBengali) "বিল পরিশোধিত মোট:" else "Net Invoice Total Paid:",
                                            color = DarkTextSecondary,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "৳${formatCurrency(bill.totalAmount - bill.returnedAmount, isBengali)}",
                                            color = DarkAccentGold,
                                            fontSize = 12.sp,
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
    }
}

@Composable
fun ReportProfitHistoryDialog(
    reportPeriod: String,
    todayBills: List<Bill>,
    medicines: List<Medicine>,
    viewModel: PharmacyViewModel,
    isBengali: Boolean,
    onDismiss: () -> Unit
) {
    data class BillProfitDetails(
        val bill: Bill,
        val netRevenue: Double,
        val originalCost: Double,
        val returnedCost: Double,
        val effectiveCost: Double,
        val profit: Double,
        val timeStr: String
    )

    val billProfitsList = remember(todayBills, medicines) {
        todayBills.map { bill ->
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
            val profit = netRevenue - effectiveCost
            
            val timeString = if (isBengali) {
                val datePart = formatBengaliDate(bill.timestamp)
                val timePart = formatBengaliTime(bill.timestamp)
                "$datePart, $timePart"
            } else {
                SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.US).format(Date(bill.timestamp))
            }
            
            BillProfitDetails(
                bill = bill,
                netRevenue = netRevenue,
                originalCost = originalCost,
                returnedCost = returnedCost,
                effectiveCost = effectiveCost,
                profit = profit,
                timeStr = timeString
            )
        }
    }

    val totalProfitCalculated = remember(billProfitsList) {
        billProfitsList.sumOf { it.profit }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isBengali) "আজকের লাভ বিবরণী" else "Profits History Analysis",
                            color = DarkAccentGold,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isBengali) "ফিল্টার: ${getPeriodLabelBangali(reportPeriod)}" else "Period: ${getPeriodLabelEnglish(reportPeriod)}",
                            color = DarkTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, "close", tint = DarkTextPrimary)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkBg),
                    border = BorderStroke(1.dp, if (totalProfitCalculated >= 0) SuccessColor.copy(alpha = 0.3f) else DarkAccentRed.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isBengali) "মোট নেট লাভ:" else "Total Net Profit:",
                            color = DarkTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "৳${formatCurrency(totalProfitCalculated, isBengali)}",
                            color = if (totalProfitCalculated >= 0) SuccessColor else DarkAccentRed,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                if (billProfitsList.isEmpty()) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            if (isBengali) "এই সময়ে কোনো বিক্রয়ের তথ্য নেই।" else "No transactions in this period.",
                            color = DarkTextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(billProfitsList) { item ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkBg)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column {
                                            Text(
                                                text = item.bill.billNumber,
                                                color = DarkAccentGold,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(item.timeStr, color = DarkTextSecondary, fontSize = 11.sp)
                                        }
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "৳${formatCurrency(item.profit, isBengali)}",
                                                color = if (item.profit >= 0) SuccessColor else DarkAccentRed,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                text = if (item.profit >= 0) (if (isBengali) "লাভ" else "Profit") else (if (isBengali) "ক্ষতি" else "Loss"),
                                                color = if (item.profit >= 0) SuccessColor else DarkAccentRed,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Divider(color = Color.White.copy(alpha = 0.05f))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (isBengali) "বিক্রয় মূল্য (রাজস্ব):" else "Revenue (Sales):",
                                            color = DarkTextSecondary,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "৳${formatCurrency(item.netRevenue, isBengali)}",
                                            color = DarkTextPrimary,
                                            fontSize = 12.sp
                                        )
                                    }
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (isBengali) "ক্রয় মূল্য (খরচ):" else "Cost of Purchase:",
                                            color = DarkTextSecondary,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = "৳${formatCurrency(item.effectiveCost, isBengali)}",
                                            color = DarkTextPrimary,
                                            fontSize = 12.sp
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

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
