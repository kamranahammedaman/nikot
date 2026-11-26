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
fun StaffUnlinkedPharmacyScreen(
    isBengali: Boolean,
    onGoToSettings: () -> Unit,
    onAddPharmacy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .background(DarkAccentGold.copy(alpha = 0.12f), CircleShape)
                .border(2.dp, DarkAccentGold.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Locked",
                tint = DarkAccentGold,
                modifier = Modifier.size(46.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = if (isBengali) "ফার্মেসী সংযুক্ত নেই" else "No Pharmacy Linked",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = DarkTextPrimary,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isBengali)
                "স্টাফ পোর্টালে আসার পর আপনি এখনো কোনো মার্চেন্ট ফার্মেসীর সাথে যুক্ত হননি। তাই ড্যাশবোর্ড, বিক্রয়, ইনভেন্টরি ও রিপোর্ট পেইজগুলো কাজ করবে না।"
            else
                "You are not connected to any merchant pharmacy yet. Dashboard, Sales, Inventory, and Reports pages remain inactive.",
            fontSize = 13.5.sp,
            color = DarkTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.dp, SleekBorderColor),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (isBengali) "কীভাবে ফার্মেসীর এক্সেস পাবেন?" else "How to gain pharmacy access?",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkAccentGold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Text("১. ", color = DarkTextSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (isBengali) "নিচের 'ফার্মেসী এড' বাটনে ক্লিক করুন অথবা সেটিংসে যান।" else "Click 'Add Pharmacy' below or navigate to Settings.",
                        color = DarkTextSecondary,
                        fontSize = 12.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Text("২. ", color = DarkTextSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (isBengali) "ফার্মেসীর নাম এবং মার্চেন্টের সিকিউরিটি কোড দিন।" else "Enter Pharmacy Name and Merchant Security Code.",
                        color = DarkTextSecondary,
                        fontSize = 12.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Text("৩. ", color = DarkTextSecondary, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (isBengali) "তথ্য মিললে তাৎক্ষণিকভাবে পূর্ণ এক্সেস পেয়ে যাবেন।" else "Upon match, gain instant full access to all pages.",
                        color = DarkTextSecondary,
                        fontSize = 12.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onAddPharmacy,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = SuccessColor,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isBengali) "ফার্মেসী এড করুন" else "Add Pharmacy",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onGoToSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DarkTextPrimary),
            border = BorderStroke(1.dp, SleekBorderColor),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp), tint = DarkAccentGold)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (isBengali) "সেটিংস-এ যান" else "Go to Settings",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun StaffAddPharmacyDialog(
    viewModel: PharmacyViewModel,
    isBengali: Boolean,
    onDismiss: () -> Unit
) {
    var pharmacyNameInput by remember { mutableStateOf("") }
    var securityCodeInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.2.dp, SuccessColor.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(SuccessColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalPharmacy,
                                contentDescription = null,
                                tint = SuccessColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isBengali) "ফার্মেসী এড" else "Add Pharmacy",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkTextPrimary
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = DarkTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (isBengali)
                        "যে ফার্মেসীর সাথে যুক্ত হতে চান তার নাম এবং মার্চেন্টের সিকিউরিটি কোড দিন। সঠিক হলে আপনি সাথে সাথে পূর্ণ এক্সেস পাবেন।"
                    else
                        "Enter the Pharmacy Name and Merchant Security Code. When verified, you will immediately gain full access.",
                    color = DarkTextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Field 1: Pharmacy Name
                OutlinedTextField(
                    value = pharmacyNameInput,
                    onValueChange = {
                        pharmacyNameInput = it
                        errorMessage = ""
                    },
                    label = { Text(if (isBengali) "ফার্মেসীর নাম" else "Pharmacy Name") },
                    placeholder = { Text(if (isBengali) "যেমন: নিকট ফার্মেসী" else "e.g. Nikot Pharmacy", color = DarkTextSecondary.copy(alpha = 0.5f)) },
                    leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null, tint = DarkAccentGold) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SuccessColor,
                        unfocusedBorderColor = SleekBorderColor,
                        focusedLabelColor = SuccessColor,
                        unfocusedLabelColor = DarkTextSecondary
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Field 2: Security Code
                OutlinedTextField(
                    value = securityCodeInput,
                    onValueChange = {
                        securityCodeInput = it
                        errorMessage = ""
                    },
                    label = { Text(if (isBengali) "সিকিউরিটি কোড" else "Security Code") },
                    placeholder = { Text(if (isBengali) "যেমন: #ROPH-3003 বা 3003" else "e.g. #ROPH-3003", color = DarkTextSecondary.copy(alpha = 0.5f)) },
                    leadingIcon = { Icon(Icons.Default.VpnKey, contentDescription = null, tint = DarkAccentGold) },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SuccessColor,
                        unfocusedBorderColor = SleekBorderColor,
                        focusedLabelColor = SuccessColor,
                        unfocusedLabelColor = DarkTextSecondary
                    )
                )

                if (errorMessage.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "⚠️ $errorMessage",
                        color = DarkAccentRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(if (isBengali) "বাতিল" else "Cancel", color = DarkTextSecondary)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (pharmacyNameInput.isBlank()) {
                                errorMessage = if (isBengali) "অনুগ্রহ করে ফার্মেসীর নাম লিখুন।" else "Please enter pharmacy name."
                                return@Button
                            }
                            if (securityCodeInput.isBlank()) {
                                errorMessage = if (isBengali) "অনুগ্রহ করে সিকিউরিটি কোড লিখুন।" else "Please enter security code."
                                return@Button
                            }
                            isSubmitting = true
                            viewModel.connectStaffToPharmacy(
                                pharmName = pharmacyNameInput.trim(),
                                secCode = securityCodeInput.trim()
                            ) { success, msg ->
                                isSubmitting = false
                                if (success) {
                                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                                    onDismiss()
                                } else {
                                    errorMessage = msg
                                }
                            }
                        },
                        enabled = !isSubmitting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SuccessColor,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = if (isBengali) "যুক্ত হোন ও এক্সেস নিন" else "Connect & Gain Access",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }
                }
            }
        }
    }
}

// FORMAT UTIL
