package com.macgamepad.controller.ui.screens

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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
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
import com.macgamepad.controller.input.sanitized
import com.macgamepad.controller.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Macro editor.
 *
 * A macro is nothing but an ordered list of "hold these buttons for N ticks"
 * frames, so editing it needs no new HID concepts: the user picks keys from the
 * palette on the pad they already have, sets how long each frame lasts, and the
 * on-screen macro button replays it. Everything here is a draft until Save.
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
    var steps by remember(macro.id) { mutableStateOf(macro.steps) }
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

    fun updateStep(index: Int, transform: (MacroStep) -> MacroStep) {
        if (index !in steps.indices) return
        val list = steps.toMutableList()
        list[index] = transform(list[index])
        steps = list
    }

    fun moveStep(index: Int, delta: Int) {
        val target = index + delta
        if (index !in steps.indices || target !in steps.indices) return
        val list = steps.toMutableList()
        val item = list.removeAt(index)
        list.add(target, item)
        steps = list
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
                StatPill("步数", "${steps.size}")
                StatPill("总长", "${draft.durationMs} ms")
                StatPill("收尾", "${cooldown * Macro.TICK_MS} ms")
            }
        }

        HorizontalDivider(color = BorderSubtle, thickness = 0.5.dp)

        // ── Steps ───────────────────────────────────────────────────────────
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "每一步就是发送给 Mac 的一帧：按住这些键 ${Macro.TICK_MS} ms 的整数倍。两步之间空一帧延时可以避免游戏吞输入。",
                    fontSize = 10.sp,
                    color = TextTertiary,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
            }

            itemsIndexed(steps, key = { index, _ -> index }) { index, step ->
                StepRow(
                    index = index,
                    step = step,
                    isFirst = index == 0,
                    isLast = index == steps.lastIndex,
                    canDelete = steps.size > 1,
                    onOpenPicker = { pickerIndex = index },
                    onTicks = { value -> updateStep(index) { it.copy(ticks = value.coerceIn(1, 250)) } },
                    onMoveUp = { moveStep(index, -1) },
                    onMoveDown = { moveStep(index, 1) },
                    onDuplicate = {
                        if (steps.size < 80) {
                            val list = steps.toMutableList()
                            list.add(index + 1, step)
                            steps = list
                        }
                    },
                    onDelete = {
                        if (steps.size > 1) steps = steps.toMutableList().also { it.removeAt(index) }
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
                        if (steps.size < 80) steps = steps + MacroStep(3, InputState(buttonA = true))
                    }
                    AddStepButton("延时", Icons.Default.Add) {
                        if (steps.size < 80) steps = steps + MacroStep(4)
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
                        steps = factory.steps
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
                "贴士：按键步建议 ≥3 tick（24 ms），短于 16 ms 的按下会被不少游戏当成抖动。",
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
                    "「${macro.name}」的步骤会一并删除，此操作无法撤销。",
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
// Step row
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun StepRow(
    index: Int,
    step: MacroStep,
    isFirst: Boolean,
    isLast: Boolean,
    canDelete: Boolean,
    onOpenPicker: () -> Unit,
    onTicks: (Int) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val summary = MacroKeys.summary(step.state)
    val accent = if (summary.isEmpty()) TextTertiary else AccentCyan

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Bg2)
            .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
            .clickable { onOpenPicker() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Bg3)
                .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text("${index + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (summary.isEmpty()) "延时（不按键）" else summary,
                fontSize = 13.sp,
                fontWeight = if (summary.isEmpty()) FontWeight.Normal else FontWeight.SemiBold,
                fontStyle = if (summary.isEmpty()) FontStyle.Italic else FontStyle.Normal,
                color = accent,
                maxLines = 2
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepIconButton(Icons.Default.Remove) { onTicks(step.ticks - 1) }
                Text(
                    "${step.ticks} tick · ${step.ticks * Macro.TICK_MS} ms",
                    fontSize = 10.sp,
                    color = TextTertiary,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                StepIconButton(Icons.Default.Add) { onTicks(step.ticks + 1) }
            }
        }

        Box {
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
                    text = { Text("上移", fontSize = 12.sp) },
                    enabled = !isFirst,
                    leadingIcon = { Icon(Icons.Default.ArrowUpward, null, modifier = Modifier.size(15.dp)) },
                    onClick = { menuOpen = false; onMoveUp() }
                )
                DropdownMenuItem(
                    text = { Text("下移", fontSize = 12.sp) },
                    enabled = !isLast,
                    leadingIcon = { Icon(Icons.Default.ArrowDownward, null, modifier = Modifier.size(15.dp)) },
                    onClick = { menuOpen = false; onMoveDown() }
                )
                DropdownMenuItem(
                    text = { Text("复制这一步", fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(15.dp)) },
                    onClick = { menuOpen = false; onDuplicate() }
                )
                DropdownMenuItem(
                    text = { Text("删除这一步", fontSize = 12.sp, color = AccentRed) },
                    enabled = canDelete,
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = AccentRed, modifier = Modifier.size(15.dp)) },
                    onClick = { menuOpen = false; onDelete() }
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
// Key picker
// ══════════════════════════════════════════════════════════════════════════

@Composable
private fun KeyPickerDialog(
    state: InputState,
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
                Text("这一帧按哪些键", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                Text(
                    "可以多选，也可以只按方向或摇杆",
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
