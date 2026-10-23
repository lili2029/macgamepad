// SDL2 Gamepad Tester for macOS
// Compile: cc -o gamepad_tester gamepad_tester.c $(sdl2-config --cflags --libs)
// Run:     ./gamepad_tester

#include <SDL.h>
#include <stdio.h>
#include <stdlib.h>
#include <signal.h>

static volatile int running = 1;

void handle_signal(int sig) {
    running = 0;
}

const char* hat_name(Uint8 hat) {
    switch (hat) {
        case SDL_HAT_CENTERED:  return "CENTER";
        case SDL_HAT_UP:        return "UP";
        case SDL_HAT_RIGHT:     return "RIGHT";
        case SDL_HAT_DOWN:      return "DOWN";
        case SDL_HAT_LEFT:      return "LEFT";
        case SDL_HAT_RIGHTUP:   return "RIGHT+UP";
        case SDL_HAT_RIGHTDOWN: return "RIGHT+DOWN";
        case SDL_HAT_LEFTUP:    return "LEFT+UP";
        case SDL_HAT_LEFTDOWN:  return "LEFT+DOWN";
        default:                return "UNKNOWN";
    }
}

int main(int argc, char* argv[]) {
    signal(SIGINT, handle_signal);

    if (SDL_Init(SDL_INIT_GAMECONTROLLER | SDL_INIT_JOYSTICK) < 0) {
        fprintf(stderr, "SDL_Init failed: %s\n", SDL_GetError());
        return 1;
    }

    printf("\n");
    printf("╔══════════════════════════════════════════════════╗\n");
    printf("║       MacGamepad — SDL2 Gamepad Tester          ║\n");
    printf("╠══════════════════════════════════════════════════╣\n");
    printf("║  Waiting for gamepad connections...              ║\n");
    printf("║  Press Ctrl+C to quit                           ║\n");
    printf("╚══════════════════════════════════════════════════╝\n");
    printf("\n");

    int num_joysticks = SDL_NumJoysticks();
    printf("Joysticks detected at startup: %d\n\n", num_joysticks);

    for (int i = 0; i < num_joysticks; i++) {
        if (SDL_IsGameController(i)) {
            SDL_GameController* gc = SDL_GameControllerOpen(i);
            if (gc) {
                printf("✅ GameController #%d: %s\n", i, SDL_GameControllerName(gc));
                printf("   Mapping: %s\n", SDL_GameControllerMapping(gc));
            }
        } else {
            SDL_Joystick* js = SDL_JoystickOpen(i);
            if (js) {
                printf("🕹️  Joystick #%d: %s\n", i, SDL_JoystickName(js));
                printf("   Axes: %d, Buttons: %d, Hats: %d\n",
                       SDL_JoystickNumAxes(js),
                       SDL_JoystickNumButtons(js),
                       SDL_JoystickNumHats(js));
            }
        }
    }
    printf("\n--- Listening for events (press Ctrl+C to quit) ---\n\n");

    SDL_Event event;
    while (running) {
        while (SDL_PollEvent(&event)) {
            switch (event.type) {
                case SDL_JOYDEVICEADDED:
                    printf("🔌 Joystick ADDED: index %d", event.jdevice.which);
                    {
                        SDL_Joystick* js = SDL_JoystickOpen(event.jdevice.which);
                        if (js) {
                            printf(" — \"%s\" (axes=%d, buttons=%d, hats=%d)",
                                   SDL_JoystickName(js),
                                   SDL_JoystickNumAxes(js),
                                   SDL_JoystickNumButtons(js),
                                   SDL_JoystickNumHats(js));
                        }
                        if (SDL_IsGameController(event.jdevice.which)) {
                            SDL_GameController* gc = SDL_GameControllerOpen(event.jdevice.which);
                            if (gc) {
                                printf("\n   ✅ Recognized as GameController: %s", SDL_GameControllerName(gc));
                            }
                        }
                    }
                    printf("\n");
                    break;

                case SDL_JOYDEVICEREMOVED:
                    printf("🔌 Joystick REMOVED: id %d\n", event.jdevice.which);
                    break;

                case SDL_JOYAXISMOTION:
                    // Only print significant axis changes to avoid flooding
                    if (abs(event.jaxis.value) > 3000 || event.jaxis.value == 0) {
                        printf("🕹️  Axis %d: %6d  (joy %d)\n",
                               event.jaxis.axis, event.jaxis.value, event.jaxis.which);
                    }
                    break;

                case SDL_JOYBUTTONDOWN:
                    printf("🔴 Button %d: DOWN  (joy %d)\n",
                           event.jbutton.button, event.jbutton.which);
                    break;

                case SDL_JOYBUTTONUP:
                    printf("⚪ Button %d: UP    (joy %d)\n",
                           event.jbutton.button, event.jbutton.which);
                    break;

                case SDL_JOYHATMOTION:
                    printf("🎩 Hat %d: %s  (joy %d)\n",
                           event.jhat.hat, hat_name(event.jhat.value), event.jhat.which);
                    break;

                case SDL_CONTROLLERAXISMOTION:
                    if (abs(event.caxis.value) > 3000 || event.caxis.value == 0) {
                        printf("🎮 GC Axis %s: %6d\n",
                               SDL_GameControllerGetStringForAxis(event.caxis.axis),
                               event.caxis.value);
                    }
                    break;

                case SDL_CONTROLLERBUTTONDOWN:
                    printf("🎮🔴 GC Button %s: DOWN\n",
                           SDL_GameControllerGetStringForButton(event.cbutton.button));
                    break;

                case SDL_CONTROLLERBUTTONUP:
                    printf("🎮⚪ GC Button %s: UP\n",
                           SDL_GameControllerGetStringForButton(event.cbutton.button));
                    break;
            }
        }
        SDL_Delay(1); // 1ms poll — low latency
    }

    printf("\nShutting down...\n");
    SDL_Quit();
    return 0;
}
