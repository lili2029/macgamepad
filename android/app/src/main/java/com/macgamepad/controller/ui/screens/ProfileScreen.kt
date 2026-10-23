package com.macgamepad.controller.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macgamepad.controller.bt.BluetoothGamepadService
import com.macgamepad.controller.ui.theme.*

@Composable
fun ProfileScreen(
    activeProfileId: String,
    onProfileChange: (String) -> Unit,
    connectionState: BluetoothGamepadService.ConnectionState,
    connectedDeviceName: String?,
    onResetProfileLayout: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Bg1)
            .verticalScroll(scrollState)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Bg2)
                .border(width = 0.dp, color = Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Bg3)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ArrowBackIosNew, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
                }
                Text(
                    "Controller Profile",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        }

        HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Connection status ─────────────────────────────────────────
            val isConnected = connectionState == BluetoothGamepadService.ConnectionState.CONNECTED
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Bg2)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Connected Host",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextTertiary,
                        letterSpacing = 0.5.sp
                    )
                    Text(
                        if (isConnected) connectedDeviceName ?: "Connected" else "Not connected",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isConnected) TextPrimary else TextTertiary
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier.size(7.dp).clip(CircleShape)
                            .background(if (isConnected) StatusConnected else StatusDisconnected)
                    )
                    Text(
                        if (isConnected) "Active" else "Offline",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isConnected) StatusConnected else StatusDisconnected
                    )
                }
            }

            // ── Profile picker section label ──────────────────────────────
            Text(
                "CONTROLLER SKIN",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = TextTertiary,
                modifier = Modifier.padding(horizontal = 2.dp)
            )

            // ── Profile cards ─────────────────────────────────────────────
            controllerProfiles.forEach { profile ->
                val isActive = activeProfileId == profile.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isActive) profile.accentColor.copy(alpha = 0.08f) else Bg2)
                        .border(
                            width = if (isActive) 1.5.dp else 1.dp,
                            color = if (isActive) profile.accentColor.copy(alpha = 0.4f) else BorderSubtle,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { onProfileChange(profile.id) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Small controller art
                        Canvas(modifier = Modifier.size(44.dp, 32.dp)) {
                            val w = size.width; val h = size.height
                            val col = profile.accentColor

                            // Controller body
                            drawRoundRect(
                                color = col.copy(alpha = 0.15f),
                                topLeft = Offset(w * 0.05f, h * 0.20f),
                                size = Size(w * 0.90f, h * 0.55f),
                                cornerRadius = CornerRadius(8f, 8f)
                            )
                            drawRoundRect(
                                color = col.copy(alpha = 0.30f),
                                topLeft = Offset(w * 0.05f, h * 0.20f),
                                size = Size(w * 0.90f, h * 0.55f),
                                cornerRadius = CornerRadius(8f, 8f),
                                style = Stroke(1f)
                            )
                            // Left grip
                            drawRoundRect(color = col.copy(alpha = 0.10f),
                                topLeft = Offset(w * 0.10f, h * 0.60f), size = Size(w * 0.22f, h * 0.30f), cornerRadius = CornerRadius(5f, 5f))
                            // Right grip
                            drawRoundRect(color = col.copy(alpha = 0.10f),
                                topLeft = Offset(w * 0.68f, h * 0.60f), size = Size(w * 0.22f, h * 0.30f), cornerRadius = CornerRadius(5f, 5f))
                            // Left stick dot
                            drawCircle(color = col.copy(alpha = 0.40f), radius = 4f, center = Offset(w * 0.28f, h * 0.48f))
                            // Right stick dot
                            drawCircle(color = col.copy(alpha = 0.40f), radius = 4f, center = Offset(w * 0.62f, h * 0.55f))
                            // ABXY dots
                            listOf(Offset(w*0.76f, h*0.35f), Offset(w*0.80f, h*0.48f), Offset(w*0.72f, h*0.48f), Offset(w*0.76f, h*0.60f))
                                .forEach { drawCircle(color = col.copy(alpha = 0.35f), radius = 2.5f, center = it) }
                        }

                        Column {
                            Text(
                                profile.name,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isActive) TextPrimary else TextSecondary
                            )
                            Text(
                                profile.tagline,
                                fontSize = 11.sp,
                                color = if (isActive) profile.accentColor else TextTertiary
                            )
                        }
                    }

                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(profile.accentColor.copy(alpha = 0.15f))
                                .border(1.5.dp, profile.accentColor.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Check, null, tint = profile.accentColor, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }

            // ── Layout management ─────────────────────────────────────────
            val activeProfile = controllerProfiles.firstOrNull { it.id == activeProfileId }
            Text(
                "LAYOUT",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = TextTertiary,
                modifier = Modifier.padding(horizontal = 2.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Bg2)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            ) {
                // Info row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("${activeProfile?.name ?: ""} Layout", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("Custom button positions & sizes for this profile", fontSize = 11.sp, color = TextSecondary)
                    }
                }
                HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                // Reset row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onResetProfileLayout() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.RestartAlt, null, tint = AccentAmber, modifier = Modifier.size(18.dp))
                        Column {
                            Text("Reset to Default", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AccentAmber)
                            Text("Clears only this profile's layout", fontSize = 10.sp, color = TextTertiary)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = TextTertiary, modifier = Modifier.size(15.dp))
                }
            }

            // ── Controller info ───────────────────────────────────────────
            Text(
                "ABOUT THIS PROFILE",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = TextTertiary,
                modifier = Modifier.padding(horizontal = 2.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Bg2)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val (desc, btName, buttons) = when (activeProfileId) {
                    "ps5" -> Triple(
                        "Emulates a Sony DualSense Wireless Controller. Your Mac recognizes it as a PS5 gamepad — works with all games that support DualSense.",
                        "DualSense Wireless Controller",
                        "Cross · Circle · Square · Triangle · L1/L2/R1/R2 · L3/R3 · Touchpad · PS"
                    )
                    "xbox" -> Triple(
                        "Emulates an Xbox Wireless Controller. Windows-compatible layout recognized by macOS as an Xbox gamepad — ideal for games with Xbox controller support.",
                        "Xbox Wireless Controller",
                        "A · B · X · Y · LB/LT/RB/RT · Back · Start · Xbox"
                    )
                    else -> Triple(
                        "Emulates a Nintendo Switch Pro Controller. Recognized by emulators (Ryujinx, Yuzu) as a Pro Controller — ideal for Switch game library.",
                        "Pro Controller",
                        "B · A · Y · X · L/ZL/R/ZR · − · + · Home · Capture"
                    )
                }

                ProfileInfoRow("Appears As", btName)
                HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                ProfileInfoRow("Button Layout", buttons)
                HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)

                Text(
                    desc,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, fontSize = 10.sp, color = TextTertiary, fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp)
        Text(value, fontSize = 12.sp, color = TextPrimary, lineHeight = 16.sp)
    }
}
