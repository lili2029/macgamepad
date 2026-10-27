package com.macgamepad.controller.bt

import com.macgamepad.controller.input.InputState

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
 * Stateless by design: every call writes into a fresh array at fixed offsets.
 *
 * This matters — [pack] is called from the UI thread (on every input change) *and*
 * from the 125 Hz heartbeat coroutine at the same time. An earlier version reused a
 * single shared ByteBuffer, so two interleaved `clear()` + writes pushed the buffer
 * position past its capacity and threw ByteBufferOverflowException after a few
 * minutes of play. Keeping this class free of mutable state makes that impossible.
 */
class HidReportPacker {

    fun pack(state: InputState): ByteArray {
        val out = ByteArray(REPORT_SIZE)

        // Sticks: convert -1.0..1.0 float to -32767..32767 int16
        putShortLE(out, 0, floatToAxis16(state.leftStickX))
        putShortLE(out, 2, floatToAxis16(state.leftStickY))
        putShortLE(out, 4, floatToAxis16(state.rightStickX))
        putShortLE(out, 6, floatToAxis16(state.rightStickY))

        // Hat switch (low nibble) + padding (high nibble)
        out[8] = (state.hatDirection() and 0x0F).toByte()

        // Buttons: 16-bit bitmask, little-endian
        putShortLE(out, 9, state.buttonBitmask().toShort())

        // Triggers: convert 0.0..1.0 float to 0..255 uint8
        out[11] = floatToTrigger8(state.leftTrigger)
        out[12] = floatToTrigger8(state.rightTrigger)

        return out
    }

    private fun putShortLE(dst: ByteArray, offset: Int, value: Short) {
        val v = value.toInt()
        dst[offset] = (v and 0xFF).toByte()
        dst[offset + 1] = ((v shr 8) and 0xFF).toByte()
    }

    companion object {
        const val REPORT_SIZE = 13

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