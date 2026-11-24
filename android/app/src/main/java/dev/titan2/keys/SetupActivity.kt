package dev.titan2.keys

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** The home screen: setup checklist, the controller, a shortcut cheat sheet and a key tester. */
class SetupActivity : Activity() {

    private lateinit var steps: LinearLayout
    private lateinit var progress: TextView
    private lateinit var miniPad: GamepadView
    private lateinit var log: TextView
    private val lines = ArrayDeque<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        useDarkWindow()
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(18), dp(22), dp(18), dp(32))

        root.addView(label("TITAN2 KEYS", 12f, Ui.PINK, Ui.BOLD).apply { letterSpacing = 0.2f })
        root.addView(label("Your Titan 2 keyboard,\nleveled up.", 30f, Ui.TEXT, Ui.HEAVY), below(6).apply { topMargin = dp(6) })
        root.addView(label("Custom layout · emoji panel · controller layer · key locks · Super key", 14f, Ui.MUTED), below(20))

        root.addView(setupCard(), below())
        root.addView(controllerCard(), below())
        root.addView(shortcutsCard(), below())
        root.addView(testCard(), below(0))

        val scroll = ScrollView(this)
        scroll.fitsSystemWindows = true
        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        refresh()
        miniPad.labels = ControllerBindings.buttonLabels(ControllerBindings.get(this))
        miniPad.live = SuperKeyService.padOn
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

    // Shows what reaches an ordinary app (after the keyboard and the key service).
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val name = KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_")
        val dir = if (event.action == KeyEvent.ACTION_DOWN) "down" else "up"
        addLine("app got $name $dir scan=${event.scanCode} meta=0x${Integer.toHexString(event.metaState)}")
        miniPad.live = SuperKeyService.padOn
        return super.dispatchKeyEvent(event)
    }

    // ---- cards ----

    private fun setupCard() = card().apply {
        val head = LinearLayout(context)
        head.orientation = LinearLayout.HORIZONTAL
        head.gravity = Gravity.CENTER_VERTICAL
        head.addView(label("Setup", 18f, Ui.TEXT, Ui.BOLD), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        progress = label("", 12f, Ui.TEXT, Ui.BOLD).apply { setPadding(dp(12), dp(5), dp(12), dp(6)) }
        head.addView(progress)
        addView(head, below(10))
        steps = LinearLayout(context)
        steps.orientation = LinearLayout.VERTICAL
        addView(steps)
    }

    private fun controllerCard() = card().apply {
        val radius = dpf(22f)
        background = Ui.ripple(Ui.rounded(Ui.CARD, radius, Ui.STROKE, dp(1)), radius)
        isClickable = true
        isFocusable = true
        setOnClickListener { startActivity(Intent(this@SetupActivity, ControllerActivity::class.java)) }

        miniPad = GamepadView(context)
        addView(miniPad, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            marginStart = dp(24)
            marginEnd = dp(24)
            bottomMargin = dp(8)
        })
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val texts = LinearLayout(context)
        texts.orientation = LinearLayout.VERTICAL
        texts.addView(label("Controller layer", 18f, Ui.TEXT, Ui.BOLD))
        texts.addView(label("Sym+C makes the keyboard a gamepad. Tap to bind and test the buttons.", 13f, Ui.MUTED))
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(label("›", 28f, Ui.PINK, Ui.BOLD).apply { setPadding(dp(12), 0, 0, dp(4)) })
        addView(row)
    }

    private fun shortcutsCard() = card().apply {
        addView(label("Shortcuts", 18f, Ui.TEXT, Ui.BOLD), below(10))
        addView(shortcut(listOf("Sym", "+", "B"), "Emoji panel"))
        addView(shortcut(listOf("Sym", "+", "C"), "Controller on / off"))
        addView(shortcut(listOf("hold", "Home"), "Super (Windows) key on / off"))
        addView(shortcut(listOf("2×", "Shift"), "Caps lock"))
        addView(shortcut(listOf("2×", "Alt"), "Alt lock (numbers & symbols)"))
        addView(shortcut(listOf("2×", "Sym"), "Sym lock (arrows, Tab, Esc…)"))
    }

    /** One cheat-sheet row. Words in [keys] that aren't "+", "hold" or "2×" are drawn as key caps. */
    private fun shortcut(keys: List<String>, what: String) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(5), 0, dp(5))
        val caps = LinearLayout(context)
        caps.orientation = LinearLayout.HORIZONTAL
        caps.gravity = Gravity.CENTER_VERTICAL
        for (key in keys) {
            val view = if (key == "+" || key == "hold" || key == "2×") {
                label(key, 12f, Ui.MUTED, Ui.BOLD).apply { setPadding(dp(4), 0, dp(4), 0) }
            } else {
                keycap(key)
            }
            caps.addView(view)
        }
        addView(caps, LinearLayout.LayoutParams(dp(128), ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(label(what, 14f, Ui.TEXT), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun testCard() = card().apply {
        addView(label("Test keys", 18f, Ui.TEXT, Ui.BOLD), below(10))
        addView(EditText(context).apply {
            hint = "Type here: try Sym+B, Sym+C, hold Home…"
            textSize = 15f
            setTextColor(Ui.TEXT)
            setHintTextColor(Ui.MUTED)
            background = Ui.rounded(Ui.RAISED, dpf(14f), Ui.STROKE, dp(1))
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }, below(10))
        log = label("The key log shows up here: what the key service saw and sent, and what reached this app.", 11f, Ui.MUTED, Typeface.MONOSPACE)
        log.background = Ui.rounded(0xFF0F1117.toInt(), dpf(12f))
        log.setPadding(dp(12), dp(10), dp(12), dp(10))
        addView(log)
    }

    // ---- setup steps ----

    private fun refresh() {
        val imm = getSystemService(InputMethodManager::class.java)
        val imeOn = imm.enabledInputMethodList.any { it.packageName == packageName }
        val imeActive = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.startsWith("$packageName/") == true
        val serviceOn = getSystemService(AccessibilityManager::class.java)
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.resolveInfo.serviceInfo.packageName == packageName }

        steps.removeAllViews()
        steps.addView(step(1, "Pick the Titan 2 Custom layout", "Settings → Physical keyboard", null) {
            open(Intent(Settings.ACTION_HARD_KEYBOARD_SETTINGS))
        })
        steps.addView(step(2, "Turn on Titan2 Emoji", "The keyboard. It stays out of sight until Sym+B", imeOn) {
            open(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })
        steps.addView(step(3, "Switch to Titan2 Emoji", "Replaces your onscreen keyboard", imeActive) {
            getSystemService(InputMethodManager::class.java).showInputMethodPicker()
        })
        steps.addView(step(4, "Turn on key locks & Super", "Accessibility → Titan2 Key locks & Super", serviceOn) {
            open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        if (!serviceOn) {
            steps.addView(label("Greyed out? Tap here: App info → ⋮ → Allow restricted settings ›", 12f, Ui.PINK).apply {
                setPadding(dp(54), dp(2), dp(8), dp(6))
                setOnClickListener {
                    open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                }
            })
        }

        val left = listOf(imeOn, imeActive, serviceOn).count { !it }
        progress.text = if (left == 0) "✓ READY" else "$left TO GO"
        val color = if (left == 0) Ui.GREEN else Ui.PINK
        progress.setTextColor(color)
        progress.background = Ui.rounded(Ui.withAlpha(color, 0.14f), dpf(20f), Ui.withAlpha(color, 0.5f), dp(1))
    }

    /** A checklist row. [done] null means Android doesn't tell us, so it just shows the number. */
    private fun step(number: Int, title: String, subtitle: String, done: Boolean?, onClick: () -> Unit): View {
        val radius = dpf(14f)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(dp(8), dp(10), dp(8), dp(10))
        row.background = Ui.ripple(Ui.rounded(0, radius), radius)
        row.isClickable = true
        row.setOnClickListener { onClick() }

        val badge = label(if (done == true) "✓" else number.toString(), 14f, if (done == true) Ui.BG else Ui.TEXT, Ui.BOLD)
        badge.gravity = Gravity.CENTER
        badge.background = if (done == true) Ui.rounded(Ui.GREEN, dpf(15f))
        else Ui.rounded(0, dpf(15f), if (done == false) Ui.PINK else Ui.VIOLET, dp(2))
        row.addView(badge, LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginEnd = dp(14) })

        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        texts.addView(label(title, 15f, if (done == true) Ui.MUTED else Ui.TEXT, Ui.MEDIUM))
        texts.addView(label(subtitle, 12f, Ui.MUTED))
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(label("›", 22f, Ui.MUTED).apply { setPadding(dp(8), 0, 0, dp(3)) })
        return row
    }

    // ---- helpers ----

    private fun addLine(line: String) {
        lines.addLast(line)
        while (lines.size > 14) lines.removeFirst()
        log.text = lines.joinToString("\n")
        log.setTextColor(Ui.TEXT)
    }

    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            addLine("couldn't open settings: ${e.message}")
        }
    }
}
