package dev.titan2.keys

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * The controller layer's keybinds, on a drawn gamepad. Tap a button, press a key, and that
 * key presses it while the controller is on (Sym+C). Pressing bound keys here lights up
 * their buttons, so it doubles as a tester.
 */
class ControllerActivity : Activity() {

    private lateinit var pad: GamepadView
    private lateinit var status: TextView
    private lateinit var promptTitle: TextView
    private lateinit var promptBody: TextView
    private lateinit var promptActions: LinearLayout
    private lateinit var clearButton: TextView

    /** The button waiting for its new key, if any. */
    private var capturing: Int? = null

    /** A key we used for binding; its release shouldn't do anything here either. */
    private var swallowUp = KeyEvent.KEYCODE_UNKNOWN

    /** Bound keys held down that reached this screen (controller off): key → button. */
    private val heldKeys = HashMap<Int, Int>()

    /** Buttons the key service is holding down (controller on). */
    private val serviceHeld = HashSet<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        useDarkWindow()
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(18), dp(14), dp(18), dp(28))

        root.addView(header(), below(16))

        pad = GamepadView(this)
        pad.onButtonTap = { button -> if (capturing == button) stopCapture() else startCapture(button) }
        val padCard = card()
        padCard.setPadding(dp(6), dp(14), dp(6), dp(6))
        padCard.addView(pad)
        root.addView(padCard, below())

        val prompt = card()
        promptTitle = label("", 18f, Ui.TEXT, Ui.BOLD)
        promptBody = label("", 14f, Ui.MUTED)
        prompt.addView(promptTitle)
        prompt.addView(promptBody, below(0).apply { topMargin = dp(4) })
        promptActions = LinearLayout(this)
        promptActions.orientation = LinearLayout.HORIZONTAL
        clearButton = pill("Clear", false) {
            capturing?.let { ControllerBindings.clear(this, it) }
            stopCapture()
        }
        promptActions.addView(clearButton, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        promptActions.addView(
            pill("Cancel", false) { stopCapture() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(10) },
        )
        prompt.addView(promptActions, below(0).apply { topMargin = dp(14) })
        root.addView(prompt, below())

        root.addView(tips(), below())
        root.addView(pill("Reset to defaults", false) {
            AlertDialog.Builder(this)
                .setMessage("Put every button back to its default key?")
                .setPositiveButton("Reset") { _, _ ->
                    ControllerBindings.reset(this)
                    render()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }, below(0))

        val scroll = ScrollView(this)
        scroll.fitsSystemWindows = true
        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        SuperKeyService.padButtonListener = ::onServiceButton
        SuperKeyService.padLayerListener = { updateStatus() }
        updateStatus()
        render()
    }

    override fun onPause() {
        stopCapture()
        SuperKeyService.padButtonListener = null
        SuperKeyService.padLayerListener = null
        heldKeys.clear()
        serviceHeld.clear()
        pad.releaseAll()
        super.onPause()
    }

    private fun header() = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(label("‹", 34f, Ui.TEXT, Ui.BOLD).apply {
            setPadding(dp(4), 0, dp(14), dp(4))
            setOnClickListener { finish() }
        })
        val titles = LinearLayout(context)
        titles.orientation = LinearLayout.VERTICAL
        titles.addView(label("Controller", 26f, Ui.TEXT, Ui.HEAVY))
        titles.addView(label("Sym+C turns it on and off", 13f, Ui.MUTED))
        addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        status = label("", 12f, Ui.TEXT, Ui.BOLD).apply { setPadding(dp(12), dp(6), dp(12), dp(7)) }
        addView(status)
    }

    private fun tips() = card().apply {
        addView(label("Good to know", 16f, Ui.TEXT, Ui.BOLD), below(8))
        for (tip in listOf(
            "Moonlight: Settings → Input Settings → turn off “Automatic gamepad presence detection”, or the PC won't see a gamepad.",
            "Hold Sym for the Sym layer as usual (Sym+C turns the controller off). Hold Alt to type digits, unless Alt is bound.",
            "No analog sticks (Android can't fake them), so play with the D-pad.",
        )) {
            val row = LinearLayout(context)
            row.orientation = LinearLayout.HORIZONTAL
            row.addView(label("●", 9f, Ui.PINK).apply { setPadding(0, dp(5), dp(10), 0) })
            row.addView(label(tip, 14f, Ui.MUTED), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(row, below(8))
        }
    }

    private fun updateStatus() {
        val on = SuperKeyService.padOn
        pad.live = on
        status.text = if (on) "● ON" else "OFF"
        status.setTextColor(if (on) Ui.GREEN else Ui.MUTED)
        status.background = Ui.rounded(
            if (on) Ui.withAlpha(Ui.GREEN, 0.14f) else Ui.RAISED, dpf(20f),
            if (on) Ui.withAlpha(Ui.GREEN, 0.5f) else Ui.STROKE, dp(1),
        )
    }

    private fun render() {
        val bindings = ControllerBindings.get(this)
        pad.labels = ControllerBindings.buttonLabels(bindings)
        pad.selected = capturing
        val button = capturing
        if (button == null) {
            promptTitle.text = "Press keys to test"
            promptBody.text = "Bound keys light up their buttons. Tap a button on the controller to change its key."
            promptActions.visibility = android.view.View.GONE
            return
        }
        val name = ControllerBindings.BUTTONS.getValue(button)
        promptTitle.text = SpannableStringBuilder("Press a key for ").apply {
            val start = length
            append(name)
            setSpan(ForegroundColorSpan(Ui.PINK), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val keys = ControllerBindings.keysFor(bindings, button)
        promptBody.text = if (keys.isEmpty()) "Not bound yet." else "Now: " + keys.joinToString(", ", transform = ControllerBindings::keyName)
        clearButton.isEnabled = keys.isNotEmpty()
        clearButton.alpha = if (keys.isEmpty()) 0.4f else 1f
        promptActions.visibility = android.view.View.VISIBLE
    }

    // ---- binding ----

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
        pad.flash(button)
    }

    // ---- testing ----

    private fun onServiceButton(button: Int, down: Boolean) {
        if (down) serviceHeld += button else serviceHeld -= button
        showPressed()
    }

    private fun showPressed() {
        val down = heldKeys.values.toSet() + serviceHeld
        for (button in ControllerBindings.BUTTONS.keys) pad.setPressed(button, button in down)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val key = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN
        if (!down && key == swallowUp) {
            swallowUp = KeyEvent.KEYCODE_UNKNOWN
            return true
        }
        if (capturing != null && key == KeyEvent.KEYCODE_BACK) {
            if (!down) stopCapture()
            return true
        }
        // Without the key service, keys to bind arrive here.
        if (capturing != null && down && event.repeatCount == 0 && !event.isSymPressed && ControllerBindings.canBind(key)) {
            swallowUp = key
            captured(key)
            return true
        }
        // Buttons the key service sent (controller on) already lit up through its listener.
        if (event.source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD &&
            key in ControllerBindings.BUTTONS
        ) return true
        // Controller off: show what the bound keys would press.
        val button = ControllerBindings.get(this)[key]
        if (button != null && (!down || !event.isSymPressed)) {
            if (down && event.repeatCount == 0) heldKeys[key] = button
            else if (!down) heldKeys -= key
            showPressed()
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}
