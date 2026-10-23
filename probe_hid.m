// 诊断工具：用与游戏相同的 IOHIDManager 匹配条件打开通用 HID 手柄，
// 打印收到的 input report，确认设备是否真的在发送数据。
#import <Foundation/Foundation.h>
#import <IOKit/hid/IOHIDManager.h>
#import <IOKit/hid/IOHIDKeys.h>

static int gCount = 0;

static void value_cb(void *ctx, IOReturn res, void *sender, IOHIDValueRef value) {
    IOHIDElementRef el = IOHIDValueGetElement(value);
    if (!el) return;
    uint32_t page = IOHIDElementGetUsagePage(el);
    uint32_t usage = IOHIDElementGetUsage(el);
    CFIndex v = IOHIDValueGetIntegerValue(value);
    // 只打印有变化的、非零的可变元素，避免刷屏
    if (page == kHIDPage_GenericDesktop || page == kHIDPage_Button) {
        printf("  page=%u usage=%u value=%ld\n", page, usage, (long)v);
        gCount++;
    }
}

static void matched_cb(void *ctx, IOReturn res, void *sender, IOHIDDeviceRef dev) {
    CFTypeRef prod = IOHIDDeviceGetProperty(dev, CFSTR(kIOHIDProductKey));
    CFTypeRef serial = IOHIDDeviceGetProperty(dev, CFSTR(kIOHIDSerialNumberKey));
    CFTypeRef supported = IOHIDDeviceGetProperty(dev, CFSTR("GameControllerSupportedHIDDevice"));
    printf("[matched] product=%s serial=%s GameControllerSupportedHIDDevice=%s\n",
           prod ? [(__bridge NSString *)prod UTF8String] : "?",
           serial ? [(__bridge NSString *)serial UTF8String] : "?",
           supported ? [(__bridge NSNumber *)supported boolValue] ? "Yes" : "No" : "absent");
}

int main(int argc, char **argv) {
    @autoreleasepool {
        IOHIDManagerRef mgr = IOHIDManagerCreate(kCFAllocatorDefault, kIOHIDOptionsTypeNone);

        NSDictionary *pad = @{ @kIOHIDDeviceUsagePageKey: @(kHIDPage_GenericDesktop),
                               @kIOHIDDeviceUsageKey: @(kHIDUsage_GD_GamePad) };
        NSDictionary *stick = @{ @kIOHIDDeviceUsagePageKey: @(kHIDPage_GenericDesktop),
                                 @kIOHIDDeviceUsageKey: @(kHIDUsage_GD_Joystick) };
        IOHIDManagerSetDeviceMatchingMultiple(mgr, (__bridge CFArrayRef)@[pad, stick]);

        IOHIDManagerRegisterDeviceMatchingCallback(mgr, matched_cb, NULL);
        IOHIDManagerRegisterInputValueCallback(mgr, value_cb, NULL);
        IOHIDManagerScheduleWithRunLoop(mgr, CFRunLoopGetCurrent(), kCFRunLoopDefaultMode);

        IOReturn r = IOHIDManagerOpen(mgr, kIOHIDOptionsTypeNone);
        printf("IOHIDManagerOpen -> 0x%x (%s)\n", r, r == kIOReturnSuccess ? "ok" : "FAILED");

        printf("监听 12 秒，请按手机上的按钮...\n");
        fflush(stdout);
        CFRunLoopRunInMode(kCFRunLoopDefaultMode, 12.0, false);

        printf("\n共收到 %d 个输入值\n", gCount);
        if (gCount == 0)
            printf("=> 设备没有发送任何数据（或本程序无 Input Monitoring 权限）\n");
        else
            printf("=> 设备在正常发送数据\n");

        IOHIDManagerClose(mgr, kIOHIDOptionsTypeNone);
    }
    return 0;
}
