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
var isDarkModeGlobal by mutableStateOf(true)

// Custom Dark Theme Colors - Sleek Interface supporting Light and Dark modes (Optimized for Nikot logo)
val DarkBg: Color @Composable get() = if (isDarkModeGlobal) Color(0xFF05171A) else Color(0xFFF2F8F9)
val DarkSurface: Color @Composable get() = if (isDarkModeGlobal) Color(0xFF0C2429) else Color(0xFFFFFFFF)
val DarkCardBg: Color @Composable get() = if (isDarkModeGlobal) Color(0xFF13363E) else Color(0xFFE5F1F3)
val DarkAccentRed: Color @Composable get() = if (isDarkModeGlobal) Color(0xFF1EB4C9) else Color(0xFF0C7F90) // Vibrant Teal/Cyan from Nikot logo
val DarkAccentText: Color @Composable get() = if (isDarkModeGlobal) Color(0xFF05171A) else Color.White // High contrast text on top of Accent Red
val DarkAccentGold: Color @Composable get() = if (isDarkModeGlobal) Color(0xFF42D0E4) else Color(0xFF1594A8) // Bright Cyan from Nikot logo
val DarkTextPrimary: Color @Composable get() = if (isDarkModeGlobal) Color(0xFFE5F5F7) else Color(0xFF061E22)
val DarkTextSecondary: Color @Composable get() = if (isDarkModeGlobal) Color(0xFF8BB1B8) else Color(0xFF4B6E75)
val SleekBorderColor: Color @Composable get() = if (isDarkModeGlobal) Color(0xFF18454D).copy(alpha = 0.4f) else Color(0xFFD0E5E8)

// ---- Semantic status colors (single source of truth, consistent across every screen) ----
val SuccessColor: Color @Composable get() = if (isDarkModeGlobal) Color(0xFF34D399) else Color(0xFF189065)
val WarningColor: Color @Composable get() = if (isDarkModeGlobal) Color(0xFFFBBF4A) else Color(0xFFB8740A)
val InfoColor: Color @Composable get() = if (isDarkModeGlobal) Color(0xFF38BDF8) else Color(0xFF0C7F90)
val DangerColor: Color @Composable get() = if (isDarkModeGlobal) Color(0xFFFF6B6B) else Color(0xFFD1413B)
val PremiumGoldGradient: Brush @Composable get() = Brush.linearGradient(listOf(Color(0xFFE6C77A), Color(0xFFBF9A4E)))

/** Soft ambient drop-shadow that reads as "premium" without the harsh default Material shadow. */
