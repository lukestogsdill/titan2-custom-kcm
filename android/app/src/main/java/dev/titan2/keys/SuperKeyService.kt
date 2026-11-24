package dev.titan2.keys

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.roundToInt

/**
 * The hardware-keyboard extras that need to see every key first:
 *
 *  - Double-tap Shift, Alt or Sym to lock it (Shift locks caps). Tap it once to unlock.
 *  - Holding Home (or the key with code 408) turns Super (Meta / the Windows key) on and
 *    off, e.g. for Hyprland through Moonlight. A short press of Home still goes home.
 *  - Sym+B opens the emoji panel, in any app.
 *  - Sym+C turns the controller layer on and off: the keys bound in [ControllerBindings]
 *    become gamepad buttons (for Moonlight), everything else types as usual.
 *  - A badge at the top of the screen shows what's locked.
 *
 * A key that a lock or Super changes is taken here, and the changed key is sent to the
 * focused app through the Titan2 Emoji keyboard (an accessibility service can't type by
 * itself). Keys sent that way go straight to the app, so Android's own Meta shortcuts
 * can't swallow them. Every other key goes through untouched.
 *
 * Why an accessibility service and not just the keyboard: Moonlight reads keys in
 * StreamView.onKeyPreIme, before any keyboard sees them. Only an accessibility key
 * filter gets them first.
 *
 * (The class keeps its old name so Android leaves the service turned on across updates.)
 */
class SuperKeyService : AccessibilityService() {

    companion object {
        /** A tap is a modifier pressed on its own and released within this time... */
        const val TAP_MAX_MS = 300L

        /** ...and a double tap is a second tap starting within this time of the first. */
        const val DOUBLE_TAP_GAP_MS = 300L

        /** Holding the Super key this long turns Super on or off. */
        const val HOLD_MS = 500L

        /** Besides Home, the key whose scan code (or keycode) is this one toggles Super too. */
        const val SUPER_KEY_CODE = 408

        private const val SYM_BITS = KeyEvent.META_SYM_ON
        private const val ALT_BITS = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON
        private const val LAYER_MASK = KeyEvent.META_SYM_ON or KeyEvent.META_ALT_MASK

        private val META_BITS = mapOf(
            KeyEvent.KEYCODE_META_LEFT to (KeyEvent.META_META_ON or KeyEvent.META_META_LEFT_ON),
            KeyEvent.KEYCODE_META_RIGHT to (KeyEvent.META_META_ON or KeyEvent.META_META_RIGHT_ON),
            KeyEvent.KEYCODE_SHIFT_LEFT to (KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON),
            KeyEvent.KEYCODE_SHIFT_RIGHT to (KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_RIGHT_ON),
            KeyEvent.KEYCODE_CTRL_LEFT to (KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON),
            KeyEvent.KEYCODE_CTRL_RIGHT to (KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_RIGHT_ON),
            KeyEvent.KEYCODE_ALT_LEFT to (KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON),
            KeyEvent.KEYCODE_ALT_RIGHT to (KeyEvent.META_ALT_ON or KeyEvent.META_ALT_RIGHT_ON),
        )

        /** Set by SetupActivity while it is open, to show what the service is doing. */
        @Volatile
        var traceListener: ((String) -> Unit)? = null

        /**
         * Set by ControllerActivity while it waits for a key to bind. The next bindable key
         * press goes to it (raw, before any lock or the controller layer changes it).
         */
        @Volatile
        var keyCapture: ((Int) -> Unit)? = null

        private val DPAD_KEYS = setOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        )
    }

    /** The badge chips, in display order. */
    private enum class Lock(val label: String, val color: Int) {
        CAPS("CAPS", 0xFF1E88E5.toInt()),
        ALT("ALT", 0xFFE65100.toInt()),
        SYM("SYM", 0xFF2E7D32.toInt()),
        SUPER("SUPER", 0xFF8E24AA.toInt()),
        PAD("PAD", 0xFFD81B60.toInt()),
    }

    /**
     * What we sent for a key we took over. Its repeats and release are sent the same way.
     * A key the controller layer took has the gamepad [button] instead, and doesn't repeat.
     */
    private class Sent(
        val code: Int,
        val meta: Int,
        val addedShift: Boolean = false,
        val withSuper: Boolean = false,
        val button: Int? = null,
    )

    /** For keys we used up ourselves (Sym+B): drop their repeats and release. */
    private val swallow = Sent(KeyEvent.KEYCODE_UNKNOWN, 0)

    private val locks = HashSet<Lock>()
    private var deviceId = KeyCharacterMap.VIRTUAL_KEYBOARD

    /** Keys we took over, by hardware keycode. Their releases are ours too. */
    private val owned = HashMap<Int, Sent>()

    /** Modifier keys we've sent down to the app and not released yet. */
    private val modsDown = LinkedHashSet<Int>()

    /** Gamepad buttons held down, with how many keys hold each (two keys can be A). */
    private val padHeld = HashMap<Int, Int>()

    /** The device the controller layer's gamepad buttons claim to come from (see [findPadDevice]). */
    private var padDevice = 0

    // Double-tap tracking.
    private var tapLock: Lock? = null
    private var tapDownAt = 0L
    private var lastTap: Lock? = null
    private var lastTapUpAt = 0L

    // Super key hold tracking.
    private val handler = Handler(Looper.getMainLooper())
    private var holdFired = false
    private val holdTimeout = Runnable {
        holdFired = true
        toggleSuper()
    }

    private val usKeyboard by lazy { KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD) }

    /** The Sym layer's `replace` entries in titan2-custom.kcm, e.g. H → DPAD_LEFT. */
    private val symReplace by lazy { loadSymReplaces() }

    private var badge: LinearLayout? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        addBadge()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(holdTimeout)
        releaseAll()
        badge?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        badge = null
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN
        if (event.repeatCount == 0 && traceListener != null) {
            val name = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
            trace("service saw $name " + (if (down) "down" else "up") + " scan=${event.scanCode}")
        }
        if (isSuperKey(event)) return onSuperKey(event, down)
        if (down && event.repeatCount == 0 && code !in owned &&
            (capture(event) || controllerToggle(event) || padPress(event))
        ) {
            tapLock = null // not a tap of a held modifier, and a bound modifier isn't one either
            lastTap = null
            return true
        }
        val lock = lockFor(code)
        if (lock != null) {
            trackTap(event, lock, down)
        } else if (down) {
            tapLock = null // a modifier held with another key is a chord, not a tap
            lastTap = null
        }

        val sent = owned[code]
        if (sent != null) {
            if (!down) release(code, sent)
            else if (sent !== swallow && sent.button == null && !isModifier(code)) {
                send(KeyEvent.ACTION_DOWN, sent.code, event.repeatCount, sent.meta)
            }
            return true
        }
        if (!down || event.repeatCount > 0) return false
        return press(event)
    }

    // ---- locks ----

    private fun lockFor(code: Int) = when (code) {
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> Lock.CAPS
        KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> Lock.ALT
        KeyEvent.KEYCODE_SYM -> Lock.SYM
        else -> null
    }

    private fun trackTap(event: KeyEvent, lock: Lock, down: Boolean) {
        val t = event.eventTime
        if (down) {
            if (event.repeatCount == 0) {
                // Two modifiers held together is a chord, not a tap of either.
                tapLock = if (tapLock == null) lock else null
                tapDownAt = t
            }
            return
        }
        val tapped = tapLock == lock && t - tapDownAt <= TAP_MAX_MS
        tapLock = null
        when {
            !tapped -> lastTap = null
            lock in locks -> {
                setLock(lock, false)
                lastTap = null
            }
            lastTap == lock && tapDownAt - lastTapUpAt <= DOUBLE_TAP_GAP_MS -> {
                setLock(lock, true)
                lastTap = null
            }
            else -> {
                lastTap = lock
                lastTapUpAt = t
            }
        }
    }

    private fun setLock(lock: Lock, on: Boolean) {
        if (on == (lock in locks)) return
        if (on) locks += lock else locks -= lock
        if (lock == Lock.SUPER && !on) for (mod in modsDown.toList()) modUp(mod)
        if (lock == Lock.PAD && !on) {
            // Let go of held buttons now; the keys' own releases are dropped later.
            for ((code, sent) in owned.entries.toList()) {
                if (sent.button != null) {
                    release(code, sent)
                    owned[code] = swallow
                }
            }
        }
        getSystemService(Vibrator::class.java)
            ?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        updateBadge()
        trace("${lock.label} " + if (on) "ON" else "off")
    }

    private fun isSuperKey(event: KeyEvent) = event.keyCode == KeyEvent.KEYCODE_HOME ||
        event.scanCode == SUPER_KEY_CODE || event.keyCode == SUPER_KEY_CODE

    /**
     * Holding the Super key toggles Super. Home is taken over so holding it doesn't also
     * go home or open the assistant; a short press is replayed as Home. Any other Super
     * key goes through as usual, so whatever it normally does still works.
     */
    private fun onSuperKey(event: KeyEvent, down: Boolean): Boolean {
        val home = event.keyCode == KeyEvent.KEYCODE_HOME
        if (down) {
            if (event.repeatCount == 0) {
                holdFired = false
                handler.removeCallbacks(holdTimeout)
                handler.postDelayed(holdTimeout, HOLD_MS)
            }
            return home
        }
        handler.removeCallbacks(holdTimeout)
        if (home && !holdFired) performGlobalAction(GLOBAL_ACTION_HOME)
        return home
    }

    private fun toggleSuper() {
        val turnOn = Lock.SUPER !in locks
        if (turnOn && EmojiKeyboardService.instance == null) {
            Toast.makeText(this, "Super needs Titan2 Emoji as the keyboard", Toast.LENGTH_SHORT).show()
            trace("Super: Titan2 Emoji isn't the active keyboard")
            return
        }
        setLock(Lock.SUPER, turnOn)
    }

    // ---- controller layer ----

    /** The controller editor is waiting for a key to bind: hand this one over. */
    private fun capture(event: KeyEvent): Boolean {
        val listener = keyCapture ?: return false
        if (event.isSymPressed || !ControllerBindings.canBind(event.keyCode)) return false
        owned[event.keyCode] = swallow
        listener(event.keyCode)
        return true
    }

    /** Sym+C (held or locked Sym) turns the controller layer on and off. */
    private fun controllerToggle(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_C) return false
        if (!event.isSymPressed && (Lock.SYM !in locks || event.isAltPressed)) return false
        owned[event.keyCode] = swallow
        val turnOn = Lock.PAD !in locks
        if (turnOn && EmojiKeyboardService.instance == null) {
            Toast.makeText(this, "The controller needs Titan2 Emoji as the keyboard", Toast.LENGTH_SHORT).show()
            trace("controller: Titan2 Emoji isn't the active keyboard")
            return true
        }
        if (turnOn) {
            padDevice = findPadDevice()
            trace("controller buttons go out as device $padDevice " +
                (InputDevice.getDevice(padDevice)?.name?.let { "($it)" } ?: "(no such device)"))
        }
        setLock(Lock.PAD, turnOn)
        return true
    }

    /**
     * A key press with the controller layer on: a bound key is sent as its gamepad button.
     * Sym, held or locked, still gives the normal Sym layer, and so does holding
     * Alt for the Alt layer, unless Alt is bound as a button itself.
     */
    private fun padPress(event: KeyEvent): Boolean {
        if (Lock.PAD !in locks || event.isSymPressed || Lock.SYM in locks) return false
        val bindings = ControllerBindings.get(this)
        if (event.isAltPressed && bindings.keys.none(::isAlt)) return false
        val button = bindings[event.keyCode] ?: return false
        val ime = EmojiKeyboardService.instance
        if (ime == null || !ime.canSendKeys || ime.panelShowing) return false
        owned[event.keyCode] = Sent(button, 0, button = button)
        val held = padHeld[button] ?: 0
        padHeld[button] = held + 1
        if (held == 0) sendPad(KeyEvent.ACTION_DOWN, button)
        return true
    }

    private fun buttonUp(button: Int) {
        val held = padHeld[button] ?: return
        if (held > 1) {
            padHeld[button] = held - 1
            return
        }
        padHeld -= button
        sendPad(KeyEvent.ACTION_UP, button)
    }

    private fun sendPad(action: Int, code: Int) {
        val source = if (code in DPAD_KEYS) InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_DPAD
        else InputDevice.SOURCE_GAMEPAD
        send(action, code, 0, 0, padDevice, source)
    }

    /**
     * Which device id the gamepad buttons should carry. Moonlight takes a key as gamepad
     * input when its device is a gamepad or not a letter keyboard, or when it has id 0 and
     * no device has that id (it's player 1 either way). It types keys from a letter keyboard
     * like the Titan's, and drops keys from any other id it doesn't know.
     */
    private fun findPadDevice(): Int {
        if (InputDevice.getDevice(0) == null) return 0
        val candidates: List<InputDevice> = InputDevice.getDeviceIds().toList()
            .mapNotNull { id -> InputDevice.getDevice(id) }
            .filter { dev ->
                !dev.isVirtual && (dev.supportsSource(InputDevice.SOURCE_GAMEPAD) ||
                    dev.keyboardType != InputDevice.KEYBOARD_TYPE_ALPHABETIC)
            }
        // Built-in buttons (e.g. volume keys) first: Moonlight makes those player 1.
        val pick = candidates.minByOrNull { dev ->
            val external = if (dev.isExternal) 2 else 0
            external + if (dev.keyboardType == InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC) 0 else 1
        }
        return pick?.id ?: 0
    }

    // ---- key presses ----

    /** A new key press. Returns true if we took it over. */
    private fun press(event: KeyEvent): Boolean {
        val code = event.keyCode
        val superOn = Lock.SUPER in locks

        if (KeyEvent.isModifierKey(code)) {
            // Outside Super, modifiers always go through as they are (taps are tracked above).
            // With Super on, Shift/Ctrl/Alt go to the app through us, in step with the keys.
            if (!superOn || !isModifier(code)) return false
            deviceId = event.deviceId
            owned[code] = Sent(code, 0)
            if (!isAlt(code)) modDown(code) // Alt is decided per key, in pressWithSuper
            return true
        }

        // Sym lock acts like holding Sym, Alt lock like holding Alt, but only on keys where
        // that layer has something: with Alt lock, Backspace and Space stay as they are.
        // Holding the other layer's key overrides the lock for that press.
        val kcm = event.keyCharacterMap
        val chord = event.isCtrlPressed || event.isMetaPressed
        var key = code
        var layer = 0
        if (Lock.SYM in locks && !event.isSymPressed && !event.isAltPressed) {
            val replaced = symReplace[code]
            if (replaced != null) key = replaced
            else if (!chord && changes(kcm, code, SYM_BITS)) layer = SYM_BITS
        }
        if (key == code && layer == 0 && !chord && Lock.ALT in locks &&
            !event.isAltPressed && !event.isSymPressed && changes(kcm, code, ALT_BITS)
        ) layer = ALT_BITS

        val ime = EmojiKeyboardService.instance
        if (key == EmojiKeyboardService.TOGGLE_KEY) {
            if (ime == null && key == code) return false
            ime?.togglePanel()
            owned[code] = swallow
            return true
        }

        // Caps lock: letters only, and not while Super is on (use Shift for Super+Shift+B).
        val caps = !superOn && !chord && key == code && layer == 0 && Lock.CAPS in locks &&
            !event.isShiftPressed && !event.isAltPressed && !event.isSymPressed &&
            changes(kcm, code, KeyEvent.META_CAPS_LOCK_ON)

        if (!superOn && key == code && layer == 0 && !caps) return false
        if (ime == null || !ime.canSendKeys) return false
        deviceId = event.deviceId

        if (ime.panelShowing) {
            // The emoji panel reads the keyboard itself. Only changed keys need handing over.
            if (key == code && layer == 0) return false
            val changed = KeyEvent(
                event.downTime, event.eventTime, KeyEvent.ACTION_DOWN, key, 0,
                event.metaState or layer, event.deviceId, event.scanCode, event.flags, event.source,
            )
            if (ime.panelKey(changed)) {
                owned[code] = swallow
                return true
            }
        }

        if (superOn) {
            pressWithSuper(event, key, layer)
            return true
        }
        val addedShift = caps && modDown(KeyEvent.KEYCODE_SHIFT_LEFT)
        val meta = event.metaState or layer or modsMeta()
        owned[code] = Sent(key, meta, addedShift)
        send(KeyEvent.ACTION_DOWN, key, 0, meta)
        return true
    }

    /** A key press while Super is on: sent as Super+key. */
    private fun pressWithSuper(event: KeyEvent, layerKey: Int, layer: Int) {
        var key = layerKey
        var shift = false
        var alt = owned.keys.any(::isAlt)
        // On the Titan, Alt and Sym pick characters (Alt+W = 1, Sym+W = ~). Send that
        // character's key instead: Super+Alt+W = Super+1. Keys where Alt changes nothing
        // (Enter, Space) keep Alt: Super+Alt+Space stays Super+Alt+Space.
        val layerMeta = (event.metaState or layer) and LAYER_MASK
        if (layerMeta != 0) {
            val kcm = event.keyCharacterMap
            val ch = kcm.get(key, layerMeta) and KeyCharacterMap.COMBINING_ACCENT_MASK
            if (ch != 0 && ch != kcm.get(key, 0)) {
                usKeyFor(ch.toChar())?.let { (usKey, needsShift) ->
                    key = usKey
                    shift = needsShift
                    alt = false
                }
            }
        }
        if (alt) modDown(KeyEvent.KEYCODE_ALT_LEFT) else modUp(KeyEvent.KEYCODE_ALT_LEFT)
        val addedShift = shift && KeyEvent.KEYCODE_SHIFT_RIGHT !in modsDown &&
            modDown(KeyEvent.KEYCODE_SHIFT_LEFT)
        // Super is pressed for each key and let go after it, so it can't get stuck down
        // on the other end when you switch apps with Super on.
        modDown(KeyEvent.KEYCODE_META_LEFT)
        val meta = (event.metaState and KeyEvent.META_CTRL_MASK) or modsMeta()
        owned[event.keyCode] = Sent(key, meta, addedShift, withSuper = true)
        send(KeyEvent.ACTION_DOWN, key, 0, meta)
    }

    /** A key we took over coming back up (the lock or Super may be over by now). */
    private fun release(code: Int, sent: Sent) {
        owned.remove(code)
        when {
            sent === swallow -> {}
            sent.button != null -> buttonUp(sent.button)
            isAlt(code) -> if (owned.keys.none(::isAlt)) modUp(KeyEvent.KEYCODE_ALT_LEFT)
            isModifier(code) -> modUp(code)
            else -> {
                send(KeyEvent.ACTION_UP, sent.code, 0, sent.meta)
                if (sent.addedShift) modUp(KeyEvent.KEYCODE_SHIFT_LEFT)
                if (sent.withSuper && owned.values.none { it.withSuper }) {
                    modUp(KeyEvent.KEYCODE_META_LEFT)
                }
            }
        }
    }

    private fun releaseAll() {
        for ((code, sent) in owned.entries.toList()) release(code, sent)
        for (mod in modsDown.toList()) modUp(mod)
        padHeld.clear()
    }

    /** Returns false if it was already down. */
    private fun modDown(code: Int): Boolean {
        if (!modsDown.add(code)) return false
        send(KeyEvent.ACTION_DOWN, code, 0, modsMeta())
        return true
    }

    private fun modUp(code: Int) {
        if (modsDown.remove(code)) send(KeyEvent.ACTION_UP, code, 0, modsMeta())
    }

    private fun modsMeta() = modsDown.fold(0) { meta, mod -> meta or (META_BITS[mod] ?: 0) }

    private fun send(
        action: Int,
        code: Int,
        repeat: Int,
        meta: Int,
        device: Int = deviceId,
        source: Int = InputDevice.SOURCE_KEYBOARD,
    ) {
        val now = SystemClock.uptimeMillis()
        val event = KeyEvent(now, now, action, code, repeat, meta, device, 0, 0, source)
        val delivered = EmojiKeyboardService.instance?.sendKey(event) == true
        if (traceListener != null) {
            val name = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
            val dir = if (action == KeyEvent.ACTION_DOWN) "down" else "up"
            val pad = if (source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) " gamepad" else ""
            trace("  sent$pad $name $dir meta=0x${Integer.toHexString(meta)}" + if (delivered) "" else " (no app to send to)")
        }
    }

    /** Whether holding [meta] changes the character [code] types (Alt on W: w → 1). */
    private fun changes(kcm: KeyCharacterMap, code: Int, meta: Int): Boolean {
        val ch = kcm.get(code, meta)
        return ch != 0 && ch != kcm.get(code, 0)
    }

    /** The US-keyboard key for [ch], and whether it needs Shift ('!' is Shift+1). */
    private fun usKeyFor(ch: Char): Pair<Int, Boolean>? {
        if (ch == '\u001b') return KeyEvent.KEYCODE_ESCAPE to false // Sym+Q
        val events = usKeyboard.getEvents(charArrayOf(ch)) ?: return null
        val key = events.firstOrNull {
            it.action == KeyEvent.ACTION_DOWN && !KeyEvent.isModifierKey(it.keyCode)
        } ?: return null
        return key.keyCode to key.isShiftPressed
    }

    private fun isModifier(code: Int) = code in META_BITS

    private fun isAlt(code: Int) = code == KeyEvent.KEYCODE_ALT_LEFT || code == KeyEvent.KEYCODE_ALT_RIGHT

    private fun loadSymReplaces(): Map<Int, Int> {
        val keyLine = Regex("""^\s*key\s+(\w+)\s*\{""")
        val symLine = Regex("""^\s*sym\s*:\s*replace\s+(\w+)""")
        val out = HashMap<Int, Int>()
        var key = KeyEvent.KEYCODE_UNKNOWN
        resources.openRawResource(R.raw.titan2_custom).bufferedReader().useLines { lines ->
            for (line in lines) {
                keyLine.find(line)?.let { key = KeyEvent.keyCodeFromString("KEYCODE_" + it.groupValues[1]) }
                symLine.find(line)?.let {
                    val to = KeyEvent.keyCodeFromString("KEYCODE_" + it.groupValues[1])
                    if (key != KeyEvent.KEYCODE_UNKNOWN && to != KeyEvent.KEYCODE_UNKNOWN) out[key] = to
                }
            }
        }
        return out
    }

    // ---- badge ----

    /** A row of chips (CAPS, ALT, SYM, SUPER, PAD) in the middle of the status bar. */
    private fun addBadge() {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER
        for (lock in Lock.entries) {
            val chip = TextView(this)
            chip.text = lock.label
            chip.textSize = 10f
            chip.typeface = Typeface.DEFAULT_BOLD
            chip.setTextColor(Color.WHITE)
            chip.background = GradientDrawable().apply {
                cornerRadius = dp(6).toFloat()
                setColor(lock.color)
            }
            chip.setPadding(dp(5), dp(1), dp(5), dp(1))
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            params.setMargins(dp(2), 0, dp(2), 0)
            row.addView(chip, params)
        }

        val statusBarId = resources.getIdentifier("status_bar_height", "dimen", "android")
        val height = if (statusBarId != 0) resources.getDimensionPixelSize(statusBarId) else dp(24)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        )
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.title = "Titan2 key locks"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            params.fitInsetsTypes = 0
        } else {
            params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        try {
            getSystemService(WindowManager::class.java).addView(row, params)
            badge = row
        } catch (e: Exception) {
            trace("couldn't show the lock badge: ${e.message}")
        }
        updateBadge()
    }

    private fun updateBadge() {
        val row = badge ?: return
        for (lock in Lock.entries) {
            row.getChildAt(lock.ordinal).visibility = if (lock in locks) View.VISIBLE else View.GONE
        }
        row.visibility = if (locks.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

    private fun trace(message: String) {
        traceListener?.invoke(message)
    }
}
