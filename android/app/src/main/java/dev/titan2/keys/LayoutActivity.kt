package dev.titan2.keys

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * The keyboard layout editor. Tap a key on the drawn keyboard, or press it (holding Shift,
 * Alt or Sym picks that layer too), then tap a layer to set what it does: a character, some
 * text, another key (arrows, Tab, F-keys…) or nothing. Changes work as soon as they're made.
 */
class LayoutActivity : Activity() {

    private lateinit var board: KeyboardView
    private lateinit var status: TextView
    private lateinit var warning: View
    private lateinit var keyCard: LinearLayout
    private lateinit var tryField: EditText

    /** The key being edited. */
    private var selected: Int? = null

    /** The layer the last press on the real keyboard was on: its row is lit. */
    private var pressedLayer: Layer? = null

    private var resumed = false

    /** Sym and Alt keys held, for presses that reach this screen without the key service. */
    private val heldMods = HashSet<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(18), dp(14), dp(18), dp(28))
        // Takes focus itself, so the try-it box doesn't get it (and the keys) on opening.
        root.isFocusableInTouchMode = true

        root.addView(header(), below(16))
        warning = warningCard()
        root.addView(warning, below())

        board = KeyboardView(this)
        board.onKeyTap = { key ->
            tryField.clearFocus()
            pressedLayer = null
            selected = key
            render()
        }
        val boardCard = card()
        boardCard.setPadding(dp(8), dp(12), dp(8), dp(10))
        boardCard.addView(board)
        root.addView(boardCard, below())

        keyCard = card()
        root.addView(keyCard, below())
        root.addView(tryCard(), below())
        root.addView(tips(), below())
        root.addView(fileButtons(), below())
        root.addView(pill("Reset everything", false) { confirmResetAll() }, below(0))

        val scroll = ScrollView(this)
        scroll.fitsSystemWindows = true
        scroll.addView(root)
        setContentView(scroll)
        useDarkWindow()
        root.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        warning.visibility = if (imeActive() && keyServiceOn()) View.GONE else View.VISIBLE
        render()
        updateCapture()
    }

    override fun onPause() {
        resumed = false
        updateCapture()
        heldMods.clear()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        updateCapture() // a dialog on top takes the focus, and its text field the keys
    }

    /** Key presses pick the key to edit, unless you're typing in the try-it box or a dialog. */
    private fun updateCapture() {
        val on = resumed && hasWindowFocus() && !tryField.hasFocus()
        SuperKeyService.layoutCapture = if (on) ::captured else null
    }

    /** A key pressed on the real keyboard, and the layer it was on. */
    private fun captured(key: Int, layer: Layer) {
        board.flash(key)
        pressedLayer = layer
        selected = key
        render()
    }

    // ---- screen ----

    private fun header() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(label("‹", 34f, Ui.TEXT, Ui.BOLD).apply {
            setPadding(dp(4), 0, dp(14), dp(4))
            setOnClickListener { finish() }
        })
        val titles = LinearLayout(context)
        titles.orientation = LinearLayout.VERTICAL
        titles.addView(label("Keyboard layout", 26f, Ui.TEXT, Ui.HEAVY))
        titles.addView(label("What every key types, on every layer", 13f, Ui.MUTED))
        addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        status = label("", 12f, Ui.TEXT, Ui.BOLD).apply { setPadding(dp(12), dp(6), dp(12), dp(7)) }
        addView(status)
    }

    private fun warningCard() = card().apply {
        val radius = dpf(22f)
        background = Ui.ripple(Ui.rounded(Ui.withAlpha(Ui.PINK, 0.1f), radius, Ui.withAlpha(Ui.PINK, 0.5f), dp(1)), radius)
        isClickable = true
        setOnClickListener { finish() }
        addView(label("Not working yet", 16f, Ui.PINK, Ui.BOLD), below(4))
        addView(label(
            "Your changes need Titan2 Emoji as the keyboard and Titan2 Key locks & Super turned on. " +
                "Until then the keyboard types the built-in layout. Tap to finish setup.",
            13f, Ui.TEXT,
        ))
    }

    private fun tryCard() = card().apply {
        addView(label("Try it", 18f, Ui.TEXT, Ui.BOLD), below(10))
        tryField = EditText(context).apply {
            hint = "Tap here and type"
            textSize = 15f
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.MUTED)
            background = Ui.rounded(Ui.RAISED, dpf(14f), Ui.STROKE, dp(1))
            setPadding(dp(14), dp(12), dp(14), dp(12))
            setOnFocusChangeListener { _, _ -> updateCapture() }
        }
        addView(tryField, below(8))
        addView(label("While this box has the cursor, keys type here instead of picking a key. Tap the keyboard above to pick again.", 12f, Ui.MUTED))
    }

    private fun tips() = card().apply {
        addView(label("Good to know", 16f, Ui.TEXT, Ui.BOLD), below(8))
        for (tip in listOf(
            "Locks follow your layout: Alt lock types your Alt layer, Sym lock your Sym layer, and caps lock capitalizes keys whose Shift is the capital of Base.",
            "Characters a US keyboard doesn't have (é, €, emoji) and text longer than one character are typed as text. Text fields take it; Moonlight and games may not.",
            "Shift, Sym, Alt, Fn, Back and Recent stay as they are. Hold Home for Super, double-tap Shift, Alt or Sym to lock, as always.",
            "Export saves your layout as a .kcm file. Put it over titan2-custom.kcm in the repo and rebuild to make it the built-in layout, or import it on another phone.",
        )) {
            val row = LinearLayout(context)
            row.orientation = LinearLayout.HORIZONTAL
            row.addView(label("●", 9f, Ui.PINK).apply { setPadding(0, dp(5), dp(10), 0) })
            row.addView(label(tip, 14f, Ui.MUTED), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(row, below(8))
        }
    }

    private fun fileButtons() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(pill("Export .kcm", false) { exportFile() }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(
            pill("Import .kcm", false) { importFile() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(10) },
        )
    }

    private fun render() {
        val layout = KeyLayout.get(this)
        val changed = KeyLayout.changedKeys(this)
        board.layout = layout
        board.changed = changed
        board.selected = selected
        val color = if (changed.isEmpty()) Ui.MUTED else Ui.PINK
        status.text = if (changed.isEmpty()) "BUILT-IN" else "${changed.size} CHANGED"
        status.setTextColor(color)
        status.background = if (changed.isEmpty()) Ui.rounded(Ui.RAISED, dpf(20f), Ui.STROKE, dp(1))
        else Ui.rounded(Ui.withAlpha(color, 0.14f), dpf(20f), Ui.withAlpha(color, 0.5f), dp(1))
        renderKey(layout)
    }

    private fun renderKey(layout: Keymap) {
        keyCard.removeAllViews()
        val key = selected
        if (key == null) {
            keyCard.addView(label("Tap a key, or press it", 18f, Ui.TEXT, Ui.BOLD))
            keyCard.addView(
                label("Hold Shift, Alt or Sym as you press it to go straight to that layer. Keys you've changed have a pink dot.", 14f, Ui.MUTED),
                below(0).apply { topMargin = dp(4) },
            )
            return
        }
        val head = LinearLayout(this)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(label(ControllerBindings.keyName(key), 24f, Ui.TEXT, Ui.HEAVY), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (KeyLayout.isChanged(this, key)) {
            head.addView(smallPill("Reset key") {
                KeyLayout.resetKey(this, key)
                render()
            })
        }
        keyCard.addView(head, below(10))
        for (layer in Layer.entries) keyCard.addView(layerRow(layout, key, layer), below(6))
        keyCard.addView(label("Tap a layer to change it.", 12f, Ui.MUTED), below(0).apply { topMargin = dp(4) })
    }

    /** One layer of the selected key: what it does now. Tap to change it. */
    private fun layerRow(layout: Keymap, key: Int, layer: Layer): View {
        val radius = dpf(14f)
        val lit = layer == pressedLayer
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(10), dp(10), dp(12), dp(10))
        row.background = Ui.ripple(
            Ui.rounded(Ui.RAISED, radius, if (lit) Ui.withAlpha(layer.color, 0.8f) else Ui.STROKE, dp(if (lit) 2 else 1)),
            radius,
        )
        row.isClickable = true
        row.setOnClickListener { edit(key, layer) }

        row.addView(label(layer.label, 12f, layer.color, Ui.BOLD).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(5))
            background = Ui.rounded(Ui.withAlpha(layer.color, 0.14f), dpf(10f))
        }, LinearLayout.LayoutParams(dp(56), ViewGroup.LayoutParams.WRAP_CONTENT))

        val output = layout[key, layer]
        val value = if (output == null) label("Same as Base", 15f, Ui.MUTED, Ui.MEDIUM)
        else label(KeyLayout.longLabel(output), 17f, if (output == Output.None) Ui.MUTED else Ui.TEXT, Ui.BOLD)
        value.isSingleLine = true
        value.ellipsize = android.text.TextUtils.TruncateAt.END
        row.addView(value, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(12) })
        if (KeyLayout.isChanged(this, key, layer)) {
            row.addView(label("changed", 11f, Ui.PINK, Ui.BOLD).apply { setPadding(dp(8), 0, dp(4), 0) })
        }
        row.addView(label("›", 22f, Ui.MUTED).apply { setPadding(dp(6), 0, 0, dp(3)) })
        return row
    }

    // ---- editing a layer ----

    private fun edit(key: Int, layer: Layer) {
        val current = KeyLayout.get(this)[key, layer]
        val builtIn = KeyLayout.builtIn(this)[key, layer]
        val dialog = Dialog(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val pick = { output: Output? ->
            KeyLayout.set(this, key, layer, output)
            dialog.dismiss()
            render()
        }

        val content = card()
        content.addView(label(SpannableStringBuilder(ControllerBindings.keyName(key) + " · ").apply {
            val start = length
            append(layer.label)
            setSpan(ForegroundColorSpan(layer.color), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }, 22f, Ui.TEXT, Ui.HEAVY))
        content.addView(label("Now: " + describe(current), 13f, Ui.MUTED), below(16))

        content.addView(heading("TYPE IT"), below(6))
        val field = EditText(this).apply {
            setText((current as? Output.Text)?.text?.takeIf { text -> text.none { it < ' ' } } ?: "")
            setSelectAllOnFocus(true)
            hint = "A character or some text"
            textSize = 17f
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_DONE
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.MUTED)
            background = Ui.rounded(Ui.RAISED, dpf(14f), Ui.STROKE, dp(1))
            setPadding(dp(14), dp(11), dp(14), dp(11))
        }
        val setTyped = {
            val output = typedOutput(field.text.toString())
            if (output == null) field.error = "Type something first" else pick(output)
        }
        field.setOnEditorActionListener { _, action, event ->
            val enter = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (action == EditorInfo.IME_ACTION_DONE || enter) {
                setTyped()
                true
            } else {
                false
            }
        }
        val typeRow = LinearLayout(this)
        typeRow.orientation = LinearLayout.HORIZONTAL
        typeRow.gravity = Gravity.CENTER_VERTICAL
        typeRow.addView(field, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        typeRow.addView(pill("Set", true) { setTyped() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(10) })
        content.addView(typeRow, below(6))
        content.addView(label("Type it on the keyboard, pick an emoji (Sym+B), or enter a code like U+00E9 for é.", 12f, Ui.MUTED), below(18))

        for ((group, outputs) in KeyLayout.ACTIONS) {
            content.addView(heading(group.uppercase()), below(6))
            content.addView(chipGrid(outputs, current, pick), below(12))
        }

        if (layer != Layer.BASE && current != null) content.addView(pill("Same as Base", false) { pick(null) }, below(8))
        if (current != builtIn) {
            content.addView(pill("Back to built-in: " + describe(builtIn), false) { pick(builtIn) }, below(8))
        }
        content.addView(pill("Cancel", false) { dialog.dismiss() }, below(0))

        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(content)
        dialog.setContentView(scroll)
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.setLayout(resources.displayMetrics.widthPixels - dp(24), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
    }

    /** What the box says to type: the text, or the character a code like U+00E9 stands for. */
    private fun typedOutput(raw: String): Output? {
        if (raw.isEmpty()) return null
        Regex("""(?i)(?:u\+|\\u)([0-9a-f]{1,6})""").matchEntire(raw.trim())?.let { match ->
            val point = match.groupValues[1].toInt(16)
            if (Character.isValidCodePoint(point) && point != 0) return Output.Text(String(Character.toChars(point)))
        }
        return Output.Text(raw)
    }

    private fun chipGrid(outputs: List<Output>, current: Output?, pick: (Output?) -> Unit): View {
        val grid = LinearLayout(this)
        grid.orientation = LinearLayout.VERTICAL
        for ((r, items) in outputs.chunked(COLUMNS).withIndex()) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (i in 0 until COLUMNS) {
                val params = LinearLayout.LayoutParams(0, dp(44), 1f).apply { if (i > 0) marginStart = dp(6) }
                val output = items.getOrNull(i)
                row.addView(if (output == null) View(this) else chip(output, output == current) { pick(output) }, params)
            }
            grid.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                if (r > 0) topMargin = dp(6)
            })
        }
        return grid
    }

    private fun chip(output: Output, on: Boolean, onClick: () -> Unit) = TextView(this).apply {
        text = chipLabel(output)
        gravity = Gravity.CENTER
        typeface = Ui.BOLD
        maxLines = 1
        setTextColor(if (on) Ui.PINK else Ui.TEXT)
        setAutoSizeTextTypeUniformWithConfiguration(9, 15, 1, TypedValue.COMPLEX_UNIT_SP)
        setPadding(dp(4), 0, dp(4), 0)
        val radius = dpf(12f)
        background = Ui.ripple(
            Ui.rounded(if (on) Ui.withAlpha(Ui.PINK, 0.14f) else Ui.RAISED, radius, if (on) Ui.PINK else Ui.STROKE, dp(1)),
            radius,
        )
        isClickable = true
        setOnClickListener { onClick() }
    }

    private fun chipLabel(output: Output): String = when (output) {
        Output.None -> "Nothing"
        Output.Text("\n") -> "Enter"
        Output.Key(KeyEvent.KEYCODE_DEL) -> "Bksp"
        Output.Key(KeyEvent.KEYCODE_FORWARD_DEL) -> "Delete"
        Output.Key(KeyEvent.KEYCODE_SEARCH) -> "Search"
        Output.Key(KeyLayout.CONTROLLER) -> "Controller"
        else -> KeyLayout.capLabel(output)
    }

    private fun describe(output: Output?) = if (output == null) "Same as Base" else KeyLayout.longLabel(output)

    private fun heading(text: String) = label(text, 11f, Ui.MUTED, Ui.BOLD).apply { letterSpacing = 0.12f }

    private fun smallPill(text: String, onClick: () -> Unit) = label(text, 13f, Ui.TEXT, Ui.BOLD).apply {
        setPadding(dp(14), dp(7), dp(14), dp(8))
        val radius = dpf(14f)
        background = Ui.ripple(Ui.rounded(Ui.RAISED, radius, Ui.STROKE, dp(1)), radius)
        isClickable = true
        setOnClickListener { onClick() }
    }

    // ---- whole layout ----

    private fun confirmResetAll() {
        AlertDialog.Builder(this)
            .setMessage("Put every key back to the built-in layout?")
            .setPositiveButton("Reset") { _, _ ->
                KeyLayout.resetAll(this)
                render()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun exportFile() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/octet-stream") // text/plain would get a .txt added to the name
            .putExtra(Intent.EXTRA_TITLE, "titan2-custom.kcm")
        start(intent, EXPORT)
    }

    private fun importFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
        start(intent, IMPORT)
    }

    private fun start(intent: Intent, request: Int) {
        try {
            startActivityForResult(intent, request)
        } catch (e: Exception) {
            Toast.makeText(this, "No app to pick a file with", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        try {
            when (requestCode) {
                EXPORT -> {
                    val stream = contentResolver.openOutputStream(uri, "wt") ?: error("can't write there")
                    stream.bufferedWriter().use { it.write(KeyLayout.toKcm(this)) }
                    Toast.makeText(this, "Layout saved", Toast.LENGTH_SHORT).show()
                }
                IMPORT -> {
                    val stream = contentResolver.openInputStream(uri) ?: error("can't read it")
                    val text = stream.bufferedReader().use { it.readText() }
                    confirmImport(text)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Didn't work: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmImport(text: String) {
        val keys = KeyLayout.parseKcm(text).size
        if (keys == 0) {
            Toast.makeText(this, "That file has no Titan 2 keys in it", Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this)
            .setMessage("Make this file your layout? It sets $keys keys; keys it leaves out type just their letter.")
            .setPositiveButton("Import") { _, _ ->
                KeyLayout.importKcm(this, text)
                selected = null
                render()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---- keys without the key service ----

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN
        if (code == KeyEvent.KEYCODE_SYM || code == KeyEvent.KEYCODE_ALT_LEFT || code == KeyEvent.KEYCODE_ALT_RIGHT) {
            if (down) heldMods += code else heldMods -= code
        }
        if (tryField.hasFocus()) return super.dispatchKeyEvent(event)
        // With the key service on, it hands presses over (see updateCapture) and they never
        // get here. Without it, they do: pick the key the same way.
        val alt = event.isAltPressed || KeyEvent.KEYCODE_ALT_LEFT in heldMods || KeyEvent.KEYCODE_ALT_RIGHT in heldMods
        val sym = event.isSymPressed || KeyEvent.KEYCODE_SYM in heldMods
        val layer = when {
            event.isCtrlPressed || event.isMetaPressed -> null
            alt -> Layer.ALT
            sym -> Layer.SYM
            event.isShiftPressed -> Layer.SHIFT
            else -> Layer.BASE
        }
        val key = KeyLayout.physicalKey(this, code, layer, event.isShiftPressed) ?: return super.dispatchKeyEvent(event)
        if (down && event.repeatCount == 0) captured(key, layer ?: Layer.BASE)
        return true
    }

    private companion object {
        const val COLUMNS = 4
        const val EXPORT = 1
        const val IMPORT = 2
    }
}
