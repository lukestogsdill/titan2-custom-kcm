# Titan 2 Custom Keyboard Layout

Optimized physical keyboard layout for the Titan 2. Features a custom Sym layer for navigation, additional symbols, and system keys, allowing you to disable the virtual keyboard entirely without needing root access.

## Layout Matrix

### Row 1 (System/Top Keys)

> **Note:** Editing these keys requires root to modify the `.kl` file. Otherwise, use **Settings → Shortcut keys** for basic remapping.

- **Shift, Back, App_Switch, ALT:** Default
- **Sym:** Set to "Show/Hide on-screen keyboard" (layer modifier)
- **Fn:** Set to Control via settings

---

### Row 2 (Top Row)
- **Base:** [q, w, e, r, t, y, u, i, o, p]
- **Alt:** [0, 1, 2, 3, (, ), -, _, /, :]
- **Sym:** [ESC, ~, $, &, {, }, [, ], , ;]

### Row 3 (Home Row)
- **Base:** [a, s, d, f, g, h, j, k, l, Enter]
- **Alt:** [@, 4, 5, 6, *, #, +, ", ']
- **Sym:** [TAB, %, ^, <, >, ←, ↓, ↑, →, INSERT]

### Row 4 (Bottom Row)
- **Base:** [z, x, c, v, Space, b, n, m]
- **Alt:** [!, 7, 8, 9, ., ,, ?]
- **Sym:** [=, —, —, —, SEARCH, —, HOME, END]

---

## Installation (No Root Required)

### Choose Your Path

- **Default:** Just install `keyboard.apk` on your device.
- **Custom:** To modify the layout, follow the exkeymo-server instructions: run the server, visit localhost in your browser, paste your `titan2-custom.kcm`, and compile a new APK.

### Activation

1. Go to **Settings → System → Languages & input → Physical keyboard**
2. Select your physical keyboard
3. For the KCM provider, you must select **"Other"**
4. Choose your installed Exkeymo layout

### Hardware Key Setup

1. Go to **Settings → Shortcut keys**
2. Set **Fn** to **Control**
3. In **Physical Keyboard** settings, toggle **"Show virtual keyboard"** to **OFF**
