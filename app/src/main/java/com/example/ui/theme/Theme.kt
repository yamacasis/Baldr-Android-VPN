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

private val NetShieldDarkColorScheme = darkColorScheme(
    primary = EmeraldPrimary,
    onPrimary = EmeraldOnPrimary,
    primaryContainer = EmeraldContainer,
    onPrimaryContainer = EmeraldOnContainer,
    secondary = CyanSecondary,
    onSecondary = CyanOnSecondary,
    secondaryContainer = CyanContainer,
    onSecondaryContainer = CyanOnContainer,
    tertiary = BaldrGold,
    onTertiary = Color(0xFF2B1C00),
    tertiaryContainer = Color(0xFF4A3710),
    onTertiaryContainer = BaldrGoldSoft,
    background = ObsidianBg,
    onBackground = Color(0xFFEDF1FC),
    surface = ObsidianSurface,
    onSurface = Color(0xFFEDF1FC),
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = Color(0xFFA9B3D1),
    outline = ObsidianOutline,
    error = CoralError,
    errorContainer = CoralContainer,
    onErrorContainer = Color(0xFFFFDAD9)
)

private val NetShieldLightColorScheme = lightColorScheme(
    primary = EmeraldLightPrimary,
    onPrimary = Color.White,
    primaryContainer = EmeraldLightContainer,
    onPrimaryContainer = Color(0xFF002114),
    secondary = CyanLightSecondary,
    onSecondary = Color.White,
    secondaryContainer = CyanLightContainer,
    onSecondaryContainer = Color(0xFF001E2E),
    tertiary = BaldrGoldDeep,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFBE5B8),
    onTertiaryContainer = Color(0xFF332000),
    background = MistBg,
    onBackground = Color(0xFF1A1E30),
    surface = MistSurface,
    onSurface = Color(0xFF1A1E30),
    surfaceVariant = MistSurfaceHigh,
    onSurfaceVariant = Color(0xFF5A4E46),
    outline = MistOutline,
    error = Color(0xFFDC2626),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
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
        typography = buildAppTypography(isFarsi = isFarsi),
        content = content
    )
}
