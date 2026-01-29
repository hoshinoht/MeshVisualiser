package com.example.meshvisualiser.ui.theme

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
val md_theme_dark_primaryContainer = Color(0xFF004D64)
val md_theme_dark_onPrimaryContainer = Color(0xFFBCE9FF)
val md_theme_dark_secondary = Color(0xFF4CD9DF)
val md_theme_dark_onSecondary = Color(0xFF003739)
val md_theme_dark_secondaryContainer = Color(0xFF004F53)
val md_theme_dark_onSecondaryContainer = Color(0xFF6FF6FC)
val md_theme_dark_tertiary = Color(0xFFEBB5ED)
val md_theme_dark_onTertiary = Color(0xFF49204E)
val md_theme_dark_tertiaryContainer = Color(0xFF623766)
val md_theme_dark_onTertiaryContainer = Color(0xFFFFD6FF)
val md_theme_dark_error = Color(0xFFFFB4AB)
val md_theme_dark_onError = Color(0xFF690005)
val md_theme_dark_errorContainer = Color(0xFF93000A)
val md_theme_dark_onErrorContainer = Color(0xFFFFDAD6)
val md_theme_dark_background = Color(0xFF001F24)
val md_theme_dark_onBackground = Color(0xFF97F0FF)
val md_theme_dark_surface = Color(0xFF001F24)
val md_theme_dark_onSurface = Color(0xFF97F0FF)
val md_theme_dark_surfaceVariant = Color(0xFF40484C)
val md_theme_dark_onSurfaceVariant = Color(0xFFC0C8CC)
val md_theme_dark_outline = Color(0xFF8A9296)
val md_theme_dark_inverseSurface = Color(0xFF97F0FF)
val md_theme_dark_inverseOnSurface = Color(0xFF00363D)
val md_theme_dark_inversePrimary = Color(0xFF006783)

// Domain-specific status colors (functional, not themeable)
val StatusDiscovering = Color(0xFFFFA726)
val StatusElecting = Color(0xFF42A5F5)
val StatusResolving = Color(0xFFAB47BC)
val StatusConnected = Color(0xFF66BB6A)
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
