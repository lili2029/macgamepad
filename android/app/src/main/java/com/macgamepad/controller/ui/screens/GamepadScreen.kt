package com.macgamepad.controller.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macgamepad.controller.input.InputState
import com.macgamepad.controller.ui.components.*
import com.macgamepad.controller.ui.theme.*
import kotlin.math.roundToInt
import kotlin.math.sqrt

// ─────────────────────────────────────────────────────────────────────────────
// DraggableLayoutElement
// The CORRECT approach for a movable/scalable container in Compose:
//   • local mutable state shadows the external offset/scale — avoids stale closure
//   • LaunchedEffect syncs from external (for reset, persistence)
//   • In edit mode, a transparent OVERLAY covers the entire element and consumes
//     ALL touch events, so the underlying button/stick never fires
//   • detectTransformGestures handles both 1-finger pan AND 2-finger pinch-to-scale
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DraggableLayoutElement(
    id: String,
    isEditMode: Boolean,
    externalOffset: Offset,
    externalScale: Float,
    onOffsetChanged: (String, Offset) -> Unit,
    onScaleChanged: (String, Float) -> Unit,
    label: String = "",
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    // Local mutable state — captured by reference in lambda, never stale
    var localOffset by remember(id) { mutableStateOf(externalOffset) }
    var localScale  by remember(id) { mutableStateOf(externalScale)  }

    // Sync when parent resets layout (externalOffset/Scale go to zero/1f)
    LaunchedEffect(externalOffset) { if (externalOffset != localOffset) localOffset = externalOffset }
    LaunchedEffect(externalScale)  { if (externalScale  != localScale)  localScale  = externalScale  }

    Box(
        modifier = modifier
            .offset { IntOffset(localOffset.x.roundToInt(), localOffset.y.roundToInt()) }
            .graphicsLayer { scaleX = localScale; scaleY = localScale }
    ) {
        // The actual button / stick content
        content()

        // ── Edit mode overlay ── (z-ordered above content → intercepts all touch)
        if (isEditMode) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.5.dp, Color(0xFF5BE9F0).copy(alpha = 0.85f), RoundedCornerShape(16.dp))
                    .background(Color(0xFF001820).copy(alpha = 0.72f), RoundedCornerShape(16.dp))
                    .pointerInput(id) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            // Mutate local state directly — no stale-capture issue
                            localOffset = Offset(localOffset.x + pan.x, localOffset.y + pan.y)
                            onOffsetChanged(id, localOffset)
                            val ns = (localScale * zoom).coerceIn(0.4f, 2.4f)
                            localScale = ns
                            onScaleChanged(id, ns)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(4.dp)
                ) {
                    // Move icon
                    Text(
                        text = "✥",
                        color = Color(0xFF5BE9F0),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Light
                    )
                    if (label.isNotEmpty()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = label.uppercase(),
                            color = Color(0xFF5BE9F0).copy(alpha = 0.9f),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "drag · pinch",
                            color = Color(0xFF5BE9F0).copy(alpha = 0.45f),
                            fontSize = 7.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Skin configuration type
// ─────────────────────────────────────────────────────────────────────────────
private data class SkinConfig(
    val bg: Color,
    val accent: Color,
    val faceLabels: List<String>,
    val faceColors: List<Color>,
    val isStaggered: Boolean  // true = left-stick-high layout (Switch/Xbox)
)

// ─────────────────────────────────────────────────────────────────────────────
// GamepadScreen
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun GamepadScreen(
    profileId: String,
    onBackToDashboard: () -> Unit,
    deadZone: Float,
    isEditMode: Boolean = false,
    componentOffsets: Map<String, Offset> = emptyMap(),
    componentScales: Map<String, Float> = emptyMap(),
    onOffsetChanged: (String, Offset) -> Unit = { _, _ -> },
    onScaleChanged: (String, Float) -> Unit = { _, _ -> },
    onResetLayout: () -> Unit = {},
    onSaveLayout: () -> Unit = {},
    onEnterEditMode: () -> Unit = {},
    modifier: Modifier = Modifier,
    onInputChanged: (InputState) -> Unit = {},
    onMouseMoved: (Float, Float) -> Unit = { _, _ -> },
    onTouchpadClick: () -> Unit = {}
) {
    var inputState by remember { mutableStateOf(InputState()) }
    fun updateInput(transform: InputState.() -> InputState) {
        inputState = inputState.transform()
        onInputChanged(inputState)
    }

    // Helper to read component values with safe defaults
    fun offset(id: String) = componentOffsets[id] ?: Offset.Zero
    fun scale(id: String)  = componentScales[id]  ?: 1f

    // ── Skin config ───────────────────────────────────────────────────────
    val skin = when (profileId) {
        "ps5" -> SkinConfig(
            bg          = Color(0xFF060810),
            accent      = ProfileColors.PsBlue,
            faceLabels  = listOf("╳", "◯", "▢", "△"),
            faceColors  = listOf(ProfileColors.PsBlue, Color(0xFFE8473F), Color(0xFFB0B4BE), Color(0xFFD4A017)),
            isStaggered = false
        )
        "xbox" -> SkinConfig(
            bg          = Color(0xFF030507),
            accent      = ProfileColors.XboxGreen,
            faceLabels  = listOf("A", "B", "X", "Y"),
            faceColors  = listOf(Color(0xFF22C55E), Color(0xFFEF4444), Color(0xFF4D8EFF), Color(0xFFF59E0B)),
            isStaggered = true
        )
        else -> SkinConfig( // "switch" — Nintendo Switch Pro
            bg          = Color(0xFF070910),
            accent      = Color(0xFF009AC7),
            faceLabels  = listOf("B", "A", "Y", "X"),
            faceColors  = listOf(
                ProfileColors.SwitchBtnA,   // B = Red (bottom)
                ProfileColors.SwitchBtnB,   // A = Yellow/Gold (right)
                ProfileColors.SwitchBtnX,   // Y = Blue (left)
                ProfileColors.SwitchBtnY    // X = Green (top)
            ),
            isStaggered = true
        )
    }

    val stickSize  = 142.dp
    val buttonSize = 50.dp
    val dpadSize   = 100.dp

    // ─────────────────────────────────────────────────────────────────────
    // Root container — background fills edge-to-edge, content respects insets
    // ─────────────────────────────────────────────────────────────────────
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(skin.bg)
            .drawBehind {
                // Very subtle vertical separators per profile (non-glow)
                when (profileId) {
                    "switch" -> {
                        drawLine(Color(0xFF009AC7).copy(alpha = 0.07f), Offset(size.width * 0.265f, 0f), Offset(size.width * 0.265f, size.height), strokeWidth = 1f)
                        drawLine(Color(0xFFE4000F).copy(alpha = 0.07f), Offset(size.width * 0.735f, 0f), Offset(size.width * 0.735f, size.height), strokeWidth = 1f)
                    }
                }
            }
    ) {
        // Inner safe-drawing box so content never hides behind navigation bars.
        // Background intentionally fills to edges; only interactive elements get inset.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
        ) {

        // ═══════════════════════════════════════════════════════════════
        // TOP BAR (Always rendered, elements wrapped in DraggableLayoutElement)
        // ═══════════════════════════════════════════════════════════════
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left shoulders
            DraggableLayoutElement(
                id = "shoulderL",
                isEditMode = isEditMode,
                externalOffset = offset("shoulderL"),
                externalScale  = scale("shoulderL"),
                onOffsetChanged = onOffsetChanged,
                onScaleChanged  = onScaleChanged,
                label = "L Shoulders"
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShoulderButton(
                        label = if (profileId == "switch") "ZL" else "L2",
                        color = skin.accent,
                        onPressChanged = { updateInput { copy(leftTrigger = if (it) 1f else 0f) } }
                    )
                    ShoulderButton(
                        label = if (profileId == "switch") "L" else "L1",
                        color = skin.accent,
                        onPressChanged = { updateInput { copy(buttonL1 = it) } }
                    )
                }
            }

            // Centre system buttons
            DraggableLayoutElement(
                id = "sysButtons",
                isEditMode = isEditMode,
                externalOffset = offset("sysButtons"),
                externalScale  = scale("sysButtons"),
                onOffsetChanged = onOffsetChanged,
                onScaleChanged  = onScaleChanged,
                label = "System"
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SystemButton(
                        label = when (profileId) {
                            "ps5"    -> "CREATE"
                            "switch" -> "−"
                            else     -> "BACK"
                        },
                        width = if (profileId == "switch") 36.dp else 60.dp,
                        onPressChanged = { updateInput { copy(buttonSelect = it) } }
                    )

                    when (profileId) {
                        "ps5" -> {
                            // PS5 Touchpad
                            var lastPos  by remember { mutableStateOf<Offset?>(null) }
                            var t0       by remember { mutableLongStateOf(0L) }
                            var totalMov by remember { mutableFloatStateOf(0f) }
                            Box(
                                modifier = Modifier
                                    .size(96.dp, 28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.03f))
                                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                    .pointerInput(onMouseMoved, onTouchpadClick) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event  = awaitPointerEvent()
                                                val change = event.changes.firstOrNull() ?: continue
                                                if (change.pressed) {
                                                    change.consume()
                                                    val pos = change.position
                                                    if (change.changedToDown()) {
                                                        t0 = System.currentTimeMillis(); totalMov = 0f; lastPos = pos
                                                    } else {
                                                        lastPos?.let { lp ->
                                                            val dx = pos.x - lp.x; val dy = pos.y - lp.y
                                                            totalMov += sqrt(dx * dx + dy * dy)
                                                            onMouseMoved(dx * 0.75f, dy * 0.75f)
                                                        }
                                                        lastPos = pos
                                                    }
                                                } else {
                                                    if (System.currentTimeMillis() - t0 < 180 && totalMov < 10f) onTouchpadClick()
                                                    lastPos = null
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("TOUCHPAD", fontSize = 7.sp, color = TextSecondary, fontWeight = FontWeight.Bold)
                            }
                        }
                        "xbox" -> {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Brush.radialGradient(listOf(Color(0xFF22C55E), Color(0xFF0A4A0A))))
                                    .border(1.dp, Color(0xFF22C55E).copy(alpha = 0.4f), CircleShape)
                                    .clickable { updateInput { copy(buttonHome = true) } },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("X", color = Color.White, fontWeight = FontWeight.Black, fontSize = 11.sp)
                            }
                        }
                        else -> { // Switch HOME
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Brush.radialGradient(listOf(Color(0xFF1C2338), Color(0xFF0E1020))))
                                    .border(1.dp, skin.accent.copy(alpha = 0.35f), CircleShape)
                                    .clickable { updateInput { copy(buttonHome = true) } },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("⌂", color = skin.accent, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                            }
                        }
                    }

                    SystemButton(
                        label = when (profileId) {
                            "ps5"    -> "OPTIONS"
                            "switch" -> "+"
                            else     -> "START"
                        },
                        width = if (profileId == "switch") 36.dp else 70.dp,
                        onPressChanged = { updateInput { copy(buttonStart = it) } }
                    )
                }
            }

            // Right shoulders
            DraggableLayoutElement(
                id = "shoulderR",
                isEditMode = isEditMode,
                externalOffset = offset("shoulderR"),
                externalScale  = scale("shoulderR"),
                onOffsetChanged = onOffsetChanged,
                onScaleChanged  = onScaleChanged,
                label = "R Shoulders"
            ) {
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShoulderButton(
                        label = if (profileId == "switch") "R" else "R1",
                        color = skin.accent,
                        onPressChanged = { updateInput { copy(buttonR1 = it) } }
                    )
                    ShoulderButton(
                        label = if (profileId == "switch") "ZR" else "R2",
                        color = skin.accent,
                        onPressChanged = { updateInput { copy(rightTrigger = if (it) 1f else 0f) } }
                    )

                    if (!isEditMode) {
                        Spacer(Modifier.width(6.dp))

                        // Edit Layout Button
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFF14203A), Color(0xFF0D1428))))
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(9.dp))
                                .clickable { onEnterEditMode() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit layout", tint = skin.accent.copy(alpha = 0.8f), modifier = Modifier.size(15.dp))
                        }

                        Spacer(Modifier.width(6.5.dp))

                        // Close Button
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color(0xFF100A0A))
                                .border(1.dp, AccentRed.copy(alpha = 0.2f), RoundedCornerShape(9.dp))
                                .clickable { onBackToDashboard() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Exit", tint = AccentRed.copy(alpha = 0.6f), modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        // ═══════════════════════════════════════════════════════════════
        // MAIN PLAY AREA
        // ═══════════════════════════════════════════════════════════════
        val topPad = if (isEditMode) 56.dp else 54.dp

        if (skin.isStaggered) {
            // ── Switch / Xbox: Left stick HIGH, D-pad LOW ────────────────
            // Left side
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.44f)
                    .padding(start = 14.dp, top = topPad, bottom = 8.dp)
                    .align(Alignment.CenterStart)
            ) {
                // Left Stick (upper)
                DraggableLayoutElement(
                    id = "leftStick",
                    isEditMode = isEditMode,
                    externalOffset = offset("leftStick"),
                    externalScale  = scale("leftStick"),
                    onOffsetChanged = onOffsetChanged,
                    onScaleChanged  = onScaleChanged,
                    label = "Left Stick",
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 2.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        VirtualStick(
                            size = stickSize,
                            deadZone = deadZone,
                            accentColor = skin.accent,
                            onValueChange = { x, y -> updateInput { copy(leftStickX = x, leftStickY = y) } }
                        )
                        Spacer(Modifier.height(3.dp))
                        SystemButton(
                            label = "L3",
                            width = 34.dp, height = 18.dp,
                            onPressChanged = { updateInput { copy(buttonL3 = it) } }
                        )
                    }
                }

                // D-Pad (lower)
                DraggableLayoutElement(
                    id = "dpad",
                    isEditMode = isEditMode,
                    externalOffset = offset("dpad"),
                    externalScale  = scale("dpad"),
                    onOffsetChanged = onOffsetChanged,
                    onScaleChanged  = onScaleChanged,
                    label = "D-Pad",
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    DPad(
                        padSize = dpadSize,
                        onDirectionChanged = { u, d, l, r ->
                            updateInput { copy(dpadUp = u, dpadDown = d, dpadLeft = l, dpadRight = r) }
                        }
                    )
                }
            }

            // Right side
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.44f)
                    .padding(end = 14.dp, top = topPad, bottom = 8.dp)
                    .align(Alignment.CenterEnd)
            ) {
                // ABXY (upper)
                DraggableLayoutElement(
                    id = "abxy",
                    isEditMode = isEditMode,
                    externalOffset = offset("abxy"),
                    externalScale  = scale("abxy"),
                    onOffsetChanged = onOffsetChanged,
                    onScaleChanged  = onScaleChanged,
                    label = "A·B·X·Y",
                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp, top = 2.dp)
                ) {
                    ActionButtonCluster(
                        buttonSize = buttonSize,
                        labels = skin.faceLabels,
                        colors = skin.faceColors,
                        onButtonChanged = { idx, pressed ->
                            updateInput {
                                if (profileId == "switch") {
                                    when (idx) {
                                        0 -> copy(buttonB = pressed)
                                        1 -> copy(buttonA = pressed)
                                        2 -> copy(buttonY = pressed)
                                        3 -> copy(buttonX = pressed)
                                        else -> this
                                    }
                                } else {
                                    when (idx) {
                                        0 -> copy(buttonA = pressed)
                                        1 -> copy(buttonB = pressed)
                                        2 -> copy(buttonX = pressed)
                                        3 -> copy(buttonY = pressed)
                                        else -> this
                                    }
                                }
                            }
                        }
                    )
                }

                // Right Stick (lower)
                DraggableLayoutElement(
                    id = "rightStick",
                    isEditMode = isEditMode,
                    externalOffset = offset("rightStick"),
                    externalScale  = scale("rightStick"),
                    onOffsetChanged = onOffsetChanged,
                    onScaleChanged  = onScaleChanged,
                    label = "Right Stick",
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        VirtualStick(
                            size = stickSize,
                            deadZone = deadZone,
                            accentColor = skin.accent,
                            onValueChange = { x, y -> updateInput { copy(rightStickX = x, rightStickY = y) } }
                        )
                        Spacer(Modifier.height(3.dp))
                        SystemButton(
                            label = "R3",
                            width = 34.dp, height = 18.dp,
                            onPressChanged = { updateInput { copy(buttonR3 = it) } }
                        )
                    }
                }
            }

        } else {
            // ── PS5 Symmetric: D-Pad HIGH (left), Left Stick LOW (left),
            //                  ABXY HIGH (right), Right Stick LOW (right) ──
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.44f)
                    .padding(start = 14.dp, top = topPad, bottom = 8.dp)
                    .align(Alignment.CenterStart)
            ) {
                DraggableLayoutElement(
                    id = "dpad",
                    isEditMode = isEditMode,
                    externalOffset = offset("dpad"),
                    externalScale  = scale("dpad"),
                    onOffsetChanged = onOffsetChanged,
                    onScaleChanged  = onScaleChanged,
                    label = "D-Pad",
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 2.dp)
                ) {
                    DPad(padSize = dpadSize, onDirectionChanged = { u, d, l, r ->
                        updateInput { copy(dpadUp = u, dpadDown = d, dpadLeft = l, dpadRight = r) }
                    })
                }
                DraggableLayoutElement(
                    id = "leftStick",
                    isEditMode = isEditMode,
                    externalOffset = offset("leftStick"),
                    externalScale  = scale("leftStick"),
                    onOffsetChanged = onOffsetChanged,
                    onScaleChanged  = onScaleChanged,
                    label = "Left Stick",
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        VirtualStick(size = stickSize, deadZone = deadZone, accentColor = skin.accent,
                            onValueChange = { x, y -> updateInput { copy(leftStickX = x, leftStickY = y) } })
                        Spacer(Modifier.height(3.dp))
                        SystemButton("L3", width = 34.dp, height = 18.dp, onPressChanged = { updateInput { copy(buttonL3 = it) } })
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.44f)
                    .padding(end = 14.dp, top = topPad, bottom = 8.dp)
                    .align(Alignment.CenterEnd)
            ) {
                DraggableLayoutElement(
                    id = "abxy",
                    isEditMode = isEditMode,
                    externalOffset = offset("abxy"),
                    externalScale  = scale("abxy"),
                    onOffsetChanged = onOffsetChanged,
                    onScaleChanged  = onScaleChanged,
                    label = "A·B·X·Y",
                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp, top = 2.dp)
                ) {
                    ActionButtonCluster(buttonSize = buttonSize, labels = skin.faceLabels, colors = skin.faceColors,
                        onButtonChanged = { idx, pressed ->
                            updateInput {
                                if (profileId == "switch") {
                                    when (idx) {
                                        0 -> copy(buttonB = pressed)
                                        1 -> copy(buttonA = pressed)
                                        2 -> copy(buttonY = pressed)
                                        3 -> copy(buttonX = pressed)
                                        else -> this
                                    }
                                } else {
                                    when (idx) {
                                        0 -> copy(buttonA = pressed)
                                        1 -> copy(buttonB = pressed)
                                        2 -> copy(buttonX = pressed)
                                        3 -> copy(buttonY = pressed)
                                        else -> this
                                    }
                                }
                            }
                        })
                }
                DraggableLayoutElement(
                    id = "rightStick",
                    isEditMode = isEditMode,
                    externalOffset = offset("rightStick"),
                    externalScale  = scale("rightStick"),
                    onOffsetChanged = onOffsetChanged,
                    onScaleChanged  = onScaleChanged,
                    label = "Right Stick",
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        VirtualStick(size = stickSize, deadZone = deadZone, accentColor = skin.accent,
                            onValueChange = { x, y -> updateInput { copy(rightStickX = x, rightStickY = y) } })
                        Spacer(Modifier.height(3.dp))
                        SystemButton("R3", width = 34.dp, height = 18.dp, onPressChanged = { updateInput { copy(buttonR3 = it) } })
                    }
                }
            }
        } // end inner layout branch

        if (isEditMode) {
            // ── Floating Edit Mode Banner at the Bottom Center ───────────
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Bg2.copy(alpha = 0.9f))
                    .border(1.dp, AccentCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Layout Customizer",
                            color = AccentCyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Drag to move · Pinch to scale",
                            color = TextSecondary,
                            fontSize = 8.sp
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Reset
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                .clickable { onResetLayout() }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Refresh, null, tint = TextSecondary, modifier = Modifier.size(11.dp))
                                Text("Reset", color = TextSecondary, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        // Save & Exit Edit Mode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFF5BE9F0), Color(0xFF007AFF))))
                                .clickable { onSaveLayout() }
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(11.dp))
                                Text("Save", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        } // end navigationBarsPadding Box
    } // end outer root Box
} // end GamepadScreen

