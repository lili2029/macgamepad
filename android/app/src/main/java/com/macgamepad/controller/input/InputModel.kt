package com.macgamepad.controller.input

/**
 * Universal input state — the canonical representation of all controller inputs.
 *
 * Every controller profile (PS5, Xbox, Switch, generic) maps its touch events
 * into this same model. The BT HID service reads from this to build HID reports.
 *
 * All values are normalized:
 * - Sticks: Float -1.0 to 1.0 (0.0 = centered)
 * - Triggers: Float 0.0 to 1.0 (0.0 = released, 1.0 = fully pressed)
 * - Buttons: Boolean
 * - D-pad: Enum direction
 */
data class InputState(
    // Left analog stick
    val leftStickX: Float = 0f,
    val leftStickY: Float = 0f,

    // Right analog stick
    val rightStickX: Float = 0f,
    val rightStickY: Float = 0f,

    // D-pad
    val dpadUp: Boolean = false,
    val dpadDown: Boolean = false,
    val dpadLeft: Boolean = false,
    val dpadRight: Boolean = false,

    // Face buttons
    val buttonA: Boolean = false,
    val buttonB: Boolean = false,
    val buttonX: Boolean = false,
    val buttonY: Boolean = false,

    // Shoulder buttons
    val buttonL1: Boolean = false,
    val buttonR1: Boolean = false,

    // Triggers (analog)
    val leftTrigger: Float = 0f,
    val rightTrigger: Float = 0f,

    // Stick clicks
    val buttonL3: Boolean = false,
    val buttonR3: Boolean = false,

    // System buttons
    val buttonStart: Boolean = false,
    val buttonSelect: Boolean = false,
    val buttonHome: Boolean = false,
    val buttonCapture: Boolean = false,
) {
    /**
     * Computes the hat switch value from D-pad booleans.
     * Returns the HID hat direction (0-7) or 0x0F for centered.
     */
    fun hatDirection(): Int {
        val up = dpadUp
        val down = dpadDown
        val left = dpadLeft
        val right = dpadRight

        return when {
            up && right -> 1    // UP_RIGHT
            up && left -> 7     // UP_LEFT
            down && right -> 3  // DOWN_RIGHT
            down && left -> 5   // DOWN_LEFT
            up -> 0             // UP
            right -> 2          // RIGHT
            down -> 4           // DOWN
            left -> 6           // LEFT
            else -> 0x0F        // CENTERED (null state)
        }
    }

    /**
     * Packs all 16 buttons into a 16-bit bitmask.
     * Bit 0 = Button 1 (A), Bit 15 = Button 16.
     */
    fun buttonBitmask(): Int {
        var mask = 0
        // TOTK 的通用 HID 通道按下发的 usage 号取位（runtime/gamepad.m 的
        // hid_pad_compute），所以要按 Switch 的排布放位，而不是 Xbox 的 A/B/X/Y：
        //   usage 1 = B(下)   2 = A(右)   4 = Y(左)   5 = X(上)
        //   usage 7 = L1      8 = R1      9 = ZL     10 = ZR
        //   usage 11 = minus  12 = plus   14 = L3    15 = R3
        if (buttonB) mask = mask or (1 shl 0)              // usage 1  B
        if (buttonA) mask = mask or (1 shl 1)              // usage 2  A
        if (buttonY) mask = mask or (1 shl 3)              // usage 4  Y
        if (buttonX) mask = mask or (1 shl 4)              // usage 5  X
        if (buttonL1) mask = mask or (1 shl 6)             // usage 7  L1
        if (buttonR1) mask = mask or (1 shl 7)             // usage 8  R1
        if (leftTrigger > 0.5f) mask = mask or (1 shl 8)   // usage 9  ZL
        if (rightTrigger > 0.5f) mask = mask or (1 shl 9)  // usage 10 ZR
        if (buttonSelect) mask = mask or (1 shl 10)        // usage 11 minus
        if (buttonStart) mask = mask or (1 shl 11)         // usage 12 plus
        if (buttonL3) mask = mask or (1 shl 13)            // usage 14 L3
        if (buttonR3) mask = mask or (1 shl 14)            // usage 15 R3
        return mask
    }
}
