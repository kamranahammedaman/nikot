package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = NikotTeal80,
    secondary = NikotTealGrey80,
    tertiary = NikotGold80,
    background = NikotBgDark,
    surface = NikotSurfaceDark,
    onBackground = Color(0xFFE5F5F7),
    onSurface = Color(0xFFE5F5F7),
  )

private val LightColorScheme =
  lightColorScheme(
    primary = NikotTeal40,
    secondary = NikotTealGrey40,
    tertiary = NikotGold40,
    background = NikotBgLight,
    surface = NikotSurfaceLight,
    onBackground = Color(0xFF061E22),
    onSurface = Color(0xFF061E22),
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic (wallpaper-derived) color is available on Android 12+, but Nikot has its own
  // brand palette, so it defaults off — native components should match the app, not the user's
  // wallpaper. Pass dynamicColor = true explicitly if that look is ever wanted again.
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
