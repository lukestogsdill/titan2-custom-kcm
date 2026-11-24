# Titan 2 Custom Keyboard Layout

Optimized physical keyboard layout for the Titan 2. Features a custom Sym layer for navigation, additional symbols, and system keys, allowing you to disable the virtual keyboard entirely without needing root access.

The **Titan2 Keys** app (`android/`) adds the parts a layout file can't do:

- **Sym + B** toggles an on-screen emoji panel. The rest of the time there's no on-screen keyboard, like "No Keyboard".
- **Sym + C** turns on a controller layer: keys act as gamepad buttons (for Moonlight). Which key is which button is set in the app.
- **Double-tap Shift, Alt or Sym** locks it (Shift = caps lock). A badge at the top of the screen shows what's locked.
- **Holding Home** turns **Super** (the Windows key) on and off, e.g. for Hyprland shortcuts through Moonlight.
- It includes this layout, so ExKeyMo isn't needed anymore.

## Layout Matrix

### Row 1 (System/Top Keys)

> **Note:** Editing these keys requires root to modify the `.kl` file. Otherwise, use **Settings → Shortcut keys** for basic remapping.

- **Back, App_Switch:** Default
- **Home** (or the key with code 408): Default. Hold = Super on/off (needs the app).
- **Shift, ALT:** Default. Double-tap = Caps lock / Alt lock (needs the app).
- **Sym:** Set to "Show/Hide on-screen keyboard" (layer modifier). Double-tap = Sym lock (needs the app).
- **Fn:** Set to Control via settings

---

### Row 2 (Top Row)
- **Base:** [q, w, e, r, t, y, u, i, o, p]
- **Alt:** [0, 1, 2, 3, (, ), -, _, /, :]
- **Sym:** [ESC, ~, $, &, {, }, [, ], `\`, ;]

### Row 3 (Home Row)
- **Base:** [a, s, d, f, g, h, j, k, l, Enter]
- **Alt:** [@, 4, 5, 6, *, #, +, ", ']
- **Sym:** [TAB, %, ^, <, >, ←, ↓, ↑, →, INSERT]

### Row 4 (Bottom Row)
- **Base:** [z, x, c, v, Space, b, n, m]
- **Alt:** [!, 7, 8, 9, ., ,, ?]
- **Sym:** [=, —, CONTROLLER, —, SEARCH, EMOJI, HOME, END]

---

## Titan2 Keys app

### Build

```sh
./android/build.sh            # -> titan2-keys.apk in the repo root
./android/build.sh --install  # same, then installs over adb
```

Run it on an x86-64 Linux PC, not in Termux on the phone (Google's SDK tools only run on x86-64). The first run downloads a JDK 17, the Android SDK and Gradle into `android/.toolchain/` (no root; delete the folder to remove them). It asks you to accept the Android SDK license once. If you already have `JAVA_HOME` / `ANDROID_HOME` set, it uses those.

`--install` needs the phone connected to adb: either a USB cable with USB debugging on, or **Developer options → Wireless debugging → Pair device with pairing code**, then on the PC `android/.toolchain/sdk/platform-tools/adb pair <ip:port>` and `adb connect <ip:port>`. Otherwise just copy `titan2-keys.apk` to the phone and open it.

### Set up on the phone

1. Install `titan2-keys.apk` and open **Titan2 Keys**. It has a button for each step and shows which ones are done.
2. **Physical keyboard layout:** Settings → System → Languages & input → Physical keyboard → pick **Titan 2 Custom** (the one from Titan2 Keys). On newer Android the layout is chosen per keyboard app, so pick it again after switching to Titan2 Emoji.
3. **Keyboard:** enable **Titan2 Emoji** and switch to it (it replaces "No Keyboard").
4. **Locks & Super:** Settings → Accessibility → **Titan2 Key locks & Super** → on. If it's greyed out ("restricted setting"), go to App info → ⋮ → **Allow restricted settings** first. Android does this for every sideloaded accessibility app.
5. **Controller (optional):** in Moonlight, Settings → Input Settings → turn off **Automatic gamepad presence detection**. Otherwise Moonlight tells the PC there's no gamepad and the PC ignores the buttons.

### Emoji panel (Sym + B)

- Tap an emoji to insert it. The panel stays open so you can pick several.
- Or type to search by name (`fire`, `thumbs up`). **Enter** inserts the highlighted one and closes the panel.
- **Sym+H/J/K/L** move the highlight, **Sym+A** (Tab) switches category, **Sym+Q** (Esc) clears the search, **Backspace** edits it.
- **Sym+B** or **Back** closes the panel. Recently used emoji get their own tab.

In Moonlight, typing goes to the stream, so use touch there.

### Locks (double-tap Shift, Alt or Sym)

| Double-tap | Locks         | Affects                                      |
| ---------- | ------------- | -------------------------------------------- |
| Shift      | **CAPS**      | Letters only                                 |
| Alt        | **ALT**       | Keys with an Alt character (numbers, symbols) |
| Sym        | **SYM**       | The Sym layer (arrows, Tab, symbols, emoji)  |

- Tap the same key once to unlock.
- A colored chip in the middle of the status bar shows each active lock (and SUPER).
- A lock only changes keys where its layer has something, so with Alt lock Backspace, Enter and Space stay normal.
- Holding the other layer's key wins for that press: Alt+key during Sym lock gives the Alt character.

### Super (hold Home)

**Hold Home** for half a second to turn Super on (you'll feel a tick and see **SUPER**). Every key is then sent as Super + key until you hold Home again. A short press of Home still goes home. A key that sends code 408 (scan code or keycode) works the same way, except a short press of it does whatever it normally does.

| With Super on, press       | Sent                   | Omarchy           |
| -------------------------- | ---------------------- | ----------------- |
| Enter                      | Super+Return           | Terminal          |
| Space                      | Super+Space            | Launcher          |
| W / Q                      | Super+W / Q            | Close window      |
| Sym+H/J/K/L                | Super+← ↓ ↑ →          | Move focus        |
| Alt+W, Alt+E, … (1, 2, …)  | Super+1, Super+2       | Workspaces        |
| Shift+Alt+W                | Super+Shift+1          | Move to workspace |
| Alt+Space / Alt+Enter      | Super+Alt+Space/Return | Menu / Tmux       |
| Shift+B, Shift+N, …        | Super+Shift+B, …       | App launchers     |

Rules:
- The Sym layer works as usual: Sym+H is Super+←, plain H is Super+H.
- Alt (held or locked) picks a character as usual (Alt+W = 1 → Super+1). Where Alt doesn't change the character (Enter, Space), it's sent as a real Alt.
- Caps lock is ignored while Super is on. Use Shift for Super+Shift combos.
- Super is pressed and let go around each key, so it can't get stuck down on the PC if you switch apps with it on.
- No Super+Alt+letter, since Alt turns letters into digits/symbols.

How it works: Moonlight reads keys before any on-screen keyboard can (`StreamView.onKeyPreIme`), so the accessibility service takes the keys a lock or Super changes. It sends the changed keys through the Titan2 Emoji keyboard, which delivers them straight to the app. That also means Android's own Meta shortcuts don't steal Super+B and the like. Every other key goes through untouched.

**If something doesn't trigger:** open Titan2 Keys and press the key in the test field. The log shows `service saw <key> down scan=<n>` for every key the service gets. If a key never shows up there, the phone is handling it itself before apps get it. Try changing it under **Settings → Shortcut keys**. A different Super key can be set with `SUPER_KEY_CODE` in `SuperKeyService.kt`.

### Controller (Sym + C)

**Sym + C** turns the controller layer on and off (you'll feel a tick and see **PAD**). While it's on, the bound keys are gamepad buttons and every other key types as usual. Default layout:

| Keys            | Gamepad          | Keys      | Gamepad             |
| --------------- | ---------------- | --------- | ------------------- |
| W / A / S / D   | D-pad ↑ ← ↓ →    | I / J / K / L | Y / X / A / B   |
| Q / E           | LB / LT          | U / O     | RB / RT             |
| Space           | A                | Enter     | Start               |
| Z               | Select (View)    | X / M     | L3 / R3 (stick click) |

**Change the keys:** open Titan2 Keys → **Controller keybinds**. Tap a key to pick its button (or **None**), or **+ Bind a key** and press any key, including Shift, Alt, Ctrl (Fn), Backspace or the volume keys. A key can also send a keyboard key (Esc, Tab, arrows, letters, F1–F12) for games that use both. **Reset to defaults** puts the table above back. Changes apply right away.

Rules:
- Holding Sym (or Sym lock) gives the normal Sym layer, so Sym+H/J/K/L are still arrows and Sym+C turns the controller off.
- Holding Alt types the Alt layer as usual, unless you've bound Alt as a button.
- Home still toggles Super. Back and the other system keys can't be bound.
- There are no analog sticks (Android doesn't let an app fake them), so games need to work with the D-pad.
- Moonlight's own controller shortcuts apply: holding Start toggles its mouse mode, Start+Select+LB+RB ends the stream.

How it works: the buttons go through the Titan2 Emoji keyboard like Super does, as gamepad key events from a device Moonlight treats as player 1 (id 0, or a built-in non-letter device such as the volume buttons). It's made for Moonlight; Android games may or may not accept these buttons. If nothing happens, check the Moonlight setting in step 5, then the log in Titan2 Keys: turning the controller on shows `controller buttons go out as device <n>`, and each press shows `sent gamepad BUTTON_A down`.

## Layout only, via ExKeyMo (no app)

`keyboard.apk` is the older ExKeyMo build of this layout. It doesn't have Sym+B. To build your own, run exkeymo-server, open it on localhost, paste `titan2-custom.kcm`, and compile an APK. Then:

1. Go to **Settings → System → Languages & input → Physical keyboard**
2. Select your physical keyboard
3. For the KCM provider, you must select **"Other"**
4. Choose your installed Exkeymo layout

### Hardware Key Setup

1. Go to **Settings → Shortcut keys**
2. Set **Fn** to **Control**
3. In **Physical Keyboard** settings, toggle **"Show virtual keyboard"** to **OFF**
