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
fun AddMedicineFormDialog(
    isBengali: Boolean,
    existingMedicines: List<Medicine>,
    initialBarcode: String = "",
    onConfirmAdd: (Medicine) -> Unit,
    onDismiss: () -> Unit
) {
    var step by remember { mutableStateOf(if (initialBarcode.isNotBlank()) 2 else 1) } // 1 to 4 steps
    var autoFilledForId by remember { mutableStateOf<Long?>(null) }

    // Step 1: Type Selection
    var mType by remember { mutableStateOf("tablet") } // tablet, syrup, ointment, injection, other
    var isSuggestionSelected by remember { mutableStateOf(false) }

    // Step 2: Info
    var name by remember { mutableStateOf("") }
    var dosageStrength by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    var generic by remember { mutableStateOf("") }
    var barcode by remember { mutableStateOf(initialBarcode) }
    
    // Expiry Date states (Year and Month manually selected)
    val currentCalendar = remember { Calendar.getInstance() }
    val currentYearInt = currentCalendar.get(Calendar.YEAR)
    val currentMonthInt = currentCalendar.get(Calendar.MONTH) + 1
    var expYear by remember { mutableStateOf(currentYearInt.toString()) }
    var expMonth by remember { mutableStateOf(String.format(Locale.US, "%02d", currentMonthInt)) }

    // Step 3: Pricing
    var purchaseBoxStr by remember { mutableStateOf("0") }
    var saleBoxStr by remember { mutableStateOf("0") }
    var profitPercentBoxStr by remember { mutableStateOf("") }
    var purchaseUnitStr by remember { mutableStateOf("0") }
    var saleUnitStr by remember { mutableStateOf("0") }
    var profitPercentUnitStr by remember { mutableStateOf("") }
    var strPerBoxStr by remember { mutableStateOf("10") }
    var pcsPerStrStr by remember { mutableStateOf("10") }

    // Step 4: Stock Info Setup
    var nowBoxesText by remember { mutableStateOf("0") }
    var nowStripsText by remember { mutableStateOf("0") }
    var nowPcsText by remember { mutableStateOf("0") }
    var mlConBotText by remember { mutableStateOf("100") }
    var botStockText by remember { mutableStateOf("0") }
    var gmConTubText by remember { mutableStateOf("15") }
    var tubStockText by remember { mutableStateOf("0") }
    var vialStockText by remember { mutableStateOf("0") }
    var othConQtyText by remember { mutableStateOf("0") }
    var othConUnitText by remember { mutableStateOf("পিস") }
    var lowStockThresholdValueStr by remember { mutableStateOf("10") }
    var lowStockThresholdUnit by remember { mutableStateOf("pieces") } // "pieces", "strips", "boxes", "bottles", "tubes", "vials", "other"

    val matchedExisting = remember(name, dosageStrength, barcode, mType, existingMedicines) {
        val cleanName = name.trim()
        val cleanDose = dosageStrength.trim()
        val cleanBarcode = barcode.trim()
        
        if (cleanName.isBlank() && cleanBarcode.isBlank()) {
            null
        } else {
            existingMedicines.find { ext ->
                val nameMatch = cleanName.isNotBlank() && ext.name.equals(cleanName, ignoreCase = true) && ext.dosageStrength.equals(cleanDose, ignoreCase = true) && ext.type.equals(mType, ignoreCase = true)
                val barcodeMatch = cleanBarcode.isNotBlank() && ext.barcode.isNotBlank() && ext.barcode == cleanBarcode
                nameMatch || barcodeMatch
            }
        }
    }

    LaunchedEffect(mType) {
        if (matchedExisting == null) {
            when (mType) {
                "tablet" -> {
                    lowStockThresholdValueStr = "5"
                    lowStockThresholdUnit = "strips"
                }
                "syrup" -> {
                    lowStockThresholdValueStr = "5"
                    lowStockThresholdUnit = "bottles"
                }
                "ointment" -> {
                    lowStockThresholdValueStr = "5"
                    lowStockThresholdUnit = "tubes"
                }
                "injection" -> {
                    lowStockThresholdValueStr = "5"
                    lowStockThresholdUnit = "vials"
                }
                else -> {
                    lowStockThresholdValueStr = "5"
                    lowStockThresholdUnit = "other"
                }
            }
        }
    }

    LaunchedEffect(matchedExisting) {
        if (matchedExisting != null) {
            if (autoFilledForId != matchedExisting.id) {
                autoFilledForId = matchedExisting.id
                name = matchedExisting.name
                dosageStrength = matchedExisting.dosageStrength
                company = matchedExisting.company
                generic = matchedExisting.genericName
                barcode = matchedExisting.barcode
                mType = matchedExisting.type
                
                // Parse expiry date if formatted as YYYY-MM-DD
                val parts = matchedExisting.expiryDate.split("-")
                if (parts.size >= 2) {
                    expYear = parts[0]
                    expMonth = parts[1]
                }
                
                purchaseBoxStr = matchedExisting.purchasePriceBox.toString()
                saleBoxStr = matchedExisting.salePriceBox.toString()
                strPerBoxStr = matchedExisting.stripsPerBox.toString()
                pcsPerStrStr = matchedExisting.pcsPerStrip.toString()
                
                // Recalculate profit percent
                if (matchedExisting.purchasePriceBox > 0.0) {
                    val pct = ((matchedExisting.salePriceBox - matchedExisting.purchasePriceBox) / matchedExisting.purchasePriceBox) * 100.0
                    profitPercentBoxStr = String.format(Locale.US, "%.1f", pct)
                } else {
                    profitPercentBoxStr = ""
                }
                
                purchaseUnitStr = matchedExisting.purchasePriceUnit.toString()
                saleUnitStr = matchedExisting.salePriceUnit.toString()
                
                // Recalculate profit percent unit
                if (matchedExisting.purchasePriceUnit > 0.0) {
                    val pct = ((matchedExisting.salePriceUnit - matchedExisting.purchasePriceUnit) / matchedExisting.purchasePriceUnit) * 100.0
                    profitPercentUnitStr = String.format(Locale.US, "%.1f", pct)
                } else {
                    profitPercentUnitStr = ""
                }
                
                mlConBotText = matchedExisting.mlPerBottle.toString()
                gmConTubText = matchedExisting.gramsPerTube.toString()
                othConUnitText = matchedExisting.otherUnit
                
                val threshold = matchedExisting.lowStockThreshold
                if (matchedExisting.type == "tablet") {
                    val sPerBox = matchedExisting.stripsPerBox
                    val pPerStrip = matchedExisting.pcsPerStrip
                    val pcsPerBox = sPerBox * pPerStrip
                    if (pcsPerBox > 0 && threshold % pcsPerBox == 0) {
                        lowStockThresholdValueStr = (threshold / pcsPerBox).toString()
                        lowStockThresholdUnit = "boxes"
                    } else if (pPerStrip > 0 && threshold % pPerStrip == 0) {
                        lowStockThresholdValueStr = (threshold / pPerStrip).toString()
                        lowStockThresholdUnit = "strips"
                    } else {
                        lowStockThresholdValueStr = threshold.toString()
                        lowStockThresholdUnit = "pieces"
                    }
                } else {
                    lowStockThresholdValueStr = threshold.toString()
                    lowStockThresholdUnit = when (matchedExisting.type) {
                        "syrup" -> "bottles"
                        "ointment" -> "tubes"
                        "injection" -> "vials"
                        else -> "other"
                    }
                }
                
                // But stock fields reset to 0:
                nowBoxesText = "0"
                nowStripsText = "0"
                nowPcsText = "0"
                botStockText = "0"
                tubStockText = "0"
                vialStockText = "0"
                othConQtyText = "0"
                
                isSuggestionSelected = true
            }
        } else {
            autoFilledForId = null
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            border = BorderStroke(1.dp, DarkAccentRed.copy(alpha = 0.2f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Stepper Visualizer
                Text(
                    text = "${if (isBengali) "নতুন ওষুধ যোগ — ধাপ " else "Add Medicine — Step "}$step/৪",
                    color = DarkAccentGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    when (step) {
                        1 -> {
                            Text(if (isBengali) "ওষুধের ধরন নির্ধারণ করুন:" else "Select Medicine Type:", color = DarkTextPrimary, fontSize = 14.sp, modifier = Modifier.padding(bottom = 12.dp))
                            val typesList = listOf(
                                "tablet" to (if (isBengali) "💊 ট্যাবলেট / ক্যাপসুল" else "💊 Tablet/Capsule"),
                                "syrup" to (if (isBengali) "🧴 সিরাপ / ড্রপ" else "🧴 Syrup/Drop"),
                                "ointment" to (if (isBengali) "🟡 মলম / ক্রিম" else "🟡 Ointment/Cream"),
                                "injection" to (if (isBengali) "💉 ইনজেকশন " else "💉 Injection"),
                                "other" to (if (isBengali) "📦 অন্যান্য" else "📦 Other")
                            )
                            typesList.forEach { (key, titleText) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (mType == key) DarkAccentRed else DarkBg)
                                        .clickable { 
                                            mType = key
                                            isSuggestionSelected = false
                                        }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = mType == key,
                                        onClick = { 
                                            mType = key
                                            isSuggestionSelected = false
                                        },
                                        colors = RadioButtonDefaults.colors(selectedColor = if (mType == key) DarkAccentText else Color.White, unselectedColor = DarkTextSecondary)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(titleText, color = if (mType == key) DarkAccentText else DarkTextPrimary, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        2 -> {
                            Text(if (isBengali) "ওষুধের বিবরণ লিখুন:" else "Fill General Information:", color = DarkTextPrimary, fontSize = 14.sp, modifier = Modifier.padding(bottom = 12.dp))
                            OutlinedTextField(
                                value = name,
                                onValueChange = { 
                                    name = it
                                    isSuggestionSelected = false
                                },
                                label = { Text(if (isBengali) "ওষুধের নাম (ব্র্যান্ড যেমন: Seclo)" else "Medicine Name (Brand e.g. Seclo)") },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                            )
                            
                            if (matchedExisting != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .background(Color.Green.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                        .border(1.dp, Color.Green.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = if (isBengali) "✓ এই ওষুধটি ইতিমধ্যেই স্টকে আছে! পূর্ববর্তী তথ্য অটো-ফিল করা হয়েছে। নতুন স্টক সংখ্যা যোগ করুন।" 
                                               else "✓ Existing stock medicine found! Auto-filled metadata and prices. Add new stock.",
                                        color = Color.Green,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else if (isSuggestionSelected) {
                                Text(
                                    text = if (isBengali) "✓ অটো-ফিল করা হয়েছে: $name ($dosageStrength)" else "✓ Auto-filled: $name ($dosageStrength)",
                                    color = Color.Green,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                            
                            // Auto-suggestions list underneath brand name field
                            val nameSuggestions = remember(name, mType, isSuggestionSelected) {
                                if (name.isBlank() || isSuggestionSelected || mType == "other") {
                                    emptyList()
                                } else {
                                    predefinedMedicineDatabase.filter {
                                        it.type.equals(mType, ignoreCase = true) &&
                                        it.name.contains(name, ignoreCase = true)
                                    }.take(4)
                                }
                            }
                            if (nameSuggestions.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .border(1.dp, SleekBorderColor, RoundedCornerShape(8.dp))
                                        .background(DarkSurface)
                                ) {
                                    Text(
                                        text = if (isBengali) "পরামর্শ তালিকা (স্পর্শ করে অটো-ফিল করুন):" else "Auto-fill suggestions (Tap to select):",
                                        color = DarkAccentGold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                    nameSuggestions.forEach { pred ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    name = pred.name
                                                    dosageStrength = pred.dose
                                                    company = pred.company
                                                    generic = pred.generic
                                                    mType = pred.type
                                                    pred.defaultStripsPerBox?.let { strPerBoxStr = it.toString() }
                                                    pred.defaultPcsPerStrip?.let { pcsPerStrStr = it.toString() }
                                                    pred.defaultMlPerBottle?.let { mlConBotText = it.toString() }
                                                    pred.defaultGramsPerTube?.let { gmConTubText = it.toString() }
                                                    isSuggestionSelected = true
                                                }
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(
                                                    text = "${pred.name} ${pred.dose}",
                                                    color = DarkTextPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "${pred.generic} | ${pred.company}",
                                                    color = DarkTextSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                            Text(
                                                text = pred.type.uppercase(),
                                                color = DarkAccentGold,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .border(1.dp, DarkAccentGold, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                        Divider(color = SleekBorderColor)
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = dosageStrength,
                                onValueChange = { dosageStrength = it },
                                label = { Text(if (isBengali) "ডোজ/শক্তি (যেমন: 20mg, 40mg, 100ml)" else "Dosage/Strength (e.g. 20mg, 40mg, 100ml)") },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                            )
                            OutlinedTextField(
                                value = company,
                                onValueChange = { company = it },
                                label = { Text(if (isBengali) "কোম্পানির নাম" else "Company Name") },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                            )
                            OutlinedTextField(
                                value = generic,
                                onValueChange = { generic = it },
                                label = { Text(if (isBengali) "জেনেরিক নাম" else "Generic Name") },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                            )
                            OutlinedTextField(
                                value = barcode,
                                onValueChange = { barcode = it },
                                label = { Text(if (isBengali) "বারকোড নম্বর" else "Barcode standard No.") },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                            )
                            Text(
                                text = if (isBengali) "ওষুধের মেয়াদকাল নির্ধারণ করুন:" else "Set Expiry Date:",
                                color = DarkAccentGold,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Month Selector
                                Box(modifier = Modifier.weight(1f)) {
                                    var showMonthMenu by remember { mutableStateOf(false) }
                                    val monthLabels = listOf(
                                        "01" to (if (isBengali) "জানুয়ারি (০১)" else "Jan (01)"),
                                        "02" to (if (isBengali) "ফেব্রুয়ারি (০২)" else "Feb (02)"),
                                        "03" to (if (isBengali) "মার্চ (০৩)" else "Mar (03)"),
                                        "04" to (if (isBengali) "এপ্রিল (০৪)" else "Apr (04)"),
                                        "05" to (if (isBengali) "মে (০৫)" else "May (05)"),
                                        "06" to (if (isBengali) "জুন (০৬)" else "Jun (06)"),
                                        "07" to (if (isBengali) "জুলাই (০৭)" else "Jul (07)"),
                                        "08" to (if (isBengali) "আগস্ট (০৮)" else "Aug (08)"),
                                        "09" to (if (isBengali) "সেপ্টেম্বর (০৯)" else "Sep (09)"),
                                        "10" to (if (isBengali) "অক্টোবর (১০)" else "Oct (10)"),
                                        "11" to (if (isBengali) "নভেম্বর (১১)" else "Nov (11)"),
                                        "12" to (if (isBengali) "ডিসেম্বর (১২)" else "Dec (12)")
                                    )
                                    val selectedMonthLabel = monthLabels.find { it.first == expMonth }?.second ?: expMonth

                                    OutlinedCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showMonthMenu = true },
                                        colors = CardDefaults.outlinedCardColors(containerColor = DarkBg),
                                        border = BorderStroke(1.dp, SleekBorderColor)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(if (isBengali) "মাস" else "Month", fontSize = 10.sp, color = DarkTextSecondary)
                                                Text(selectedMonthLabel, fontSize = 13.sp, color = DarkTextPrimary, fontWeight = FontWeight.Bold)
                                            }
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = DarkAccentGold)
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = showMonthMenu,
                                        onDismissRequest = { showMonthMenu = false },
                                        modifier = Modifier.background(DarkSurface).border(1.dp, SleekBorderColor)
                                    ) {
                                        monthLabels.forEach { (mCode, label) ->
                                            DropdownMenuItem(
                                                text = { Text(label, color = DarkTextPrimary) },
                                                onClick = {
                                                    expMonth = mCode
                                                    showMonthMenu = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Year Selector
                                Box(modifier = Modifier.weight(1f)) {
                                    var showYearMenu by remember { mutableStateOf(false) }
                                    val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                                    val years = (currentYear..(currentYear + 15)).map { it.toString() }

                                    OutlinedCard(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showYearMenu = true },
                                        colors = CardDefaults.outlinedCardColors(containerColor = DarkBg),
                                        border = BorderStroke(1.dp, SleekBorderColor)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(if (isBengali) "সাল" else "Year", fontSize = 10.sp, color = DarkTextSecondary)
                                                Text(if (isBengali) formatBengaliNumber(expYear) else expYear, fontSize = 13.sp, color = DarkTextPrimary, fontWeight = FontWeight.Bold)
                                            }
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = DarkAccentGold)
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = showYearMenu,
                                        onDismissRequest = { showYearMenu = false },
                                        modifier = Modifier.background(DarkSurface).border(1.dp, SleekBorderColor)
                                    ) {
                                        years.forEach { yearStr ->
                                            DropdownMenuItem(
                                                text = { Text(if (isBengali) formatBengaliNumber(yearStr) else yearStr, color = DarkTextPrimary) },
                                                onClick = {
                                                    expYear = yearStr
                                                    showYearMenu = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        3 -> {
                            Text(if (isBengali) "ক্রয় ও বিক্রয় মূল্য নির্ধারণ করুন" else "Setup Prices", color = DarkTextPrimary, fontSize = 14.sp, modifier = Modifier.padding(bottom = 12.dp))
                            if (mType == "tablet") {
                                OutlinedTextField(
                                    value = saleBoxStr,
                                    onValueChange = { input ->
                                        saleBoxStr = input
                                        val sale = input.toDoubleOrNull() ?: 0.0
                                        val pct = profitPercentBoxStr.toDoubleOrNull()
                                        val pur = purchaseBoxStr.toDoubleOrNull()
                                        if (pur != null && pur > 0.0) {
                                            val calculatedPct = ((sale - pur) / pur) * 100.0
                                            profitPercentBoxStr = String.format(Locale.US, "%.1f", calculatedPct)
                                        } else if (pct != null && pct > -100.0) {
                                            val calculatedPur = sale / (1.0 + (pct / 100.0))
                                            purchaseBoxStr = String.format(Locale.US, "%.2f", calculatedPur)
                                        }
                                    },
                                    label = { Text(if (isBengali) "বিক্রয় মূল্য ১ বক্স" else "Sale Price 1 Box") },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                OutlinedTextField(
                                    value = profitPercentBoxStr,
                                    onValueChange = { input ->
                                        profitPercentBoxStr = input
                                        val pct = input.toDoubleOrNull() ?: 0.0
                                        val sale = saleBoxStr.toDoubleOrNull() ?: 0.0
                                        if (pct > -100.0 && sale > 0.0) {
                                            val pur = sale / (1.0 + (pct / 100.0))
                                            purchaseBoxStr = String.format(Locale.US, "%.2f", pur)
                                        }
                                    },
                                    label = { Text(if (isBengali) "লাভের হার (%) (ঐচ্ছিক)" else "Profit Percentage (%) (Optional)") },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                OutlinedTextField(
                                    value = purchaseBoxStr,
                                    onValueChange = { input ->
                                        purchaseBoxStr = input
                                        val pur = input.toDoubleOrNull() ?: 0.0
                                        val sale = saleBoxStr.toDoubleOrNull() ?: 0.0
                                        if (pur > 0.0) {
                                            val pct = ((sale - pur) / pur) * 100.0
                                            profitPercentBoxStr = String.format(Locale.US, "%.1f", pct)
                                        }
                                    },
                                    label = { Text(if (isBengali) "ক্রয় মূল্য ১ বক্স" else "Buy Price 1 Box") },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                OutlinedTextField(
                                    value = strPerBoxStr,
                                    onValueChange = { strPerBoxStr = it },
                                    label = { Text(if (isBengali) "প্রতি বক্সে কতটি পাতা" else "Strips Per Box") },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                OutlinedTextField(
                                    value = pcsPerStrStr,
                                    onValueChange = { pcsPerStrStr = it },
                                    label = { Text(if (isBengali) "প্রতি পাতায় কতটি পিস" else "Pieces Per Strip") },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )

                                // Live dynamic calculations display
                                val pBox = purchaseBoxStr.toDoubleOrNull() ?: 0.0
                                val sBox = saleBoxStr.toDoubleOrNull() ?: 0.0
                                val strPerB = strPerBoxStr.toIntOrNull() ?: 1
                                val pcsPerStr = pcsPerStrStr.toIntOrNull() ?: 1

                                val pStrip = if (strPerB > 0) pBox / strPerB else 0.0
                                val pPc = if (pcsPerStr > 0) pStrip / pcsPerStr else 0.0
                                val sStrip = if (strPerB > 0) sBox / strPerB else 0.0
                                val sPc = if (pcsPerStr > 0) sStrip / pcsPerStr else 0.0
                                val profitP = sPc - pPc
                                val profitPct = if (pPc > 0) (profitP / pPc) * 100 else 0.0

                                Card(colors = CardDefaults.cardColors(containerColor = DarkBg), modifier = Modifier.padding(top = 8.dp)) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("${if (isBengali) "প্রতি পাতা ক্রয় = ৳" else "Strip Buy = ৳"}${String.format("%.2f", pStrip)}", color = DarkTextSecondary, fontSize = 11.sp)
                                        Text("${if (isBengali) "প্রতি পিস ক্রয় = ৳" else "Pc Buy = ৳"}${String.format("%.2f", pPc)}", color = DarkTextSecondary, fontSize = 11.sp)
                                        Text("${if (isBengali) "প্রতি পাতা বিক্রয় = ৳" else "Strip Sell = ৳"}${String.format("%.2f", sStrip)}", color = DarkTextSecondary, fontSize = 11.sp)
                                        Text("${if (isBengali) "প্রতি পিস বিক্রয় = ৳" else "Pc Sell = ৳"}${String.format("%.2f", sPc)}", color = DarkTextSecondary, fontSize = 11.sp)
                                        Text("${if (isBengali) "প্রতি পিস লাভ = ৳" else "Per Piece Profit = ৳"}${String.format("%.2f", profitP)} (${String.format("%.1f", profitPct)}%)", color = Color.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                OutlinedTextField(
                                    value = saleUnitStr,
                                    onValueChange = { input ->
                                        saleUnitStr = input
                                        val sale = input.toDoubleOrNull() ?: 0.0
                                        val pct = profitPercentUnitStr.toDoubleOrNull()
                                        val pur = purchaseUnitStr.toDoubleOrNull()
                                        if (pur != null && pur > 0.0) {
                                            val calculatedPct = ((sale - pur) / pur) * 100.0
                                            profitPercentUnitStr = String.format(Locale.US, "%.1f", calculatedPct)
                                        } else if (pct != null && pct > -100.0) {
                                            val calculatedPur = sale / (1.0 + (pct / 100.0))
                                            purchaseUnitStr = String.format(Locale.US, "%.2f", calculatedPur)
                                        }
                                    },
                                    label = { Text(if (isBengali) "বিক্রয় মূল্য (প্রতি পিস/বোতল)" else "Sale Price per unit") },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                OutlinedTextField(
                                    value = profitPercentUnitStr,
                                    onValueChange = { input ->
                                        profitPercentUnitStr = input
                                        val pct = input.toDoubleOrNull() ?: 0.0
                                        val sale = saleUnitStr.toDoubleOrNull() ?: 0.0
                                        if (pct > -100.0 && sale > 0.0) {
                                            val pur = sale / (1.0 + (pct / 100.0))
                                            purchaseUnitStr = String.format(Locale.US, "%.2f", pur)
                                        }
                                    },
                                    label = { Text(if (isBengali) "লাভের হার (%) (ঐচ্ছিক)" else "Profit Percentage (%) (Optional)") },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                OutlinedTextField(
                                    value = purchaseUnitStr,
                                    onValueChange = { input ->
                                        purchaseUnitStr = input
                                        val pur = input.toDoubleOrNull() ?: 0.0
                                        val sale = saleUnitStr.toDoubleOrNull() ?: 0.0
                                        if (pur > 0.0) {
                                            val pct = ((sale - pur) / pur) * 100.0
                                            profitPercentUnitStr = String.format(Locale.US, "%.1f", pct)
                                        }
                                    },
                                    label = { Text(if (isBengali) "ক্রয় মূল্য (প্রতি পিস/বোতল)" else "Buy Price per unit") },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                            }

                            val isPriceWarning = if (mType == "tablet") {
                                val pur = purchaseBoxStr.toDoubleOrNull() ?: 0.0
                                val sale = saleBoxStr.toDoubleOrNull() ?: 0.0
                                pur > sale && pur > 0.0 && sale > 0.0
                            } else {
                                val pur = purchaseUnitStr.toDoubleOrNull() ?: 0.0
                                val sale = saleUnitStr.toDoubleOrNull() ?: 0.0
                                pur > sale && pur > 0.0 && sale > 0.0
                            }
                            if (isPriceWarning) {
                                Text(
                                    text = if (isBengali) "⚠️ সতর্কবার্তা: ক্রয় মূল্য বিক্রয় মূল্য থেকে বেশি হতে পারে না!" else "⚠️ Warning: Purchase price cannot exceed sale price!",
                                    color = DarkAccentRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }
                        4 -> {
                            Text(if (isBengali) "বর্তমান স্টকের পরিমাণ নির্ধারণ করুন" else "Set Stock Quantities", color = DarkTextPrimary, fontSize = 14.sp, modifier = Modifier.padding(bottom = 12.dp))
                            when (mType) {
                                "tablet" -> {
                                    OutlinedTextField(
                                        value = nowBoxesText,
                                        onValueChange = { nowBoxesText = it },
                                        label = { Text(if (isBengali) "কতটি বক্স আছে" else "How many boxes") },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                    OutlinedTextField(
                                        value = nowStripsText,
                                        onValueChange = { nowStripsText = it },
                                        label = { Text(if (isBengali) "কতটি পাতা আছে" else "How many strips") },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                    OutlinedTextField(
                                        value = nowPcsText,
                                        onValueChange = { nowPcsText = it },
                                        label = { Text(if (isBengali) "কতটি পিস আছে" else "How many pieces") },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )

                                    val bS = nowBoxesText.toIntOrNull() ?: 0
                                    val sS = nowStripsText.toIntOrNull() ?: 0
                                    val pS = nowPcsText.toIntOrNull() ?: 0
                                    val strPerB = strPerBoxStr.toIntOrNull() ?: 1
                                    val pcsPerS = pcsPerStrStr.toIntOrNull() ?: 1
                                    val autoTotalPcs = (bS * strPerB * pcsPerS) + (sS * pcsPerS) + pS

                                    Text(
                                        text = "${if (isBengali) "মোট পিস সংখ্যা: " else "Auto Total Pieces: "}$autoTotalPcs",
                                        color = DarkAccentGold,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                                "syrup" -> {
                                    OutlinedTextField(
                                        value = mlConBotText,
                                        onValueChange = { mlConBotText = it },
                                        label = { Text(if (isBengali) "বোতলের সাইজ (ML)" else "ML per bottle") },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                    OutlinedTextField(
                                        value = botStockText,
                                        onValueChange = { botStockText = it },
                                        label = { Text(if (isBengali) "বোতলের স্টক" else "Bottles in stock") },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                }
                                "ointment" -> {
                                    OutlinedTextField(
                                        value = gmConTubText,
                                        onValueChange = { gmConTubText = it },
                                        label = { Text(if (isBengali) "টিউবের সাইজ (গ্রাম)" else "Grams per tube") },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                    OutlinedTextField(
                                        value = tubStockText,
                                        onValueChange = { tubStockText = it },
                                        label = { Text(if (isBengali) "টিউবের স্টক" else "Tubes in stock") },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                }
                                "injection" -> {
                                    OutlinedTextField(
                                        value = vialStockText,
                                        onValueChange = { vialStockText = it },
                                        label = { Text(if (isBengali) "ভায়ালের স্টক" else "Vials in stock") },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                }
                                else -> { // other
                                    OutlinedTextField(
                                        value = othConQtyText,
                                        onValueChange = { othConQtyText = it },
                                        label = { Text(if (isBengali) "স্টকের সংখ্যা" else "Quantity") },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                    OutlinedTextField(
                                        value = othConUnitText,
                                        onValueChange = { othConUnitText = it },
                                        label = { Text(if (isBengali) "পরিমাণের ইউনিট (e.g. পিস/বক্স)" else "Unit name") },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = SleekBorderColor, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Text(
                                text = if (isBengali) "কম স্টক সতর্কতা সীমা নির্ধারণ:" else "Low Stock Alert Limit Setup:",
                                color = DarkAccentGold,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            
                            // Unit selector row
                            Text(
                                text = if (isBengali) "কোন ইউনিটে সতর্কতা দেখতে চান?" else "Which unit for the alert limit?",
                                color = DarkTextSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val units = when (mType) {
                                    "tablet" -> listOf(
                                        "boxes" to (if (isBengali) "বক্স" else "Boxes"),
                                        "strips" to (if (isBengali) "পাতা" else "Strips"),
                                        "pieces" to (if (isBengali) "পিস" else "Pieces")
                                    )
                                    "syrup" -> listOf(
                                        "bottles" to (if (isBengali) "বোতল" else "Bottles")
                                    )
                                    "ointment" -> listOf(
                                        "tubes" to (if (isBengali) "টিউব" else "Tubes")
                                    )
                                    "injection" -> listOf(
                                        "vials" to (if (isBengali) "ভায়াল" else "Vials")
                                    )
                                    else -> listOf(
                                        "other" to othConUnitText.trim().ifEmpty { if (isBengali) "পিস" else "Pcs" }
                                    )
                                }
                                
                                units.forEach { (key, label) ->
                                    val isSelected = lowStockThresholdUnit == key
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(if (isSelected) DarkAccentRed else DarkSurface, RoundedCornerShape(8.dp))
                                            .border(1.dp, if (isSelected) DarkAccentRed else SleekBorderColor, RoundedCornerShape(8.dp))
                                            .clickable { 
                                                lowStockThresholdUnit = key
                                                lowStockThresholdValueStr = when (key) {
                                                    "boxes" -> "2"
                                                    "strips" -> "5"
                                                    "pieces" -> "50"
                                                    "bottles" -> "5"
                                                    "tubes" -> "5"
                                                    "vials" -> "5"
                                                    else -> "5"
                                                }
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) DarkAccentText else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            
                            val explanationText = remember(mType, lowStockThresholdUnit, isBengali, othConUnitText, strPerBoxStr, pcsPerStrStr, lowStockThresholdValueStr) {
                                val sPerBox = strPerBoxStr.toIntOrNull() ?: 10
                                val pPerStr = pcsPerStrStr.toIntOrNull() ?: 10
                                val enteredValue = lowStockThresholdValueStr.toIntOrNull() ?: 5
                                when (mType) {
                                    "tablet" -> {
                                        when (lowStockThresholdUnit) {
                                            "boxes" -> if (isBengali) {
                                                "১ বক্স = ${sPerBox * pPerStr} পিস। সেট করা সীমা অনুযায়ী মোট স্টক ${sPerBox * pPerStr * enteredValue} পিসের নিচে নামলে সতর্কবার্তা দেখাবে।"
                                            } else {
                                                "1 box = ${sPerBox * pPerStr} pieces. Alert will trigger when stock falls below ${sPerBox * pPerStr * enteredValue} pieces."
                                            }
                                            "strips" -> if (isBengali) {
                                                "১ পাতা = $pPerStr পিস। সেট করা সীমা অনুযায়ী মোট স্টক ${pPerStr * enteredValue} পিসের নিচে নামলে সতর্কবার্তা দেখাবে।"
                                            } else {
                                                "1 strip = $pPerStr pieces. Alert will trigger when stock falls below ${pPerStr * enteredValue} pieces."
                                            }
                                            else -> if (isBengali) {
                                                "সেট করা সীমা অনুযায়ী মোট স্টক $enteredValue পিসের নিচে নামলে সতর্কবার্তা দেখাবে।"
                                            } else {
                                                "Alert will trigger when stock falls below $enteredValue pieces."
                                            }
                                        }
                                    }
                                    "syrup" -> if (isBengali) {
                                        "সিরাপের ক্ষেত্রে মোট বোতলের সংখ্যা লিখুন (যেমন: স্টক $enteredValue বোতলের নিচে নামলে সতর্ক করবে)।"
                                    } else {
                                        "For syrup, enter the limit in total bottles (e.g., alert below $enteredValue bottles)."
                                    }
                                    "ointment" -> if (isBengali) {
                                        "মলম/টিউবের ক্ষেত্রে মোট টিউবের সংখ্যা লিখুন (যেমন: স্টক $enteredValue টিউবের নিচে নামলে সতর্ক করবে)।"
                                    } else {
                                        "For ointment, enter the limit in total tubes (e.g., alert below $enteredValue tubes)."
                                    }
                                    "injection" -> if (isBengali) {
                                        "ইনজেকশনের ক্ষেত্রে মোট ভায়ালের সংখ্যা লিখুন (যেমন: স্টক $enteredValue ভায়ালের নিচে নামলে সতর্ক করবে)।"
                                    } else {
                                        "For injection, enter the limit in total vials (e.g., alert below $enteredValue vials)."
                                    }
                                    else -> {
                                        val unit = othConUnitText.trim().ifEmpty { if (isBengali) "পিস" else "Pcs" }
                                        if (isBengali) {
                                            "অন্যান্য ওষুধের ক্ষেত্রে পরিমাণের ইউনিট ($unit) অনুযায়ী সংখ্যা লিখুন (যেমন: স্টক $enteredValue $unit এর নিচে নামলে সতর্ক করবে)।"
                                        } else {
                                            "For other items, enter the limit in units of $unit (e.g., alert below $enteredValue $unit)."
                                        }
                                    }
                                }
                            }
                            
                            Text(
                                text = explanationText,
                                color = DarkTextSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )

                            OutlinedTextField(
                                value = lowStockThresholdValueStr,
                                onValueChange = { lowStockThresholdValueStr = it },
                                label = { 
                                    val unitSuffix = when (lowStockThresholdUnit) {
                                        "boxes" -> if (isBengali) "বক্স" else "boxes"
                                        "strips" -> if (isBengali) "পাতা" else "strips"
                                        "pieces" -> if (isBengali) "পিস সংখ্যায়" else "in pieces"
                                        "bottles" -> if (isBengali) "বোতলে" else "in bottles"
                                        "tubes" -> if (isBengali) "টিউবে" else "in tubes"
                                        "vials" -> if (isBengali) "ভায়ালে" else "in vials"
                                        else -> if (isBengali) othConUnitText else "in units"
                                    }
                                    Text(if (isBengali) "সতর্কতা সীমা ($unitSuffix)" else "Alert limit ($unitSuffix)") 
                                },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                            )

                            // Quick selection suggestion chips
                            val suggestions = remember(mType, lowStockThresholdUnit, isBengali, othConUnitText) {
                                when (mType) {
                                    "tablet" -> {
                                        when (lowStockThresholdUnit) {
                                            "boxes" -> listOf(
                                                "1" to (if (isBengali) "১ বক্স" else "1 Box"),
                                                "2" to (if (isBengali) "২ বক্স" else "2 Boxes"),
                                                "5" to (if (isBengali) "৫ বক্স" else "5 Boxes")
                                            )
                                            "strips" -> listOf(
                                                "3" to (if (isBengali) "৩ পাতা" else "3 Strips"),
                                                "5" to (if (isBengali) "৫ পাতা" else "5 Strips"),
                                                "10" to (if (isBengali) "১০ পাতা" else "10 Strips")
                                            )
                                            else -> listOf(
                                                "20" to (if (isBengali) "২০ পিস" else "20 Pcs"),
                                                "50" to (if (isBengali) "৫০ পিস" else "50 Pcs"),
                                                "100" to (if (isBengali) "১০০ পিস" else "100 Pcs")
                                            )
                                        }
                                    }
                                    "syrup" -> listOf(
                                        "3" to (if (isBengali) "৩ বোতল" else "3 Bottles"),
                                        "5" to (if (isBengali) "৫ বোতল" else "5 Bottles"),
                                        "10" to (if (isBengali) "১০ বোতল" else "10 Bottles")
                                    )
                                    "ointment" -> listOf(
                                        "3" to (if (isBengali) "৩ টিউব" else "3 Tubes"),
                                        "5" to (if (isBengali) "৫ টিউব" else "5 Tubes"),
                                        "10" to (if (isBengali) "১০ টিউব" else "10 Tubes")
                                    )
                                    "injection" -> listOf(
                                        "3" to (if (isBengali) "৩ ভায়াল" else "3 Vials"),
                                        "5" to (if (isBengali) "৫ ভায়াল" else "5 Vials"),
                                        "10" to (if (isBengali) "১০ ভায়াল" else "10 Vials")
                                    )
                                    else -> {
                                        val unit = othConUnitText.trim().ifEmpty { if (isBengali) "পিস" else "Pcs" }
                                        listOf(
                                            "3" to "৩ $unit",
                                            "5" to "৫ $unit",
                                            "10" to "১০ $unit"
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                suggestions.forEach { (valStr, label) ->
                                    val isSelected = lowStockThresholdValueStr == valStr
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(if (isSelected) DarkAccentRed else DarkSurface, RoundedCornerShape(8.dp))
                                            .border(1.dp, if (isSelected) DarkAccentRed else SleekBorderColor, RoundedCornerShape(8.dp))
                                            .clickable { lowStockThresholdValueStr = valStr }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) DarkAccentText else Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (step > 1) {
                        OutlinedButton(onClick = { step-- }) {
                            Text(if (isBengali) "পূর্ববর্তী" else "Back")
                        }
                    } else {
                        TextButton(onClick = onDismiss) {
                            Text(if (isBengali) "বাতিল" else "Cancel", color = DarkTextSecondary)
                        }
                    }

                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = DarkAccentRed, contentColor = DarkAccentText),
                        onClick = {
                            if (step < 4) {
                                step++
                            } else {
                                val medicine = Medicine(
                                    type = mType,
                                    name = name.trim().ifEmpty { "N/A" },
                                    dosageStrength = dosageStrength.trim(),
                                    company = company.trim().ifEmpty { "N/A" },
                                    genericName = generic.trim().ifEmpty { "N/A" },
                                    barcode = barcode.trim(),
                                    expiryDate = "$expYear-$expMonth-28",
                                    purchasePriceBox = purchaseBoxStr.toDoubleOrNull() ?: 0.0,
                                    salePriceBox = saleBoxStr.toDoubleOrNull() ?: 0.0,
                                    stripsPerBox = strPerBoxStr.toIntOrNull() ?: 1,
                                    pcsPerStrip = pcsPerStrStr.toIntOrNull() ?: 1,
                                    boxesInStock = nowBoxesText.toIntOrNull() ?: 0,
                                    stripsInStock = nowStripsText.toIntOrNull() ?: 0,
                                    pcsInStock = nowPcsText.toIntOrNull() ?: 0,
                                    mlPerBottle = mlConBotText.toIntOrNull() ?: 0,
                                    bottlesInStock = botStockText.toIntOrNull() ?: 0,
                                    gramsPerTube = gmConTubText.toIntOrNull() ?: 0,
                                    tubesInStock = tubStockText.toIntOrNull() ?: 0,
                                    vialsInStock = vialStockText.toIntOrNull() ?: 0,
                                    otherQuantity = othConQtyText.toDoubleOrNull() ?: 0.0,
                                    otherUnit = othConUnitText.trim(),
                                    purchasePriceUnit = purchaseUnitStr.toDoubleOrNull() ?: 0.0,
                                    salePriceUnit = saleUnitStr.toDoubleOrNull() ?: 0.0,
                                    lowStockThreshold = when (mType) {
                                        "tablet" -> {
                                            val sVal = lowStockThresholdValueStr.toIntOrNull() ?: 10
                                            val strPerBox = strPerBoxStr.toIntOrNull() ?: 1
                                            val pcsPerStr = pcsPerStrStr.toIntOrNull() ?: 1
                                            when (lowStockThresholdUnit) {
                                                "boxes" -> sVal * strPerBox * pcsPerStr
                                                "strips" -> sVal * pcsPerStr
                                                else -> sVal
                                            }
                                        }
                                        else -> lowStockThresholdValueStr.toIntOrNull() ?: 5
                                    }
                                )
                                onConfirmAdd(medicine)
                            }
                        }
                    ) {
                        Text(if (step < 4) (if (isBengali) "পরবর্তী" else "Next") else (if (isBengali) "যোগ করুন" else "Save"), color = DarkAccentText)
                    }
                }
            }
        }
    }
}

@Composable
fun QuickStockUpdateDialog(
    med: Medicine,
    isBengali: Boolean,
    onConfirmed: (boxes: Int, strips: Int, pcs: Int, bottles: Int, tubes: Int, vials: Int, otherQty: Double, newPurchasePrice: Double?, newSalePrice: Double?, lowStockThreshold: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var bText by remember { mutableStateOf("0") }
    var sText by remember { mutableStateOf("0") }
    var pText by remember { mutableStateOf("0") }
    var botText by remember { mutableStateOf("0") }
    var tubeText by remember { mutableStateOf("0") }
    var vialText by remember { mutableStateOf("0") }
    var oQtyText by remember { mutableStateOf("0") }
    
    val initialPurchasePrice = if (med.type == "tablet") med.purchasePriceBox else med.purchasePriceUnit
    val initialSalePrice = if (med.type == "tablet") med.salePriceBox else med.salePriceUnit

    var purchasePriceText by remember { mutableStateOf("") }
    var salePriceText by remember { mutableStateOf("") }
    var profitPercentText by remember {
        val initialPct = if (initialPurchasePrice > 0.0) {
            ((initialSalePrice - initialPurchasePrice) / initialPurchasePrice) * 100.0
        } else 0.0
        mutableStateOf(if (initialPct > 0.0) String.format(Locale.US, "%.1f", initialPct) else "")
    }

    var lowStockThresholdValueStr by remember {
        val threshold = med.lowStockThreshold
        val initialVal = if (med.type == "tablet") {
            val sPerBox = med.stripsPerBox
            val pPerStrip = med.pcsPerStrip
            val pcsPerBox = sPerBox * pPerStrip
            if (pcsPerBox > 0 && threshold % pcsPerBox == 0) {
                (threshold / pcsPerBox).toString()
            } else if (pPerStrip > 0 && threshold % pPerStrip == 0) {
                (threshold / pPerStrip).toString()
            } else {
                threshold.toString()
            }
        } else {
            threshold.toString()
        }
        mutableStateOf(initialVal)
    }

    var lowStockThresholdUnit by remember {
        val threshold = med.lowStockThreshold
        val initialUnit = if (med.type == "tablet") {
            val sPerBox = med.stripsPerBox
            val pPerStrip = med.pcsPerStrip
            val pcsPerBox = sPerBox * pPerStrip
            if (pcsPerBox > 0 && threshold % pcsPerBox == 0) {
                "boxes"
            } else if (pPerStrip > 0 && threshold % pPerStrip == 0) {
                "strips"
            } else {
                "pieces"
            }
        } else {
            when (med.type) {
                "syrup" -> "bottles"
                "ointment" -> "tubes"
                "injection" -> "vials"
                else -> "other"
            }
        }
        mutableStateOf(initialUnit)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "${if (isBengali) "স্টক আপডেট: " else "Update Stock: "}${med.fullName}",
                    color = DarkAccentGold,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Read-only Details
                Text(
                    text = if (isBengali) "বর্তমান বিবরণ ও স্টক:" else "Current Details & Stock:",
                    color = DarkTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkCardBg, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(med.name, color = DarkTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${med.genericName} | ${med.company}", color = DarkTextSecondary, fontSize = 11.sp)
                    Text("${if (isBengali) "বারকোড: " else "Barcode: "}${med.barcode} | ${if (isBengali) "মেয়াদ: " else "Exp: "}${med.expiryDate}", color = DarkTextSecondary, fontSize = 11.sp)
                    if (med.type == "tablet") {
                        Text("${if (isBengali) "প্রতি বক্সে পাতা: " else "Strips per box: "}${formatBengaliNumber(med.stripsPerBox)} | ${if (isBengali) "প্রতি পাতায় পিস: " else "Pieces per strip: "}${formatBengaliNumber(med.pcsPerStrip)}", color = DarkTextSecondary, fontSize = 11.sp)
                    }
                    Text(
                        text = "${if (isBengali) "বর্তমান স্টক: " else "Current Stock: "}${formatMedicineStock(med, isBengali)}",
                        color = DarkAccentGold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = if (isBengali) "নতুন স্টক যোগ করুন (ডিফল্ট: ০):" else "Add New Stock (Default: 0):",
                    color = DarkTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                when (med.type) {
                    "tablet" -> {
                        OutlinedTextField(
                            value = bText,
                            onValueChange = { bText = it },
                            label = { Text(if (isBengali) "নতুন কতটি বক্স যোগ হবে" else "New Boxes to Add") },
                            textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = sText,
                            onValueChange = { sText = it },
                            label = { Text(if (isBengali) "নতুন কতটি পাতা যোগ হবে" else "New Strips to Add") },
                            textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = pText,
                            onValueChange = { pText = it },
                            label = { Text(if (isBengali) "নতুন কতটি পিস যোগ হবে" else "New Pieces to Add") },
                            textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    "syrup" -> {
                        OutlinedTextField(
                            value = botText,
                            onValueChange = { botText = it },
                            label = { Text(if (isBengali) "নতুন কতটি বোতল যোগ হবে" else "New Bottles to Add") },
                            textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    "ointment" -> {
                        OutlinedTextField(
                            value = tubeText,
                            onValueChange = { tubeText = it },
                            label = { Text(if (isBengali) "নতুন কতটি টিউব যোগ হবে" else "New Tubes to Add") },
                            textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    "injection" -> {
                        OutlinedTextField(
                            value = vialText,
                            onValueChange = { vialText = it },
                            label = { Text(if (isBengali) "নতুন কতটি ভায়াল যোগ হবে" else "New Vials to Add") },
                            textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    else -> {
                        OutlinedTextField(
                            value = oQtyText,
                            onValueChange = { oQtyText = it },
                            label = { Text(if (isBengali) "নতুন কত পরিমাণ যোগ হবে" else "New Quantity to Add") },
                            textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (isBengali) "ক্রয় ও বিক্রয় মূল্য পরিবর্তন (ঐচ্ছিক):" else "Change Purchase & Sale Price (Optional):",
                    color = DarkTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                if (med.type == "tablet") {
                    OutlinedTextField(
                        value = salePriceText,
                        onValueChange = { input ->
                            salePriceText = input
                            val sale = input.toDoubleOrNull() ?: initialSalePrice
                            val pct = profitPercentText.toDoubleOrNull()
                            val pur = purchasePriceText.toDoubleOrNull() ?: initialPurchasePrice
                            if (pur > 0.0) {
                                val calculatedPct = ((sale - pur) / pur) * 100.0
                                profitPercentText = String.format(Locale.US, "%.1f", calculatedPct)
                            } else if (pct != null && pct > -100.0) {
                                val calculatedPur = sale / (1.0 + (pct / 100.0))
                                purchasePriceText = String.format(Locale.US, "%.2f", calculatedPur)
                            }
                        },
                        label = { Text(if (isBengali) "নতুন বিক্রয় মূল্য (বক্স প্রতি)" else "New Sale Price (per Box)") },
                        placeholder = { Text(if (isBengali) "পূর্বে: ৳${med.salePriceBox}" else "Prev: ৳${med.salePriceBox}") },
                        textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = profitPercentText,
                        onValueChange = { input ->
                            profitPercentText = input
                            val pct = input.toDoubleOrNull() ?: 0.0
                            val sale = salePriceText.toDoubleOrNull() ?: initialSalePrice
                            if (pct > -100.0 && sale > 0.0) {
                                val pur = sale / (1.0 + (pct / 100.0))
                                purchasePriceText = String.format(Locale.US, "%.2f", pur)
                            }
                        },
                        label = { Text(if (isBengali) "লাভের হার (%) (ঐচ্ছিক)" else "Profit Percentage (%) (Optional)") },
                        textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = purchasePriceText,
                        onValueChange = { input ->
                            purchasePriceText = input
                            val pur = input.toDoubleOrNull() ?: initialPurchasePrice
                            val sale = salePriceText.toDoubleOrNull() ?: initialSalePrice
                            if (pur > 0.0) {
                                val pct = ((sale - pur) / pur) * 100.0
                                profitPercentText = String.format(Locale.US, "%.1f", pct)
                            }
                        },
                        label = { Text(if (isBengali) "নতুন ক্রয় মূল্য (বক্স প্রতি)" else "New Purchase Price (per Box)") },
                        placeholder = { Text(if (isBengali) "পূর্বে: ৳${med.purchasePriceBox}" else "Prev: ৳${med.purchasePriceBox}") },
                        textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                } else {
                    OutlinedTextField(
                        value = salePriceText,
                        onValueChange = { input ->
                            salePriceText = input
                            val sale = input.toDoubleOrNull() ?: initialSalePrice
                            val pct = profitPercentText.toDoubleOrNull()
                            val pur = purchasePriceText.toDoubleOrNull() ?: initialPurchasePrice
                            if (pur > 0.0) {
                                val calculatedPct = ((sale - pur) / pur) * 100.0
                                profitPercentText = String.format(Locale.US, "%.1f", calculatedPct)
                            } else if (pct != null && pct > -100.0) {
                                val calculatedPur = sale / (1.0 + (pct / 100.0))
                                purchasePriceText = String.format(Locale.US, "%.2f", calculatedPur)
                            }
                        },
                        label = { Text(if (isBengali) "নতুন বিক্রয় মূল্য (ইউনিট প্রতি)" else "New Sale Price (per Unit)") },
                        placeholder = { Text(if (isBengali) "পূর্বে: ৳${med.salePriceUnit}" else "Prev: ৳${med.salePriceUnit}") },
                        textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = profitPercentText,
                        onValueChange = { input ->
                            profitPercentText = input
                            val pct = input.toDoubleOrNull() ?: 0.0
                            val sale = salePriceText.toDoubleOrNull() ?: initialSalePrice
                            if (pct > -100.0 && sale > 0.0) {
                                val pur = sale / (1.0 + (pct / 100.0))
                                purchasePriceText = String.format(Locale.US, "%.2f", pur)
                            }
                        },
                        label = { Text(if (isBengali) "লাভের হার (%) (ঐচ্ছিক)" else "Profit Percentage (%) (Optional)") },
                        textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = purchasePriceText,
                        onValueChange = { input ->
                            purchasePriceText = input
                            val pur = input.toDoubleOrNull() ?: initialPurchasePrice
                            val sale = salePriceText.toDoubleOrNull() ?: initialSalePrice
                            if (pur > 0.0) {
                                val pct = ((sale - pur) / pur) * 100.0
                                profitPercentText = String.format(Locale.US, "%.1f", pct)
                            }
                        },
                        label = { Text(if (isBengali) "নতুন ক্রয় মূল্য (ইউনিট প্রতি)" else "New Purchase Price (per Unit)") },
                        placeholder = { Text(if (isBengali) "পূর্বে: ৳${med.purchasePriceUnit}" else "Prev: ৳${med.purchasePriceUnit}") },
                        textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }

                val isPriceWarning = if (med.type == "tablet") {
                    val pur = purchasePriceText.toDoubleOrNull() ?: initialPurchasePrice
                    val sale = salePriceText.toDoubleOrNull() ?: initialSalePrice
                    pur > sale && pur > 0.0 && sale > 0.0
                } else {
                    val pur = purchasePriceText.toDoubleOrNull() ?: initialPurchasePrice
                    val sale = salePriceText.toDoubleOrNull() ?: initialSalePrice
                    pur > sale && pur > 0.0 && sale > 0.0
                }
                if (isPriceWarning) {
                    Text(
                        text = if (isBengali) "⚠️ সতর্কবার্তা: ক্রয় মূল্য বিক্রয় মূল্য থেকে বেশি হতে পারে না!" else "⚠️ Warning: Purchase price cannot exceed sale price!",
                        color = DarkAccentRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                
                // Low Stock Alert threshold section
                Text(
                    text = if (isBengali) "স্টক শেষ হওয়ার সতর্কতা (স্টক অ্যালার্ট):" else "Low Stock Alert Threshold:",
                    color = DarkAccentGold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                // Unit selection buttons
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val units = when (med.type) {
                        "tablet" -> listOf(
                            "boxes" to (if (isBengali) "বক্স" else "Boxes"),
                            "strips" to (if (isBengali) "পাতা" else "Strips"),
                            "pieces" to (if (isBengali) "পিস" else "Pieces")
                        )
                        "syrup" -> listOf(
                            "bottles" to (if (isBengali) "বোতল" else "Bottles")
                        )
                        "ointment" -> listOf(
                            "tubes" to (if (isBengali) "টিউব" else "Tubes")
                        )
                        "injection" -> listOf(
                            "vials" to (if (isBengali) "ভায়াল" else "Vials")
                        )
                        else -> listOf(
                            "other" to (if (isBengali) "অন্যান্য" else "Other")
                        )
                    }

                    units.forEach { (key, label) ->
                        val isSelected = lowStockThresholdUnit == key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (isSelected) DarkAccentRed else DarkCardBg, RoundedCornerShape(8.dp))
                                .border(1.dp, if (isSelected) DarkAccentRed else SleekBorderColor, RoundedCornerShape(8.dp))
                                .clickable { 
                                    lowStockThresholdUnit = key
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) DarkAccentText else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = lowStockThresholdValueStr,
                    onValueChange = { lowStockThresholdValueStr = it },
                    label = { Text(if (isBengali) "সতর্কতা সীমা পরিমাণ" else "Alert Limit Quantity") },
                    textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                val explanationText = remember(med.type, lowStockThresholdUnit, isBengali, lowStockThresholdValueStr) {
                    val vNum = lowStockThresholdValueStr.toIntOrNull() ?: 0
                    if (med.type == "tablet") {
                        val sPerBox = med.stripsPerBox
                        val pPerStrip = med.pcsPerStrip
                        val totalPcs = when (lowStockThresholdUnit) {
                            "boxes" -> vNum * sPerBox * pPerStrip
                            "strips" -> vNum * pPerStrip
                            else -> vNum
                        }
                        if (isBengali) {
                            "সতর্কতা: ${formatBengaliNumber(vNum.toString())} ${
                                when (lowStockThresholdUnit) {
                                    "boxes" -> "বক্স"
                                    "strips" -> "পাতা"
                                    else -> "পিস"
                                }
                            } = মোট ${formatBengaliNumber(totalPcs.toString())} পিস এর নিচে স্টক নামলে অ্যালার্ট দেখাবে।"
                        } else {
                            "Alert: $vNum ${lowStockThresholdUnit.replaceFirstChar { it.uppercase() }} = total $totalPcs pcs. Alert triggers when stock falls below this."
                        }
                    } else {
                        val uName = when (med.type) {
                            "syrup" -> if (isBengali) "বোতল" else "bottles"
                            "ointment" -> if (isBengali) "টিউব" else "tubes"
                            "injection" -> if (isBengali) "ভায়াল" else "vials"
                            else -> if (isBengali) "পিস/ইউনিট" else "pcs/units"
                        }
                        if (isBengali) {
                            "সতর্কতা: ${formatBengaliNumber(vNum.toString())} $uName এর নিচে স্টক নামলে অ্যালার্ট দেখাবে।"
                        } else {
                            "Alert: Triggers when stock falls below $vNum $uName."
                        }
                    }
                }

                Text(
                    text = explanationText,
                    color = DarkAccentGold.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(if (isBengali) "বাতিল" else "Cancel", color = DarkTextSecondary) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = DarkAccentRed, contentColor = DarkAccentText),
                        onClick = {
                            val addBoxes = bText.toIntOrNull() ?: 0
                            val addStrips = sText.toIntOrNull() ?: 0
                            val addPcs = pText.toIntOrNull() ?: 0
                            val addBottles = botText.toIntOrNull() ?: 0
                            val addTubes = tubeText.toIntOrNull() ?: 0
                            val addVials = vialText.toIntOrNull() ?: 0
                            val addOtherQty = oQtyText.toDoubleOrNull() ?: 0.0
                            
                            val nPurchase = purchasePriceText.toDoubleOrNull()
                            val nSale = salePriceText.toDoubleOrNull()

                            val calculatedThreshold = when (med.type) {
                                "tablet" -> {
                                    val sVal = lowStockThresholdValueStr.toIntOrNull() ?: 10
                                    val strPerBox = med.stripsPerBox
                                    val pcsPerStr = med.pcsPerStrip
                                    when (lowStockThresholdUnit) {
                                        "boxes" -> sVal * strPerBox * pcsPerStr
                                        "strips" -> sVal * pcsPerStr
                                        else -> sVal
                                    }
                                }
                                else -> lowStockThresholdValueStr.toIntOrNull() ?: 5
                            }

                            onConfirmed(addBoxes, addStrips, addPcs, addBottles, addTubes, addVials, addOtherQty, nPurchase, nSale, calculatedThreshold)
                        }
                    ) {
                        Text(if (isBengali) "হালনাগাদ করুন" else "Update", color = DarkAccentText)
                    }
                }
            }
        }
    }
}

@Composable
fun QuickPriceChangeDialog(
    med: Medicine,
    isBengali: Boolean,
    onConfirmed: (boxPurchase: Double, boxSale: Double, unitPurchase: Double, unitSale: Double, lowStockThreshold: Int) -> Unit,
    onDismiss: () -> Unit
) {
    var bPText by remember { mutableStateOf(med.purchasePriceBox.toString()) }
    var bSText by remember { mutableStateOf(med.salePriceBox.toString()) }
    var uPText by remember { mutableStateOf(med.purchasePriceUnit.toString()) }
    var uSText by remember { mutableStateOf(med.salePriceUnit.toString()) }

    var profitPercentText by remember {
        val initialPurchase = if (med.type == "tablet") med.purchasePriceBox else med.purchasePriceUnit
        val initialSale = if (med.type == "tablet") med.salePriceBox else med.salePriceUnit
        val initialPct = if (initialPurchase > 0.0) {
            ((initialSale - initialPurchase) / initialPurchase) * 100.0
        } else 0.0
        mutableStateOf(if (initialPct > 0.0) String.format(Locale.US, "%.1f", initialPct) else "")
    }

    var lowStockThresholdValueStr by remember {
        val threshold = med.lowStockThreshold
        val initialVal = if (med.type == "tablet") {
            val sPerBox = med.stripsPerBox
            val pPerStrip = med.pcsPerStrip
            val pcsPerBox = sPerBox * pPerStrip
            if (pcsPerBox > 0 && threshold % pcsPerBox == 0) {
                (threshold / pcsPerBox).toString()
            } else if (pPerStrip > 0 && threshold % pPerStrip == 0) {
                (threshold / pPerStrip).toString()
            } else {
                threshold.toString()
            }
        } else {
            threshold.toString()
        }
        mutableStateOf(initialVal)
    }

    var lowStockThresholdUnit by remember {
        val threshold = med.lowStockThreshold
        val initialUnit = if (med.type == "tablet") {
            val sPerBox = med.stripsPerBox
            val pPerStrip = med.pcsPerStrip
            val pcsPerBox = sPerBox * pPerStrip
            if (pcsPerBox > 0 && threshold % pcsPerBox == 0) {
                "boxes"
            } else if (pPerStrip > 0 && threshold % pPerStrip == 0) {
                "strips"
            } else {
                "pieces"
            }
        } else {
            when (med.type) {
                "syrup" -> "bottles"
                "ointment" -> "tubes"
                "injection" -> "vials"
                else -> "other"
            }
        }
        mutableStateOf(initialUnit)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "${if (isBengali) "দাম ও সতর্কতা পরিবর্তন: " else "Change Price & Alert: "}${med.fullName}",
                    color = DarkAccentGold,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (med.type == "tablet") {
                    OutlinedTextField(
                        value = bSText, 
                        onValueChange = { input -> 
                            bSText = input
                            val sale = input.toDoubleOrNull() ?: 0.0
                            val pct = profitPercentText.toDoubleOrNull()
                            val pur = bPText.toDoubleOrNull() ?: 0.0
                            if (pur > 0.0) {
                                val calculatedPct = ((sale - pur) / pur) * 100.0
                                profitPercentText = String.format(Locale.US, "%.1f", calculatedPct)
                            } else if (pct != null && pct > -100.0) {
                                val calculatedPur = sale / (1.0 + (pct / 100.0))
                                bPText = String.format(Locale.US, "%.2f", calculatedPur)
                            }
                        }, 
                        label = { Text(if (isBengali) "১ বক্স বিক্রয় মূল্য" else "1 Box Sale Price") }, 
                        textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = profitPercentText,
                        onValueChange = { input ->
                            profitPercentText = input
                            val pct = input.toDoubleOrNull() ?: 0.0
                            val sale = bSText.toDoubleOrNull() ?: 0.0
                            if (pct > -100.0 && sale > 0.0) {
                                val pur = sale / (1.0 + (pct / 100.0))
                                bPText = String.format(Locale.US, "%.2f", pur)
                            }
                        },
                        label = { Text(if (isBengali) "লাভের হার (%)" else "Profit Percentage (%)") },
                        textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = bPText, 
                        onValueChange = { input -> 
                            bPText = input
                            val pur = input.toDoubleOrNull() ?: 0.0
                            val sale = bSText.toDoubleOrNull() ?: 0.0
                            if (pur > 0.0) {
                                val pct = ((sale - pur) / pur) * 100.0
                                profitPercentText = String.format(Locale.US, "%.1f", pct)
                            }
                        }, 
                        label = { Text(if (isBengali) "১ বক্স ক্রয় মূল্য" else "1 Box Purchase Price") }, 
                        textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                } else {
                    OutlinedTextField(
                        value = uSText, 
                        onValueChange = { input -> 
                            uSText = input
                            val sale = input.toDoubleOrNull() ?: 0.0
                            val pct = profitPercentText.toDoubleOrNull()
                            val pur = uPText.toDoubleOrNull() ?: 0.0
                            if (pur > 0.0) {
                                val calculatedPct = ((sale - pur) / pur) * 100.0
                                profitPercentText = String.format(Locale.US, "%.1f", calculatedPct)
                            } else if (pct != null && pct > -100.0) {
                                val calculatedPur = sale / (1.0 + (pct / 100.0))
                                uPText = String.format(Locale.US, "%.2f", calculatedPur)
                            }
                        }, 
                        label = { Text(if (isBengali) "বিক্রয় মূল্য (প্রতি পিস/বোতল)" else "Sale price") }, 
                        textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = profitPercentText,
                        onValueChange = { input ->
                            profitPercentText = input
                            val pct = input.toDoubleOrNull() ?: 0.0
                            val sale = uSText.toDoubleOrNull() ?: 0.0
                            if (pct > -100.0 && sale > 0.0) {
                                val pur = sale / (1.0 + (pct / 100.0))
                                uPText = String.format(Locale.US, "%.2f", pur)
                            }
                        },
                        label = { Text(if (isBengali) "লাভের হার (%)" else "Profit Percentage (%)") },
                        textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    OutlinedTextField(
                        value = uPText, 
                        onValueChange = { input -> 
                            uPText = input
                            val pur = input.toDoubleOrNull() ?: 0.0
                            val sale = uSText.toDoubleOrNull() ?: 0.0
                            if (pur > 0.0) {
                                val pct = ((sale - pur) / pur) * 100.0
                                profitPercentText = String.format(Locale.US, "%.1f", pct)
                            }
                        }, 
                        label = { Text(if (isBengali) "ক্রয় মূল্য (প্রতি পিস/বোতল)" else "Purchase price") }, 
                        textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }

                val isPriceWarning = if (med.type == "tablet") {
                    val pur = bPText.toDoubleOrNull() ?: 0.0
                    val sale = bSText.toDoubleOrNull() ?: 0.0
                    pur > sale && pur > 0.0 && sale > 0.0
                } else {
                    val pur = uPText.toDoubleOrNull() ?: 0.0
                    val sale = uSText.toDoubleOrNull() ?: 0.0
                    pur > sale && pur > 0.0 && sale > 0.0
                }
                if (isPriceWarning) {
                    Text(
                        text = if (isBengali) "⚠️ সতর্কবার্তা: ক্রয় মূল্য বিক্রয় মূল্য থেকে বেশি হতে পারে না!" else "⚠️ Warning: Purchase price cannot exceed sale price!",
                        color = DarkAccentRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                
                // Low Stock Alert threshold section
                Text(
                    text = if (isBengali) "স্টক শেষ হওয়ার সতর্কতা (স্টক অ্যালার্ট):" else "Low Stock Alert Threshold:",
                    color = DarkAccentGold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                // Unit selection buttons
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val units = when (med.type) {
                        "tablet" -> listOf(
                            "boxes" to (if (isBengali) "বক্স" else "Boxes"),
                            "strips" to (if (isBengali) "পাতা" else "Strips"),
                            "pieces" to (if (isBengali) "পিস" else "Pieces")
                        )
                        "syrup" -> listOf(
                            "bottles" to (if (isBengali) "বোতল" else "Bottles")
                        )
                        "ointment" -> listOf(
                            "tubes" to (if (isBengali) "টিউব" else "Tubes")
                        )
                        "injection" -> listOf(
                            "vials" to (if (isBengali) "ভায়াল" else "Vials")
                        )
                        else -> listOf(
                            "other" to (if (isBengali) "অন্যান্য" else "Other")
                        )
                    }

                    units.forEach { (key, label) ->
                        val isSelected = lowStockThresholdUnit == key
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (isSelected) DarkAccentRed else DarkCardBg, RoundedCornerShape(8.dp))
                                .border(1.dp, if (isSelected) DarkAccentRed else SleekBorderColor, RoundedCornerShape(8.dp))
                                .clickable { 
                                    lowStockThresholdUnit = key
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) DarkAccentText else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = lowStockThresholdValueStr,
                    onValueChange = { lowStockThresholdValueStr = it },
                    label = { Text(if (isBengali) "সতর্কতা সীমা পরিমাণ" else "Alert Limit Quantity") },
                    textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                val explanationText = remember(med.type, lowStockThresholdUnit, isBengali, lowStockThresholdValueStr) {
                    val vNum = lowStockThresholdValueStr.toIntOrNull() ?: 0
                    if (med.type == "tablet") {
                        val sPerBox = med.stripsPerBox
                        val pPerStrip = med.pcsPerStrip
                        val totalPcs = when (lowStockThresholdUnit) {
                            "boxes" -> vNum * sPerBox * pPerStrip
                            "strips" -> vNum * pPerStrip
                            else -> vNum
                        }
                        if (isBengali) {
                            "সতর্কতা: ${formatBengaliNumber(vNum.toString())} ${
                                when (lowStockThresholdUnit) {
                                    "boxes" -> "বক্স"
                                    "strips" -> "পাতা"
                                    else -> "পিস"
                                }
                            } = মোট ${formatBengaliNumber(totalPcs.toString())} পিস এর নিচে স্টক নামলে অ্যালার্ট দেখাবে।"
                        } else {
                            "Alert: $vNum ${lowStockThresholdUnit.replaceFirstChar { it.uppercase() }} = total $totalPcs pcs. Alert triggers when stock falls below this."
                        }
                    } else {
                        val uName = when (med.type) {
                            "syrup" -> if (isBengali) "বোতল" else "bottles"
                            "ointment" -> if (isBengali) "টিউব" else "tubes"
                            "injection" -> if (isBengali) "ভায়াল" else "vials"
                            else -> if (isBengali) "পিস/ইউনিট" else "pcs/units"
                        }
                        if (isBengali) {
                            "সতর্কতা: ${formatBengaliNumber(vNum.toString())} $uName এর নিচে স্টক নামলে অ্যালার্ট দেখাবে।"
                        } else {
                            "Alert: Triggers when stock falls below $vNum $uName."
                        }
                    }
                }

                Text(
                    text = explanationText,
                    color = DarkAccentGold.copy(alpha = 0.9f),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(if (isBengali) "বাতিল" else "Cancel", color = DarkTextSecondary) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = DarkAccentRed, contentColor = DarkAccentText),
                        onClick = {
                            val calculatedThreshold = when (med.type) {
                                "tablet" -> {
                                    val sVal = lowStockThresholdValueStr.toIntOrNull() ?: 10
                                    val strPerBox = med.stripsPerBox
                                    val pcsPerStr = med.pcsPerStrip
                                    when (lowStockThresholdUnit) {
                                        "boxes" -> sVal * strPerBox * pcsPerStr
                                        "strips" -> sVal * pcsPerStr
                                        else -> sVal
                                    }
                                }
                                else -> lowStockThresholdValueStr.toIntOrNull() ?: 5
                            }
                            onConfirmed(
                                bPText.toDoubleOrNull() ?: 0.0,
                                bSText.toDoubleOrNull() ?: 0.0,
                                uPText.toDoubleOrNull() ?: 0.0,
                                uSText.toDoubleOrNull() ?: 0.0,
                                calculatedThreshold
                            )
                        }
                    ) {
                        Text(if (isBengali) "হালনাগাদ করুন" else "Change", color = DarkAccentText)
                    }
                }
            }
        }
    }
}

