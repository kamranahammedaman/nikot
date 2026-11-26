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
fun formatBengaliNumber(number: Any): String {
    val numberStr = number.toString()
    val engDigits = listOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')
    val benDigits = listOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    val map = engDigits.zip(benDigits).toMap()
    return numberStr.map { map[it] ?: it }.joinToString("")
}

fun formatBengaliDate(timestamp: Long): String {
    val date = Date(timestamp)
    val cal = Calendar.getInstance().apply { time = date }
    val day = cal.get(Calendar.DAY_OF_MONTH)
    val month = cal.get(Calendar.MONTH) // 0-indexed
    val year = cal.get(Calendar.YEAR)
    
    val bnMonths = listOf(
        "জানুয়ারি", "ফেব্রুয়ারি", "মার্চ", "এপ্রিল", "মে", "জুন",
        "জুলাই", "আগস্ট", "সেপ্টেম্বর", "অক্টোবর", "নভেম্বর", "ডিসেম্বর"
    )
    val monthName = bnMonths.getOrElse(month) { "জুন" }
    
    val englishDateStr = "$day $monthName $year"
    return formatBengaliNumber(englishDateStr)
}

fun formatBengaliTime(timestamp: Long): String {
    val date = Date(timestamp)
    val cal = Calendar.getInstance().apply { time = date }
    val hour = cal.get(Calendar.HOUR) // 12-hour format
    val displayedHour = if (hour == 0) 12 else hour
    val minute = cal.get(Calendar.MINUTE)
    val amPm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
    
    val hourStr = formatBengaliNumber(displayedHour)
    val minStr = formatBengaliNumber(String.format("%02d", minute))
    return "$hourStr:$minStr $amPm"
}

fun formatItemQuantity(item: BillItem, isBengali: Boolean): String {
    val parts = mutableListOf<String>()
    
    val qtyBox = item.soldBoxes
    val qtyStrip = item.soldStrips
    val qtyPc = item.soldPcs
    val qtyBottle = item.soldBottles
    val qtyTube = item.soldTubes
    val qtyVial = item.soldVials
    val qtyOther = item.soldOtherQty

    if (item.type == "tablet") {
        if (qtyBox > 0) parts.add("${if (isBengali) formatBengaliNumber(qtyBox) else qtyBox} ${if (isBengali) "বক্স" else "box"}")
        if (qtyStrip > 0) parts.add("${if (isBengali) formatBengaliNumber(qtyStrip) else qtyStrip} ${if (isBengali) "পাতা" else "strip"}")
        if (qtyPc > 0) parts.add("${if (isBengali) formatBengaliNumber(qtyPc) else qtyPc} ${if (isBengali) "পিস" else "pc"}")
    } else {
        if (qtyBottle > 0) parts.add("${if (isBengali) formatBengaliNumber(qtyBottle) else qtyBottle} ${if (isBengali) "বোতল" else "bottle"}")
        if (qtyTube > 0) parts.add("${if (isBengali) formatBengaliNumber(qtyTube) else qtyTube} ${if (isBengali) "টিউব" else "tube"}")
        if (qtyVial > 0) parts.add("${if (isBengali) formatBengaliNumber(qtyVial) else qtyVial} ${if (isBengali) "ভায়াল" else "vial"}")
        if (qtyOther > 0.0) parts.add("${if (isBengali) formatBengaliNumber(String.format("%.1f", qtyOther)) else qtyOther} Unit")
    }
    return if (parts.isEmpty()) (if (isBengali) "০" else "0") else parts.joinToString(" ")
}

fun formatNetQuantity(original: BillItem, returned: BillItem, isBengali: Boolean): String {
    val reqBoxes = (original.soldBoxes - returned.soldBoxes).coerceAtLeast(0)
    val reqStrips = (original.soldStrips - returned.soldStrips).coerceAtLeast(0)
    val reqPcs = (original.soldPcs - returned.soldPcs).coerceAtLeast(0)
    val reqBottles = (original.soldBottles - returned.soldBottles).coerceAtLeast(0)
    val reqTubes = (original.soldTubes - returned.soldTubes).coerceAtLeast(0)
    val reqVials = (original.soldVials - returned.soldVials).coerceAtLeast(0)
    val reqOther = (original.soldOtherQty - returned.soldOtherQty).coerceAtLeast(0.0)

    val parts = mutableListOf<String>()
    if (original.type == "tablet") {
        if (reqBoxes > 0) parts.add("${if (isBengali) formatBengaliNumber(reqBoxes) else reqBoxes} ${if (isBengali) "বক্স" else "box"}")
        if (reqStrips > 0) parts.add("${if (isBengali) formatBengaliNumber(reqStrips) else reqStrips} ${if (isBengali) "পাতা" else "strip"}")
        if (reqPcs > 0) parts.add("${if (isBengali) formatBengaliNumber(reqPcs) else reqPcs} ${if (isBengali) "পিস" else "pc"}")
    } else {
        if (reqBottles > 0) parts.add("${if (isBengali) formatBengaliNumber(reqBottles) else reqBottles} ${if (isBengali) "বোতল" else "bottle"}")
        if (reqTubes > 0) parts.add("${if (isBengali) formatBengaliNumber(reqTubes) else reqTubes} ${if (isBengali) "টিউব" else "tube"}")
        if (reqVials > 0) parts.add("${if (isBengali) formatBengaliNumber(reqVials) else reqVials} ${if (isBengali) "ভায়াল" else "vial"}")
        if (reqOther > 0.0) parts.add("${if (isBengali) formatBengaliNumber(String.format("%.1f", reqOther)) else reqOther} Unit")
    }
    return if (parts.isEmpty()) {
        if (isBengali) "০" else "0"
    } else {
        parts.joinToString(" ")
    }
}

fun formatMedicineStock(m: Medicine, isBengali: Boolean): String {
    return when (m.type) {
        "tablet" -> {
            val sBox = if (isBengali) formatBengaliNumber(m.boxesInStock) else m.boxesInStock.toString()
            val sStrip = if (isBengali) formatBengaliNumber(m.stripsInStock) else m.stripsInStock.toString()
            val sPc = if (isBengali) formatBengaliNumber(m.pcsInStock) else m.pcsInStock.toString()
            if (isBengali) "💊 $sBox বক্স | $sStrip পাতা | $sPc পিস" else "💊 $sBox Box | $sStrip Strip | $sPc Pcs"
        }
        "syrup" -> {
            val sBot = if (isBengali) formatBengaliNumber(m.bottlesInStock) else m.bottlesInStock.toString()
            if (isBengali) "🧴 $sBot বোতল (${formatBengaliNumber(m.mlPerBottle)} ML)" else "🧴 $sBot Bottles (${m.mlPerBottle} ML)"
        }
        "ointment" -> {
            val sTube = if (isBengali) formatBengaliNumber(m.tubesInStock) else m.tubesInStock.toString()
            if (isBengali) "🟡 $sTube টিউব (${formatBengaliNumber(m.gramsPerTube)} গ্রাম)" else "🟡 $sTube Tubes (${m.gramsPerTube} Grams)"
        }
        "injection" -> {
            val sVial = if (isBengali) formatBengaliNumber(m.vialsInStock) else m.vialsInStock.toString()
            if (isBengali) "💉 $sVial ভায়াল" else "💉 $sVial Vials"
        }
        else -> {
            val sQty = if (isBengali) formatBengaliNumber(m.otherQuantity) else m.otherQuantity.toString()
            if (isBengali) "📦 $sQty ${m.otherUnit}" else "📦 $sQty ${m.otherUnit}"
        }
    }
}

fun formatStockInLog(r: StockInRecord, isBengali: Boolean): String {
    return when (r.medicineType) {
        "tablet" -> {
            val parts = mutableListOf<String>()
            if (r.addedBoxes > 0) parts.add(if (isBengali) "${formatBengaliNumber(r.addedBoxes)} বক্স" else "${r.addedBoxes} Box(es)")
            if (r.addedStrips > 0) parts.add(if (isBengali) "${formatBengaliNumber(r.addedStrips)} পাতা" else "${r.addedStrips} Strip(s)")
            if (r.addedPcs > 0) parts.add(if (isBengali) "${formatBengaliNumber(r.addedPcs)} পিস" else "${r.addedPcs} Pc(s)")
            if (parts.isEmpty()) {
                if (isBengali) "০ পিস" else "0 Pcs"
            } else {
                parts.joinToString(" ")
            }
        }
        "syrup" -> {
            if (isBengali) "${formatBengaliNumber(r.addedBottles)} বোতল" else "${r.addedBottles} Bottle(s)"
        }
        "ointment" -> {
            if (isBengali) "${formatBengaliNumber(r.addedTubes)} টিউব" else "${r.addedTubes} Tube(s)"
        }
        "injection" -> {
            if (isBengali) "${formatBengaliNumber(r.addedVials)} ভায়াল" else "${r.addedVials} Vial(s)"
        }
        else -> {
            val q = if (r.addedOtherQty == r.addedOtherQty.toInt().toDouble()) r.addedOtherQty.toInt().toString() else r.addedOtherQty.toString()
            if (isBengali) "${formatBengaliNumber(q)} পিস" else "$q Pcs"
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
fun formatCurrency(amount: Double, isBengali: Boolean): String {
    val formatted = try {
        val df = if (amount % 1.0 == 0.0) {
            java.text.DecimalFormat("#,##,##0")
        } else {
            java.text.DecimalFormat("#,##,##0.00")
        }
        df.format(amount)
    } catch (_: Exception) {
        if (amount % 1.0 == 0.0) {
            String.format("%.0f", amount)
        } else {
            String.format("%.2f", amount)
        }
    }
    return if (isBengali) formatBengaliNumber(formatted) else formatted
}

fun getPeriodLabelBangali(period: String): String {
    if (period.startsWith("month_")) {
        try {
            val parts = period.split("_")
            val year = parts[1]
            val month = parts[2].toInt()
            val monthName = when (month) {
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
            return "$monthName ${formatBengaliNumber(year.toInt())}"
        } catch (e: Exception) {
            return period
        }
    }
    return when(period) {
        "today" -> "আজ"
        "week" -> "এই সপ্তাহ"
        "month" -> "এই মাস"
        else -> "সব সময়"
    }
}

fun getPeriodLabelEnglish(period: String): String {
    if (period.startsWith("month_")) {
        try {
            val parts = period.split("_")
            val year = parts[1]
            val month = parts[2].toInt()
            val monthName = when (month) {
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
            }
            return "$monthName $year"
        } catch (e: Exception) {
            return period
        }
    }
    return when(period) {
        "today" -> "Today"
        "week" -> "This Week"
        "month" -> "This Month"
        else -> "All Time"
    }
}

fun isToday(timestamp: Long): Boolean {
    val target = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val today = java.util.Calendar.getInstance()
    return target.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) &&
           target.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR)
}

fun formatDateTime(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

