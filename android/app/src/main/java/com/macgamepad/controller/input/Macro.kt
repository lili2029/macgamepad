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
data class MacroStep(
    /** When this press starts, in 8 ms ticks from the beginning of the macro. */
    val start: Int = 0,
    /** How long it is held, in ticks. 2 ticks = 16 ms is the practical floor. */
    val ticks: Int = 2,
    val state: InputState = InputState()
) {
    /** One past the last tick this press is live on. */
    val end: Int get() = start + ticks

    /** "ZL+A" style caption, derived rather than typed. */
    val caption: String get() = MacroKeys.summary(state)

    val isEmpty: Boolean get() = MacroKeys.isEmpty(state)
}

/**
 * @param steps the presses; several may overlap, and gaps between them are just
 *        clock time with nothing pressed — no filler steps needed.
 * @param cooldownTicks trailing frames sent with nothing pressed, so a report that
 *        arrives out of order can never leave a button stuck down.
 */
data class Macro(
    val id: String,
    val name: String,
    val steps: List<MacroStep>,
    val cooldownTicks: Int = 4
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
            if (tick >= step.start && tick < step.end) merged = merged.union(step.state)
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

    val isRunning: Boolean get() = macro != null

    val runningId: String? get() = macro?.id

    fun start(macro: Macro) {
        tick = 0
        this.macro = macro
    }

    fun cancel() {
        macro = null
        tick = 0
    }

    /**
     * The overlay for the tick being transmitted right now, or null when idle.
     * After the last press the cooldown frames are empty overlays, i.e. "nothing
     * pressed", which is what releases the buttons.
     */
    fun currentFrame(): InputState? = macro?.overlayAt(tick)

    /** Advance one tick. Returns false once the macro (and its cooldown) is over. */
    fun advance(): Boolean {
        val m = macro ?: return false
        val next = tick + 1
        if (next >= m.totalTicks + m.cooldownTicks) {
            cancel()
            return false
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
     * One long ZL hold with three A taps inside it: the whole point of the timeline
     * model, since repeating ZL in every frame would be both longer and unreadable.
     */
    val zlACombo = Macro(
        id = "zl_a_combo",
        name = "ZL 长按 + A×3",
        steps = listOf(
            MacroStep(start = 0, ticks = 40, state = InputState(leftTrigger = 1f)),
            MacroStep(start = 6, ticks = 3, state = InputState(buttonA = true)),
            MacroStep(start = 14, ticks = 3, state = InputState(buttonA = true)),
            MacroStep(start = 22, ticks = 3, state = InputState(buttonA = true))
        )
    )

    /**
     * The exact shape the editor was built around: hold ZL, press X while it is
     * still down, then A. Each press keeps its own window, so the overlap is what
     * the game sees.
     */
    val zlHoldXThenA = Macro(
        id = "zl_x_a",
        name = "ZL 长按 + X → A",
        steps = listOf(
            MacroStep(start = 0, ticks = 32, state = InputState(leftTrigger = 1f)),
            MacroStep(start = 6, ticks = 4, state = InputState(buttonX = true)),
            MacroStep(start = 18, ticks = 4, state = InputState(buttonA = true))
        )
    )

    /**
     * The classic "Y+B" duplication input: with materials held in the pouch menu,
     * Y and B have to arrive in the *same* report. Split across two frames the game
     * reads B (close the menu) and then Y (throw/attack), which is why doing this
     * with one thumb is so hit-and-miss. Only a single 16 ms frame is held so the
     * pair cannot be sampled twice after the menu is already gone.
     *
     * Works on TotK 1.1.0 / 1.1.1; Nintendo closed it in 1.1.2.
     */
    val ybDupe = Macro(
        id = "yb_dupe",
        name = "YB 复制",
        steps = listOf(
            MacroStep(start = 2, ticks = 2, state = InputState(buttonY = true, buttonB = true))
        )
    )

    /** Mash A five times, for dialogue and "press repeatedly" prompts. */
    val mashA = Macro(
        id = "mash_a",
        name = "连发 A ×5",
        steps = List(5) { i ->
            MacroStep(start = i * 4, ticks = 2, state = InputState(buttonA = true))
        }
    )

    /** Dodge/backstep style: B then X, the second input inside the first's recovery. */
    val bIntoX = Macro(
        id = "b_into_x",
        name = "B → X 连招",
        steps = listOf(
            MacroStep(start = 0, ticks = 3, state = InputState(buttonB = true)),
            MacroStep(start = 4, ticks = 3, state = InputState(buttonX = true))
        )
    )

    val all: List<Macro> = listOf(zlACombo, zlHoldXThenA, ybDupe, mashA, bIntoX)

    /** Factory versions, before any user edit. [MacroStore] layers overrides on top. */
    val defaults: List<Macro> get() = all

    fun isBuiltin(id: String): Boolean = all.any { it.id == id }

    fun byId(id: String?): Macro = all.firstOrNull { it.id == id } ?: zlACombo
}
