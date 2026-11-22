# Titan 2 Custom Keyboard Layout

Optimized physical keyboard layout for the Titan 2. Features a custom Sym layer for navigation, additional symbols, and system keys, allowing you to disable the virtual keyboard entirely without needing root access.

The **Titan2 Keys** app (`android/`) adds the parts a layout file can't do:

- **Sym + B** toggles an on-screen emoji panel. The rest of the time there's no on-screen keyboard, like "No Keyboard".
- **Double-tap and hold Sym** acts as **Super** (the Windows key), e.g. for Hyprland shortcuts through Moonlight.
- It includes this layout, so ExKeyMo isn't needed anymore.

## Layout Matrix

### Row 1 (System/Top Keys)

> **Note:** Editing these keys requires root to modify the `.kl` file. Otherwise, use **Settings → Shortcut keys** for basic remapping.

- **Shift, Back, App_Switch, ALT:** Default
- **Sym:** Set to "Show/Hide on-screen keyboard" (layer modifier). Double-tap and hold = Super (needs the app).
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
- **Sym:** [=, —, —, —, SEARCH, EMOJI, HOME, END]

---

## Titan2 Keys app

### Build

```sh
./android/build.sh            # -> titan2-keys.apk in the repo root
./android/build.sh --install  # same, then installs over adb
```

The first run downloads a JDK 17, the Android SDK and Gradle into `android/.toolchain/` (no root; delete the folder to remove them). It asks you to accept the Android SDK license once. If you already have `JAVA_HOME` / `ANDROID_HOME` set, it uses those.

### Set up on the phone

1. Install `titan2-keys.apk` and open **Titan2 Keys**. It has a button for each step and shows which ones are done.
2. **Physical keyboard layout:** Settings → System → Languages & input → Physical keyboard → pick **Titan 2 Custom** (the one from Titan2 Keys). On newer Android the layout is chosen per keyboard app, so pick it again after switching to Titan2 Emoji.
3. **Keyboard:** enable **Titan2 Emoji** and switch to it (it replaces "No Keyboard").
4. **Super key:** Settings → Accessibility → **Titan2 Super key** → on. If it's greyed out ("restricted setting"), go to App info → ⋮ → **Allow restricted settings** first. Android does this for every sideloaded accessibility app.

### Emoji panel (Sym + B)

- Tap an emoji to insert it. The panel stays open so you can pick several.
- Or type to search by name (`fire`, `thumbs up`). **Enter** inserts the highlighted one and closes the panel.
- **Sym+H/J/K/L** move the highlight, **Sym+A** (Tab) switches category, **Sym+Q** (Esc) clears the search, **Backspace** edits it.
- **Sym+B** or **Back** closes the panel. Recently used emoji get their own tab.

In Moonlight, typing goes to the stream, so use touch there.

### Super key (double-tap and hold Sym)

Tap Sym, then press and hold it. You'll feel a tick. While you hold it, keys are sent as Super + key:

| You press (while holding)  | Sent              | Omarchy           |
| -------------------------- | ----------------- | ----------------- |
| Enter                      | Super+Return      | Terminal          |
| Space                      | Super+Space       | Launcher          |
| W / Q                      | Super+W / Q       | Close window      |
| Alt+W, Alt+E, … (1, 2, …)  | Super+1, Super+2  | Workspaces        |
| Shift+Alt+W                | Super+Shift+1     | Move to workspace |
| Alt+Space / Alt+Enter      | Super+Alt+Space/Return | Menu / Tmux |
| Shift+B, Shift+N, …        | Super+Shift+B, …  | App launchers     |

Rules:
- The Sym layer is undone. Holding Sym for Super doesn't turn Enter into Insert or H into ←: you get Super+Enter and Super+H.
- Alt picks a character as usual (Alt+W = 1 → Super+1). Where Alt doesn't change the character (Enter, Space), it's sent as a real Alt.
- Press Super first, then the modifiers.

Limits:
- No Super+arrows, since H/J/K/L are letters while Super is held. To get Super+← … → instead, delete the four `DPAD_*` lines in `SYM_LAYER_KEYS` in `SuperKeyService.kt`.
- No Super+Alt+letter, since Alt turns letters into digits/symbols.

How it works: Moonlight reads keys before any on-screen keyboard can (`StreamView.onKeyPreIme`), so the accessibility service has to take the keys during a Super hold. It sends them through the Titan2 Emoji keyboard, which delivers them straight to the app. That also means Android's own Meta shortcuts don't steal Super+B and the like. Keys pressed outside a Super hold are never touched.

**If it doesn't trigger:** open Titan2 Keys and press Sym in the test field. If the log never shows `service saw SYM`, the phone is handling Sym itself before apps get it. Try changing Sym under **Settings → Shortcut keys**.

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
