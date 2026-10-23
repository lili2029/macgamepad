package com.macgamepad.controller.ui.screens

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macgamepad.controller.bt.GamepadService
import com.macgamepad.controller.ui.theme.*

@Composable
fun SettingsScreen(
    // Input settings
    sensitivity: Float,
    onSensitivityChange: (Float) -> Unit,
    deadZone: Float,
    onDeadZoneChange: (Float) -> Unit,
    // Haptics
    hapticEnabled: Boolean,
    onHapticEnabledChange: (Boolean) -> Unit,
    hapticStrength: Float,
    onHapticStrengthChange: (Float) -> Unit,
    // Vibration
    vibrationEnabled: Boolean,
    onVibrationChange: (Boolean) -> Unit,
    // Connection
    autoReconnect: Boolean,
    onAutoReconnectChange: (Boolean) -> Unit,
    mode: GamepadService.Companion.Mode,
    onModeChange: (GamepadService.Companion.Mode) -> Unit,
    // Storage
    estimatedStorageBytes: Int,
    onClearAllLayouts: () -> Unit,
    // Navigation
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var showClearConfirm by remember { mutableStateOf(false) }

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
                Text("Settings", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        }

        HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {

            // ── Input ─────────────────────────────────────────────────────
            SettingsSection(title = "INPUT") {
                // Sensitivity
                SettingsSliderRow(
                    icon = Icons.Default.Tune,
                    iconTint = AccentPurple,
                    title = "Stick Sensitivity",
                    subtitle = "How far inputs are amplified",
                    value = sensitivity,
                    displayValue = "${(sensitivity * 100).toInt()}%",
                    valueRange = 0.5f..2.0f,
                    accentColor = AccentPurple,
                    onValueChange = onSensitivityChange
                )
                HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)

                // Dead zone
                SettingsSliderRow(
                    icon = Icons.Default.CenterFocusWeak,
                    iconTint = AccentCyan,
                    title = "Dead Zone",
                    subtitle = "Ignore small accidental movements",
                    value = deadZone,
                    displayValue = "${(deadZone * 100).toInt()}%",
                    valueRange = 0.05f..0.35f,
                    accentColor = AccentCyan,
                    onValueChange = onDeadZoneChange
                )
                HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)

                // Output protocol
                SettingsRow(
                    icon = Icons.Default.Gamepad,
                    iconTint = AccentBlue,
                    title = "Output Mode",
                    subtitle = "How your Mac reads the controller"
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Bg3)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
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
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (selected) AccentBlue else Color.Transparent)
                                    .clickable { onModeChange(m) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            // ── Haptics ───────────────────────────────────────────────────
            SettingsSection(title = "HAPTICS") {
                SettingsToggleRow(
                    icon = Icons.Default.Vibration,
                    iconTint = AccentGreen,
                    title = "Haptic Feedback",
                    subtitle = "Vibration on button presses",
                    checked = hapticEnabled,
                    onCheckedChange = onHapticEnabledChange
                )
                if (hapticEnabled) {
                    HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                    SettingsSliderRow(
                        icon = Icons.Default.GraphicEq,
                        iconTint = AccentGreen,
                        title = "Intensity",
                        subtitle = "Strength of haptic pulses",
                        value = hapticStrength,
                        displayValue = "${(hapticStrength * 100).toInt()}%",
                        valueRange = 0.1f..1.0f,
                        accentColor = AccentGreen,
                        onValueChange = onHapticStrengthChange
                    )
                }
                HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                SettingsToggleRow(
                    icon = Icons.Default.PhoneAndroid,
                    iconTint = AccentBlue,
                    title = "Controller Vibration",
                    subtitle = "Receive rumble events from Mac",
                    checked = vibrationEnabled,
                    onCheckedChange = onVibrationChange
                )
            }

            // ── Connection ────────────────────────────────────────────────
            SettingsSection(title = "CONNECTION") {
                SettingsToggleRow(
                    icon = Icons.Default.Autorenew,
                    iconTint = AccentCyan,
                    title = "Auto-Reconnect",
                    subtitle = "Reconnect to last host on app launch",
                    checked = autoReconnect,
                    onCheckedChange = onAutoReconnectChange
                )
            }

            // ── Storage ───────────────────────────────────────────────────
            SettingsSection(title = "STORAGE") {
                // Storage usage
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(AccentAmber.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Storage, null, tint = AccentAmber, modifier = Modifier.size(17.dp))
                        }
                        Column {
                            Text("App Data Size", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
                            Text("Settings + all layout data", fontSize = 10.sp, color = TextSecondary)
                        }
                    }
                    Text(
                        if (estimatedStorageBytes < 1024) "${estimatedStorageBytes} B"
                        else "${estimatedStorageBytes / 1024} KB",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary
                    )
                }
                HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)
                // Clear layouts
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showClearConfirm = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(AccentRed.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.DeleteOutline, null, tint = AccentRed, modifier = Modifier.size(17.dp))
                        }
                        Column {
                            Text("Clear All Layouts", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AccentRed)
                            Text("Resets button positions for all profiles", fontSize = 10.sp, color = TextSecondary)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = TextTertiary, modifier = Modifier.size(15.dp))
                }
            }

            // ── About ─────────────────────────────────────────────────────
            SettingsSection(title = "ABOUT") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("MacGamepad", fontSize = 16.sp, fontWeight = FontWeight.Black, color = TextPrimary)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(AccentBlue.copy(alpha = 0.1f))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text("v1.0.0", fontSize = 10.sp, color = AccentBlue, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Turn your Android phone into a Bluetooth game controller for Mac.",
                        fontSize = 11.sp,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 16.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("Supports PS5 · Xbox · Nintendo Switch Pro profiles", fontSize = 10.sp, color = TextTertiary)
                    Text("HID-over-Bluetooth · macOS compatible", fontSize = 10.sp, color = TextTertiary)
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    // ── Confirm dialog ───────────────────────────────────────────────────────
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            containerColor = Bg3,
            tonalElevation = 0.dp,
            title = {
                Text("Clear All Layouts?", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
            },
            text = {
                Text(
                    "This will reset button positions and sizes for all 3 controller profiles. This cannot be undone.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { onClearAllLayouts(); showClearConfirm = false }) {
                    Text("Clear All", color = AccentRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable section container
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
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
            content()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Settings row types
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(iconTint.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = iconTint, modifier = Modifier.size(17.dp))
            }
            Column {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(subtitle, fontSize = 10.sp, color = TextSecondary)
            }
        }
        trailing()
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingsRow(icon = icon, iconTint = iconTint, title = title, subtitle = subtitle) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentGreen,
                uncheckedThumbColor = TextTertiary,
                uncheckedTrackColor = Bg4
            )
        )
    }
}

@Composable
private fun SettingsSliderRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    value: Float,
    displayValue: String,
    valueRange: ClosedFloatingPointRange<Float>,
    accentColor: Color,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(iconTint.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = iconTint, modifier = Modifier.size(17.dp))
                }
                Column {
                    Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(subtitle, fontSize = 10.sp, color = TextSecondary)
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
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
            modifier = Modifier.padding(start = 42.dp),
            colors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = Bg4
            )
        )
    }
}
