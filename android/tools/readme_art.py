#!/usr/bin/env python3
"""Draws the README pictures: docs/controller.svg and docs/layout.svg.

controller.svg is the app's controller screen (GamepadView.kt, same 1000 x 640 geometry)
with the default keys from ControllerBindings.kt. layout.svg is the built-in layout, read
from titan2-custom.kcm, drawn like the app's keyboard (KeyboardView.kt). Re-run after changing either:  python3 android/tools/readme_art.py
"""
from pathlib import Path
from xml.sax.saxutils import escape

DOCS = Path(__file__).resolve().parents[2] / "docs"

BG, CARD, RAISED, STROKE = "#0C0E13", "#151820", "#1E222D", "#272C39"
TEXT, MUTED, PINK, VIOLET = "#F3F5F9", "#8A93A6", "#FF4D8D", "#8B5CF6"
GREEN, RED, BLUE, YELLOW, ORANGE = "#3DDC84", "#FF5370", "#4F9DFF", "#FFC947", "#FF9F43"
FONT = "Inter, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif"


def text(s, x, y, size, color, weight=700, max_width=None, anchor="middle"):
    if max_width:  # rough fit, like GamepadView.text()
        width = len(s) * size * 0.62
        if width > max_width:
            size *= max_width / width
    return (f'<text x="{x:.1f}" y="{y:.1f}" dy="0.36em" font-family="{FONT}" font-size="{size:.1f}" '
            f'font-weight="{weight}" fill="{color}" text-anchor="{anchor}">{escape(s)}</text>')


# ---- controller ----

BODY = ("M330 150 L670 150 C800 140 885 170 930 290 C980 420 995 565 910 615 C845 650 785 600 745 550 "
        "C715 515 665 505 500 505 C335 505 285 515 255 550 C215 600 155 650 90 615 "
        "C5 565 20 420 70 290 C115 170 200 140 330 150 Z")

# Default keys (ControllerBindings.DEFAULTS), as the app prints them.
KEYS = {"LT": "E", "RT": "O", "LB": "Q", "RB": "U", "L3": "X", "R3": "M",
        "Up": "W", "Down": "S", "Left": "A", "Right": "D",
        "Y": "I", "A": "K·Spc", "X": "J", "B": "L", "SELECT": "Z", "START": "Ent"}


def controller():
    out = []
    add = out.append
    add('<defs>'
        '<linearGradient id="body" x1="0" y1="150" x2="0" y2="630" gradientUnits="userSpaceOnUse">'
        '<stop offset="0" stop-color="#2D3344"/><stop offset="1" stop-color="#15181F"/></linearGradient>'
        '<radialGradient id="sheen" cx="500" cy="140" r="520" gradientUnits="userSpaceOnUse">'
        '<stop offset="0" stop-color="#fff" stop-opacity="0.12"/><stop offset="1" stop-color="#fff" stop-opacity="0"/></radialGradient>'
        '<radialGradient id="shadow" cx="500" cy="610" r="430" gradientUnits="userSpaceOnUse">'
        '<stop offset="0" stop-color="#000" stop-opacity="0.55"/><stop offset="1" stop-color="#000" stop-opacity="0"/></radialGradient>'
        f'<linearGradient id="accent" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="{PINK}"/>'
        f'<stop offset="1" stop-color="{VIOLET}"/></linearGradient>'
        '</defs>')
    add('<ellipse cx="500" cy="610" rx="390" ry="40" fill="url(#shadow)"/>')

    # Shoulders, behind the body.
    for name, cx in (("LT", 235), ("RT", 765)):
        add(f'<rect x="{cx - 75}" y="47" width="150" height="62" rx="26" fill="#252A37" stroke="#3B4254" stroke-width="2"/>')
        add(text(name, cx, 66, 15, MUTED))
        add(text(KEYS[name], cx, 91, 24, TEXT, max_width=130))
    for name, cx in (("LB", 245), ("RB", 755)):
        add(f'<rect x="{cx - 110}" y="115" width="220" height="38" rx="19" fill="#2C3242" stroke="#3B4254" stroke-width="2"/>')
        add(text(name, cx - 62, 134, 15, MUTED))
        add(text(KEYS[name], cx + 18, 134, 21, TEXT, max_width=120))

    add(f'<path d="{BODY}" fill="url(#body)"/>')
    add(f'<path d="{BODY}" fill="url(#sheen)"/>')
    add(f'<path d="{BODY}" fill="none" stroke="#3B4254" stroke-width="3"/>')
    add('<rect x="435" y="158" width="130" height="24" rx="12" fill="#FF4D8D" fill-opacity="0.18"/>')
    add('<rect x="445" y="166" width="110" height="8" rx="4" fill="url(#accent)"/>')

    # D-pad.
    add('<rect x="362" y="332" width="46" height="156" rx="10" fill="#252A37"/>')
    add('<rect x="307" y="387" width="156" height="46" rx="10" fill="#252A37"/>')
    add('<circle cx="385" cy="410" r="11" fill="#1B1F29"/>')
    for name, cx, cy, w in (("Up", 385, 360, 46), ("Down", 385, 460, 46), ("Left", 335, 410, 56), ("Right", 435, 410, 56)):
        add(text(KEYS[name], cx, cy, 21, TEXT, max_width=w - 6))

    # Sticks.
    for name, cx, cy, r in (("L3", 250, 282, 58), ("R3", 615, 410, 54)):
        add(f'<circle cx="{cx}" cy="{cy}" r="{r + 14}" fill="#0F1218" stroke="#2A2F3D" stroke-width="2"/>')
        add(f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="#2E3445"/>')
        add(f'<circle cx="{cx}" cy="{cy}" r="{r - 9}" fill="none" stroke="#454D61" stroke-width="6"/>')
        add(text(KEYS[name], cx, cy - 6, 26, TEXT, max_width=r * 1.4))
        add(text(name, cx, cy + 24, 13, MUTED))

    # Face buttons.
    for name, cx, cy, color in (("Y", 750, 214, YELLOW), ("A", 750, 350, GREEN), ("X", 682, 282, BLUE), ("B", 818, 282, RED)):
        add(f'<circle cx="{cx}" cy="{cy}" r="32" fill="#1B1F2A" stroke="{color}" stroke-opacity="0.9" stroke-width="3"/>')
        add(text(KEYS[name], cx, cy, 28, color, weight=900, max_width=51))

    # Select, Start, Guide.
    for name, cx in (("SELECT", 432), ("START", 568)):
        add(f'<circle cx="{cx}" cy="300" r="20" fill="#262B38" stroke="#3B4254" stroke-width="2"/>')
        add(text(KEYS[name], cx, 300, 17, TEXT, max_width=34))
        add(text(name, cx, 337, 12, MUTED))
    add(f'<circle cx="500" cy="222" r="31" fill="#231F35" stroke="{VIOLET}" stroke-opacity="0.9" stroke-width="3"/>')
    add('<g stroke="#F3F5F9" stroke-opacity="0.55" stroke-width="3" fill="none">'
        '<circle cx="500" cy="222" r="14"/><path d="M493 215 L507 229 M507 215 L493 229"/></g>')

    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="1000" height="680" viewBox="0 -20 1000 680">'
           f'<rect x="0" y="-20" width="1000" height="680" rx="36" fill="{BG}"/>'
           + "".join(out) + '</svg>\n')
    (DOCS / "controller.svg").write_text(svg)


# ---- keyboard layout ----

KCM = Path(__file__).resolve().parents[2] / "titan2-custom.kcm"

# The Titan 2's typing keys as they sit on the phone (KeyLayout.ROWS), under the
# Shift / Sym / Back / Recent / Fn / Alt row.
ROWS = [list("QWERTYUIOP"), list("ASDFGHJKL") + ["DEL"], list("ZXCV") + ["SPACE"] + list("BNM") + ["ENTER"]]
MODS = [("⇧", BLUE), ("sym", GREEN), ("◁", MUTED), ("□", MUTED), ("fn", MUTED), ("alt", ORANGE)]

# What the app prints for keys a layer presses (KeyLayout.capLabel).
KEY_LABELS = {"DPAD_LEFT": "←", "DPAD_RIGHT": "→", "DPAD_UP": "↑", "DPAD_DOWN": "↓", "TAB": "Tab",
              "ESCAPE": "Esc", "MOVE_HOME": "Home", "MOVE_END": "End", "INSERT": "Ins", "SEARCH": "Search",
              "PICTSYMBOLS": "Emoji", "PAGE_UP": "PgUp", "PAGE_DOWN": "PgDn", "FORWARD_DEL": "Del", "DEL": "⌫"}
CHAR_LABELS = {"\x1b": "Esc", "\n": "⏎", "\t": "Tab", " ": "␣"}


def kcm_char(lit):
    body = lit[1:-1]
    if body.startswith("\\u"):
        return chr(int(body[2:6], 16))
    return {"\\n": "\n", "\\t": "\t", "\\\\": "\\", "\\'": "'", '\\"': '"'}.get(body, body)


def read_kcm():
    """{key: {layer: label}} for base, shift, alt and sym, like KeyLayout.parseKcm."""
    keys, key = {}, None
    for raw in KCM.read_text().splitlines():
        line, quoted, i = raw, False, 0
        while i < len(line):  # strip a # comment, but not a '#' character
            c = line[i]
            if quoted and c == "\\":
                i += 1
            elif c == "'":
                quoted = not quoted
            elif c == "#" and not quoted:
                line = line[:i]
                break
            i += 1
        line = line.strip()
        if line.startswith("key ") and line.endswith("{"):
            key = keys.setdefault(line.split()[1], {})
        elif line == "}":
            key = None
        elif key is not None and ":" in line:
            props, value = line.split(":", 1)
            value = value.strip()
            if value.startswith("'"):
                ch = kcm_char(value[:value.index("'", 2 if value[1] != "\\" else 3) + 1])
                label = CHAR_LABELS.get(ch, ch)
            elif value.startswith("replace"):
                name = value.split()[1]
                label = KEY_LABELS.get(name, name)
            else:
                continue
            for prop in props.split(","):
                if prop.strip() in ("base", "shift", "alt", "sym"):
                    key[prop.strip()] = label
    return keys


def layout():
    kcm = read_kcm()
    kcm.setdefault("C", {}).setdefault("sym", "Pad")  # Sym+C, the app's controller toggle
    kcm.setdefault("DEL", {}).setdefault("base", "⌫")
    pad, gap, key_h, mod_h = 28, 10, 96, 52
    inner = 1000
    width = inner + 2 * pad
    unit = (inner - 9 * gap) / 10
    out = []
    mod_w = (inner - 5 * gap) / 6
    for i, (label, color) in enumerate(MODS):
        x = pad + i * (mod_w + gap)
        out.append(f'<rect x="{x:.1f}" y="{pad}" width="{mod_w:.1f}" height="{mod_h}" rx="14" fill="#161920" stroke="#262B37" stroke-width="2"/>')
        out.append(text(label, x + mod_w / 2, pad + mod_h / 2, 24, color, max_width=mod_w - 20))
    y = pad + mod_h + 18
    for row in ROWS:
        x = pad
        for name in row:
            w = unit * 2 + gap if name == "SPACE" else unit
            layers = kcm.get(name, {})
            out.append(f'<rect x="{x:.1f}" y="{y + 5}" width="{w:.1f}" height="{key_h}" rx="16" fill="#07080B"/>')
            out.append(f'<rect x="{x:.1f}" y="{y}" width="{w:.1f}" height="{key_h}" rx="16" fill="#252A37" stroke="#3A4152" stroke-width="2"/>')
            base, shift = layers.get("base", ""), layers.get("shift")
            letter = len(base) == 1 and shift == base.upper() and shift != base
            if name == "SPACE":
                out.append(f'<rect x="{x + w / 2 - 38:.1f}" y="{y + key_h * 0.62 - 3:.1f}" width="76" height="6" rx="3" fill="{TEXT}"/>')
            else:
                label = shift if letter else base
                out.append(text(label, x + w / 2, y + key_h * 0.6, 38 if len(label) == 1 else 26, TEXT, max_width=w - 18))
            if shift and not letter:
                out.append(text(shift, x + 11, y + key_h - 17, 17, BLUE, max_width=w / 2 - 8, anchor="start"))
            corner = (w - 2 * 11 - 8) / 2  # each top corner gets half the key, with a gap between
            if layers.get("alt", base) != base:  # Alt+Space is just a space: nothing to show
                out.append(text(layers["alt"], x + 11, y + 20, 19, ORANGE, max_width=corner, anchor="start"))
            if layers.get("sym", base) != base:
                color = PINK if layers["sym"] == "Pad" else GREEN
                out.append(text(layers["sym"], x + w - 11, y + 20, 19, color, max_width=corner, anchor="end"))
            x += w + gap
        y += key_h + gap
    y += 22
    legend = [("Alt", ORANGE), ("Sym", GREEN), ("Sym+C controller", PINK)]
    lx = pad
    for name, color in legend:
        out.append(f'<rect x="{lx}" y="{y - 7}" width="14" height="14" rx="4" fill="{color}"/>')
        out.append(text(name, lx + 22, y, 16, MUTED, weight=600, anchor="start"))
        lx += 34 + len(name) * 10
    height = y + 30
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">'
           f'<rect width="{width}" height="{height}" rx="28" fill="{BG}"/>' + "".join(out) + '</svg>\n')
    (DOCS / "layout.svg").write_text(svg)


if __name__ == "__main__":
    DOCS.mkdir(exist_ok=True)
    controller()
    layout()
    print("wrote", DOCS / "controller.svg", "and", DOCS / "layout.svg")
