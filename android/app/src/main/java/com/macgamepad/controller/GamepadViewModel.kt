package com.macgamepad.controller

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import android.bluetooth.BluetoothDevice
import androidx.core.content.ContextCompat
import com.macgamepad.controller.bt.BluetoothGamepadService
import com.macgamepad.controller.bt.GamepadService
import com.macgamepad.controller.input.InputState
import com.macgamepad.controller.input.Macro
import com.macgamepad.controller.input.MacroLibrary
import com.macgamepad.controller.input.MacroRecorder
import com.macgamepad.controller.input.MacroStore
import com.macgamepad.controller.input.sanitized
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

        // How long the automatic reconnect is allowed to keep trying before the
        // manual screen takes over. Long enough for a Mac waking up, short enough
        // that a phone away from its host isn't stuck on a spinner.
        private const val AUTO_CONNECT_TIMEOUT_MS = 9_000L
        private const val AUTO_CONNECT_FIRST_DELAY_MS = 350L
        private const val AUTO_CONNECT_RETRY_DELAY_MS = 2_400L
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

    // ================================================================
    // Auto-connect
    //
    // Relaunching the app shouldn't mean re-pairing every time: the host that was
    // connected last is remembered by the BT service, and this side dials it as
    // soon as the HID app is registered. Only when that fails does the player get
    // the manual connection screen.
    // ================================================================
    enum class AutoConnectState {
        /** Nothing in flight — the screen shows whatever the BT state is. */
        IDLE,
        /** Dialling the remembered host; the connection screen stays in a waiting pose. */
        CONNECTING,
        /** Gave up after [AUTO_CONNECT_TIMEOUT_MS]; the manual options are offered. */
        FAILED
    }

    private val _autoConnectState = MutableStateFlow(AutoConnectState.IDLE)
    val autoConnectState: StateFlow<AutoConnectState> = _autoConnectState.asStateFlow()

    /** Name of the host we would reconnect to, for the UI copy. */
    private val _lastHostName = MutableStateFlow<String?>(null)
    val lastHostName: StateFlow<String?> = _lastHostName.asStateFlow()

    private var autoConnectJob: Job? = null
    private var launchAutoConnectDone = false
    /** True once this process has seen a live link, used by [onForeground]. */
    private var wasConnected = false
    /** Set when the player disconnects on purpose; automatic attempts must not undo that. */
    private var optedOut = false

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

    // Off by default? No: reconnecting to the host you used five minutes ago is the
    // common case, so the toggle now defaults to on and can be switched off here.
    private val _autoReconnect = MutableStateFlow(prefs.getBoolean("pref_auto_reconnect", true))
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
        recordingJob?.cancel()
        recordingJob = null
        _isRecording.value = false
        _recordingStats.value = MacroRecorder.Stats()
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
            observeRecording(service)

            _lastHostName.value = service.btService.lastHostName
            maybeAutoConnectOnLaunch(service)
        }
    }

    /**
     * Runs at most once per process launch: bring the HID app up and dial the host
     * we were connected to last time, skipping both the "初始化手柄" tap and the
     * discoverability dialog (a paired host doesn't need to be discoverable).
     */
    private fun maybeAutoConnectOnLaunch(service: GamepadService) {
        if (launchAutoConnectDone) return
        launchAutoConnectDone = true

        if (!_autoReconnect.value) {
            Log.i(TAG, "Auto-connect on launch is off")
            return
        }
        if (!service.btService.hasSavedHost) {
            Log.i(TAG, "Auto-connect: no remembered host yet")
            return
        }
        if (!hasBluetoothPermissions()) {
            Log.i(TAG, "Auto-connect: bluetooth permissions not granted yet")
            return
        }
        // The service may already be connected (Activity recreated while the
        // foreground service kept the link): nothing to do then.
        if (_connectionState.value == BluetoothGamepadService.ConnectionState.CONNECTED) return
        startAutoConnect()
    }

    /**
     * Registers the HID app and keeps trying the remembered host for a few seconds.
     * Any manual action (picking a device, disconnecting) cancels it, so the two
     * paths never fight over the same link.
     */
    fun startAutoConnect() {
        val service = _gamepadService.value ?: return
        if (!service.btService.hasSavedHost) return

        autoConnectJob?.cancel()
        _autoConnectState.value = AutoConnectState.CONNECTING
        _lastHostName.value = service.btService.lastHostName

        // Registers the HID app (and keeps waiting for the Mac) without asking for
        // discoverability — the host already knows us. A retry finds the app already
        // registered, and re-registering would tear down a live link.
        val btState = service.btService.connectionState.value
        if (btState == BluetoothGamepadService.ConnectionState.DISCONNECTED ||
            btState == BluetoothGamepadService.ConnectionState.ERROR
        ) {
            service.startSession()
        }

        autoConnectJob = viewModelScope.launch {
            val deadline = System.currentTimeMillis() + AUTO_CONNECT_TIMEOUT_MS
            var attempts = 0
            while (isActive && System.currentTimeMillis() < deadline) {
                delay(if (attempts == 0) AUTO_CONNECT_FIRST_DELAY_MS else AUTO_CONNECT_RETRY_DELAY_MS)
                attempts++
                when (_connectionState.value) {
                    BluetoothGamepadService.ConnectionState.CONNECTED,
                    BluetoothGamepadService.ConnectionState.CONNECTING -> return@launch
                    else -> service.btService.connectToLastHost()
                }
            }
            if (_connectionState.value != BluetoothGamepadService.ConnectionState.CONNECTED) {
                Log.i(TAG, "Auto-connect gave up after $attempts attempts")
                _autoConnectState.value = AutoConnectState.FAILED
            }
        }
    }

    /** Stops the automatic attempt and hands the connection screen back to the player. */
    fun cancelAutoConnect() {
        autoConnectJob?.cancel()
        autoConnectJob = null
        if (_autoConnectState.value == AutoConnectState.CONNECTING) {
            Log.i(TAG, "Auto-connect cancelled by the player")
            _autoConnectState.value = AutoConnectState.IDLE
        }
    }

    /** "再试一次" on the failed banner. */
    fun retryAutoConnect() {
        optedOut = false
        startAutoConnect()
    }

    /**
     * Called when the Activity comes back to the foreground. A link that died while
     * the app was in the background (Mac asleep, out of range) is re-dialled quietly
     * instead of making the player notice and tap again. A deliberate disconnect is
     * respected, and the very first launch is handled by [maybeAutoConnectOnLaunch],
     * which doesn't need this.
     */
    fun onForeground() {
        if (!wasConnected || optedOut) return
        if (!_autoReconnect.value) return
        val service = _gamepadService.value ?: return
        if (!service.btService.hasSavedHost) return
        if (_connectionState.value == BluetoothGamepadService.ConnectionState.CONNECTED) return
        if (_autoConnectState.value == AutoConnectState.CONNECTING) return
        Log.i(TAG, "Link was down on resume, re-dialling the remembered host")
        startAutoConnect()
    }

    private fun hasBluetoothPermissions(): Boolean {
        val app = getApplication<Application>()
        val required = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_SCAN,
                android.Manifest.permission.BLUETOOTH_ADVERTISE
            )
        } else {
            listOf(
                android.Manifest.permission.BLUETOOTH,
                android.Manifest.permission.BLUETOOTH_ADMIN
            )
        }
        return required.all { ContextCompat.checkSelfPermission(app, it) == PackageManager.PERMISSION_GRANTED }
    }

    /**
     * Mirrors the service's recording stats into the UI state. The 12 s budget
     * hitting its cap is handled here rather than in the service so that saving a
     * take has exactly one code path.
     */
    private fun observeRecording(service: GamepadService) {
        recordingJob?.cancel()
        recordingJob = viewModelScope.launch {
            service.recordingStats.collect { stats ->
                _recordingStats.value = stats
                _isRecording.value = stats.recording
                if (stats.recording && stats.saturated) {
                    Log.i(TAG, "Recording hit its limit, saving what we have")
                    finishRecording()
                }
            }
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

        if (connState == BluetoothGamepadService.ConnectionState.CONNECTED) {
            // The link is up: whatever automatic attempt was in flight is done, and
            // the name to show next time is the one we just talked to.
            autoConnectJob?.cancel()
            autoConnectJob = null
            _autoConnectState.value = AutoConnectState.IDLE
            if (devName != null) _lastHostName.value = devName
            wasConnected = true
            optedOut = false
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
            // The manual path owns the session from here on.
            cancelAutoConnect()
            optedOut = false
            updateTargetDeviceName()
            service.startSession()
        } else {
            Log.w(TAG, "Cannot initialize BT, service is null")
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        // An explicit pick wins over whatever the automatic attempt was doing.
        cancelAutoConnect()
        optedOut = false
        _gamepadService.value?.btService?.connectToDevice(device)
    }

    fun disconnect() {
        cancelAutoConnect()
        _autoConnectState.value = AutoConnectState.IDLE
        // Deliberate: don't silently reconnect the moment the app is resumed.
        optedOut = true
        wasConnected = false
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

    fun navigateToDashboard() {
        // Walking away mid-take saves it rather than throwing it away: the player
        // can review it from the macro list, and nothing is silently lost.
        if (_isRecording.value) finishRecording()
        _currentScreen.value = Screen.DASHBOARD
    }
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

    // ================================================================
    // Recording
    //
    // The service samples the outgoing reports; this side only turns the result
    // into a saved macro. Recording never sends anything extra to the host, so the
    // game behaves exactly as if the player were just playing.
    // ================================================================

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingStats = MutableStateFlow(MacroRecorder.Stats())
    val recordingStats: StateFlow<MacroRecorder.Stats> = _recordingStats.asStateFlow()

    private var recordingJob: Job? = null

    /** Starts capturing the on-screen controls. The macro button turns into Stop. */
    fun startRecording() {
        val service = _gamepadService.value
        if (service == null) {
            Log.i(TAG, "Recording ignored: no service")
            return
        }
        if (!service.startRecording()) {
            Log.i(TAG, "Recording ignored: connect a host in gamepad mode first")
            return
        }
        _recordingStats.value = MacroRecorder.Stats(recording = true)
        _isRecording.value = true
    }

    /**
     * Saves the take and selects it, so the macro button fires it right away.
     * Safe to call twice: the service only has a recording while one is running.
     */
    fun finishRecording() {
        val service = _gamepadService.value ?: return
        if (!_isRecording.value) return
        val steps = service.stopRecording()
        _isRecording.value = false
        _recordingStats.value = MacroRecorder.Stats()
        if (steps.isEmpty()) {
            Log.i(TAG, "Recording dropped: nothing was pressed")
            return
        }
        val fresh = macroStore.newMacro(_macros.value)
        val recorded = fresh.copy(name = nextRecordingName(), steps = steps, cooldownTicks = 4).sanitized()
        _macros.value = macroStore.save(recorded)
        setSelectedMacro(recorded.id)
        Log.i(TAG, "Recording saved: ${recorded.id} \"${recorded.name}\", ${recorded.steps.size} steps, ${recorded.durationMs} ms")
    }

    /** Throws the take away. */
    fun discardRecording() {
        _gamepadService.value?.cancelRecording()
        _isRecording.value = false
        _recordingStats.value = MacroRecorder.Stats()
        Log.i(TAG, "Recording discarded")
    }

    private fun nextRecordingName(): String {
        val used = _macros.value.map { it.name }.toSet()
        var n = 1
        while ("录制 $n" in used) n++
        return "录制 $n"
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
        // A take still running belongs to the pad screen; the editor can't show it.
        if (_isRecording.value) discardRecording()
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
