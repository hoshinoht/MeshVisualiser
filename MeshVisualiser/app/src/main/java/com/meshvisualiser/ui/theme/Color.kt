package com.meshvisualiser.ui.theme

import androidx.compose.ui.graphics.Color

// Material 3 seed-based palette (dark teal/cyan theme)
// Primary: Light Blue 300
val md_theme_light_primary = Color(0xFF006783)
val md_theme_light_onPrimary = Color(0xFFFFFFFF)
val md_theme_light_primaryContainer = Color(0xFFBCE9FF)
val md_theme_light_onPrimaryContainer = Color(0xFF001F2A)
val md_theme_light_secondary = Color(0xFF00696E)
val md_theme_light_onSecondary = Color(0xFFFFFFFF)
val md_theme_light_secondaryContainer = Color(0xFF6FF6FC)
val md_theme_light_onSecondaryContainer = Color(0xFF002021)
val md_theme_light_tertiary = Color(0xFF7B4E7F)
val md_theme_light_onTertiary = Color(0xFFFFFFFF)
val md_theme_light_tertiaryContainer = Color(0xFFFFD6FF)
val md_theme_light_onTertiaryContainer = Color(0xFF310937)
val md_theme_light_error = Color(0xFFBA1A1A)
val md_theme_light_onError = Color(0xFFFFFFFF)
val md_theme_light_errorContainer = Color(0xFFFFDAD6)
val md_theme_light_onErrorContainer = Color(0xFF410002)
val md_theme_light_background = Color(0xFFF6FEFF)
val md_theme_light_onBackground = Color(0xFF001F24)
val md_theme_light_surface = Color(0xFFF6FEFF)
val md_theme_light_onSurface = Color(0xFF001F24)
val md_theme_light_surfaceVariant = Color(0xFFDCE4E8)
val md_theme_light_onSurfaceVariant = Color(0xFF40484C)
val md_theme_light_outline = Color(0xFF70787C)
val md_theme_light_inverseSurface = Color(0xFF00363D)
val md_theme_light_inverseOnSurface = Color(0xFFD0F8FF)
val md_theme_light_inversePrimary = Color(0xFF63D4FF)

val md_theme_dark_primary = Color(0xFF63D4FF)
val md_theme_dark_onPrimary = Color(0xFF003546)
val md_theme_dark_primaryContainer = Color(0xFF005F7A)
val md_theme_dark_onPrimaryContainer = Color(0xFFBCE9FF)
val md_theme_dark_secondary = Color(0xFF4CD9DF)
val md_theme_dark_onSecondary = Color(0xFF003739)
val md_theme_dark_secondaryContainer = Color(0xFF006166)
val md_theme_dark_onSecondaryContainer = Color(0xFF6FF6FC)
val md_theme_dark_tertiary = Color(0xFFEBB5ED)
val md_theme_dark_onTertiary = Color(0xFF49204E)
val md_theme_dark_tertiaryContainer = Color(0xFF7A4580)
val md_theme_dark_onTertiaryContainer = Color(0xFFFFD6FF)
val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_errorContainer = Color(0xFF93000A)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)
val md_theme_dark_background = Color(0xFF001F24)
val md_theme_dark_onBackground = Color(0xFFD6E4E8)
val md_theme_dark_surface = Color(0xFF001F24)
val md_theme_dark_onSurface = Color(0xFFD6E4E8)
val md_theme_dark_surfaceVariant = Color(0xFF40484C)
val md_theme_dark_onSurfaceVariant = Color(0xFFC0C8CC)
val md_theme_dark_outline = Color(0xFF8A9296)
val md_theme_dark_inverseSurface = Color(0xFF97F0FF)
val md_theme_dark_inverseOnSurface = Color(0xFF00363D)
val md_theme_dark_inversePrimary = Color(0xFF006783)

// Domain-specific status colors (functional, not themeable)
val StatusDiscovering = Color(0xFFFF9800)
val StatusElecting = Color(0xFF2196F3)
val StatusResolving = Color(0xFFAB47BC)
val StatusConnected = Color(0xFF6EDB72)  // was 0xFF4CAF50 (Green 500, fails contrast)
val StatusLeader = Color(0xFFFFD54F)

// Data exchange log colors
val LogTcp = Color(0xFF42A5F5)      // Blue
val LogUdp = Color(0xFFFFA726)      // Orange
val LogAck = Color(0xFF66BB6A)      // Green
val LogError = Color(0xFFEF5350)    // Red

// AR visualization colors
val ArLineColor = Color(0xFF00E5FF)
val ArPeerNode = Color(0xFFFF4081)

// Packet visualization
val PacketTcp = Color(0xFF42A5F5)
val PacketUdp = Color(0xFFFFA726)
val PacketAck = Color(0xFF66BB6A)
val PacketDrop = Color(0xFFEF5350)

// Election protocol visualization
val ElectionMsg = Color(0xFFFFD54F)    // Amber — ELECTION messages
val ElectionOk = Color(0xFF81C784)     // Green — OK replies
val ElectionCoord = Color(0xFFE040FB)  // Purple — COORDINATOR broadcast

// CSMA/CD states
val CsmaIdle = Color(0xFF78909C)
val CsmaSensing = Color(0xFFFFCA28)
val CsmaTransmitting = Color(0xFF66BB6A)
val CsmaCollision = Color(0xFFEF5350)
val CsmaBackoff = Color(0xFFAB47BC)

// Topology quality
val TopologyExcellent = Color(0xFF66BB6A)
val TopologyGood = Color(0xFFFFCA28)
val TopologyPoor = Color(0xFFEF5350)

// Surface hierarchy tokens
val SurfaceContainerLowest = Color(0xFF0A1F24)
val SurfaceContainerLow = Color(0xFF112A30)

// Surface container hierarchy — full M3 Expressive 5-level scale
val SurfaceContainer = Color(0xFF1A3038)
val SurfaceContainerHigh = Color(0xFF243A42)
val SurfaceContainerHighest = Color(0xFF2E4550)

// Fixed surface tokens for M3 Expressive
val PrimaryFixed = Color(0xFFBCE9FF)
val PrimaryFixedDim = Color(0xFF63D4FF)
val OnPrimaryFixed = Color(0xFF001F2A)
val OnPrimaryFixedVariant = Color(0xFF005F7A)

val SecondaryFixed = Color(0xFF6FF6FC)
val SecondaryFixedDim = Color(0xFF4CD9DF)
val OnSecondaryFixed = Color(0xFF002021)
val OnSecondaryFixedVariant = Color(0xFF006166)

val TertiaryFixed = Color(0xFFFFD6FF)
val TertiaryFixedDim = Color(0xFFEBB5ED)
val OnTertiaryFixed = Color(0xFF310937)
val OnTertiaryFixedVariant = Color(0xFF7A4580)

// Semantic overlay tokens
val GlassOverlayDark = Color(0x1AFFFFFF)
val GlassOverlayLight = Color(0x1A000000)

// Mesh visualization accents (for decorative background elements)
val MeshAccent1 = Color(0xFF63D4FF) // cyan
val MeshAccent2 = Color(0xFFEBB5ED) // lavender
val MeshAccent3 = Color(0xFF4CD9DF) // teal
val MeshAccent4 = Color(0xFFFFD54F) // amber
