package com.macgamepad.controller.input

/**
 * A macro is a timeline of ordinary controller presses replayed by the phone.
 *
 * The Mac never learns that a macro exists: the HID report descriptor, the report
 * length and the button bitmap are all untouched. A macro only decides which of the
 * already-declared buttons are held on each 8 ms tick, which is why the macro button
 * costs nothing on the host side (no repair, no rebinding, works with Switch, Xbox
 * and DualSense profiles alike).
 *
 * The timeline model is what makes "hold ZL, tap X half-way through, then tap A"
 * expressible at all: each [MacroStep] is an independent press that occupies a
 * window on the clock, so several of them can be live at the same time. Two steps
 * whose windows overlap are simply merged into one report (see [Macro.overlayAt]),
 * which is also how a chord such as Y+B ends up in a single frame.
 *
 * Steps are *overlays*, not full frames: fields left at their defaults mean "this
 * press does not touch that control", and whatever the player is physically doing
 * is kept (see [withMacroOverlay]).
 */
/**
 * How a press behaves inside its own window.
 *
 * [HOLD] is the plain "keep the button down" case — ZL, a stick push, a chord.
 * [TURBO] turns the same window into a repeating tap: it is down for
 * [MacroStep.burstTicks], up for [MacroStep.gapTicks], over and over. Mashing A
 * through a dialogue box is one turbo press instead of twenty hand-placed rows.
 */
enum class PressMode { HOLD, TURBO }

data class MacroStep(
    /** When this press starts, in 8 ms ticks from the beginning of the macro. */
    val start: Int = 0,
    /** How long the press owns the button, in ticks — the whole window, turbo or not. */
    val ticks: Int = 2,
    val state: InputState = InputState(),
    val mode: PressMode = PressMode.HOLD,
    /** Turbo only: how long each tap stays down. */
    val burstTicks: Int = 2,
    /** Turbo only: silence between two taps. */
    val gapTicks: Int = 2
) {
    /** One past the last tick this press is live on. */
    val end: Int get() = start + ticks

    /** Down-for / up-for cycle of a turbo press; never shorter than two ticks. */
    val periodTicks: Int get() = (burstTicks + gapTicks).coerceAtLeast(2)

    /** How many taps a turbo press manages to start inside its window. */
    val repeatCount: Int
        get() = if (mode == PressMode.HOLD) 1 else ((ticks + gapTicks) / periodTicks).coerceAtLeast(1)

    /** True on the ticks this press actually has the buttons down. */
    fun isLiveAt(tick: Int): Boolean {
        if (tick < start || tick >= end) return false
        if (mode == PressMode.HOLD) return true
        val local = tick - start
        return local % periodTicks < burstTicks.coerceIn(1, periodTicks - 1)
    }

    /** "ZL+A" style caption, derived rather than typed. */
    val caption: String get() = MacroKeys.summary(state)

    /** Caption plus the repeat count, for lists that have the room. */
    val fullCaption: String
        get() = if (mode == PressMode.TURBO) "${caption} 连击 ×$repeatCount" else caption

    val isEmpty: Boolean get() = MacroKeys.isEmpty(state)
}

/**
 * @param steps the presses; several may overlap, and gaps between them are just
 *        clock time with nothing pressed — no filler steps needed.
 * @param cooldownTicks trailing frames sent with nothing pressed, so a report that
 *        arrives out of order can never leave a button stuck down. In [loop] mode
 *        these are also the breath between two rounds.
 * @param loop replay the script over and over until the player taps the macro
 *        button again. The trailing cooldown is what separates the rounds, so a
 *        looping macro cannot machine-gun the same press without a frame of air.
 */
data class Macro(
    val id: String,
    val name: String,
    val steps: List<MacroStep>,
    val cooldownTicks: Int = 4,
    val loop: Boolean = false
) {
    init {
        require(steps.isNotEmpty()) { "a macro needs at least one press" }
        require(steps.all { it.start >= 0 && it.ticks > 0 }) { "every press needs a start and a length" }
    }

    /** Length of the timeline, i.e. the end of the last press. */
    val totalTicks: Int = steps.maxOf { it.end }

    /** Total wall-clock length, for the UI. */
    val durationMs: Int get() = (totalTicks + cooldownTicks) * TICK_MS

    /** Steps in timeline order, which is also the order the editor lists them in. */
    val ordered: List<MacroStep> get() = steps.sortedWith(compareBy({ it.start }, { it.end }))

    /** True when two presses are live on the same tick, i.e. a chord or a hold. */
    val hasOverlap: Boolean
        get() {
            val sorted = ordered
            for (i in sorted.indices) {
                for (j in i + 1 until sorted.size) {
                    if (sorted[j].start >= sorted[i].end) break
                    if (sorted[j].start < sorted[i].end && sorted[j].end > sorted[i].start) return true
                }
            }
            return false
        }

    /** The report the macro asks for on [tick]: every press live at that moment, merged. */
    fun overlayAt(tick: Int): InputState {
        var merged = InputState()
        for (step in steps) {
            if (step.isLiveAt(tick)) merged = merged.union(step.state)
        }
        return merged
    }

    companion object {
        const val TICK_MS = 8
    }
}

/**
 * Merges two live presses into one report: buttons are OR-ed, triggers take the
 * stronger value, and axes add up (so "up + right" works) before clamping.
 */
fun InputState.union(other: InputState): InputState = copy(
    leftStickX = (leftStickX + other.leftStickX).coerceIn(-1f, 1f),
    leftStickY = (leftStickY + other.leftStickY).coerceIn(-1f, 1f),
    rightStickX = (rightStickX + other.rightStickX).coerceIn(-1f, 1f),
    rightStickY = (rightStickY + other.rightStickY).coerceIn(-1f, 1f),

    dpadUp = dpadUp || other.dpadUp,
    dpadDown = dpadDown || other.dpadDown,
    dpadLeft = dpadLeft || other.dpadLeft,
    dpadRight = dpadRight || other.dpadRight,

    buttonA = buttonA || other.buttonA,
    buttonB = buttonB || other.buttonB,
    buttonX = buttonX || other.buttonX,
    buttonY = buttonY || other.buttonY,

    buttonL1 = buttonL1 || other.buttonL1,
    buttonR1 = buttonR1 || other.buttonR1,

    leftTrigger = maxOf(leftTrigger, other.leftTrigger),
    rightTrigger = maxOf(rightTrigger, other.rightTrigger),

    buttonL3 = buttonL3 || other.buttonL3,
    buttonR3 = buttonR3 || other.buttonR3,

    buttonStart = buttonStart || other.buttonStart,
    buttonSelect = buttonSelect || other.buttonSelect,
    buttonHome = buttonHome || other.buttonHome,
    buttonCapture = buttonCapture || other.buttonCapture
)

/**
 * Drives a [Macro] one tick at a time.
 *
 * Deliberately has no timers of its own: the 125 Hz transmission loop in
 * `GamepadService` calls [advance] once per iteration, so the macro can never
 * outrun the Bluetooth link or block the sending thread with a sleep.
 *
 * Thread safety: [start], [advance] and [cancel] are called from the sending
 * coroutine, [currentFrame] additionally from the UI thread (every touch event),
 * hence the volatile fields and the immutable snapshots they publish.
 */
class MacroPlayer {

    @Volatile private var macro: Macro? = null
    @Volatile private var tick: Int = 0
    /** How many rounds of a looping macro have finished, for the UI and the log. */
    @Volatile private var rounds: Int = 0

    val isRunning: Boolean get() = macro != null

    val runningId: String? get() = macro?.id

    /** Rounds completed since [start]; always 0 for a one-shot macro. */
    val completedRounds: Int get() = rounds

    fun start(macro: Macro) {
        tick = 0
        rounds = 0
        this.macro = macro
    }

    fun cancel() {
        macro = null
        tick = 0
        rounds = 0
    }

    /**
     * The overlay for the tick being transmitted right now, or null when idle.
     * After the last press the cooldown frames are empty overlays, i.e. "nothing
     * pressed", which is what releases the buttons.
     */
    fun currentFrame(): InputState? = macro?.overlayAt(tick)

    /**
     * Advance one tick. Returns false once the macro (and its cooldown) is over.
     *
     * A looping macro never ends by itself: at the end of the cooldown it rewinds to
     * tick 0 and runs again, which is why the caller must keep offering frames until
     * the player stops it (or the session drops).
     */
    fun advance(): Boolean {
        val m = macro ?: return false
        val next = tick + 1
        if (next >= m.totalTicks + m.cooldownTicks) {
            if (!m.loop) {
                cancel()
                return false
            }
            tick = 0
            rounds++
            return true
        }
        tick = next
        return true
    }
}

/**
 * Combines what the player is doing with the macro's overlay.
 *
 * Buttons are OR-ed (either side can press), triggers take the stronger value and
 * an axis is taken from the macro only when the macro actually moves it — a macro
 * that walks the stick must not cancel the player's own camera input.
 */
fun InputState.withMacroOverlay(overlay: InputState): InputState = copy(
    leftStickX = if (overlay.leftStickX != 0f) overlay.leftStickX else leftStickX,
    leftStickY = if (overlay.leftStickY != 0f) overlay.leftStickY else leftStickY,
    rightStickX = if (overlay.rightStickX != 0f) overlay.rightStickX else rightStickX,
    rightStickY = if (overlay.rightStickY != 0f) overlay.rightStickY else rightStickY,

    dpadUp = dpadUp || overlay.dpadUp,
    dpadDown = dpadDown || overlay.dpadDown,
    dpadLeft = dpadLeft || overlay.dpadLeft,
    dpadRight = dpadRight || overlay.dpadRight,

    buttonA = buttonA || overlay.buttonA,
    buttonB = buttonB || overlay.buttonB,
    buttonX = buttonX || overlay.buttonX,
    buttonY = buttonY || overlay.buttonY,

    buttonL1 = buttonL1 || overlay.buttonL1,
    buttonR1 = buttonR1 || overlay.buttonR1,

    leftTrigger = maxOf(leftTrigger, overlay.leftTrigger),
    rightTrigger = maxOf(rightTrigger, overlay.rightTrigger),

    buttonL3 = buttonL3 || overlay.buttonL3,
    buttonR3 = buttonR3 || overlay.buttonR3,

    buttonStart = buttonStart || overlay.buttonStart,
    buttonSelect = buttonSelect || overlay.buttonSelect,
    buttonHome = buttonHome || overlay.buttonHome,
    buttonCapture = buttonCapture || overlay.buttonCapture
)

/**
 * The built-in macros. Add one here and it becomes selectable by id; the on-screen
 * macro button fires whichever the `macro_selected` preference names.
 *
 * Step lengths are in 8 ms ticks and positions are absolute. Giving every press at
 * least 2 ticks and leaving 2 ticks between repeats is what keeps a game from
 * swallowing an input — Tears of the Kingdom in particular reads a press shorter
 * than ~16 ms as noise.
 */
object MacroLibrary {

    const val DEFAULT_ID = "zl_a_combo"

    /**
     * One round of the duplication input: START, X, an A mash, then the same-frame
     * Y+B dupe chord. Offsets are relative to the round's own start so [dupRounds]
     * can stamp as many of them as the chain needs.
     */
    private val dupRound = listOf(
        MacroStep(start = 0, ticks = 3, state = InputState(buttonStart = true)),
        MacroStep(start = 5, ticks = 3, state = InputState(buttonX = true)),
        MacroStep(
            start = 11, ticks = 38,
            state = InputState(buttonA = true),
            mode = PressMode.TURBO, burstTicks = 6, gapTicks = 2
        ),
        MacroStep(start = 52, ticks = 3, state = InputState(buttonB = true, buttonY = true))
    )

    /** How long one [dupRound] occupies, in ticks: the START press to the Y+B release. */
    private const val DUP_ROUND_TICKS = 55

    /**
     * Stamps [times] copies of [dupRound] onto the clock from [base], [gap] ticks of
     * silence between two rounds. 8 ms ticks, so a "50 ms" gap is really 48 ms.
     */
    private fun dupRounds(times: Int, gap: Int = 6, base: Int = 62): List<MacroStep> {
        val stride = DUP_ROUND_TICKS + gap
        return (0 until times).flatMap { round ->
            dupRound.map { it.copy(start = base + round * stride + it.start) }
        }
    }

    /**
     * The take the user settled on as the factory default (4.9 s, 23 presses): ZL
     * held for 520 ms while X / A / START are tapped inside it, then the START → X →
     * A×5 → Y+B round played four times, 48 ms apart, a stick flick, an A burst, a
     * second flick and a second burst.
     *
     * The round is stamped rather than hand-listed so the spacing inside it stays
     * identical from copy to copy — the game only cares that the Y+B pair lands in
     * one report, not that a human counted the gaps. Only the first round sits
     * inside the ZL hold; the three repeats run without it.
     */
    val zlACombo = Macro(
        id = "zl_a_combo",
        name = "yb复制脚本",
        steps = listOf(
            MacroStep(start = 0, ticks = 65, state = InputState(leftTrigger = 1f)),
            MacroStep(start = 31, ticks = 3, state = InputState(buttonX = true)),
            MacroStep(start = 36, ticks = 3, state = InputState(buttonA = true))
        ) + dupRounds(times = 4) + listOf(
            MacroStep(start = 321, ticks = 27, state = InputState(leftStickY = 1f)),
            MacroStep(
                start = 392, ticks = 70,
                state = InputState(buttonA = true),
                mode = PressMode.TURBO, burstTicks = 4, gapTicks = 3
            ),
            MacroStep(start = 498, ticks = 27, state = InputState(leftStickY = 1f)),
            MacroStep(
                start = 544, ticks = 70,
                state = InputState(buttonA = true),
                mode = PressMode.TURBO, burstTicks = 4, gapTicks = 3
            )
        )
    )

    /**
     * The first take of the duplication script the user actually got working
     * (3.4 s, 11 presses): ZL held for 520 ms with X / A / START tapped inside it,
     * then START → X → A×5 → Y+B once, a downward stick flick, an A burst, a second
     * flick and a second burst.
     *
     * Kept next to [zlACombo] as the "one round" version: the same choreography
     * without the extra three passes, which is what to fall back to when the game
     * refuses the second or third dupe.
     */
    val ybDupe = Macro(
        id = "yb_dupe",
        name = "YB 复制",
        steps = listOf(
            MacroStep(start = 0, ticks = 65, state = InputState(leftTrigger = 1f)),
            MacroStep(start = 31, ticks = 3, state = InputState(buttonX = true)),
            MacroStep(start = 36, ticks = 3, state = InputState(buttonA = true)),
            MacroStep(start = 62, ticks = 3, state = InputState(buttonStart = true)),
            MacroStep(start = 67, ticks = 3, state = InputState(buttonX = true)),
            MacroStep(
                start = 73, ticks = 38,
                state = InputState(buttonA = true),
                mode = PressMode.TURBO, burstTicks = 6, gapTicks = 2
            ),
            MacroStep(start = 114, ticks = 3, state = InputState(buttonB = true, buttonY = true)),
            MacroStep(start = 138, ticks = 27, state = InputState(leftStickY = 1f)),
            MacroStep(
                start = 209, ticks = 70,
                state = InputState(buttonA = true),
                mode = PressMode.TURBO, burstTicks = 4, gapTicks = 3
            ),
            MacroStep(start = 315, ticks = 27, state = InputState(leftStickY = 1f)),
            MacroStep(
                start = 361, ticks = 70,
                state = InputState(buttonA = true),
                mode = PressMode.TURBO, burstTicks = 4, gapTicks = 3
            )
        )
    )

    val all: List<Macro> = listOf(zlACombo, ybDupe)

    /** Factory versions, before any user edit. [MacroStore] layers overrides on top. */
    val defaults: List<Macro> get() = all

    fun isBuiltin(id: String): Boolean = all.any { it.id == id }

    fun byId(id: String?): Macro = all.firstOrNull { it.id == id } ?: zlACombo
}
