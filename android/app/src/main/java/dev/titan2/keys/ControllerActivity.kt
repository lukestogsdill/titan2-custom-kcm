package dev.titan2.keys

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

/** Edits the controller layer's keys (Sym+C turns the layer on). */
class ControllerActivity : Activity() {

    private lateinit var bindButton: Button
    private lateinit var rows: LinearLayout
    private var capturing = false

    /** A key we used for binding; its release shouldn't do anything here either. */
    private var swallowUp = KeyEvent.KEYCODE_UNKNOWN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(16), dp(16), dp(16))

        root.addView(label("Controller keybinds", 22f))
        root.addView(label(
            "Sym+C turns the controller on and off (PAD at the top of the screen). While it's on, " +
                "the keys below are gamepad buttons and every other key types as usual. " +
                "Hold Sym (or Alt, if it isn't bound) to type that layer as usual.",
            14f,
        ))
        root.addView(label(
            "Moonlight: Settings → Input Settings → turn off “Automatic gamepad presence detection”, " +
                "or the PC won't see a gamepad. There are no analog sticks, so use the D-pad.",
            14f,
        ))
        bindButton = button(BIND_TEXT) { if (capturing) stopCapture() else startCapture() }
        root.addView(bindButton)
        rows = LinearLayout(this)
        rows.orientation = LinearLayout.VERTICAL
        root.addView(rows)
        root.addView(button("Reset to defaults") {
            AlertDialog.Builder(this)
                .setMessage("Put every key back to the default controller layout?")
                .setPositiveButton("Reset") { _, _ ->
                    ControllerBindings.reset(this)
                    render()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        })

        val scroll = ScrollView(this)
        scroll.fitsSystemWindows = true
        scroll.addView(root)
        setContentView(scroll)
        render()
    }

    override fun onPause() {
        stopCapture()
        super.onPause()
    }

    private fun render() {
        rows.removeAllViews()
        val bindings = ControllerBindings.get(this)
        if (bindings.isEmpty()) rows.addView(label("No keys bound.", 14f))
        for ((key, target) in ControllerBindings.sorted(bindings)) {
            rows.addView(button("${ControllerBindings.keyName(key)}   →   ${target.label}") { pickTarget(key) }.apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            })
        }
    }

    // ---- binding a new key ----

    private fun startCapture() {
        capturing = true
        bindButton.text = "Press a key to bind… (tap here to cancel)"
        // The key service sees keys before this screen does, so it hands them over.
        SuperKeyService.keyCapture = ::captured
    }

    private fun stopCapture() {
        capturing = false
        SuperKeyService.keyCapture = null
        bindButton.text = BIND_TEXT
    }

    private fun captured(key: Int) {
        stopCapture()
        pickTarget(key)
    }

    // Without the key service, keys arrive here instead.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val key = event.keyCode
        if (event.action == KeyEvent.ACTION_UP && key == swallowUp) {
            swallowUp = KeyEvent.KEYCODE_UNKNOWN
            return true
        }
        if (capturing && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
            !event.isSymPressed && ControllerBindings.canBind(key)
        ) {
            swallowUp = key
            captured(key)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun pickTarget(key: Int) {
        val targets = ControllerBindings.TARGETS
        val labels = (listOf("None (types as usual)") + targets.map { it.label }).toTypedArray()
        val current = ControllerBindings.get(this)[key]
        val checked = if (current == null) 0 else targets.indexOf(current) + 1
        AlertDialog.Builder(this)
            .setTitle("${ControllerBindings.keyName(key)} sends")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val bindings = LinkedHashMap(ControllerBindings.get(this))
                if (which == 0) bindings.remove(key) else bindings[key] = targets[which - 1]
                ControllerBindings.set(this, bindings)
                dialog.dismiss()
                render()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun label(text: String, size: Float) = TextView(this).apply {
        this.text = text
        textSize = size
        setPadding(0, dp(6), 0, dp(6))
    }

    private fun button(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val BIND_TEXT = "+ Bind a key"
    }
}
