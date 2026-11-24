package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Baldr Heritage Gold Accents
val BaldrGold = Color(0xFFEDC365)
val BaldrGoldSoft = Color(0xFFFFE5A8)
val BaldrGoldDeep = Color(0xFF9A6B16)

// Lapis Lazuli & Royal Sapphire Blue Spectrum (Cohesive Multi-Shade Blue Palette)
val LapisEmblemBg = Color(0xFF081B43)      // Exact emblem backdrop
val LapisMidnight = Color(0xFF081128)      // Dark scaffold background
val LapisDeep = Color(0xFF0F1C3D)          // Dark card surface
val LapisRoyal = Color(0xFF172957)         // Dark elevated surface / container
val LapisIndigo = Color(0xFF1E3678)        // Dark secondary container
val LapisBorder = Color(0xFF2E498C)        // Dark hairline outline
val LapisAzure = Color(0xFF7AA2FF)         // Dark secondary luminous lapis accent
val LapisPeriwinkle = Color(0xFFDCE7FF)    // Dark on-secondary-container mist

val SapphirePrimary = Color(0xFF3B82F6)    // Dark primary button / active blue
val SapphireOnPrimary = Color(0xFFFFFFFF)  // Crisp white icon & label on blue buttons
val SapphireContainer = Color(0xFF183A7A)  // Deep royal sapphire container
val SapphireOnContainer = Color(0xFFD6E6FF)

val SapphireLightPrimary = Color(0xFF1D4ED8)    // Light primary royal sapphire buttons
val SapphireLightContainer = Color(0xFFD6E4FF)  // Light primary soft blue container
val SapphireLightOnContainer = Color(0xFF0A1D56)

val LapisCobalt = Color(0xFF2B46A8)        // Light secondary classic Persian Lapis Lazuli
val LapisCobaltDeep = Color(0xFF16328C)    // Deep Persian Lapis
val LapisWash = Color(0xFFE0E7FF)          // Light secondary container
val LapisInk = Color(0xFF121F5E)           // Light on-secondary-container ink

// Dark Theme Palette aliases (harmonized with bg_lapis_dark & Royal Blue / Lapis spectrum)
val ObsidianBg = LapisMidnight
val ObsidianSurface = LapisDeep
val ObsidianSurfaceHigh = LapisRoyal
val ObsidianOutline = LapisBorder

// Primary aliases mapped to Royal Sapphire Blue (replacing green buttons across the app)
val EmeraldPrimary = SapphirePrimary
val EmeraldOnPrimary = SapphireOnPrimary
val EmeraldContainer = SapphireContainer
val EmeraldOnContainer = SapphireOnContainer

// Secondary aliases mapped to Lapis Lazuli spectrum
val CyanSecondary = LapisAzure
val CyanOnSecondary = Color(0xFF071B4F)
val CyanContainer = LapisIndigo
val CyanOnContainer = LapisPeriwinkle

// Tertiary & Semantic Status Tokens
val VioletTertiary = BaldrGold
val AmberStatus = Color(0xFFF5B73B)
val CoralError = Color(0xFFF45B6E)
val CoralContainer = Color(0xFF5C1927)

// Light Theme Palette (harmonized with bg_lapis_light cool alabaster & Lapis shades)
val MistBg = Color(0xFFF3F6FD)
val MistSurface = Color(0xFFEBF0FB)
val MistSurfaceHigh = Color(0xFFDFE7F8)
val MistOutline = Color(0xFFB7C6E8)
val EmeraldLightPrimary = SapphireLightPrimary
val EmeraldLightContainer = SapphireLightContainer
val CyanLightSecondary = LapisCobalt
val CyanLightContainer = LapisWash
