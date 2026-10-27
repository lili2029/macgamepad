package com.macgamepad.controller.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.macgamepad.controller.input.InputState
import com.macgamepad.controller.input.Macro
import com.macgamepad.controller.input.MacroKeys
import com.macgamepad.controller.input.MacroLibrary
import com.macgamepad.controller.input.MacroStep
import com.macgamepad.controller.input.MacroStore
import com.macgamepad.controller.input.PressMode
import com.macgamepad.controller.input.sanitized
import com.macgamepad.controller.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Macro editor.
 *
 * A macro is a timeline, not a tape: every row on this screen is one *press* that
 * owns a window on the clock — when it goes down and how long it stays down. Two
 * windows that overlap are one report on the phone, which is exactly how "hold ZL,
 * press X while it is still down, then A" is built: one long ZL bar with the X and
 * A bars sitting inside it.
 *
 * Everything here is a draft until Save; the pad keeps firing whatever was saved.
 */
@Composable
fun MacroEditorScreen(
    macro: Macro,
    isBuiltin: Boolean,
    canPreview: Boolean,
    onSave: (Macro) -> Unit,
    onPreview: (Macro) -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var name by remember(macro.id) { mutableStateOf(macro.name) }
    var steps by remember(macro.id) {
        mutableStateOf(macro.steps.sortedWith(compareBy({ it.start }, { it.end })))
    }
    var cooldown by remember(macro.id) { mutableStateOf(macro.cooldownTicks) }
    var pickerIndex by remember { mutableStateOf(-1) }
    var showDelete by remember { mutableStateOf(false) }
    var savedFlash by remember { mutableStateOf(false) }

    // Factory copy of this id, so "restore defaults" works even after the user
    // edited a built-in and the parent list now holds the edited version.
    val factory = remember(macro.id) { MacroLibrary.byId(macro.id) }
    val canReset = isBuiltin && (
        steps != factory.steps || cooldown != factory.cooldownTicks || name.trim() != factory.name
        )

    LaunchedEffect(savedFlash) {
        if (savedFlash) {
            delay(1400)
            savedFlash = false
        }
    }

    // Rebuilt on every edit; cheap, and it keeps `Macro`'s own invariants as the
    // single source of truth for totalTicks / durationMs.
    val draft: Macro = macro.copy(
        name = name.trim().ifEmpty { "未命名宏" },
        steps = steps,
        cooldownTicks = cooldown
    )

    /**
     * Rows are kept sorted so the list, the timeline strip and the stored macro all
     * agree on the order. Dragging a slider passes [sort] = false so the row does
     * not jump out from under the finger; letting go re-sorts.
     */
    fun applySteps(next: List<MacroStep>, sort: Boolean = true) {
        steps = if (sort) next.sortedWith(compareBy({ it.start }, { it.end })) else next
    }

    fun updateStep(index: Int, sort: Boolean = false, transform: (MacroStep) -> MacroStep) {
        if (index !in steps.indices) return
        val list = steps.toMutableList()
        list[index] = transform(list[index])
        applySteps(list, sort)
    }

    // Bulk move. Normally it means "the whole script", but 「选择」 narrows it to the
    // ticked rows, which is the "only these three should move" case.
    var selecting by remember(macro.id) { mutableStateOf(false) }
    var selection by remember(macro.id) { mutableStateOf<Set<Int>>(emptySet()) }

    val targets: Set<Int>? =
        if (selecting) selection.takeIf { it.isNotEmpty() } else steps.indices.toSet()
    val targetSteps = if (targets == null) emptyList() else steps.filterIndexed { i, _ -> i in targets }
    // How far the target presses can still slide before one would fall off either
    // end. Clamping the delta instead of each press keeps the spacing intact.
    val shiftRoomBack = if (targets == null) 0 else -(targetSteps.minOfOrNull { it.start } ?: 0)
    val shiftRoomForward =
        if (targets == null) 0 else MacroStore.MAX_START - (targetSteps.maxOfOrNull { it.end } ?: 0)

    /**
     * Moves the target presses by [deltaTicks]. The internal choreography is
     * untouched, only where those presses sit on the clock changes, so a macro that
     * needs a longer lead-in — or three presses that need to come later — does not
     * have to be rebuilt by dragging a dozen rows. The tick marks ride along through
     * the re-sort so a second nudge hits the same rows.
     */
    fun shift(deltaTicks: Int) {
        val picked = targets ?: return
        val delta = deltaTicks.coerceAtLeast(shiftRoomBack).coerceAtMost(shiftRoomForward)
        if (delta == 0) return
        val marked = steps.mapIndexed { i, step ->
            (if (i in picked) step.copy(start = step.start + delta) else step) to (i in picked)
        }
        val ordered = marked.sortedWith(compareBy({ it.first.start }, { it.first.end }))
        steps = ordered.map { it.first }
        if (selecting) selection = ordered.indices.filter { ordered[it].second }.toSet()
    }

    fun toggleTick(index: Int) {
        selection = if (index in selection) selection - index else selection + index
    }

    val maxEnd = draft.totalTicks
    // Upper end of the "when does this press start" slider. It only ever grows, so
    // dragging a long hold later cannot rescale the thumb under the finger.
    var startRange by remember(macro.id) { mutableStateOf(maxOf(maxEnd + 12, 32)) }
    LaunchedEffect(maxEnd) {
        if (maxEnd + 12 > startRange) startRange = maxEnd + 12
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Bg1)
            .navigationBarsPadding()
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Bg2)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
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
                Column {
                    Text("宏编辑器", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(
                        if (isBuiltin) "内置宏 · 可修改" else "自定义宏",
                        fontSize = 10.sp,
                        color = TextTertiary
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (savedFlash) {
                    Text("已保存", fontSize = 11.sp, color = AccentGreen, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(AccentBlue.copy(alpha = 0.15f))
                        .border(1.dp, AccentBlue.copy(alpha = 0.5f), RoundedCornerShape(9.dp))
                        .clickable {
                            onSave(draft.sanitized())
                            savedFlash = true
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(Icons.Default.Save, null, tint = AccentBlue, modifier = Modifier.size(13.dp))
                        Text("保存", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentBlue)
                    }
                }
            }
        }

        HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(24) },
                singleLine = true,
                label = { Text("宏名称", fontSize = 11.sp, color = TextTertiary) },
                textStyle = TextStyle(color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentBlue,
                    unfocusedBorderColor = BorderSubtle,
                    focusedContainerColor = Bg2,
                    unfocusedContainerColor = Bg2,
                    cursorColor = AccentBlue
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                StatPill("按压", "${steps.size} 段")
                StatPill("总长", "${draft.durationMs} ms")
                StatPill("收尾", "${cooldown * Macro.TICK_MS} ms")
                if (draft.hasOverlap) StatPill("重叠", "有同时按下")
            }

            // ── Timeline strip ──────────────────────────────────────────────
            MacroTimeline(
                steps = steps,
                totalTicks = draft.totalTicks,
                selected = pickerIndex,
                marked = if (selecting) selection else emptySet(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Bg2)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                    .padding(8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("0 ms", fontSize = 9.sp, color = TextTertiary)
                Text(
                    "一行 = 一段按压 · 上下重叠就是同时按住",
                    fontSize = 9.sp,
                    color = TextTertiary
                )
                Text("${draft.durationMs} ms", fontSize = 9.sp, color = TextTertiary)
            }

            // Bulk nudge: the whole script by default, only the ticked presses while
            // 「选择」 is on. Spacing inside the moved group is preserved.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    if (selecting) "已选 ${selection.size}" else "整批",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selecting) AccentBlue else TextTertiary,
                    modifier = Modifier.width(34.dp)
                )
                ShiftChip("−200", enabled = shiftRoomBack <= -25) { shift(-25) }
                ShiftChip("−40", enabled = shiftRoomBack <= -5) { shift(-5) }
                Spacer(Modifier.weight(1f))
                ShiftChip("+40", enabled = shiftRoomForward >= 5) { shift(5) }
                ShiftChip("+200", enabled = shiftRoomForward >= 25) { shift(25) }
                ShiftChip(
                    label = if (selecting) "完成" else "选择",
                    enabled = true,
                    tint = AccentAmber
                ) {
                    selecting = !selecting
                    if (!selecting) selection = emptySet()
                }
            }
        }

        HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)

        // ── Presses ─────────────────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "每段按压有自己的起点和时长：把 ZL 拉长到 200 ms，再把 X 的起点拖到 48 ms，就是「按住 ZL 的同时按 X」。重叠的几段在手机端合并成同一帧发送。",
                    fontSize = 10.sp,
                    color = TextTertiary,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            itemsIndexed(steps, key = { index, _ -> index }) { index, step ->
                PressRow(
                    index = index,
                    step = step,
                    startRange = startRange,
                    canDelete = steps.size > 1,
                    selecting = selecting,
                    checked = index in selection,
                    onToggle = { toggleTick(index) },
                    onOpenPicker = {
                        if (selecting) toggleTick(index) else pickerIndex = index
                    },
                    onStart = { value ->
                        updateStep(index, sort = false) { it.copy(start = value.coerceIn(0, MacroStore.MAX_START)) }
                    },
                    onStartDone = { applySteps(steps) },
                    onTicks = { value ->
                        updateStep(index) { it.copy(ticks = value.coerceIn(1, 250)) }
                    },
                    onMode = { mode ->
                        updateStep(index) { step ->
                            if (mode == PressMode.HOLD) {
                                step.copy(mode = PressMode.HOLD)
                            } else {
                                // A turbo needs room for a few cycles or it is just a tap
                                // with extra bookkeeping, so the window grows to fit four.
                                val period = (step.burstTicks + step.gapTicks).coerceAtLeast(2)
                                step.copy(mode = PressMode.TURBO, ticks = maxOf(step.ticks, period * 4))
                            }
                        }
                    },
                    onBurst = { value -> updateStep(index) { it.copy(burstTicks = value.coerceIn(1, 60)) } },
                    onGap = { value -> updateStep(index) { it.copy(gapTicks = value.coerceIn(1, 60)) } },
                    onDuplicate = {
                        if (steps.size < 80) {
                            val list = steps.toMutableList()
                            list.add(index + 1, step.copy(start = step.end))
                            applySteps(list)
                        }
                    },
                    onStartTogether = {
                        if (steps.size < 80) {
                            val list = steps.toMutableList()
                            list.add(index + 1, step)
                            applySteps(list)
                        }
                    },
                    onInsertInside = {
                        if (steps.size < 80) {
                            val at = step.start + step.ticks / 2
                            val list = steps.toMutableList()
                            list.add(index + 1, MacroStep(start = at, ticks = 3))
                            applySteps(list)
                            pickerIndex = list.indexOfFirst { it.start == at && it.ticks == 3 && it.isEmpty }
                        }
                    },
                    onExtend = {
                        val next = steps.filter { it.start > step.start }.minByOrNull { it.start }
                        if (next != null) {
                            updateStep(index) { it.copy(ticks = (next.start - it.start).coerceAtLeast(1)) }
                        }
                    },
                    onDelete = {
                        if (steps.size > 1) applySteps(steps.toMutableList().also { it.removeAt(index) })
                    }
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AddStepButton("按键", Icons.Default.Add) {
                        if (steps.size < 80) {
                            val at = maxEnd
                            val list = steps + MacroStep(start = at, ticks = 3)
                            applySteps(list)
                            pickerIndex = list.indexOfFirst { it.start == at && it.isEmpty }
                        }
                    }
                    AddStepButton("同时按", Icons.Default.Add) {
                        if (steps.size < 80) {
                            val at = steps.firstOrNull()?.start ?: 0
                            val list = steps + MacroStep(start = at, ticks = 3)
                            applySteps(list)
                            pickerIndex = list.indexOfFirst { it.start == at && it.isEmpty }
                        }
                    }
                    AddStepButton("收尾 +", Icons.Default.Add) {
                        cooldown = (cooldown + 4).coerceAtMost(40)
                    }
                }
            }
        }

        HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)

        // ── Bottom bar ──────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (canPreview) AccentGreen.copy(alpha = 0.14f) else Bg3)
                        .border(
                            1.dp,
                            if (canPreview) AccentGreen.copy(alpha = 0.5f) else BorderSubtle,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable(enabled = canPreview) { onPreview(draft.sanitized()) }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            null,
                            tint = if (canPreview) AccentGreen else TextTertiary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            if (canPreview) "试运行" else "连接后才能试运行",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (canPreview) AccentGreen else TextTertiary
                        )
                    }
                }

                if (isBuiltin) {
                    SmallAction("恢复默认", Icons.Default.Refresh, AccentAmber, enabled = canReset) {
                        name = factory.name
                        steps = factory.steps.sortedWith(compareBy({ it.start }, { it.end }))
                        cooldown = factory.cooldownTicks
                        onReset()
                    }
                } else {
                    SmallAction("删除", Icons.Default.Delete, AccentRed, enabled = true) {
                        showDelete = true
                    }
                }
            }
            Text(
                "贴士：短按建议 ≥3 tick（24 ms）；「按住」是一路压着不放，「连击」在同一段时间里按「每下/间隔」的节拍重复点。两段并存的窗口不要同时按同一个键。",
                fontSize = 9.sp,
                color = TextTertiary,
                textAlign = TextAlign.Start
            )
        }
    }

    // ── Key picker ──────────────────────────────────────────────────────────
    if (pickerIndex in steps.indices) {
        val step = steps[pickerIndex]
        KeyPickerDialog(
            state = step.state,
            start = step.start,
            ticks = step.ticks,
            onToggle = { key -> updateStep(pickerIndex) { it.copy(state = MacroKeys.toggle(it.state, key)) } },
            onClear = { updateStep(pickerIndex) { it.copy(state = InputState()) } },
            onDone = { pickerIndex = -1 }
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            containerColor = Bg3,
            tonalElevation = 0.dp,
            title = { Text("删除这个宏？", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp) },
            text = {
                Text(
                    "「${macro.name}」的所有按压段会一并删除，此操作无法撤销。",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    onDelete()
                }) { Text("删除", color = AccentRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("取消", color = TextSecondary) }
            }
        )
    }
}

// ══════════════════════════════════════════════════════════════════════════
// Timeline strip
// ══════════════════════════════════════════════════════════════════════════

/**
 * Every press as one bar on a shared clock, one row each. Overlapping rows are the
 * visual answer to "am I really holding ZL while tapping X?" — if two bars sit on
 * top of each other, the phone merges them into one report. A turbo press is drawn
 * as the row of short taps it actually is.
 */
@Composable
private fun MacroTimeline(
    steps: List<MacroStep>,
    totalTicks: Int,
    selected: Int,
    marked: Set<Int> = emptySet(),
    modifier: Modifier = Modifier
) {
    val rows = steps.size.coerceAtLeast(1)
    val span = totalTicks.coerceAtLeast(1)

    Canvas(modifier = modifier) {
        val rowH = size.height / rows
        val barH = (rowH * 0.68f).coerceIn(1.5f, 9.dp.toPx())
        fun x(tick: Int) = size.width * tick / span

        fun bar(from: Int, to: Int, y: Float, color: Color, minWidth: Float = 2.dp.toPx()) {
            val x1 = x(from)
            val x2 = x(to)
            drawRoundRect(
                color = color,
                topLeft = Offset(x1, y),
                size = Size((x2 - x1).coerceAtLeast(minWidth), barH),
                cornerRadius = CornerRadius(barH / 2f)
            )
        }

        // Nothing pressed yet: keep a faint rail so the strip still reads as a clock.
        drawRoundRect(
            color = BorderSubtle.copy(alpha = 0.5f),
            topLeft = Offset(0f, size.height / 2f - 0.5.dp.toPx()),
            size = Size(size.width, 1.dp.toPx()),
            cornerRadius = CornerRadius(1f)
        )

        steps.forEachIndexed { index, step ->
            val y = index * rowH + (rowH - barH) / 2f
            val color = when {
                step.isEmpty -> BorderSubtle.copy(alpha = 0.5f)
                index == selected -> AccentAmber.copy(alpha = 0.9f)
                // Ticked rows stand out, so 「整批」 while selecting is obvious about
                // which bars it is about to move.
                index in marked -> AccentBlue.copy(alpha = 0.95f)
                else -> AccentCyan.copy(alpha = if (marked.isEmpty()) 0.85f else 0.35f)
            }
            if (step.mode == PressMode.TURBO) {
                var at = step.start
                while (at < step.end) {
                    bar(at, minOf(at + step.burstTicks, step.end), y, color, minWidth = 1.dp.toPx())
                    at += step.periodTicks
                }
            } else {
                bar(step.start, step.end, y, color)
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// Press row
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun PressRow(
    index: Int,
    step: MacroStep,
    startRange: Int,
    canDelete: Boolean,
    selecting: Boolean = false,
    checked: Boolean = false,
    onToggle: () -> Unit = {},
    onOpenPicker: () -> Unit,
    onStart: (Int) -> Unit,
    onStartDone: () -> Unit,
    onTicks: (Int) -> Unit,
    onMode: (PressMode) -> Unit,
    onBurst: (Int) -> Unit,
    onGap: (Int) -> Unit,
    onDuplicate: () -> Unit,
    onStartTogether: () -> Unit,
    onInsertInside: () -> Unit,
    onExtend: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val summary = step.caption
    val accent = if (summary.isEmpty()) TextTertiary else AccentCyan
    val turbo = step.mode == PressMode.TURBO

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Bg2)
            .border(1.dp, if (turbo) AccentAmber.copy(alpha = 0.35f) else BorderSubtle, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selecting && checked) AccentBlue.copy(alpha = 0.22f) else Bg3)
                    .border(
                        1.dp,
                        if (selecting && checked) AccentBlue else BorderSubtle,
                        RoundedCornerShape(8.dp)
                    )
                    .clickable(enabled = selecting) { onToggle() },
                contentAlignment = Alignment.Center
            ) {
                if (selecting) {
                    if (checked) {
                        Icon(
                            Icons.Default.Check,
                            null,
                            tint = AccentBlue,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                } else {
                    Text(
                        "${index + 1}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenPicker() }
                    .padding(vertical = 2.dp)
            ) {
                Text(
                    text = if (summary.isEmpty()) "未设置按键（点击选择）" else step.fullCaption,
                    fontSize = 13.sp,
                    fontWeight = if (summary.isEmpty()) FontWeight.Normal else FontWeight.SemiBold,
                    fontStyle = if (summary.isEmpty()) FontStyle.Italic else FontStyle.Normal,
                    color = accent,
                    maxLines = 2
                )
                Text(
                    "${step.start * Macro.TICK_MS} → ${step.end * Macro.TICK_MS} ms · 共 ${step.ticks * Macro.TICK_MS} ms",
                    fontSize = 9.sp,
                    color = TextTertiary
                )
            }

            if (!selecting) Box {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { menuOpen = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.MoreVert, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("复制一段（接在后面）", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(15.dp)) },
                        onClick = { menuOpen = false; onDuplicate() }
                    )
                    DropdownMenuItem(
                        text = { Text("同时按（同一起点）", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(15.dp)) },
                        onClick = { menuOpen = false; onStartTogether() }
                    )
                    DropdownMenuItem(
                        text = { Text("在这段中间插入按键", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Add, null, modifier = Modifier.size(15.dp)) },
                        onClick = { menuOpen = false; onInsertInside() }
                    )
                    DropdownMenuItem(
                        text = { Text("延长到下一段开始", fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Add, null, modifier = Modifier.size(15.dp)) },
                        onClick = { menuOpen = false; onExtend() }
                    )
                    DropdownMenuItem(
                        text = { Text("删除这一段的按键", fontSize = 12.sp, color = AccentRed) },
                        enabled = canDelete,
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = AccentRed, modifier = Modifier.size(15.dp)) },
                        onClick = { menuOpen = false; onDelete() }
                    )
                }
            }
        }

        if (!selecting) {
        TimeBar(
            label = "起点",
            ticks = step.start,
            maxTicks = startRange,
            onChange = onStart,
            onChangeFinished = onStartDone,
            tint = AccentAmber
        )
        TimeBar(
            label = "时长",
            ticks = step.ticks,
            maxTicks = 200,
            onChange = onTicks,
            onChangeFinished = {},
            tint = AccentCyan
        )

        // ── 按住 / 连击 ──────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("方式", fontSize = 9.sp, color = TextTertiary, modifier = Modifier.width(22.dp))
            ModeChip("按住", selected = !turbo) { onMode(PressMode.HOLD) }
            ModeChip("连击", selected = turbo) { onMode(PressMode.TURBO) }
            Spacer(Modifier.weight(1f))
            if (turbo) {
                Text(
                    "${step.burstTicks * Macro.TICK_MS}ms 按 / ${step.gapTicks * Macro.TICK_MS}ms 停 · ×${step.repeatCount}",
                    fontSize = 9.sp,
                    color = TextTertiary
                )
            }
        }

        if (turbo) {
            TimeBar(
                label = "每下",
                ticks = step.burstTicks,
                maxTicks = 60,
                onChange = onBurst,
                onChangeFinished = {},
                tint = AccentAmber
            )
            TimeBar(
                label = "间隔",
                ticks = step.gapTicks,
                maxTicks = 60,
                onChange = onGap,
                onChangeFinished = {},
                tint = AccentAmber
            )
        }
        }
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) AccentAmber else TextTertiary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) AccentAmber.copy(alpha = 0.16f) else Bg3)
            .border(1.dp, if (selected) AccentAmber.copy(alpha = 0.55f) else BorderSubtle, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = tint
        )
    }
}

/**
 * Label + slider + numeric readout + ±1 tick. The slider is for the coarse move
 * (dragging a hold from 24 ms to 400 ms), the buttons are for the single tick that
 * decides whether a game registers the press at all.
 */
@Composable
private fun TimeBar(
    label: String,
    ticks: Int,
    maxTicks: Int,
    onChange: (Int) -> Unit,
    onChangeFinished: () -> Unit,
    tint: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(label, fontSize = 9.sp, color = TextTertiary, modifier = Modifier.width(22.dp))
        Slider(
            value = ticks.coerceIn(0, maxTicks).toFloat(),
            onValueChange = { onChange(it.toInt()) },
            onValueChangeFinished = onChangeFinished,
            valueRange = 0f..maxTicks.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = tint,
                activeTrackColor = tint.copy(alpha = 0.65f),
                inactiveTrackColor = Bg3
            ),
            modifier = Modifier
                .weight(1f)
                .height(22.dp)
        )
        Text(
            "${ticks * Macro.TICK_MS} ms",
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = TextSecondary,
            modifier = Modifier.width(46.dp),
            textAlign = TextAlign.End
        )
        StepIconButton(Icons.Default.Remove) { onChange(ticks - 1) }
        StepIconButton(Icons.Default.Add) { onChange(ticks + 1) }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// Key picker
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun KeyPickerDialog(
    state: InputState,
    start: Int,
    ticks: Int,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDone,
        containerColor = Bg2,
        tonalElevation = 0.dp,
        title = {
            Column {
                Text("这一段按哪些键", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                Text(
                    "${start * Macro.TICK_MS} ms 起，按住 ${ticks * Macro.TICK_MS} ms · 可以多选",
                    fontSize = 10.sp,
                    color = TextTertiary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MacroKeys.groups.forEach { group ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(group, fontSize = 10.sp, color = TextTertiary, fontWeight = FontWeight.Bold)
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            MacroKeys.all.filter { it.group == group }.chunked(4).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    row.forEach { key ->
                                        val on = MacroKeys.isPressed(state, key.id)
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (on) AccentCyan.copy(alpha = 0.18f) else Bg3)
                                                .border(
                                                    1.dp,
                                                    if (on) AccentCyan else BorderSubtle,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable { onToggle(key.id) }
                                                .padding(vertical = 7.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                key.label,
                                                fontSize = 11.sp,
                                                fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                                                color = if (on) AccentCyan else TextSecondary
                                            )
                                        }
                                    }
                                    // Keep the grid aligned when a group doesn't fill the row.
                                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDone) {
                Text("完成", color = AccentBlue, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onClear) { Text("清空", color = TextSecondary) }
        }
    )
}

// ══════════════════════════════════════════════════════════════════════════
// Small building blocks
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun StatPill(label: String, value: String) {
    Column {
        Text(label, fontSize = 9.sp, color = TextTertiary)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
    }
}

@Composable
private fun StepIconButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(Bg3)
            .border(1.dp, BorderSubtle, RoundedCornerShape(7.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(12.dp))
    }
}

@Composable
private fun RowScope.AddStepButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(Bg2)
            .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(icon, null, tint = AccentBlue, modifier = Modifier.size(13.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = AccentBlue)
        }
    }
}

/** One nudge button of the whole-script shift rail (−200 / −40 / +40 / +200 ms). */
@Composable
private fun ShiftChip(
    label: String,
    enabled: Boolean,
    tint: Color = AccentBlue,
    onClick: () -> Unit
) {
    val color = if (enabled) tint else TextTertiary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) tint.copy(alpha = 0.12f) else Bg3)
            .border(
                1.dp,
                if (enabled) tint.copy(alpha = 0.42f) else BorderSubtle,
                RoundedCornerShape(8.dp)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 9.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun SmallAction(
    label: String,
    icon: ImageVector,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val color = if (enabled) tint else TextTertiary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) tint.copy(alpha = 0.12f) else Bg3)
            .border(1.dp, if (enabled) tint.copy(alpha = 0.45f) else BorderSubtle, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, null, tint = color, modifier = Modifier.size(13.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
