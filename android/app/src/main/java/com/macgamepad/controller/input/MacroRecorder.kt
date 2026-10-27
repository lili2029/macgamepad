package com.macgamepad.controller.input

import android.util.Log
import kotlin.math.abs
import kotlin.math.round

/**
 * Turns "what the player just did" into a [Macro].
 *
 * The recorder is fed one [InputState] per 8 ms tick by the transmission loop in
 * `GamepadService` — the same frame the host has just received — and keeps a run
 * list of equal states instead of every single frame. On the way out those runs
 * are re-cut per control into [MacroStep]s, so a three-second flourish costs a
 * handful of editable press bars rather than 375 frames.
 *
 * Two deliberate losses of detail, both matching what a macro can actually express:
 * triggers are recorded as pressed/not (ZL and ZR are switches on a real pad), and
 * stick axes are snapped to a quarter — finer values are thumb jitter, and every
 * wobble would otherwise become its own step.
 *
 * Thread safety: [start], [capture] and [stop] are called from the sending
 * coroutine; [stats] also from the UI thread, hence the lock around everything.
 */
class MacroRecorder {

    /** What the UI needs to draw the recording pill. */
    data class Stats(
        val recording: Boolean = false,
        val ticks: Int = 0,
        val steps: Int = 0
    ) {
        val durationMs: Int get() = ticks * Macro.TICK_MS

        /** True once the 12 s budget is used up; the caller stops and saves. */
        val saturated: Boolean get() = ticks >= MAX_TICKS
    }

    private class Run(val state: InputState, var ticks: Int)

    private val lock = Any()
    private var running = false
    private var totalTicks = 0
    private val runs = ArrayList<Run>()
    private var current: InputState? = null
    private var currentTicks = 0
    private var capped = false

    val isRecording: Boolean get() = synchronized(lock) { running }

    fun start() = synchronized(lock) {
        runs.clear()
        current = null
        currentTicks = 0
        totalTicks = 0
        capped = false
        running = true
    }

    fun cancel() = synchronized(lock) {
        running = false
        runs.clear()
        current = null
        currentTicks = 0
        totalTicks = 0
        capped = false
    }

    /** One tick worth of the report the host just received. */
    fun capture(state: InputState) {
        val snapped = snap(state)
        synchronized(lock) {
            if (!running || capped) return
            totalTicks++
            if (current != null && current == snapped) {
                currentTicks++
            } else {
                flushLocked()
                current = snapped
                currentTicks = 1
            }
            if (totalTicks >= MAX_TICKS) {
                // Stop sampling but stay "recording": the caller still has to save,
                // and letting it do that through the normal path keeps one code path.
                capped = true
                Log.i(TAG, "Recorder hit the ${MAX_TICKS}-tick limit")
            }
        }
    }

    fun stats(): Stats = synchronized(lock) {
        Stats(running, totalTicks, runs.size + if (currentTicks > 0) 1 else 0)
    }

    /** Ends the recording and returns its steps; empty when nothing was pressed. */
    fun stop(): List<MacroStep> = synchronized(lock) {
        if (!running) return emptyList()
        running = false
        flushLocked()
        val trimmed = trim(runs)
        runs.clear()
        current = null
        currentTicks = 0
        totalTicks = 0
        capped = false
        toSteps(trimmed)
    }

    // ── internals ────────────────────────────────────────────────────────

    private fun flushLocked() {
        val state = current ?: return
        if (currentTicks <= 0) return
        if (runs.isNotEmpty() && runs.last().state == state) {
            runs.last().ticks += currentTicks
        } else {
            runs += Run(state, currentTicks)
        }
        current = null
        currentTicks = 0
    }

    /**
     * Drops the idle frames before the first real press and after the last one
     * (keeping a couple so the release is unambiguous), so tapping record and then
     * thinking for two seconds doesn't bake dead air into the macro.
     */
    private fun trim(input: List<Run>): List<Run> {
        var from = 0
        var to = input.size
        while (from < to && MacroKeys.isEmpty(input[from].state)) from++
        while (to > from && MacroKeys.isEmpty(input[to - 1].state)) to--
        if (from >= to) return emptyList()
        val head = input.subList(from, to).toMutableList()
        // Less than a host frame is unplayable, so pad up to the practical floor.
        head.forEach { if (it.ticks < MIN_TICKS) it.ticks = MIN_TICKS }
        if (head.last().ticks < TRAILING_RELEASE_TICKS && !MacroKeys.isEmpty(head.last().state)) {
            head += Run(InputState(), TRAILING_RELEASE_TICKS)
        }
        return head
    }

    private fun toSteps(input: List<Run>): List<MacroStep> {
        if (input.isEmpty()) return emptyList()
        val steps = spansToSteps(collectSpans(input))
        if (steps.size <= MacroStore.MAX_STEPS) return steps
        // Longer than the editor will keep: cut the tail. Dropping the last presses
        // is safe because the macro's own cooldown releases everything afterwards.
        Log.w(TAG, "recording longer than ${MacroStore.MAX_STEPS} presses, trimming ${steps.size - MacroStore.MAX_STEPS}")
        return steps.take(MacroStore.MAX_STEPS)
    }

    /**
     * One press of one control, on the recording's clock.
     *
     * A take is stored per control rather than per frame, which is what makes the
     * result editable afterwards: "ZL held from 0 to 300 ms" stays a single bar the
     * user can shorten, instead of being smeared across forty frames that each have
     * to be edited by hand.
     */
    private class Span(
        val channel: String,
        val start: Int,
        var ticks: Int,
        val apply: (InputState) -> InputState
    )

    /** Splits every frame into one span per control that is doing something. */
    private fun collectSpans(runs: List<Run>): List<Span> {
        val spans = ArrayList<Span>()
        var t = 0
        for (run in runs) {
            val dur = run.ticks
            FLAG_FIELDS.forEach { f ->
                if (f.read(run.state)) spans += Span(f.key, t, dur) { s -> f.write(s, true) }
            }
            AXIS_FIELDS.forEach { f ->
                val v = f.read(run.state)
                if (v != 0f) spans += Span("${f.key}=$v", t, dur) { s -> f.write(s, v) }
            }
            t += dur
        }
        return spans
    }

    /**
     * Glues neighbouring spans of the same control back together (a hold that was
     * recorded as two runs because another button changed meanwhile becomes one
     * bar), splits the ones that outgrow the per-step limit, and finally bundles
     * every control that shares a window into one step.
     */
    private fun spansToSteps(spans: List<Span>): List<MacroStep> {
        val merged = ArrayList<Span>()
        spans.groupBy { it.channel }.forEach { (_, list) ->
            val sorted = list.sortedBy { it.start }
            var current = sorted.first()
            for (next in sorted.drop(1)) {
                if (next.start <= current.start + current.ticks) {
                    current.ticks = maxOf(current.ticks, next.start + next.ticks - current.start)
                } else {
                    merged += current
                    current = next
                }
            }
            merged += current
        }

        val split = ArrayList<Span>()
        merged.forEach { span ->
            var left = span.ticks
            var at = span.start
            while (left > 0) {
                val take = left.coerceAtMost(MacroStore.MAX_TICKS)
                split += Span(span.channel, at, take, span.apply)
                at += take
                left -= take
            }
        }

        return split
            .groupBy { it.start to it.ticks }
            .map { (window, group) ->
                MacroStep(
                    start = window.first,
                    ticks = window.second,
                    state = group.fold(InputState()) { acc, span -> span.apply(acc) }
                )
            }
            .sortedWith(compareBy({ it.start }, { it.end }))
    }

    /** Collapses frame-to-frame noise so a still thumb doesn't create steps. */
    private fun snap(state: InputState): InputState = state.copy(
        leftTrigger = if (state.leftTrigger >= TRIGGER_THRESHOLD) 1f else 0f,
        rightTrigger = if (state.rightTrigger >= TRIGGER_THRESHOLD) 1f else 0f,
        leftStickX = snapAxis(state.leftStickX),
        leftStickY = snapAxis(state.leftStickY),
        rightStickX = snapAxis(state.rightStickX),
        rightStickY = snapAxis(state.rightStickY)
    )

    private fun snapAxis(value: Float): Float {
        if (abs(value) < STICK_DEAD_ZONE) return 0f
        val snapped = round(value / STICK_QUANTUM) * STICK_QUANTUM
        return snapped.coerceIn(-1f, 1f)
    }

    companion object {
        private const val TAG = "MacroRecorder"

        /** 12 seconds at 8 ms a tick. */
        const val MAX_TICKS = 1500

        private const val MIN_TICKS = 2
        private const val TRAILING_RELEASE_TICKS = 2
        private const val STICK_DEAD_ZONE = 0.12f
        private const val STICK_QUANTUM = 0.25f
        private const val TRIGGER_THRESHOLD = 0.4f
    }
}
