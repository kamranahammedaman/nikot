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
fun SalesScreen(viewModel: PharmacyViewModel, isBengali: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val medicines by viewModel.allMedicines.collectAsState()
    val cart by viewModel.cart.collectAsState()
    val todayBillList by viewModel.todayBills.collectAsState()
    val totalCartAmount = cart.sumOf { it.totalPrice }

    var salesTabState by remember { mutableStateOf(0) } // 0 = Manual Sale, 1 = Online Orders

    var searchQuery by remember { mutableStateOf("") }
    var selectedMedicineForAdd by remember { mutableStateOf<Medicine?>(null) }
    var showCameraSimulation by remember { mutableStateOf(false) }
    var showPreviewBill by remember { mutableStateOf(false) }

    var selectedOrderForReceipt by remember { mutableStateOf<CustomerOrder?>(null) }
    var orderBillNumber by remember { mutableStateOf("") }

    val filteredMedicines = if (searchQuery.trim().isEmpty()) {
        emptyList()
    } else {
        medicines.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.fullName.contains(searchQuery, ignoreCase = true) ||
            it.genericName.contains(searchQuery, ignoreCase = true) ||
            it.barcode == searchQuery.trim()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        RoLogoHeader()
        Spacer(modifier = Modifier.height(12.dp))

        // Dual-Tab selection row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .background(DarkSurface, RoundedCornerShape(10.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = { salesTabState = 0 },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (salesTabState == 0) DarkAccentRed else Color.Transparent,
                    contentColor = if (salesTabState == 0) DarkAccentText else DarkTextSecondary
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Icon(Icons.Filled.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isBengali) "ম্যানুয়াল বিক্রয়" else "Manual Sale",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            val pendingOrdersCount = remember(viewModel.allCustomerOrders.collectAsState().value) {
                viewModel.allCustomerOrders.value.filter { it.status == "Pending" }.size
            }
            
            Button(
                onClick = { salesTabState = 1 },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (salesTabState == 1) DarkAccentRed else Color.Transparent,
                    contentColor = if (salesTabState == 1) DarkAccentText else DarkTextSecondary
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isBengali) "অনলাইন অর্ডার (${formatBengaliNumber(pendingOrdersCount)})" else "Online Orders ($pendingOrdersCount)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (salesTabState == 0) {
            // --- TAB 0: MANUAL SALE ---
            // Search + Scanner trigger row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(if (isBengali) "ওষুধ খুঁজুন (নাম/জেনেরিক/বারকোড)" else "Search Medicine (Name/Generic/Barcode)") },
                    modifier = Modifier.weight(1f),
                    textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DarkAccentRed,
                        unfocusedBorderColor = DarkTextSecondary,
                        focusedLabelColor = DarkAccentRed,
                        unfocusedLabelColor = DarkTextSecondary
                    ),
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Close, "clear", tint = DarkTextPrimary)
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { showCameraSimulation = true },
                    modifier = Modifier
                        .size(54.dp)
                        .background(DarkSurface, RoundedCornerShape(12.dp))
                        .border(1.dp, DarkAccentRed.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Filled.CameraAlt, "scan barcode", tint = DarkAccentRed)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Query results OR Cart header
            Box(modifier = Modifier.weight(1f)) {
                if (filteredMedicines.isNotEmpty()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(if (isBengali) "অনুসন্ধানের ফলাফল:" else "Search Results:", color = DarkTextSecondary, fontSize = 12.sp)
                            Text(if (isBengali) "ফলাফল বন্ধ করতে মুছে দিন" else "Clear to close matches", color = DarkAccentGold, fontSize = 11.sp, modifier = Modifier.clickable { searchQuery = "" })
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyColumn {
                            items(filteredMedicines) { med ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable {
                                            val isOutOfStock = when (med.type.lowercase().trim()) {
                                                "tablet" -> med.totalPiecesInStock <= 0
                                                "syrup" -> med.bottlesInStock <= 0
                                                "ointment" -> med.tubesInStock <= 0
                                                "injection" -> med.vialsInStock <= 0
                                                else -> med.otherQuantity <= 0.0
                                            }
                                            if (isOutOfStock) {
                                                android.widget.Toast.makeText(
                                                    context,
                                                    if (isBengali) "স্টক নেই, বিক্রয় সম্ভব নয়" else "Out of stock, sale not possible",
                                                    android.widget.Toast.LENGTH_LONG
                                                ).show()
                                            } else {
                                                selectedMedicineForAdd = med
                                            }
                                        },
                                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(med.fullName, color = DarkTextPrimary, fontWeight = FontWeight.Bold)
                                            Text("${med.genericName} | ${med.company}", color = DarkTextSecondary, fontSize = 11.sp)
                                            Text(
                                                text = "${if (isBengali) "মজুদ: " else "Stock: "}${formatMedicineStock(med, isBengali)}",
                                                color = DarkAccentGold,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Icon(Icons.Filled.Add, "add", tint = DarkAccentRed)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Cart overview List
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(if (isBengali) "বর্তমান কার্ট তালিকা:" else "Current Cart Items:", color = DarkTextSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                        if (cart.isEmpty()) {
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.ShoppingCart, "cart empty", tint = DarkTextSecondary, modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        if (isBengali) "কার্ট বর্তমানে খালি রয়েছে।\nওষুধ যুক্ত করার জন্য উপরে খুঁজুন।" else "Your cart is empty.\nSearch medicine above to add into cart.",
                                        color = DarkTextSecondary,
                                        textAlign = TextAlign.Center,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        } else {
                            LazyColumn(modifier = Modifier.weight(1f)) {
                                items(cart) { item ->
                                    val desc = when (item.medicine.type) {
                                        "tablet" -> {
                                            val parts = mutableListOf<String>()
                                            if (item.soldBoxes > 0) parts.add("${item.soldBoxes} বক্স")
                                            if (item.soldStrips > 0) parts.add("${item.soldStrips} পাতা")
                                            if (item.soldPcs > 0) parts.add("${item.soldPcs} পিস")
                                            parts.joinToString(" ")
                                        }
                                        "syrup" -> "${item.soldBottles} বোতল"
                                        "ointment" -> "${item.soldTubes} টিউব"
                                        "injection" -> "${item.soldVials} ভায়াল"
                                        else -> "${item.soldOtherQty} ${item.medicine.otherUnit}"
                                    }

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(containerColor = DarkSurface)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(item.medicine.fullName, color = DarkTextPrimary, fontWeight = FontWeight.Bold)
                                                Text(
                                                    text = if (isBengali) "পরিমাণ: $desc" else "Qty: $desc",
                                                    color = DarkTextSecondary,
                                                    fontSize = 12.sp
                                                )
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = "৳${if (isBengali) formatBengaliNumber(String.format("%.2f", item.totalPrice)) else String.format("%.2f", item.totalPrice)}",
                                                    color = DarkAccentGold,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(end = 8.dp)
                                                )
                                                IconButton(onClick = { viewModel.removeFromCart(item.medicine.id) }) {
                                                    Icon(Icons.Filled.Delete, "delete", tint = DarkAccentRed)
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

            Spacer(modifier = Modifier.height(12.dp))

            // Total pricing count panel at bottom
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (isBengali) "সর্বমোট টাকা:" else "Total Amount:", color = DarkTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "৳${if (isBengali) formatBengaliNumber(String.format("%.2f", totalCartAmount)) else String.format("%.2f", totalCartAmount)}",
                    color = DarkAccentGold,
                    fontWeight = FontWeight.Black,
                    fontSize = 22.sp
                )
            }

            Button(
                onClick = { if (cart.isNotEmpty()) showPreviewBill = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DarkAccentRed, contentColor = DarkAccentText),
                enabled = cart.isNotEmpty()
            ) {
                Icon(Icons.Filled.ListAlt, "bill preview", tint = DarkAccentText)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isBengali) "বিল প্রিন্ট করুন" else "Print Bill Preview", fontWeight = FontWeight.Bold, color = DarkAccentText)
            }
        } else {
            // --- TAB 1: ONLINE ORDERS ---
            val pendingOrders = viewModel.allCustomerOrders.collectAsState().value.filter { it.status == "Pending" }
            if (pendingOrders.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.CloudDownload, "no orders", tint = DarkTextSecondary.copy(alpha = 0.5f), modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isBengali) "বর্তমানে কোন পেন্ডিং অনলাইন অর্ডার নেই!" else "No pending online orders right now!",
                            color = DarkTextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(pendingOrders) { ord ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = DarkSurface),
                            border = BorderStroke(1.dp, SleekBorderColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "${if (isBengali) "অর্ডারকারী: " else "Customer: "}${ord.customerUsername}",
                                        color = DarkTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    val dateFmt = if (isBengali) formatBengaliDate(ord.timestamp) else java.text.SimpleDateFormat("dd-MM-yyyy", java.util.Locale.US).format(java.util.Date(ord.timestamp))
                                    Text(
                                        text = dateFmt,
                                        color = DarkTextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = ord.medicineName,
                                    color = DarkAccentGold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val medicine = viewModel.allMedicines.value.find { it.id == ord.medicineId }
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
                                    "${formatBengaliNumber(ord.quantity)} $unitName"
                                } else {
                                    "${ord.quantity} $unitName"
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        text = "${if (isBengali) "পরিমাণ: " else "Quantity: "}$qtyDesc",
                                        color = DarkTextSecondary,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "৳${if (isBengali) formatBengaliNumber(String.format("%.1f", ord.totalPrice)) else String.format("%.1f", ord.totalPrice)}",
                                        color = DarkTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Divider(color = Color.White.copy(alpha = 0.05f))
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Button(
                                        onClick = {
                                            viewModel.cancelCustomerOrder(ord) {
                                                android.widget.Toast.makeText(
                                                    context,
                                                    if (isBengali) "অর্ডার বাতিল করা হয়েছে!" else "Order Cancelled!",
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = DarkAccentRed.copy(alpha = 0.1f), contentColor = DarkAccentRed),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(36.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                    ) {
                                        Text(if (isBengali) "অর্ডার বাতিল" else "Cancel Order", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                    
                                    Button(
                                        onClick = {
                                            selectedOrderForReceipt = ord
                                            orderBillNumber = "BILL-ON-${System.currentTimeMillis() % 1000000}"
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SuccessColor, contentColor = Color.White),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(36.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                    ) {
                                        Icon(Icons.Filled.ListAlt, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (isBengali) "রিসিভ ও বিল প্রিন্ট" else "Confirm & Print Bill", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Real camera barcode scanner (CameraX + ML Kit)
    if (showCameraSimulation) {
        BarcodeScannerDialog(medicines, isBengali, onSelectBarcode = { code ->
            searchQuery = code
            showCameraSimulation = false
        }, onDismiss = { showCameraSimulation = false })
    }

    // Medicine Cart adder dialog based on selected medicine type
    if (selectedMedicineForAdd != null) {
        val medicineToConfigure = selectedMedicineForAdd!!
        CartQuantityInputDialog(
            medicine = medicineToConfigure,
            isBengali = isBengali,
            onConfirm = { boxes, strips, pcs, bottles, tubes, vials, otherQty ->
                viewModel.addToCart(medicineToConfigure, boxes, strips, pcs, bottles, tubes, vials, otherQty)
                selectedMedicineForAdd = null
                searchQuery = "" // clear search to focus back on list
            },
            onDismiss = { selectedMedicineForAdd = null }
        )
    }

    // Final preview bill drawer dialog
    if (showPreviewBill) {
        val generatedBillNo = viewModel.generateNextBillNumber(todayBillList)
        BillPreviewDialog(
            viewModel = viewModel,
            billNumber = generatedBillNo,
            cartItems = cart,
            totalPrice = totalCartAmount,
            isBengali = isBengali,
            onConfirmed = {
                showPreviewBill = false
            },
            onDismiss = { showPreviewBill = false }
        )
    }

    // Online Order receipt drawer dialog
    if (selectedOrderForReceipt != null) {
        OnlineOrderReceiptDialog(
            viewModel = viewModel,
            order = selectedOrderForReceipt!!,
            billNumber = orderBillNumber,
            isBengali = isBengali,
            onConfirmed = {
                selectedOrderForReceipt = null
            },
            onDismiss = {
                selectedOrderForReceipt = null
            }
        )
    }
}

/**
 * Real barcode/QR scanner: CameraX preview + an ML Kit on-device analyzer, with a
 * glass-styled viewfinder overlay. Falls back to a manual-entry glass sheet when the
 * camera permission isn't granted. Replaces the old canvas-tap simulation.
 */
@OptIn(ExperimentalPermissionsApi::class)
@SuppressLint("MissingPermission")
