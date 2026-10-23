package com.macgamepad.controller.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.macgamepad.controller.ui.theme.*
import kotlin.math.*

/**
 * Hyper-responsive, zero-latency virtual analog stick with realistic console visuals.
 * Fully multi-touch pointer isolated and calibrated with physical response curves to eliminate drift.
 */
@Composable
fun VirtualStick(
    modifier: Modifier = Modifier,
    size: Dp = 140.dp,
    deadZone: Float = 0.15f,
    accentColor: Color = StickThumbActive,
    onValueChange: (Float, Float) -> Unit = { _, _ -> }
) {
    var thumbOffset by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }
    var hasLeftDeadZone by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }

    // Calibration settings matching actual Xbox/PlayStation stick profiles
    val responseCurvePower = 1.7f // Allows fine control near center (precision aiming)

    Canvas(
        modifier = modifier
            .size(size)
            .pointerInput(size, deadZone, onValueChange) {
                awaitPointerEventScope {
                    var activePointerId: PointerId? = null
                    
                    while (true) {
                        val event = awaitPointerEvent()
                        val center = Offset(sizePx / 2f, sizePx / 2f)
                        val maxRadius = sizePx / 2f
                        val activeRadiusLimit = maxRadius * 0.65f // Actual physical travel zone limit

                        // Find our active pointer or bind to a new touch
                        val change = if (activePointerId == null) {
                            val newTouch = event.changes.firstOrNull { it.pressed }
                            if (newTouch != null) {
                                activePointerId = newTouch.id
                            }
                            newTouch
                        } else {
                            event.changes.firstOrNull { it.id == activePointerId }
                        }

                        if (change != null && change.pressed) {
                            change.consume()
                            isDragging = true
                            
                            val position = change.position
                            val dx = position.x - center.x
                            val dy = position.y - center.y
                            val distance = sqrt(dx * dx + dy * dy)
                            val angle = atan2(dy, dx)

                            // Clamp thumb displacement visually to travel limits
                            val clampedRadius = min(distance, activeRadiusLimit)
                            val tx = cos(angle) * clampedRadius
                            val ty = sin(angle) * clampedRadius
                            thumbOffset = Offset(tx, ty)

                            // Normalized distance: 0.0 at center, 1.0 at maximum travel bounds
                            val normalizedDistance = (clampedRadius / activeRadiusLimit).coerceIn(0f, 1f)
                            val leftDz = normalizedDistance > deadZone
                            
                            if (leftDz && !hasLeftDeadZone) {
                                hasLeftDeadZone = true
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } else if (!leftDz) {
                                hasLeftDeadZone = false
                            }

                            // Calculate calibrated output
                            val outputDistance = if (leftDz) {
                                // Linear rescale outside the deadzone: maps (deadZone..1.0) to (0.0..1.0)
                                val rescaled = (normalizedDistance - deadZone) / (1f - deadZone)
                                // Apply exponential calibration curve (curve power > 1.0 reduces sensitivity near deadzone)
                                rescaled.pow(responseCurvePower).coerceIn(0f, 1f)
                            } else {
                                0f
                            }

                            val targetX = cos(angle) * outputDistance
                            val targetY = sin(angle) * outputDistance

                            onValueChange(targetX, targetY)
                        } else {
                            // Touch released or pointer cancelled: snap stick back to zero instantly
                            activePointerId = null
                            isDragging = false
                            hasLeftDeadZone = false
                            thumbOffset = Offset.Zero
                            onValueChange(0f, 0f)
                        }
                    }
                }
            }
    ) {
        val center = Offset(this.size.width / 2f, this.size.height / 2f)
        val baseRadius = min(this.size.width, this.size.height) / 2f
        val thumbRadius = baseRadius * 0.44f

        // Draw realistic 3D stick base housing
        drawStickBaseHousing(center, baseRadius, isDragging, accentColor)

        // Draw floating analog thumb stick cap
        val thumbCenter = if (isDragging) {
            Offset(center.x + thumbOffset.x, center.y + thumbOffset.y)
        } else {
            center
        }
        drawRealisticThumbCap(thumbCenter, thumbRadius, isDragging, accentColor)
    }
}

private fun DrawScope.drawStickBaseHousing(
    center: Offset,
    radius: Float,
    isActive: Boolean,
    accentColor: Color
) {
    // Outer halo glow
    if (isActive) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(accentColor.copy(alpha = 0.18f), Color.Transparent),
                center = center,
                radius = radius * 1.25f
            ),
            radius = radius * 1.2f,
            center = center
        )
    }

    // Outer plastic housing shadow
    drawCircle(
        color = Color.Black.copy(alpha = 0.6f),
        radius = radius,
        center = center,
        style = Stroke(width = 4f)
    )

    // Deep inner well gradient (giving depth to the joystick well)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF020205), Color(0xFF14141E)),
            center = center,
            radius = radius
        ),
        radius = radius - 2f,
        center = center
    )

    // Well accent ring border
    drawCircle(
        color = if (isActive) accentColor.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.08f),
        radius = radius - 2f,
        center = center,
        style = Stroke(width = 1.5f)
    )

    // Directional axis guidelines (highly professional cross lines)
    val guideColor = Color.White.copy(alpha = 0.03f)
    drawLine(guideColor, Offset(center.x - radius, center.y), Offset(center.x + radius, center.y), strokeWidth = 1f)
    drawLine(guideColor, Offset(center.x, center.y - radius), Offset(center.x, center.y + radius), strokeWidth = 1f)
}

private fun DrawScope.drawRealisticThumbCap(
    center: Offset,
    radius: Float,
    isActive: Boolean,
    accentColor: Color
) {
    // Drop shadow
    drawCircle(
        color = Color.Black.copy(alpha = 0.45f),
        radius = radius + 3f,
        center = Offset(center.x + 3f, center.y + 4f)
    )

    // Concave thumb stick body
    drawCircle(
        brush = Brush.linearGradient(
            colors = if (isActive) {
                listOf(Color(0xFF2E2E3A), Color(0xFF1C1C24))
            } else {
                listOf(Color(0xFF40404C), Color(0xFF24242D))
            }
        ),
        radius = radius,
        center = center
    )

    // Outer grip texture rim (ridged feeling)
    drawCircle(
        color = if (isActive) accentColor.copy(alpha = 0.8f) else Color(0xFF101015),
        radius = radius - 2f,
        center = center,
        style = Stroke(width = 3.5f)
    )

    // Inner concave indent circle (giving that physical PlayStation/Xbox thumbwell)
    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFF101015), Color(0xFF383845))
        ),
        radius = radius * 0.72f,
        center = center
    )

    // Center indicator dot matching controller accents
    drawCircle(
        color = if (isActive) accentColor.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.05f),
        radius = radius * 0.22f,
        center = center
    )

    // Subtle center dome highlight (makes the stick feel curved and 3D)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
            center = Offset(center.x - radius * 0.1f, center.y - radius * 0.1f),
            radius = radius * 0.4f
        ),
        radius = radius * 0.4f,
        center = Offset(center.x - radius * 0.1f, center.y - radius * 0.1f)
    )
}
