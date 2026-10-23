package com.macgamepad.controller.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.macgamepad.controller.bt.BluetoothGamepadService
import com.macgamepad.controller.bt.GamepadService
import com.macgamepad.controller.ui.theme.*
import kotlin.math.absoluteValue

// ─────────────────────────────────────────────────────────────────────────────
// Controller profile definitions
// ─────────────────────────────────────────────────────────────────────────────
data class ControllerProfile(
    val name: String,
    val tagline: String,
    val description: String,
    val brandIcon: String,
    val primaryColor: Color,
    val accentColor: Color,
    val glowColor: Color,
    val id: String
)

val controllerProfiles = listOf(
    ControllerProfile(
        name        = "DualSense",
        tagline     = "PlayStation 5",
        description = "Symmetric sticks · PS glyphs · Touchpad · Adaptive",
        brandIcon   = "▲",
        primaryColor = ProfileColors.PsWhite,
        accentColor  = ProfileColors.PsBlue,
        glowColor    = ProfileColors.PsCyan,
        id = "ps5"
    ),
    ControllerProfile(
        name        = "Xbox Wireless",
        tagline     = "Series X / S",
        description = "Offset sticks · A/B/X/Y · Bumpers · Vibration",
        brandIcon   = "✕",
        primaryColor = ProfileColors.XboxBlack,
        accentColor  = ProfileColors.XboxGreen,
        glowColor    = Color(0xFF1EDD1E),
        id = "xbox"
    ),
    ControllerProfile(
        name        = "Switch Pro",
        tagline     = "Nintendo Switch",
        description = "Joy-Con layout · B/A/Y/X · ZL/ZR · Home",
        brandIcon   = "✚",
        primaryColor = SurfaceVariant,
        accentColor  = ProfileColors.SwitchBlue,
        glowColor    = ProfileColors.SwitchRed,
        id = "switch"
    )
)

// ─────────────────────────────────────────────────────────────────────────────
// Canvas controller silhouette per profile
// ─────────────────────────────────────────────────────────────────────────────
private fun DrawScope.drawControllerArt(profile: ControllerProfile) {
    val w = size.width; val h = size.height
    val accent = profile.accentColor
    val bodyAlpha = 0.12f

    when (profile.id) {
        "ps5" -> {
            // Main body — wide pill shape
            drawRoundRect(
                color = accent.copy(alpha = bodyAlpha),
                topLeft = Offset(w * 0.12f, h * 0.28f),
                size = Size(w * 0.76f, h * 0.44f),
                cornerRadius = CornerRadius(32f, 32f)
            )
            drawRoundRect(
                color = accent.copy(alpha = 0.2f),
                topLeft = Offset(w * 0.12f, h * 0.28f),
                size = Size(w * 0.76f, h * 0.44f),
                cornerRadius = CornerRadius(32f, 32f),
                style = Stroke(1.5f)
            )
            // Left grip
            drawRoundRect(color = accent.copy(alpha = bodyAlpha * 0.7f),
                topLeft = Offset(w * 0.18f, h * 0.55f), size = Size(w * 0.18f, h * 0.3f), cornerRadius = CornerRadius(20f, 20f))
            // Right grip
            drawRoundRect(color = accent.copy(alpha = bodyAlpha * 0.7f),
                topLeft = Offset(w * 0.64f, h * 0.55f), size = Size(w * 0.18f, h * 0.3f), cornerRadius = CornerRadius(20f, 20f))
            // Touchpad
            drawRoundRect(
                color = accent.copy(alpha = 0.18f),
                topLeft = Offset(w * 0.37f, h * 0.34f),
                size = Size(w * 0.26f, h * 0.16f),
                cornerRadius = CornerRadius(8f, 8f)
            )
            // Left stick dot
            drawCircle(color = accent.copy(alpha = 0.4f), radius = 8f, center = Offset(w * 0.30f, h * 0.50f))
            // Right stick dot
            drawCircle(color = accent.copy(alpha = 0.4f), radius = 8f, center = Offset(w * 0.60f, h * 0.55f))
            // ABXY dots (small)
            val btns = listOf(Offset(w*0.72f, h*0.43f), Offset(w*0.75f, h*0.50f), Offset(w*0.69f, h*0.50f), Offset(w*0.72f, h*0.57f))
            btns.forEach { drawCircle(color = accent.copy(alpha = 0.35f), radius = 5f, center = it) }
        }
        "xbox" -> {
            // Main oval body
            drawOval(
                color = accent.copy(alpha = bodyAlpha),
                topLeft = Offset(w * 0.10f, h * 0.22f),
                size = Size(w * 0.80f, h * 0.50f)
            )
            drawOval(
                color = accent.copy(alpha = 0.18f),
                topLeft = Offset(w * 0.10f, h * 0.22f),
                size = Size(w * 0.80f, h * 0.50f),
                style = Stroke(1.5f)
            )
            // Left grip (offset lower)
            drawRoundRect(color = accent.copy(alpha = bodyAlpha * 0.6f),
                topLeft = Offset(w * 0.14f, h * 0.58f), size = Size(w * 0.22f, h * 0.28f), cornerRadius = CornerRadius(16f, 16f))
            // Right grip (slightly higher)
            drawRoundRect(color = accent.copy(alpha = bodyAlpha * 0.6f),
                topLeft = Offset(w * 0.64f, h * 0.52f), size = Size(w * 0.22f, h * 0.28f), cornerRadius = CornerRadius(16f, 16f))
            // Xbox ball (center circle)
            drawCircle(
                brush = Brush.radialGradient(listOf(accent.copy(alpha = 0.5f), Color.Transparent)),
                radius = 14f, center = Offset(w * 0.50f, h * 0.42f)
            )
            drawCircle(color = accent.copy(alpha = 0.5f), radius = 9f, center = Offset(w * 0.50f, h * 0.42f), style = Stroke(1.5f))
            // Left stick (offset high-left)
            drawCircle(color = accent.copy(alpha = 0.35f), radius = 9f, center = Offset(w * 0.30f, h * 0.45f))
            // Right stick (offset low-right)
            drawCircle(color = accent.copy(alpha = 0.35f), radius = 9f, center = Offset(w * 0.58f, h * 0.58f))
        }
        else -> { // Switch Pro
            // Center body (rectangular)
            drawRoundRect(
                color = Color(0xFF009AC7).copy(alpha = bodyAlpha + 0.04f),
                topLeft = Offset(w * 0.28f, h * 0.22f),
                size = Size(w * 0.44f, h * 0.56f),
                cornerRadius = CornerRadius(16f, 16f)
            )
            drawRoundRect(
                color = Color(0xFF009AC7).copy(alpha = 0.22f),
                topLeft = Offset(w * 0.28f, h * 0.22f),
                size = Size(w * 0.44f, h * 0.56f),
                cornerRadius = CornerRadius(16f, 16f),
                style = Stroke(1.5f)
            )
            // Left Joy-Con (blue)
            drawRoundRect(
                color = Color(0xFF009AC7).copy(alpha = bodyAlpha + 0.06f),
                topLeft = Offset(w * 0.06f, h * 0.14f),
                size = Size(w * 0.23f, h * 0.72f),
                cornerRadius = CornerRadius(22f, 22f)
            )
            drawRoundRect(
                color = Color(0xFF009AC7).copy(alpha = 0.3f),
                topLeft = Offset(w * 0.06f, h * 0.14f),
                size = Size(w * 0.23f, h * 0.72f),
                cornerRadius = CornerRadius(22f, 22f),
                style = Stroke(1.5f)
            )
            // Right Joy-Con (red)
            drawRoundRect(
                color = Color(0xFFE4000F).copy(alpha = bodyAlpha + 0.06f),
                topLeft = Offset(w * 0.71f, h * 0.14f),
                size = Size(w * 0.23f, h * 0.72f),
                cornerRadius = CornerRadius(22f, 22f)
            )
            drawRoundRect(
                color = Color(0xFFE4000F).copy(alpha = 0.3f),
                topLeft = Offset(w * 0.71f, h * 0.14f),
                size = Size(w * 0.23f, h * 0.72f),
                cornerRadius = CornerRadius(22f, 22f),
                style = Stroke(1.5f)
            )
            // Left stick (upper-left — Switch Pro layout)
            drawCircle(color = Color(0xFF009AC7).copy(alpha = 0.5f), radius = 10f, center = Offset(w * 0.175f, h * 0.38f))
            // D-pad (lower-left)
            drawCircle(color = Color(0xFF009AC7).copy(alpha = 0.35f), radius = 8f, center = Offset(w * 0.175f, h * 0.62f))
            // ABXY (upper-right)
            val cols = listOf(Color(0xFFE4000F), Color(0xFFE8A000), Color(0xFF009AC7), Color(0xFF4CAF50))
            val bpos = listOf(Offset(w*0.82f, h*0.45f), Offset(w*0.86f, h*0.38f), Offset(w*0.78f, h*0.38f), Offset(w*0.82f, h*0.31f))
            bpos.zip(cols).forEach { (pos, col) -> drawCircle(color = col.copy(alpha = 0.55f), radius = 5.5f, center = pos) }
            // Right stick (lower-right — Switch Pro)
            drawCircle(color = Color(0xFFE4000F).copy(alpha = 0.5f), radius = 10f, center = Offset(w * 0.825f, h * 0.60f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    connectionState: BluetoothGamepadService.ConnectionState,
    connectedDeviceName: String?,
    mode: GamepadService.Companion.Mode,
    onModeChange: (GamepadService.Companion.Mode) -> Unit,
    activeProfileId: String,
    onProfileChange: (String) -> Unit,
    sensitivity: Float,
    onSensitivityChange: (Float) -> Unit,
    deadZone: Float,
    onDeadZoneChange: (Float) -> Unit,
    gyroEnabled: Boolean,
    onGyroChange: (Boolean) -> Unit,
    onEditLayoutClick: () -> Unit,
    onStartGamepad: () -> Unit,
    onDisconnect: () -> Unit,
    onNavigateToProfile: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isConnected = connectionState == BluetoothGamepadService.ConnectionState.CONNECTED

    val pagerState = rememberPagerState(
        initialPage = controllerProfiles.indexOfFirst { it.id == activeProfileId }.coerceAtLeast(0),
        pageCount = { controllerProfiles.size }
    )

    LaunchedEffect(pagerState.currentPage) {
        onProfileChange(controllerProfiles[pagerState.currentPage].id)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Bg0, Bg1, Bg1)))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        // ══════════════════════════════════════════════════════════════════
        // HEADER
        // ══════════════════════════════════════════════════════════════════
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Bg2)
                .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Logo mark + title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Canvas logo mark
                Canvas(modifier = Modifier.size(34.dp)) {
                    val cx = size.width / 2f; val cy = size.height / 2f
                    drawCircle(color = Bg3, radius = cx * 0.85f, center = Offset(cx, cy))
                    drawCircle(color = AccentBlue.copy(alpha = 0.5f), radius = cx * 0.85f, center = Offset(cx, cy), style = Stroke(1.5f))
                    drawLine(AccentBlue.copy(alpha = 0.6f), Offset(cx - 6f, cy), Offset(cx + 6f, cy), strokeWidth = 2f)
                    drawLine(AccentBlue.copy(alpha = 0.6f), Offset(cx, cy - 6f), Offset(cx, cy + 6f), strokeWidth = 2f)
                    drawCircle(AccentCyan.copy(alpha = 0.85f), radius = 2.5f, center = Offset(cx - 5f, cy - 5f))
                    drawCircle(AccentCyan.copy(alpha = 0.85f), radius = 2.5f, center = Offset(cx + 5f, cy + 5f))
                }
                Column {
                    Text("MacGamepad", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.3).sp, color = TextPrimary)
                    Text("Bluetooth Controller", fontSize = 9.sp, color = TextTertiary, letterSpacing = 0.3.sp)
                }
            }

            // Right: nav buttons + connection pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Profile button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Bg3)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(9.dp))
                        .clickable { onNavigateToProfile() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Gamepad, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
                // Settings button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Bg3)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(9.dp))
                        .clickable { onNavigateToSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Settings, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(2.dp))
                // Connection status pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isConnected) StatusConnected.copy(alpha = 0.08f) else Bg3)
                        .border(
                            1.dp,
                            if (isConnected) StatusConnected.copy(alpha = 0.35f) else BorderSubtle,
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .then(if (isConnected) Modifier.clickable { onDisconnect() } else Modifier),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (isConnected) StatusConnected else StatusConnecting))
                    Text(
                        text = if (isConnected) (connectedDeviceName ?: "Connected") else "Disconnected",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isConnected) StatusConnected else TextSecondary
                    )
                    if (isConnected) { Icon(Icons.Default.Close, null, tint = AccentRed.copy(alpha = 0.6f), modifier = Modifier.size(10.dp)) }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════
        // PROFILE CAROUSEL LABEL
        // ══════════════════════════════════════════════════════════════════
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "CONTROLLER SKIN",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = TextTertiary
            )
            // Pager dots
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                controllerProfiles.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .size(if (index == pagerState.currentPage) 18.dp else 5.dp, 5.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == pagerState.currentPage)
                                    controllerProfiles[index].accentColor
                                else
                                    BorderMedium
                            )
                            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessHigh))
                    )
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════
        // CONTROLLER SKIN CARDS (Pager)
        // ══════════════════════════════════════════════════════════════════
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().height(200.dp),
            contentPadding = PaddingValues(horizontal = 32.dp),
            pageSpacing = 12.dp,
            verticalAlignment = Alignment.CenterVertically
        ) { page ->
            val profile = controllerProfiles[page]
            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
            val scale = lerp(0.88f, 1f, 1f - pageOffset.coerceIn(0f, 1f))
            val pageAlpha = lerp(0.45f, 1f, 1f - pageOffset.coerceIn(0f, 1f))

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = pageAlpha }
                    .clip(RoundedCornerShape(20.dp))
                    .background(Bg2)
                    .border(
                        width = if (page == pagerState.currentPage) 1.5.dp else 1.dp,
                        color = if (page == pagerState.currentPage)
                            profile.accentColor.copy(alpha = 0.45f)
                        else
                            BorderSubtle,
                        shape = RoundedCornerShape(20.dp)
                    )
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(profile.accentColor.copy(alpha = 0.1f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(profile.tagline, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp, color = profile.accentColor)
                        }
                        Text(profile.name, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp, color = TextPrimary, lineHeight = 26.sp)
                        Text(profile.description, fontSize = 10.sp, color = TextSecondary, lineHeight = 15.sp)
                    }
                    // Right: controller art (clean, no gradients)
                    Canvas(modifier = Modifier.size(110.dp, 80.dp)) {
                        drawControllerArt(profile)
                    }
                }
            }
        }

        // ══════════════════════════════════════════════════════════════════
        // SETTINGS PANEL
        // ══════════════════════════════════════════════════════════════════
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(Bg2)
                .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Text(
                "SETTINGS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = TextTertiary,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            // ── Output Protocol ───────────────────────────────────────────
            SettingRow(
                icon = Icons.Default.Gamepad,
                iconTint = AccentBlue,
                title = "Output Protocol",
                subtitle = "How your Mac reads the inputs"
            ) {
                // Segmented control
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(Bg3)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(9.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf(
                        GamepadService.Companion.Mode.GAMEPAD to "Gamepad",
                        GamepadService.Companion.Mode.KEYBOARD to "Keyboard"
                    ).forEach { (m, label) ->
                        val selected = mode == m
                        Text(
                            label,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) Color.White else TextSecondary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(if (selected) AccentBlue else Color.Transparent)
                                .clickable { onModeChange(m) }
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Mode hint (only shown in Gamepad mode — BUG FIX: was always visible)
            AnimatedVisibility(
                visible = (mode == GamepadService.Companion.Mode.GAMEPAD),
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentAmber.copy(alpha = 0.07f))
                        .border(1.dp, AccentAmber.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(
                        "Tip: In emulators, select your controller (not Keyboard) in Player 1 settings.",
                        fontSize = 9.sp,
                        color = AccentAmber.copy(alpha = 0.85f),
                        lineHeight = 13.sp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))

            // ── Stick Sensitivity ─────────────────────────────────────────
            SliderSettingRow(
                icon = Icons.Default.Tune,
                iconTint = AccentPurple,
                title = "Stick Sensitivity",
                value = sensitivity,
                displayValue = "${(sensitivity * 100).toInt()}%",
                valueRange = 0.5f..2.0f,
                accentColor = AccentPurple,
                onValueChange = onSensitivityChange
            )

            Spacer(Modifier.height(4.dp))

            // ── Dead Zone ─────────────────────────────────────────────────
            SliderSettingRow(
                icon = Icons.Default.CenterFocusWeak,
                iconTint = AccentCyan,
                title = "Stick Dead Zone",
                value = deadZone,
                displayValue = "${(deadZone * 100).toInt()}%",
                valueRange = 0.05f..0.30f,
                accentColor = AccentCyan,
                onValueChange = onDeadZoneChange
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))

            // ── Gyro removed (not implemented in HID descriptor) ──────────
            // Use Settings screen for advanced options


            // ── Customize Layout ──────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (isConnected)
                            Modifier
                                .background(Bg3)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                                .clickable { onEditLayoutClick() }
                        else
                            Modifier.background(Bg2).border(1.dp, BorderSubtle.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentBlue.copy(alpha = if (isConnected) 0.12f else 0.05f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.DashboardCustomize, null, tint = AccentBlue.copy(alpha = if (isConnected) 1f else 0.3f), modifier = Modifier.size(16.dp))
                    }
                    Column {
                        Text("Customize Layout", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (isConnected) TextPrimary else TextTertiary)
                        Text("Drag & pinch buttons, sticks", fontSize = 9.sp, color = TextTertiary)
                    }
                }
                Icon(
                    if (isConnected) Icons.Default.ChevronRight else Icons.Default.Lock,
                    null,
                    tint = if (isConnected) AccentBlue.copy(alpha = 0.7f) else TextTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // ══════════════════════════════════════════════════════════════════
        // CTA BUTTON
        // ══════════════════════════════════════════════════════════════════
        val currentProfile = controllerProfiles[pagerState.currentPage]
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .then(
                    if (isConnected) {
                        Modifier.background(
                            Brush.linearGradient(
                                listOf(
                                    currentProfile.accentColor,
                                    currentProfile.glowColor.copy(alpha = 0.8f)
                                )
                            )
                        )
                    } else {
                        Modifier.background(Bg3).border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
                    }
                )
                .clickable(enabled = isConnected) { onStartGamepad() },
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (isConnected) Icons.Default.SportsEsports else Icons.Default.BluetoothDisabled,
                    null,
                    tint = if (isConnected) Color.White else TextTertiary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    if (isConnected) "Launch ${currentProfile.name}" else "Connect to Mac First",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = if (isConnected) Color.White else TextTertiary,
                    letterSpacing = (-0.2).sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable settings row composables
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SettingRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconTint.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(16.dp))
            }
            Column {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(subtitle, fontSize = 9.sp, color = TextSecondary)
            }
        }
        trailing()
    }
}

@Composable
private fun SliderSettingRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    value: Float,
    displayValue: String,
    valueRange: ClosedFloatingPointRange<Float>,
    accentColor: Color,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconTint.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = iconTint, modifier = Modifier.size(16.dp))
                }
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            }
            // Value pill badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.1f))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(displayValue, fontSize = 11.sp, color = accentColor, fontWeight = FontWeight.Bold)
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            modifier = Modifier.padding(start = 40.dp),
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = Bg4
            )
        )
    }
}
