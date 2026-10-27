package com.macgamepad.controller

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import android.bluetooth.BluetoothDevice
import com.macgamepad.controller.bt.BluetoothGamepadService
import com.macgamepad.controller.bt.GamepadService
import com.macgamepad.controller.input.InputState
import com.macgamepad.controller.input.Macro
import com.macgamepad.controller.input.MacroLibrary
import com.macgamepad.controller.input.MacroStore
import com.macgamepad.controller.input.sanitized
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.ui.geometry.Offset

/**
 * ViewModel managing the gamepad and UI state.
 *
 * Interacts with the background GamepadService to send input reports and get connection updates.
 */
class GamepadViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "GamepadVM"
        private const val PREFS_NAME = "macgamepad_prefs"
        private const val KEY_PROFILE = "active_profile"
        private const val KEY_MODE = "active_mode"
        private const val KEY_MACRO = "macro_selected"
        // "macro" is a layout element like any other, so the extra button can be
        // dragged, scaled and reset per profile.
        private val LAYOUT_COMPONENTS = listOf("leftStick", "rightStick", "dpad", "abxy", "shoulderL", "shoulderR", "sysButtons", "macro")
    }

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ================================================================
    // Layout pref key helpers — NAMESPACED BY PROFILE so each profile
    // has its own independent layout. This was the root cause of the
    // "one layout affects all controllers" bug.
    // ================================================================
    private fun offsetXKey(profileId: String, componentId: String) = "ly_${profileId}_ox_$componentId"
    private fun offsetYKey(profileId: String, componentId: String) = "ly_${profileId}_oy_$componentId"
    private fun scaleKey(profileId: String, componentId: String)   = "ly_${profileId}_sc_$componentId"

    // ================================================================
    // Navigation state
    // ================================================================
    enum class Screen { CONNECTION, DASHBOARD, GAMEPAD, PROFILE, SETTINGS, MACRO_EDITOR }

    private val _currentScreen = MutableStateFlow(Screen.CONNECTION)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    // ================================================================
    // Active controller profile skin
    // ================================================================
    private val _activeProfileId = MutableStateFlow(prefs.getString(KEY_PROFILE, "ps5") ?: "ps5")
    val activeProfileId: StateFlow<String> = _activeProfileId.asStateFlow()

    // ================================================================
    // Active service reference (bound from MainActivity)
    // ================================================================
    private val _gamepadService = MutableStateFlow<GamepadService?>(null)
    val gamepadService: StateFlow<GamepadService?> = _gamepadService.asStateFlow()

    // ================================================================
    // Flows mapping connection states directly from GamepadService
    // ================================================================
    private val _connectionState = MutableStateFlow(BluetoothGamepadService.ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BluetoothGamepadService.ConnectionState> = _connectionState.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _mode = MutableStateFlow(GamepadService.Companion.Mode.GAMEPAD)
    val mode: StateFlow<GamepadService.Companion.Mode> = _mode.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDevice>> = _pairedDevices.asStateFlow()

    // Persistent configuration flows
    private val _sensitivity = MutableStateFlow(prefs.getFloat("pref_sensitivity", 1.0f))
    val sensitivity: StateFlow<Float> = _sensitivity.asStateFlow()

    private val _deadZone = MutableStateFlow(prefs.getFloat("pref_deadzone", 0.15f))
    val deadZone: StateFlow<Float> = _deadZone.asStateFlow()

    private val _hapticStrength = MutableStateFlow(prefs.getFloat("pref_haptic", 0.8f))
    val hapticStrength: StateFlow<Float> = _hapticStrength.asStateFlow()

    private val _hapticEnabled = MutableStateFlow(prefs.getBoolean("pref_haptic_enabled", true))
    val hapticEnabled: StateFlow<Boolean> = _hapticEnabled.asStateFlow()

    private val _gyroEnabled = MutableStateFlow(prefs.getBoolean("pref_gyro", false))
    val gyroEnabled: StateFlow<Boolean> = _gyroEnabled.asStateFlow()

    private val _autoReconnect = MutableStateFlow(prefs.getBoolean("pref_auto_reconnect", false))
    val autoReconnect: StateFlow<Boolean> = _autoReconnect.asStateFlow()

    private val _vibrationEnabled = MutableStateFlow(prefs.getBoolean("pref_vibration", true))
    val vibrationEnabled: StateFlow<Boolean> = _vibrationEnabled.asStateFlow()

    // ================================================================
    // Macros
    //
    // The built-ins live in MacroLibrary; whatever the user edits or creates is
    // layered on top by MacroStore, so the on-screen button can replay a script
    // of the user's own making without touching the HID descriptor.
    // ================================================================
    private val macroStore = MacroStore(prefs)

    private val _macros = MutableStateFlow(macroStore.loadAll())
    val macros: StateFlow<List<Macro>> = _macros.asStateFlow()

    private val _selectedMacroId = MutableStateFlow(
        prefs.getString(KEY_MACRO, MacroLibrary.DEFAULT_ID) ?: MacroLibrary.DEFAULT_ID
    )
    val selectedMacroId: StateFlow<String> = _selectedMacroId.asStateFlow()

    /** Non-null while the editor is open. */
    private val _editingMacroId = MutableStateFlow<String?>(null)
    val editingMacroId: StateFlow<String?> = _editingMacroId.asStateFlow()

    /** Where the editor was opened from, so Back returns there. */
    private var macroEditorReturn: Screen = Screen.DASHBOARD

    init {
        // The stored selection may point at a macro that was deleted in a previous run.
        if (_macros.value.none { it.id == _selectedMacroId.value }) {
            _macros.value.firstOrNull()?.let { _selectedMacroId.value = it.id }
        }
    }

    fun setService(service: GamepadService?) {
        _gamepadService.value = service
        if (service != null) {
            Log.i(TAG, "Service reference acquired by ViewModel")
            _connectionState.value = service.btService.connectionState.value
            _connectedDeviceName.value = service.btService.connectedDeviceName.value
            _errorMessage.value = service.btService.errorMessage.value
            _pairedDevices.value = service.btService.getBondedDevices()

            val savedMode = prefs.getString(KEY_MODE, GamepadService.Companion.Mode.GAMEPAD.name)
                ?.let { GamepadService.Companion.Mode.valueOf(it) }
                ?: GamepadService.Companion.Mode.GAMEPAD
            _mode.value = savedMode
            service.setMode(savedMode)

            service.updatePreferences(_sensitivity.value, _deadZone.value, _gyroEnabled.value)
            updateTargetDeviceName()
        }
    }

    private fun updateTargetDeviceName() {
        val name = when (_mode.value) {
            GamepadService.Companion.Mode.GAMEPAD -> "Wireless Controller"
            GamepadService.Companion.Mode.KEYBOARD -> "MacGamepad Keyboard"
        }
        Log.i(TAG, "Setting target Bluetooth device name to: $name")
        _gamepadService.value?.btService?.setTargetDeviceName(name)
    }

    /**
     * Update connection state flows (called by MainActivity ServiceConnection).
     */
    fun updateServiceStates(
        connState: BluetoothGamepadService.ConnectionState,
        devName: String?,
        error: String?
    ) {
        _connectionState.value = connState
        _connectedDeviceName.value = devName
        _errorMessage.value = error

        _gamepadService.value?.let { service ->
            _pairedDevices.value = service.btService.getBondedDevices()
        }

        // On connect: jump from Connection screen to Dashboard
        if (connState == BluetoothGamepadService.ConnectionState.CONNECTED && _currentScreen.value == Screen.CONNECTION) {
            _currentScreen.value = Screen.DASHBOARD
        }
        // On disconnect: return to Connection from Gamepad only (Dashboard/Profile/Settings stay visible)
        if (connState != BluetoothGamepadService.ConnectionState.CONNECTED && _currentScreen.value == Screen.GAMEPAD) {
            _currentScreen.value = Screen.DASHBOARD
        }
    }

    // ================================================================
    // Public API
    // ================================================================

    fun initializeBluetooth() {
        val service = _gamepadService.value
        if (service != null) {
            updateTargetDeviceName()
            service.startSession()
        } else {
            Log.w(TAG, "Cannot initialize BT, service is null")
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        _gamepadService.value?.btService?.connectToDevice(device)
    }

    fun disconnect() {
        _gamepadService.value?.stopSession()
        _currentScreen.value = Screen.CONNECTION
    }

    fun setMode(newMode: GamepadService.Companion.Mode) {
        _mode.value = newMode
        prefs.edit().putString(KEY_MODE, newMode.name).apply()
        _gamepadService.value?.let { service ->
            updateTargetDeviceName()
            service.setMode(newMode)
        }
    }

    fun setProfile(profileId: String) {
        val changed = _activeProfileId.value != profileId
        _activeProfileId.value = profileId
        prefs.edit().putString(KEY_PROFILE, profileId).apply()
        // Reload layouts for the new profile — critical for per-profile independence
        if (changed) {
            _componentOffsets.value = loadComponentOffsets(profileId)
            _componentScales.value = loadComponentScales(profileId)
        }
        _gamepadService.value?.let { service ->
            updateTargetDeviceName()
            val currState = service.btService.connectionState.value
            if (changed && currState == BluetoothGamepadService.ConnectionState.REGISTERED_WAITING) {
                Log.i(TAG, "Restarting discoverable session for profile: $profileId")
                service.stopSession()
                service.startSession()
            }
        }
    }

    fun navigateToGamepad() {
        if (_connectionState.value == BluetoothGamepadService.ConnectionState.CONNECTED) {
            _currentScreen.value = Screen.GAMEPAD
        }
    }

    fun navigateToDashboard() { _currentScreen.value = Screen.DASHBOARD }
    fun navigateToProfile()   { _currentScreen.value = Screen.PROFILE   }
    fun navigateToSettings()  { _currentScreen.value = Screen.SETTINGS  }

    fun setSensitivity(value: Float) {
        _sensitivity.value = value
        prefs.edit().putFloat("pref_sensitivity", value).apply()
        _gamepadService.value?.updatePreferences(value, _deadZone.value, _gyroEnabled.value)
    }

    fun setDeadZone(value: Float) {
        _deadZone.value = value
        prefs.edit().putFloat("pref_deadzone", value).apply()
        _gamepadService.value?.updatePreferences(_sensitivity.value, value, _gyroEnabled.value)
    }

    fun setHapticsIntensity(value: Float) {
        _hapticStrength.value = value
        prefs.edit().putFloat("pref_haptic", value).apply()
    }

    fun setHapticEnabled(value: Boolean) {
        _hapticEnabled.value = value
        prefs.edit().putBoolean("pref_haptic_enabled", value).apply()
    }

    fun setGyroEnabled(value: Boolean) {
        _gyroEnabled.value = value
        prefs.edit().putBoolean("pref_gyro", value).apply()
        _gamepadService.value?.updatePreferences(_sensitivity.value, _deadZone.value, value)
    }

    fun setAutoReconnect(value: Boolean) {
        _autoReconnect.value = value
        prefs.edit().putBoolean("pref_auto_reconnect", value).apply()
    }

    fun setVibrationEnabled(value: Boolean) {
        _vibrationEnabled.value = value
        prefs.edit().putBoolean("pref_vibration", value).apply()
    }

    // Layout Edit Mode state
    private val _isLayoutEditMode = MutableStateFlow(false)
    val isLayoutEditMode: StateFlow<Boolean> = _isLayoutEditMode.asStateFlow()

    // Per-profile component offsets and scales — loaded fresh each time profile changes
    private val _componentOffsets = MutableStateFlow<Map<String, Offset>>(loadComponentOffsets(_activeProfileId.value))
    val componentOffsets: StateFlow<Map<String, Offset>> = _componentOffsets.asStateFlow()

    private val _componentScales = MutableStateFlow<Map<String, Float>>(loadComponentScales(_activeProfileId.value))
    val componentScales: StateFlow<Map<String, Float>> = _componentScales.asStateFlow()

    /** Called from Dashboard 'Edit Layout' — navigates to Gamepad in edit mode */
    fun setLayoutEditMode(enabled: Boolean) {
        _isLayoutEditMode.value = enabled
        if (enabled) {
            _currentScreen.value = Screen.GAMEPAD
        } else {
            _currentScreen.value = Screen.DASHBOARD
        }
    }

    /** Called from inside the controller's ✓ button — exits edit mode, stays on Gamepad */
    fun exitEditModeToGamepad() {
        _isLayoutEditMode.value = false
        // Intentionally do NOT change _currentScreen
    }

    fun updateComponentOffset(componentId: String, offset: Offset) {
        val updated = _componentOffsets.value.toMutableMap()
        updated[componentId] = offset
        _componentOffsets.value = updated
        // Store with profile-namespaced key
        prefs.edit()
            .putFloat(offsetXKey(_activeProfileId.value, componentId), offset.x)
            .putFloat(offsetYKey(_activeProfileId.value, componentId), offset.y)
            .apply()
    }

    fun updateComponentScale(componentId: String, scale: Float) {
        val updated = _componentScales.value.toMutableMap()
        updated[componentId] = scale
        _componentScales.value = updated
        prefs.edit().putFloat(scaleKey(_activeProfileId.value, componentId), scale).apply()
    }

    fun resetLayout() {
        _componentOffsets.value = emptyMap()
        _componentScales.value = emptyMap()
        val pid = _activeProfileId.value
        val editor = prefs.edit()
        LAYOUT_COMPONENTS.forEach { id ->
            editor.remove(offsetXKey(pid, id))
            editor.remove(offsetYKey(pid, id))
            editor.remove(scaleKey(pid, id))
        }
        editor.apply()
    }

    /** Clears ALL layout data for ALL profiles — used in Settings > Clear Cache */
    fun clearAllLayouts() {
        _componentOffsets.value = emptyMap()
        _componentScales.value = emptyMap()
        val editor = prefs.edit()
        listOf("ps5", "xbox", "switch").forEach { pid ->
            LAYOUT_COMPONENTS.forEach { id ->
                editor.remove(offsetXKey(pid, id))
                editor.remove(offsetYKey(pid, id))
                editor.remove(scaleKey(pid, id))
            }
        }
        editor.apply()
    }

    /** Returns approximate storage used by this app's shared preferences (bytes) */
    fun estimateStorageBytes(): Int {
        return prefs.all.entries.sumOf { (key, value) ->
            key.length * 2 + when (value) {
                is Float   -> 4
                is Boolean -> 1
                is String  -> (value.length * 2)
                else       -> 8
            }
        }
    }

    private fun loadComponentOffsets(profileId: String): Map<String, Offset> {
        val map = mutableMapOf<String, Offset>()
        LAYOUT_COMPONENTS.forEach { id ->
            val x = prefs.getFloat(offsetXKey(profileId, id), 0f)
            val y = prefs.getFloat(offsetYKey(profileId, id), 0f)
            if (x != 0f || y != 0f) map[id] = Offset(x, y)
        }
        return map
    }

    private fun loadComponentScales(profileId: String): Map<String, Float> {
        val map = mutableMapOf<String, Float>()
        LAYOUT_COMPONENTS.forEach { id ->
            val scale = prefs.getFloat(scaleKey(profileId, id), 1.0f)
            if (scale != 1.0f) map[id] = scale
        }
        return map
    }

    // Throttle input reports to ~120 Hz (8ms min interval) to prevent BT queue buildup
    private var lastInputReportTime = 0L
    private var lastState: InputState? = null

    fun onInputChanged(state: InputState) {
        val now = System.currentTimeMillis()
        var isReleaseTransition = false
        val prev = lastState
        if (prev != null) {
            // Sticks returning to zero
            if (prev.leftStickX != 0f && state.leftStickX == 0f) isReleaseTransition = true
            if (prev.leftStickY != 0f && state.leftStickY == 0f) isReleaseTransition = true
            if (prev.rightStickX != 0f && state.rightStickX == 0f) isReleaseTransition = true
            if (prev.rightStickY != 0f && state.rightStickY == 0f) isReleaseTransition = true
            
            // Buttons transitioning from pressed to released
            if (prev.buttonA && !state.buttonA) isReleaseTransition = true
            if (prev.buttonB && !state.buttonB) isReleaseTransition = true
            if (prev.buttonX && !state.buttonX) isReleaseTransition = true
            if (prev.buttonY && !state.buttonY) isReleaseTransition = true
            if (prev.buttonL1 && !state.buttonL1) isReleaseTransition = true
            if (prev.buttonR1 && !state.buttonR1) isReleaseTransition = true
            if (prev.buttonL3 && !state.buttonL3) isReleaseTransition = true
            if (prev.buttonR3 && !state.buttonR3) isReleaseTransition = true
            if (prev.buttonStart && !state.buttonStart) isReleaseTransition = true
            if (prev.buttonSelect && !state.buttonSelect) isReleaseTransition = true
            if (prev.buttonHome && !state.buttonHome) isReleaseTransition = true
            
            // Triggers returning to zero
            if (prev.leftTrigger != 0f && state.leftTrigger == 0f) isReleaseTransition = true
            if (prev.rightTrigger != 0f && state.rightTrigger == 0f) isReleaseTransition = true
            
            // D-Pad returning to center
            if ((prev.dpadUp || prev.dpadDown || prev.dpadLeft || prev.dpadRight) &&
                (!state.dpadUp && !state.dpadDown && !state.dpadLeft && !state.dpadRight)) {
                isReleaseTransition = true
            }
        }
        lastState = state

        if (isReleaseTransition || now - lastInputReportTime >= 4L) {
            lastInputReportTime = now
            _gamepadService.value?.updateInputState(state)
        }
    }

    fun onMouseMoved(dx: Float, dy: Float) {
        _gamepadService.value?.sendMouseMovement(dx, dy)
    }

    fun onTouchpadClick() {
        _gamepadService.value?.triggerMouseClick()
    }

    // ================================================================
    // Macro button
    //
    // The macro is replayed entirely on the phone: the host keeps seeing the same
    // Switch/Xbox/PlayStation buttons, so nothing has to be re-paired or rebound
    // to gain the extra key.
    // ================================================================

    fun macroById(id: String?): Macro =
        _macros.value.firstOrNull { it.id == id } ?: _macros.value.firstOrNull() ?: MacroLibrary.zlACombo

    /** The macro the on-screen button fires. */
    fun selectedMacro(): Macro = macroById(_selectedMacroId.value)

    fun setSelectedMacro(macroId: String) {
        val m = _macros.value.firstOrNull { it.id == macroId } ?: return
        _selectedMacroId.value = m.id
        prefs.edit().putString(KEY_MACRO, m.id).apply()
    }

    /** Long-press on the macro button opens the picker; this walks it without the UI. */
    fun cycleMacro(): Macro {
        val all = _macros.value
        if (all.isEmpty()) return MacroLibrary.zlACombo
        val index = all.indexOfFirst { it.id == _selectedMacroId.value }
        val next = all[(index + 1).mod(all.size)]
        setSelectedMacro(next.id)
        Log.i(TAG, "Macro selection: ${next.id}")
        return next
    }

    fun triggerMacro() {
        val macro = selectedMacro()
        Log.i(TAG, "Macro button: ${macro.id}")
        _gamepadService.value?.triggerMacro(macro)
    }

    fun cancelMacro() {
        _gamepadService.value?.cancelMacro()
    }

    /** Fires an unsaved draft straight from the editor, so timing can be felt out. */
    fun previewMacro(macro: Macro) {
        _gamepadService.value?.triggerMacro(macro.sanitized())
    }

    fun saveMacro(macro: Macro) {
        _macros.value = macroStore.save(macro)
        Log.i(TAG, "Macro saved: ${macro.id}, ${macro.steps.size} steps")
    }

    fun deleteMacro(macroId: String) {
        val wasCustom = !MacroLibrary.isBuiltin(macroId)
        _macros.value = macroStore.delete(macroId)
        if (_macros.value.none { it.id == _selectedMacroId.value }) {
            _macros.value.firstOrNull()?.let { setSelectedMacro(it.id) }
        }
        // A deleted custom macro has no editor to return to; a built-in reset keeps its id.
        if (wasCustom && _editingMacroId.value == macroId) closeMacroEditor()
    }

    /** Restores a built-in to its factory steps (its override is simply dropped). */
    fun resetMacro(macroId: String) {
        _macros.value = macroStore.delete(macroId)
    }

    fun isMacroOverridden(macroId: String): Boolean = macroStore.isOverridden(macroId)

    fun duplicateMacro(macroId: String) {
        val src = _macros.value.firstOrNull { it.id == macroId } ?: return
        val copy = macroStore.newMacro(_macros.value, src)
        _macros.value = macroStore.save(copy)
        setSelectedMacro(copy.id)
    }

    /**
     * Opens the editor. A null [macroId] creates a new macro and selects it, so the
     * on-screen button is already pointing at whatever the user is about to build.
     */
    fun openMacroEditor(macroId: String?) {
        // Leaving the pad screen drops whatever was being held; without this a
        // stick pushed at the moment of the tap would stay pushed on the Mac.
        _gamepadService.value?.cancelMacro()
        _gamepadService.value?.updateInputState(InputState())
        val target = if (macroId == null) {
            val fresh = macroStore.newMacro(_macros.value)
            _macros.value = macroStore.save(fresh)
            setSelectedMacro(fresh.id)
            fresh.id
        } else macroId
        _editingMacroId.value = target
        macroEditorReturn = if (_currentScreen.value == Screen.MACRO_EDITOR) macroEditorReturn else _currentScreen.value
        _currentScreen.value = Screen.MACRO_EDITOR
    }

    fun closeMacroEditor() {
        _editingMacroId.value = null
        _currentScreen.value = macroEditorReturn
    }
}
