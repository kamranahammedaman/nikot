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
fun InventoryScreen(viewModel: PharmacyViewModel, isBengali: Boolean) {
    val medicines by viewModel.allMedicines.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    var searchQuery by remember { mutableStateOf("") }
    var showAddMedicineForm by remember { mutableStateOf(false) }
    var scaffoldAddBarcode by remember { mutableStateOf("") }
    var showAddOptionsDialog by remember { mutableStateOf(false) }
    var showCameraSimulationForAdd by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("name") } // "name", "stock", "expiry"

    // quick actions
    var medForQuickStockUpdate by remember { mutableStateOf<Medicine?>(null) }
    var medForQuickPriceUpdate by remember { mutableStateOf<Medicine?>(null) }
    var medForDeleteConfirmation by remember { mutableStateOf<Medicine?>(null) }

    var stockStatusTab by remember { mutableStateOf(0) } // 0: Available Stock, 1: Out of Stock (Zero)

    val filteredList = medicines.filter {
        val matchesSearch = it.name.contains(searchQuery, ignoreCase = true) ||
            it.fullName.contains(searchQuery, ignoreCase = true) ||
            it.genericName.contains(searchQuery, ignoreCase = true) ||
            it.barcode == searchQuery.trim()

        val isOutOfStock = when (it.type) {
            "tablet" -> it.totalPiecesInStock <= 0
            "syrup" -> it.bottlesInStock <= 0
            "ointment" -> it.tubesInStock <= 0
            "injection" -> it.vialsInStock <= 0
            else -> it.otherQuantity <= 0.0
        }

        val matchesTab = if (stockStatusTab == 0) !isOutOfStock else isOutOfStock

        matchesSearch && matchesTab
    }.sortedWith { a, b ->
        when (selectedFilter) {
            "stock" -> a.totalPiecesInStock.compareTo(b.totalPiecesInStock)
            "expiry" -> {
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                    val ad = sdf.parse(a.expiryDate)?.time ?: Long.MAX_VALUE
                    val bd = sdf.parse(b.expiryDate)?.time ?: Long.MAX_VALUE
                    ad.compareTo(bd)
                } catch (_: Exception) {
                    0
                }
            }
            "company" -> a.company.compareTo(b.company, ignoreCase = true)
            else -> a.name.compareTo(b.name, ignoreCase = true) // name A-Z
        }
    }

    val groupedMedicines = remember(filteredList) {
        filteredList.groupBy { it.name.trim() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            RoLogoHeader()
            Spacer(modifier = Modifier.height(12.dp))

            // Inventory search + Camera button next to it
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text(if (isBengali) "ওষুধ খুঁজুন..." else "Find medicine...") },
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
                    onClick = { showCameraSimulationForAdd = true },
                    modifier = Modifier
                        .size(54.dp)
                        .background(DarkSurface, RoundedCornerShape(12.dp))
                        .border(1.dp, DarkAccentRed.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                ) {
                    Icon(Icons.Filled.CameraAlt, "scan barcode", tint = DarkAccentRed)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

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

        // Multi Filter Selection Options Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (isBengali) "ফিল্টার:" else "Filters:", color = DarkTextSecondary, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterTabButton(
                    label = if (isBengali) "নাম (A → Z)" else "Name (A → Z)",
                    selected = selectedFilter == "name",
                    onClick = { selectedFilter = "name" }
                )
                FilterTabButton(
                    label = if (isBengali) "স্টক (কম → বেশি)" else "Stock (Low → High)",
                    selected = selectedFilter == "stock",
                    onClick = { selectedFilter = "stock" }
                )
                FilterTabButton(
                    label = if (isBengali) "মেয়াদ (নিকট → দূর)" else "Expiry (Soon → Far)",
                    selected = selectedFilter == "expiry",
                    onClick = { selectedFilter = "expiry" }
                )
                FilterTabButton(
                    label = if (isBengali) "কোম্পানি (A → Z)" else "Company (A → Z)",
                    selected = selectedFilter == "company",
                    onClick = { selectedFilter = "company" }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Medicine Card Grid Lists
        Box(modifier = Modifier.weight(1f)) {
            if (filteredList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (isBengali) "ইনভেন্টরিতে কোনো ওষুধ নেই!\nওষুধ যুক্ত করতে '+' বাটন ট্যাপ করুন।" else "Inventory is empty!\nTap '+' to register dynamic items.",
                        color = DarkTextSecondary,
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    groupedMedicines.forEach { (brandName, medicinesInGroup) ->
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = SleekBorderColor.copy(alpha = 0.3f)),
                                border = BorderStroke(1.dp, SleekBorderColor),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = brandName,
                                        color = DarkAccentGold,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    val countStr = if (isBengali) "${formatBengaliNumber(medicinesInGroup.size)} টি ধরন" else "${medicinesInGroup.size} variant(s)"
                                    Text(
                                        text = countStr,
                                        color = DarkTextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                        items(medicinesInGroup) { med ->
                            MedicineCardItem(
                                med = med,
                                isBengali = isBengali,
                                isAdmin = viewModel.isMerchant(currentUser),
                                onUpdateStock = { medForQuickStockUpdate = med },
                                onChangePrice = { medForQuickPriceUpdate = med },
                                onDelete = { medForDeleteConfirmation = med }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCameraSimulationForAdd) {
        BarcodeScannerDialog(
            allMedicines = medicines,
            isBengali = isBengali,
            onSelectBarcode = { code ->
                scaffoldAddBarcode = code
                showCameraSimulationForAdd = false
                showAddMedicineForm = true
            },
            onDismiss = { showCameraSimulationForAdd = false }
        )
    }

    FloatingActionButton(
        onClick = {
            scaffoldAddBarcode = ""
            showAddMedicineForm = true
        },
        containerColor = DarkAccentRed,
        contentColor = Color.White,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(20.dp)
            .size(56.dp)
    ) {
        Icon(Icons.Filled.Add, "add medicine options")
    }

    if (showAddMedicineForm) {
        AddMedicineFormDialog(
            isBengali = isBengali,
            existingMedicines = medicines,
            initialBarcode = scaffoldAddBarcode,
            onConfirmAdd = { newMed ->
                val existing = medicines.find { ext ->
                    (ext.name.equals(newMed.name, ignoreCase = true) &&
                     ext.dosageStrength.equals(newMed.dosageStrength, ignoreCase = true) &&
                     ext.type.equals(newMed.type, ignoreCase = true)) ||
                    (newMed.barcode.isNotBlank() && ext.barcode == newMed.barcode)
                }
                
                if (currentUser.role == "staff") {
                    viewModel.requestPendingMedicine(newMed, currentUser.username, existing?.id)
                    android.widget.Toast.makeText(
                        context,
                        if (isBengali) "অনুরোধটি অনুমোদনের জন্য মার্চেন্টের নিকট পাঠানো হয়েছে।" else "The request has been sent to the merchant for approval.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    showAddMedicineForm = false
                } else {
                    if (existing != null) {
                        val mergedMed = when (existing.type) {
                            "tablet" -> {
                                val oldTotal = existing.totalPiecesInStock
                                val addedTotal = (newMed.boxesInStock * newMed.stripsPerBox * newMed.pcsPerStrip) +
                                                 (newMed.stripsInStock * newMed.pcsPerStrip) +
                                                 newMed.pcsInStock
                                val combinedTotal = oldTotal + addedTotal
                                
                                val stripsPerBox = if (newMed.stripsPerBox > 0) newMed.stripsPerBox else existing.stripsPerBox
                                val pcsPerStrip = if (newMed.pcsPerStrip > 0) newMed.pcsPerStrip else existing.pcsPerStrip
                                val factor = stripsPerBox * pcsPerStrip
                                
                                val finalBoxes = if (factor > 0) combinedTotal / factor else 0
                                val remAfterBoxes = if (factor > 0) combinedTotal % factor else 0
                                val finalStrips = if (pcsPerStrip > 0) remAfterBoxes / pcsPerStrip else 0
                                val finalPcs = if (pcsPerStrip > 0) remAfterBoxes % pcsPerStrip else 0
                                
                                existing.copy(
                                    name = newMed.name,
                                    company = newMed.company,
                                    genericName = newMed.genericName,
                                    barcode = newMed.barcode,
                                    expiryDate = newMed.expiryDate,
                                    purchasePriceBox = newMed.purchasePriceBox,
                                    salePriceBox = newMed.salePriceBox,
                                    stripsPerBox = stripsPerBox,
                                    pcsPerStrip = pcsPerStrip,
                                    boxesInStock = finalBoxes,
                                    stripsInStock = finalStrips,
                                    pcsInStock = finalPcs
                                )
                            }
                            "syrup" -> {
                                existing.copy(
                                    name = newMed.name,
                                    company = newMed.company,
                                    genericName = newMed.genericName,
                                    barcode = newMed.barcode,
                                    expiryDate = newMed.expiryDate,
                                    mlPerBottle = newMed.mlPerBottle,
                                    purchasePriceUnit = newMed.purchasePriceUnit,
                                    salePriceUnit = newMed.salePriceUnit,
                                    bottlesInStock = existing.bottlesInStock + newMed.bottlesInStock
                                )
                            }
                            "ointment" -> {
                                existing.copy(
                                    name = newMed.name,
                                    company = newMed.company,
                                    genericName = newMed.genericName,
                                    barcode = newMed.barcode,
                                    expiryDate = newMed.expiryDate,
                                    gramsPerTube = newMed.gramsPerTube,
                                    purchasePriceUnit = newMed.purchasePriceUnit,
                                    salePriceUnit = newMed.salePriceUnit,
                                    tubesInStock = existing.tubesInStock + newMed.tubesInStock
                                )
                            }
                            "injection" -> {
                                existing.copy(
                                    name = newMed.name,
                                    company = newMed.company,
                                    genericName = newMed.genericName,
                                    barcode = newMed.barcode,
                                    expiryDate = newMed.expiryDate,
                                    purchasePriceUnit = newMed.purchasePriceUnit,
                                    salePriceUnit = newMed.salePriceUnit,
                                    vialsInStock = existing.vialsInStock + newMed.vialsInStock
                                )
                            }
                            else -> {
                                existing.copy(
                                    name = newMed.name,
                                    company = newMed.company,
                                    genericName = newMed.genericName,
                                    barcode = newMed.barcode,
                                    expiryDate = newMed.expiryDate,
                                    otherUnit = newMed.otherUnit,
                                    purchasePriceUnit = newMed.purchasePriceUnit,
                                    salePriceUnit = newMed.salePriceUnit,
                                    otherQuantity = existing.otherQuantity + newMed.otherQuantity
                                )
                            }
                        }
                        
                        viewModel.updateMedicine(mergedMed)
                        
                        val hasChangeInStock = (newMed.boxesInStock > 0 || newMed.stripsInStock > 0 || newMed.pcsInStock > 0 ||
                                               newMed.bottlesInStock > 0 || newMed.tubesInStock > 0 ||
                                               newMed.vialsInStock > 0 || newMed.otherQuantity > 0.0)
                        if (hasChangeInStock) {
                            val record = StockInRecord(
                                medicineId = existing.id,
                                medicineName = existing.name,
                                medicineType = existing.type,
                                timestamp = System.currentTimeMillis(),
                                addedBoxes = newMed.boxesInStock,
                                addedStrips = newMed.stripsInStock,
                                addedPcs = newMed.pcsInStock,
                                addedBottles = newMed.bottlesInStock,
                                addedTubes = newMed.tubesInStock,
                                addedVials = newMed.vialsInStock,
                                addedOtherQty = newMed.otherQuantity,
                                oldPurchasePrice = if (existing.type == "tablet") existing.purchasePriceBox else existing.purchasePriceUnit,
                                oldSalePrice = if (existing.type == "tablet") existing.salePriceBox else existing.salePriceUnit,
                                newPurchasePrice = if (existing.type == "tablet") newMed.purchasePriceBox else newMed.purchasePriceUnit,
                                newSalePrice = if (existing.type == "tablet") newMed.salePriceBox else newMed.salePriceUnit
                            )
                            viewModel.addStockInRecord(record)
                        }
                        showAddMedicineForm = false
                    } else {
                        viewModel.addMedicine(newMed) { newId ->
                            val hasChangeInStock = (newMed.boxesInStock > 0 || newMed.stripsInStock > 0 || newMed.pcsInStock > 0 ||
                                                   newMed.bottlesInStock > 0 || newMed.tubesInStock > 0 ||
                                                   newMed.vialsInStock > 0 || newMed.otherQuantity > 0.0)
                            if (hasChangeInStock) {
                                val record = StockInRecord(
                                    medicineId = newId,
                                    medicineName = newMed.name,
                                    medicineType = newMed.type,
                                    timestamp = System.currentTimeMillis(),
                                    addedBoxes = newMed.boxesInStock,
                                    addedStrips = newMed.stripsInStock,
                                    addedPcs = newMed.pcsInStock,
                                    addedBottles = newMed.bottlesInStock,
                                    addedTubes = newMed.tubesInStock,
                                    addedVials = newMed.vialsInStock,
                                    addedOtherQty = newMed.otherQuantity,
                                    oldPurchasePrice = 0.0,
                                    oldSalePrice = 0.0,
                                    newPurchasePrice = if (newMed.type == "tablet") newMed.purchasePriceBox else newMed.purchasePriceUnit,
                                    newSalePrice = if (newMed.type == "tablet") newMed.salePriceBox else newMed.salePriceUnit
                                )
                                viewModel.addStockInRecord(record)
                            }
                        }
                        showAddMedicineForm = false
                    }
                }
            },
            onDismiss = { showAddMedicineForm = false }
        )
    }

    if (medForQuickStockUpdate != null) {
        QuickStockUpdateDialog(
            med = medForQuickStockUpdate!!,
            isBengali = isBengali,
            onConfirmed = { b, s, p, bot, t, v, oQty, purchase, sale, lowStock ->
                if (currentUser.role == "staff") {
                    viewModel.requestPendingStockIn(
                        medicine = medForQuickStockUpdate!!,
                        addBoxes = b,
                        addStrips = s,
                        addPcs = p,
                        addBottles = bot,
                        addTubes = t,
                        addVials = v,
                        addOtherQty = oQty,
                        newPurchasePrice = purchase,
                        newSalePrice = sale,
                        staffUsername = currentUser.username
                    )
                    viewModel.updatePriceOnly(
                        medicine = medForQuickStockUpdate!!,
                        boxPurchase = medForQuickStockUpdate!!.purchasePriceBox,
                        boxSale = medForQuickStockUpdate!!.salePriceBox,
                        unitPurchase = medForQuickStockUpdate!!.purchasePriceUnit,
                        unitSale = medForQuickStockUpdate!!.salePriceUnit,
                        lowStockThreshold = lowStock
                    )
                    android.widget.Toast.makeText(
                        context,
                        if (isBengali) "স্টক যোগের অনুরোধ মার্চেন্টের অনুমোদনের জন্য পাঠানো হয়েছে।" else "Stock addition request sent for Merchant approval.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                } else {
                    viewModel.addStockAndPrices(medForQuickStockUpdate!!, b, s, p, bot, t, v, oQty, purchase, sale, lowStock)
                }
                medForQuickStockUpdate = null
            },
            onDismiss = { medForQuickStockUpdate = null }
        )
    }

    if (medForQuickPriceUpdate != null) {
        QuickPriceChangeDialog(
            med = medForQuickPriceUpdate!!,
            isBengali = isBengali,
            onConfirmed = { bP, bS, uP, uS, lowStock ->
                viewModel.updatePriceOnly(medForQuickPriceUpdate!!, bP, bS, uP, uS, lowStock)
                medForQuickPriceUpdate = null
            },
            onDismiss = { medForQuickPriceUpdate = null }
        )
    }

    if (medForDeleteConfirmation != null) {
        val selectedMed = medForDeleteConfirmation!!
        Dialog(onDismissRequest = { medForDeleteConfirmation = null }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, DarkAccentRed.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Warning icon
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = "Warning",
                        tint = DarkAccentRed,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = if (isBengali) "ওষুধ ডিলিট নিশ্চিত করুন" else "Confirm Delete",
                        color = DarkAccentGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (isBengali) 
                            "আপনি কি নিশ্চিত যে আপনি '${selectedMed.fullName}' ইনভেন্টরি থেকে ডিলিট করতে চান?" 
                            else "Are you sure you want to delete '${selectedMed.fullName}' from the inventory?",
                        color = DarkTextPrimary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Dismiss Button
                        TextButton(
                            onClick = { medForDeleteConfirmation = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isBengali) "বাতিল" else "Cancel", color = DarkTextSecondary)
                        }

                        // Confirm Button
                        Button(
                            onClick = {
                                viewModel.deleteMedicine(selectedMed)
                                medForDeleteConfirmation = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkAccentRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isBengali) "ডিলিট" else "Delete", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    }
}

