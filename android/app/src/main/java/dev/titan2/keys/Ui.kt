package dev.titan2.keys

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

/** The app's look: dark, with a pink-to-violet accent. Plain framework views, no libraries. */
object Ui {
    const val BG = 0xFF0C0E13.toInt()
    const val CARD = 0xFF151820.toInt()
    const val RAISED = 0xFF1E222D.toInt()
    const val STROKE = 0xFF272C39.toInt()
    const val TEXT = 0xFFF3F5F9.toInt()
    const val MUTED = 0xFF8A93A6.toInt()
    const val PINK = 0xFFFF4D8D.toInt()
    const val VIOLET = 0xFF8B5CF6.toInt()
    const val GREEN = 0xFF3DDC84.toInt()
    const val RED = 0xFFFF5370.toInt()
    const val BLUE = 0xFF4F9DFF.toInt()
    const val YELLOW = 0xFFFFC947.toInt()

    val BOLD: Typeface = Typeface.create("sans-serif", Typeface.BOLD)
    val MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    val HEAVY: Typeface = Typeface.create("sans-serif-black", Typeface.NORMAL)

    /** Blends [a] into [b] by [t] (0 = a, 1 = b), alpha included. */
    fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int) = (((a ushr shift) and 0xFF) + (((b ushr shift) and 0xFF) - ((a ushr shift) and 0xFF)) * t)
            .roundToInt().coerceIn(0, 255) shl shift
        return ch(24) or ch(16) or ch(8) or ch(0)
    }

    fun withAlpha(color: Int, alpha: Float) = (color and 0xFFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24)

    fun rounded(color: Int, radius: Float, strokeColor: Int = 0, strokeWidth: Int = 0) = GradientDrawable().apply {
        cornerRadius = radius
        setColor(color)
        if (strokeWidth > 0) setStroke(strokeWidth, strokeColor)
    }

    fun gradient(radius: Float, vararg colors: Int) =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply { cornerRadius = radius }

    /** [content] with a touch ripple. */
    fun ripple(content: Drawable, radius: Float): Drawable =
        RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), content, rounded(Color.WHITE, radius))
}

fun Context.dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()

fun Context.dpf(value: Float) = value * resources.displayMetrics.density

/** Dark window background and light status/navigation bar icons. */
@Suppress("DEPRECATION")
fun Activity.useDarkWindow() {
    window.setBackgroundDrawable(ColorDrawable(Ui.BG))
    window.statusBarColor = Ui.BG
    window.navigationBarColor = Ui.BG
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.insetsController?.setSystemBarsAppearance(
            0,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
        )
    } else {
        window.decorView.systemUiVisibility = 0
    }
}

fun Context.label(text: CharSequence, size: Float, color: Int = Ui.TEXT, face: Typeface = Typeface.DEFAULT) =
    TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(color)
        typeface = face
        setLineSpacing(0f, 1.15f)
    }

/** A rounded panel that stacks its children. */
fun Context.card(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = Ui.rounded(Ui.CARD, dpf(22f), Ui.STROKE, dp(1))
    setPadding(dp(18), dp(16), dp(18), dp(18))
}

/** A big tappable pill. [primary] ones get the accent gradient. */
fun Context.pill(text: String, primary: Boolean, onClick: () -> Unit) = TextView(this).apply {
    this.text = text
    textSize = 15f
    typeface = Ui.BOLD
    gravity = Gravity.CENTER
    setTextColor(if (primary) Color.WHITE else Ui.TEXT)
    setPadding(dp(16), dp(13), dp(16), dp(13))
    val radius = dpf(16f)
    val content = if (primary) Ui.gradient(radius, Ui.PINK, Ui.VIOLET)
    else Ui.rounded(Ui.RAISED, radius, Ui.STROKE, dp(1))
    background = Ui.ripple(content, radius)
    isClickable = true
    isFocusable = true
    setOnClickListener { onClick() }
}

/** A key cap chip, for showing shortcuts: [Sym] + [B]. */
fun Context.keycap(text: String) = TextView(this).apply {
    this.text = text
    textSize = 12f
    typeface = Ui.BOLD
    setTextColor(Ui.TEXT)
    gravity = Gravity.CENTER
    minWidth = dp(30)
    setPadding(dp(8), dp(4), dp(8), dp(5))
    background = Ui.rounded(Ui.RAISED, dpf(8f), 0xFF3A4152.toInt(), dp(1))
}

fun Context.vgap(height: Int) = View(this).apply {
    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(height))
}

/** Layout params for a full-width child with a gap below it. */
fun Context.below(gap: Int = 12) = LinearLayout.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
).apply { bottomMargin = dp(gap) }
