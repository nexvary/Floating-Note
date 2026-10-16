package com.fgmachines.fgnote

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.roundToInt

internal object Palette {
    const val BACK = 0xFFF4F7F2.toInt()
    const val PANEL = 0xFFFFFFFF.toInt()
    const val PANEL_LIGHT = 0xFFE2F1E8.toInt()
    const val MINT = 0xFF067F74.toInt()
    const val WHITE = 0xFF193338.toInt()
    const val MUTED = 0xFF6E8583.toInt()
    const val RED = 0xFFFF9B9B.toInt()
}

internal fun Context.dp(value: Int): Int = (resources.displayMetrics.density * value).roundToInt()

internal fun shape(fill: Int, radius: Float, stroke: Int? = null): GradientDrawable =
    GradientDrawable().apply {
        setColor(fill)
        cornerRadius = radius
        if (stroke != null) setStroke(1, stroke)
    }

internal fun Context.roundShape(fill: Int, radiusDp: Int, stroke: Int? = null) =
    shape(fill, dp(radiusDp).toFloat(), stroke)

internal fun Context.icon(
    drawable: Int,
    label: String,
    tintBack: Int = Palette.PANEL_LIGHT,
    size: Int = 44,
    click: () -> Unit
): ImageView = ImageView(this).apply {
    setImageResource(drawable)
    imageTintList = android.content.res.ColorStateList.valueOf(Palette.MINT)
    scaleType = ImageView.ScaleType.FIT_CENTER
    val p = dp(11)
    setPadding(p, p, p, p)
    background = roundShape(tintBack, size / 2)
    contentDescription = label
    isClickable = true
    isFocusable = true
    setOnClickListener { click() }
    layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
}

internal fun Context.label(
    text: String, sizeSp: Float, color: Int = Palette.WHITE,
    bold: Boolean = false
): TextView = TextView(this).apply {
    this.text = text
    textSize = sizeSp
    setTextColor(color)
    if (bold) setTypeface(typeface, Typeface.BOLD)
    gravity = Gravity.CENTER_VERTICAL
    textDirection = View.TEXT_DIRECTION_FIRST_STRONG
}

internal fun Context.action(
    text: String, primary: Boolean, click: () -> Unit
): TextView = label(
    text, 15f,
    if (primary) Palette.BACK else Palette.WHITE,
    bold = true
).apply {
    gravity = Gravity.CENTER
    background = roundShape(if (primary) Palette.MINT else Palette.PANEL_LIGHT, 16)
    val vertical = dp(15)
    setPadding(dp(18), vertical, dp(18), vertical)
    isClickable = true
    isFocusable = true
    setOnClickListener { click() }
}

internal fun LinearLayout.addSpace(context: Context, heightDp: Int) {
    addView(View(context), LinearLayout.LayoutParams(1, context.dp(heightDp)))
}

internal fun View.detach() {
    (parent as? ViewGroup)?.removeView(this)
}
