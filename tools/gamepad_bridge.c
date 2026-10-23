#include <CoreFoundation/CoreFoundation.h>
#include <IOKit/hid/IOHIDManager.h>
#include <IOKit/hid/IOHIDKeys.h>
#include <IOKit/hidsystem/IOHIDUserDevice.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <signal.h>
#include <mach/mach_time.h>
#include <dispatch/dispatch.h>

// Unified HID Report Descriptor matching the phone's layout
static const uint8_t hid_descriptor[] = {
    // ============================================================
    // GAMEPAD (Report ID 1)
    // ============================================================
    0x05, 0x01,             // USAGE_PAGE (Generic Desktop)
    0x09, 0x05,             // USAGE (Game Pad)
    0xA1, 0x01,             // COLLECTION (Application)
    0x85, 0x01,             //   REPORT_ID (1)

    // Left Stick (X, Y) — 16-bit signed
    0x05, 0x01,             //   USAGE_PAGE (Generic Desktop)
    0x09, 0x30,             //   USAGE (X)
    0x09, 0x31,             //   USAGE (Y)
    0x16, 0x01, 0x80,       //   LOGICAL_MINIMUM (-32767)
    0x26, 0xFF, 0x7F,       //   LOGICAL_MAXIMUM (32767)
    0x75, 0x10,             //   REPORT_SIZE (16)
    0x95, 0x02,             //   REPORT_COUNT (2)
    0x81, 0x02,             //   INPUT (Data, Variable, Absolute)

    // Right Stick (Rx, Ry) — 16-bit signed
    0x09, 0x33,             //   USAGE (Rx)
    0x09, 0x34,             //   USAGE (Ry)
    0x16, 0x01, 0x80,       //   LOGICAL_MINIMUM (-32767)
    0x26, 0xFF, 0x7F,       //   LOGICAL_MAXIMUM (32767)
    0x75, 0x10,             //   REPORT_SIZE (16)
    0x95, 0x02,             //   REPORT_COUNT (2)
    0x81, 0x02,             //   INPUT (Data, Variable, Absolute)

    // Hat Switch (D-Pad) — 4-bit, 8 directions + null
    0x05, 0x01,             //   USAGE_PAGE (Generic Desktop)
    0x09, 0x39,             //   USAGE (Hat Switch)
    0x15, 0x00,             //   LOGICAL_MINIMUM (0)
    0x25, 0x07,             //   LOGICAL_MAXIMUM (7)
    0x35, 0x00,             //   PHYSICAL_MINIMUM (0)
    0x46, 0x3B, 0x01,       //   PHYSICAL_MAXIMUM (315 degrees)
    0x65, 0x14,             //   UNIT (Degrees)
    0x75, 0x04,             //   REPORT_SIZE (4)
    0x95, 0x01,             //   REPORT_COUNT (1)
    0x81, 0x42,             //   INPUT (Data, Variable, Absolute, Null State)

    // 4-bit padding after D-pad
    0x75, 0x04,             //   REPORT_SIZE (4)
    0x95, 0x01,             //   REPORT_COUNT (1)
    0x81, 0x03,             //   INPUT (Const, Var, Abs)

    // Buttons — 16 buttons (2 bytes)
    0x05, 0x09,             //   USAGE_PAGE (Button)
    0x19, 0x01,             //   USAGE_MINIMUM (Button 1)
    0x29, 0x10,             //   USAGE_MAXIMUM (Button 16)
    0x15, 0x00,             //   LOGICAL_MINIMUM (0)
    0x25, 0x01,             //   LOGICAL_MAXIMUM (1)
    0x75, 0x01,             //   REPORT_SIZE (1)
    0x95, 0x10,             //   REPORT_COUNT (16)
    0x81, 0x02,             //   INPUT (Data, Var, Abs)

    // Triggers — Z (L2) and Rz (R2), 8-bit unsigned
    0x05, 0x01,             //   USAGE_PAGE (Generic Desktop)
    0x09, 0x32,             //   USAGE (Z)
    0x09, 0x35,             //   USAGE (Rz)
    0x15, 0x00,             //   LOGICAL_MINIMUM (0)
    0x26, 0xFF, 0x00,       //   LOGICAL_MAXIMUM (255)
    0x75, 0x08,             //   REPORT_SIZE (8)
    0x95, 0x02,             //   REPORT_COUNT (2)
    0x81, 0x02,             //   INPUT (Data, Var, Abs)

    0xC0,                   // END_COLLECTION

    // ============================================================
    // KEYBOARD (Report ID 2)
    // ============================================================
    0x05, 0x01,             // USAGE_PAGE (Generic Desktop)
    0x09, 0x06,             // USAGE (Keyboard)
    0xA1, 0x01,             // COLLECTION (Application)
    0x85, 0x02,             //   REPORT_ID (2)

    // Modifier keys (8 bits)
    0x05, 0x07,             //   USAGE_PAGE (Keyboard/Keypad)
    0x19, 0xE0,             //   USAGE_MINIMUM (Keyboard Left Control)
    0x29, 0xE7,             //   USAGE_MAXIMUM (Keyboard Right GUI)
    0x15, 0x00,             //   LOGICAL_MINIMUM (0)
    0x25, 0x01,             //   LOGICAL_MAXIMUM (1)
    0x75, 0x01,             //   REPORT_SIZE (1)
    0x95, 0x08,             //   REPORT_COUNT (8)
    0x81, 0x02,             //   INPUT (Data, Variable, Absolute)

    // Reserved byte
    0x95, 0x01,             //   REPORT_COUNT (1)
    0x75, 0x08,             //   REPORT_SIZE (8)
    0x81, 0x03,             //   INPUT (Constant, Variable, Absolute)

    // Keycodes (up to 6 simultaneous keys pressed)
    0x95, 0x06,             //   REPORT_COUNT (6)
    0x75, 0x08,             //   REPORT_SIZE (8)
    0x15, 0x00,             //   LOGICAL_MINIMUM (0)
    0x26, 0xFF, 0x00,       //   LOGICAL_MAXIMUM (255)
    0x05, 0x07,             //   USAGE_PAGE (Keyboard/Keypad)
    0x19, 0x00,             //   USAGE_MINIMUM (Reserved)
    0x29, 0xFF,             //   USAGE_MAXIMUM (Keyboard Application)
    0x81, 0x00,             //   INPUT (Data, Array, Absolute)

    0xC0,                   // END_COLLECTION

    // ============================================================
    // MOUSE (Report ID 3)
    // ============================================================
    0x05, 0x01,             // USAGE_PAGE (Generic Desktop)
    0x09, 0x02,             // USAGE (Mouse)
    0xA1, 0x01,             // COLLECTION (Application)
    0x09, 0x01,             //   USAGE (Pointer)
    0xA1, 0x00,             //   COLLECTION (Physical)
    0x85, 0x03,             //     REPORT_ID (3)

    // Mouse Buttons (Left, Right, Middle)
    0x05, 0x09,             //     USAGE_PAGE (Button)
    0x19, 0x01,             //     USAGE_MINIMUM (Button 1)
    0x29, 0x03,             //     USAGE_MAXIMUM (Button 3)
    0x15, 0x00,             //     LOGICAL_MINIMUM (0)
    0x25, 0x01,             //     LOGICAL_MAXIMUM (1)
    0x75, 0x01,             //     REPORT_SIZE (1)
    0x95, 0x03,             //     REPORT_COUNT (3)
    0x81, 0x02,             //     INPUT (Data, Variable, Absolute)

    // Padding (5 bits to byte-align)
    0x95, 0x01,             //     REPORT_COUNT (1)
    0x75, 0x05,             //     REPORT_SIZE (5)
    0x81, 0x03,             //     INPUT (Constant, Variable, Absolute)

    // Relative Movement (X, Y)
    0x05, 0x01,             //     USAGE_PAGE (Generic Desktop)
    0x09, 0x30,             //     USAGE (X)
    0x09, 0x31,             //     USAGE (Y)
    0x15, 0x81,             //     LOGICAL_MINIMUM (-127)
    0x25, 0x7F,             //     LOGICAL_MAXIMUM (127)
    0x75, 0x08,             //     REPORT_SIZE (8)
    0x95, 0x02,             //     REPORT_COUNT (2)
    0x81, 0x06,             //     INPUT (Data, Variable, Relative)

    // Scroll Wheel
    0x09, 0x38,             //     USAGE (Wheel)
    0x15, 0x81,             //     LOGICAL_MINIMUM (-127)
    0x25, 0x7F,             //     LOGICAL_MAXIMUM (127)
    0x75, 0x08,             //     REPORT_SIZE (8)
    0x95, 0x01,             //     REPORT_COUNT (1)
    0x81, 0x06,             //     INPUT (Data, Variable, Relative)

    0xC0,                   //   END_COLLECTION
    0xC0                    // END_COLLECTION
};

static IOHIDUserDeviceRef virtualDevice = NULL;
static dispatch_queue_t deviceQueue = NULL;
static volatile int running = 1;

void handle_sig(int sig) {
    running = 0;
    CFRunLoopStop(CFRunLoopGetCurrent());
}

// Lazy virtual device creation (Modern macOS 10.15+ API)
static void create_virtual_device(void) {
    if (virtualDevice != NULL) return;

    printf("[Bridge] Creating virtual gamepad controller...\n");

    CFMutableDictionaryRef properties = CFDictionaryCreateMutable(kCFAllocatorDefault, 0,
                                                                 &kCFTypeDictionaryKeyCallBacks,
                                                                 &kCFTypeDictionaryValueCallBacks);

    CFDataRef descriptorCFData = CFDataCreate(kCFAllocatorDefault, hid_descriptor, sizeof(hid_descriptor));
    CFDictionarySetValue(properties, CFSTR(kIOHIDReportDescriptorKey), descriptorCFData);

    // Xbox 360 Controller Vendor/Product ID: maps natively in macOS GameController.framework
    int vendorId = 0x045E;  // Microsoft
    int productId = 0x028E; // Xbox 360 Controller
    CFNumberRef vidNum = CFNumberCreate(kCFAllocatorDefault, kCFNumberIntType, &vendorId);
    CFNumberRef pidNum = CFNumberCreate(kCFAllocatorDefault, kCFNumberIntType, &productId);
    CFDictionarySetValue(properties, CFSTR(kIOHIDVendorIDKey), vidNum);
    CFDictionarySetValue(properties, CFSTR(kIOHIDProductIDKey), pidNum);

    CFDictionarySetValue(properties, CFSTR(kIOHIDProductKey), CFSTR("MacGamepad Virtual Controller"));

    virtualDevice = IOHIDUserDeviceCreateWithProperties(kCFAllocatorDefault, properties, 0);

    CFRelease(descriptorCFData);
    CFRelease(vidNum);
    CFRelease(pidNum);
    CFRelease(properties);

    if (virtualDevice == NULL) {
        fprintf(stderr, "[Bridge] ❌ ERROR: Failed to create IOHIDUserDevice.\n");
        fprintf(stderr, "         Make sure you are running as root (sudo).\n");
        exit(1);
    }

    // Set up modern dispatch queue and activate
    deviceQueue = dispatch_queue_create("com.macgamepad.virtual_device_queue", DISPATCH_QUEUE_SERIAL);
    IOHIDUserDeviceSetDispatchQueue(virtualDevice, deviceQueue);

    // Set cancellation handler to release device safely
    IOHIDUserDeviceSetCancelHandler(virtualDevice, ^{
        printf("[Bridge] Virtual gamepad fully cancelled and clean.\n");
    });

    IOHIDUserDeviceActivate(virtualDevice);

    printf("[Bridge] ✅ Virtual gamepad registered successfully! (Xbox 360 mode)\n");
}

static void destroy_virtual_device(void) {
    if (virtualDevice == NULL) return;
    printf("[Bridge] Destroying virtual gamepad controller...\n");

    IOHIDUserDeviceCancel(virtualDevice);
    CFRelease(virtualDevice);
    virtualDevice = NULL;

    if (deviceQueue) {
        deviceQueue = NULL;
    }
}

// Callback when matching phone connects
static void device_added_callback(void *context, IOReturn result, void *sender, IOHIDDeviceRef device) {
    printf("[Bridge] 🔌 Android phone connected via HID!\n");
    
    CFStringRef prodName = IOHIDDeviceGetProperty(device, CFSTR(kIOHIDProductKey));
    if (prodName) {
        char nameStr[256];
        if (CFStringGetCString(prodName, nameStr, sizeof(nameStr), kCFStringEncodingUTF8)) {
            printf("[Bridge] Device product: %s\n", nameStr);
        }
    }
    
    // Create the virtual controller
    create_virtual_device();
}

// Callback when matching phone disconnects
static void device_removed_callback(void *context, IOReturn result, void *sender, IOHIDDeviceRef device) {
    printf("[Bridge] 🔌 Android phone disconnected.\n");
    destroy_virtual_device();
}

// Callback when phone sends an input report
static void input_report_callback(void *context, IOReturn result, void *sender,
                                  IOHIDReportType type, uint32_t reportID,
                                  uint8_t *report, CFIndex reportLength) {
    if (virtualDevice == NULL) {
        create_virtual_device();
    }

    // Build the packet: report ID (1 byte) + raw data
    uint8_t relay_buf[256];
    if (reportLength + 1 > sizeof(relay_buf)) return;

    relay_buf[0] = (uint8_t)reportID;
    memcpy(&relay_buf[1], report, reportLength);

    uint64_t timestamp = mach_absolute_time();
    IOReturn ret = IOHIDUserDeviceHandleReportWithTimeStamp(virtualDevice, timestamp, relay_buf, reportLength + 1);
    if (ret != kIOReturnSuccess) {
        // Silently ignore report error
    }
}

int main(int argc, char **argv) {
    if (geteuid() != 0) {
        fprintf(stderr, "❌ ERROR: This tool must be run as root (sudo).\n");
        fprintf(stderr, "   Usage: sudo %s\n", argv[0]);
        return 1;
    }

    signal(SIGINT, handle_sig);
    signal(SIGTERM, handle_sig);

    printf("\n");
    printf("╔══════════════════════════════════════════════════╗\n");
    printf("║       MacGamepad — Virtual Controller Bridge     ║\n");
    printf("╠══════════════════════════════════════════════════╣\n");
    printf("║  1. Pairing: Pair your phone to your Mac         ║\n");
    printf("║  2. Status: Listening for Android Gamepad...     ║\n");
    printf("║  3. Mode: Userspace IOHIDUserDevice forwarding   ║\n");
    printf("║                                                  ║\n");
    printf("║  Press Ctrl+C to stop the bridge                 ║\n");
    printf("╚══════════════════════════════════════════════════╝\n\n");

    // Initialize IOHIDManager
    IOHIDManagerRef hidManager = IOHIDManagerCreate(kCFAllocatorDefault, kIOHIDManagerOptionNone);
    if (CFGetTypeID(hidManager) != IOHIDManagerGetTypeID()) {
        fprintf(stderr, "[Bridge] Failed to create IOHIDManager\n");
        return 1;
    }

    // Match Android phones running Bluetooth HID gamepad service
    CFMutableDictionaryRef matchDict = CFDictionaryCreateMutable(kCFAllocatorDefault, 0,
                                                                 &kCFTypeDictionaryKeyCallBacks,
                                                                 &kCFTypeDictionaryValueCallBacks);
    int vendorId = 0x001D;  // Google/Android BT stack VID
    int productId = 0x1200; // Android default HID PID
    CFNumberRef vidNum = CFNumberCreate(kCFAllocatorDefault, kCFNumberIntType, &vendorId);
    CFNumberRef pidNum = CFNumberCreate(kCFAllocatorDefault, kCFNumberIntType, &productId);
    CFDictionarySetValue(matchDict, CFSTR(kIOHIDVendorIDKey), vidNum);
    CFDictionarySetValue(matchDict, CFSTR(kIOHIDProductIDKey), pidNum);

    IOHIDManagerSetDeviceMatching(hidManager, matchDict);
    CFRelease(vidNum);
    CFRelease(pidNum);
    CFRelease(matchDict);

    // Register connect/disconnect callbacks
    IOHIDManagerRegisterDeviceMatchingCallback(hidManager, device_added_callback, NULL);
    IOHIDManagerRegisterDeviceRemovalCallback(hidManager, device_removed_callback, NULL);

    // Register input report callback
    IOHIDManagerRegisterInputReportCallback(hidManager, input_report_callback, NULL);

    // Open HID Manager
    IOReturn openRet = IOHIDManagerOpen(hidManager, kIOHIDOptionsTypeNone);
    if (openRet != kIOReturnSuccess) {
        fprintf(stderr, "[Bridge] Failed to open IOHIDManager (error %d)\n", openRet);
        CFRelease(hidManager);
        return 1;
    }

    // Run loop
    IOHIDManagerScheduleWithRunLoop(hidManager, CFRunLoopGetCurrent(), kCFRunLoopDefaultMode);
    
    // Check if phone is already connected at launch
    CFSetRef connectedDevices = IOHIDManagerCopyDevices(hidManager);
    if (connectedDevices != NULL) {
        CFIndex count = CFSetGetCount(connectedDevices);
        if (count > 0) {
            printf("[Bridge] Found %ld connected Android phone(s) at startup.\n", (long)count);
            create_virtual_device();
        }
        CFRelease(connectedDevices);
    }

    // Start Run Loop
    CFRunLoopRun();

    // Clean up
    destroy_virtual_device();
    IOHIDManagerUnscheduleFromRunLoop(hidManager, CFRunLoopGetCurrent(), kCFRunLoopDefaultMode);
    IOHIDManagerClose(hidManager, kIOHIDOptionsTypeNone);
    CFRelease(hidManager);

    printf("\n[Bridge] Stopped. Bye!\n");
    return 0;
}
