package dev.titan2.keys

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.roundToInt

/** Setup checklist plus a key log for checking what the Titan's keys actually send. */
class SetupActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var log: TextView
    private val lines = ArrayDeque<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(16), dp(16), dp(16), dp(16))

        root.addView(label(getString(R.string.app_name), 22f))
        status = label("", 15f)
        root.addView(status)
        root.addView(button("1. Pick “Titan 2 Custom” layout (Physical keyboard)") {
            open(Intent(Settings.ACTION_HARD_KEYBOARD_SETTINGS))
        })
        root.addView(button("2. Turn on the Titan2 Emoji keyboard") {
            open(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        root.addView(button("3. Switch to Titan2 Emoji") {
            getSystemService(InputMethodManager::class.java).showInputMethodPicker()
        })
        root.addView(button("4. Turn on Titan2 Key locks & Super (Accessibility)") {
            open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(button("Greyed out? App info → ⋮ → Allow restricted settings") {
            open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        })
        root.addView(button("Controller keybinds (Sym+C turns the controller on)") {
            startActivity(Intent(this, ControllerActivity::class.java))
        })
        root.addView(EditText(this).apply {
            hint = "Test: Sym+B emoji · Sym+C controller · hold Home for Super · double-tap Shift/Alt/Sym to lock"
        })
        log = label("", 12f)
        log.typeface = Typeface.MONOSPACE
        root.addView(log)

        val scroll = ScrollView(this)
        scroll.fitsSystemWindows = true
        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refresh()
        SuperKeyService.traceListener = ::addLine
    }

    override fun onPause() {
        SuperKeyService.traceListener = null
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refresh() // e.g. after the keyboard picker closes
    }

    // Shows what reaches an ordinary app (after the keyboard and the Super service).
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val name = KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_")
        val dir = if (event.action == KeyEvent.ACTION_DOWN) "down" else "up"
        addLine("app got $name $dir scan=${event.scanCode} meta=0x${Integer.toHexString(event.metaState)}")
        return super.dispatchKeyEvent(event)
    }

    private fun refresh() {
        val imm = getSystemService(InputMethodManager::class.java)
        val imeOn = imm.enabledInputMethodList.any { it.packageName == packageName }
        val imeActive = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.startsWith("$packageName/") == true
        val superOn = getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName }
        status.text = listOf(
            "Emoji keyboard turned on" to imeOn,
            "Emoji keyboard selected" to imeActive,
            "Key locks & Super service on" to superOn,
        ).joinToString("\n") { (text, ok) -> (if (ok) "✅ " else "❌ ") + text }
    }

    private fun addLine(line: String) {
        lines.addLast(line)
        while (lines.size > 14) lines.removeFirst()
        log.text = lines.joinToString("\n")
    }

    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            addLine("couldn't open settings: ${e.message}")
        }
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
