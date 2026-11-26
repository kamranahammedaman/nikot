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
fun LoggedInNavigator(viewModel: PharmacyViewModel, isBengali: Boolean) {
    val currentUser by viewModel.currentUser.collectAsState()

    if (currentUser.role == "super_admin") {
        SuperAdminPortalScreen(viewModel, isBengali)
        return
    }

    if (currentUser.role == "customer") {
        CustomerPortalScreen(viewModel, isBengali)
        return
    }

    var currentTab by remember { mutableStateOf(0) } // 0: ড্যাশবোর্ড, 1: বিক্রয়, 2: ইনভেন্টরি, 3: রিপোর্ট, 4: অ্যাকাউন্ট

    androidx.activity.compose.BackHandler(enabled = currentTab != 0) {
        currentTab = 0
    }

    val notifications by viewModel.notifications.collectAsState()
    val unreadNotifCount = notifications.count { !it.isRead }

    var showPortalSwitchDialog by remember { mutableStateOf(false) }
    var showStaffAddPharmacyDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            NavigationBar(
                containerColor = Color.Transparent,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(GlassFillStrong, RoundedCornerShape(26.dp))
                    .border(1.dp, GlassBorder, RoundedCornerShape(26.dp))
            ) {
                val tabs = listOf(
                    Triple(if (isBengali) "ড্যাশবোর্ড" else "Dashboard", Icons.Filled.Dashboard, Icons.Outlined.Dashboard),
                    Triple(if (isBengali) "বিক্রয়" else "Sales", Icons.Filled.ShoppingCart, Icons.Outlined.ShoppingCart),
                    Triple(if (isBengali) "ইনভেন্টরি" else "Inventory", Icons.Filled.ListAlt, Icons.Outlined.ListAlt),
                    Triple(if (isBengali) "রিপোর্ট" else "Reports", Icons.Filled.Assessment, Icons.Outlined.Assessment),
                    Triple(if (isBengali) "সেটিংস" else "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
                )

                tabs.forEachIndexed { index, (label, filledIcon, outlinedIcon) ->
                    NavigationBarItem(
                        selected = currentTab == index,
                        onClick = { currentTab = index },
                        label = { Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1) },
                        icon = {
                            Box {
                                Icon(
                                    imageVector = if (currentTab == index) filledIcon else outlinedIcon,
                                    contentDescription = label
                                )
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = if (isDarkModeGlobal) Color.Black else Color.White,
                            unselectedIconColor = DarkTextSecondary.copy(alpha = 0.6f),
                            selectedTextColor = DarkTextPrimary,
                            unselectedTextColor = DarkTextSecondary.copy(alpha = 0.5f),
                            indicatorColor = DarkAccentRed
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(GlassBackgroundGradient)
        ) {
            if (currentUser.role == "staff" && currentUser.linkedAdminUsername.isBlank() && currentTab != 4) {
                StaffUnlinkedPharmacyScreen(
                    isBengali = isBengali,
                    onGoToSettings = { currentTab = 4 },
                    onAddPharmacy = { showStaffAddPharmacyDialog = true }
                )
            } else {
                when (currentTab) {
                    0 -> DashboardScreen(viewModel, isBengali)
                    1 -> SalesScreen(viewModel, isBengali)
                    2 -> InventoryScreen(viewModel, isBengali)
                    3 -> ReportsScreen(viewModel, isBengali)
                    4 -> AccountScreen(viewModel, isBengali)
                }
            }
        }

        if (showStaffAddPharmacyDialog) {
            StaffAddPharmacyDialog(
                viewModel = viewModel,
                isBengali = isBengali,
                onDismiss = { showStaffAddPharmacyDialog = false }
            )
        }

        if (showPortalSwitchDialog) {
            NikotPortalSwitchDialog(
                viewModel = viewModel,
                isBengali = isBengali,
                onDismiss = { showPortalSwitchDialog = false }
            )
        }
    }
}

