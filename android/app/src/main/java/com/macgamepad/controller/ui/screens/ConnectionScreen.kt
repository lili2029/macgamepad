package com.macgamepad.controller.ui.screens

import android.bluetooth.BluetoothDevice
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macgamepad.controller.bt.BluetoothGamepadService
import com.macgamepad.controller.ui.theme.*

@Composable
fun ConnectionScreen(
    connectionState: BluetoothGamepadService.ConnectionState,
    connectedDeviceName: String?,
    errorMessage: String?,
    pairedDevices: List<BluetoothDevice>,
    autoConnecting: Boolean,
    autoConnectFailed: Boolean,
    lastHostName: String?,
    onDeviceConnectClick: (BluetoothDevice) -> Unit,
    onConnectClick: () -> Unit,
    onRetryAutoConnect: () -> Unit,
    onCancelAutoConnect: () -> Unit,
    onDisconnectClick: () -> Unit,
    onStartGamepad: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Bg0, Bg1, Bg1))
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        // Ambient radial glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(AccentBlue.copy(alpha = 0.06f), Color.Transparent),
                            radius = size.minDimension * 0.75f
                        ),
                        radius = size.minDimension * 0.75f
                    )
                }
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .padding(vertical = 20.dp)
        ) {

            // ══════════════════════════════════════════════════════════════
            // LOGO MARK + TITLE
            // ══════════════════════════════════════════════════════════════
            Spacer(Modifier.height(8.dp))

            // Canvas-drawn controller logo
            Canvas(modifier = Modifier.size(88.dp)) {
                val cx = size.width / 2f; val cy = size.height / 2f
                val bw = size.width; val bh = size.height

                // Outer glow halo
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(AccentBlue.copy(alpha = 0.18f), Color.Transparent),
                        center = Offset(cx, cy), radius = cx * 1.3f
                    ),
                    radius = cx * 1.3f, center = Offset(cx, cy)
                )

                // Controller body — main wide pill
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(Bg4, Bg3)),
                    topLeft = Offset(bw * 0.08f, bh * 0.28f),
                    size = Size(bw * 0.84f, bh * 0.40f),
                    cornerRadius = CornerRadius(20f, 20f)
                )
                drawRoundRect(
                    color = AccentBlue.copy(alpha = 0.45f),
                    topLeft = Offset(bw * 0.08f, bh * 0.28f),
                    size = Size(bw * 0.84f, bh * 0.40f),
                    cornerRadius = CornerRadius(20f, 20f),
                    style = Stroke(1.5f)
                )

                // Left grip
                drawRoundRect(
                    color = Bg3,
                    topLeft = Offset(bw * 0.14f, bh * 0.52f),
                    size = Size(bw * 0.22f, bh * 0.30f),
                    cornerRadius = CornerRadius(12f, 12f)
                )
                drawRoundRect(
                    color = AccentBlue.copy(alpha = 0.35f),
                    topLeft = Offset(bw * 0.14f, bh * 0.52f),
                    size = Size(bw * 0.22f, bh * 0.30f),
                    cornerRadius = CornerRadius(12f, 12f),
                    style = Stroke(1.2f)
                )

                // Right grip
                drawRoundRect(
                    color = Bg3,
                    topLeft = Offset(bw * 0.64f, bh * 0.52f),
                    size = Size(bw * 0.22f, bh * 0.30f),
                    cornerRadius = CornerRadius(12f, 12f)
                )
                drawRoundRect(
                    color = AccentBlue.copy(alpha = 0.35f),
                    topLeft = Offset(bw * 0.64f, bh * 0.52f),
                    size = Size(bw * 0.22f, bh * 0.30f),
                    cornerRadius = CornerRadius(12f, 12f),
                    style = Stroke(1.2f)
                )

                // Left shoulder
                drawRoundRect(
                    color = Bg4,
                    topLeft = Offset(bw * 0.08f, bh * 0.18f),
                    size = Size(bw * 0.24f, bh * 0.14f),
                    cornerRadius = CornerRadius(8f, 8f)
                )
                // Right shoulder
                drawRoundRect(
                    color = Bg4,
                    topLeft = Offset(bw * 0.68f, bh * 0.18f),
                    size = Size(bw * 0.24f, bh * 0.14f),
                    cornerRadius = CornerRadius(8f, 8f)
                )

                // Left stick
                drawCircle(
                    brush = Brush.radialGradient(listOf(AccentCyan.copy(alpha = 0.6f), AccentBlue.copy(alpha = 0.2f))),
                    radius = 7f, center = Offset(bw * 0.29f, bh * 0.46f)
                )
                // Right stick
                drawCircle(
                    brush = Brush.radialGradient(listOf(AccentCyan.copy(alpha = 0.6f), AccentBlue.copy(alpha = 0.2f))),
                    radius = 7f, center = Offset(bw * 0.58f, bh * 0.52f)
                )

                // ABXY dots
                val btnColors = listOf(
                    Color(0xFF22C55E), Color(0xFFEF4444), Color(0xFF4D8EFF), Color(0xFFF59E0B)
                )
                val btnPos = listOf(
                    Offset(bw*0.74f, bh*0.40f),
                    Offset(bw*0.78f, bh*0.46f),
                    Offset(bw*0.70f, bh*0.46f),
                    Offset(bw*0.74f, bh*0.52f)
                )
                btnPos.zip(btnColors).forEach { (pos, col) ->
                    drawCircle(color = col.copy(alpha = 0.7f), radius = 4f, center = pos)
                }

                // Centre connection dot (pulsing feel via static for now)
                drawCircle(
                    brush = Brush.radialGradient(listOf(AccentBlue.copy(alpha = 0.9f), AccentCyan.copy(alpha = 0.0f))),
                    radius = 5f,
                    center = Offset(cx, cy)
                )
            }

            Spacer(Modifier.height(14.dp))

            // Title with gradient (using drawBehind glow)
            Text(
                text = "MacGamepad",
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp,
                color = TextPrimary,
                modifier = Modifier.drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(AccentBlue.copy(alpha = 0.25f), Color.Transparent),
                            radius = size.width * 0.6f
                        ),
                        radius = size.width * 0.6f
                    )
                }
            )
            Text(
                text = "蓝牙游戏手柄",
                fontSize = 12.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                letterSpacing = 0.3.sp
            )

            Spacer(Modifier.height(24.dp))

            // ══════════════════════════════════════════════════════════════
            // STATE CARD
            // ══════════════════════════════════════════════════════════════
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(Bg2)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(24.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Status badge
                ConnectionStatusBadge(connectionState, connectedDeviceName, autoConnecting)

                // Error box
                errorMessage?.let { err ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AccentRed.copy(alpha = 0.08f))
                            .border(1.dp, AccentRed.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(Icons.Default.ErrorOutline, null, tint = AccentRed, modifier = Modifier.size(14.dp).padding(top = 1.dp))
                            Text(err, fontSize = 11.sp, color = AccentRed, lineHeight = 15.sp)
                        }
                    }
                }

                // ── State-dependent UI ───────────────────────────────────
                // A boolean-driven `when`: the automatic reconnect outranks the raw
                // BT state, because mid-handshake the state bounces through
                // REGISTERING → REGISTERED_WAITING and would flash "可被发现" at the
                // player right before it connects.
                when {
                    autoConnecting -> AutoConnectPanel(
                        hostName = lastHostName,
                        onCancel = onCancelAutoConnect
                    )

                    connectionState == BluetoothGamepadService.ConnectionState.DISCONNECTED ||
                        connectionState == BluetoothGamepadService.ConnectionState.ERROR -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                if (lastHostName != null)
                                    "上次连接的是「$lastHostName」。\n点下面直接连回去，或重新初始化再配对。"
                                else
                                    "将本机注册为蓝牙 HID 手柄。\n然后在 Mac 的「蓝牙」设置里配对连接。",
                                fontSize = 11.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center,
                                lineHeight = 16.sp
                            )
                            PrimaryActionButton(
                                text = if (lastHostName != null) "连接 $lastHostName" else "初始化手柄",
                                icon = Icons.Default.Bluetooth,
                                onClick = if (lastHostName != null) onRetryAutoConnect else onConnectClick
                            )
                            if (lastHostName != null) {
                                Text(
                                    "初始化手柄（重新配对）",
                                    fontSize = 11.sp,
                                    color = TextTertiary,
                                    modifier = Modifier
                                        .clickable { onConnectClick() }
                                        .padding(6.dp)
                                )
                            }
                        }
                    }

                    connectionState == BluetoothGamepadService.ConnectionState.REGISTERING ||
                        connectionState == BluetoothGamepadService.ConnectionState.CONNECTING -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                color = AccentBlue,
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 2.5.dp
                            )
                            Text(
                                "正在注册蓝牙 HID 配置…",
                                fontSize = 12.sp,
                                color = TextSecondary,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    connectionState == BluetoothGamepadService.ConnectionState.REGISTERED_WAITING -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Pulsing beacon
                            PulsingBeacon()

                            Text(
                                if (lastHostName != null) "等待「$lastHostName」连接"
                                else "可被发现 · 等待 Mac 连接",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )

                            // The automatic attempt already ran and gave up: say so
                            // once, with a one-tap retry, instead of leaving the
                            // player wondering why nothing happened.
                            if (autoConnectFailed) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(AccentAmber.copy(alpha = 0.08f))
                                        .border(1.dp, AccentAmber.copy(alpha = 0.30f), RoundedCornerShape(12.dp))
                                        .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.SyncProblem, null, tint = AccentAmber, modifier = Modifier.size(14.dp))
                                    Text(
                                        "自动连接没成功，可以在 Mac 上点连接，或重试一次。",
                                        fontSize = 10.sp,
                                        color = AccentAmber,
                                        lineHeight = 14.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(AccentAmber.copy(alpha = 0.16f))
                                            .clickable { onRetryAutoConnect() }
                                            .padding(horizontal = 10.dp, vertical = 5.dp)
                                    ) {
                                        Text("重试", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentAmber)
                                    }
                                }
                            }

                            // Pairing guide is for the first pairing only; once a host
                            // is remembered the useful hint is "click the name on the
                            // Mac", not "delete the old pairing".
                            if (lastHostName == null) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Bg3)
                                        .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text("配对指引", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary, letterSpacing = 0.5.sp)
                                    PairingStepRow("1", "在 Mac 上打开「系统设置 → 蓝牙」")
                                    PairingStepRow("2", "先删掉旧的手机配对记录")
                                    PairingStepRow("3", "找到本机名称，点「连接」")
                                }
                            } else {
                                Text(
                                    "也可以在 Mac 的「蓝牙」里点本机名称，从主机侧发起。",
                                    fontSize = 10.sp,
                                    color = TextTertiary,
                                    textAlign = TextAlign.Center
                                )
                            }

                            // Paired hosts quick-connect list
                            // Filter to devices that are likely Mac hosts (name contains "Mac" or "MacBook")
                            val macHosts = remember(pairedDevices) {
                                pairedDevices.filter { device ->
                                    try {
                                        val name = device.name ?: ""
                                        name.contains("mac", ignoreCase = true) ||
                                        name.contains("book", ignoreCase = true) ||
                                        name.contains("imac", ignoreCase = true) ||
                                        name.contains("studio", ignoreCase = true) ||
                                        name.contains("mini", ignoreCase = true) ||
                                        name.isEmpty() // unknown = include to not miss valid hosts
                                    } catch (e: SecurityException) { true }
                                }
                            }

                            if (macHosts.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        "快速连接",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp,
                                        color = TextTertiary
                                    )
                                    macHosts.forEach { device ->
                                        val name = try { device.name ?: "Mac 主机" } catch (e: SecurityException) { "Mac 主机" }
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Bg4)
                                                .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                                                .clickable { onDeviceConnectClick(device) }
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Icon(Icons.Default.Laptop, null, tint = AccentBlue, modifier = Modifier.size(16.dp))
                                                Text(name, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                            }
                                            Icon(Icons.Default.ChevronRight, null, tint = TextSecondary, modifier = Modifier.size(15.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    connectionState == BluetoothGamepadService.ConnectionState.CONNECTED -> {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Launch button
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Brush.linearGradient(listOf(StatusConnected, Color(0xFF16A34A))))
                                    .clickable { onStartGamepad() },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.SportsEsports, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                    Text("启动手柄", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = Color.White)
                                }
                            }

                            // Disconnect link
                            Text(
                                "断开连接",
                                fontSize = 12.sp,
                                color = TextTertiary,
                                modifier = Modifier
                                    .clickable { onDisconnectClick() }
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Version footer
            Text(
                "v1.0 · MacGamepad",
                fontSize = 10.sp,
                color = TextTertiary,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Automatic reconnect — shown while the phone dials the host it used last time
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun AutoConnectPanel(hostName: String?, onCancel: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp)) {
            CircularProgressIndicator(
                color = AccentBlue,
                strokeWidth = 2.dp,
                modifier = Modifier.fillMaxSize()
            )
            Icon(Icons.Default.Bluetooth, null, tint = AccentBlue, modifier = Modifier.size(20.dp))
        }
        Text(
            if (hostName != null) "正在连接「$hostName」…" else "正在连接上次的主机…",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Text(
            "自动注册蓝牙 HID 并连回上次的 Mac，通常一两秒。",
            fontSize = 10.sp,
            color = TextTertiary,
            textAlign = TextAlign.Center
        )
        Text(
            "手动选择主机",
            fontSize = 11.sp,
            color = TextSecondary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onCancel() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Primary action button — the one thing the player is most likely to tap
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PrimaryActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(AccentBlue, Color(0xFF2563EB))))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text(text, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Pulsing beacon — shown during REGISTERED_WAITING state
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PulsingBeacon() {
    val infiniteTransition = rememberInfiniteTransition(label = "beacon")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp)) {
        // Outer pulse ring
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = AccentAmber.copy(alpha = pulseAlpha),
                radius = (size.minDimension / 2f) * pulseScale,
                style = Stroke(2f)
            )
        }
        // Inner static dot
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(AccentAmber.copy(alpha = 0.15f))
                .border(1.5.dp, AccentAmber.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Bluetooth, null, tint = AccentAmber, modifier = Modifier.size(12.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Status badge
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ConnectionStatusBadge(
    state: BluetoothGamepadService.ConnectionState,
    deviceName: String?,
    autoConnecting: Boolean = false
) {
    // The automatic attempt outranks the raw state: mid-handshake the stack reports
    // REGISTERED_WAITING, which would read as "可被发现" seconds before connecting.
    val (label, color) = when {
        autoConnecting -> "自动连接中…" to StatusConnecting
        state == BluetoothGamepadService.ConnectionState.DISCONNECTED   -> "未连接" to StatusDisconnected
        state == BluetoothGamepadService.ConnectionState.REGISTERING    -> "注册中…" to StatusConnecting
        state == BluetoothGamepadService.ConnectionState.REGISTERED_WAITING -> "可被发现" to StatusConnecting
        state == BluetoothGamepadService.ConnectionState.CONNECTING     -> "连接中…" to StatusConnecting
        state == BluetoothGamepadService.ConnectionState.CONNECTED      -> "已连接 · ${deviceName ?: "Mac"}" to StatusConnected
        else                                                            -> "出错" to StatusDisconnected
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(32.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(32.dp))
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Numbered pairing step row
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PairingStepRow(num: String, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(AccentBlue.copy(alpha = 0.15f))
                .border(1.dp, AccentBlue.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(num, fontSize = 9.sp, color = AccentBlue, fontWeight = FontWeight.ExtraBold)
        }
        Text(text, fontSize = 11.sp, color = TextSecondary, lineHeight = 16.sp, modifier = Modifier.weight(1f))
    }
}
