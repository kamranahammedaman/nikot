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
fun BarcodeScannerDialog(
    allMedicines: List<Medicine>,
    isBengali: Boolean,
    onSelectBarcode: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    var torchOn by remember { mutableStateOf(false) }
    var showManualEntry by remember { mutableStateOf(false) }
    var manualCode by remember { mutableStateOf("") }
    var hasScanned by remember { mutableStateOf(false) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    LaunchedEffect(torchOn) {
        try {
            cameraRef?.cameraControl?.enableTorch(torchOn)
        } catch (e: Exception) { /* torch not supported on this device */ }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            } catch (e: Exception) { /* camera already released */ }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

            if (cameraPermissionState.status.isGranted && !showManualEntry) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val scanner = BarcodeScanning.getClient()
                            val imageAnalysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build()
                            imageAnalysis.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                                val mediaImage = imageProxy.image
                                if (mediaImage != null) {
                                    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                    scanner.process(inputImage)
                                        .addOnSuccessListener { barcodes ->
                                            val value = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                                            if (!value.isNullOrBlank() && !hasScanned) {
                                                hasScanned = true
                                                onSelectBarcode(value)
                                            }
                                        }
                                        .addOnCompleteListener { imageProxy.close() }
                                } else {
                                    imageProxy.close()
                                }
                            }
                            try {
                                cameraProvider.unbindAll()
                                cameraRef = cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    imageAnalysis
                                )
                            } catch (e: Exception) { /* no camera available on this device */ }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    }
                )

                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)))

                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        GlassIconButton(onClick = onDismiss, icon = Icons.Filled.ArrowBack, contentDescription = "Back")
                        Text(
                            if (isBengali) "বারকোড স্ক্যান করুন" else "Scan Barcode",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        GlassIconButton(
                            onClick = { torchOn = !torchOn },
                            icon = Icons.Filled.FlashOn,
                            contentDescription = "Flash",
                            tint = if (torchOn) DarkAccentGold else Color.White
                        )
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        ScannerViewfinderFrame(accent = DarkAccentGold)
                        Text(
                            if (isBengali) "বারকোডটি ফ্রেমের মধ্যে রাখুন" else "Align the barcode within the frame",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 90.dp)
                        )
                    }

                    GlassPanel(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp),
                        strong = true
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    if (isBengali) "সাম্প্রতিক বারকোড" else "Recent barcodes",
                                    color = DarkTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = { showManualEntry = true }) {
                                    Text(
                                        if (isBengali) "ম্যানুয়াল এন্ট্রি" else "Enter manually",
                                        color = DarkAccentGold,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            val recent = allMedicines.filter { it.barcode.isNotBlank() }.take(3)
                            if (recent.isEmpty()) {
                                Text(
                                    if (isBengali) "কোনো বারকোডযুক্ত ওষুধ নেই" else "No barcoded items yet",
                                    color = DarkTextSecondary,
                                    fontSize = 11.sp
                                )
                            } else {
                                recent.forEach { med ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(med.name, color = DarkTextPrimary, fontSize = 12.5.sp)
                                        Text(med.barcode, color = DarkAccentGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Permission not granted yet, or the user chose manual entry.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(GlassBackgroundGradient)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = null,
                        tint = DarkAccentGold,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (!cameraPermissionState.status.isGranted)
                            (if (isBengali) "বারকোড স্ক্যান করতে ক্যামেরার অনুমতি দিন" else "Camera permission is needed to scan barcodes")
                        else
                            (if (isBengali) "বারকোড নম্বর লিখুন" else "Enter the barcode number"),
                        color = DarkTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (!cameraPermissionState.status.isGranted) {
                        Button(
                            onClick = { cameraPermissionState.launchPermissionRequest() },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkAccentRed, contentColor = DarkAccentText)
                        ) {
                            Text(if (isBengali) "অনুমতি দিন" else "Grant permission", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                    OutlinedTextField(
                        value = manualCode,
                        onValueChange = { manualCode = it },
                        label = { Text(if (isBengali) "বারকোড" else "Barcode") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                            Text(if (isBengali) "বাতিল" else "Cancel")
                        }
                        Button(
                            onClick = { if (manualCode.isNotBlank()) onSelectBarcode(manualCode.trim()) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = DarkAccentRed, contentColor = DarkAccentText)
                        ) {
                            Text(if (isBengali) "নিশ্চিত করুন" else "Confirm", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (allMedicines.any { it.barcode.isNotBlank() }) {
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            if (isBengali) "অথবা তালিকা থেকে বেছে নিন:" else "Or pick from inventory:",
                            color = DarkTextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        LazyColumn(modifier = Modifier.height(130.dp)) {
                            items(allMedicines.filter { it.barcode.isNotBlank() }) { med ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelectBarcode(med.barcode) }
                                        .padding(vertical = 6.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(med.name, color = DarkTextPrimary, fontSize = 13.sp)
                                    Text(med.barcode, color = DarkAccentGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                                Divider(color = Color.LightGray.copy(alpha = 0.1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Animated glass viewfinder frame: four corner brackets + a sweeping scan line. */
@Composable
private fun ScannerViewfinderFrame(accent: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "scanline")
    val lineProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scanlineProgress"
    )
    Box(modifier = Modifier.size(250.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val corner = 28.dp.toPx()
            val strokeW = 4.dp.toPx()
            val w = size.width
            val h = size.height
            drawLine(accent, androidx.compose.ui.geometry.Offset(0f, corner), androidx.compose.ui.geometry.Offset(0f, 0f), strokeW, StrokeCap.Round)
            drawLine(accent, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(corner, 0f), strokeW, StrokeCap.Round)
            drawLine(accent, androidx.compose.ui.geometry.Offset(w - corner, 0f), androidx.compose.ui.geometry.Offset(w, 0f), strokeW, StrokeCap.Round)
            drawLine(accent, androidx.compose.ui.geometry.Offset(w, 0f), androidx.compose.ui.geometry.Offset(w, corner), strokeW, StrokeCap.Round)
            drawLine(accent, androidx.compose.ui.geometry.Offset(0f, h - corner), androidx.compose.ui.geometry.Offset(0f, h), strokeW, StrokeCap.Round)
            drawLine(accent, androidx.compose.ui.geometry.Offset(0f, h), androidx.compose.ui.geometry.Offset(corner, h), strokeW, StrokeCap.Round)
            drawLine(accent, androidx.compose.ui.geometry.Offset(w - corner, h), androidx.compose.ui.geometry.Offset(w, h), strokeW, StrokeCap.Round)
            drawLine(accent, androidx.compose.ui.geometry.Offset(w, h), androidx.compose.ui.geometry.Offset(w, h - corner), strokeW, StrokeCap.Round)
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(y = (lineProgress * 220).dp)
                .fillMaxWidth()
                .height(2.dp)
                .background(accent.copy(alpha = 0.85f))
        )
    }
}

@Composable
fun CartQuantityInputDialog(
    medicine: Medicine,
    isBengali: Boolean,
    onConfirm: (boxes: Int, strips: Int, pcs: Int, bottles: Int, tubes: Int, vials: Int, otherQty: Double) -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // states
    var tBoxText by remember { mutableStateOf("0") }
    var tStripText by remember { mutableStateOf("0") }
    var tPcText by remember { mutableStateOf("0") }
    var sBotText by remember { mutableStateOf("1") }
    var oTubText by remember { mutableStateOf("1") }
    var iVialText by remember { mutableStateOf("1") }
    var othQtyText by remember { mutableStateOf("1") }

    // Handlers for reactive clamping
    fun handleTabletChange(b: String, s: String, p: String) {
        val boxVal = b.toIntOrNull() ?: 0
        val stripVal = s.toIntOrNull() ?: 0
        val pcVal = p.toIntOrNull() ?: 0
        
        val totalRequested = (boxVal * medicine.stripsPerBox * medicine.pcsPerStrip) +
                             (stripVal * medicine.pcsPerStrip) + pcVal
        val maxAvailable = medicine.totalPiecesInStock
        
        if (totalRequested > maxAvailable) {
            val maxBoxes = maxAvailable / (medicine.stripsPerBox * medicine.pcsPerStrip)
            val remAfterB = maxAvailable % (medicine.stripsPerBox * medicine.pcsPerStrip)
            val maxStrips = remAfterB / medicine.pcsPerStrip
            val maxPcs = remAfterB % medicine.pcsPerStrip
            
            tBoxText = maxBoxes.toString()
            tStripText = maxStrips.toString()
            tPcText = maxPcs.toString()
            
            android.widget.Toast.makeText(
                context,
                if (isBengali) "স্টকে মাত্র ${formatBengaliNumber(medicine.boxesInStock)} বক্স ${formatBengaliNumber(medicine.stripsInStock)} পাতা ${formatBengaliNumber(medicine.pcsInStock)} পিস আছে"
                else "Only ${medicine.boxesInStock} Box(es) ${medicine.stripsInStock} Strip(s) ${medicine.pcsInStock} Piece(s) in stock",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } else {
            tBoxText = b
            tStripText = s
            tPcText = p
        }
    }

    fun handleSyrupChange(valStr: String) {
        if (valStr.isEmpty()) {
            sBotText = ""
            return
        }
        val n = valStr.toIntOrNull() ?: 0
        if (n > medicine.bottlesInStock) {
            sBotText = medicine.bottlesInStock.toString()
            android.widget.Toast.makeText(
                context,
                if (isBengali) "স্টকে মাত্র ${formatBengaliNumber(medicine.bottlesInStock)} বোতল আছে"
                else "Only ${medicine.bottlesInStock} bottle(s) in stock",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } else {
            sBotText = valStr
        }
    }

    fun handleOintmentChange(valStr: String) {
        if (valStr.isEmpty()) {
            oTubText = ""
            return
        }
        val n = valStr.toIntOrNull() ?: 0
        if (n > medicine.tubesInStock) {
            oTubText = medicine.tubesInStock.toString()
            android.widget.Toast.makeText(
                context,
                if (isBengali) "স্টকে মাত্র ${formatBengaliNumber(medicine.tubesInStock)} টিউব আছে"
                else "Only ${medicine.tubesInStock} tube(s) in stock",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } else {
            oTubText = valStr
        }
    }

    fun handleInjectionChange(valStr: String) {
        if (valStr.isEmpty()) {
            iVialText = ""
            return
        }
        val n = valStr.toIntOrNull() ?: 0
        if (n > medicine.vialsInStock) {
            iVialText = medicine.vialsInStock.toString()
            android.widget.Toast.makeText(
                context,
                if (isBengali) "স্টকে মাত্র ${formatBengaliNumber(medicine.vialsInStock)} ভায়াল আছে"
                else "Only ${medicine.vialsInStock} vial(s) in stock",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        } else {
            iVialText = valStr
        }
    }

    fun handleOtherChange(valStr: String) {
        if (valStr.isEmpty()) {
            othQtyText = ""
            return
        }
        val d = valStr.toDoubleOrNull() ?: 0.0
        if (d > medicine.otherQuantity) {
            val unit = medicine.otherUnit.ifBlank { if (isBengali) "পিস" else "Pcs" }
            android.widget.Toast.makeText(
                context,
                if (isBengali) "স্টকে মাত্র ${formatBengaliNumber(medicine.otherQuantity)} $unit আছে"
                else "Only ${medicine.otherQuantity} $unit in stock",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            val checkInt = medicine.otherQuantity.toInt()
            othQtyText = if (medicine.otherQuantity == checkInt.toDouble()) checkInt.toString() else medicine.otherQuantity.toString()
        } else {
            othQtyText = valStr
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = medicine.fullName,
                    color = DarkAccentGold,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${if (isBengali) "ধরন: " else "Type: "}${medicine.type.uppercase()}",
                    color = DarkTextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                // Current available stock segment
                val stockDisplay = when (medicine.type) {
                    "tablet" -> {
                        val sBox = if (isBengali) formatBengaliNumber(medicine.boxesInStock) else medicine.boxesInStock.toString()
                        val sStrip = if (isBengali) formatBengaliNumber(medicine.stripsInStock) else medicine.stripsInStock.toString()
                        val sPc = if (isBengali) formatBengaliNumber(medicine.pcsInStock) else medicine.pcsInStock.toString()
                        if (isBengali) "স্টকে আছে: $sBox বক্স | $sStrip পাতা | $sPc পিস" else "In Stock: $sBox Box | $sStrip Strip | $sPc Piece(s)"
                    }
                    "syrup" -> {
                        val sBot = if (isBengali) formatBengaliNumber(medicine.bottlesInStock) else medicine.bottlesInStock.toString()
                        if (isBengali) "স্টকে আছে: $sBot বোতল" else "In Stock: $sBot Bottle(s)"
                    }
                    "ointment" -> {
                        val sTube = if (isBengali) formatBengaliNumber(medicine.tubesInStock) else medicine.tubesInStock.toString()
                        if (isBengali) "স্টকে আছে: $sTube টিউব" else "In Stock: $sTube Tube(s)"
                    }
                    "injection" -> {
                        val sVial = if (isBengali) formatBengaliNumber(medicine.vialsInStock) else medicine.vialsInStock.toString()
                        if (isBengali) "স্টকে আছে: $sVial ভায়াল" else "In Stock: $sVial Vial(s)"
                    }
                    else -> {
                        val sQty = if (isBengali) formatBengaliNumber(medicine.otherQuantity) else medicine.otherQuantity.toString()
                        val unit = medicine.otherUnit.ifBlank { if (isBengali) "পিস" else "Pcs" }
                        if (isBengali) "স্টকে আছে: $sQty $unit" else "In Stock: $sQty $unit"
                    }
                }
                Text(
                    text = stockDisplay,
                    color = DarkAccentRed,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Render dynamic inputs
                when (medicine.type) {
                    "tablet" -> {
                        Text(if (isBengali) "বিক্রয়ের পরিমাণ নির্ধারণ করুন:" else "Set Sold Quantities:", color = DarkTextPrimary, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = tBoxText,
                                onValueChange = { handleTabletChange(it, tStripText, tPcText) },
                                label = { Text(if (isBengali) "বক্স" else "Box") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(2.dp),
                                textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary)
                            )
                            OutlinedTextField(
                                value = tStripText,
                                onValueChange = { handleTabletChange(tBoxText, it, tPcText) },
                                label = { Text(if (isBengali) "পাতা" else "Strip") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(2.dp),
                                textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary)
                            )
                            OutlinedTextField(
                                value = tPcText,
                                onValueChange = { handleTabletChange(tBoxText, tStripText, it) },
                                label = { Text(if (isBengali) "পিস" else "Piece") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(2.dp),
                                textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary)
                            )
                        }
                    }
                    "syrup" -> {
                        OutlinedTextField(
                            value = sBotText,
                            onValueChange = { handleSyrupChange(it) },
                            label = { Text(if (isBengali) "বোতলের সংখ্যা" else "Bottles Count") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary)
                        )
                    }
                    "ointment" -> {
                        OutlinedTextField(
                            value = oTubText,
                            onValueChange = { handleOintmentChange(it) },
                            label = { Text(if (isBengali) "টিউবের সংখ্যা" else "Tubes Count") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary)
                        )
                    }
                    "injection" -> {
                        OutlinedTextField(
                            value = iVialText,
                            onValueChange = { handleInjectionChange(it) },
                            label = { Text(if (isBengali) "ভায়ালের সংখ্যা" else "Vials Count") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary)
                        )
                    }
                    else -> { // other
                        OutlinedTextField(
                            value = othQtyText,
                            onValueChange = { handleOtherChange(it) },
                            label = { Text(if (isBengali) "পরিমাণ" else "Quantity") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Real-time calculation mockup visualizer
                val calculatedPriceTotal = when (medicine.type) {
                    "tablet" -> {
                        val boxes = tBoxText.toIntOrNull() ?: 0
                        val strips = tStripText.toIntOrNull() ?: 0
                        val pcs = tPcText.toIntOrNull() ?: 0
                        (boxes * medicine.salePriceBox) +
                        (strips * medicine.salePriceStrip) +
                        (pcs * medicine.salePricePiece)
                    }
                    "syrup" -> (sBotText.toIntOrNull() ?: 0) * medicine.salePriceUnit
                    "ointment" -> (oTubText.toIntOrNull() ?: 0) * medicine.salePriceUnit
                    "injection" -> (iVialText.toIntOrNull() ?: 0) * medicine.salePriceUnit
                    else -> (othQtyText.toDoubleOrNull() ?: 0.0) * medicine.salePriceUnit
                }

                Text(
                    text = "${if (isBengali) "মোট টাকা: ৳" else "Total Price: ৳"}${if (isBengali) formatBengaliNumber(String.format("%.2f", calculatedPriceTotal)) else String.format("%.2f", calculatedPriceTotal)}",
                    color = DarkAccentGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.End)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(if (isBengali) "বাতিল" else "Cancel", color = DarkTextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = DarkAccentRed, contentColor = DarkAccentText),
                        onClick = {
                            val boxesVal = tBoxText.toIntOrNull() ?: 0
                            val stripsVal = tStripText.toIntOrNull() ?: 0
                            val pcsVal = tPcText.toIntOrNull() ?: 0
                            val bottlesVal = sBotText.toIntOrNull() ?: 0
                            val tubesVal = oTubText.toIntOrNull() ?: 0
                            val vialsVal = iVialText.toIntOrNull() ?: 0
                            val otherQtyVal = othQtyText.toDoubleOrNull() ?: 0.0

                            onConfirm(boxesVal, stripsVal, pcsVal, bottlesVal, tubesVal, vialsVal, otherQtyVal)
                        }
                    ) {
                        Text(if (isBengali) "যোগ করুন" else "Add to Cart", color = DarkAccentText)
                    }
                }
            }
        }
    }
}

@Composable
fun BillPreviewDialog(
    viewModel: PharmacyViewModel,
    billNumber: String,
    cartItems: List<PharmacyViewModel.CartItem>,
    totalPrice: Double,
    isBengali: Boolean,
    onConfirmed: () -> Unit,
    onDismiss: () -> Unit
) {
    val pharmacyName by viewModel.pharmacyName.collectAsState()
    val address by viewModel.pharmacyAddress.collectAsState()
    val phone by viewModel.pharmacyPhone.collectAsState()

    val now = System.currentTimeMillis()
    val dateStr = if (isBengali) {
        "${formatBengaliDate(now)} ${formatBengaliTime(now)}"
    } else {
        SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.US).format(Date(now))
    }

    var discountText by remember { mutableStateOf("") }
    val discountVal = discountText.toDoubleOrNull() ?: 0.0
    val netTotal = (totalPrice - discountVal).coerceAtLeast(0.0)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            shape = RoundedCornerShape(16.dp),
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
                // Receipt Header
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
                    Divider(color = Color.LightGray.copy(alpha = 0.2f))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Metadata
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = "${if (isBengali) "বিল নম্বর: " else "Bill No: "}$billNumber", color = DarkTextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(text = dateStr, color = DarkTextSecondary, fontSize = 11.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = Color.LightGray.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))

                // Scrollable Medicine List
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(cartItems) { item ->
                        val qtyDesc = when (item.medicine.type) {
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

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(0.7f)) {
                                Text(item.medicine.fullName, color = DarkTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(qtyDesc, color = DarkTextSecondary, fontSize = 11.sp)
                            }
                            Text(
                                text = "৳${if (isBengali) formatBengaliNumber(String.format("%.2f", item.totalPrice)) else String.format("%.2f", item.totalPrice)}",
                                color = DarkTextPrimary,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(0.3f),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                
                // Discount Input field
                OutlinedTextField(
                    value = discountText,
                    onValueChange = { input ->
                        if (input.isEmpty() || (input.all { it.isDigit() } && input.length <= 6)) {
                            discountText = input
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (isBengali) "ছাড় (৳)" else "Discount (৳)", color = DarkAccentGold, fontSize = 12.sp) },
                    placeholder = { Text("0", color = Color.White.copy(alpha = 0.4f)) },
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

                Spacer(modifier = Modifier.height(8.dp))
                Divider(color = Color.LightGray.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(8.dp))

                // Receipt price summation
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(if (isBengali) "মোট:" else "Total:", color = DarkTextSecondary, fontSize = 13.sp)
                        Text(
                            "৳${if (isBengali) formatBengaliNumber(String.format("%.2f", totalPrice)) else String.format("%.2f", totalPrice)}",
                            color = DarkTextPrimary,
                            fontSize = 13.sp
                        )
                    }
                    if (discountVal > 0.0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (isBengali) "ছাড়:" else "Discount:", color = DarkAccentRed, fontSize = 13.sp)
                            Text(
                                "৳${if (isBengali) formatBengaliNumber(String.format("%.2f", discountVal)) else String.format("%.2f", discountVal)} ${if (isBengali) "(বিয়োগ)" else "(less)"}",
                                color = DarkAccentRed,
                                fontSize = 13.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (isBengali) "নেট মোট:" else "Net Total:", color = DarkTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "৳${if (isBengali) formatBengaliNumber(String.format("%.2f", netTotal)) else String.format("%.2f", netTotal)}",
                            color = DarkAccentGold,
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Confirm/Cancel buttons row
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
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkTextPrimary)
                    ) {
                        Text(if (isBengali) "❌ বাতিল" else "❌ Cancel")
                    }
                    Button(
                        onClick = {
                            viewModel.confirmSale(billNumber, discountVal) {
                                onConfirmed()
                            }
                        },
                        modifier = Modifier
                            .weight(1.2f)
                            .height(44.dp)
                            .padding(start = 4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DarkAccentRed, contentColor = DarkAccentText)
                    ) {
                        Text(if (isBengali) "✅ বিল কনফার্ম করুন" else "✅ Confirm Bill", color = DarkAccentText, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
