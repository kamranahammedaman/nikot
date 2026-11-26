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
fun TodaySalesDetailsDialog(
    viewModel: PharmacyViewModel,
    todayBills: List<Bill>,
    isBengali: Boolean,
    onDismiss: () -> Unit
) {
    class MedicineAggr(
        val name: String,
        val type: String,
        var originalBoxes: Int = 0,
        var originalStrips: Int = 0,
        var originalPcs: Int = 0,
        var originalBottles: Int = 0,
        var originalTubes: Int = 0,
        var originalVials: Int = 0,
        var originalOtherQty: Double = 0.0,
        var returnedBoxes: Int = 0,
        var returnedStrips: Int = 0,
        var returnedPcs: Int = 0,
        var returnedBottles: Int = 0,
        var returnedTubes: Int = 0,
        var returnedVials: Int = 0,
        var returnedOtherQty: Double = 0.0,
        var originalPrice: Double = 0.0,
        var returnedPrice: Double = 0.0,
        var countOfSales: Int = 0
    )

    val aggrMap = mutableMapOf<Long, MedicineAggr>()

    todayBills.forEach { bill ->
        val items = viewModel.getBillItemsList(bill)
        val returnedItems = viewModel.getReturnedItemsList(bill)

        items.forEach { item ->
            val aggr = aggrMap.getOrPut(item.medicineId) {
                MedicineAggr(item.name, item.type)
            }
            aggr.countOfSales += 1
            aggr.originalPrice += item.totalPrice
            if (item.type == "tablet") {
                aggr.originalBoxes += item.soldBoxes
                aggr.originalStrips += item.soldStrips
                aggr.originalPcs += item.soldPcs
            } else {
                aggr.originalBottles += item.soldBottles
                aggr.originalTubes += item.soldTubes
                aggr.originalVials += item.soldVials
                aggr.originalOtherQty += item.soldOtherQty
            }
        }

        returnedItems.forEach { item ->
            val aggr = aggrMap.getOrPut(item.medicineId) {
                MedicineAggr(item.name, item.type)
            }
            aggr.returnedPrice += item.totalPrice
            if (item.type == "tablet") {
                aggr.returnedBoxes += item.soldBoxes
                aggr.returnedStrips += item.soldStrips
                aggr.returnedPcs += item.soldPcs
            } else {
                aggr.returnedBottles += item.soldBottles
                aggr.returnedTubes += item.soldTubes
                aggr.returnedVials += item.soldVials
                aggr.returnedOtherQty += item.soldOtherQty
            }
        }
    }

    val soldList = aggrMap.values.map { aggr ->
        val reqBoxes = (aggr.originalBoxes - aggr.returnedBoxes).coerceAtLeast(0)
        val reqStrips = (aggr.originalStrips - aggr.returnedStrips).coerceAtLeast(0)
        val reqPcs = (aggr.originalPcs - aggr.returnedPcs).coerceAtLeast(0)
        val reqBottles = (aggr.originalBottles - aggr.returnedBottles).coerceAtLeast(0)
        val reqTubes = (aggr.originalTubes - aggr.returnedTubes).coerceAtLeast(0)
        val reqVials = (aggr.originalVials - aggr.returnedVials).coerceAtLeast(0)
        val reqOther = (aggr.originalOtherQty - aggr.returnedOtherQty).coerceAtLeast(0.0)

        val parts = mutableListOf<String>()
        if (aggr.type == "tablet") {
            if (reqBoxes > 0) parts.add("${if (isBengali) formatBengaliNumber(reqBoxes) else reqBoxes} ${if (isBengali) "বক্স" else "box"}")
            if (reqStrips > 0) parts.add("${if (isBengali) formatBengaliNumber(reqStrips) else reqStrips} ${if (isBengali) "পাতা" else "strip"}")
            if (reqPcs > 0) parts.add("${if (isBengali) formatBengaliNumber(reqPcs) else reqPcs} ${if (isBengali) "পিস" else "pc"}")
        } else {
            if (reqBottles > 0) parts.add("${if (isBengali) formatBengaliNumber(reqBottles) else reqBottles} ${if (isBengali) "বোতল" else "bottle"}")
            if (reqTubes > 0) parts.add("${if (isBengali) formatBengaliNumber(reqTubes) else reqTubes} ${if (isBengali) "টিউব" else "tube"}")
            if (reqVials > 0) parts.add("${if (isBengali) formatBengaliNumber(reqVials) else reqVials} ${if (isBengali) "ভায়াল" else "vial"}")
            if (reqOther > 0.0) parts.add("${if (isBengali) formatBengaliNumber(String.format("%.1f", reqOther)) else reqOther} Unit")
        }
        val netQtyDesc = if (parts.isEmpty()) (if (isBengali) "০" else "0") else parts.joinToString(" ")
        val netPrice = (aggr.originalPrice - aggr.returnedPrice).coerceAtLeast(0.0)

        SoldItemAggregation(
            name = aggr.name,
            qtyDesc = netQtyDesc,
            price = netPrice,
            countOfSales = aggr.countOfSales
        )
    }.filter { it.price > 0.0 || (it.qtyDesc != "0" && it.qtyDesc != "০") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isBengali) "আজকের বিক্রয় বিবরণ" else "Today's Sales Details",
                        color = DarkTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, "close", tint = DarkTextPrimary)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (soldList.isEmpty()) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            if (isBengali) "আজকে কোনো ওষুধ বিক্রি হয়নি।" else "No item sales today.",
                            color = DarkTextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(soldList) { group ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkBg)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(group.name, color = DarkTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = "${if (isBengali) "মূল পরিমাণ: " else "Sold qty: "}${group.qtyDesc}",
                                            color = DarkTextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Text(
                                        text = "৳${if (isBengali) formatBengaliNumber(String.format("%.2f", group.price)) else String.format("%.2f", group.price)}",
                                        color = DarkAccentGold,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                val totalOriginal = todayBills.sumOf { it.totalAmount }
                val totalReturned = todayBills.sumOf { it.returnedAmount }
                val totalNet = totalOriginal - totalReturned

                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(if (isBengali) "মূল বিক্রয়:" else "Original Sales:", color = DarkTextSecondary, fontSize = 13.sp)
                        Text("৳${if (isBengali) formatBengaliNumber(String.format("%.2f", totalOriginal)) else String.format("%.2f", totalOriginal)}", color = DarkTextPrimary, fontSize = 13.sp)
                    }
                    if (totalReturned > 0.0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (isBengali) "ফেরত টাকা:" else "Refund Amount:", color = DarkAccentRed, fontSize = 13.sp)
                            Text("− ৳${if (isBengali) formatBengaliNumber(String.format("%.2f", totalReturned)) else String.format("%.2f", totalReturned)}", color = DarkAccentRed, fontSize = 13.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Divider(color = Color.White.copy(alpha = 0.08f))
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isBengali) "নেট বিক্রয়:" else "Net Sales:", color = DarkTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("৳${if (isBengali) formatBengaliNumber(String.format("%.2f", totalNet)) else String.format("%.2f", totalNet)}", color = DarkAccentGold, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

data class SoldItemAggregation(
    val name: String,
    val qtyDesc: String,
    val price: Double,
    val countOfSales: Int
)

data class SoldSummaryItem(
    val medicineId: Long,
    val fullName: String,
    val quantityDescription: String,
    val totalSales: Double
)

@Composable
fun TodayBillHistoryDialog(
    viewModel: PharmacyViewModel,
    todayBills: List<Bill>,
    isBengali: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isBengali) "আজকের বিল হিস্টোরি" else "Today's Bill History",
                        color = DarkTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, "close", tint = DarkTextPrimary)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (todayBills.isEmpty()) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            if (isBengali) "আজকে কোনো বিল তৈরি হয়নি।" else "No bills generated today.",
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
                                formatBengaliTime(bill.timestamp)
                            } else {
                                SimpleDateFormat("hh:mm a", Locale.US).format(Date(bill.timestamp))
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
                                    val returnedTimeStr = if (isReturned && bill.returnedTime > 0L) {
                                        val sdf = SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.US)
                                        val formatted = sdf.format(Date(bill.returnedTime))
                                        if (isBengali) formatBengaliNumber(formatted) else formatted
                                    } else {
                                        ""
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = if (isReturned) "${bill.billNumber} — $returnedLabel" else bill.billNumber,
                                                color = if (isReturned) DarkAccentRed else DarkAccentGold,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(timeStr, color = DarkTextSecondary, fontSize = 11.sp)
                                        }
                                        val displayAmt = if (isReturned) bill.totalAmount - bill.returnedAmount else bill.totalAmount
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "৳${if (isBengali) formatBengaliNumber(String.format("%.2f", displayAmt)) else String.format("%.2f", displayAmt)}",
                                                color = if (isReturned) DarkAccentGold else DarkTextPrimary,
                                                fontWeight = FontWeight.Bold
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
                                        if (isReturned) {
                                            items.forEach { originalItem ->
                                                val returnedItem = returnedItems.find { it.medicineId == originalItem.medicineId }
                                                
                                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                                    Text(
                                                        text = if (isBengali) {
                                                            "মূল বিক্রয়: ${originalItem.name} — ${formatItemQuantity(originalItem, true)} — ৳${formatCurrency(originalItem.totalPrice, true)}"
                                                        } else {
                                                            "Original Sale: ${originalItem.name} — ${formatItemQuantity(originalItem, false)} — $${formatCurrency(originalItem.totalPrice, false)}"
                                                        },
                                                        color = DarkTextPrimary,
                                                        fontSize = 13.sp
                                                    )
                                                    if (returnedItem != null) {
                                                        val retQtyText = formatItemQuantity(returnedItem, isBengali)
                                                        if (retQtyText != "0" && retQtyText != "০") {
                                                            Text(
                                                                text = if (isBengali) {
                                                                    "ফেরত: ${originalItem.name} — $retQtyText — ৳${formatCurrency(returnedItem.totalPrice, true)}"
                                                                } else {
                                                                    "Returned: ${originalItem.name} — $retQtyText — $${formatCurrency(returnedItem.totalPrice, false)}"
                                                                },
                                                                color = DarkAccentRed,
                                                                fontSize = 13.sp,
                                                                modifier = Modifier.padding(start = 4.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Divider(color = Color.White.copy(alpha = 0.05f))
                                            Spacer(modifier = Modifier.height(6.dp))
                                            
                                            val netTotal = (bill.totalAmount - bill.returnedAmount).coerceAtLeast(0.0)
                                            if (bill.discount > 0.0) {
                                                val originalTotal = bill.totalAmount + bill.discount
                                                Text(
                                                    text = if (isBengali) "মূল মোট বিক্রয়: ৳${formatCurrency(originalTotal, true)}" else "Original Total Sale: ৳${formatCurrency(originalTotal, false)}",
                                                    color = DarkTextSecondary,
                                                    fontSize = 12.sp
                                                )
                                                Text(
                                                    text = if (isBengali) "ছাড়: ৳${formatCurrency(bill.discount, true)} (বিয়োগ)" else "Discount: ৳${formatCurrency(bill.discount, false)} (less)",
                                                    color = DarkAccentRed,
                                                    fontSize = 12.sp
                                                )
                                                Text(
                                                    text = if (isBengali) "ফেরত মূল্য: ৳${formatCurrency(bill.returnedAmount, true)}" else "Refunded Amount: ৳${formatCurrency(bill.returnedAmount, false)}",
                                                    color = DarkAccentRed,
                                                    fontSize = 12.sp
                                                )
                                            }
                                            Text(
                                                text = if (isBengali) "সমন্বয়কৃত নেট মোট: ৳${formatCurrency(netTotal, true)}" else "Adjusted Net Total: ৳${formatCurrency(netTotal, false)}",
                                                color = DarkAccentGold,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            if (returnedTimeStr.isNotEmpty()) {
                                                Text(
                                                    text = if (isBengali) "ফেরত তারিখ ও সময়: $returnedTimeStr (অটো সেভ)" else "Return Date & Time: $returnedTimeStr (Auto Saved)",
                                                    color = DarkTextSecondary,
                                                    fontSize = 11.sp,
                                                    modifier = Modifier.padding(top = 4.dp)
                                                )
                                            }
                                        } else {
                                            items.forEach { item ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 2.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text("${item.name} (${item.formatDescription})", color = DarkTextSecondary, fontSize = 12.sp)
                                                    Text("৳${if (isBengali) formatBengaliNumber(String.format("%.2f", item.totalPrice)) else String.format("%.2f", item.totalPrice)}", color = DarkTextPrimary, fontSize = 12.sp)
                                                }
                                            }
                                            if (bill.discount > 0.0) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Divider(color = Color.White.copy(alpha = 0.05f))
                                                Spacer(modifier = Modifier.height(4.dp))
                                                val originalTotal = bill.totalAmount + bill.discount
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(if (isBengali) "মোট:" else "Total:", color = DarkTextSecondary, fontSize = 12.sp)
                                                    Text("৳${if (isBengali) formatBengaliNumber(String.format("%.2f", originalTotal)) else String.format("%.2f", originalTotal)}", color = DarkTextSecondary, fontSize = 12.sp)
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(if (isBengali) "ছাড়:" else "Discount:", color = DarkAccentRed, fontSize = 12.sp)
                                                    Text("− ৳${if (isBengali) formatBengaliNumber(String.format("%.2f", bill.discount)) else String.format("%.2f", bill.discount)} ${if (isBengali) "(বিয়োগ)" else "(less)"}", color = DarkAccentRed, fontSize = 12.sp)
                                                }
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(if (isBengali) "নেট মোট:" else "Net Total:", color = DarkAccentGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    Text("৳${if (isBengali) formatBengaliNumber(String.format("%.2f", bill.totalAmount)) else String.format("%.2f", bill.totalAmount)}", color = DarkAccentGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(10.dp))
                                        val context = androidx.compose.ui.platform.LocalContext.current
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End
                                        ) {
                                            Button(
                                                onClick = {
                                                    viewModel.queuePrintJob(bill, isReturned = bill.isReturned)
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        if (isBengali) "প্রিন্ট জব ক্লাউড কিউতে পাঠানো হয়েছে!" else "Print job queued to cloud!",
                                                        android.widget.Toast.LENGTH_SHORT
                                                    ).show()
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = DarkAccentGold, contentColor = DarkBg),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.height(32.dp),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                            ) {
                                                Icon(Icons.Filled.ListAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(if (isBengali) "রিসিপ্ট প্রিন্ট (ওয়াইফাই)" else "Print Receipt (WiFi)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

@Composable
fun RefundWorkflowDialog(
    viewModel: PharmacyViewModel,
    todayBills: List<Bill>,
    isBengali: Boolean,
    onDismiss: () -> Unit
) {
    val medicines by viewModel.allMedicines.collectAsState()
    val allBillsList by viewModel.allBills.collectAsState()

    val todayDDMMYY = remember {
        SimpleDateFormat("ddMMyy", Locale.US).format(Date(System.currentTimeMillis()))
    }
    
    var searchDateQuery by remember { mutableStateOf(todayDDMMYY) }
    var searchSeqQuery by remember { mutableStateOf("") }
    
    val matchedBill = remember(searchDateQuery, searchSeqQuery, allBillsList) {
        if (searchSeqQuery.isBlank()) {
            null
        } else {
            val datePart = searchDateQuery.trim()
            val seqPart = searchSeqQuery.trim().let { s ->
                if (s.isEmpty()) ""
                else if (s.length < 3) s.padStart(3, '0')
                else s
            }
            val targetBillNo = "RO$datePart$seqPart"
            allBillsList.find { it.billNumber.equals(targetBillNo, ignoreCase = true) }
        }
    }

    val returnedBoxesMap = remember { mutableStateMapOf<Long, Int>() }
    val returnedStripsMap = remember { mutableStateMapOf<Long, Int>() }
    val returnedPcsMap = remember { mutableStateMapOf<Long, Int>() }
    val returnedBottlesMap = remember { mutableStateMapOf<Long, Int>() }
    val returnedTubesMap = remember { mutableStateMapOf<Long, Int>() }
    val returnedVialsMap = remember { mutableStateMapOf<Long, Int>() }
    val returnedOtherQtyMap = remember { mutableStateMapOf<Long, Double>() }

    // Print Receipt Popup State after confirming return
    var showRefundReceiptByBill by remember { mutableStateOf<Bill?>(null) }
    var confirmedReturnedItemsList by remember { mutableStateOf<List<BillItem>>(emptyList()) }

    LaunchedEffect(matchedBill) {
        returnedBoxesMap.clear()
        returnedStripsMap.clear()
        returnedPcsMap.clear()
        returnedBottlesMap.clear()
        returnedTubesMap.clear()
        returnedVialsMap.clear()
        returnedOtherQtyMap.clear()
        
        matchedBill?.let { bill ->
            val items = viewModel.getBillItemsList(bill)
            items.forEach { item ->
                // Start return quantity input at [ 0 ] as required!
                returnedBoxesMap[item.medicineId] = 0
                returnedStripsMap[item.medicineId] = 0
                returnedPcsMap[item.medicineId] = 0
                returnedBottlesMap[item.medicineId] = 0
                returnedTubesMap[item.medicineId] = 0
                returnedVialsMap[item.medicineId] = 0
                returnedOtherQtyMap[item.medicineId] = 0.0
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isBengali) "ওষুধ ফেরত ও মূল্য সমন্বয়" else "Return & Refund",
                        color = DarkAccentGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "close",
                            tint = DarkTextPrimary
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = Color.White.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(12.dp))

                // SEARCH BAR - 3 separate fields (RO fixed prefix, auto prefilled DDMMYY date, sequence num)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Field 1: RO (Fixed, can't change)
                    OutlinedTextField(
                        value = "RO",
                        onValueChange = {},
                        enabled = false,
                        label = { Text(if (isBengali) "প্রিফিক্স" else "Prefix", color = DarkTextSecondary, fontSize = 11.sp) },
                        modifier = Modifier.weight(0.7f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledBorderColor = Color.White.copy(alpha = 0.15f),
                            disabledTextColor = Color.White.copy(alpha = 0.6f),
                            disabledLabelColor = DarkTextSecondary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Field 2: তারিখ (DDMMYY, editable, prefilled)
                    OutlinedTextField(
                        value = searchDateQuery,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() } && input.length <= 6) {
                                searchDateQuery = input
                            }
                        },
                        label = { Text(if (isBengali) "তারিখ" else "Date (DDMMYY)", color = DarkAccentGold, fontSize = 11.sp) },
                        modifier = Modifier.weight(1.5f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DarkAccentGold,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedLabelColor = DarkAccentGold,
                            unfocusedLabelColor = DarkTextSecondary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Field 3: বিল নম্বর (001, editable, numeric keyboard, up to 4 digits)
                    OutlinedTextField(
                        value = searchSeqQuery,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() } && input.length <= 4) {
                                searchSeqQuery = input
                            }
                        },
                        placeholder = { Text("003", color = DarkTextSecondary.copy(alpha = 0.5f), fontSize = 13.sp) },
                        label = { Text(if (isBengali) "বিল নম্বর" else "Bill No (001)", color = DarkAccentGold, fontSize = 11.sp) },
                        modifier = Modifier.weight(1.3f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DarkAccentGold,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedLabelColor = DarkAccentGold,
                            unfocusedLabelColor = DarkTextSecondary
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (matchedBill == null) {
                    // When no matching bill is searched, contain ONLY clean instructions, nothing else!
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Filled.ListAlt,
                                contentDescription = "search",
                                tint = DarkTextSecondary.copy(alpha = 0.4f),
                                modifier = Modifier.size(56.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (isBengali) {
                                    if (searchSeqQuery.isBlank()) {
                                        "অনুসন্ধান করতে উপরে তারিখ ও বিল নম্বর লিখুন।"
                                    } else {
                                        "RO${searchDateQuery}${searchSeqQuery.padStart(3, '0')} নম্বরের কোনো বিল পাওয়া যায়নি।"
                                    }
                                } else {
                                    if (searchSeqQuery.isBlank()) {
                                        "Please enter date and bill sequence number above to search."
                                    } else {
                                        "No bill found with number 'RO${searchDateQuery}${searchSeqQuery.padStart(3, '0')}'."
                                    }
                                },
                                color = DarkTextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    val bill = matchedBill
                    val billItems = viewModel.getBillItemsList(bill)
                    
                    // Auto calculate refund amount based on returns
                    val calculatedRefundAmount = billItems.sumOf { item ->
                        val med = medicines.find { m -> m.id == item.medicineId }
                        val retBoxes = returnedBoxesMap[item.medicineId] ?: 0
                        val retStrips = returnedStripsMap[item.medicineId] ?: 0
                        val retPcs = returnedPcsMap[item.medicineId] ?: 0
                        val retBottles = returnedBottlesMap[item.medicineId] ?: 0
                        val retTubes = returnedTubesMap[item.medicineId] ?: 0
                        val retVials = returnedVialsMap[item.medicineId] ?: 0
                        val retOtherQty = returnedOtherQtyMap[item.medicineId] ?: 0.0
                        
                        val calcType = item.type.lowercase().trim()
                        when (calcType) {
                            "tablet" -> {
                                val boxPrice = if (med != null && med.salePriceBox > 0.0) {
                                    med.salePriceBox
                                } else {
                                    if (item.soldBoxes > 0) item.totalPrice / item.soldBoxes else 0.0
                                }
                                
                                val stripPrice = if (med != null && med.salePriceStrip > 0.0) {
                                    med.salePriceStrip
                                } else {
                                    if (item.soldStrips > 0) item.totalPrice / item.soldStrips else {
                                        if (med != null && med.stripsPerBox > 0) med.salePriceBox / med.stripsPerBox else 0.0
                                    }
                                }
                                
                                val piecePrice = if (med != null && med.salePricePiece > 0.0) {
                                    med.salePricePiece
                                } else {
                                    if (item.soldPcs > 0) item.totalPrice / item.soldPcs else {
                                        val tempStripPrice = if (med != null && med.stripsPerBox > 0) med.salePriceBox / med.stripsPerBox else stripPrice
                                        if (med != null && med.pcsPerStrip > 0) tempStripPrice / med.pcsPerStrip else 0.0
                                    }
                                }
                                (retBoxes * boxPrice) + (retStrips * stripPrice) + (retPcs * piecePrice)
                            }
                            "syrup" -> {
                                val pricePerUnit = med?.salePriceUnit ?: (if (item.soldBottles > 0) item.totalPrice / item.soldBottles else 0.0)
                                retBottles * pricePerUnit
                            }
                            "ointment" -> {
                                val pricePerUnit = med?.salePriceUnit ?: (if (item.soldTubes > 0) item.totalPrice / item.soldTubes else 0.0)
                                retTubes * pricePerUnit
                            }
                            "injection" -> {
                                val pricePerUnit = med?.salePriceUnit ?: (if (item.soldVials > 0) item.totalPrice / item.soldVials else 0.0)
                                retVials * pricePerUnit
                            }
                            else -> {
                                val pricePerUnit = med?.salePriceUnit ?: (if (item.soldOtherQty > 0) item.totalPrice / item.soldOtherQty else 0.0)
                                retOtherQty * pricePerUnit
                            }
                        }
                    }

                    // Auto calculate net total (নেট মোট অটো হিসাব)
                    val calculatedNetTotal = (bill.totalAmount - calculatedRefundAmount).coerceAtLeast(0.0)

                    Column(modifier = Modifier.weight(1f)) {
                        // Read Only Bill Number (বিল নম্বর কখনো পরিবর্তন হবে না)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isBengali) "বিল নম্বর (স্থায়ী):" else "Bill No (Static):",
                                color = DarkTextSecondary,
                                fontSize = 12.sp
                            )
                            Text(
                                text = bill.billNumber,
                                color = DarkAccentGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(billItems) { item ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = DarkBg),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        // "মূল বিক্রয়:" format details as requested
                                        val qtyDesc = formatItemQuantity(item, isBengali)
                                        Text(
                                            text = "${if (isBengali) "মূল বিক্রয়: " else "Orig Sale: "}${item.name} — $qtyDesc — ৳${formatCurrency(item.totalPrice, isBengali)}",
                                            color = DarkTextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                        
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = if (isBengali) "ফেরত পরিমাণ input:" else "Return quantity input:",
                                            color = DarkAccentGold,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        
                                        val normItemType = item.type.lowercase().trim()
                                        when (normItemType) {
                                            "tablet" -> {
                                                val med = medicines.find { m -> m.id == item.medicineId }
                                                val stripsPerBox = if (med != null && med.stripsPerBox > 0) med.stripsPerBox else 10
                                                val pcsPerStrip = if (med != null && med.pcsPerStrip > 0) med.pcsPerStrip else 10
                                                val factor = stripsPerBox * pcsPerStrip
                                                
                                                val soldTotalPieces = (item.soldBoxes * factor) + (item.soldStrips * pcsPerStrip) + item.soldPcs
                                                
                                                val currentBox = returnedBoxesMap[item.medicineId] ?: 0
                                                val currentStrip = returnedStripsMap[item.medicineId] ?: 0
                                                val currentPcs = returnedPcsMap[item.medicineId] ?: 0
                                                
                                                val maxBox = (soldTotalPieces - (currentStrip * pcsPerStrip + currentPcs)) / factor
                                                val maxStrip = (soldTotalPieces - (currentBox * factor + currentPcs)) / pcsPerStrip
                                                val maxPcs = soldTotalPieces - (currentBox * factor + currentStrip * pcsPerStrip)

                                                QuantityBoxInputRow(
                                                    label = if (isBengali) "বক্স:" else "Box:",
                                                    quantity = currentBox,
                                                    max = maxBox,
                                                    isBengali = isBengali,
                                                    onChanged = { returnedBoxesMap[item.medicineId] = it }
                                                )
                                                QuantityBoxInputRow(
                                                    label = if (isBengali) "পাতা:" else "Strip:",
                                                    quantity = currentStrip,
                                                    max = maxStrip,
                                                    isBengali = isBengali,
                                                    onChanged = { returnedStripsMap[item.medicineId] = it }
                                                )
                                                QuantityBoxInputRow(
                                                    label = if (isBengali) "পিস:" else "Pcs:",
                                                    quantity = currentPcs,
                                                    max = maxPcs,
                                                    isBengali = isBengali,
                                                    onChanged = { returnedPcsMap[item.medicineId] = it }
                                                )
                                            }
                                            "syrup" -> {
                                                val current = returnedBottlesMap[item.medicineId] ?: 0
                                                QuantityBoxInputRow(
                                                    label = if (isBengali) "বোতল:" else "Bottle:",
                                                    quantity = current,
                                                    max = item.soldBottles,
                                                    isBengali = isBengali,
                                                    onChanged = { returnedBottlesMap[item.medicineId] = it }
                                                )
                                            }
                                            "ointment" -> {
                                                val current = returnedTubesMap[item.medicineId] ?: 0
                                                QuantityBoxInputRow(
                                                    label = if (isBengali) "টিউব/পট:" else "Tube/Pot:",
                                                    quantity = current,
                                                    max = item.soldTubes,
                                                    isBengali = isBengali,
                                                    onChanged = { returnedTubesMap[item.medicineId] = it }
                                                )
                                            }
                                            "injection" -> {
                                                val current = returnedVialsMap[item.medicineId] ?: 0
                                                QuantityBoxInputRow(
                                                    label = if (isBengali) "ভায়াল:" else "Vial:",
                                                    quantity = current,
                                                    max = item.soldVials,
                                                    isBengali = isBengali,
                                                    onChanged = { returnedVialsMap[item.medicineId] = it }
                                                )
                                            }
                                            else -> {
                                                val current = returnedOtherQtyMap[item.medicineId] ?: 0.0
                                                DoubleQuantityBoxInputRow(
                                                    label = if (isBengali) "পরিমাণ:" else "Qty:",
                                                    quantity = current,
                                                    max = item.soldOtherQty,
                                                    isBengali = isBengali,
                                                    onChanged = { returnedOtherQtyMap[item.medicineId] = it }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        // Calculated summary cards
                        Card(
                            colors = CardDefaults.cardColors(containerColor = DarkBg),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                if (bill.discount > 0.0) {
                                    val originalTotal = bill.totalAmount + bill.discount
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isBengali) "মূল মোট:" else "Original Total:",
                                            color = DarkTextSecondary,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "৳${formatCurrency(originalTotal, isBengali)}",
                                            color = DarkTextPrimary,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isBengali) "ছাড়:" else "Discount:",
                                            color = DarkAccentRed,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "৳${formatCurrency(bill.discount, isBengali)} ${if (isBengali) "(বিয়োগ)" else "(less)"}",
                                            color = DarkAccentRed,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isBengali) "বিল বিক্রয় মোট:" else "Bill Sale Total:",
                                        color = DarkTextSecondary,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "৳${formatCurrency(bill.totalAmount, isBengali)}",
                                        color = DarkTextPrimary,
                                        fontSize = 13.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isBengali) "ফেরত টাকা (অটো হিসাব):" else "Refund Amount (Auto):",
                                        color = DarkTextSecondary,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "৳${formatCurrency(calculatedRefundAmount, isBengali)}",
                                        color = DarkAccentRed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isBengali) "নেট মোট (অটো হিসাব):" else "Net Total (Auto):",
                                        color = DarkTextSecondary,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "৳${formatCurrency(calculatedNetTotal, isBengali)}",
                                        color = DarkAccentGold,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Button(
                            onClick = {
                                val returnedBillItemsList = billItems.mapNotNull { item ->
                                    val retBoxes = returnedBoxesMap[item.medicineId] ?: 0
                                    val retStrips = returnedStripsMap[item.medicineId] ?: 0
                                    val retPcs = returnedPcsMap[item.medicineId] ?: 0
                                    val retBottles = returnedBottlesMap[item.medicineId] ?: 0
                                    val retTubes = returnedTubesMap[item.medicineId] ?: 0
                                    val retVials = returnedVialsMap[item.medicineId] ?: 0
                                    val retOtherQty = returnedOtherQtyMap[item.medicineId] ?: 0.0
                                    
                                    val hasRefund = retBoxes > 0 || retStrips > 0 || retPcs > 0 ||
                                            retBottles > 0 || retTubes > 0 || retVials > 0 || retOtherQty > 0.0
                                            
                                    if (hasRefund) {
                                        val parts = mutableListOf<String>()
                                        if (retBoxes > 0) parts.add(if (isBengali) "$retBoxes বক্স" else "$retBoxes Box")
                                        if (retStrips > 0) parts.add(if (isBengali) "$retStrips পাতা" else "$retStrips Strip")
                                        if (retPcs > 0) parts.add(if (isBengali) "$retPcs পিস" else "$retPcs Pcs")
                                        if (retBottles > 0) parts.add(if (isBengali) "$retBottles বোতল" else "$retBottles Bottle")
                                        if (retTubes > 0) parts.add(if (isBengali) "$retTubes টিউব/পট" else "$retTubes Tube/Pot")
                                        if (retVials > 0) parts.add(if (isBengali) "$retVials ভায়াল" else "$retVials Vial")
                                        if (retOtherQty > 0.0) parts.add(if (isBengali) "$retOtherQty কাস্টম পরিমাণ" else "$retOtherQty Custom Qty")
                                        val customDesc = parts.joinToString(" ")
                                        
                                        val med = medicines.find { m -> m.id == item.medicineId }
                                        val calcTypeConfirm = item.type.lowercase().trim()
                                        val calcItemRefundPrice = when (calcTypeConfirm) {
                                            "tablet" -> {
                                                val boxPrice = if (med != null && med.salePriceBox > 0.0) {
                                                    med.salePriceBox
                                                } else {
                                                    if (item.soldBoxes > 0) item.totalPrice / item.soldBoxes else 0.0
                                                }
                                                
                                                val stripPrice = if (med != null && med.salePriceStrip > 0.0) {
                                                    med.salePriceStrip
                                                } else {
                                                    if (item.soldStrips > 0) item.totalPrice / item.soldStrips else {
                                                        if (med != null && med.stripsPerBox > 0) med.salePriceBox / med.stripsPerBox else 0.0
                                                    }
                                                }
                                                
                                                val piecePrice = if (med != null && med.salePricePiece > 0.0) {
                                                    med.salePricePiece
                                                } else {
                                                    if (item.soldPcs > 0) item.totalPrice / item.soldPcs else {
                                                        val tempStripPrice = if (med != null && med.stripsPerBox > 0) med.salePriceBox / med.stripsPerBox else stripPrice
                                                        if (med != null && med.pcsPerStrip > 0) tempStripPrice / med.pcsPerStrip else 0.0
                                                    }
                                                }
                                                (retBoxes * boxPrice) + (retStrips * stripPrice) + (retPcs * piecePrice)
                                            }
                                            "syrup" -> {
                                                val pricePerUnit = med?.salePriceUnit ?: (if (item.soldBottles > 0) item.totalPrice / item.soldBottles else 0.0)
                                                retBottles * pricePerUnit
                                            }
                                            "ointment" -> {
                                                val pricePerUnit = med?.salePriceUnit ?: (if (item.soldTubes > 0) item.totalPrice / item.soldTubes else 0.0)
                                                retTubes * pricePerUnit
                                            }
                                            "injection" -> {
                                                val pricePerUnit = med?.salePriceUnit ?: (if (item.soldVials > 0) item.totalPrice / item.soldVials else 0.0)
                                                retVials * pricePerUnit
                                            }
                                            else -> {
                                                val pricePerUnit = med?.salePriceUnit ?: (if (item.soldOtherQty > 0) item.totalPrice / item.soldOtherQty else 0.0)
                                                retOtherQty * pricePerUnit
                                            }
                                        }
                                        
                                        item.copy(
                                            formatDescription = customDesc,
                                            totalPrice = calcItemRefundPrice,
                                            soldBoxes = retBoxes,
                                            soldStrips = retStrips,
                                            soldPcs = retPcs,
                                            soldBottles = retBottles,
                                            soldTubes = retTubes,
                                            soldVials = retVials,
                                            soldOtherQty = retOtherQty
                                        )
                                    } else {
                                        null
                                    }
                                }
                                
                                if (returnedBillItemsList.isNotEmpty()) {
                                    // Trigger the return in system
                                    viewModel.returnBill(
                                        bill = bill,
                                        returnedItems = returnedBillItemsList,
                                        refundAmount = calculatedRefundAmount
                                    )
                                    // Save state to trigger print dialog!
                                    confirmedReturnedItemsList = returnedBillItemsList
                                    showRefundReceiptByBill = bill.copy(
                                        isReturned = true,
                                        returnedAmount = calculatedRefundAmount,
                                        returnedTime = System.currentTimeMillis()
                                    )
                                } else {
                                    onDismiss()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkAccentRed),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (isBengali) "ফেরত নিশ্চিত করুন" else "Confirm Return",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Spawn printed popup on complete match requirements
    if (showRefundReceiptByBill != null) {
        RefundReceiptDialog(
            viewModel = viewModel,
            bill = showRefundReceiptByBill!!,
            returnedItems = confirmedReturnedItemsList,
            isBengali = isBengali,
            onDismiss = {
                showRefundReceiptByBill = null
                onDismiss() // Dismiss main dialog too
            }
        )
    }
}

@Composable
fun QuantityBoxInputRow(
    label: String,
    quantity: Int,
    max: Int,
    isBengali: Boolean,
    onChanged: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = DarkTextSecondary, fontSize = 13.sp)
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { if (quantity > 0) onChanged(quantity - 1) },
                modifier = Modifier.size(32.dp)
            ) {
                Text("—", color = DarkAccentGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            
            // Custom input display box that mimics [ 0 ]
            Box(
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.03f), shape = RoundedCornerShape(6.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp))
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isBengali) formatBengaliNumber(quantity) else quantity.toString(),
                    color = DarkTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            
            IconButton(
                onClick = { if (quantity < max) onChanged(quantity + 1) },
                modifier = Modifier.size(32.dp)
            ) {
                Text("+", color = DarkAccentGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun DoubleQuantityBoxInputRow(
    label: String,
    quantity: Double,
    max: Double,
    isBengali: Boolean,
    onChanged: (Double) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = DarkTextSecondary, fontSize = 13.sp)
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { if (quantity > 0.0) onChanged((quantity - 0.5).coerceAtLeast(0.0)) },
                modifier = Modifier.size(32.dp)
            ) {
                Text("—", color = DarkAccentGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            
            val doubleStr = String.format(Locale.US, "%.1f", quantity)
            Box(
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.03f), shape = RoundedCornerShape(6.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isBengali) formatBengaliNumber(doubleStr) else doubleStr,
                    color = DarkTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            
            IconButton(
                onClick = { if (quantity < max) onChanged((quantity + 0.5).coerceAtMost(max)) },
                modifier = Modifier.size(32.dp)
            ) {
                Text("+", color = DarkAccentGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun RefundReceiptDialog(
    viewModel: PharmacyViewModel,
    bill: Bill,
    returnedItems: List<BillItem>,
    isBengali: Boolean,
    onDismiss: () -> Unit
) {
    val pharmacyName by viewModel.pharmacyName.collectAsState()
    val address by viewModel.pharmacyAddress.collectAsState()
    val phone by viewModel.pharmacyPhone.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val now = System.currentTimeMillis()
    val dateStr = if (isBengali) {
        "${formatBengaliDate(now)} ${formatBengaliTime(now)}"
    } else {
        SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.US).format(Date(now))
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, DarkAccentGold.copy(alpha = 0.3f)),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    RoLogoHeader()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(pharmacyName, color = DarkTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(address, color = DarkTextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                    Text("Phone: $phone", color = DarkTextSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isBengali) "ওষুধ ফেরত ও মূল্য সমন্বয় রিসিট" else "RETURN & REFUND RECEIPT",
                        color = DarkAccentRed,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Divider(color = Color.LightGray.copy(alpha = 0.15f))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Metadata
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "${if (isBengali) "বিল নম্বর: " else "Bill No: "}${bill.billNumber}",
                        color = DarkTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = dateStr, color = DarkTextSecondary, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = Color.LightGray.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(8.dp))

                // Scrollable List of returned items
                val originalItems = viewModel.getBillItemsList(bill)
                
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(returnedItems) { returnedItem ->
                        val originalItem = originalItems.find { it.medicineId == returnedItem.medicineId }
                        if (originalItem != null) {
                            val originalQtyDesc = formatItemQuantity(originalItem, isBengali)
                            val returnedQtyDesc = formatItemQuantity(returnedItem, isBengali)
                            
                            val originalPrice = originalItem.totalPrice
                            val returnPrice = returnedItem.totalPrice
                            val netPrice = (originalPrice - returnPrice).coerceAtLeast(0.0)

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                            ) {
                                Text(
                                    text = returnedItem.name,
                                    color = DarkTextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                // Format matching requested layout precisely:
                                // মূল: ৫ বক্স ২ পাতা ৩ পিস — ৳XXXX
                                Text(
                                    text = "${if (isBengali) "মূল: " else "Orig: "}$originalQtyDesc — ৳${formatCurrency(originalPrice, isBengali)}",
                                    color = DarkTextSecondary,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                                // ফেরত: ২ বক্স ১ পাতা ২ পিস — ৳XXXX
                                Text(
                                    text = "${if (isBengali) "ফেরত: " else "Ret: "}$returnedQtyDesc — ৳${formatCurrency(returnPrice, isBengali)}",
                                    color = DarkAccentRed,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                                // নেট: ৳XXXX
                                Text(
                                    text = "${if (isBengali) "নেট: " else "Net: "}৳${formatCurrency(netPrice, isBengali)}",
                                    color = DarkAccentGold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Divider(color = Color.White.copy(alpha = 0.05f))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = Color.LightGray.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(8.dp))

                // Refund totals summary
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(if (isBengali) "মোট মূল বিক্রয়:" else "Original Total:", color = DarkTextSecondary, fontSize = 12.sp)
                        Text("৳${formatCurrency(bill.totalAmount, isBengali)}", color = DarkTextPrimary, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(if (isBengali) "মোট ফেরত টাকা:" else "Total Refunded:", color = DarkAccentRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("৳${formatCurrency(bill.returnedAmount, isBengali)}", color = DarkAccentRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(if (isBengali) "সমন্বয়কৃত নেট মোট:" else "Net Paid Total:", color = DarkAccentGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("৳${formatCurrency(bill.totalAmount - bill.returnedAmount, isBengali)}", color = DarkAccentGold, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions: print or dismiss
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .padding(end = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkTextPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (isBengali) "বন্ধ করুন" else "Close")
                    }
                    Button(
                        onClick = {
                            viewModel.queuePrintJob(bill, isReturned = true)
                            android.widget.Toast.makeText(
                                context,
                                if (isBengali) "প্রিন্ট জব কিউতে পাঠানো হয়েছে!" else "Sent to print queue!",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier
                            .weight(1.2f)
                            .height(44.dp)
                            .padding(start = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkAccentGold, contentColor = DarkBg),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.ListAlt, if (isBengali) "প্রিন্ট" else "Print", tint = DarkBg, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isBengali) "প্রিন্ট করুন" else "Print Bill")
                    }
                }
            }
        }
    }
}

@Composable
fun PlainMedicinesListDialog(
    title: String,
    medicinesList: List<Medicine>,
    isBengali: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        color = DarkTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, "close", tint = DarkTextPrimary)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                if (medicinesList.isEmpty()) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            if (isBengali) "কোনো ঔষধ পাওয়া যায়নি।" else "No medicines found.",
                            color = DarkTextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(medicinesList) { m ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = DarkBg)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(m.fullName, color = DarkTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        text = "${if (isBengali) "জেনেরিক: " else "Generic: "}${m.genericName}",
                                        color = DarkTextSecondary,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = "${if (isBengali) "মজুদ: " else "Stock: "}${formatMedicineStock(m, isBengali)}",
                                        color = DarkAccentGold,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
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

// FORMAT UTIL
@Composable
fun OnlineOrderReceiptDialog(
    viewModel: PharmacyViewModel,
    order: CustomerOrder,
    billNumber: String,
    isBengali: Boolean,
    onConfirmed: () -> Unit,
    onDismiss: () -> Unit
) {
    val pharmacyName by viewModel.pharmacyName.collectAsState()
    val address by viewModel.pharmacyAddress.collectAsState()
    val phone by viewModel.pharmacyPhone.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val now = System.currentTimeMillis()
    val dateStr = if (isBengali) {
        "${formatBengaliDate(now)} ${formatBengaliTime(now)}"
    } else {
        java.text.SimpleDateFormat("dd-MM-yyyy hh:mm a", java.util.Locale.US).format(java.util.Date(now))
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, DarkAccentGold.copy(alpha = 0.3f)),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    RoLogoHeader()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(pharmacyName, color = DarkTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(address, color = DarkTextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center)
                    Text("Phone: $phone", color = DarkTextSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isBengali) "অনলাইন অর্ডার বিল রিসিট" else "ONLINE ORDER BILL RECEIPT",
                        color = DarkAccentGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Divider(color = Color.LightGray.copy(alpha = 0.15f))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Metadata
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "${if (isBengali) "বিল নম্বর: " else "Bill No: "}$billNumber",
                        color = DarkTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = dateStr, color = DarkTextSecondary, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        text = "${if (isBengali) "অর্ডারকারী: " else "Customer: "}${order.customerUsername}",
                        color = DarkTextSecondary,
                        fontSize = 11.sp
                    )
                    Text(
                        text = if (isBengali) "অর্ডার ধরণ: অনলাইন অর্ডার" else "Type: Online Order",
                        color = DarkAccentGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = Color.LightGray.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(8.dp))

                // List of ordered item
                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Text(
                                text = order.medicineName,
                                color = DarkTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            val medicine = viewModel.allMedicines.value.find { it.id == order.medicineId }
                            val unitName = if (medicine != null) {
                                when (medicine.type) {
                                    "tablet" -> if (isBengali) "পিস" else "pcs"
                                    "syrup" -> if (isBengali) "বোতল" else "bottles"
                                    "ointment" -> if (isBengali) "টিউব" else "tubes"
                                    "injection" -> if (isBengali) "ভায়াল" else "vials"
                                    else -> medicine.otherUnit
                                }
                            } else {
                                if (isBengali) "টি" else "unit"
                            }

                            val qtyDesc = if (isBengali) {
                                "${formatBengaliNumber(order.quantity)} $unitName"
                            } else {
                                "${order.quantity} $unitName"
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${if (isBengali) "পরিমাণ: " else "Qty: "}$qtyDesc",
                                    color = DarkTextSecondary,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "৳${if (isBengali) formatBengaliNumber(String.format("%.2f", order.totalPrice)) else String.format("%.2f", order.totalPrice)}",
                                    color = DarkTextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Divider(color = Color.White.copy(alpha = 0.05f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = Color.LightGray.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(8.dp))

                // Totals
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(if (isBengali) "মোট বিল পরিমাণ:" else "Total Bill Amount:", color = DarkTextSecondary, fontSize = 12.sp)
                        Text("৳${if (isBengali) formatBengaliNumber(String.format("%.2f", order.totalPrice)) else String.format("%.2f", order.totalPrice)}", color = DarkTextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(if (isBengali) "নেট পরিশোধযোগ্য:" else "Net Payable:", color = DarkAccentGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("৳${if (isBengali) formatBengaliNumber(String.format("%.2f", order.totalPrice)) else String.format("%.2f", order.totalPrice)}", color = DarkAccentGold, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .padding(end = 4.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkTextPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (isBengali) "বন্ধ করুন" else "Close")
                    }
                    Button(
                        onClick = {
                            viewModel.confirmOnlineOrderSale(order, billNumber) {
                                android.widget.Toast.makeText(
                                    context,
                                    if (isBengali) "অনলাইন অর্ডার বিল নিশ্চিত করা হয়েছে ও বিক্রয়ে যোগ হয়েছে!" else "Online Order Bill Confirmed & Added to Sales!",
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                                onConfirmed()
                            }
                        },
                        modifier = Modifier
                            .weight(1.3f)
                            .height(44.dp)
                            .padding(start = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkAccentGold, contentColor = DarkBg),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Filled.ListAlt, if (isBengali) "প্রিন্ট" else "Print", tint = DarkBg, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isBengali) "প্রিন্ট ও বিক্রয় করুন" else "Print & Finalize")
                    }
                }
            }
        }
    }
}

