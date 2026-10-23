package com.macgamepad.controller.bt

/**
 * Unified HID Report Descriptor: Keyboard + Mouse + Gamepad.
 *
 * Keyboard is kept first (Report ID 1) because macOS built-in drivers expect
 * it at ID 1 for native keyboard functionality. Gamepad (Report ID 3) works
 * through Steam Input or direct raw HID access (e.g. SDL2 emulators).
 */
object KeyboardHidDescriptor {

    const val KEYBOARD_REPORT_ID: Byte = 0x01
    const val MOUSE_REPORT_ID: Byte = 0x02
    const val GAMEPAD_REPORT_ID: Byte = 0x03

    val descriptor: ByteArray = byteArrayOf(
        // ============================================================
        // Gamepad (Report ID 3)
        // ============================================================
        0x05, 0x01,             // USAGE_PAGE (Generic Desktop)
        0x09, 0x05,             // USAGE (Game Pad)
        0xA1.toByte(), 0x01,    // COLLECTION (Application)
        0x85.toByte(), GAMEPAD_REPORT_ID, // REPORT_ID (3)

        // Left Stick (X, Y)
        0x05, 0x01,             //   USAGE_PAGE (Generic Desktop)
        0x09, 0x30,             //   USAGE (X)
        0x09, 0x31,             //   USAGE (Y)
        0x16, 0x01, 0x80.toByte(), //   LOGICAL_MINIMUM (-32767)
        0x26, 0xFF.toByte(), 0x7F, //   LOGICAL_MAXIMUM (32767)
        0x75, 0x10,             //   REPORT_SIZE (16)
        0x95.toByte(), 0x02,    //   REPORT_COUNT (2)
        0x81.toByte(), 0x02,    //   INPUT (Data, Variable, Absolute)

        // Right Stick (Rx, Ry)
        0x09, 0x33,             //   USAGE (Rx)
        0x09, 0x34,             //   USAGE (Ry)
        0x16, 0x01, 0x80.toByte(), //   LOGICAL_MINIMUM (-32767)
        0x26, 0xFF.toByte(), 0x7F, //   LOGICAL_MAXIMUM (32767)
        0x75, 0x10,             //   REPORT_SIZE (16)
        0x95.toByte(), 0x02,    //   REPORT_COUNT (2)
        0x81.toByte(), 0x02,    //   INPUT (Data, Variable, Absolute)

        // Hat switch (D-Pad)
        0x05, 0x01,             //   USAGE_PAGE (Generic Desktop)
        0x09, 0x39,             //   USAGE (Hat Switch)
        0x15, 0x00,             //   LOGICAL_MINIMUM (0)
        0x25, 0x07,             //   LOGICAL_MAXIMUM (7)
        0x35, 0x00,             //   PHYSICAL_MINIMUM (0)
        0x46, 0x3B, 0x01,      //   PHYSICAL_MAXIMUM (315 degrees)
        0x65, 0x14,             //   UNIT (Degrees)
        0x75, 0x04,             //   REPORT_SIZE (4)
        0x95.toByte(), 0x01,    //   REPORT_COUNT (1)
        0x81.toByte(), 0x42,    //   INPUT (Data, Variable, Absolute, Null State)

        // 4-bit padding after D-pad
        0x75, 0x04,             //   REPORT_SIZE (4)
        0x95.toByte(), 0x01,    //   REPORT_COUNT (1)
        0x81.toByte(), 0x03,    //   INPUT (Const, Var, Abs)

        // Buttons
        0x05, 0x09,             //   USAGE_PAGE (Button)
        0x19, 0x01,             //   USAGE_MINIMUM (Button 1)
        0x29, 0x10,             //   USAGE_MAXIMUM (Button 16)
        0x15, 0x00,             //   LOGICAL_MINIMUM (0)
        0x25, 0x01,             //   LOGICAL_MAXIMUM (1)
        0x75, 0x01,             //   REPORT_SIZE (1)
        0x95.toByte(), 0x10,    //   REPORT_COUNT (16)
        0x81.toByte(), 0x02,    //   INPUT (Data, Var, Abs)

        // Triggers (Z/Rz)
        0x05, 0x01,             //   USAGE_PAGE (Generic Desktop)
        0x09, 0x32,             //   USAGE (Z)
        0x09, 0x35,             //   USAGE (Rz)
        0x15, 0x00,             //   LOGICAL_MINIMUM (0)
        0x26, 0xFF.toByte(), 0x00, //   LOGICAL_MAXIMUM (255)
        0x75, 0x08,             //   REPORT_SIZE (8)
        0x95.toByte(), 0x02,    //   REPORT_COUNT (2)
        0x81.toByte(), 0x02,    //   INPUT (Data, Var, Abs)

        0xC0.toByte(),          // END_COLLECTION (gamepad)

        // ============================================================
        // KEYBOARD (Report ID 1)
        // ============================================================
        0x05, 0x01,         // USAGE_PAGE (Generic Desktop)
        0x09, 0x06,         // USAGE (Keyboard)
        0xA1.toByte(), 0x01, // COLLECTION (Application)
        0x85.toByte(), KEYBOARD_REPORT_ID, // REPORT_ID (1)

        // Modifier keys (8 bits)
        0x05, 0x07,         //   USAGE_PAGE (Keyboard/Keypad)
        0x19, 0xE0.toByte(), //   USAGE_MINIMUM (Keyboard Left Control)
        0x29, 0xE7.toByte(), //   USAGE_MAXIMUM (Keyboard Right GUI)
        0x15, 0x00,         //   LOGICAL_MINIMUM (0)
        0x25, 0x01,         //   LOGICAL_MAXIMUM (1)
        0x75, 0x01,         //   REPORT_SIZE (1)
        0x95.toByte(), 0x08, //   REPORT_COUNT (8)
        0x81.toByte(), 0x02, //   INPUT (Data, Variable, Absolute)

        // Reserved byte
        0x95.toByte(), 0x01, //   REPORT_COUNT (1)
        0x75, 0x08,         //   REPORT_SIZE (8)
        0x81.toByte(), 0x03, //   INPUT (Constant, Variable, Absolute)

        // Keycodes (up to 6 simultaneous keys pressed)
        0x95.toByte(), 0x06, //   REPORT_COUNT (6)
        0x75, 0x08,         //   REPORT_SIZE (8)
        0x15, 0x00,         //   LOGICAL_MINIMUM (0)
        0x26, 0xFF.toByte(), 0x00, //   LOGICAL_MAXIMUM (255)
        0x05, 0x07,         //   USAGE_PAGE (Keyboard/Keypad)
        0x19, 0x00,         //   USAGE_MINIMUM (Reserved)
        0x29, 0xFF.toByte(), //   USAGE_MAXIMUM (Keyboard Application)
        0x81.toByte(), 0x00, //   INPUT (Data, Array, Absolute)

        0xC0.toByte(),       // END_COLLECTION

        // ============================================================
        // Mouse/Trackpad (Report ID 2)
        // ============================================================
        0x05, 0x01,         // USAGE_PAGE (Generic Desktop)
        0x09, 0x02,         // USAGE (Mouse)
        0xA1.toByte(), 0x01, // COLLECTION (Application)
        0x09, 0x01,         //   USAGE (Pointer)
        0xA1.toByte(), 0x00, //   COLLECTION (Physical)
        0x85.toByte(), MOUSE_REPORT_ID, //     REPORT_ID (2)

        // Mouse Buttons (Left, Right, Middle)
        0x05, 0x09,         //     USAGE_PAGE (Button)
        0x19, 0x01,         //     USAGE_MINIMUM (Button 1)
        0x29, 0x03,         //     USAGE_MAXIMUM (Button 3)
        0x15, 0x00,         //     LOGICAL_MINIMUM (0)
        0x25, 0x01,         //     LOGICAL_MAXIMUM (1)
        0x75, 0x01,         //     REPORT_SIZE (1)
        0x95.toByte(), 0x03, //     REPORT_COUNT (3)
        0x81.toByte(), 0x02, //     INPUT (Data, Variable, Absolute)

        // Padding (5 bits to byte-align)
        0x95.toByte(), 0x01, //     REPORT_COUNT (1)
        0x75, 0x05,         //     REPORT_SIZE (5)
        0x81.toByte(), 0x03, //     INPUT (Constant, Variable, Absolute)

        // Relative Movement (X, Y)
        0x05, 0x01,         //     USAGE_PAGE (Generic Desktop)
        0x09, 0x30,         //     USAGE (X)
        0x09, 0x31,         //     USAGE (Y)
        0x15, 0x81.toByte(), //     LOGICAL_MINIMUM (-127)
        0x25, 0x7F,         //     LOGICAL_MAXIMUM (127)
        0x75, 0x08,         //     REPORT_SIZE (8)
        0x95.toByte(), 0x02, //     REPORT_COUNT (2)
        0x81.toByte(), 0x06, //     INPUT (Data, Variable, Relative)

        // Scroll Wheel
        0x09, 0x38,         //     USAGE (Wheel)
        0x15, 0x81.toByte(), //     LOGICAL_MINIMUM (-127)
        0x25, 0x7F,         //     LOGICAL_MAXIMUM (127)
        0x75, 0x08,         //     REPORT_SIZE (8)
        0x95.toByte(), 0x01, //     REPORT_COUNT (1)
        0x81.toByte(), 0x06, //     INPUT (Data, Variable, Relative)

        0xC0.toByte(),       //   END_COLLECTION
        0xC0.toByte(),       // END_COLLECTION
    )

    object Keycodes {
        const val NONE: Byte = 0x00
        
        // Left Stick (Movement)
        const val W: Byte = 0x1A
        const val A: Byte = 0x04
        const val S: Byte = 0x16
        const val D: Byte = 0x07

        // Right Stick (Camera)
        const val I: Byte = 0x0C
        const val K: Byte = 0x0E
        const val J: Byte = 0x0D
        const val L: Byte = 0x0F

        // D-Pad
        const val UP: Byte = 0x52
        const val DOWN: Byte = 0x51
        const val LEFT: Byte = 0x50
        const val RIGHT: Byte = 0x4F

        // Bumpers & Triggers
        const val E: Byte = 0x08  // L
        const val U: Byte = 0x18  // R
        const val Q: Byte = 0x14  // ZL
        const val O: Byte = 0x12  // ZR

        // Face Buttons
        const val Z: Byte = 0x1D  // Switch A -> Keyboard Z
        const val X: Byte = 0x1B  // Switch B -> Keyboard X
        const val C: Byte = 0x06  // Switch X -> Keyboard C
        const val V: Byte = 0x19  // Switch Y -> Keyboard V

        // Stick Click Buttons
        const val F: Byte = 0x09  // L3
        const val H: Byte = 0x0B  // R3

        // System Buttons (+ / -)
        const val EQUALS: Byte = 0x2E // Start (+)
        const val MINUS: Byte = 0x2D  // Select (-)
        const val ESCAPE: Byte = 0x29
    }
}
