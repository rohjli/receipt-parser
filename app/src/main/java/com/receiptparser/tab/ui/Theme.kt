package com.receiptparser.tab.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.receiptparser.tab.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Composable
private fun tungaonColors() = lightColorScheme(
    primary = colorResource(R.color.brand_emerald), onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F3E5), onPrimaryContainer = Color(0xFF063D2B),
    secondary = Color(0xFF9B315A), onSecondary = Color.White,
    secondaryContainer = colorResource(R.color.brand_rose), onSecondaryContainer = Color(0xFF3D1023),
    tertiary = Color(0xFF76543B), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF8DFC9), onTertiaryContainer = Color(0xFF2C1708),
    background = Color(0xFFF8FAF8), onBackground = colorResource(R.color.brand_ink),
    surface = Color.White, onSurface = colorResource(R.color.brand_ink),
    surfaceVariant = Color(0xFFE8EDE5), onSurfaceVariant = Color(0xFF4C5E54),
    surfaceTint = colorResource(R.color.brand_emerald),
    surfaceBright = Color.White, surfaceDim = Color(0xFFE4EAE5),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF2F6F2),
    surfaceContainer = Color(0xFFEDF2EE), surfaceContainerHigh = Color(0xFFE7EEE9),
    surfaceContainerHighest = Color(0xFFE1E9E3),
    outline = Color(0xFF718077), outlineVariant = Color(0xFFDCE5DE),
    inverseSurface = colorResource(R.color.brand_ink), inverseOnSurface = Color(0xFFF5F2EA),
    inversePrimary = Color(0xFF83DBB3),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
)
private val TabTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 38.sp, lineHeight = 46.sp, letterSpacing = (-1).sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
)
@Composable fun TabTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = tungaonColors(), typography = TabTypography, shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(32.dp),
), content = content)
