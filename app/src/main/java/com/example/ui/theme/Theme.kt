package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme =
  lightColorScheme(
    primary = OrangePrimary,
    secondary = AcceptGreen,
    tertiary = AmberHighlight,
    background = SandyBg,
    surface = CleanWhite,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = DarkCharcoal,
    onBackground = DarkCharcoal,
    onSurface = DarkCharcoal,
    outline = GrayBorder
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false, // Force customized warm sandstone theme for high visibility
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = LightColorScheme,
    typography = Typography,
    content = content
  )
}
