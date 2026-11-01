package com.puretube.adfree.ui.theme

import android.app.Activity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme =
  darkColorScheme(
    primary = CrimsonRed,
    onPrimary = Color.White,
    primaryContainer = CrimsonRedDark,
    onPrimaryContainer = Color.White,
    secondary = ShieldGreen,
    onSecondary = PureBlack,
    secondaryContainer = DarkCard,
    onSecondaryContainer = ShieldGreen,
    tertiary = AccentCyan,
    background = PureBlack,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkCard,
    onSurfaceVariant = TextSecondary,
    outline = DarkBorder,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = CrimsonRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD9DF),
    onPrimaryContainer = CrimsonRedDark,
    secondary = Color(0xFF00875A),
    onSecondary = Color.White,
    background = Color(0xFFF9F9FB),
    onBackground = Color(0xFF1A1A24),
    surface = Color.White,
    onSurface = Color(0xFF1A1A24),
    surfaceVariant = Color(0xFFF0F0F5),
    onSurfaceVariant = Color(0xFF555566),
    outline = Color(0xFFE0E0EB),
  )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Default to sleek dark theme for YouTube video viewing
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as Activity).window
      window.statusBarColor = colorScheme.background.toArgb()
      window.navigationBarColor = colorScheme.background.toArgb()
      val insetsController = WindowCompat.getInsetsController(window, view)
      insetsController.isAppearanceLightStatusBars = !darkTheme
      insetsController.isAppearanceLightNavigationBars = !darkTheme
    }
  }

  CompositionLocalProvider(
    LocalRippleConfiguration provides null
  ) {
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
  }
}
