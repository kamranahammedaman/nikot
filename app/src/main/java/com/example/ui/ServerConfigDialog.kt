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
fun ServerConfigDialog(
    viewModel: PharmacyViewModel,
    isBengali: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isConnected by viewModel.isOnlineSyncConnected.collectAsState()
    val syncMessage by viewModel.firebaseSyncMessage.collectAsState()
    val isTesting by viewModel.isTestingConnection.collectAsState()
    val customDbUrl by viewModel.customFirebaseDatabaseUrl.collectAsState()
    val customApiKey by viewModel.customFirebaseApiKey.collectAsState()
    val customProjectId by viewModel.customFirebaseProjectId.collectAsState()

    var inputUrl by remember { mutableStateOf(if (customDbUrl.isNotBlank()) customDbUrl else viewModel.getEffectiveDatabaseUrl()) }
    var inputApiKey by remember { mutableStateOf(customApiKey) }
    var inputProjectId by remember { mutableStateOf(customProjectId) }
    var statusText by remember { mutableStateOf("") }
    var isSuccess by remember { mutableStateOf<Boolean?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = BorderStroke(1.5.dp, if (isConnected) Color(0xFF00E676) else DarkAccentGold),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(DarkAccentGold.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Dns,
                                contentDescription = null,
                                tint = DarkAccentGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isBengali) "সার্ভার সেটিংস / এড" else "Server Settings",
                            color = DarkTextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = DarkTextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Connection Status Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isConnected) Color(0xFF00E676).copy(alpha = 0.12f) else DarkAccentRed.copy(alpha = 0.12f)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isConnected) Color(0xFF00E676).copy(alpha = 0.4f) else DarkAccentRed.copy(alpha = 0.4f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(if (isConnected) Color(0xFF00E676) else DarkAccentRed, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isConnected) {
                                    if (isBengali) "সার্ভার সংযুক্ত ও সক্রিয় (Online)" else "Server Connected & Online"
                                } else {
                                    if (isBengali) "সার্ভার এর সাথে কানেক্টেড না (Offline)" else "Not Connected to Server (Offline)"
                                },
                                color = if (isConnected) Color(0xFF00E676) else DarkAccentRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = syncMessage,
                                color = DarkTextSecondary,
                                fontSize = 11.5.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Instructions Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkBg),
                    border = BorderStroke(1.dp, SleekBorderColor),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (isBengali) "💡 নতুন ক্লাউড সার্ভার এড করার নিয়ম:" else "💡 How to add your Firebase server:",
                            color = DarkAccentGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isBengali)
                                "১. আপনার Firebase Console (firebase.google.com)-এ যান।\n২. 'Realtime Database' তৈরি করুন এবং ডাটাবেজ URL নিচে দিন।\n৩. Rules ট্যাবে \".read\": true ও \".write\": true দিয়ে Publish করুন।"
                            else
                                "1. Open your Firebase Console (firebase.google.com)\n2. Create 'Realtime Database' & paste the Database URL below.\n3. In Rules tab, set \".read\": true and \".write\": true, then Publish.",
                            color = DarkTextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Database URL input
                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = {
                        inputUrl = it
                        statusText = ""
                        isSuccess = null
                    },
                    label = { Text(if (isBengali) "সার্ভার ডাটাবেজ URL (Database URL) *" else "Database URL *") },
                    placeholder = { Text("https://your-db.asia-southeast1.firebasedatabase.app", fontSize = 11.sp, color = DarkTextSecondary.copy(alpha = 0.5f)) },
                    textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary, fontSize = 12.5.sp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DarkAccentGold,
                        unfocusedBorderColor = SleekBorderColor,
                        focusedLabelColor = DarkAccentGold,
                        unfocusedLabelColor = DarkTextSecondary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // API Key input (Optional)
                OutlinedTextField(
                    value = inputApiKey,
                    onValueChange = {
                        inputApiKey = it
                        statusText = ""
                        isSuccess = null
                    },
                    label = { Text(if (isBengali) "Firebase API Key (ঐচ্ছিক / Optional)" else "API Key (Optional)") },
                    placeholder = { Text("AIzaSy...", fontSize = 11.sp, color = DarkTextSecondary.copy(alpha = 0.5f)) },
                    textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary, fontSize = 12.5.sp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DarkAccentGold,
                        unfocusedBorderColor = SleekBorderColor,
                        focusedLabelColor = DarkAccentGold,
                        unfocusedLabelColor = DarkTextSecondary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Project ID input (Optional)
                OutlinedTextField(
                    value = inputProjectId,
                    onValueChange = {
                        inputProjectId = it
                        statusText = ""
                        isSuccess = null
                    },
                    label = { Text(if (isBengali) "Firebase Project ID (ঐচ্ছিক / Optional)" else "Project ID (Optional)") },
                    placeholder = { Text("my-pharmacy-app", fontSize = 11.sp, color = DarkTextSecondary.copy(alpha = 0.5f)) },
                    textStyle = LocalTextStyle.current.copy(color = DarkTextPrimary, fontSize = 12.5.sp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DarkAccentGold,
                        unfocusedBorderColor = SleekBorderColor,
                        focusedLabelColor = DarkAccentGold,
                        unfocusedLabelColor = DarkTextSecondary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                if (statusText.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = statusText,
                        color = if (isSuccess == true) Color(0xFF00E676) else DarkAccentRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Save & Connect Button
                Button(
                    onClick = {
                        if (inputUrl.trim().isEmpty()) {
                            statusText = if (isBengali) "দয়া করে সার্ভার ডাটাবেজ URL লিখুন!" else "Please enter Database URL!"
                            isSuccess = false
                        } else {
                            viewModel.setCustomServerConfig(
                                databaseUrl = inputUrl.trim(),
                                apiKey = inputApiKey.trim(),
                                projectId = inputProjectId.trim()
                            ) { ok, msg ->
                                isSuccess = ok
                                statusText = msg
                                if (ok) {
                                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    enabled = !isTesting,
                    colors = ButtonDefaults.buttonColors(containerColor = DarkAccentGold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isBengali) "কানেক্ট করা হচ্ছে..." else "Connecting...",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(Icons.Default.CloudSync, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isBengali) "সার্ভার সেভ ও কানেক্ট করুন" else "Save & Connect Server",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Reconnect & Reset Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.reconnectFirebase { ok, msg ->
                                isSuccess = ok
                                statusText = msg
                            }
                        },
                        enabled = !isTesting,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, SleekBorderColor)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = DarkTextPrimary, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isBengali) "টেস্ট ও রিফ্রেশ" else "Test Ping",
                            color = DarkTextPrimary,
                            fontSize = 11.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.resetDefaultServer { ok, msg ->
                                inputUrl = viewModel.getEffectiveDatabaseUrl()
                                inputApiKey = ""
                                inputProjectId = ""
                                isSuccess = ok
                                statusText = if (isBengali) "ডিফল্ট সার্ভার রিসেট হয়েছে" else "Reset to default server"
                            }
                        },
                        enabled = !isTesting,
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, SleekBorderColor)
                    ) {
                        Text(
                            text = if (isBengali) "ডিফল্ট রিসেট" else "Reset Default",
                            color = DarkTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}



