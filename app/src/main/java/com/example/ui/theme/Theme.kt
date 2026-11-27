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
    onBackground = Color(0xFFF2F6FF),
    surface = LapisDeep,
    onSurface = Color(0xFFF2F6FF),
    surfaceVariant = LapisRoyal,
    onSurfaceVariant = Color(0xFFBDD1F7),
    surfaceContainerLowest = Color(0xFF040917),
    surfaceContainerLow = Color(0xFF0A1636),
    surfaceContainer = LapisDeep,
    surfaceContainerHigh = LapisRoyal,
    surfaceContainerHighest = Color(0xFF24438A),
    outline = LapisBorder,
    outlineVariant = Color(0xFF2C4A8C),
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
    onBackground = Color(0xFF0B1838),
    surface = MistSurface,
    onSurface = Color(0xFF0B1838),
    surfaceVariant = MistSurfaceHigh,
    onSurfaceVariant = Color(0xFF354B7A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7FAFF),
    surfaceContainer = MistSurface,
    surfaceContainerHigh = MistSurfaceHigh,
    surfaceContainerHighest = Color(0xFFC8DCFF),
    outline = MistOutline,
    outlineVariant = Color(0xFFB3C9F0),
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
