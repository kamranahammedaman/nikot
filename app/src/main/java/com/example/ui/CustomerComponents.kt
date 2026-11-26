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
fun CustomerMedicineCard(
    medGroup: CustomerGroupedMedicine,
    isBengali: Boolean,
    userProfiles: Map<String, PharmacyViewModel.UserProfile>,
    currentLat: Double?,
    currentLon: Double?,
    onAddToCart: (Medicine) -> Unit,
    onOrderNow: (Medicine, SimulatedPharmacy) -> Unit
) {
    var showPharmacies by remember(medGroup.groupKey) { mutableStateOf(false) }

    val inStockOfferings = remember(medGroup) {
        medGroup.pharmacyOfferings.filter { med ->
            when (med.type.lowercase().trim()) {
                "tablet" -> med.totalPiecesInStock > 0
                "syrup" -> med.bottlesInStock > 0
                "ointment" -> med.tubesInStock > 0
                "injection" -> med.vialsInStock > 0
                else -> med.otherQuantity > 0.0
            }
        }
    }
    val isAvailable = inStockOfferings.isNotEmpty()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = BorderStroke(1.dp, if (isAvailable) DarkAccentGold.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Medicine Name, Dosage, Generic & Stock Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = medGroup.name + (if (medGroup.dosageStrength.isNotEmpty()) " ${medGroup.dosageStrength}" else ""),
                        color = DarkTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${medGroup.genericName} • ${medGroup.type}",
                        color = DarkTextSecondary,
                        fontSize = 12.5.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .background(
                            color = if (isAvailable) SuccessColor.copy(alpha = 0.15f) else DarkAccentRed.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isAvailable) {
                            if (isBengali) "স্টকে আছে (${formatBengaliNumber(inStockOfferings.size)}টি ফার্মেসী)" else "In Stock (${inStockOfferings.size} Pharmacies)"
                        } else {
                            if (isBengali) "স্টক শেষ" else "Out of Stock"
                        },
                        color = if (isAvailable) SuccessColor else DarkAccentGold,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // PRICING SECTION - BOX, STRIP, PIECE
            // যে ঔষধ সার্চ করবে সুধু ঔষধ দেখাবে এবং বক্স এর দাম, পাতার দাম ও প্রতি পিস
            if (medGroup.type.lowercase().trim() == "tablet") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Box Price
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = DarkBg.copy(alpha = 0.7f)),
                        border = BorderStroke(1.dp, SleekBorderColor),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (isBengali) "📦 বক্সের দাম" else "📦 Box Price",
                                color = DarkTextSecondary,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "৳${formatBengaliNumber(String.format("%.1f", medGroup.salePriceBox))}",
                                color = DarkAccentGold,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Strip Price
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = DarkBg.copy(alpha = 0.7f)),
                        border = BorderStroke(1.dp, SleekBorderColor),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (isBengali) "📄 পাতার দাম" else "📄 Strip Price",
                                color = DarkTextSecondary,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "৳${formatBengaliNumber(String.format("%.1f", medGroup.salePriceStrip))}",
                                color = DarkAccentGold,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Per Piece Price
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = DarkBg.copy(alpha = 0.7f)),
                        border = BorderStroke(1.dp, SleekBorderColor),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (isBengali) "💊 প্রতি পিস" else "💊 Per Piece",
                                color = DarkTextSecondary,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "৳${formatBengaliNumber(String.format("%.1f", medGroup.salePricePiece))}",
                                color = DarkAccentGold,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                val unitSuffix = when (medGroup.type.lowercase().trim()) {
                    "syrup" -> if (isBengali) "প্রতি বোতল" else "per bottle"
                    "ointment" -> if (isBengali) "প্রতি টিউব" else "per tube"
                    "injection" -> if (isBengali) "প্রতি ভায়াল" else "per vial"
                    else -> if (isBengali) "প্রতি পিস" else "per pc"
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DarkBg.copy(alpha = 0.7f)),
                    border = BorderStroke(1.dp, SleekBorderColor),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${if (isBengali) "মূল্য (" else "Price ("}$unitSuffix):",
                            color = DarkTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "৳${formatBengaliNumber(String.format("%.1f", medGroup.salePriceUnit))}",
                            color = DarkAccentGold,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ACTION BUTTONS: Pharmacy view & Quick Add to Cart
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showPharmacies = !showPharmacies },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (showPharmacies) DarkAccentRed else Color.White
                    ),
                    border = BorderStroke(1.dp, if (showPharmacies) DarkAccentRed else SleekBorderColor),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(38.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = if (showPharmacies) Icons.Default.ArrowDropUp else Icons.Default.LocalPharmacy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (showPharmacies) {
                            if (isBengali) "ফার্মেসী লুকান" else "Hide Stores"
                        } else {
                            if (isBengali) "ফার্মেসী তালিকা (${formatBengaliNumber(medGroup.pharmacyOfferings.size)})" else "Stores (${medGroup.pharmacyOfferings.size})"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = {
                        val target = inStockOfferings.firstOrNull() ?: medGroup.pharmacyOfferings.first()
                        onAddToCart(target)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAvailable) SuccessColor else Color.Gray.copy(alpha = 0.4f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).height(38.dp),
                    enabled = isAvailable,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Filled.ShoppingCart, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isBengali) "কার্টে যোগ করুন" else "Add to Cart",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // PHARMACIES EXPANDABLE SECTION
            if (showPharmacies) {
                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = Color.White.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isBengali) "যেসব ফার্মেসীতে এই ওষুধ পাওয়া যাচ্ছে:" else "Pharmacies carrying this medicine:",
                    color = DarkAccentGold,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    medGroup.pharmacyOfferings.forEach { pharmMed ->
                        val pProfile = userProfiles[pharmMed.pharmacyOwnerUsername]
                        val pName = pProfile?.pharmacyName ?: (if (pharmMed.pharmacyOwnerUsername == "admin") "আমার ফার্মেসী" else pharmMed.pharmacyOwnerUsername)
                        val pAddr = pProfile?.address ?: "সিলেট সিটি"
                        val pLat = pProfile?.latitude ?: 24.9015
                        val pLng = pProfile?.longitude ?: 91.8683

                        val dist = if (currentLat != null && currentLon != null) {
                            val latDiff = pLat - currentLat
                            val lonDiff = pLng - currentLon
                            val rawDist = Math.sqrt(latDiff * latDiff + lonDiff * lonDiff) * 111.0f
                            if (rawDist <= 0.05) 0.15 else rawDist
                        } else null

                        val stock = when (pharmMed.type.lowercase().trim()) {
                            "tablet" -> pharmMed.totalPiecesInStock
                            "syrup" -> pharmMed.bottlesInStock
                            "ointment" -> pharmMed.tubesInStock
                            "injection" -> pharmMed.vialsInStock
                            else -> pharmMed.otherQuantity.toInt()
                        }
                        val hasStock = stock > 0

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = DarkBg.copy(alpha = 0.6f)),
                            border = BorderStroke(1.dp, if (hasStock) SuccessColor.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .background(if (hasStock) SuccessColor else DarkAccentRed, RoundedCornerShape(50.dp))
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(pName, color = DarkTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        if (dist != null) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "• ${formatBengaliNumber(String.format("%.1f", dist))} কি.মি.",
                                                color = InfoColor,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (hasStock) {
                                            if (isBengali) "স্টকে আছে (${pharmMed.formatQuantityString(stock, true)})" else "In stock (${pharmMed.formatQuantityString(stock, false)})"
                                        } else {
                                            if (isBengali) "স্টক শেষ" else "Out of stock"
                                        },
                                        color = if (hasStock) SuccessColor else DarkAccentRed,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { onAddToCart(pharmMed) },
                                        enabled = hasStock,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.ShoppingCart,
                                            contentDescription = "Add to cart",
                                            tint = if (hasStock) SuccessColor else Color.Gray.copy(alpha = 0.4f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            val pObj = SimulatedPharmacy(
                                                name = pName,
                                                nameEn = pName,
                                                lat = pLat,
                                                lLng = pLng,
                                                stockAvailable = hasStock,
                                                baseDistanceKm = dist ?: 0.0,
                                                description = pAddr,
                                                descriptionEn = pAddr,
                                                ownerUsername = pharmMed.pharmacyOwnerUsername
                                            )
                                            onOrderNow(pharmMed, pObj)
                                        },
                                        enabled = hasStock,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (hasStock) DarkAccentRed else Color.Gray.copy(alpha = 0.3f),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text(if (isBengali) "অর্ডার" else "Order", fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
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

