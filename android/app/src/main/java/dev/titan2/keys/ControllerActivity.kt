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

/**
 * Lists the gamepad buttons and the key for each. Tap a button, press a key, and that
 * key presses it in the controller layer (Sym+C).
 */
class ControllerActivity : Activity() {

    private lateinit var rows: LinearLayout

    /** The button waiting for its new key, if any. */
    private var capturing: Int? = null

    /** A key we used for binding; its release shouldn't do anything here either. */
    private var swallowUp = KeyEvent.KEYCODE_UNKNOWN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(16), dp(16), dp(16))

        root.addView(label("Controller keybinds", 22f))
        root.addView(label(
            "Tap a button, then press the key you want for it. Sym+C turns the controller on " +
                "and off (PAD at the top of the screen). While it's on, these keys press their " +
                "buttons and every other key types as usual. Hold Sym (or Alt, if it isn't " +
                "bound) to type that layer as usual.",
            14f,
        ))
        root.addView(label(
            "Moonlight: Settings → Input Settings → turn off “Automatic gamepad presence detection”, " +
                "or the PC won't see a gamepad. There are no analog sticks, so use the D-pad.",
            14f,
        ))
        rows = LinearLayout(this)
        rows.orientation = LinearLayout.VERTICAL
        root.addView(rows)
        root.addView(button("Reset to defaults") {
            AlertDialog.Builder(this)
                .setMessage("Put every button back to its default key?")
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
        for ((button, name) in ControllerBindings.BUTTONS) {
            val keys = ControllerBindings.keysFor(bindings, button)
            val text = if (capturing == button) "$name   →   press a key… (tap to cancel)"
            else "$name   →   " + keys.joinToString(", ", transform = ControllerBindings::keyName).ifEmpty { "—" }
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.addView(
                button(text) { if (capturing == button) stopCapture() else startCapture(button) }.apply {
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            row.addView(button("Clear") {
                ControllerBindings.clear(this, button)
                stopCapture()
            }.apply { isEnabled = keys.isNotEmpty() })
            rows.addView(row)
        }
    }

    private fun startCapture(button: Int) {
        capturing = button
        // The key service sees keys before this screen does, so it hands them over.
        SuperKeyService.keyCapture = ::captured
        render()
    }

    private fun stopCapture() {
        capturing = null
        SuperKeyService.keyCapture = null
        render()
    }

    private fun captured(key: Int) {
        val button = capturing ?: return
        ControllerBindings.setKey(this, button, key)
        stopCapture()
    }

    // Without the key service, keys arrive here instead.
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val key = event.keyCode
        if (event.action == KeyEvent.ACTION_UP && key == swallowUp) {
            swallowUp = KeyEvent.KEYCODE_UNKNOWN
            return true
        }
        if (capturing != null && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0 &&
            !event.isSymPressed && ControllerBindings.canBind(key)
        ) {
            swallowUp = key
            captured(key)
            return true
        }
        return super.dispatchKeyEvent(event)
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
}
