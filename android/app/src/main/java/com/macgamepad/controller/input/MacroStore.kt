package com.macgamepad.controller.input

import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "MacroStore"

/**
 * One boolean button, as understood by the macro editor.
 *
 * [key] doubles as the JSON field name, so a saved macro and a key chip in the
 * editor always refer to the same thing.
 */
private class FlagField(
    val key: String,
    val read: (InputState) -> Boolean,
    val write: (InputState, Boolean) -> InputState
)

/** One analog axis. Only ±1 / 0 is used by the editor; the encoder keeps the exact value. */
private class AxisField(
    val key: String,
    val read: (InputState) -> Float,
    val write: (InputState, Float) -> InputState
)

private val FLAG_FIELDS = listOf(
    FlagField("A", { it.buttonA }, { s, v -> s.copy(buttonA = v) }),
    FlagField("B", { it.buttonB }, { s, v -> s.copy(buttonB = v) }),
    FlagField("X", { it.buttonX }, { s, v -> s.copy(buttonX = v) }),
    FlagField("Y", { it.buttonY }, { s, v -> s.copy(buttonY = v) }),
    FlagField("L1", { it.buttonL1 }, { s, v -> s.copy(buttonL1 = v) }),
    FlagField("R1", { it.buttonR1 }, { s, v -> s.copy(buttonR1 = v) }),
    FlagField("UP", { it.dpadUp }, { s, v -> s.copy(dpadUp = v) }),
    FlagField("DOWN", { it.dpadDown }, { s, v -> s.copy(dpadDown = v) }),
    FlagField("LEFT", { it.dpadLeft }, { s, v -> s.copy(dpadLeft = v) }),
    FlagField("RIGHT", { it.dpadRight }, { s, v -> s.copy(dpadRight = v) }),
    FlagField("L3", { it.buttonL3 }, { s, v -> s.copy(buttonL3 = v) }),
    FlagField("R3", { it.buttonR3 }, { s, v -> s.copy(buttonR3 = v) }),
    FlagField("START", { it.buttonStart }, { s, v -> s.copy(buttonStart = v) }),
    FlagField("SELECT", { it.buttonSelect }, { s, v -> s.copy(buttonSelect = v) }),
    FlagField("HOME", { it.buttonHome }, { s, v -> s.copy(buttonHome = v) }),
    FlagField("CAPTURE", { it.buttonCapture }, { s, v -> s.copy(buttonCapture = v) })
)

private val AXIS_FIELDS = listOf(
    AxisField("LX", { it.leftStickX }, { s, v -> s.copy(leftStickX = v) }),
    AxisField("LY", { it.leftStickY }, { s, v -> s.copy(leftStickY = v) }),
    AxisField("RX", { it.rightStickX }, { s, v -> s.copy(rightStickX = v) }),
    AxisField("RY", { it.rightStickY }, { s, v -> s.copy(rightStickY = v) }),
    AxisField("LT", { it.leftTrigger }, { s, v -> s.copy(leftTrigger = v) }),
    AxisField("RT", { it.rightTrigger }, { s, v -> s.copy(rightTrigger = v) })
)

/**
 * The palette of things a macro step can hold down.
 *
 * Triggers are binary here (ZL / ZR are switches on a real pad) and stick
 * directions are the four cardinals — anything finer is pointless for a macro
 * and impossible to hit reliably at 8 ms per tick.
 */
object MacroKeys {

    const val G_FACE = "面键"
    const val G_SHOULDER = "肩键"
    const val G_DPAD = "方向键"
    const val G_STICK = "摇杆"
    const val G_SYSTEM = "系统键"

    data class Key(val id: String, val label: String, val group: String)

    val all: List<Key> = listOf(
        Key("A", "A", G_FACE),
        Key("B", "B", G_FACE),
        Key("X", "X", G_FACE),
        Key("Y", "Y", G_FACE),

        Key("L1", "L1", G_SHOULDER),
        Key("R1", "R1", G_SHOULDER),
        Key("LT", "ZL", G_SHOULDER),
        Key("RT", "ZR", G_SHOULDER),

        Key("UP", "↑", G_DPAD),
        Key("DOWN", "↓", G_DPAD),
        Key("LEFT", "←", G_DPAD),
        Key("RIGHT", "→", G_DPAD),

        Key("LS_UP", "LS↑", G_STICK),
        Key("LS_DOWN", "LS↓", G_STICK),
        Key("LS_LEFT", "LS←", G_STICK),
        Key("LS_RIGHT", "LS→", G_STICK),
        Key("RS_UP", "RS↑", G_STICK),
        Key("RS_DOWN", "RS↓", G_STICK),
        Key("RS_LEFT", "RS←", G_STICK),
        Key("RS_RIGHT", "RS→", G_STICK),
        Key("L3", "L3", G_STICK),
        Key("R3", "R3", G_STICK),

        Key("START", "＋", G_SYSTEM),
        Key("SELECT", "－", G_SYSTEM),
        Key("HOME", "Home", G_SYSTEM),
        Key("CAPTURE", "Capture", G_SYSTEM)
    )

    val groups: List<String> = listOf(G_FACE, G_SHOULDER, G_DPAD, G_STICK, G_SYSTEM)

    fun label(id: String): String = all.firstOrNull { it.id == id }?.label ?: id

    /** Whether the macro currently holds [id] down. */
    fun isPressed(state: InputState, id: String): Boolean = when (id) {
        "LT" -> state.leftTrigger > 0f
        "RT" -> state.rightTrigger > 0f
        "LS_UP" -> state.leftStickY < 0f
        "LS_DOWN" -> state.leftStickY > 0f
        "LS_LEFT" -> state.leftStickX < 0f
        "LS_RIGHT" -> state.leftStickX > 0f
        "RS_UP" -> state.rightStickY < 0f
        "RS_DOWN" -> state.rightStickY > 0f
        "RS_LEFT" -> state.rightStickX < 0f
        "RS_RIGHT" -> state.rightStickX > 0f
        else -> FLAG_FIELDS.firstOrNull { it.key == id }?.read(state) ?: false
    }

    /** Flips one key, leaving everything else in the step untouched. */
    fun toggle(state: InputState, id: String): InputState = when (id) {
        "LT" -> state.copy(leftTrigger = if (state.leftTrigger > 0f) 0f else 1f)
        "RT" -> state.copy(rightTrigger = if (state.rightTrigger > 0f) 0f else 1f)
        // Screen/HID convention: pushing up is a negative Y.
        "LS_UP" -> state.copy(leftStickY = if (state.leftStickY < 0f) 0f else -1f)
        "LS_DOWN" -> state.copy(leftStickY = if (state.leftStickY > 0f) 0f else 1f)
        "LS_LEFT" -> state.copy(leftStickX = if (state.leftStickX < 0f) 0f else -1f)
        "LS_RIGHT" -> state.copy(leftStickX = if (state.leftStickX > 0f) 0f else 1f)
        "RS_UP" -> state.copy(rightStickY = if (state.rightStickY < 0f) 0f else -1f)
        "RS_DOWN" -> state.copy(rightStickY = if (state.rightStickY > 0f) 0f else 1f)
        "RS_LEFT" -> state.copy(rightStickX = if (state.rightStickX < 0f) 0f else -1f)
        "RS_RIGHT" -> state.copy(rightStickX = if (state.rightStickX > 0f) 0f else 1f)
        else -> FLAG_FIELDS.firstOrNull { it.key == id }?.let { f -> f.write(state, !f.read(state)) } ?: state
    }

    /** Ordered list of held keys, used for the "ZL+A" caption on a step. */
    fun pressedIds(state: InputState): List<String> = all.filter { isPressed(state, it.id) }.map { it.id }

    fun summary(state: InputState): String = pressedIds(state).joinToString("+") { label(it) }

    fun isEmpty(state: InputState): Boolean = pressedIds(state).isEmpty() &&
        state.leftStickX == 0f && state.leftStickY == 0f &&
        state.rightStickX == 0f && state.rightStickY == 0f
}

/** JSON <-> [InputState]. Only non-default fields are written, so files stay small. */
internal fun encodeState(state: InputState): JSONObject {
    val o = JSONObject()
    FLAG_FIELDS.forEach { if (it.read(state)) o.put(it.key, true) }
    AXIS_FIELDS.forEach { f ->
        val v = f.read(state)
        if (v != 0f) o.put(f.key, (v * 1000f).toInt() / 1000f)
    }
    return o
}

internal fun decodeState(o: JSONObject?): InputState {
    var s = InputState()
    o ?: return s
    FLAG_FIELDS.forEach { f -> if (o.optBoolean(f.key, false)) s = f.write(s, true) }
    AXIS_FIELDS.forEach { f ->
        val raw = o.optDouble(f.key, 0.0).toFloat()
        if (raw != 0f) {
            val v = if (f.key == "LT" || f.key == "RT") raw.coerceIn(0f, 1f) else raw.coerceIn(-1f, 1f)
            s = f.write(s, v)
        }
    }
    return s
}

/**
 * Clamps a user-edited macro into something the player can actually run.
 *
 * Every step carries its own caption (derived, never typed by the user), which is
 * what the editor and the log lines show.
 */
fun Macro.sanitized(): Macro = copy(
    name = name.trim().ifEmpty { "未命名宏" }.take(24),
    steps = steps
        .take(MacroStore.MAX_STEPS)
        .map { MacroStep(it.ticks.coerceIn(MacroStore.MIN_TICKS, MacroStore.MAX_TICKS), it.state, MacroKeys.summary(it.state)) }
        .ifEmpty { listOf(MacroStep(2)) },
    cooldownTicks = cooldownTicks.coerceIn(0, MacroStore.MAX_COOLDOWN)
)

/**
 * Persists the macro list.
 *
 * Three built-ins ship with the app and live in [MacroLibrary]; anything the user
 * edits is written into an `overrides` table keyed by the built-in id, so a later
 * app update can still improve a built-in the user never touched. Macros created
 * from scratch go into `custom` and can be deleted for real.
 */
class MacroStore(private val prefs: SharedPreferences) {

    companion object {
        const val KEY_DEFS = "macro_defs_v1"
        const val MAX_STEPS = 80
        const val MIN_TICKS = 1
        const val MAX_TICKS = 250
        const val MAX_COOLDOWN = 40
        private const val KEY_OVERRIDES = "overrides"
        private const val KEY_CUSTOM = "custom"
        private const val KEY_VERSION = "version"
        private const val VERSION = 1
    }

    /** Built-ins (with any user override applied) followed by user-created macros. */
    fun loadAll(): List<Macro> {
        val root = readRoot()
        val overrides = root.optJSONObject(KEY_OVERRIDES) ?: JSONObject()

        val builtins = MacroLibrary.defaults.map { base ->
            decode(overrides.optJSONObject(base.id), base.id) ?: base
        }

        val custom = mutableListOf<Macro>()
        root.optJSONArray(KEY_CUSTOM)?.let { arr ->
            for (i in 0 until arr.length()) {
                val m = decode(arr.optJSONObject(i), null) ?: continue
                if (builtins.any { it.id == m.id } || custom.any { it.id == m.id }) continue
                custom += m
            }
        }
        return builtins + custom
    }

    /** Writes [macro] and returns the refreshed list. Built-ins are overridden, not copied. */
    fun save(macro: Macro): List<Macro> {
        val clean = macro.sanitized()
        val root = readRoot()
        if (MacroLibrary.isBuiltin(clean.id)) {
            val ov = root.optJSONObject(KEY_OVERRIDES) ?: JSONObject().also { root.put(KEY_OVERRIDES, it) }
            ov.put(clean.id, encode(clean))
        } else {
            val arr = root.optJSONArray(KEY_CUSTOM) ?: JSONArray().also { root.put(KEY_CUSTOM, it) }
            var replaced = false
            for (i in 0 until arr.length()) {
                if (arr.optJSONObject(i)?.optString("id") == clean.id) {
                    arr.put(i, encode(clean))
                    replaced = true
                    break
                }
            }
            if (!replaced) arr.put(encode(clean))
        }
        writeRoot(root)
        return loadAll()
    }

    /**
     * Removes a user-created macro, or resets a built-in back to its factory steps.
     * Either way the id survives the call for built-ins.
     */
    fun delete(id: String): List<Macro> {
        val root = readRoot()
        if (MacroLibrary.isBuiltin(id)) {
            root.optJSONObject(KEY_OVERRIDES)?.remove(id)
        } else {
            val arr = root.optJSONArray(KEY_CUSTOM)
            if (arr != null) {
                val kept = JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i)
                    if (o != null && o.optString("id") != id) kept.put(o)
                }
                root.put(KEY_CUSTOM, kept)
            }
        }
        writeRoot(root)
        return loadAll()
    }

    /** True when a built-in has been edited and can be restored. */
    fun isOverridden(id: String): Boolean =
        (readRoot().optJSONObject(KEY_OVERRIDES))?.has(id) == true

    /** Builds (but does not save) a fresh macro, optionally copied from [from]. */
    fun newMacro(existing: List<Macro>, from: Macro? = null): Macro {
        val id = "user_" + System.currentTimeMillis().toString(36)
        val baseName = from?.let { "${it.name} 副本" } ?: "新宏"
        val used = existing.map { it.name }.toSet()
        var name = baseName.take(24)
        var n = 2
        while (name in used) {
            val suffix = " $n"
            name = baseName.take(24 - suffix.length) + suffix
            n++
        }
        val steps = from?.steps?.take(MAX_STEPS)
            ?: listOf(MacroStep(2, InputState(buttonA = true), "A"), MacroStep(2))
        return Macro(id = id, name = name, steps = steps, cooldownTicks = from?.cooldownTicks ?: 4)
    }

    // ── internals ────────────────────────────────────────────────────────

    private fun encode(macro: Macro): JSONObject {
        val o = JSONObject()
        o.put("id", macro.id)
        o.put("name", macro.name)
        o.put("c", macro.cooldownTicks)
        val steps = JSONArray()
        macro.steps.forEach { step ->
            steps.put(
                JSONObject()
                    .put("t", step.ticks)
                    .put("s", encodeState(step.state))
            )
        }
        o.put("steps", steps)
        return o
    }

    private fun decode(o: JSONObject?, fallbackId: String?): Macro? {
        o ?: return null
        val id = o.optString("id").ifBlank { fallbackId ?: return null }
        val arr = o.optJSONArray("steps") ?: return null
        val steps = mutableListOf<MacroStep>()
        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            val ticks = s.optInt("t", 2).coerceIn(MIN_TICKS, MAX_TICKS)
            val state = decodeState(s.optJSONObject("s"))
            steps += MacroStep(ticks, state, MacroKeys.summary(state))
        }
        if (steps.isEmpty()) return null
        val name = o.optString("name").trim().ifEmpty { "未命名宏" }.take(24)
        val cooldown = o.optInt("c", 4).coerceIn(0, MAX_COOLDOWN)
        return try {
            Macro(id = id, name = name, steps = steps, cooldownTicks = cooldown)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "dropping unreadable macro $id", e)
            null
        }
    }

    private fun readRoot(): JSONObject = try {
        val raw = prefs.getString(KEY_DEFS, null)
        if (raw.isNullOrBlank()) JSONObject() else JSONObject(raw)
    } catch (e: Exception) {
        Log.w(TAG, "macro store is corrupt, ignoring it", e)
        JSONObject()
    }

    private fun writeRoot(root: JSONObject) {
        root.put(KEY_VERSION, VERSION)
        prefs.edit().putString(KEY_DEFS, root.toString()).apply()
    }
}
