package com.macgamepad.controller

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.macgamepad.controller.bt.BluetoothGamepadService
import com.macgamepad.controller.bt.GamepadService
import com.macgamepad.controller.input.MacroLibrary
import com.macgamepad.controller.ui.screens.ConnectionScreen
import com.macgamepad.controller.ui.screens.DashboardScreen
import com.macgamepad.controller.ui.screens.GamepadScreen
import com.macgamepad.controller.ui.screens.MacroEditorScreen
import com.macgamepad.controller.ui.screens.ProfileScreen
import com.macgamepad.controller.ui.screens.SettingsScreen
import com.macgamepad.controller.ui.theme.MacGamepadTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val requiredPermissions: Array<String>
        get() = mutableListOf<String>().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_ADVERTISE)
            } else {
                add(Manifest.permission.BLUETOOTH)
                add(Manifest.permission.BLUETOOTH_ADMIN)
            }
        }.toTypedArray()

    private var viewModelRef: GamepadViewModel? = null
    private var gamepadService: GamepadService? = null
    private var isBound = false
    private var serviceCollectorJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.i(TAG, "Connected to GamepadService")
            val binder = service as GamepadService.LocalBinder
            val srv = binder.getService()
            gamepadService = srv
            isBound = true
            
            viewModelRef?.let { vm ->
                vm.setService(srv)
                startStateCollection(srv, vm)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "Disconnected from GamepadService")
            stopStateCollection()
            gamepadService = null
            isBound = false
            viewModelRef?.setService(null)
        }
    }

    private fun startStateCollection(srv: GamepadService, vm: GamepadViewModel) {
        stopStateCollection()
        serviceCollectorJob = lifecycleScope.launch {
            launch {
                srv.btService.connectionState.collectLatest { state ->
                    vm.updateServiceStates(state, srv.btService.connectedDeviceName.value, srv.btService.errorMessage.value)
                }
            }
            launch {
                srv.btService.connectedDeviceName.collectLatest { name ->
                    vm.updateServiceStates(srv.btService.connectionState.value, name, srv.btService.errorMessage.value)
                }
            }
            launch {
                srv.btService.errorMessage.collectLatest { err ->
                    vm.updateServiceStates(srv.btService.connectionState.value, srv.btService.connectedDeviceName.value, err)
                }
            }
        }
    }

    private fun stopStateCollection() {
        serviceCollectorJob?.cancel()
        serviceCollectorJob = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Immersive mode configuration
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Start Foreground Service so it stays alive
        Intent(this, GamepadService::class.java).also { intent ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }

        setContent {
            MacGamepadTheme {
                val vm: GamepadViewModel = viewModel()
                viewModelRef = vm

                // Sync service if bound late
                LaunchedEffect(gamepadService) {
                    gamepadService?.let { vm.setService(it) }
                }

                val currentScreen by vm.currentScreen.collectAsState()
                val connectionState by vm.connectionState.collectAsState()
                val connectedDevice by vm.connectedDeviceName.collectAsState()
                val errorMessage by vm.errorMessage.collectAsState()
                val activeProfileId by vm.activeProfileId.collectAsState()
                val mode by vm.mode.collectAsState()

                // Immersive landscape vs Portrait control matching
                LaunchedEffect(currentScreen) {
                    requestedOrientation = when (currentScreen) {
                        GamepadViewModel.Screen.GAMEPAD -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }

                    val insetsController = WindowInsetsControllerCompat(window, window.decorView)
                    if (currentScreen == GamepadViewModel.Screen.GAMEPAD) {
                        insetsController.hide(WindowInsetsCompat.Type.systemBars())
                        insetsController.systemBarsBehavior =
                            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    } else {
                        insetsController.show(WindowInsetsCompat.Type.systemBars())
                    }
                }

                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith
                            fadeOut(animationSpec = tween(300))
                    },
                    label = "screenTransition"
                ) { screen ->
                    when (screen) {
                        GamepadViewModel.Screen.CONNECTION -> {
                            val pairedDevices by vm.pairedDevices.collectAsState()
                            val autoConnectState by vm.autoConnectState.collectAsState()
                            val lastHost by vm.lastHostName.collectAsState()
                            ConnectionScreen(
                                connectionState = connectionState,
                                connectedDeviceName = connectedDevice,
                                errorMessage = errorMessage,
                                pairedDevices = pairedDevices,
                                autoConnecting = autoConnectState == GamepadViewModel.AutoConnectState.CONNECTING,
                                autoConnectFailed = autoConnectState == GamepadViewModel.AutoConnectState.FAILED,
                                lastHostName = lastHost,
                                onDeviceConnectClick = { vm.connectToDevice(it) },
                                onConnectClick = { checkPermissionsAndConnect() },
                                onRetryAutoConnect = { vm.retryAutoConnect() },
                                onCancelAutoConnect = { vm.cancelAutoConnect() },
                                onDisconnectClick = { vm.disconnect() },
                                onStartGamepad = { vm.navigateToGamepad() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        GamepadViewModel.Screen.DASHBOARD -> {
                            val sensitivity by vm.sensitivity.collectAsState()
                            val deadZone by vm.deadZone.collectAsState()
                            val gyroEnabled by vm.gyroEnabled.collectAsState()
                            DashboardScreen(
                                connectionState = connectionState,
                                connectedDeviceName = connectedDevice,
                                mode = mode,
                                onModeChange = { vm.setMode(it) },
                                activeProfileId = activeProfileId,
                                onProfileChange = { vm.setProfile(it) },
                                sensitivity = sensitivity,
                                onSensitivityChange = { vm.setSensitivity(it) },
                                deadZone = deadZone,
                                onDeadZoneChange = { vm.setDeadZone(it) },
                                gyroEnabled = gyroEnabled,
                                onGyroChange = { vm.setGyroEnabled(it) },
                                onEditLayoutClick = { vm.setLayoutEditMode(true) },
                                onStartGamepad = { vm.navigateToGamepad() },
                                onDisconnect = { vm.disconnect() },
                                onNavigateToProfile = { vm.navigateToProfile() },
                                onNavigateToSettings = { vm.navigateToSettings() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        GamepadViewModel.Screen.GAMEPAD -> {
                            val deadZone by vm.deadZone.collectAsState()
                            val isEditMode by vm.isLayoutEditMode.collectAsState()
                            val offsets by vm.componentOffsets.collectAsState()
                            val scales by vm.componentScales.collectAsState()
                            val macroId by vm.selectedMacroId.collectAsState()
                            val macroList by vm.macros.collectAsState()
                            val recording by vm.isRecording.collectAsState()
                            val recordStats by vm.recordingStats.collectAsState()

                            GamepadScreen(
                                profileId = activeProfileId,
                                onBackToDashboard = {
                                    if (isEditMode) vm.setLayoutEditMode(false)
                                    else vm.navigateToDashboard()
                                },
                                deadZone = deadZone,
                                isEditMode = isEditMode,
                                componentOffsets = offsets,
                                componentScales = scales,
                                onOffsetChanged = { id, offset -> vm.updateComponentOffset(id, offset) },
                                onScaleChanged = { id, scale -> vm.updateComponentScale(id, scale) },
                                onResetLayout = { vm.resetLayout() },
                                onSaveLayout = { vm.exitEditModeToGamepad() },
                                onEnterEditMode = { vm.setLayoutEditMode(true) },
                                modifier = Modifier.fillMaxSize(),
                                onInputChanged = { vm.onInputChanged(it) },
                                onMouseMoved = { dx, dy -> vm.onMouseMoved(dx, dy) },
                                onTouchpadClick = { vm.onTouchpadClick() },
                                onMacroTriggered = { vm.triggerMacro() },
                                macroName = macroList.firstOrNull { it.id == macroId }?.name ?: "宏",
                                macros = macroList,
                                selectedMacroId = macroId,
                                onMacroSelected = { vm.setSelectedMacro(it) },
                                onEditMacro = { vm.openMacroEditor(it) },
                                isRecording = recording,
                                recordingMs = recordStats.durationMs,
                                recordingSteps = recordStats.steps,
                                onStartRecording = { vm.startRecording() },
                                onStopRecording = { vm.finishRecording() },
                                onDiscardRecording = { vm.discardRecording() }
                            )
                        }

                        GamepadViewModel.Screen.MACRO_EDITOR -> {
                            val macroList by vm.macros.collectAsState()
                            val editingId by vm.editingMacroId.collectAsState()
                            val current = macroList.firstOrNull { it.id == editingId } ?: macroList.firstOrNull()
                            if (current != null) {
                                MacroEditorScreen(
                                    macro = current,
                                    isBuiltin = MacroLibrary.isBuiltin(current.id),
                                    canPreview = connectionState ==
                                        BluetoothGamepadService.ConnectionState.CONNECTED &&
                                        mode == GamepadService.Companion.Mode.GAMEPAD,
                                    onSave = { vm.saveMacro(it) },
                                    onPreview = { vm.previewMacro(it) },
                                    onReset = { vm.resetMacro(current.id) },
                                    onDelete = { vm.deleteMacro(current.id) },
                                    onBack = { vm.closeMacroEditor() },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        GamepadViewModel.Screen.PROFILE -> {
                            ProfileScreen(
                                activeProfileId = activeProfileId,
                                onProfileChange = { vm.setProfile(it) },
                                connectionState = connectionState,
                                connectedDeviceName = connectedDevice,
                                onResetProfileLayout = { vm.resetLayout() },
                                onBack = { vm.navigateToDashboard() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        GamepadViewModel.Screen.SETTINGS -> {
                            val sensitivity by vm.sensitivity.collectAsState()
                            val deadZone by vm.deadZone.collectAsState()
                            val hapticEnabled by vm.hapticEnabled.collectAsState()
                            val hapticStrength by vm.hapticStrength.collectAsState()
                            val vibrationEnabled by vm.vibrationEnabled.collectAsState()
                            val autoReconnect by vm.autoReconnect.collectAsState()
                            val macroList by vm.macros.collectAsState()
                            val selectedMacroId by vm.selectedMacroId.collectAsState()
                            val storageBytes = remember(sensitivity, deadZone) { vm.estimateStorageBytes() }
                            SettingsScreen(
                                sensitivity = sensitivity,
                                onSensitivityChange = { vm.setSensitivity(it) },
                                deadZone = deadZone,
                                onDeadZoneChange = { vm.setDeadZone(it) },
                                hapticEnabled = hapticEnabled,
                                onHapticEnabledChange = { vm.setHapticEnabled(it) },
                                hapticStrength = hapticStrength,
                                onHapticStrengthChange = { vm.setHapticsIntensity(it) },
                                vibrationEnabled = vibrationEnabled,
                                onVibrationChange = { vm.setVibrationEnabled(it) },
                                autoReconnect = autoReconnect,
                                onAutoReconnectChange = { vm.setAutoReconnect(it) },
                                mode = mode,
                                onModeChange = { vm.setMode(it) },
                                macroCount = macroList.size,
                                onOpenMacros = { vm.openMacroEditor(selectedMacroId) },
                                estimatedStorageBytes = storageBytes,
                                onClearAllLayouts = { vm.clearAllLayouts() },
                                onBack = { vm.navigateToDashboard() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Bind to service
        Intent(this, GamepadService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
        // A link that dropped while we were in the background gets re-dialled here.
        viewModelRef?.onForeground()
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        Log.d(TAG, "Permission request results: $results")
        val bluetoothGranted = requiredPermissions.all { results[it] == true }
        if (bluetoothGranted) {
            Log.i(TAG, "Required Bluetooth permissions granted from dialog")
            onPermissionsGranted()
        } else {
            val denied = requiredPermissions.filter { results[it] != true }
            Log.e(TAG, "Required Bluetooth permissions denied: $denied")
        }
    }

    private fun checkPermissionsAndConnect() {
        val missingRequired = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        val allToRequest = mutableListOf<String>().apply {
            addAll(missingRequired)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }

        Log.d(TAG, "Checking permissions. Missing required: $missingRequired, All to request: $allToRequest")

        if (missingRequired.isEmpty()) {
            Log.i(TAG, "Required Bluetooth permissions already granted")
            onPermissionsGranted()
        } else {
            Log.i(TAG, "Requesting permissions: $allToRequest")
            permissionLauncher.launch(allToRequest.toTypedArray())
        }
    }

    /**
     * Request Bluetooth discoverability so the Mac can find this phone during scanning.
     * Duration: 300 seconds (5 minutes). Android shows a system dialog to the user.
     */
    private val discoverableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.i(TAG, "Discoverability result code: ${result.resultCode}")
        // resultCode == RESULT_CANCELED means user denied, > 0 means granted for that many seconds
        // Initialize BT regardless — if already paired, it'll connect without discoverability
        viewModelRef?.initializeBluetooth()
    }

    private fun onPermissionsGranted() {
        Log.i(TAG, "onPermissionsGranted() called, requesting discoverability...")
        // Request discoverability so the phone appears in the Mac's Bluetooth scan
        val discoverableIntent = Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(android.bluetooth.BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        }
        discoverableLauncher.launch(discoverableIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStateCollection()
    }
}
