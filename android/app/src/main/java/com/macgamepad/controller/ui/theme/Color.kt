package com.macgamepad.controller.ui.theme

import androidx.compose.ui.graphics.Color

// ══════════════════════════════════════════════════════════════════════════
// MacGamepad Design System — Color Palette
// Deep-space dark with cool blue undertones. Premium layered surfaces.
// ══════════════════════════════════════════════════════════════════════════

// ── Layered backgrounds (each step is subtly lighter) ───────────────────
val Bg0            = Color(0xFF050812)   // Deepest — used behind everything
val Bg1            = Color(0xFF080C1A)   // Base background
val Bg2            = Color(0xFF0D1223)   // Cards background
val Bg3            = Color(0xFF131930)   // Elevated cards
val Bg4            = Color(0xFF1A2240)   // Interactive surface / hover

// Legacy compat aliases (referenced by existing code)
val BackgroundDark = Bg1
val SurfaceDark    = Bg2
val SurfaceVariant = Bg3
val SurfaceElevated = Bg4

// ── Borders (3-tier glass system) ────────────────────────────────────────
val BorderSubtle  = Color(0xFF1B2240)   // Barely-there dividers
val BorderMedium  = Color(0xFF263060)   // Card outlines
val BorderStrong  = Color(0xFF3D5090)   // Focused / active borders
val BorderFocus   = Color(0xFF4D8EFF)   // Keyboard focus ring

// ── Core accent colors — vivid but controlled ────────────────────────────
val AccentBlue    = Color(0xFF4D8EFF)   // Primary interactive
val AccentCyan    = Color(0xFF22D3EE)   // Highlights / edit mode
val AccentGreen   = Color(0xFF22C55E)   // Connected / success
val AccentRed     = Color(0xFFEF4444)   // Error / disconnect
val AccentAmber   = Color(0xFFF59E0B)   // Warning / caution
val AccentPurple  = Color(0xFFA78BFA)   // Optional / secondary feature

// ── Interactive state semantic tokens ─────────────────────────────────────
val ColorInteractive        = AccentBlue
val ColorInteractiveSubdued = Color(0xFF1A2D5E)
val ColorInteractiveHover   = Color(0xFF5A9BFF)

// ── Stick / button colors ─────────────────────────────────────────────────
val ButtonSurface    = Color(0xFF141C38)
val ButtonBorder     = Color(0xFF253058)
val StickBase        = Color(0xFF0E1428)
val StickThumb       = Color(0xFF1F2845)
val StickThumbActive = AccentBlue

// ── Typography ────────────────────────────────────────────────────────────
val TextPrimary   = Color(0xFFF0F4FF)   // Near-white with cool tint
val TextSecondary = Color(0xFF7A8AAD)   // Subdued labels
val TextTertiary  = Color(0xFF485278)   // Placeholder / disabled
val TextAccent    = AccentCyan

// ── Status indicators ─────────────────────────────────────────────────────
val StatusConnected    = Color(0xFF22C55E)
val StatusDisconnected = Color(0xFFEF4444)
val StatusConnecting   = Color(0xFFF59E0B)

// ══════════════════════════════════════════════════════════════════════════
// Per-controller accurate palette (matched to official branding)
// ══════════════════════════════════════════════════════════════════════════
object ProfileColors {

    // ── PlayStation 5 DualSense (Sony official) ──────────────────────────
    val PsBlue   = Color(0xFF0070D1)
    val PsCyan   = Color(0xFF00B9F2)
    val PsWhite  = Color(0xFFE8EAEF)

    // ── Xbox Series X (Microsoft official) ───────────────────────────────
    val XboxGreen = Color(0xFF107C10)
    val XboxBlack = Color(0xFF0E0E0E)

    // ── Nintendo Switch Pro (Nintendo official) ───────────────────────────
    val SwitchRed  = Color(0xFFE4000F)   // Joy-Con L color
    val SwitchBlue = Color(0xFF009AC7)   // Joy-Con R color

    // Accurate face button colors for Switch
    val SwitchBtnA = Color(0xFFE4000F)   // A — Red
    val SwitchBtnB = Color(0xFFE8A000)   // B — Yellow/Gold
    val SwitchBtnX = Color(0xFF009AC7)   // X — Blue
    val SwitchBtnY = Color(0xFF4CAF50)   // Y — Green
}
