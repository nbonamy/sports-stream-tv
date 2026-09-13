package fr.bonamy.sports

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.widget.*

internal val INK = Color.rgb(7, 17, 29)
internal val PANEL = Color.rgb(18, 23, 30)
internal val MUTED = Color.rgb(143, 163, 184)
internal val ACCENT = Color.rgb(52, 152, 218)
internal fun Context.dp(value: Int) = (value * resources.displayMetrics.density).toInt()
internal fun Context.label(value: String, size: Float = 16f, color: Int = Color.WHITE) = TextView(this).apply {
    typeface = resources.getFont(R.font.theme); text = value; textSize = size; setTextColor(color); includeFontPadding = false
}
internal fun Context.column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
internal fun Context.row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
internal fun Context.shape(color: Int, border: Int = color) = GradientDrawable().apply {
    setColor(color); cornerRadius = dp(7).toFloat(); setStroke(dp(2), border)
}
internal fun Context.focusBackground() = StateListDrawable().apply {
    addState(intArrayOf(android.R.attr.state_focused), shape(Color.rgb(18, 27, 38), ACCENT))
    addState(intArrayOf(android.R.attr.state_pressed), shape(Color.rgb(18, 27, 38), ACCENT))
    addState(intArrayOf(), shape(PANEL))
}
internal fun Context.action(title: String, clicked: () -> Unit) = Button(this).apply {
    typeface = resources.getFont(R.font.theme); text = title; textSize = 14f; isAllCaps = false; setTextColor(Color.WHITE)
    background = focusBackground(); setPadding(dp(18), dp(10), dp(18), dp(10))
    minHeight = dp(48); minimumHeight = dp(48)
    setOnClickListener { clicked() }
}
internal fun LinearLayout.addSpaced(view: View, height: Int = LinearLayout.LayoutParams.WRAP_CONTENT, bottom: Int = 10) {
    addView(view, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, height).apply { bottomMargin = context.dp(bottom) })
}
internal fun TextView.bold() { setTypeface(typeface, Typeface.BOLD) }

internal fun Context.browserBackground() = GradientDrawable(
    GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(7, 17, 29), Color.rgb(9, 18, 28), Color.rgb(2, 6, 11)))
