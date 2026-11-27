package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Baldr Heritage Gold Accents
val BaldrGold = Color(0xFFEDC365)
val BaldrGoldSoft = Color(0xFFFFE5A8)
val BaldrGoldDeep = Color(0xFF9A6B16)

// Lapis Lazuli & Royal Sapphire Blue Spectrum (High-Contrast Multi-Shade Blue Palette)
val LapisEmblemBg = Color(0xFF071638)      // Exact emblem backdrop
val LapisMidnight = Color(0xFF050B1C)      // Dark scaffold deepest navy background
val LapisDeep = Color(0xFF0D1C42)          // Dark card surface (clearly separated from backdrop)
val LapisRoyal = Color(0xFF18326D)         // Dark elevated container / inner tile (distinct from card)
val LapisIndigo = Color(0xFF224694)        // Dark secondary container / active pill
val LapisBorder = Color(0xFF456EC9)        // Crisp dark outline separating blue layers
val LapisAzure = Color(0xFF56D8FF)         // Luminous cyan-sky secondary accent (distinct from royal blue)
val LapisPeriwinkle = Color(0xFFE3EEFF)    // Crisp high-contrast text on secondary containers

// Navy Blue Shadow Tokens (سایه سرمه‌ای)
val NavyShadow = Color(0xFF040C28)         // Rich deep navy drop shadow
val NavyShadowSpot = Color(0xFF081B54)     // Soft navy ambient glow/shadow

val SapphirePrimary = Color(0xFF3B8BFF)    // Vivid bright royal blue (pops clearly against navy)
val SapphireOnPrimary = Color(0xFFFFFFFF)  // Crisp white icon & label on blue buttons
val SapphireContainer = Color(0xFF19428F)  // Distinct royal sapphire container
val SapphireOnContainer = Color(0xFFE2EEFF)

val SapphireLightPrimary = Color(0xFF1D4ED8)    // Vivid royal sapphire for light theme
val SapphireLightContainer = Color(0xFFD2E4FF)  // Clear soft sky-blue primary container
val SapphireLightOnContainer = Color(0xFF081B54)

val LapisCobalt = Color(0xFF0284C7)        // Distinct ocean-cerulean secondary in light theme
val LapisCobaltDeep = Color(0xFF0C3B7A)    // Deep Persian Lapis
val LapisWash = Color(0xFFD8F0FF)          // Light secondary container (distinct cool cyan tint)
val LapisInk = Color(0xFF0A2A52)           // High-contrast ink on light secondary container

// Connect Power Button Semantic Tokens
// Connected: Vibrant lush emerald green core with dark forest-green outer ring
val ConnectedGreenLight = Color(0xFF34E89E)
val ConnectedGreen = Color(0xFF10B981)
val ConnectedGreenDeep = Color(0xFF047857)
val ConnectedGreenDarkRing = Color(0xFF064E3B)
val ConnectedGreenBezel = Color(0xFF05382B)

// Connecting / Reconnecting: Rich warm tangerine orange
val ConnectingOrangeLight = Color(0xFFFFAC52)
val ConnectingOrange = Color(0xFFF97316)
val ConnectingOrangeDeep = Color(0xFFDD4B08)
val ConnectingOrangeDarkRing = Color(0xFF9A3412)

// Idle / Disconnected Power Orb: Crisp royal sapphire with deep navy bezel
val IdleBlueLight = Color(0xFF4BA3FF)
val IdleBlueCore = Color(0xFF1E56D9)
val IdleBlueDeep = Color(0xFF0E2C7A)
val IdleBlueRing = Color(0xFF091B4D)

// Dark Theme Palette aliases (harmonized with bg_lapis_dark & Royal Blue / Lapis spectrum)
val ObsidianBg = LapisMidnight
val ObsidianSurface = LapisDeep
val ObsidianSurfaceHigh = LapisRoyal
val ObsidianOutline = LapisBorder

// Primary aliases mapped to Royal Sapphire Blue
val EmeraldPrimary = SapphirePrimary
val EmeraldOnPrimary = SapphireOnPrimary
val EmeraldContainer = SapphireContainer
val EmeraldOnContainer = SapphireOnContainer

// Secondary aliases mapped to clear Cyan-Azure spectrum
val CyanSecondary = LapisAzure
val CyanOnSecondary = Color(0xFF051A3B)
val CyanContainer = LapisIndigo
val CyanOnContainer = LapisPeriwinkle

// Tertiary & Semantic Status Tokens
val VioletTertiary = BaldrGold
val AmberStatus = ConnectingOrange
val CoralErrorLight = Color(0xFFFF7A8A)
val CoralError = Color(0xFFF43F5E)
val CoralErrorDeep = Color(0xFFB91C3C)
val CoralContainer = Color(0xFF5C1927)

// Light Theme Palette (crisp white cards & clearly separated blue tiers with navy shadows)
val MistBg = Color(0xFFEEF4FF)
val MistSurface = Color(0xFFFFFFFF)
val MistSurfaceHigh = Color(0xFFDCE9FF)
val MistOutline = Color(0xFF8AAEE8)
val EmeraldLightPrimary = SapphireLightPrimary
val EmeraldLightContainer = SapphireLightContainer
val CyanLightSecondary = LapisCobalt
val CyanLightContainer = LapisWash
