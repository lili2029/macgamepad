#include <CoreFoundation/CoreFoundation.h>
#include <IOKit/hid/IOHIDManager.h>
#include <IOKit/hid/IOHIDKeys.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <signal.h>

static volatile int running = 1;

void handle_sig(int sig) {
    running = 0;
    CFRunLoopStop(CFRunLoopGetCurrent());
}

static void input_report_callback(void *context, IOReturn result, void *sender,
                                  IOHIDReportType type, uint32_t reportID,
                                  uint8_t *report, CFIndex reportLength) {
    printf("[Diagnose] Received Report (ID: %d, Len: %ld): ", reportID, (long)reportLength);
    for (CFIndex i = 0; i < reportLength; i++) {
        printf("%02X ", report[i]);
    }
    printf("\n");
}

int main(int argc, char **argv) {
    setbuf(stdout, NULL);
    signal(SIGINT, handle_sig);

    printf("[Diagnose] Listening for reports from phone (VID: 0x001D, PID: 0x1200)...\n");
    printf("[Diagnose] Press buttons / move sticks on phone to test.\n");
    printf("[Diagnose] Press Ctrl+C to exit.\n\n");

    IOHIDManagerRef hidManager = IOHIDManagerCreate(kCFAllocatorDefault, kIOHIDManagerOptionNone);
    
    CFMutableDictionaryRef matchDict = CFDictionaryCreateMutable(kCFAllocatorDefault, 0,
                                                                 &kCFTypeDictionaryKeyCallBacks,
                                                                 &kCFTypeDictionaryValueCallBacks);
    int vendorId = 0x001D;
    int productId = 0x1200;
    CFNumberRef vidNum = CFNumberCreate(kCFAllocatorDefault, kCFNumberIntType, &vendorId);
    CFNumberRef pidNum = CFNumberCreate(kCFAllocatorDefault, kCFNumberIntType, &productId);
    CFDictionarySetValue(matchDict, CFSTR(kIOHIDVendorIDKey), vidNum);
    CFDictionarySetValue(matchDict, CFSTR(kIOHIDProductIDKey), pidNum);

    IOHIDManagerSetDeviceMatching(hidManager, matchDict);
    CFRelease(vidNum);
    CFRelease(pidNum);
    CFRelease(matchDict);

    IOHIDManagerRegisterInputReportCallback(hidManager, input_report_callback, NULL);

    IOReturn openRet = IOHIDManagerOpen(hidManager, kIOHIDOptionsTypeNone);
    if (openRet != kIOReturnSuccess) {
        fprintf(stderr, "[Diagnose] Failed to open IOHIDManager (%d)\n", openRet);
        CFRelease(hidManager);
        return 1;
    }

    IOHIDManagerScheduleWithRunLoop(hidManager, CFRunLoopGetCurrent(), kCFRunLoopDefaultMode);

    CFRunLoopRun();

    IOHIDManagerUnscheduleFromRunLoop(hidManager, CFRunLoopGetCurrent(), kCFRunLoopDefaultMode);
    IOHIDManagerClose(hidManager, kIOHIDOptionsTypeNone);
    CFRelease(hidManager);

    printf("\n[Diagnose] Finished.\n");
    return 0;
}
