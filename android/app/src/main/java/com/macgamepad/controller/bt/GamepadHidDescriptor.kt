package com.macgamepad.controller.bt

/**
 * HID Report Descriptor for a full-featured gamepad.
 *
 * This descriptor defines a gamepad with:
 * - 2 analog sticks (16-bit X/Y axes each)
 * - 1 D-pad (8-way hat switch)
 * - 16 buttons
 * - 2 analog triggers (8-bit each)
 *
 * Total input report size: 13 bytes (Report ID 0x01)
 *
 * The descriptor follows USB HID Usage Tables 1.4 and is designed to be
 * recognized by macOS's IOHIDManager, SDL2/SDL3, and Apple's GameController.framework
 * as a standard gamepad — no brand-specific VID/PID spoofing needed.
 *
 * Report layout (Report ID 0x01, 13 bytes):
 *   Byte 0-1:   Left Stick X   (int16, little-endian, -32767..32767)
 *   Byte 2-3:   Left Stick Y   (int16, little-endian, -32767..32767)
 *   Byte 4-5:   Right Stick X  (int16, little-endian, -32767..32767)
 *   Byte 6-7:   Right Stick Y  (int16, little-endian, -32767..32767)
 *   Byte 8:     Hat Switch (low nibble, 0-7 for directions, 0x0F for centered)
 *   Byte 9-10:  Buttons bitmask (16 bits, little-endian)
 *   Byte 11:    Left Trigger   (uint8, 0-255)
 *   Byte 12:    Right Trigger  (uint8, 0-255)
 */
object GamepadHidDescriptor {

    /** Report ID for the main gamepad input report */
    const val REPORT_ID: Byte = 0x01

    /** Total size of the input report (excluding report ID byte) */
    const val REPORT_SIZE_BYTES = 13

    /**
     * Full HID Report Descriptor bytes.
     *
     * Carefully constructed to match what macOS, SDL2, and emulators expect
     * from a standard HID gamepad. Each section is commented with the HID
     * item it represents.
     */
    val descriptor: ByteArray = byteArrayOf(
        // ============================================================
        // Top-level Application Collection: Game Pad
        // ============================================================
        0x05, 0x01,             // USAGE_PAGE (Generic Desktop)
        0x09, 0x05,             // USAGE (Game Pad)
        0xA1.toByte(), 0x01,    // COLLECTION (Application)

        0x85.toByte(), REPORT_ID, // REPORT_ID (1)

        // ============================================================
        // Left Stick: X and Y axes (16-bit signed each)
        // ============================================================
        0x05, 0x01,             //   USAGE_PAGE (Generic Desktop)
        0x09, 0x30,             //   USAGE (X) — Left Stick Horizontal
        0x09, 0x31,             //   USAGE (Y) — Left Stick Vertical
        0x16, 0x01, 0x80.toByte(), //   LOGICAL_MINIMUM (-32767)
        0x26, 0xFF.toByte(), 0x7F, //   LOGICAL_MAXIMUM (32767)
        0x75, 0x10,             //   REPORT_SIZE (16 bits)
        0x95.toByte(), 0x02,    //   REPORT_COUNT (2) — X and Y
        0x81.toByte(), 0x02,    //   INPUT (Data, Variable, Absolute)

        // ============================================================
        // Right Stick: Z and Rz axes (16-bit signed each)
        //   TOTK 的通用 HID 通道把 Z/Rz 当右摇杆读（runtime/gamepad.m），
        //   所以右摇杆必须占用 Z/Rz，而不是 Rx/Ry。
        // ============================================================
        0x09, 0x32,             //   USAGE (Z)  — Right Stick Horizontal
        0x09, 0x35,             //   USAGE (Rz) — Right Stick Vertical
        0x16, 0x01, 0x80.toByte(), //   LOGICAL_MINIMUM (-32767)
        0x26, 0xFF.toByte(), 0x7F, //   LOGICAL_MAXIMUM (32767)
        0x75, 0x10,             //   REPORT_SIZE (16 bits)
        0x95.toByte(), 0x02,    //   REPORT_COUNT (2) — Rx and Ry
        0x81.toByte(), 0x02,    //   INPUT (Data, Variable, Absolute)

        // ============================================================
        // Hat Switch (D-Pad): 4-bit, 8 directions + null
        // ============================================================
        0x05, 0x01,             //   USAGE_PAGE (Generic Desktop)
        0x09, 0x39,             //   USAGE (Hat Switch)
        0x15, 0x00,             //   LOGICAL_MINIMUM (0)
        0x25, 0x07,             //   LOGICAL_MAXIMUM (7)
        0x35, 0x00,             //   PHYSICAL_MINIMUM (0)
        0x46, 0x3B, 0x01,      //   PHYSICAL_MAXIMUM (315 degrees)
        0x65, 0x14,             //   UNIT (English Rotation: Degrees)
        0x75, 0x04,             //   REPORT_SIZE (4 bits)
        0x95.toByte(), 0x01,    //   REPORT_COUNT (1)
        0x81.toByte(), 0x42,    //   INPUT (Data, Variable, Absolute, Null State)

        // 4-bit padding to byte-align after hat switch
        0x75, 0x04,             //   REPORT_SIZE (4 bits)
        0x95.toByte(), 0x01,    //   REPORT_COUNT (1)
        0x81.toByte(), 0x03,    //   INPUT (Constant, Variable, Absolute) — padding

        // ============================================================
        // Buttons: 16 buttons (2 bytes)
        // ============================================================
        0x05, 0x09,             //   USAGE_PAGE (Button)
        0x19, 0x01,             //   USAGE_MINIMUM (Button 1)
        0x29, 0x10,             //   USAGE_MAXIMUM (Button 16)
        0x15, 0x00,             //   LOGICAL_MINIMUM (0)
        0x25, 0x01,             //   LOGICAL_MAXIMUM (1)
        0x75, 0x01,             //   REPORT_SIZE (1 bit)
        0x95.toByte(), 0x10,    //   REPORT_COUNT (16)
        0x81.toByte(), 0x02,    //   INPUT (Data, Variable, Absolute)

        // ============================================================
        // Analog triggers kept on Rx/Ry: TOTK reads Z/Rz as the right stick,
        // so ZL/ZR are delivered through the button bitmap instead.
        // ============================================================
        0x05, 0x01,             //   USAGE_PAGE (Generic Desktop)
        0x09, 0x33,             //   USAGE (Rx) — Left Trigger
        0x09, 0x34,             //   USAGE (Ry) — Right Trigger
        0x15, 0x00,             //   LOGICAL_MINIMUM (0)
        0x26, 0xFF.toByte(), 0x00, //   LOGICAL_MAXIMUM (255)
        0x75, 0x08,             //   REPORT_SIZE (8 bits)
        0x95.toByte(), 0x02,    //   REPORT_COUNT (2)
        0x81.toByte(), 0x02,    //   INPUT (Data, Variable, Absolute)

        // ============================================================
        // End Collection
        // ============================================================
        0xC0.toByte()           // END_COLLECTION
    )

    // ================================================================
    // Hat switch direction constants
    // ================================================================
    object HatDirection {
        const val UP = 0
        const val UP_RIGHT = 1
        const val RIGHT = 2
        const val DOWN_RIGHT = 3
        const val DOWN = 4
        const val DOWN_LEFT = 5
        const val LEFT = 6
        const val UP_LEFT = 7
        const val CENTERED = 0x0F // Null state — no direction pressed
    }

    // ================================================================
    // Button index constants (bit positions in the 16-bit bitmask)
    // ================================================================
    object Buttons {
        const val A = 0          // Button 1 — Cross/A
        const val B = 1          // Button 2 — Circle/B
        const val X = 2          // Button 3 — Square/X
        const val Y = 3          // Button 4 — Triangle/Y
        const val L1 = 4         // Button 5 — Left Bumper
        const val R1 = 5         // Button 6 — Right Bumper
        const val SELECT = 6     // Button 7 — Back/Select/Share
        const val START = 7      // Button 8 — Start/Options
        const val HOME = 8       // Button 9 — Guide/PS/Home
        const val L3 = 9         // Button 10 — Left Stick Click
        const val R3 = 10        // Button 11 — Right Stick Click
        const val CAPTURE = 11   // Button 12 — Capture/Touchpad
        const val SPARE_1 = 12   // Button 13
        const val SPARE_2 = 13   // Button 14
        const val SPARE_3 = 14   // Button 15
        const val SPARE_4 = 15   // Button 16
    }
}
