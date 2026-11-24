package dev.titan2.keys

import android.content.Context
import android.view.KeyEvent

/**
 * The controller layer's bindings (Sym+C turns the layer on): which keyboard key presses
 * which gamepad button. Edited in [ControllerActivity], used by [SuperKeyService]; both
 * run in the same process and share the cached copy here.
 *
 * Stored per key (key code → button code), since that's what a key press looks up.
 * A button can have several keys; a key presses one button.
 */
object ControllerBindings {

    /** The gamepad buttons, in the order the editor lists them. */
    val BUTTONS: Map<Int, String> = linkedMapOf(
        KeyEvent.KEYCODE_BUTTON_A to "A",
        KeyEvent.KEYCODE_BUTTON_B to "B",
        KeyEvent.KEYCODE_BUTTON_X to "X",
        KeyEvent.KEYCODE_BUTTON_Y to "Y",
        KeyEvent.KEYCODE_DPAD_UP to "D-pad ↑",
        KeyEvent.KEYCODE_DPAD_DOWN to "D-pad ↓",
        KeyEvent.KEYCODE_DPAD_LEFT to "D-pad ←",
        KeyEvent.KEYCODE_DPAD_RIGHT to "D-pad →",
        KeyEvent.KEYCODE_BUTTON_L1 to "LB",
        KeyEvent.KEYCODE_BUTTON_R1 to "RB",
        KeyEvent.KEYCODE_BUTTON_L2 to "LT",
        KeyEvent.KEYCODE_BUTTON_R2 to "RT",
        KeyEvent.KEYCODE_BUTTON_START to "Start",
        KeyEvent.KEYCODE_BUTTON_SELECT to "Select",
        KeyEvent.KEYCODE_BUTTON_THUMBL to "L3",
        KeyEvent.KEYCODE_BUTTON_THUMBR to "R3",
        KeyEvent.KEYCODE_BUTTON_MODE to "Guide",
    )

    /**
     * Left thumb: WASD is the D-pad, Q/E are LB/LT. Right thumb: I/J/K/L are Y/X/A/B
     * (the face-button diamond), U/O are RB/RT. Space is A too, Enter is Start,
     * Z is Select, X and M click the sticks.
     */
    val DEFAULTS: Map<Int, Int> = linkedMapOf(
        KeyEvent.KEYCODE_W to KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_A to KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_S to KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_D to KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_Q to KeyEvent.KEYCODE_BUTTON_L1,
        KeyEvent.KEYCODE_E to KeyEvent.KEYCODE_BUTTON_L2,
        KeyEvent.KEYCODE_I to KeyEvent.KEYCODE_BUTTON_Y,
        KeyEvent.KEYCODE_J to KeyEvent.KEYCODE_BUTTON_X,
        KeyEvent.KEYCODE_K to KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_L to KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_U to KeyEvent.KEYCODE_BUTTON_R1,
        KeyEvent.KEYCODE_O to KeyEvent.KEYCODE_BUTTON_R2,
        KeyEvent.KEYCODE_SPACE to KeyEvent.KEYCODE_BUTTON_A,
        KeyEvent.KEYCODE_ENTER to KeyEvent.KEYCODE_BUTTON_START,
        KeyEvent.KEYCODE_Z to KeyEvent.KEYCODE_BUTTON_SELECT,
        KeyEvent.KEYCODE_X to KeyEvent.KEYCODE_BUTTON_THUMBL,
        KeyEvent.KEYCODE_M to KeyEvent.KEYCODE_BUTTON_THUMBR,
    )

    /** Keys that keep their job in the controller layer: Sym (Sym+C turns it off), Home (Super), system keys. */
    private val RESERVED = setOf(
        KeyEvent.KEYCODE_UNKNOWN, KeyEvent.KEYCODE_SYM, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_APP_SWITCH, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_MENU,
        SuperKeyService.SUPER_KEY_CODE,
    )

    /** The Titan 2's keys in keyboard order, for listing a button's keys the way they're laid out. */
    private val ORDER = "QWERTYUIOPASDFGHJKL".map { KeyEvent.keyCodeFromString("KEYCODE_$it") } +
        KeyEvent.KEYCODE_ENTER +
        "ZXCV".map { KeyEvent.keyCodeFromString("KEYCODE_$it") } +
        KeyEvent.KEYCODE_SPACE +
        "BNM".map { KeyEvent.keyCodeFromString("KEYCODE_$it") }

    private const val PREFS = "controller"
    private const val KEY_BINDINGS = "bindings"

    @Volatile
    private var cache: Map<Int, Int>? = null

    fun canBind(code: Int) = code !in RESERVED

    /** Key code → the button it presses. */
    fun get(context: Context): Map<Int, Int> = cache ?: load(context).also { cache = it }

    /** The keys that press [button], in keyboard order. */
    fun keysFor(bindings: Map<Int, Int>, button: Int): List<Int> =
        bindings.filterValues { it == button }.keys
            .sortedBy { key -> ORDER.indexOf(key).takeIf { it >= 0 } ?: (1000 + key) }

    /** Makes [key] the only key for [button] (taking it off any other button it had). */
    fun setKey(context: Context, button: Int, key: Int) {
        val bindings = LinkedHashMap(get(context))
        bindings.values.removeAll { it == button }
        bindings[key] = button
        save(context, bindings)
    }

    fun clear(context: Context, button: Int) {
        val bindings = LinkedHashMap(get(context))
        bindings.values.removeAll { it == button }
        save(context, bindings)
    }

    fun reset(context: Context) {
        cache = DEFAULTS
        prefs(context).edit().remove(KEY_BINDINGS).apply()
    }

    /** A keyboard key's name: "W", "Space", "Shift". */
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

    /** A key's name short enough to print on a button: "K", "Spc", "Shift". */
    fun shortName(code: Int): String = when (code) {
        KeyEvent.KEYCODE_SPACE -> "Spc"
        KeyEvent.KEYCODE_ENTER -> "Ent"
        KeyEvent.KEYCODE_DEL -> "Bksp"
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> "Shift"
        KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> "Alt"
        KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> "Ctrl"
        KeyEvent.KEYCODE_VOLUME_UP -> "Vol+"
        KeyEvent.KEYCODE_VOLUME_DOWN -> "Vol−"
        else -> keyName(code)
    }

    /** What the drawn controller prints on each bound button: "K·Spc". */
    fun buttonLabels(bindings: Map<Int, Int>): Map<Int, String> = BUTTONS.keys.mapNotNull { button ->
        val keys = keysFor(bindings, button)
        if (keys.isEmpty()) null
        else button to (keys.take(2).joinToString("·", transform = ::shortName) + if (keys.size > 2) "+" else "")
    }.toMap()

    private fun save(context: Context, bindings: Map<Int, Int>) {
        cache = bindings
        val encoded = bindings.entries.joinToString(",") { (key, button) -> "$key=$button" }
        prefs(context).edit().putString(KEY_BINDINGS, encoded).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Stored as "51=19,29=21,…" (key code = button code).
    private fun load(context: Context): Map<Int, Int> {
        val stored = prefs(context).getString(KEY_BINDINGS, null) ?: return DEFAULTS
        val out = LinkedHashMap<Int, Int>()
        for (entry in stored.split(',')) {
            val key = entry.substringBefore('=').toIntOrNull() ?: continue
            // The first version stored "p<code>" (and "k<code>" for keyboard keys, now gone).
            val button = entry.substringAfter('=', "").removePrefix("p").toIntOrNull() ?: continue
            if (button in BUTTONS) out[key] = button
        }
        return out
    }
}
