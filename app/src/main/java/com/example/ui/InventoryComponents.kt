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
fun FilterTabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) DarkAccentRed else DarkSurface)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = if (selected) DarkAccentText else DarkTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MedicineCardItem(
    med: Medicine,
    isBengali: Boolean,
    isAdmin: Boolean = false,
    onUpdateStock: () -> Unit,
    onChangePrice: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCardBg),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, SleekBorderColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Title block
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(med.fullName, color = DarkTextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("${med.genericName} | ${med.company}", color = DarkTextSecondary, fontSize = 11.sp)
                    Text("${if (isBengali) "বারকোড: " else "Barcode: "}${med.barcode} | ${if (isBengali) "মেয়াদ: " else "Exp: "}${med.expiryDate}", color = DarkTextSecondary, fontSize = 11.sp)
                }
                
                if (isAdmin) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Delete, "delete item", tint = DarkAccentRed.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = Color.LightGray.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(10.dp))

            // stock summary line
            Text(
                formatMedicineStock(med, isBengali),
                color = DarkTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(4.dp))

            // purchase/sales display breakdown
            if (med.type == "tablet") {
                val pB = if (isBengali) formatBengaliNumber(med.purchasePriceBox) else med.purchasePriceBox.toString()
                val pS = if (isBengali) formatBengaliNumber(String.format("%.2f", med.purchasePriceStrip)) else String.format("%.2f", med.purchasePriceStrip)
                val pP = if (isBengali) formatBengaliNumber(String.format("%.2f", med.purchasePricePiece)) else String.format("%.2f", med.purchasePricePiece)

                val sB = if (isBengali) formatBengaliNumber(med.salePriceBox) else med.salePriceBox.toString()
                val sS = if (isBengali) formatBengaliNumber(String.format("%.2f", med.salePriceStrip)) else String.format("%.2f", med.salePriceStrip)
                val sP = if (isBengali) formatBengaliNumber(String.format("%.2f", med.salePricePiece)) else String.format("%.2f", med.salePricePiece)

                val profitP = if (isBengali) formatBengaliNumber(String.format("%.2f", med.profitPerPiece)) else String.format("%.2f", med.profitPerPiece)
                val profitPct = if (isBengali) formatBengaliNumber(String.format("%.1f", med.profitPercentageTablet)) else String.format("%.1f", med.profitPercentageTablet)

                Column {
                    Text(
                        "${if (isBengali) "ক্রয়: বক্স ৳" else "Buy: Box ৳"}$pB | ${if (isBengali) "পাতা ৳" else "Strip ৳"}$pS | ${if (isBengali) "পিস ৳" else "Pc ৳"}$pP",
                        fontSize = 11.sp,
                        color = DarkTextSecondary
                    )
                    Text(
                        "${if (isBengali) "বিক্রয়: বক্স ৳" else "Sell: Box ৳"}$sB | ${if (isBengali) "পাতা ৳" else "Strip ৳"}$sS | ${if (isBengali) "পিস ৳" else "Pc ৳"}$sP",
                        fontSize = 11.sp,
                        color = DarkTextSecondary
                    )
                    Text(
                        "${if (isBengali) "লাভ: পিস প্রতি ৳" else "Profit: Per Pc ৳"}$profitP ($profitPct%)",
                        fontSize = 11.sp,
                        color = Color.Green,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                val buyU = if (isBengali) formatBengaliNumber(med.purchasePriceUnit) else med.purchasePriceUnit.toString()
                val sellU = if (isBengali) formatBengaliNumber(med.salePriceUnit) else med.salePriceUnit.toString()
                val profitU = if (isBengali) formatBengaliNumber(String.format("%.2f", med.profitPerUnit)) else String.format("%.2f", med.profitPerUnit)
                val profitPct = if (isBengali) formatBengaliNumber(String.format("%.1f", med.profitPercentageUnit)) else String.format("%.1f", med.profitPercentageUnit)

                Column {
                    Text("${if (isBengali) "ক্রয়: প্রতি ইউনিট ৳" else "Buy: Per Unit ৳"}$buyU | ${if (isBengali) "বিক্রয়: প্রতি ইউনিট ৳" else "Sell: Per Unit ৳"}$sellU", fontSize = 11.sp, color = DarkTextSecondary)
                    Text("${if (isBengali) "লাভ: ইউনিট প্রতি ৳" else "Profit: Per Unit ৳"}$profitU ($profitPct%)", fontSize = 11.sp, color = Color.Green, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Update items configuration action triggers
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onUpdateStock,
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .padding(end = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkTextPrimary)
                ) {
                    Icon(Icons.Filled.Edit, "edit stock", modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isBengali) "স্টক আপডেট" else "Update Stock", fontSize = 10.sp)
                }
                OutlinedButton(
                    onClick = onChangePrice,
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .padding(start = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkTextPrimary)
                ) {
                    Icon(Icons.Filled.Edit, "price config", modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isBengali) "দাম পরিবর্তন" else "Change Price", fontSize = 10.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
