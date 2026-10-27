package com.macgamepad.controller.input

/**
 * A macro is a timed sequence of ordinary controller states replayed by the phone.
 *
 * The Mac never learns that a macro exists: the HID report descriptor, the report
 * length and the button bitmap are all untouched. A macro only decides which of the
 * already-declared buttons are held on each 8 ms tick, which is why the macro button
 * costs nothing on the host side (no repair, no rebinding, works with Switch, Xbox
 * and DualSense profiles alike).
 *
 * A step's [state] is an *overlay*, not a full frame: fields left at their defaults
 * mean "don't press this", and whatever the player is physically holding is kept
 * (see [withMacroOverlay]).
 */
data class MacroStep(
    /** How long this step lasts, in 8 ms ticks. 2 ticks = 16 ms is the practical floor. */
    val ticks: Int,
    val state: InputState = InputState(),
    val label: String = ""
)

/**
 * @param steps replayed in order; a step with an empty state is a deliberate gap.
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
        require(steps.isNotEmpty()) { "a macro needs at least one step" }
        require(steps.all { it.ticks > 0 }) { "every step must last at least one tick" }
    }

    val totalTicks: Int = steps.sumOf { it.ticks }

    /** Total wall-clock length, for the UI. */
    val durationMs: Int get() = (totalTicks + cooldownTicks) * TICK_MS

    companion object {
        const val TICK_MS = 8
    }
}

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
     * After the last step the cooldown frames are empty overlays, i.e. "nothing
     * pressed", which is what releases the buttons.
     */
    fun currentFrame(): InputState? {
        val m = macro ?: return null
        var start = 0
        for (step in m.steps) {
            if (tick < start + step.ticks) return step.state
            start += step.ticks
        }
        return InputState()
    }

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
 * Step lengths are in 8 ms ticks. Giving every press at least 2 ticks and putting
 * a 2-tick gap between repeats is what keeps a game from swallowing an input —
 * Tears of the Kingdom in particular reads a press shorter than ~16 ms as noise.
 */
object MacroLibrary {

    const val DEFAULT_ID = "zl_a_combo"

    /** Hold ZL, tap A three times, release — one thumb, one button. */
    val zlACombo = Macro(
        id = "zl_a_combo",
        name = "ZL + A 三连",
        steps = listOf(
            MacroStep(2),                                                   // settle
            MacroStep(4, InputState(leftTrigger = 1f), "按下 ZL"),
            MacroStep(3, InputState(leftTrigger = 1f, buttonA = true), "A"),
            MacroStep(2, InputState(leftTrigger = 1f)),
            MacroStep(3, InputState(leftTrigger = 1f, buttonA = true), "A"),
            MacroStep(2, InputState(leftTrigger = 1f)),
            MacroStep(3, InputState(leftTrigger = 1f, buttonA = true), "A"),
            MacroStep(4, InputState(leftTrigger = 1f), "松开 ZL"),
            MacroStep(2)
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
            MacroStep(2),                                                          // settle
            MacroStep(2, InputState(buttonY = true, buttonB = true), "Y+B"),        // one frame, both bits
            MacroStep(2)                                                           // release
        )
    )

    /** Mash A five times, for dialogue and "press repeatedly" prompts. */
    val mashA = Macro(
        id = "mash_a",
        name = "连发 A ×5",
        steps = buildList {
            repeat(5) {
                add(MacroStep(2, InputState(buttonA = true), "A"))
                add(MacroStep(2))
            }
            add(MacroStep(2))
        }
    )

    /** Dodge/backstep style: B then X, the second input inside the first's recovery. */
    val bIntoX = Macro(
        id = "b_into_x",
        name = "B → X 连招",
        steps = listOf(
            MacroStep(2),
            MacroStep(3, InputState(buttonB = true), "B"),
            MacroStep(1),
            MacroStep(3, InputState(buttonX = true), "X"),
            MacroStep(3)
        )
    )

    val all: List<Macro> = listOf(zlACombo, ybDupe, mashA, bIntoX)

    /** Factory versions, before any user edit. [MacroStore] layers overrides on top. */
    val defaults: List<Macro> get() = all

    fun isBuiltin(id: String): Boolean = all.any { it.id == id }

    fun byId(id: String?): Macro = all.firstOrNull { it.id == id } ?: zlACombo
}
