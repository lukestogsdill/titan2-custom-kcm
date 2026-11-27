package dev.titan2.keys

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The Titan 2's keyboard, drawn with the keys where they sit on the phone. Each key shows
 * what it types on every layer: Base in the middle, Alt (orange) top left, Sym (green) top
 * right, and Shift (blue) bottom left when it isn't just the capital letter. Keys you changed
 * get a pink dot. Tapping a key calls [onKeyTap]; the [selected] one gets a pulsing ring.
 *
 * The top row (Shift, Sym, Back, Recent, Fn, Alt) is drawn for orientation only.
 * Everything is laid out in a 1000-wide design space and scaled to fit.
 */
@SuppressLint("ViewConstructor")
class KeyboardView(context: Context) : View(context) {

    /** Called with the key's keycode when one is tapped. Null leaves touches to the parent. */
    var onKeyTap: ((Int) -> Unit)? = null

    /** What the keys type. Nothing is drawn on them until it's set. */
    var layout: Keymap? = null
        set(value) {
            field = value
            invalidate()
        }

    /** Keys to mark as changed from the built-in layout. */
    var changed: Set<Int> = emptySet()
        set(value) {
            field = value
            invalidate()
        }

    /** The key being edited. */
    var selected: Int? = null
        set(value) {
            field = value
            postInvalidateOnAnimation()
        }

    private class Cap(val key: Int, val rect: RectF)

    private class ModCap(val label: String, val color: Int, val rect: RectF)

    private val caps = ArrayList<Cap>()
    private val modCaps = ArrayList<ModCap>()
    private val indexOf = HashMap<Int, Int>()

    // Press animation: each key eases from press[i] toward goal[i].
    private val press: FloatArray
    private val goal: FloatArray
    private var lastFrame = 0L
    private var touched: Int? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val capFill = LinearGradient(0f, 0f, 0f, ROW_H, 0xFF2A3040.toInt(), 0xFF1D212B.toInt(), Shader.TileMode.CLAMP)

    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    init {
        val modW = (DESIGN_W - 2 * MARGIN - 5 * GAP) / 6
        listOf(
            Triple("⇧", Ui.BLUE, 0), Triple("sym", Ui.GREEN, 1), Triple("◁", Ui.MUTED, 2),
            Triple("□", Ui.MUTED, 3), Triple("fn", Ui.MUTED, 4), Triple("alt", Ui.ORANGE, 5),
        ).forEach { (label, color, i) ->
            val x = MARGIN + i * (modW + GAP)
            modCaps += ModCap(label, color, RectF(x, MARGIN, x + modW, MARGIN + MOD_H))
        }
        val unit = (DESIGN_W - 2 * MARGIN - 9 * GAP) / 10
        var y = MARGIN + MOD_H + ROW_GAP
        for (row in KeyLayout.ROWS) {
            var x = MARGIN
            for (key in row) {
                val w = if (key == KeyEvent.KEYCODE_SPACE) unit * 2 + GAP else unit
                indexOf[key] = caps.size
                caps += Cap(key, RectF(x, y, x + w, y + ROW_H))
                x += w + GAP
            }
            y += ROW_H + GAP
        }
        press = FloatArray(caps.size)
        goal = FloatArray(caps.size)
    }

    fun setPressed(key: Int, down: Boolean) {
        val i = indexOf[key] ?: return
        goal[i] = if (down) 1f else 0f
        postInvalidateOnAnimation()
    }

    /** A quick press and release, e.g. when the key was pressed on the real keyboard. */
    fun flash(key: Int) {
        setPressed(key, true)
        postDelayed({ setPressed(key, false) }, 160)
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val width = MeasureSpec.getSize(widthSpec)
        setMeasuredDimension(width, (width * DESIGN_H / DESIGN_W).roundToInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        scale = min(w / DESIGN_W, h / DESIGN_H)
        offsetX = (w - DESIGN_W * scale) / 2
        offsetY = (h - DESIGN_H * scale) / 2
    }

    override fun onDraw(canvas: Canvas) {
        val moving = step()
        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)
        for (mod in modCaps) drawModCap(canvas, mod)
        for ((i, cap) in caps.withIndex()) drawCap(canvas, cap, press[i])
        selected?.let { indexOf[it] }?.let { drawSelection(canvas, caps[it]) }
        canvas.restore()
        if (moving || selected != null) postInvalidateOnAnimation() else lastFrame = 0L
    }

    /** Advances the press animation. Returns true while something is still moving. */
    private fun step(): Boolean {
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 16f else (now - lastFrame).coerceIn(1L, 64L).toFloat()
        lastFrame = now
        var moving = false
        for (i in press.indices) {
            val target = goal[i]
            if (press[i] == target) continue
            val tau = if (target > press[i]) 26f else 90f // snap down, ease back up
            val next = press[i] + (target - press[i]) * (1f - exp(-dt / tau))
            press[i] = if (abs(target - next) < 0.01f) target else next
            if (press[i] != target) moving = true
        }
        return moving
    }

    // ---- keys ----

    private fun drawModCap(canvas: Canvas, mod: ModCap) {
        val r = mod.rect
        fill(0xFF161920.toInt())
        canvas.drawRoundRect(r, 14f, 14f, paint)
        stroke(0xFF262B37.toInt(), 2f)
        canvas.drawRoundRect(r, 14f, 14f, paint)
        text(canvas, mod.label, r.centerX(), r.centerY(), 24f, r.width() - 20f, Ui.withAlpha(mod.color, 0.75f), Paint.Align.CENTER)
    }

    private fun drawCap(canvas: Canvas, cap: Cap, pressed: Float) {
        val sink = 4f * pressed
        rect.set(cap.rect)
        rect.offset(0f, 5f)
        fill(0xFF07080B.toInt())
        canvas.drawRoundRect(rect, 16f, 16f, paint)

        rect.set(cap.rect)
        rect.offset(0f, sink)
        canvas.save()
        canvas.translate(rect.left, rect.top)
        paint.style = Paint.Style.FILL
        paint.shader = capFill
        canvas.drawRoundRect(0f, 0f, rect.width(), rect.height(), 16f, 16f, paint)
        paint.shader = null
        if (pressed > 0f) {
            fill(Ui.withAlpha(Ui.PINK, 0.38f * pressed))
            canvas.drawRoundRect(0f, 0f, rect.width(), rect.height(), 16f, 16f, paint)
        }
        canvas.restore()
        stroke(Ui.mix(0xFF3A4152.toInt(), Ui.PINK, pressed), 2f)
        canvas.drawRoundRect(rect, 16f, 16f, paint)

        val layout = layout ?: return
        val key = cap.key
        val w = rect.width()
        val base = layout[key, Layer.BASE]
        val shift = layout[key, Layer.SHIFT]
        // A letter shows its capital, like a printed key. Anything else shows what it types.
        val letter = layout.capsLocks(key) && (base as Output.Text).text.length == 1
        val baseLabel = when {
            letter -> (shift as Output.Text).text
            base == null -> ""
            else -> KeyLayout.capLabel(base)
        }
        if (key == KeyEvent.KEYCODE_SPACE && base == Output.Text(" ")) {
            fill(Ui.TEXT)
            canvas.drawRoundRect(rect.centerX() - 38f, rect.top + ROW_H * 0.62f - 3f, rect.centerX() + 38f, rect.top + ROW_H * 0.62f + 3f, 3f, 3f, paint)
        } else {
            val color = if (base == Output.None) Ui.MUTED else Ui.TEXT
            text(canvas, baseLabel, rect.centerX(), rect.top + ROW_H * 0.6f, if (baseLabel.length == 1) 38f else 26f, w - 18f, color, Paint.Align.CENTER)
        }
        if (shift != null && !letter) {
            text(canvas, KeyLayout.capLabel(shift), rect.left + 11f, rect.bottom - 17f, 17f, w / 2 - 8f, Ui.BLUE, Paint.Align.LEFT)
        }
        val corner = (w - 2 * 11f - 8f) / 2 // each top corner gets half the key, with a gap between
        // A layer that types the same as Base (Alt+Space is a space) has nothing to show.
        layout[key, Layer.ALT]?.takeIf { layout.changes(key, Layer.ALT) }?.let {
            text(canvas, KeyLayout.capLabel(it), rect.left + 11f, rect.top + 20f, 19f, corner, Ui.ORANGE, Paint.Align.LEFT)
        }
        layout[key, Layer.SYM]?.takeIf { layout.changes(key, Layer.SYM) }?.let {
            val color = if (it == Output.Key(KeyLayout.CONTROLLER)) Ui.PINK else Ui.GREEN
            text(canvas, KeyLayout.capLabel(it), rect.right - 11f, rect.top + 20f, 19f, corner, color, Paint.Align.RIGHT)
        }
        if (key in changed) {
            fill(Ui.PINK)
            canvas.drawCircle(rect.right - 13f, rect.bottom - 13f, 6f, paint)
        }
    }

    private fun drawSelection(canvas: Canvas, cap: Cap) {
        val pulse = 0.55f + 0.45f * sin(SystemClock.uptimeMillis() / 900f * 2f * Math.PI.toFloat())
        stroke(Ui.withAlpha(Ui.PINK, pulse), 5f)
        rect.set(cap.rect)
        rect.inset(-6f, -6f)
        canvas.drawRoundRect(rect, 21f, 21f, paint)
        paint.style = Paint.Style.FILL
    }

    // ---- drawing helpers ----

    private fun fill(color: Int) {
        paint.style = Paint.Style.FILL
        paint.color = color
    }

    private fun stroke(color: Int, width: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width
        paint.color = color
    }

    /** Text at (x, y) vertically centered, shrunk to fit [maxWidth]. */
    private fun text(canvas: Canvas, s: String, x: Float, y: Float, size: Float, maxWidth: Float, color: Int, align: Paint.Align) {
        if (s.isEmpty()) return
        ink.typeface = Ui.BOLD
        ink.textAlign = align
        // Arrows come out small next to letters in this font.
        ink.textSize = if (s in ARROWS) size * 1.4f else size
        ink.color = color
        val width = ink.measureText(s)
        if (width > maxWidth) ink.textSize = ink.textSize * maxWidth / width
        canvas.drawText(s, x, y + ink.textSize * 0.36f, ink)
    }

    // ---- touch ----

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val tap = onKeyTap ?: return false
        val x = (event.x - offsetX) / scale
        val y = (event.y - offsetY) / scale
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touched = hit(x, y)
                touched?.let { setPressed(it, true) }
            }
            MotionEvent.ACTION_UP -> {
                val key = touched
                touched = null
                if (key != null) {
                    setPressed(key, false)
                    if (hit(x, y) == key) {
                        performClick()
                        tap(key)
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                touched?.let { setPressed(it, false) }
                touched = null
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun hit(x: Float, y: Float): Int? = caps.firstOrNull { cap ->
        x >= cap.rect.left - GAP / 2 && x <= cap.rect.right + GAP / 2 &&
            y >= cap.rect.top - GAP / 2 && y <= cap.rect.bottom + GAP / 2
    }?.key

    private companion object {
        const val DESIGN_W = 1000f
        const val MARGIN = 10f
        const val GAP = 10f
        const val MOD_H = 52f
        const val ROW_GAP = 18f
        const val ROW_H = 96f
        const val DESIGN_H = MARGIN + MOD_H + ROW_GAP + 3 * ROW_H + 2 * GAP + MARGIN + 5f
        val ARROWS = setOf("←", "→", "↑", "↓")
    }
}
