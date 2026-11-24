package dev.titan2.keys

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A drawn gamepad. Each button shows the key bound to it, lights up and sinks while it's
 * pressed ([setPressed]), and the whole pad tilts with the D-pad. Tapping a button calls
 * [onButtonTap]; the one being rebound ([selected]) gets a pulsing ring.
 *
 * Everything is laid out in a 1000 x 640 design space and scaled to fit.
 */
@SuppressLint("ViewConstructor")
class GamepadView(context: Context) : View(context) {

    /** Called with the button's keycode when one is tapped. Null leaves touches to the parent. */
    var onButtonTap: ((Int) -> Unit)? = null

    /** The button being rebound. */
    var selected: Int? = null
        set(value) {
            field = value
            postInvalidateOnAnimation()
        }

    /** What to print on each button: its key(s). Buttons missing here are unbound. */
    var labels: Map<Int, String> = emptyMap()
        set(value) {
            field = value
            invalidate()
        }

    /** Lights the player LED: the controller layer is on. */
    var live = false
        set(value) {
            field = value
            invalidate()
        }

    private enum class Kind { TRIGGER, BUMPER, STICK, DPAD, FACE, SMALL, GUIDE }

    /** One button. Round kinds use [w] as the diameter. */
    private class Part(
        val button: Int,
        val kind: Kind,
        val cx: Float,
        val cy: Float,
        val w: Float,
        val h: Float,
        val color: Int,
        val name: String,
    )

    private val parts = listOf(
        Part(KeyEvent.KEYCODE_BUTTON_L2, Kind.TRIGGER, 235f, 78f, 150f, 62f, Ui.PINK, "LT"),
        Part(KeyEvent.KEYCODE_BUTTON_R2, Kind.TRIGGER, 765f, 78f, 150f, 62f, Ui.PINK, "RT"),
        Part(KeyEvent.KEYCODE_BUTTON_L1, Kind.BUMPER, 245f, 134f, 220f, 38f, Ui.PINK, "LB"),
        Part(KeyEvent.KEYCODE_BUTTON_R1, Kind.BUMPER, 755f, 134f, 220f, 38f, Ui.PINK, "RB"),
        Part(KeyEvent.KEYCODE_BUTTON_THUMBL, Kind.STICK, 250f, 282f, 116f, 116f, Ui.PINK, "L3"),
        Part(KeyEvent.KEYCODE_BUTTON_THUMBR, Kind.STICK, 615f, 410f, 108f, 108f, Ui.PINK, "R3"),
        Part(KeyEvent.KEYCODE_DPAD_UP, Kind.DPAD, 385f, 360f, 46f, 56f, Ui.PINK, "Up"),
        Part(KeyEvent.KEYCODE_DPAD_DOWN, Kind.DPAD, 385f, 460f, 46f, 56f, Ui.PINK, "Down"),
        Part(KeyEvent.KEYCODE_DPAD_LEFT, Kind.DPAD, 335f, 410f, 56f, 46f, Ui.PINK, "Left"),
        Part(KeyEvent.KEYCODE_DPAD_RIGHT, Kind.DPAD, 435f, 410f, 56f, 46f, Ui.PINK, "Right"),
        Part(KeyEvent.KEYCODE_BUTTON_Y, Kind.FACE, 750f, 214f, 64f, 64f, Ui.YELLOW, "Y"),
        Part(KeyEvent.KEYCODE_BUTTON_A, Kind.FACE, 750f, 350f, 64f, 64f, Ui.GREEN, "A"),
        Part(KeyEvent.KEYCODE_BUTTON_X, Kind.FACE, 682f, 282f, 64f, 64f, Ui.BLUE, "X"),
        Part(KeyEvent.KEYCODE_BUTTON_B, Kind.FACE, 818f, 282f, 64f, 64f, Ui.RED, "B"),
        Part(KeyEvent.KEYCODE_BUTTON_SELECT, Kind.SMALL, 432f, 300f, 40f, 40f, Ui.PINK, "SELECT"),
        Part(KeyEvent.KEYCODE_BUTTON_START, Kind.SMALL, 568f, 300f, 40f, 40f, Ui.PINK, "START"),
        Part(KeyEvent.KEYCODE_BUTTON_MODE, Kind.GUIDE, 500f, 222f, 62f, 62f, Ui.VIOLET, "Guide"),
    )
    private val indexOf = parts.withIndex().associate { (i, part) -> part.button to i }

    // Press animation: each part eases from press[i] toward goal[i].
    private val press = FloatArray(parts.size)
    private val goal = FloatArray(parts.size)
    private var lastFrame = 0L
    private var touched: Int? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val rect = RectF()
    private val shape = Path()

    private val body = Path().apply {
        moveTo(330f, 150f)
        lineTo(670f, 150f)
        cubicTo(800f, 140f, 885f, 170f, 930f, 290f)
        cubicTo(980f, 420f, 995f, 565f, 910f, 615f)
        cubicTo(845f, 650f, 785f, 600f, 745f, 550f)
        cubicTo(715f, 515f, 665f, 505f, 500f, 505f)
        cubicTo(335f, 505f, 285f, 515f, 255f, 550f)
        cubicTo(215f, 600f, 155f, 650f, 90f, 615f)
        cubicTo(5f, 565f, 20f, 420f, 70f, 290f)
        cubicTo(115f, 170f, 200f, 140f, 330f, 150f)
        close()
    }
    private val bodyFill = LinearGradient(0f, 150f, 0f, 630f, 0xFF2D3344.toInt(), 0xFF15181F.toInt(), Shader.TileMode.CLAMP)
    private val sheen = RadialGradient(500f, 140f, 520f, 0x1FFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
    private val shadow = RadialGradient(500f, 610f, 430f, 0x88000000.toInt(), 0x00000000, Shader.TileMode.CLAMP)
    private val led = LinearGradient(445f, 0f, 555f, 0f, Ui.PINK, Ui.VIOLET, Shader.TileMode.CLAMP)

    private var scale = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    fun setPressed(button: Int, down: Boolean) {
        val i = indexOf[button] ?: return
        goal[i] = if (down) 1f else 0f
        postInvalidateOnAnimation()
    }

    /** A quick press and release, e.g. when a key gets bound to [button]. */
    fun flash(button: Int) {
        setPressed(button, true)
        postDelayed({ setPressed(button, false) }, 160)
    }

    fun releaseAll() {
        goal.fill(0f)
        postInvalidateOnAnimation()
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

        paint.shader = shadow
        canvas.drawOval(110f, 570f, 890f, 650f, paint)
        paint.shader = null

        // The pad leans with the D-pad.
        canvas.translate(0f, (amount(KeyEvent.KEYCODE_DPAD_DOWN) - amount(KeyEvent.KEYCODE_DPAD_UP)) * 7f)
        canvas.rotate((amount(KeyEvent.KEYCODE_DPAD_RIGHT) - amount(KeyEvent.KEYCODE_DPAD_LEFT)) * 4f, 500f, 380f)

        for (part in parts) if (part.kind == Kind.TRIGGER || part.kind == Kind.BUMPER) drawShoulder(canvas, part)
        drawBody(canvas)
        drawDpadBase(canvas)
        for (part in parts) {
            when (part.kind) {
                Kind.STICK -> drawStick(canvas, part)
                Kind.DPAD -> drawDpadArm(canvas, part)
                Kind.FACE -> drawFace(canvas, part)
                Kind.SMALL -> drawSmall(canvas, part)
                Kind.GUIDE -> drawGuide(canvas, part)
                else -> {}
            }
        }
        selected?.let { indexOf[it] }?.let { drawSelection(canvas, parts[it]) }
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

    private fun amount(button: Int) = indexOf[button]?.let { press[it] } ?: 0f

    private fun amount(part: Part) = press[indexOf.getValue(part.button)]

    // ---- parts ----

    private fun drawBody(canvas: Canvas) {
        paint.style = Paint.Style.FILL
        paint.shader = bodyFill
        canvas.drawPath(body, paint)
        paint.shader = sheen
        canvas.save()
        canvas.clipPath(body)
        canvas.drawRect(0f, 0f, DESIGN_W, DESIGN_H, paint)
        canvas.restore()
        paint.shader = null
        stroke(0xFF3B4254.toInt(), 3f)
        canvas.drawPath(body, paint)
        paint.style = Paint.Style.FILL

        // Player LED above the Guide button.
        paint.shader = led
        paint.alpha = if (live) 255 else 45
        canvas.drawRoundRect(445f, 166f, 555f, 174f, 4f, 4f, paint)
        paint.shader = null
        paint.alpha = 255
        if (live) {
            paint.color = Ui.withAlpha(Ui.PINK, 0.18f)
            canvas.drawRoundRect(435f, 158f, 565f, 182f, 12f, 12f, paint)
        }
    }

    private fun drawShoulder(canvas: Canvas, part: Part) {
        val a = amount(part)
        val trigger = part.kind == Kind.TRIGGER
        val dy = a * if (trigger) 11f else 5f
        rect.set(part.cx - part.w / 2, part.cy - part.h / 2 + dy, part.cx + part.w / 2, part.cy + part.h / 2 + dy)
        val radius = if (trigger) 26f else 19f
        fill(Ui.mix(if (trigger) 0xFF252A37.toInt() else 0xFF2C3242.toInt(), part.color, a * 0.9f))
        canvas.drawRoundRect(rect, radius, radius, paint)
        stroke(0xFF3B4254.toInt(), 2f)
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.style = Paint.Style.FILL

        val key = labels[part.button]
        val nameColor = Ui.mix(Ui.MUTED, Ui.TEXT, a)
        if (trigger) {
            text(canvas, part.name, part.cx, rect.top + 19f, 15f, part.w, nameColor)
            text(canvas, key ?: "—", part.cx, rect.top + 44f, 24f, part.w - 20f, if (key == null) Ui.MUTED else Ui.TEXT)
        } else {
            text(canvas, part.name, part.cx - 62f, rect.centerY(), 15f, 50f, nameColor)
            text(canvas, key ?: "—", part.cx + 18f, rect.centerY(), 21f, 120f, if (key == null) Ui.MUTED else Ui.TEXT)
        }
    }

    private fun drawStick(canvas: Canvas, part: Part) {
        val a = amount(part)
        val r = part.w / 2
        fill(0xFF0F1218.toInt())
        canvas.drawCircle(part.cx, part.cy, r + 14f, paint)
        stroke(0xFF2A2F3D.toInt(), 2f)
        canvas.drawCircle(part.cx, part.cy, r + 14f, paint)

        val cap = r * (1f - 0.08f * a)
        fill(Ui.mix(0xFF2E3445.toInt(), part.color, a * 0.85f))
        canvas.drawCircle(part.cx, part.cy, cap, paint)
        stroke(Ui.mix(0xFF454D61.toInt(), 0xFFFFFFFF.toInt(), a * 0.5f), 6f)
        canvas.drawCircle(part.cx, part.cy, cap - 9f, paint)
        paint.style = Paint.Style.FILL

        val key = labels[part.button]
        text(canvas, key ?: "—", part.cx, part.cy - 6f, 26f, cap * 1.4f, if (key == null) Ui.MUTED else Ui.TEXT)
        text(canvas, part.name, part.cx, part.cy + 24f, 13f, cap, Ui.mix(Ui.MUTED, Ui.TEXT, a))
    }

    private fun drawDpadBase(canvas: Canvas) {
        val cx = 385f
        val cy = 410f
        fill(0xFF252A37.toInt())
        canvas.drawRoundRect(cx - 23f, cy - 78f, cx + 23f, cy + 78f, 10f, 10f, paint)
        canvas.drawRoundRect(cx - 78f, cy - 23f, cx + 78f, cy + 23f, 10f, 10f, paint)
        fill(0xFF1B1F29.toInt())
        canvas.drawCircle(cx, cy, 11f, paint)
    }

    private fun drawDpadArm(canvas: Canvas, part: Part) {
        val a = amount(part)
        rect.set(part.cx - part.w / 2, part.cy - part.h / 2, part.cx + part.w / 2, part.cy + part.h / 2)
        if (a > 0f) {
            fill(Ui.withAlpha(part.color, 0.95f * a))
            canvas.drawRoundRect(rect, 10f, 10f, paint)
        }
        val key = labels[part.button]
        if (key != null) {
            text(canvas, key, part.cx, part.cy, 21f, part.w - 6f, Ui.TEXT)
        } else {
            // An arrow pointing out from the middle.
            val dx = (part.cx - 385f).let { if (it == 0f) 0f else it / abs(it) }
            val dy = (part.cy - 410f).let { if (it == 0f) 0f else it / abs(it) }
            shape.reset()
            shape.moveTo(part.cx + dx * 9f, part.cy + dy * 9f)
            shape.lineTo(part.cx - dx * 6f - dy * 9f, part.cy - dy * 6f - dx * 9f)
            shape.lineTo(part.cx - dx * 6f + dy * 9f, part.cy - dy * 6f + dx * 9f)
            shape.close()
            fill(Ui.mix(Ui.MUTED, Ui.TEXT, a))
            canvas.drawPath(shape, paint)
        }
    }

    private fun drawFace(canvas: Canvas, part: Part) {
        val a = amount(part)
        val r = part.w / 2 * (1f - 0.07f * a)
        if (a > 0f) {
            fill(Ui.withAlpha(part.color, 0.32f * a))
            canvas.drawCircle(part.cx, part.cy, r + 16f * a, paint)
        }
        fill(Ui.mix(0xFF1B1F2A.toInt(), part.color, a))
        canvas.drawCircle(part.cx, part.cy, r, paint)
        stroke(Ui.withAlpha(part.color, 0.9f), 3f)
        canvas.drawCircle(part.cx, part.cy, r, paint)
        paint.style = Paint.Style.FILL

        val key = labels[part.button]
        val color = Ui.mix(part.color, Ui.BG, a)
        if (key != null) text(canvas, key, part.cx, part.cy, 28f, r * 1.6f, color, Ui.HEAVY)
        else text(canvas, part.name, part.cx, part.cy, 28f, r * 1.6f, Ui.withAlpha(color, 0.5f), Ui.HEAVY)
    }

    private fun drawSmall(canvas: Canvas, part: Part) {
        val a = amount(part)
        val r = part.w / 2 * (1f - 0.08f * a)
        fill(Ui.mix(0xFF262B38.toInt(), part.color, a))
        canvas.drawCircle(part.cx, part.cy, r, paint)
        stroke(0xFF3B4254.toInt(), 2f)
        canvas.drawCircle(part.cx, part.cy, r, paint)
        paint.style = Paint.Style.FILL
        val key = labels[part.button]
        val color = Ui.mix(Ui.TEXT, Ui.BG, a)
        if (key != null) {
            text(canvas, key, part.cx, part.cy, 17f, r * 1.7f, color)
        } else if (part.button == KeyEvent.KEYCODE_BUTTON_START) {
            fill(Ui.withAlpha(color, 0.6f))
            for (i in -1..1) canvas.drawRoundRect(part.cx - 9f, part.cy + i * 6f - 1.5f, part.cx + 9f, part.cy + i * 6f + 1.5f, 1.5f, 1.5f, paint)
        } else {
            stroke(Ui.withAlpha(color, 0.6f), 2.5f)
            canvas.drawRect(part.cx - 8f, part.cy - 6f, part.cx + 3f, part.cy + 3f, paint)
            canvas.drawRect(part.cx - 3f, part.cy - 2f, part.cx + 8f, part.cy + 7f, paint)
            paint.style = Paint.Style.FILL
        }
        text(canvas, part.name, part.cx, part.cy + 37f, 12f, 80f, Ui.mix(Ui.MUTED, Ui.TEXT, a))
    }

    private fun drawGuide(canvas: Canvas, part: Part) {
        val a = amount(part)
        val r = part.w / 2 * (1f - 0.06f * a)
        if (a > 0f) {
            fill(Ui.withAlpha(part.color, 0.35f * a))
            canvas.drawCircle(part.cx, part.cy, r + 16f * a, paint)
        }
        fill(Ui.mix(0xFF231F35.toInt(), part.color, a))
        canvas.drawCircle(part.cx, part.cy, r, paint)
        stroke(Ui.withAlpha(part.color, 0.9f), 3f)
        canvas.drawCircle(part.cx, part.cy, r, paint)
        val key = labels[part.button]
        if (key != null) {
            paint.style = Paint.Style.FILL
            text(canvas, key, part.cx, part.cy, 22f, r * 1.6f, Ui.TEXT)
        } else {
            stroke(Ui.withAlpha(Ui.TEXT, 0.55f), 3f)
            canvas.drawCircle(part.cx, part.cy, 14f, paint)
            canvas.drawLine(part.cx - 7f, part.cy - 7f, part.cx + 7f, part.cy + 7f, paint)
            canvas.drawLine(part.cx + 7f, part.cy - 7f, part.cx - 7f, part.cy + 7f, paint)
            paint.style = Paint.Style.FILL
        }
    }

    private fun drawSelection(canvas: Canvas, part: Part) {
        val pulse = 0.55f + 0.45f * sin(SystemClock.uptimeMillis() / 900f * 2f * Math.PI.toFloat())
        stroke(Ui.withAlpha(Ui.PINK, pulse), 5f)
        when (part.kind) {
            Kind.TRIGGER, Kind.BUMPER, Kind.DPAD -> {
                val dy = if (part.kind == Kind.TRIGGER) 11f * amount(part) else 0f
                rect.set(part.cx - part.w / 2 - 8f, part.cy - part.h / 2 - 8f + dy, part.cx + part.w / 2 + 8f, part.cy + part.h / 2 + 8f + dy)
                canvas.drawRoundRect(rect, 24f, 24f, paint)
            }
            Kind.STICK -> canvas.drawCircle(part.cx, part.cy, part.w / 2 + 22f, paint)
            else -> canvas.drawCircle(part.cx, part.cy, part.w / 2 + 10f, paint)
        }
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

    /** Text centered on (x, y), shrunk to fit [maxWidth]. */
    private fun text(
        canvas: Canvas,
        s: String,
        x: Float,
        y: Float,
        size: Float,
        maxWidth: Float,
        color: Int,
        face: android.graphics.Typeface = Ui.BOLD,
    ) {
        ink.typeface = face
        ink.textSize = size
        ink.color = color
        val width = ink.measureText(s)
        if (width > maxWidth) ink.textSize = size * maxWidth / width
        canvas.drawText(s, x, y + ink.textSize * 0.36f, ink)
    }

    // ---- touch ----

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val tap = onButtonTap ?: return false
        val x = (event.x - offsetX) / scale
        val y = (event.y - offsetY) / scale
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touched = hit(x, y)
                touched?.let { setPressed(it, true) }
            }
            MotionEvent.ACTION_UP -> {
                val button = touched
                touched = null
                if (button != null) {
                    setPressed(button, false)
                    if (hit(x, y) == button) {
                        performClick()
                        tap(button)
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

    private fun hit(x: Float, y: Float): Int? = parts.asReversed().firstOrNull { part ->
        when (part.kind) {
            Kind.TRIGGER, Kind.BUMPER, Kind.DPAD -> abs(x - part.cx) <= part.w / 2 + 6f && abs(y - part.cy) <= part.h / 2 + 6f
            else -> hypot(x - part.cx, y - part.cy) <= part.w / 2 + 12f
        }
    }?.button

    private companion object {
        const val DESIGN_W = 1000f
        const val DESIGN_H = 640f
    }
}
