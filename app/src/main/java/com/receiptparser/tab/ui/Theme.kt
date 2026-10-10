package com.receiptparser.tab.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val TabColors = lightColorScheme(primary = Color(0xFF3448AE), onPrimary = Color.White,
    primaryContainer = Color(0xFFE5E9FF), onPrimaryContainer = Color(0xFF192D86),
    secondary = Color(0xFF236B50), secondaryContainer = Color(0xFFD9F2E4),
    background = Color(0xFFF7F8FC), surface = Color.White, onBackground = Color(0xFF19213A), onSurface = Color(0xFF19213A),
    onSurfaceVariant = Color(0xFF586077), surfaceVariant = Color(0xFFEBEDF5), error = Color(0xFFBA1A1A))
private val TabTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
)
@Composable fun TabTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = TabColors, typography = TabTypography, content = content)
