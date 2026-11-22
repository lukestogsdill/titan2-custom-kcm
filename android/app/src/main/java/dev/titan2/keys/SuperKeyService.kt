package dev.titan2.keys

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Double-tap and hold Sym to use it as Super (Meta / the Windows key).
 *
 * While Super is held, every key pressed is taken over here, the Sym layer is undone
 * (Sym+Enter is INSERT in the .kcm, but we want Super+Enter), and the result is sent with
 * Meta to the focused app through the emoji keyboard. Keys sent that way go straight to the
 * app, so Android's own Meta shortcuts can't swallow them.
 *
 * Why an accessibility service and not just the keyboard: Moonlight reads keys in
 * StreamView.onKeyPreIme, before any keyboard sees them. Only an accessibility key filter
 * gets them first. Keys pressed outside a Super hold are never touched.
 */
class SuperKeyService : AccessibilityService() {

    companion object {
        const val TRIGGER = KeyEvent.KEYCODE_SYM

        /** The first press must be a tap (no other key, released within this time)... */
        const val TAP_MAX_MS = 250L

        /** ...and the second press must come within this time of releasing it. */
        const val DOUBLE_TAP_GAP_MS = 300L

        /**
         * The `replace` entries of the Sym layer in titan2-custom.kcm, mapped back to the
         * key that was pressed. Keep this in sync with the .kcm file. (Character entries
         * like `sym: '~'` don't change the keycode, so they need nothing here.)
         */
        private val SYM_LAYER_KEYS = mapOf(
            KeyEvent.KEYCODE_TAB to KeyEvent.KEYCODE_A,
            KeyEvent.KEYCODE_DPAD_LEFT to KeyEvent.KEYCODE_H,
            KeyEvent.KEYCODE_DPAD_DOWN to KeyEvent.KEYCODE_J,
            KeyEvent.KEYCODE_DPAD_UP to KeyEvent.KEYCODE_K,
            KeyEvent.KEYCODE_DPAD_RIGHT to KeyEvent.KEYCODE_L,
            KeyEvent.KEYCODE_PICTSYMBOLS to KeyEvent.KEYCODE_B,
            KeyEvent.KEYCODE_MOVE_HOME to KeyEvent.KEYCODE_N,
            KeyEvent.KEYCODE_MOVE_END to KeyEvent.KEYCODE_M,
            KeyEvent.KEYCODE_SEARCH to KeyEvent.KEYCODE_SPACE,
            KeyEvent.KEYCODE_INSERT to KeyEvent.KEYCODE_ENTER,
        )

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
    }

    /** What we sent for a key that went down during a Super hold. */
    private class Sent(val code: Int, val addedShift: Boolean)

    private var lastTapUp = 0L
    private var symDownAt = 0L
    private var symPressIsTap = false
    private var superOn = false
    private var deviceId = KeyCharacterMap.VIRTUAL_KEYBOARD

    /** Keys pressed during a Super hold, by hardware keycode. Their releases are ours too. */
    private val owned = HashMap<Int, Sent>()

    /** Modifier keys we've sent down to the app and not released yet. */
    private val modsDown = LinkedHashSet<Int>()

    private val usKeyboard by lazy { KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD) }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        releaseAll()
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        val down = event.action == KeyEvent.ACTION_DOWN
        if (code == TRIGGER) return onTrigger(event, down)

        if (down) {
            symPressIsTap = false // Sym held with another key = the Sym layer, not a tap
            lastTapUp = 0L
        }
        val sent = owned[code]
        if (sent != null) {
            if (!down) release(code, sent)
            else if (!isModifier(code)) send(KeyEvent.ACTION_DOWN, sent.code, event.repeatCount, event.metaState)
            return true
        }
        if (!down || !superOn) return false
        press(event)
        return true
    }

    private fun onTrigger(event: KeyEvent, down: Boolean): Boolean {
        val t = event.eventTime
        if (event.repeatCount == 0) trace("service saw SYM " + if (down) "down" else "up")
        if (down) {
            if (event.repeatCount > 0) return superOn
            val doubleTap = lastTapUp != 0L && t - lastTapUp <= DOUBLE_TAP_GAP_MS
            lastTapUp = 0L
            symDownAt = t
            symPressIsTap = true
            return doubleTap && startSuper(event)
        }
        if (superOn) {
            stopSuper()
            return true
        }
        lastTapUp = if (symPressIsTap && t - symDownAt <= TAP_MAX_MS) t else 0L
        return false
    }

    private fun startSuper(event: KeyEvent): Boolean {
        if (EmojiKeyboardService.instance?.canSendKeys != true) {
            trace("Super: Titan2 Emoji isn't the active keyboard (or nothing has focus)")
            return false
        }
        superOn = true
        deviceId = event.deviceId
        modDown(KeyEvent.KEYCODE_META_LEFT)
        getSystemService(Vibrator::class.java)
            ?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        trace("Super ON")
        return true
    }

    private fun stopSuper() {
        superOn = false
        modUp(KeyEvent.KEYCODE_META_LEFT)
        trace("Super OFF")
    }

    /** A key going down while Super is held. */
    private fun press(event: KeyEvent) {
        val code = event.keyCode
        if (isModifier(code)) {
            owned[code] = Sent(code, false)
            // Alt is decided per key, below. Shift and Ctrl go through as they are.
            if (!isAlt(code)) modDown(code)
            return
        }

        var key = SYM_LAYER_KEYS[code] ?: code
        var shift = false
        var alt = owned.keys.any(::isAlt)
        if (alt) {
            // On the Titan, Alt picks a character (Alt+W = 1). When it does, send that
            // character's key instead: Super+Alt+W = Super+1. Keys where Alt changes
            // nothing (Enter, Space) keep Alt: Super+Alt+Space stays Super+Alt+Space.
            val kcm = event.keyCharacterMap
            val ch = kcm.get(key, KeyEvent.META_ALT_ON)
            if (ch != 0 && ch != kcm.get(key, 0)) {
                usKeyFor(ch.toChar())?.let { (usKey, needsShift) ->
                    key = usKey
                    shift = needsShift
                    alt = false
                }
            }
        }
        if (alt) modDown(KeyEvent.KEYCODE_ALT_LEFT) else modUp(KeyEvent.KEYCODE_ALT_LEFT)
        val addShift = shift && KeyEvent.KEYCODE_SHIFT_LEFT !in modsDown && KeyEvent.KEYCODE_SHIFT_RIGHT !in modsDown
        if (addShift) modDown(KeyEvent.KEYCODE_SHIFT_LEFT)

        owned[code] = Sent(key, addShift)
        send(KeyEvent.ACTION_DOWN, key, 0, event.metaState)
    }

    /** A key that went down during a Super hold coming back up (Super may be over by now). */
    private fun release(code: Int, sent: Sent) {
        owned.remove(code)
        if (isAlt(code)) {
            if (owned.keys.none(::isAlt)) modUp(KeyEvent.KEYCODE_ALT_LEFT)
            return
        }
        if (isModifier(code)) {
            modUp(code)
            return
        }
        send(KeyEvent.ACTION_UP, sent.code, 0, 0)
        if (sent.addedShift) modUp(KeyEvent.KEYCODE_SHIFT_LEFT)
    }

    private fun releaseAll() {
        for ((code, sent) in owned.entries.toList()) release(code, sent)
        if (superOn) stopSuper()
        for (mod in modsDown.toList()) modUp(mod)
    }

    private fun modDown(code: Int) {
        if (modsDown.add(code)) send(KeyEvent.ACTION_DOWN, code, 0, 0)
    }

    private fun modUp(code: Int) {
        if (modsDown.remove(code)) send(KeyEvent.ACTION_UP, code, 0, 0)
    }

    /**
     * Sends a key with the modifiers we're holding. Ctrl from the hardware event is kept
     * too, in case the Fn→Control shortcut only sets the Ctrl flag without a Ctrl key.
     */
    private fun send(action: Int, code: Int, repeat: Int, hardwareMeta: Int) {
        var meta = hardwareMeta and KeyEvent.META_CTRL_MASK
        for (mod in modsDown) meta = meta or (META_BITS[mod] ?: 0)
        val now = SystemClock.uptimeMillis()
        val event = KeyEvent(now, now, action, code, repeat, meta, deviceId, 0, 0, InputDevice.SOURCE_KEYBOARD)
        val delivered = EmojiKeyboardService.instance?.sendKey(event) == true
        if (traceListener != null) {
            val name = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
            val dir = if (action == KeyEvent.ACTION_DOWN) "down" else "up"
            trace("  sent $name $dir meta=0x${Integer.toHexString(meta)}" + if (delivered) "" else " (no app to send to)")
        }
    }

    /** The US-keyboard key for [ch], and whether it needs Shift ('!' is Shift+1). */
    private fun usKeyFor(ch: Char): Pair<Int, Boolean>? {
        val events = usKeyboard.getEvents(charArrayOf(ch)) ?: return null
        val key = events.firstOrNull {
            it.action == KeyEvent.ACTION_DOWN && !KeyEvent.isModifierKey(it.keyCode)
        } ?: return null
        return key.keyCode to key.isShiftPressed
    }

    private fun isModifier(code: Int) = code in META_BITS

    private fun isAlt(code: Int) = code == KeyEvent.KEYCODE_ALT_LEFT || code == KeyEvent.KEYCODE_ALT_RIGHT

    private fun trace(message: String) {
        traceListener?.invoke(message)
    }
}
