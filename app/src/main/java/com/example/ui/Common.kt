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
fun Modifier.premiumShadow(radius: Dp = 18.dp, shape: Shape = RoundedCornerShape(20.dp), alpha: Float = 0.22f): Modifier =
    this.shadow(elevation = radius, shape = shape, ambientColor = Color.Black.copy(alpha = alpha), spotColor = Color.Black.copy(alpha = alpha))

// ---- Glass UI tokens (glassmorphism overlay system, premium minimal look) ----
val GlassFill: Color @Composable get() = if (isDarkModeGlobal) Color.White.copy(alpha = 0.055f) else Color.White.copy(alpha = 0.62f)
val GlassFillStrong: Color @Composable get() = if (isDarkModeGlobal) Color.White.copy(alpha = 0.09f) else Color.White.copy(alpha = 0.78f)
val GlassBorder: Color @Composable get() = if (isDarkModeGlobal) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.9f)
val GlassBackgroundGradient: Brush @Composable get() = if (isDarkModeGlobal)
    Brush.linearGradient(listOf(Color(0xFF040F12), Color(0xFF0A2228), Color(0xFF0F2E33)))
else
    Brush.linearGradient(listOf(Color(0xFFF2F8F9), Color(0xFFE9F3F4), Color(0xFFDDEEF0)))
val GlassAccentGradient: Brush @Composable get() = Brush.linearGradient(listOf(DarkAccentRed, DarkAccentGold))

/**
 * A frosted translucent panel: the standard building block for the glass UI.
 * Lets whatever gradient/background sits behind it show through softly.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    strong: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val fill = if (strong) GlassFillStrong else GlassFill
    Box(
        modifier = modifier
            .premiumShadow(radius = 14.dp, shape = shape, alpha = if (isDarkModeGlobal) 0.28f else 0.10f)
            .clip(shape)
            .background(fill, shape)
            .border(1.dp, GlassBorder, shape),
        content = content
    )
}

/** A small circular frosted-glass icon button, used in glass top bars / floating controls. */
@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    tint: Color = Color.White,
    size: Dp = 40.dp
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.12f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

val LocalUploadedLogo = staticCompositionLocalOf<String?> { null }

@Composable
fun rememberBitmapFromBase64(base64String: String?): ImageBitmap? {
    return remember(base64String) {
        if (base64String == null) return@remember null
        try {
            val cleanBase64 = base64String.substringAfter("base64,")
            val decodedBytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            bitmap?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
}

@Composable
fun RoLogoHeader(
    modifier: Modifier = Modifier,
    height: Dp = 36.dp
) {
    val logoBase64 = LocalUploadedLogo.current
    val bitmap = rememberBitmapFromBase64(logoBase64)

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = "Uploaded Logo",
            modifier = modifier
                .height(height)
                .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat(), matchHeightConstraintsFirst = true)
                .clip(RoundedCornerShape(6.dp))
        )
    } else {
        // Plain text "NIKOT" default styling
        Box(
            modifier = modifier
                .height(height)
                .background(Color.White, shape = RoundedCornerShape(8.dp))
                .padding(horizontal = (height.value * 0.35f).dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                NikotLogo(
                    modifier = Modifier
                        .size((height.value * 0.70f).dp)
                        .padding(end = (height.value * 0.08f).dp),
                    color = DarkAccentRed
                )
                Text(
                    text = "NIKOT",
                    color = Color(0xFF071C20),
                    fontSize = (height.value * 0.42f).sp,
                    fontWeight = FontWeight.ExtraBold,
                    style = androidx.compose.ui.text.TextStyle(
                        letterSpacing = 0.5.sp
                    )
                )
            }
        }
    }
}

@Composable
fun NikotLogo(modifier: Modifier = Modifier, color: Color = DarkAccentGold) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val minS = minOf(w, h)
        val s = minS / 108f // 108x108 coordinate viewport matching launcher icon
        
        // 1. Deep Navy Base Pin (#0C2429)
        val basePin = Path().apply {
            moveTo(54f * s, 14f * s)
            cubicTo(32f * s, 14f * s, 14f * s, 32f * s, 14f * s, 54f * s)
            cubicTo(14f * s, 83f * s, 54f * s, 102f * s, 54f * s, 102f * s)
            cubicTo(54f * s, 102f * s, 94f * s, 83f * s, 94f * s, 54f * s)
            cubicTo(94f * s, 32f * s, 76f * s, 14f * s, 54f * s, 14f * s)
            close()
        }
        drawPath(basePin, color = Color(0xFF0C2429))

        // 2. Right Sweeping Crescent in Cyan (#1EB4C9)
        val rightSweep = Path().apply {
            moveTo(54f * s, 14f * s)
            cubicTo(76f * s, 14f * s, 94f * s, 32f * s, 94f * s, 54f * s)
            cubicTo(94f * s, 83f * s, 54f * s, 102f * s, 54f * s, 102f * s)
            cubicTo(68f * s, 88f * s, 84f * s, 72f * s, 84f * s, 54f * s)
            cubicTo(84f * s, 36f * s, 72f * s, 14f * s, 54f * s, 14f * s)
            close()
        }
        drawPath(rightSweep, color = Color(0xFF1EB4C9))

        // 3. Accent Light Cyan Sweep (#42D0E4)
        val accentSweep = Path().apply {
            moveTo(54f * s, 14f * s)
            cubicTo(66f * s, 14f * s, 78f * s, 26f * s, 78f * s, 44f * s)
            cubicTo(78f * s, 58f * s, 54f * s, 80f * s, 54f * s, 80f * s)
            cubicTo(60f * s, 70f * s, 68f * s, 58f * s, 68f * s, 44f * s)
            cubicTo(68f * s, 30f * s, 62f * s, 14f * s, 54f * s, 14f * s)
            close()
        }
        drawPath(accentSweep, color = Color(0xFF42D0E4))

        // 4. Inner White Circle Cutout
        val innerWhiteCutout = Path().apply {
            moveTo(54f * s, 30f * s)
            cubicTo(63.9f * s, 30f * s, 72f * s, 38.1f * s, 72f * s, 48f * s)
            cubicTo(72f * s, 57.9f * s, 63.9f * s, 66f * s, 54f * s, 66f * s)
            cubicTo(44.1f * s, 66f * s, 36f * s, 57.9f * s, 36f * s, 48f * s)
            cubicTo(36f * s, 38.1f * s, 44.1f * s, 30f * s, 54f * s, 30f * s)
            close()
        }
        drawPath(innerWhiteCutout, color = Color.White)

        // 5. Shopping Bag Handle in Medium Teal (#0C7F90)
        val bagHandle = Path().apply {
            moveTo(48f * s, 45f * s)
            cubicTo(48f * s, 41.5f * s, 52f * s, 41.5f * s, 52f * s, 45f * s)
        }
        drawPath(
            path = bagHandle,
            color = Color(0xFF0C7F90),
            style = Stroke(width = 2.2f * s, cap = StrokeCap.Round)
        )

        // 6. Shopping Bag Body in Medium Teal (#0C7F90)
        val bagBody = Path().apply {
            moveTo(43.5f * s, 46f * s)
            lineTo(56.5f * s, 46f * s)
            lineTo(55.2f * s, 58f * s)
            cubicTo(55.0f * s, 59f * s, 54.2f * s, 59.8f * s, 53.2f * s, 59.8f * s)
            lineTo(46.8f * s, 59.8f * s)
            cubicTo(45.8f * s, 59.8f * s, 45.0f * s, 59f * s, 44.8f * s, 58f * s)
            close()
        }
        drawPath(bagBody, color = Color(0xFF0C7F90))

        // 7. Magnifying Glass White Outer Border
        val ringOuterPath = Path().apply {
            addOval(androidx.compose.ui.geometry.Rect(53.5f * s, 49.5f * s, 62.5f * s, 58.5f * s))
        }
        drawPath(
            path = ringOuterPath,
            color = Color.White,
            style = Stroke(width = 4.0f * s)
        )
        drawLine(
            color = Color.White,
            start = androidx.compose.ui.geometry.Offset(61.5f * s, 57.5f * s),
            end = androidx.compose.ui.geometry.Offset(65.5f * s, 61.5f * s),
            strokeWidth = 4.0f * s,
            cap = StrokeCap.Round
        )

        // 8. Magnifying Glass Lens Ring (Navy #05171A)
        drawPath(
            path = ringOuterPath,
            color = Color(0xFF05171A),
            style = Stroke(width = 2.0f * s)
        )

        // 9. Magnifying Glass Handle (Navy #05171A)
        drawLine(
            color = Color(0xFF05171A),
            start = androidx.compose.ui.geometry.Offset(61.5f * s, 57.5f * s),
            end = androidx.compose.ui.geometry.Offset(65.5f * s, 61.5f * s),
            strokeWidth = 2.0f * s,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun RoCompanyLogoHeader(
    modifier: Modifier = Modifier,
    height: Dp = 18.dp
) {
    Box(
        modifier = modifier
            .height(height)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFF1EB4C9), Color(0xFF0C7F90))
                ),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "RO",
            color = Color.White,
            fontSize = (height.value * 0.72f).sp,
            fontWeight = FontWeight.Black,
            style = androidx.compose.ui.text.TextStyle(
                letterSpacing = 1.2.sp
            )
        )
    }
}

@Composable
fun PoweredByRoFooter(
    isBengali: Boolean,
    modifier: Modifier = Modifier,
    badgeHeight: Dp = 18.dp
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = if (isBengali) "পাওয়ার বাই " else "Powered by ",
            color = DarkTextSecondary.copy(alpha = 0.8f),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            style = androidx.compose.ui.text.TextStyle(
                letterSpacing = 0.5.sp
            )
        )
        Spacer(modifier = Modifier.width(4.dp))
        RoCompanyLogoHeader(height = badgeHeight)
    }
}

