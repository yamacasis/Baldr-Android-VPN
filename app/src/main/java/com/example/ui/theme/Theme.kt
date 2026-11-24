package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val NetShieldDarkColorScheme = darkColorScheme(
    primary = SapphirePrimary,
    onPrimary = SapphireOnPrimary,
    primaryContainer = SapphireContainer,
    onPrimaryContainer = SapphireOnContainer,
    secondary = LapisAzure,
    onSecondary = CyanOnSecondary,
    secondaryContainer = LapisIndigo,
    onSecondaryContainer = LapisPeriwinkle,
    tertiary = BaldrGold,
    onTertiary = Color(0xFF2B1D00),
    tertiaryContainer = Color(0xFF473612),
    onTertiaryContainer = BaldrGoldSoft,
    background = LapisMidnight,
    onBackground = Color(0xFFEFF4FF),
    surface = LapisDeep,
    onSurface = Color(0xFFEFF4FF),
    surfaceVariant = LapisRoyal,
    onSurfaceVariant = Color(0xFFA9BBE3),
    surfaceContainerLowest = Color(0xFF060D21),
    surfaceContainerLow = Color(0xFF0C1633),
    surfaceContainer = LapisDeep,
    surfaceContainerHigh = LapisRoyal,
    surfaceContainerHighest = Color(0xFF20366E),
    outline = LapisBorder,
    outlineVariant = Color(0xFF213566),
    error = CoralError,
    onError = Color(0xFF4A0A14),
    errorContainer = CoralContainer,
    onErrorContainer = Color(0xFFFFDADF)
)

private val NetShieldLightColorScheme = lightColorScheme(
    primary = SapphireLightPrimary,
    onPrimary = Color.White,
    primaryContainer = SapphireLightContainer,
    onPrimaryContainer = SapphireLightOnContainer,
    secondary = LapisCobalt,
    onSecondary = Color.White,
    secondaryContainer = LapisWash,
    onSecondaryContainer = LapisInk,
    tertiary = BaldrGoldDeep,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFBF0D4),
    onTertiaryContainer = Color(0xFF332100),
    background = MistBg,
    onBackground = Color(0xFF101B3B),
    surface = MistSurface,
    onSurface = Color(0xFF101B3B),
    surfaceVariant = MistSurfaceHigh,
    onSurfaceVariant = Color(0xFF44547D),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F8FF),
    surfaceContainer = MistSurface,
    surfaceContainerHigh = MistSurfaceHigh,
    surfaceContainerHighest = Color(0xFFD3DFF5),
    outline = MistOutline,
    outlineVariant = Color(0xFFCEDAF2),
    error = Color(0xFFD92D46),
    onError = Color.White,
    errorContainer = Color(0xFFFFE2E7),
    onErrorContainer = Color(0xFF7A1424)
)

@Composable
fun NetShieldTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    isFarsi: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> NetShieldDarkColorScheme
        else -> NetShieldLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = buildAppTypography(isFarsi = isFarsi)
    ) {
        CompositionLocalProvider(LocalContentColor provides colorScheme.onSurface) {
            content()
        }
    }
}
