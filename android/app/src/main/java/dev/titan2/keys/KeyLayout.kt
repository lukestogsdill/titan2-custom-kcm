package dev.titan2.keys

import android.content.Context
import android.view.KeyEvent
import org.json.JSONObject
import java.util.EnumMap

/** One of a key's four layers: what it types alone, with Shift, with Alt and with Sym held. */
enum class Layer(val label: String, val color: Int) {
    BASE("Base", Ui.TEXT),
    SHIFT("Shift", Ui.BLUE),
    ALT("Alt", Ui.ORANGE),
    SYM("Sym", Ui.GREEN);

    /** Its name in a .kcm file and in the saved layout. */
    val id: String get() = name.lowercase()
}

/** What a key does on one layer. */
sealed interface Output {
    /** Types [text]: usually one character. Longer text is typed all at once. */
    data class Text(val text: String) : Output

    /** Presses another key: an arrow, Tab, Home… [code] is a KeyEvent keycode, or [KeyLayout.CONTROLLER]. */
    data class Key(val code: Int) : Output

    /** Does nothing. */
    data object None : Output
}

/**
 * Every key's outputs, layer by layer. A layer a key doesn't set falls back the way it does
 * in a .kcm file: Alt and Sym to Shift (while Shift is held) or Base, Shift to Base.
 */
class Keymap internal constructor(private val cells: Map<Int, Map<Layer, Output>>) {

    operator fun get(key: Int, layer: Layer): Output? = cells[key]?.get(layer)

    /** What [key] types on [layer], and the layer that comes from after falling back. */
    fun resolve(key: Int, layer: Layer, shift: Boolean): Pair<Layer, Output> {
        get(key, layer)?.let { return layer to it }
        if (shift && (layer == Layer.ALT || layer == Layer.SYM)) {
            get(key, Layer.SHIFT)?.let { return Layer.SHIFT to it }
        }
        return Layer.BASE to (get(key, Layer.BASE) ?: Output.Key(key))
    }

    /** Whether [layer] does something else than Base on [key]. Locks only apply where it does. */
    fun changes(key: Int, layer: Layer): Boolean {
        val out = get(key, layer) ?: return false
        return out != resolve(key, Layer.BASE, false).second
    }

    /** Whether caps lock gives [key] its Shift layer: when Shift types the capital of Base. */
    fun capsLocks(key: Int): Boolean {
        val base = (get(key, Layer.BASE) as? Output.Text)?.text ?: return false
        val shift = (get(key, Layer.SHIFT) as? Output.Text)?.text ?: return false
        return shift != base && shift == base.uppercase()
    }
}

/**
 * The keyboard layout: what every key types on every layer.
 *
 * The built-in layout is titan2-custom.kcm, which Android types from by itself. Changes made
 * in [LayoutActivity] are saved here as differences from it, and [SuperKeyService] types
 * them: it takes over a key press when the layout says something else than the .kcm would.
 * Both run in the same process and share the cached copy here.
 */
object KeyLayout {

    /** Not a real keycode: turns the controller layer on and off (Sym+C out of the box). */
    const val CONTROLLER = -100

    /** The Titan 2's typing keys, row by row as they sit on the phone. */
    val ROWS: List<List<Int>> = listOf(
        codes("QWERTYUIOP"),
        codes("ASDFGHJKL") + KeyEvent.KEYCODE_DEL,
        codes("ZXCV") + KeyEvent.KEYCODE_SPACE + codes("BNM") + KeyEvent.KEYCODE_ENTER,
    )

    val KEYS: List<Int> = ROWS.flatten()
    private val KEY_SET = KEYS.toHashSet()

    /** The keys the editor offers besides typing a character, in groups. */
    val ACTIONS: List<Pair<String, List<Output>>> = listOf(
        "Move" to keys(
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END, KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN,
        ),
        "Edit" to listOf(
            Output.Key(KeyEvent.KEYCODE_TAB), Output.Text(ESC), Output.Text("\n"), Output.Key(KeyEvent.KEYCODE_DEL),
            Output.Key(KeyEvent.KEYCODE_FORWARD_DEL), Output.Key(KeyEvent.KEYCODE_INSERT),
            Output.Key(KeyEvent.KEYCODE_CUT), Output.Key(KeyEvent.KEYCODE_COPY), Output.Key(KeyEvent.KEYCODE_PASTE),
        ),
        "Titan2 Keys" to listOf(
            Output.Key(KeyEvent.KEYCODE_PICTSYMBOLS), Output.Key(CONTROLLER), Output.Key(KeyEvent.KEYCODE_SEARCH), Output.None,
        ),
        "Function keys" to keys(*IntArray(12) { KeyEvent.KEYCODE_F1 + it }),
    )

    private const val ESC = "\u001b"
    private const val PREFS = "layout"
    private const val KEY_CHANGES = "changes"
    private const val SKIP = Int.MIN_VALUE
    private val KEY_LINE = Regex("""key\s+(\w+)\s*\{""")

    @Volatile
    private var builtIn: Keymap? = null

    /** For each keycode, the keys whose built-in layout presses it instead on some layer (← for H). */
    @Volatile
    private var swaps: Map<Int, List<Int>>? = null

    @Volatile
    private var changes: Map<Int, Map<Layer, Output?>>? = null

    @Volatile
    private var cache: Keymap? = null

    fun isKey(code: Int) = code in KEY_SET

    /** The layout in use: the built-in one with your changes. */
    fun get(context: Context): Keymap = cache ?: build(context).also { cache = it }

    /** The built-in layout: titan2-custom.kcm, plus Sym+C for the controller. */
    fun builtIn(context: Context): Keymap = builtIn ?: run {
        val text = context.resources.openRawResource(R.raw.titan2_custom).bufferedReader().use { it.readText() }
        val cells = fill(parseKcm(text))
        val c = cells.getValue(KeyEvent.KEYCODE_C)
        if (Layer.SYM !in c) c[Layer.SYM] = Output.Key(CONTROLLER)
        Keymap(cells).also { builtIn = it }
    }

    /**
     * The key that was pressed, or null if it isn't one of the [KEYS]. While a modifier is
     * held, the .kcm swaps some keys for another before anyone sees them (Sym+H arrives as ←);
     * this undoes that. [layer] is the one the held modifiers pick (Alt over Sym over Shift),
     * or null while Ctrl or Meta is held (the .kcm swaps nothing then).
     */
    fun physicalKey(context: Context, code: Int, layer: Layer?, shift: Boolean): Int? {
        if (layer != null) {
            val map = builtIn(context)
            for (key in swaps(context)[code].orEmpty()) {
                if (map.resolve(key, layer, shift).second == Output.Key(code)) return key
            }
        }
        return code.takeIf(::isKey)
    }

    // ---- changes ----

    fun isChanged(context: Context, key: Int, layer: Layer) = changes(context)[key]?.containsKey(layer) == true

    fun isChanged(context: Context, key: Int) = !changes(context)[key].isNullOrEmpty()

    fun changedKeys(context: Context): Set<Int> = changes(context).filterValues { it.isNotEmpty() }.keys

    /** Sets what [key] does on [layer]. Null makes the layer fall back (to Base, or Shift). */
    fun set(context: Context, key: Int, layer: Layer, output: Output?) {
        val all = editable(changes(context))
        val mine = all.getOrPut(key) { EnumMap(Layer::class.java) }
        if (output == builtIn(context)[key, layer]) mine.remove(layer) else mine[layer] = output
        if (mine.isEmpty()) all.remove(key)
        save(context, all)
    }

    fun resetKey(context: Context, key: Int) {
        val all = editable(changes(context))
        all.remove(key)
        save(context, all)
    }

    fun resetAll(context: Context) = save(context, emptyMap())

    private fun changes(context: Context): Map<Int, Map<Layer, Output?>> = changes ?: load(context).also { changes = it }

    private fun build(context: Context): Keymap {
        val cells = HashMap<Int, EnumMap<Layer, Output>>()
        val base = builtIn(context)
        val mine = changes(context)
        for (key in KEYS) {
            val row = EnumMap<Layer, Output>(Layer::class.java)
            for (layer in Layer.entries) base[key, layer]?.let { row[layer] = it }
            mine[key]?.forEach { (layer, output) -> if (output == null) row.remove(layer) else row[layer] = output }
            cells[key] = row
        }
        return Keymap(cells)
    }

    private fun editable(all: Map<Int, Map<Layer, Output?>>): HashMap<Int, EnumMap<Layer, Output?>> {
        val out = HashMap<Int, EnumMap<Layer, Output?>>()
        for ((key, layers) in all) {
            val row = EnumMap<Layer, Output?>(Layer::class.java)
            row.putAll(layers)
            out[key] = row
        }
        return out
    }

    private fun swaps(context: Context): Map<Int, List<Int>> = swaps ?: run {
        val map = builtIn(context)
        val out = HashMap<Int, MutableList<Int>>()
        for (key in KEYS) for (layer in Layer.entries) {
            val code = (map[key, layer] as? Output.Key)?.code ?: continue
            if (code >= 0 && key !in out.getOrPut(code) { ArrayList() }) out.getValue(code) += key
        }
        out.also { swaps = it }
    }

    // ---- saving ----

    // Saved as JSON: {"W": {"alt": "text:#", "sym": "same"}, "H": {"sym": "key:MOVE_HOME"}}.
    // "same" is a layer set to fall back, "none" does nothing.

    private fun save(context: Context, all: Map<Int, Map<Layer, Output?>>) {
        val json = JSONObject()
        for ((key, layers) in all) {
            if (layers.isEmpty()) continue
            val row = JSONObject()
            for ((layer, output) in layers) row.put(layer.id, encode(output))
            json.put(codeName(key), row)
        }
        changes = all
        cache = null
        prefs(context).edit().putString(KEY_CHANGES, json.toString()).apply()
    }

    private fun load(context: Context): Map<Int, Map<Layer, Output?>> {
        val stored = prefs(context).getString(KEY_CHANGES, null) ?: return emptyMap()
        val json = try {
            JSONObject(stored)
        } catch (e: Exception) {
            return emptyMap()
        }
        val out = HashMap<Int, Map<Layer, Output?>>()
        for (name in json.keys()) {
            val key = codeFromName(name)
            val row = json.optJSONObject(name)
            if (!isKey(key) || row == null) continue
            val layers = EnumMap<Layer, Output?>(Layer::class.java)
            for (layer in Layer.entries) {
                val value = row.optString(layer.id, "")
                if (value.isEmpty()) continue
                try {
                    layers[layer] = decode(value)
                } catch (e: IllegalArgumentException) {
                    // Unreadable (from a newer version?): leave that layer built-in.
                }
            }
            if (layers.isNotEmpty()) out[key] = layers
        }
        return out
    }

    private fun encode(output: Output?): String = when (output) {
        null -> "same"
        Output.None -> "none"
        is Output.Key -> "key:" + codeName(output.code)
        is Output.Text -> "text:" + output.text
    }

    private fun decode(value: String): Output? = when {
        value == "same" -> null
        value == "none" -> Output.None
        value.startsWith("text:") -> Output.Text(value.removePrefix("text:")).also { require(it.text.isNotEmpty()) }
        value.startsWith("key:") -> {
            val code = codeFromName(value.removePrefix("key:"))
            require(code != KeyEvent.KEYCODE_UNKNOWN)
            Output.Key(code)
        }
        else -> throw IllegalArgumentException(value)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** A keycode's name without KEYCODE_, as a .kcm writes it: "DPAD_LEFT". */
    fun codeName(code: Int): String =
        if (code == CONTROLLER) "CONTROLLER" else KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")

    private fun codeFromName(name: String): Int =
        if (name == "CONTROLLER") CONTROLLER else KeyEvent.keyCodeFromString("KEYCODE_$name")

    // ---- .kcm files ----

    /**
     * The layers a .kcm file sets for each of the [KEYS]: base, shift, alt and sym, each a
     * character, `none` or `replace KEY`. Labels, other modifiers and other keys are skipped.
     */
    fun parseKcm(text: String): Map<Int, Map<Layer, Output>> {
        val out = LinkedHashMap<Int, EnumMap<Layer, Output>>()
        var key: Int? = null
        for (raw in text.lines()) {
            val line = withoutComment(raw).trim()
            if (line.isEmpty()) continue
            val head = KEY_LINE.matchEntire(line)
            if (head != null) {
                key = KeyEvent.keyCodeFromString("KEYCODE_" + head.groupValues[1]).takeIf(::isKey) ?: SKIP
                continue
            }
            if (line == "}") {
                key = null
                continue
            }
            val current = key ?: continue
            if (current == SKIP) continue
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val output = parseBehavior(line.substring(colon + 1).trim()) ?: continue
            for (property in line.substring(0, colon).split(',')) {
                val layer = when (property.trim().lowercase()) {
                    "base" -> Layer.BASE
                    "shift", "lshift", "rshift" -> Layer.SHIFT
                    "alt", "lalt", "ralt" -> Layer.ALT
                    "sym" -> Layer.SYM
                    else -> null // label, capslock, ctrl, meta, combinations like sym+shift
                } ?: continue
                out.getOrPut(current) { EnumMap(Layer::class.java) }[layer] = output
            }
        }
        return out
    }

    /** The layout in use as a .kcm file, for building into the app or importing elsewhere. */
    fun toKcm(context: Context): String {
        val map = get(context)
        return buildString {
            appendLine("# Titan 2 keyboard layout, from Titan2 Keys.")
            appendLine("# To build it into the app, save it over titan2-custom.kcm and rebuild.")
            appendLine()
            appendLine("type OVERLAY")
            for (key in KEYS) {
                val generic = generic(key)
                if (key == KeyEvent.KEYCODE_DEL && Layer.entries.all { map[key, it] == generic[it] }) continue
                appendLine()
                appendLine("key ${codeName(key)} {")
                kcmLabel(key)?.let { property("label", it) }
                for (layer in Layer.entries) {
                    val output = map[key, layer] ?: continue
                    val name = if (layer == Layer.SHIFT && map.capsLocks(key)) "shift, capslock" else layer.id
                    val value = kcmValue(output)
                    if (value == null) {
                        appendLine("    # $name: ${longLabel(output)} (a .kcm can't do this, the app does)")
                        continue
                    }
                    property(name, value)
                    // Shift+Sym+H is Shift+← (selects), like in the original file.
                    if (layer == Layer.SYM && output is Output.Key) property("sym+shift", value)
                }
                appendLine("}")
            }
        }
    }

    /**
     * Makes the .kcm in [text] your layout. A key the file leaves out types what Android
     * gives it by default (just its letter). Returns how many keys the file set.
     */
    fun importKcm(context: Context, text: String): Int {
        val raw = parseKcm(text)
        require(raw.isNotEmpty()) { "That has no Titan 2 keys in it." }
        val cells = fill(raw)
        // What a .kcm can't hold (the controller toggle, text like "hello") stays where it is
        // unless the file sets that layer, so exporting and importing again loses nothing.
        val current = get(context)
        for (key in KEYS) for (layer in Layer.entries) {
            val mine = current[key, layer] ?: continue
            if (kcmValue(mine) == null && raw[key]?.containsKey(layer) != true) cells.getValue(key)[layer] = mine
        }
        val base = builtIn(context)
        val all = HashMap<Int, EnumMap<Layer, Output?>>()
        for (key in KEYS) for (layer in Layer.entries) {
            val mine = cells.getValue(key)[layer]
            if (mine != base[key, layer]) all.getOrPut(key) { EnumMap(Layer::class.java) }[layer] = mine
        }
        save(context, all)
        return raw.size
    }

    /** [raw] for all of the [KEYS]: what a key leaves out (or a key left out) comes from [generic]. */
    private fun fill(raw: Map<Int, Map<Layer, Output>>): HashMap<Int, EnumMap<Layer, Output>> {
        val out = HashMap<Int, EnumMap<Layer, Output>>()
        for (key in KEYS) {
            val row = EnumMap<Layer, Output>(Layer::class.java)
            row.putAll(raw[key] ?: generic(key))
            if (Layer.BASE !in row) generic(key)[Layer.BASE]?.let { row[Layer.BASE] = it }
            out[key] = row
        }
        return out
    }

    /** What Android's own layout (Generic.kcm) has a key type: its letter, a space, Enter. */
    private fun generic(key: Int): Map<Layer, Output> {
        val name = codeName(key)
        return when {
            name.length == 1 && name[0].isLetter() ->
                mapOf(Layer.BASE to Output.Text(name.lowercase()), Layer.SHIFT to Output.Text(name))
            key == KeyEvent.KEYCODE_SPACE -> mapOf(Layer.BASE to Output.Text(" "))
            key == KeyEvent.KEYCODE_ENTER -> mapOf(Layer.BASE to Output.Text("\n"))
            else -> mapOf(Layer.BASE to Output.Key(key))
        }
    }

    private fun parseBehavior(s: String): Output? = when {
        s.startsWith("'") -> parseChar(s)?.let { if (it == '\u0000') Output.None else Output.Text(it.toString()) }
        s.startsWith("none") -> Output.None
        s.startsWith("replace") -> {
            val name = s.removePrefix("replace").trim().substringBefore(' ')
            KeyEvent.keyCodeFromString("KEYCODE_$name").takeIf { it != KeyEvent.KEYCODE_UNKNOWN }?.let { Output.Key(it) }
        }
        else -> null // fallback: not something a key types
    }

    /** A .kcm character literal: 'a', '\n', '\'', '\u001b'. */
    private fun parseChar(s: String): Char? {
        if (s.length < 3) return null
        if (s[1] != '\\') return s[1].takeIf { s[2] == '\'' }
        return when (s[2]) {
            'n' -> '\n'
            't' -> '\t'
            '0' -> '\u0000'
            '\\', '\'', '"' -> s[2]
            'u' -> s.takeIf { it.length >= 7 }?.substring(3, 7)?.toIntOrNull(16)?.toChar()
            else -> null
        }
    }

    /** [line] without its # comment (a '#' in quotes is a character, not a comment). */
    private fun withoutComment(line: String): String {
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '\\' -> i++
                c == '\'' -> quoted = !quoted
                !quoted && c == '#' -> return line.substring(0, i)
            }
            i++
        }
        return line
    }

    private fun kcmLabel(key: Int): String? = when (key) {
        KeyEvent.KEYCODE_SPACE -> "' '"
        KeyEvent.KEYCODE_ENTER -> "'\\n'"
        else -> codeName(key).takeIf { it.length == 1 }?.let { "'$it'" }
    }

    private fun kcmValue(output: Output): String? = when (output) {
        Output.None -> "none"
        is Output.Key -> if (output.code < 0) null else "replace " + codeName(output.code)
        is Output.Text -> if (output.text.length == 1) kcmChar(output.text[0]) else null
    }

    private fun kcmChar(c: Char): String = when (c) {
        '\n' -> "'\\n'"
        '\t' -> "'\\t'"
        '\\' -> "'\\\\'"
        '\'' -> "'\\''"
        in ' '..'~' -> "'$c'"
        else -> "'\\u%04x'".format(c.code)
    }

    private fun StringBuilder.property(name: String, value: String) {
        appendLine("    " + "$name:".padEnd(36) + value)
    }

    // ---- names ----

    private class Names(val short: String, val long: String)

    private val NAMES: Map<Int, Names> = mapOf(
        KeyEvent.KEYCODE_DPAD_LEFT to Names("←", "← Left"),
        KeyEvent.KEYCODE_DPAD_RIGHT to Names("→", "→ Right"),
        KeyEvent.KEYCODE_DPAD_UP to Names("↑", "↑ Up"),
        KeyEvent.KEYCODE_DPAD_DOWN to Names("↓", "↓ Down"),
        KeyEvent.KEYCODE_MOVE_HOME to Names("Home", "Home (line start)"),
        KeyEvent.KEYCODE_MOVE_END to Names("End", "End (line end)"),
        KeyEvent.KEYCODE_PAGE_UP to Names("PgUp", "Page up"),
        KeyEvent.KEYCODE_PAGE_DOWN to Names("PgDn", "Page down"),
        KeyEvent.KEYCODE_TAB to Names("Tab", "Tab"),
        KeyEvent.KEYCODE_ESCAPE to Names("Esc", "Esc"),
        KeyEvent.KEYCODE_ENTER to Names("⏎", "Enter"),
        KeyEvent.KEYCODE_DEL to Names("⌫", "Backspace"),
        KeyEvent.KEYCODE_FORWARD_DEL to Names("Del", "Delete"),
        KeyEvent.KEYCODE_INSERT to Names("Ins", "Insert"),
        KeyEvent.KEYCODE_CUT to Names("Cut", "Cut"),
        KeyEvent.KEYCODE_COPY to Names("Copy", "Copy"),
        KeyEvent.KEYCODE_PASTE to Names("Paste", "Paste"),
        KeyEvent.KEYCODE_SEARCH to Names("Search", "Search"),
        KeyEvent.KEYCODE_PICTSYMBOLS to Names("Emoji", "Emoji panel"),
        CONTROLLER to Names("Pad", "Controller on/off"),
    )

    /** A label short enough for a key cap: "1", "←", "Esc". */
    fun capLabel(output: Output): String = when (output) {
        Output.None -> "∅"
        is Output.Key -> NAMES[output.code]?.short ?: codeName(output.code)
        is Output.Text -> when (output.text) {
            " " -> "␣"
            ESC -> "Esc"
            "\n" -> "⏎"
            "\t" -> "Tab"
            else -> if (output.text.length <= 3) output.text else output.text.take(2) + "…"
        }
    }

    /** A label for the editor: "1", "← Left", "Space", "“hello”". */
    fun longLabel(output: Output): String = when (output) {
        Output.None -> "Nothing"
        is Output.Key -> NAMES[output.code]?.long ?: codeName(output.code).replace('_', ' ')
        is Output.Text -> when (output.text) {
            " " -> "Space"
            ESC -> "Esc"
            "\n" -> "Enter"
            "\t" -> "Tab"
            else -> if (output.text.codePointCount(0, output.text.length) == 1) output.text else "“${output.text}”"
        }
    }

    private fun codes(letters: String) = letters.map { KeyEvent.keyCodeFromString("KEYCODE_$it") }

    private fun keys(vararg codes: Int): List<Output> = codes.map { Output.Key(it) }
}
