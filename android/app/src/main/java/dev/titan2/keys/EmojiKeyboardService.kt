package dev.titan2.keys

import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View

/**
 * An on-screen keyboard that shows nothing (like "No Keyboard") until Sym+B opens the
 * emoji panel. It also gives [SuperKeyService] a way to send keys to the focused app,
 * for the modifier locks and Super.
 */
class EmojiKeyboardService : InputMethodService() {

    companion object {
        /**
         * Sym+B sends this keycode (see `key B` in titan2-custom.kcm). [SuperKeyService]
         * normally catches it first; handling it here too keeps Sym+B working without it.
         */
        const val TOGGLE_KEY = KeyEvent.KEYCODE_PICTSYMBOLS

        /** The running keyboard. [SuperKeyService] runs in the same process and uses it. */
        @Volatile
        var instance: EmojiKeyboardService? = null
            private set
    }

    private var panelOpen = false
    private var panel: EmojiPanel? = null
    private val swallowedUps = HashSet<Int>()

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onCreateInputView(): View =
        EmojiPanel(this, ::pick, ::closePanel).also { panel = it }

    // Only show when Sym+B asked for it. This also turns down the system's own show
    // requests, e.g. when a text field gets focus or the Sym "show keyboard" shortcut.
    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        return panelOpen
    }

    override fun onShowInputRequested(flags: Int, configChange: Boolean) = panelOpen

    override fun onEvaluateFullscreenMode() = false

    override fun onWindowHidden() {
        super.onWindowHidden()
        panelOpen = false
    }

    override fun onFinishInput() {
        super.onFinishInput()
        swallowedUps.clear()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val used = when {
            keyCode == TOGGLE_KEY -> {
                if (event.repeatCount == 0) togglePanel()
                true
            }
            panelOpen && isInputViewShown -> panel?.onKey(event) == true
            else -> false
        }
        if (!used) return super.onKeyDown(keyCode, event)
        swallowedUps += keyCode
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        swallowedUps.remove(keyCode) || super.onKeyUp(keyCode, event)

    /** True when there is an app to send keys to. */
    val canSendKeys: Boolean
        get() = currentInputConnection != null

    /**
     * Delivers [event] straight to the focused app. Unlike a real key press this skips
     * Android's own shortcut handling, so Meta+key reaches the app (and Moonlight) intact.
     */
    fun sendKey(event: KeyEvent): Boolean {
        val ic = currentInputConnection ?: return false
        ic.sendKeyEvent(event)
        return true
    }

    /** True while the emoji panel is on screen. */
    val panelShowing: Boolean
        get() = panelOpen && isInputViewShown

    /** Gives the open panel a key that [SuperKeyService] changed (e.g. Sym lock + H = ←). */
    fun panelKey(event: KeyEvent): Boolean = panelShowing && panel?.onKey(event) == true

    fun togglePanel() {
        if (panelOpen) closePanel() else openPanel()
    }

    private fun openPanel() {
        if (currentInputConnection == null) return
        panel?.reset()
        panelOpen = true
        requestShowSelf(0)
    }

    private fun closePanel() {
        panelOpen = false
        requestHideSelf(0)
    }

    private fun pick(emoji: String, fromKeyboard: Boolean) {
        currentInputConnection?.commitText(emoji, 1)
        Recents.add(this, emoji)
        // Typed search + Enter is a quick insert, so get out of the way afterwards.
        // Tapping keeps the panel open for picking several.
        if (fromKeyboard) closePanel()
    }
}
