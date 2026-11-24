package dev.titan2.keys

import android.content.Context
import android.view.KeyEvent

/** What a key sends in the controller layer: a gamepad button ([pad]) or a keyboard key. */
data class PadTarget(val pad: Boolean, val code: Int) {
    val label: String
        get() = if (pad) "🎮 " + (ControllerBindings.PAD_BUTTONS[code] ?: code.toString())
        else "⌨ " + (ControllerBindings.KEYS[code] ?: ControllerBindings.keyName(code))
}

/**
 * The controller layer's key bindings (Sym+C turns the layer on). Edited in
 * [ControllerActivity], used by [SuperKeyService]; both run in the same process and
 * share the cached copy here.
 */
object ControllerBindings {

    /** The gamepad buttons a key can be, in the order the picker lists them. */
    val PAD_BUTTONS: Map<Int, String> = linkedMapOf(
        KeyEvent.KEYCODE_BUTTON_A to "A",
        KeyEvent.KEYCODE_BUTTON_B to "B",
        KeyEvent.KEYCODE_BUTTON_X to "X",
        KeyEvent.KEYCODE_BUTTON_Y to "Y",
        KeyEvent.KEYCODE_BUTTON_L1 to "LB",
        KeyEvent.KEYCODE_BUTTON_R1 to "RB",
        KeyEvent.KEYCODE_BUTTON_L2 to "LT",
        KeyEvent.KEYCODE_BUTTON_R2 to "RT",
        KeyEvent.KEYCODE_DPAD_UP to "D-pad ↑",
        KeyEvent.KEYCODE_DPAD_DOWN to "D-pad ↓",
        KeyEvent.KEYCODE_DPAD_LEFT to "D-pad ←",
        KeyEvent.KEYCODE_DPAD_RIGHT to "D-pad →",
        KeyEvent.KEYCODE_BUTTON_START to "Start (Menu)",
        KeyEvent.KEYCODE_BUTTON_SELECT to "Select (View)",
        KeyEvent.KEYCODE_BUTTON_THUMBL to "L3 (left stick click)",
        KeyEvent.KEYCODE_BUTTON_THUMBR to "R3 (right stick click)",
        KeyEvent.KEYCODE_BUTTON_MODE to "Guide (Xbox button)",
    )

    /** The keyboard keys a key can be instead, for games that also want a few keys. */
    val KEYS: Map<Int, String> = LinkedHashMap<Int, String>().apply {
        put(KeyEvent.KEYCODE_ESCAPE, "Esc")
        put(KeyEvent.KEYCODE_TAB, "Tab")
        put(KeyEvent.KEYCODE_ENTER, "Enter")
        put(KeyEvent.KEYCODE_SPACE, "Space")
        put(KeyEvent.KEYCODE_DEL, "Backspace")
        put(KeyEvent.KEYCODE_DPAD_UP, "↑")
        put(KeyEvent.KEYCODE_DPAD_DOWN, "↓")
        put(KeyEvent.KEYCODE_DPAD_LEFT, "←")
        put(KeyEvent.KEYCODE_DPAD_RIGHT, "→")
        put(KeyEvent.KEYCODE_SHIFT_LEFT, "Shift")
        put(KeyEvent.KEYCODE_CTRL_LEFT, "Ctrl")
        put(KeyEvent.KEYCODE_ALT_LEFT, "Alt")
        for (i in 0 until 26) put(KeyEvent.KEYCODE_A + i, ('A' + i).toString())
        for (i in 0 until 10) put(KeyEvent.KEYCODE_0 + i, i.toString())
        for (i in 0 until 12) put(KeyEvent.KEYCODE_F1 + i, "F${i + 1}")
    }

    /** Every target, in picker order: gamepad buttons first. */
    val TARGETS: List<PadTarget> =
        PAD_BUTTONS.keys.map { PadTarget(true, it) } + KEYS.keys.map { PadTarget(false, it) }

    /**
     * Left thumb: WASD is the D-pad, Q/E are LB/LT. Right thumb: I/J/K/L are Y/X/A/B
     * (the face-button diamond), U/O are RB/RT. Space is A too, Enter is Start,
     * Z is Select, X and M click the sticks.
     */
    val DEFAULTS: Map<Int, PadTarget> = linkedMapOf(
        KeyEvent.KEYCODE_W to pad(KeyEvent.KEYCODE_DPAD_UP),
        KeyEvent.KEYCODE_A to pad(KeyEvent.KEYCODE_DPAD_LEFT),
        KeyEvent.KEYCODE_S to pad(KeyEvent.KEYCODE_DPAD_DOWN),
        KeyEvent.KEYCODE_D to pad(KeyEvent.KEYCODE_DPAD_RIGHT),
        KeyEvent.KEYCODE_Q to pad(KeyEvent.KEYCODE_BUTTON_L1),
        KeyEvent.KEYCODE_E to pad(KeyEvent.KEYCODE_BUTTON_L2),
        KeyEvent.KEYCODE_I to pad(KeyEvent.KEYCODE_BUTTON_Y),
        KeyEvent.KEYCODE_J to pad(KeyEvent.KEYCODE_BUTTON_X),
        KeyEvent.KEYCODE_K to pad(KeyEvent.KEYCODE_BUTTON_A),
        KeyEvent.KEYCODE_L to pad(KeyEvent.KEYCODE_BUTTON_B),
        KeyEvent.KEYCODE_U to pad(KeyEvent.KEYCODE_BUTTON_R1),
        KeyEvent.KEYCODE_O to pad(KeyEvent.KEYCODE_BUTTON_R2),
        KeyEvent.KEYCODE_SPACE to pad(KeyEvent.KEYCODE_BUTTON_A),
        KeyEvent.KEYCODE_ENTER to pad(KeyEvent.KEYCODE_BUTTON_START),
        KeyEvent.KEYCODE_Z to pad(KeyEvent.KEYCODE_BUTTON_SELECT),
        KeyEvent.KEYCODE_X to pad(KeyEvent.KEYCODE_BUTTON_THUMBL),
        KeyEvent.KEYCODE_M to pad(KeyEvent.KEYCODE_BUTTON_THUMBR),
    )

    /** Keys that keep their job in the controller layer: Sym (Sym+C turns it off), Home (Super), system keys. */
    private val RESERVED = setOf(
        KeyEvent.KEYCODE_UNKNOWN, KeyEvent.KEYCODE_SYM, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_APP_SWITCH, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_MENU,
        SuperKeyService.SUPER_KEY_CODE,
    )

    /** The Titan 2's keys in keyboard order, for listing bindings the way they're laid out. */
    private val ORDER = "QWERTYUIOPASDFGHJKL".map { KeyEvent.keyCodeFromString("KEYCODE_$it") } +
        KeyEvent.KEYCODE_ENTER +
        "ZXCV".map { KeyEvent.keyCodeFromString("KEYCODE_$it") } +
        KeyEvent.KEYCODE_SPACE +
        "BNM".map { KeyEvent.keyCodeFromString("KEYCODE_$it") } +
        listOf(KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT,
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT, KeyEvent.KEYCODE_CTRL_LEFT,
            KeyEvent.KEYCODE_CTRL_RIGHT)

    private const val PREFS = "controller"
    private const val KEY_BINDINGS = "bindings"

    @Volatile
    private var cache: Map<Int, PadTarget>? = null

    fun canBind(code: Int) = code !in RESERVED

    fun get(context: Context): Map<Int, PadTarget> = cache ?: load(context).also { cache = it }

    fun set(context: Context, bindings: Map<Int, PadTarget>) {
        cache = LinkedHashMap(bindings)
        prefs(context).edit().putString(KEY_BINDINGS, encode(bindings)).apply()
    }

    fun reset(context: Context) {
        cache = DEFAULTS
        prefs(context).edit().remove(KEY_BINDINGS).apply()
    }

    /** [bindings] in keyboard order. */
    fun sorted(bindings: Map<Int, PadTarget>): List<Pair<Int, PadTarget>> =
        bindings.entries.sortedBy { (key, _) -> ORDER.indexOf(key).takeIf { it >= 0 } ?: (1000 + key) }
            .map { it.key to it.value }

    /** A physical key's name: "W", "Space", "Shift". */
    fun keyName(code: Int): String = when (code) {
        KeyEvent.KEYCODE_SPACE -> "Space"
        KeyEvent.KEYCODE_ENTER -> "Enter"
        KeyEvent.KEYCODE_DEL -> "Backspace"
        KeyEvent.KEYCODE_SHIFT_LEFT -> "Shift"
        KeyEvent.KEYCODE_SHIFT_RIGHT -> "Right Shift"
        KeyEvent.KEYCODE_ALT_LEFT -> "Alt"
        KeyEvent.KEYCODE_ALT_RIGHT -> "Right Alt"
        KeyEvent.KEYCODE_CTRL_LEFT -> "Ctrl"
        KeyEvent.KEYCODE_CTRL_RIGHT -> "Right Ctrl"
        KeyEvent.KEYCODE_VOLUME_UP -> "Volume +"
        KeyEvent.KEYCODE_VOLUME_DOWN -> "Volume −"
        else -> KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_").replace('_', ' ')
    }

    private fun pad(code: Int) = PadTarget(true, code)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Stored as "51=p19,29=p21,…": key code = p(ad)/k(ey) + target code.
    private fun encode(bindings: Map<Int, PadTarget>) = bindings.entries.joinToString(",") { (key, target) ->
        "$key=" + (if (target.pad) "p" else "k") + target.code
    }

    private fun load(context: Context): Map<Int, PadTarget> {
        val stored = prefs(context).getString(KEY_BINDINGS, null) ?: return DEFAULTS
        val out = LinkedHashMap<Int, PadTarget>()
        for (entry in stored.split(',')) {
            val key = entry.substringBefore('=').toIntOrNull() ?: continue
            val value = entry.substringAfter('=', "")
            val code = value.drop(1).toIntOrNull() ?: continue
            when (value.firstOrNull()) {
                'p' -> out[key] = PadTarget(true, code)
                'k' -> out[key] = PadTarget(false, code)
            }
        }
        return out
    }
}
