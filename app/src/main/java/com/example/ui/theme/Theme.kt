package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val KmtMailColorScheme = darkColorScheme(
  primary = PrimaryBlue,
  onPrimary = DarkBackground,
  primaryContainer = SecondaryBlue,
  onPrimaryContainer = TextWhite,
  secondary = SecondaryBlue,
  onSecondary = TextWhite,
  background = DarkBackground,
  onBackground = TextWhite,
  surface = DarkCard,
  onSurface = TextWhite,
  surfaceVariant = DarkCard,
  onSurfaceVariant = TextMuted,
  outline = SurfaceBorder,
  error = AccentRed,
  onError = TextWhite
)

@Composable
fun KmtMailTheme(
  content: @Composable () -> Unit
) {
  MaterialTheme(
    colorScheme = KmtMailColorScheme,
    typography = Typography,
    content = content
  )
}

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit
) {
  KmtMailTheme(content = content)
}

