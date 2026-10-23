package com.macgamepad.controller.bt

import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.macgamepad.controller.MainActivity
import com.macgamepad.controller.R
import com.macgamepad.controller.input.InputState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.util.concurrent.Executors

/**
 * Foreground Service that manages the Bluetooth connection and input transmission.
 * Implements SensorEventListener to provide real-time gyroscope mouse/stick steering aiming.
 */
class GamepadService : Service(), SensorEventListener {

    companion object {
        private const val TAG = "GamepadService"
        private const val NOTIFICATION_ID = 901
        private const val CHANNEL_ID = "macgamepad_service_channel"
        private const val REPORT_INTERVAL_MS = 8L // 125Hz

        enum class Mode {
            GAMEPAD,
            KEYBOARD
        }
    }

    private val binder = LocalBinder()

    private val _mode = MutableStateFlow(Mode.GAMEPAD)
    val mode: StateFlow<Mode> = _mode.asStateFlow()

    // Configurable Preferences
    @Volatile var sensitivity: Float = 1.0f
    @Volatile var deadZone: Float = 0.15f
    @Volatile var gyroEnabled: Boolean = false

    // Gyroscope tracking accumulator variables
    private var sensorManager: SensorManager? = null
    private var gyroSensor: Sensor? = null
    @Volatile private var gyroAccumX = 0f
    @Volatile private var gyroAccumY = 0f

    // BT Gamepad Service helper
    lateinit var btService: BluetoothGamepadService
    private var serviceJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Volatile state accessed by the high-speed sending thread
    @Volatile
    private var currentInputState = InputState()

    @Volatile
    private var lastSentState: InputState? = null
    private var heartbeatCount = 0

    inner class LocalBinder : Binder() {
        fun getService(): GamepadService = this@GamepadService
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Creating GamepadService...")
        createNotificationChannel()
        btService = BluetoothGamepadService(applicationContext)

        // Initialize Gyroscope sensor APIs
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        gyroSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Starting GamepadService as Foreground Service...")
        startForeground(NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    /**
     * Start the Bluetooth HID connection session.
     */
    fun startSession() {
        btService.setMode(_mode.value)
        btService.initialize()
        startTransmissionLoop()
    }

    /**
     * Terminate the session and release Bluetooth bindings.
     */
    fun stopSession() {
        stopTransmissionLoop()
        btService.shutdown()
    }

    /**
     * Set the emulation mode (Gamepad or Keyboard).
     * Since we always register the unified descriptor (keyboard + mouse + gamepad),
     * mode switching only needs to restart the transmission loop — no BT re-registration.
     */
    fun setMode(newMode: Mode) {
        if (_mode.value == newMode) return
        Log.i(TAG, "Switching mode to: $newMode")
        stopTransmissionLoop()
        _mode.value = newMode
        btService.setMode(newMode)
        startTransmissionLoop()
    }

    /**
     * Update configuration preferences dynamically from ViewModel.
     */
    fun updatePreferences(sens: Float, dz: Float, gyro: Boolean) {
        Log.i(TAG, "Updating preferences: sens=$sens, dz=$dz, gyro=$gyro")
        sensitivity = sens
        deadZone = dz
        val gyroChanged = gyroEnabled != gyro
        gyroEnabled = gyro

        if (gyroChanged) {
            if (gyroEnabled) {
                registerGyroListener()
            } else {
                unregisterGyroListener()
            }
        }
    }

    private fun registerGyroListener() {
        val manager = sensorManager ?: return
        val sensor = gyroSensor ?: return
        Log.i(TAG, "Registering gyroscope sensor listener")
        manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    private fun unregisterGyroListener() {
        val manager = sensorManager ?: return
        Log.i(TAG, "Unregistering gyroscope sensor listener")
        manager.unregisterListener(this)
        gyroAccumX = 0f
        gyroAccumY = 0f
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !gyroEnabled) return
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            // event.values[0] = pitch (rotation around X-axis) -> Y movement
            // event.values[1] = yaw (rotation around Y-axis) -> X movement
            val pitch = event.values[0]
            val yaw = event.values[1]

            // Accumulate relative delta, scaling by sensitivity
            gyroAccumX += -yaw * 160f * sensitivity
            gyroAccumY += -pitch * 160f * sensitivity
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * Update the target input state.
     */
    fun updateInputState(state: InputState) {
        currentInputState = state
        if (_mode.value == Companion.Mode.GAMEPAD && btService.connectionState.value == BluetoothGamepadService.ConnectionState.CONNECTED) {
            var finalState = state
            if (gyroEnabled) {
                val rsX = (state.rightStickX + (gyroAccumX / 60f)).coerceIn(-1f, 1f)
                val rsY = (state.rightStickY + (gyroAccumY / 60f)).coerceIn(-1f, 1f)
                gyroAccumX = 0f
                gyroAccumY = 0f
                finalState = state.copy(rightStickX = rsX, rightStickY = rsY)
            }
            btService.sendReport(finalState)
        }
    }

    private var lastSentKeys: List<Byte> = emptyList()

    private fun getKeyboardKeys(state: InputState): List<Byte> {
        val keys = mutableListOf<Byte>()
        // Map Left stick to WASD digital zones
        if (state.leftStickY < -0.4f) keys.add(KeyboardHidDescriptor.Keycodes.W)
        if (state.leftStickY > 0.4f) keys.add(KeyboardHidDescriptor.Keycodes.S)
        if (state.leftStickX < -0.4f) keys.add(KeyboardHidDescriptor.Keycodes.A)
        if (state.leftStickX > 0.4f) keys.add(KeyboardHidDescriptor.Keycodes.D)

        // If gyroscope is disabled, map Right Stick to I/K/J/L keys for native camera/aiming look
        if (!gyroEnabled) {
            if (state.rightStickY < -0.4f) keys.add(KeyboardHidDescriptor.Keycodes.I)
            if (state.rightStickY > 0.4f) keys.add(KeyboardHidDescriptor.Keycodes.K)
            if (state.rightStickX < -0.4f) keys.add(KeyboardHidDescriptor.Keycodes.J)
            if (state.rightStickX > 0.4f) keys.add(KeyboardHidDescriptor.Keycodes.L)
        }

        // Map D-pad to Arrow Keys
        if (state.dpadUp) keys.add(KeyboardHidDescriptor.Keycodes.UP)
        if (state.dpadDown) keys.add(KeyboardHidDescriptor.Keycodes.DOWN)
        if (state.dpadLeft) keys.add(KeyboardHidDescriptor.Keycodes.LEFT)
        if (state.dpadRight) keys.add(KeyboardHidDescriptor.Keycodes.RIGHT)

        // Map Face Buttons to Ryujinx/Astris default keys
        if (state.buttonA) keys.add(KeyboardHidDescriptor.Keycodes.Z)       // Switch A -> Keyboard Z
        if (state.buttonB) keys.add(KeyboardHidDescriptor.Keycodes.X)       // Switch B -> Keyboard X
        if (state.buttonX) keys.add(KeyboardHidDescriptor.Keycodes.C)       // Switch X -> Keyboard C
        if (state.buttonY) keys.add(KeyboardHidDescriptor.Keycodes.V)       // Switch Y -> Keyboard V

        // Map shoulder bumpers
        if (state.buttonL1) keys.add(KeyboardHidDescriptor.Keycodes.E)      // L -> Keyboard E
        if (state.buttonR1) keys.add(KeyboardHidDescriptor.Keycodes.U)      // R -> Keyboard U

        // Map analog triggers (digital mapping threshold in Keyboard Mode)
        if (state.leftTrigger > 0.4f) keys.add(KeyboardHidDescriptor.Keycodes.Q)   // ZL -> Keyboard Q
        if (state.rightTrigger > 0.4f) keys.add(KeyboardHidDescriptor.Keycodes.O)  // ZR -> Keyboard O

        // Map L3/R3 clicks
        if (state.buttonL3) keys.add(KeyboardHidDescriptor.Keycodes.F)      // L3 -> Keyboard F
        if (state.buttonR3) keys.add(KeyboardHidDescriptor.Keycodes.H)      // R3 -> Keyboard H

        // Map System buttons (+ / -)
        if (state.buttonStart) keys.add(KeyboardHidDescriptor.Keycodes.EQUALS)  // Start (+) -> Keyboard =
        if (state.buttonSelect) keys.add(KeyboardHidDescriptor.Keycodes.MINUS)  // Select (-) -> Keyboard -
        
        return keys
    }

    // ================================================================
    // High-speed transmission loop (125Hz)
    // ================================================================
    private fun startTransmissionLoop() {
        if (serviceJob?.isActive == true) return
        if (gyroEnabled) {
            registerGyroListener()
        }

        serviceJob = serviceScope.launch {
            Log.i(TAG, "Starting high-frequency report loop...")
            val keyboardBuffer = ByteBuffer.allocate(8)
            val mouseBuffer = ByteBuffer.allocate(4)

            // Tracks if mouse was previously moving to send a final stop report
            var mouseWasMoving = false

            while (isActive) {
                if (btService.connectionState.value == BluetoothGamepadService.ConnectionState.CONNECTED) {
                    val state = currentInputState
                    val last = lastSentState

                    when (_mode.value) {
                        Mode.GAMEPAD -> {
                            // Active inputs are sent instantly in updateInputState() for zero latency.
                            // We only send a heartbeat report here every 25 iterations (~200ms) to maintain the Bluetooth link.
                            if (heartbeatCount >= 25) {
                                btService.sendReport(state)
                                heartbeatCount = 0
                            } else {
                                heartbeatCount++
                            }
                        }
                        Mode.KEYBOARD -> {
                            val keys = getKeyboardKeys(state)
                            val keysChanged = keys != lastSentKeys

                            if (keysChanged || heartbeatCount >= 50) {
                                sendKeyboardReport(keys, keyboardBuffer)
                                lastSentKeys = keys
                                heartbeatCount = 0
                            } else {
                                heartbeatCount++
                            }

                            // Compute mouse movements (only map right stick to mouse if gyro is enabled, otherwise it maps to keyboard IKJL keys)
                            val stickX = if (gyroEnabled) state.rightStickX * 32f * sensitivity else 0f
                            val stickY = if (gyroEnabled) state.rightStickY * 32f * sensitivity else 0f
                            
                            val dx = (stickX + gyroAccumX).toInt().coerceIn(-127, 127)
                            val dy = (stickY + gyroAccumY).toInt().coerceIn(-127, 127)
                            
                            // Reset gyro deltas after consumption
                            gyroAccumX = 0f
                            gyroAccumY = 0f

                            val isMoving = dx != 0 || dy != 0
                            if (isMoving) {
                                sendMouseReportDirect(dx.toByte(), dy.toByte(), 0x00.toByte(), mouseBuffer)
                                mouseWasMoving = true
                            } else if (mouseWasMoving) {
                                // Send one final zeroed mouse report to stop movement drift
                                sendMouseReportDirect(0, 0, 0x00.toByte(), mouseBuffer)
                                mouseWasMoving = false
                            }
                        }
                    }
                }
                delay(REPORT_INTERVAL_MS)
            }
        }
    }

    private fun stopTransmissionLoop() {
        serviceJob?.cancel()
        serviceJob = null
        unregisterGyroListener()
    }

    // ================================================================
    // Keyboard and Mouse Mapping & Report Sending
    // ================================================================

    @SuppressLint("MissingPermission")
    private fun sendKeyboardReport(keys: List<Byte>, buffer: ByteBuffer) {
        buffer.clear()
        // Byte 0: Modifier keys
        buffer.put(0x00.toByte())

        // Byte 1: Reserved
        buffer.put(0x00.toByte())

        // Bytes 2-7: Up to 6 keycodes
        for (i in 0 until 6) {
            if (i < keys.size) {
                buffer.put(keys[i])
            } else {
                buffer.put(KeyboardHidDescriptor.Keycodes.NONE)
            }
        }

        btService.sendRawReport(KeyboardHidDescriptor.KEYBOARD_REPORT_ID.toInt(), buffer.array())
    }

    @SuppressLint("MissingPermission")
    fun sendMouseReportDirect(dx: Byte, dy: Byte, buttons: Byte, buffer: ByteBuffer) {
        buffer.clear()
        // Byte 0: Buttons
        buffer.put(buttons)

        // Byte 1: Relative X (-127..127)
        buffer.put(dx)

        // Byte 2: Relative Y (-127..127)
        buffer.put(dy)

        // Byte 3: Wheel
        buffer.put(0x00.toByte())

        btService.sendRawReport(KeyboardHidDescriptor.MOUSE_REPORT_ID.toInt(), buffer.array())
    }

    @SuppressLint("MissingPermission")
    fun sendMouseMovement(dx: Float, dy: Float) {
        val mouseBuffer = ByteBuffer.allocate(4)
        val rx = (dx * sensitivity).toInt().coerceIn(-127, 127).toByte()
        val ry = (dy * sensitivity).toInt().coerceIn(-127, 127).toByte()
        sendMouseReportDirect(rx, ry, 0x00.toByte(), mouseBuffer)
    }

    fun triggerMouseClick() {
        serviceScope.launch {
            val mouseBuffer = ByteBuffer.allocate(4)
            sendMouseReportDirect(0, 0, 0x01.toByte(), mouseBuffer)
            delay(50)
            sendMouseReportDirect(0, 0, 0x00.toByte(), mouseBuffer)
        }
    }

    // ================================================================
    // Service Notification
    // ================================================================

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MacGamepad Connected")
            .setContentText("Emulating native controller via Bluetooth HID")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth) // Use system icon for compatibility
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MacGamepad Connection Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Bluetooth controller connection alive in background"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTransmissionLoop()
        btService.shutdown()
        serviceScope.cancel()
    }
}
