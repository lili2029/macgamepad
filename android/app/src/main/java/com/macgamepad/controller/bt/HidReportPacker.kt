package com.macgamepad.controller.bt

import com.macgamepad.controller.input.InputState
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Packs an [InputState] into a 13-byte HID input report matching our descriptor.
 *
 * Report layout (13 bytes, excluding report ID):
 *   Byte 0-1:   Left Stick X   (int16 LE)
 *   Byte 2-3:   Left Stick Y   (int16 LE)
 *   Byte 4-5:   Right Stick X  (int16 LE)
 *   Byte 6-7:   Right Stick Y  (int16 LE)
 *   Byte 8:     Hat (low nibble) | padding (high nibble)
 *   Byte 9-10:  Button bitmask (uint16 LE)
 *   Byte 11:    Left Trigger   (uint8)
 *   Byte 12:    Right Trigger  (uint8)
 *
 * This class is designed for zero-allocation hot-path usage:
 * it reuses a single ByteBuffer across calls.
 */
class HidReportPacker {

    // Pre-allocated buffer — reused every frame to avoid GC pressure
    private val buffer = ByteBuffer.allocate(13)
        .order(ByteOrder.LITTLE_ENDIAN)

    /**
     * Pack the current input state into a byte array suitable for
     * [android.bluetooth.BluetoothHidDevice.sendReport].
     *
     * @param state The current controller input state
     * @return A 13-byte array containing the packed HID report
     */
    fun pack(state: InputState): ByteArray {
        buffer.clear()

        // Sticks: convert -1.0..1.0 float to -32767..32767 int16
        buffer.putShort(floatToAxis16(state.leftStickX))
        buffer.putShort(floatToAxis16(state.leftStickY))
        buffer.putShort(floatToAxis16(state.rightStickX))
        buffer.putShort(floatToAxis16(state.rightStickY))

        // Hat switch (low nibble) + padding (high nibble)
        val hat = state.hatDirection() and 0x0F
        buffer.put(hat.toByte())

        // Buttons: 16-bit bitmask, little-endian
        buffer.putShort(state.buttonBitmask().toShort())

        // Triggers: convert 0.0..1.0 float to 0..255 uint8
        buffer.put(floatToTrigger8(state.leftTrigger))
        buffer.put(floatToTrigger8(state.rightTrigger))

        return buffer.array().copyOf()
    }

    companion object {
        /**
         * Convert a normalized float (-1.0 to 1.0) to a 16-bit signed axis value.
         * Clamps to [-32767, 32767] — we avoid -32768 because some HID parsers
         * treat it as a special value.
         */
        fun floatToAxis16(value: Float): Short {
            val clamped = value.coerceIn(-1f, 1f)
            return (clamped * 32767f).toInt().toShort()
        }

        /**
         * Convert a normalized float (0.0 to 1.0) to an 8-bit unsigned trigger value.
         */
        fun floatToTrigger8(value: Float): Byte {
            val clamped = value.coerceIn(0f, 1f)
            return (clamped * 255f).toInt().toByte()
        }
    }
}
