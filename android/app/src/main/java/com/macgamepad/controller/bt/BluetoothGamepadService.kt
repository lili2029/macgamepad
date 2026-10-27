package com.macgamepad.controller.bt

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.util.Log
import com.macgamepad.controller.input.InputState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

/**
 * Manages the Bluetooth HID Device profile connection.
 *
 * This service:
 * 1. Registers the app as a Bluetooth HID Device (gamepad) with our HID descriptor
 * 2. Manages pairing and connection with the host (macOS)
 * 3. Sends HID input reports at high frequency via [sendReport]
 *
 * Uses Android's [BluetoothHidDevice] API (available since API 28).
 */
class BluetoothGamepadService(private val context: Context) {

    companion object {
        private const val TAG = "BTGamepad"

        // Generic gamepad identifiers — no brand spoofing
        private const val DEVICE_NAME = "MacGamepad"
        private const val DEVICE_DESCRIPTION = "Virtual Gamepad Controller"
        private const val DEVICE_PROVIDER = "MacGamepad"

        // HID subclass for gamepad
        private const val HID_SUBCLASS_GAMEPAD: Byte = 0x02

        // Same preferences file the ViewModel uses: the host we last talked to is
        // remembered here so the next launch can dial it without any tapping.
        private const val PREFS_NAME = "macgamepad_prefs"
        private const val KEY_LAST_HOST_ADDRESS = "last_host_address"
        private const val KEY_LAST_HOST_NAME = "last_host_name"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** True once this install has completed a connection, i.e. there is a host to dial. */
    val hasSavedHost: Boolean get() = prefs.getString(KEY_LAST_HOST_ADDRESS, null) != null

    /** Friendly name of the remembered host, for the "connecting back to …" copy. */
    val lastHostName: String? get() = prefs.getString(KEY_LAST_HOST_NAME, null)

    // ================================================================
    // Connection state
    // ================================================================
    enum class ConnectionState {
        DISCONNECTED,
        REGISTERING,
        REGISTERED_WAITING,   // Registered, waiting for host to connect
        CONNECTING,
        CONNECTED,
        ERROR
    }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName: StateFlow<String?> = _connectedDeviceName.asStateFlow()

    // ================================================================
    // Bluetooth objects
    // ================================================================
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var hidDevice: BluetoothHidDevice? = null
    private var hostDevice: BluetoothDevice? = null
    private var originalBluetoothName: String? = null  // Save to restore on shutdown

    private val reportPacker = HidReportPacker()
    private val executor = Executors.newSingleThreadExecutor()
    private var lastSendErrorLogMs = 0L

    /** Used only to give a lying registerApp() a moment to be contradicted. */
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    // ================================================================
    // HID Device callback
    // ================================================================
    @SuppressLint("MissingPermission")
    private val hidCallback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            Log.d(TAG, "App status changed: registered=$registered, device=${pluggedDevice?.name}")
            if (registered) {
                _errorMessage.value = null
                _connectionState.value = ConnectionState.REGISTERED_WAITING
                Log.i(TAG, "✅ HID Device app registered. Waiting for host connection...")
            } else {
                _connectionState.value = ConnectionState.DISCONNECTED
                Log.w(TAG, "⚠️ HID Device app unregistered")
            }
        }

        override fun onConnectionStateChanged(device: BluetoothDevice?, state: Int) {
            val deviceName = try { device?.name ?: device?.address ?: "unknown" } catch (e: SecurityException) { "unknown" }
            Log.d(TAG, "Connection state changed: device=$deviceName, state=$state")

            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    hostDevice = device
                    _connectedDeviceName.value = deviceName
                    _connectionState.value = ConnectionState.CONNECTED
                    rememberHost(device, deviceName)
                    Log.i(TAG, "✅ Connected to host: $deviceName")
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    _connectionState.value = ConnectionState.CONNECTING
                    Log.i(TAG, "🔄 Connecting to: $deviceName")
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    hostDevice = null
                    _connectedDeviceName.value = null
                    _connectionState.value = ConnectionState.REGISTERED_WAITING
                    Log.i(TAG, "🔌 Disconnected from: $deviceName")
                }
                BluetoothProfile.STATE_DISCONNECTING -> {
                    Log.i(TAG, "🔌 Disconnecting from: $deviceName")
                }
            }
        }

        override fun onGetReport(device: BluetoothDevice?, type: Byte, id: Byte, bufferSize: Int) {
            Log.d(TAG, "GET_REPORT: type=$type, id=$id, bufferSize=$bufferSize")
            // Respond with a zeroed-out report (all sticks centered, no buttons pressed)
            hidDevice?.replyReport(device, type, id, ByteArray(13))
        }

        override fun onSetReport(device: BluetoothDevice?, type: Byte, id: Byte, data: ByteArray?) {
            Log.d(TAG, "SET_REPORT: type=$type, id=$id, data=${data?.size} bytes")
            // Future: handle force-feedback/rumble data from host
        }

        override fun onInterruptData(device: BluetoothDevice?, reportId: Byte, data: ByteArray?) {
            Log.d(TAG, "INTERRUPT_DATA: reportId=$reportId, data=${data?.size} bytes")
            // Future: handle output reports (LED control, etc.)
        }
    }

    // ================================================================
    // Profile service listener
    // ================================================================
    private val serviceListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = proxy as? BluetoothHidDevice
                Log.i(TAG, "HID Device profile proxy obtained")
                registerHidApp()
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HID_DEVICE) {
                hidDevice = null
                _connectionState.value = ConnectionState.DISCONNECTED
                Log.w(TAG, "HID Device profile disconnected")
            }
        }
    }

    // ================================================================
    // Public API
    // ================================================================

    /**
     * Initialize the Bluetooth HID Device service.
     * Call this once from your Activity/ViewModel after permissions are granted.
     */
    @SuppressLint("MissingPermission")
    fun initialize() {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter

        if (bluetoothAdapter == null) {
            _errorMessage.value = "此设备不支持蓝牙"
            _connectionState.value = ConnectionState.ERROR
            return
        }

        if (bluetoothAdapter?.isEnabled != true) {
            _errorMessage.value = "蓝牙未开启"
            _connectionState.value = ConnectionState.ERROR
            return
        }

        _connectionState.value = ConnectionState.REGISTERING
        Log.i(TAG, "Requesting HID Device profile proxy...")

        // Rename Bluetooth adapter to the target device profile name so it's recognized on the Mac
        try {
            originalBluetoothName = bluetoothAdapter?.name
            bluetoothAdapter?.name = targetDeviceName
            Log.i(TAG, "Renamed BT adapter from '$originalBluetoothName' to '$targetDeviceName'")
        } catch (e: Exception) {
            Log.w(TAG, "Could not rename BT adapter", e)
        }

        bluetoothAdapter?.getProfileProxy(
            context,
            serviceListener,
            BluetoothProfile.HID_DEVICE
        )
    }

    /**
     * Get a list of currently bonded (paired) devices that we can try to connect to.
     */
    @SuppressLint("MissingPermission")
    fun getBondedDevices(): List<BluetoothDevice> {
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    /**
     * Attempt to programmatically connect to a specific paired device.
     * Useful when the Mac has already been paired previously.
     */
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice): Boolean {
        val hid = hidDevice ?: return false
        Log.i(TAG, "Attempting to connect to: ${device.name ?: device.address}")
        return hid.connect(device)
    }

    /**
     * Dials the host that was connected last time, so relaunching the app doesn't
     * require walking through pairing again.
     *
     * Returns false when there is nothing remembered, the HID app is not registered
     * yet, or the host is no longer paired — the caller retries a few times, and the
     * Mac can always start the connection from its own side as well.
     */
    @SuppressLint("MissingPermission")
    fun connectToLastHost(): Boolean {
        val address = prefs.getString(KEY_LAST_HOST_ADDRESS, null) ?: return false
        val hid = hidDevice ?: return false
        val device = try {
            bluetoothAdapter?.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Auto-connect: bad remembered address $address")
            null
        } ?: return false

        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            Log.w(TAG, "Auto-connect skipped: $address is no longer paired")
            return false
        }

        return try {
            Log.i(TAG, "Auto-connecting to last host: ${lastHostName ?: address}")
            hid.connect(device)
        } catch (e: Exception) {
            Log.w(TAG, "Auto-connect failed", e)
            false
        }
    }

    /** Overwrites the remembered host; called on every successful connection. */
    private fun rememberHost(device: BluetoothDevice?, name: String) {
        val address = device?.address ?: return
        val previous = prefs.getString(KEY_LAST_HOST_ADDRESS, null)
        if (previous != address || prefs.getString(KEY_LAST_HOST_NAME, null) != name) {
            Log.i(TAG, "Remembering host for next launch: $name ($address)")
        }
        prefs.edit()
            .putString(KEY_LAST_HOST_ADDRESS, address)
            .putString(KEY_LAST_HOST_NAME, name)
            .apply()
    }

    // Active mode configuration
    @Volatile
    private var activeMode = GamepadService.Companion.Mode.GAMEPAD

    /**
     * Set the mode of operation before calling initialize().
     */
    fun setMode(mode: GamepadService.Companion.Mode) {
        activeMode = mode
    }

    /**
     * Send a raw HID report with a specific report ID.
     */
    @SuppressLint("MissingPermission")
    fun sendRawReport(reportId: Int, reportData: ByteArray): Boolean {
        val device = hostDevice ?: return false
        val hid = hidDevice ?: return false
        return try {
            hid.sendReport(device, reportId, reportData)
        } catch (e: Exception) {
            Log.w(TAG, "sendRawReport failed", e)
            false
        }
    }

    /**
     * Send the current input state as an HID report to the connected host (Gamepad mode).
     * This should be called at 125Hz (every 8ms) from a high-priority thread.
     *
     * Called from both the UI thread and the heartbeat coroutine. Any Bluetooth error is
     * swallowed here (returning false) so a dropped link can't crash the whole app.
     *
     * @param state The current controller input state
     * @return true if the report was sent successfully
     */
    @SuppressLint("MissingPermission")
    fun sendReport(state: InputState): Boolean {
        val device = hostDevice ?: return false
        val hid = hidDevice ?: return false

        return try {
            val reportData = reportPacker.pack(state)
            // GamepadHidDescriptor 的 Report ID 是 1（纯手柄描述符里只有这一个 collection）。
            hid.sendReport(device, GamepadHidDescriptor.REPORT_ID.toInt(), reportData)
        } catch (e: Exception) {
            // Rate-limit the log: this path can be hit 125 times a second.
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastSendErrorLogMs > 5000) {
                lastSendErrorLogMs = now
                Log.w(TAG, "sendReport failed", e)
            }
            false
        }
    }

    /**
     * Disconnect from the host and unregister the HID app.
     */
    @SuppressLint("MissingPermission")
    fun shutdown() {
        try {
            hostDevice?.let { device ->
                hidDevice?.disconnect(device)
            }
            hidDevice?.unregisterApp()
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HID_DEVICE, hidDevice)

            // Restore original Bluetooth name
            originalBluetoothName?.let { origName ->
                bluetoothAdapter?.name = origName
                Log.i(TAG, "Restored BT adapter name to '$origName'")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during shutdown", e)
        }
        hidDevice = null
        hostDevice = null
        originalBluetoothName = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    // Customizable Bluetooth display name to match target controllers
    @Volatile
    private var targetDeviceName = "Wireless Controller"

    fun setTargetDeviceName(name: String) {
        targetDeviceName = name
    }

    // ================================================================
    // Internal
    // ================================================================

    @SuppressLint("MissingPermission")
    private fun registerHidApp() {
        val hid = hidDevice ?: run {
            _errorMessage.value = "HID 设备代理不可用"
            _connectionState.value = ConnectionState.ERROR
            return
        }

        // Always use the unified descriptor (gamepad + keyboard + mouse in a single
        // SDP record). The Gamepad collection is placed FIRST in the descriptor so
        // macOS GameController.framework recognizes this as a controller device.
        // Subclass MUST be SUBCLASS2_GAMEPAD for macOS to set the Bluetooth CoD
        // to Peripheral > Gamepad — this is what makes it show as a real controller.
        val subclass: Byte = BluetoothHidDevice.SUBCLASS2_GAMEPAD
        // 纯手柄描述符：不含键盘/鼠标 collection，macOS 才不会把它当作需要
        // 「输入监控」权限的设备，IOHIDDeviceOpen 也才能被普通 App 成功调用。
        val descriptor = GamepadHidDescriptor.descriptor
        Log.i(
            TAG,
            "descriptor head = " + descriptor.take(8).joinToString(" ") {
                "0x%02X".format(it)
            } + "  len=${descriptor.size}  (expect 0x05 0x01 0x09 0x05 = GamePad first)"
        )

        // SDP settings: what the host sees when discovering this device
        val sdpSettings = BluetoothHidDeviceAppSdpSettings(
            targetDeviceName,
            DEVICE_DESCRIPTION,
            DEVICE_PROVIDER,
            subclass,
            descriptor
        )

        val result = hid.registerApp(
            sdpSettings,
            null,    // Let Bluetooth stack negotiate default stable QoS (fixes drops)
            null,    // Output QoS
            executor,
            hidCallback
        )

        if (result) {
            Log.i(TAG, "✅ registerApp() succeeded — waiting for callback...")
        } else {
            // Several ROMs (Flyme, MIUI, EMUI…) return false here while their stack
            // still registers the app a few milliseconds later — the callback is the
            // only trustworthy source, so don't shout "unsupported" until it stays
            // quiet for a beat.
            Log.w(TAG, "⚠️ registerApp() returned false — waiting for the callback to settle")
            mainHandler.postDelayed({
                if (_connectionState.value == ConnectionState.REGISTERING) {
                    _errorMessage.value =
                        "系统没有响应 HID 注册，这个型号可能关闭了蓝牙手柄模式"
                    _connectionState.value = ConnectionState.ERROR
                    Log.e(TAG, "❌ HID registration never completed")
                } else {
                    Log.i(TAG, "✅ Callback beat registerApp()'s false — HID is actually up")
                }
            }, 1500)
        }
    }
}
