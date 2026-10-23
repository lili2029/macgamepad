CC = clang
CFLAGS = -O3 -Wall
LDFLAGS = -framework CoreFoundation -framework IOKit

all: gamepad_bridge print_reports

gamepad_bridge: gamepad_bridge.c
	$(CC) $(CFLAGS) -o gamepad_bridge gamepad_bridge.c $(LDFLAGS)

print_reports: print_reports.c
	$(CC) $(CFLAGS) -o print_reports print_reports.c $(LDFLAGS)

clean:
	rm -f gamepad_bridge print_reports
