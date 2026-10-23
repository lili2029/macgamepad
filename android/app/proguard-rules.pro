# MacGamepad ProGuard Rules
# Keep Bluetooth HID classes
-keep class android.bluetooth.** { *; }
-keep class com.macgamepad.controller.bt.** { *; }
