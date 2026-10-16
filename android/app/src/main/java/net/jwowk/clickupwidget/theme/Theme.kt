package net.jwowk.clickupwidget.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
  primary = BrandAccentDark,
  onPrimary = Color(0xFF1B1240),
  primaryContainer = Color(0xFF3B3470),
  onPrimaryContainer = Color(0xFFE5DFFF),
  secondary = BrandPink,
  onSecondary = Color(0xFF4A0B2A),
  tertiary = Color(0xFFFBBF24),
  background = BrandBackgroundDark,
  onBackground = BrandOnDark,
  surface = BrandBackgroundDark,
  onSurface = BrandOnDark,
  surfaceVariant = BrandSurfaceHighDark,
  onSurfaceVariant = BrandOnDarkVariant,
  surfaceContainerLowest = Color(0xFF101116),
  surfaceContainerLow = Color(0xFF1A1B22),
  surfaceContainer = BrandSurfaceDark,
  surfaceContainerHigh = BrandSurfaceHighDark,
  surfaceContainerHighest = Color(0xFF30333F),
  outline = Color(0xFF6D7080),
  outlineVariant = Color(0xFF3A3D4A),
  error = Color(0xFFF87171),
)

private val LightColorScheme = lightColorScheme(
  primary = BrandAccentLight,
  onPrimary = Color.White,
  primaryContainer = Color(0xFFE5DFFF),
  onPrimaryContainer = Color(0xFF1B1240),
  secondary = Color(0xFFD6336F),
  tertiary = Color(0xFFB45309),
  background = BrandBackgroundLight,
  onBackground = BrandOnLight,
  surface = BrandBackgroundLight,
  onSurface = BrandOnLight,
  surfaceVariant = BrandSurfaceHighLight,
  onSurfaceVariant = BrandOnLightVariant,
  surfaceContainerLowest = Color.White,
  surfaceContainerLow = Color(0xFFFAFAFD),
  surfaceContainer = BrandSurfaceLight,
  surfaceContainerHigh = BrandSurfaceHighLight,
  surfaceContainerHighest = Color(0xFFE2E2EA),
  outline = Color(0xFF8A8C9B),
  outlineVariant = Color(0xFFD3D4DE),
  error = Color(0xFFDC2626),
)

/** The app color scheme: Material You on Android 12+, otherwise the ClickUp brand palette; follows system dark mode. */
@Composable
fun appColorScheme(darkTheme: Boolean = isSystemInDarkTheme(), dynamicColor: Boolean = true): ColorScheme =
  when {
    dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
      val context = LocalContext.current
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    darkTheme -> DarkColorScheme
    else -> LightColorScheme
  }

@Composable
fun ClickUpTasksTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  content: @Composable () -> Unit,
) {
  MaterialTheme(colorScheme = appColorScheme(darkTheme, dynamicColor), typography = Typography, content = content)
}
