package com.macgamepad.controller.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macgamepad.controller.ui.theme.*

/**
 * Realistic Action Button with console-quality 3D render and zero touch latency.
 */
@Composable
fun ActionButton(
    label: String,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 54.dp,
    color: Color = AccentBlue,
    onPressChanged: (Boolean) -> Unit = {}
) {
    var isPressed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .size(buttonSize)
            .pointerInput(buttonSize, onPressChanged) {
                // Immediate low-latency touch handler bypassing drag slop
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        if (change.pressed) {
                            change.consume()
                            if (!isPressed) {
                                isPressed = true
                                onPressChanged(true)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        } else {
                            if (isPressed) {
                                isPressed = false
                                onPressChanged(false)
                            }
                        }
                    }
                }
            }
    ) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension / 2f

        // Outer glow halo when pressed
        if (isPressed) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.45f), Color.Transparent),
                    center = center,
                    radius = radius * 1.35f
                ),
                radius = radius * 1.35f,
                center = center
            )
        }

        // Drop shadow for 3D depth
        drawCircle(
            color = Color.Black.copy(alpha = 0.4f),
            radius = radius,
            center = Offset(center.x + 2f, center.y + 3f)
        )

        // Glassmorphic / Glossy surface styling
        val surfaceBrush = Brush.verticalGradient(
            colors = if (isPressed) {
                listOf(color.copy(alpha = 0.9f), color.copy(alpha = 0.6f))
            } else {
                listOf(Color(0xFF32323E), Color(0xFF1E1E24))
            }
        )

        drawCircle(
            brush = surfaceBrush,
            radius = radius - 1.5f,
            center = center
        )

        // Metallic / Colored border bezel
        drawCircle(
            color = if (isPressed) Color.White.copy(alpha = 0.8f) else color.copy(alpha = 0.3f),
            radius = radius - 1.5f,
            center = center,
            style = Stroke(width = 2.5f)
        )

        // Subtle specular highlight on top rim (giving glossy look)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.15f), Color.Transparent),
                center = Offset(center.x, center.y - radius * 0.4f),
                radius = radius * 0.6f
            ),
            radius = radius * 0.6f,
            center = Offset(center.x, center.y - radius * 0.4f)
        )

        // Label layout
        val textLayout = textMeasurer.measure(
            text = AnnotatedString(label),
            style = TextStyle(
                color = if (isPressed) Color.White else color,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black
            )
        )
        drawText(
            textLayoutResult = textLayout,
            topLeft = Offset(
                center.x - textLayout.size.width / 2f,
                center.y - textLayout.size.height / 2f
            )
        )
    }
}

/**
 * ABXY diamond button cluster.
 */
@Composable
fun ActionButtonCluster(
    modifier: Modifier = Modifier,
    buttonSize: Dp = 52.dp,
    labels: List<String> = listOf("A", "B", "X", "Y"),
    colors: List<Color> = listOf(AccentGreen, AccentRed, AccentBlue, AccentAmber),
    onButtonChanged: (Int, Boolean) -> Unit = { _, _ -> }
) {
    val spacing = buttonSize * 0.15f

    Box(
        modifier = modifier.size(buttonSize * 3 + spacing * 2),
        contentAlignment = Alignment.Center
    ) {
        // Y — top
        ActionButton(
            label = labels[3],
            buttonSize = buttonSize,
            color = colors[3],
            modifier = Modifier.align(Alignment.TopCenter),
            onPressChanged = { onButtonChanged(3, it) }
        )
        // X — left
        ActionButton(
            label = labels[2],
            buttonSize = buttonSize,
            color = colors[2],
            modifier = Modifier.align(Alignment.CenterStart),
            onPressChanged = { onButtonChanged(2, it) }
        )
        // B — right
        ActionButton(
            label = labels[1],
            buttonSize = buttonSize,
            color = colors[1],
            modifier = Modifier.align(Alignment.CenterEnd),
            onPressChanged = { onButtonChanged(1, it) }
        )
        // A — bottom
        ActionButton(
            label = labels[0],
            buttonSize = buttonSize,
            color = colors[0],
            modifier = Modifier.align(Alignment.BottomCenter),
            onPressChanged = { onButtonChanged(0, it) }
        )
    }
}

/**
 * Console-accurate D-Pad.
 */
@Composable
fun DPad(
    modifier: Modifier = Modifier,
    padSize: Dp = 130.dp,
    onDirectionChanged: (up: Boolean, down: Boolean, left: Boolean, right: Boolean) -> Unit = { _, _, _, _ -> }
) {
    var upPressed by remember { mutableStateOf(false) }
    var downPressed by remember { mutableStateOf(false) }
    var leftPressed by remember { mutableStateOf(false) }
    var rightPressed by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val armWidth = padSize * 0.32f

    Box(
        modifier = modifier.size(padSize),
        contentAlignment = Alignment.Center
    ) {
        // Main DPad cross plate shadow
        Canvas(modifier = Modifier.size(padSize)) {
            drawCircle(
                color = Color.Black.copy(alpha = 0.4f),
                radius = size.minDimension / 2f
            )
        }

        // Up Arm
        DPadArm(
            label = "▲",
            modifier = Modifier.align(Alignment.TopCenter).size(armWidth, padSize * 0.35f),
            onPressChanged = {
                upPressed = it
                if (it) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onDirectionChanged(upPressed, downPressed, leftPressed, rightPressed)
            }
        )
        // Down Arm
        DPadArm(
            label = "▼",
            modifier = Modifier.align(Alignment.BottomCenter).size(armWidth, padSize * 0.35f),
            onPressChanged = {
                downPressed = it
                if (it) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onDirectionChanged(upPressed, downPressed, leftPressed, rightPressed)
            }
        )
        // Left Arm
        DPadArm(
            label = "◀",
            modifier = Modifier.align(Alignment.CenterStart).size(padSize * 0.35f, armWidth),
            onPressChanged = {
                leftPressed = it
                if (it) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onDirectionChanged(upPressed, downPressed, leftPressed, rightPressed)
            }
        )
        // Right Arm
        DPadArm(
            label = "▶",
            modifier = Modifier.align(Alignment.CenterEnd).size(padSize * 0.35f, armWidth),
            onPressChanged = {
                rightPressed = it
                if (it) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onDirectionChanged(upPressed, downPressed, leftPressed, rightPressed)
            }
        )

        // Center hub well (glowing bezel)
        Canvas(modifier = Modifier.size(armWidth).align(Alignment.Center)) {
            drawRoundRect(
                color = Color(0xFF101014),
                cornerRadius = CornerRadius(8f, 8f),
                size = size
            )
        }
    }
}

@Composable
private fun DPadArm(
    label: String,
    modifier: Modifier = Modifier,
    onPressChanged: (Boolean) -> Unit = {}
) {
    var isPressed by remember { mutableStateOf(false) }
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .pointerInput(onPressChanged) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        if (change.pressed) {
                            change.consume()
                            if (!isPressed) {
                                isPressed = true
                                onPressChanged(true)
                            }
                        } else {
                            if (isPressed) {
                                isPressed = false
                                onPressChanged(false)
                            }
                        }
                    }
                }
            }
    ) {
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = if (isPressed) {
                    listOf(AccentBlue.copy(alpha = 0.9f), AccentBlue.copy(alpha = 0.6f))
                } else {
                    listOf(Color(0xFF282830), Color(0xFF1E1E24))
                }
            ),
            cornerRadius = CornerRadius(12f, 12f),
            size = size
        )
        drawRoundRect(
            color = if (isPressed) Color.White.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.05f),
            cornerRadius = CornerRadius(12f, 12f),
            size = size,
            style = Stroke(width = 1.5f)
        )

        val textLayout = textMeasurer.measure(
            text = AnnotatedString(label),
            style = TextStyle(
                color = if (isPressed) Color.White else TextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        )
        drawText(
            textLayoutResult = textLayout,
            topLeft = Offset(
                (size.width - textLayout.size.width) / 2f,
                (size.height - textLayout.size.height) / 2f
            )
        )
    }
}

/**
 * Wide Rectangular Shoulder Button (L1/R1/L2/R2)
 */
@Composable
fun ShoulderButton(
    label: String,
    modifier: Modifier = Modifier,
    color: Color = AccentBlue,
    onPressChanged: (Boolean) -> Unit = {}
) {
    var isPressed by remember { mutableStateOf(false) }
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .size(75.dp, 36.dp)
            .pointerInput(onPressChanged) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        if (change.pressed) {
                            change.consume()
                            if (!isPressed) {
                                isPressed = true
                                onPressChanged(true)
                            }
                        } else {
                            if (isPressed) {
                                isPressed = false
                                onPressChanged(false)
                            }
                        }
                    }
                }
            }
    ) {
        val size = Size(size.width, size.height)

        // Glow
        if (isPressed) {
            drawRoundRect(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.35f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.width
                ),
                size = size,
                cornerRadius = CornerRadius(12f, 12f)
            )
        }

        // Drop Shadow
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.5f),
            topLeft = Offset(1f, 2f),
            size = size,
            cornerRadius = CornerRadius(12f, 12f)
        )

        // Body
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = if (isPressed) {
                    listOf(color.copy(alpha = 0.9f), color.copy(alpha = 0.6f))
                } else {
                    listOf(Color(0xFF2C2C35), Color(0xFF1E1E24))
                }
            ),
            size = size,
            cornerRadius = CornerRadius(12f, 12f)
        )

        // Border Bezel
        drawRoundRect(
            color = if (isPressed) Color.White.copy(alpha = 0.6f) else color.copy(alpha = 0.2f),
            size = size,
            cornerRadius = CornerRadius(12f, 12f),
            style = Stroke(width = 1.5f)
        )

        // Specular highlight
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent)
            ),
            size = Size(size.width, size.height * 0.4f),
            cornerRadius = CornerRadius(12f, 12f)
        )

        // Text
        val textLayout = textMeasurer.measure(
            text = AnnotatedString(label),
            style = TextStyle(
                color = if (isPressed) Color.White else color,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold
            )
        )
        drawText(
            textLayoutResult = textLayout,
            topLeft = Offset(
                (size.width - textLayout.size.width) / 2f,
                (size.height - textLayout.size.height) / 2f
            )
        )
    }
}

/**
 * Small Oval System Button (Select, Options, Home)
 */
@Composable
fun SystemButton(
    label: String,
    modifier: Modifier = Modifier,
    width: Dp = 60.dp,
    height: Dp = 22.dp,
    onPressChanged: (Boolean) -> Unit = {}
) {
    var isPressed by remember { mutableStateOf(false) }
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .size(width, height)
            .pointerInput(width, height, onPressChanged) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        if (change.pressed) {
                            change.consume()
                            if (!isPressed) {
                                isPressed = true
                                onPressChanged(true)
                            }
                        } else {
                            if (isPressed) {
                                isPressed = false
                                onPressChanged(false)
                            }
                        }
                    }
                }
            }
    ) {
        val size = Size(size.width, size.height)

        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = if (isPressed) {
                    listOf(Color.LightGray, Color.Gray)
                } else {
                    listOf(Color(0xFF282830), Color(0xFF14141A))
                }
            ),
            size = size,
            cornerRadius = CornerRadius(16f, 16f)
        )

        drawRoundRect(
            color = Color.White.copy(alpha = 0.08f),
            size = size,
            cornerRadius = CornerRadius(16f, 16f),
            style = Stroke(width = 1f)
        )

        val textLayout = textMeasurer.measure(
            text = AnnotatedString(label),
            style = TextStyle(
                color = if (isPressed) Color.Black else TextSecondary,
                fontSize = 8.sp,
                fontWeight = FontWeight.ExtraBold
            )
        )
        drawText(
            textLayoutResult = textLayout,
            topLeft = Offset(
                (size.width - textLayout.size.width) / 2f,
                (size.height - textLayout.size.height) / 2f
            )
        )
    }
}
