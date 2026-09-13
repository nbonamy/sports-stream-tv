package fr.bonamy.sports

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.LinearLayout

internal class ScheduleLoadingView(context: Context) : LinearLayout(context) {
    private val pulse = ObjectAnimator.ofFloat(this, View.ALPHA, .5f, .95f).apply {
        duration = 1100
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
    }

    init {
        orientation = VERTICAL
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        setPadding(0, context.dp(14), 0, 0)
        addView(block(), LayoutParams(context.dp(112), context.dp(14)).apply { bottomMargin = context.dp(20) })
        repeat(3) { index ->
            val card = context.row().apply {
                background = context.shape(PANEL)
                setPadding(context.dp(16), 0, context.dp(22), 0)
            }
            card.addView(block(), LayoutParams(context.dp(42), context.dp(42)).apply { marginEnd = context.dp(20) })
            val text = context.column()
            text.addView(block(), LayoutParams(context.dp(88), context.dp(9)).apply { bottomMargin = context.dp(12) })
            text.addView(block(), LayoutParams(context.dp(if (index == 1) 210 else 270), context.dp(15)))
            card.addView(text, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
            card.addView(block(), LayoutParams(context.dp(72), context.dp(12)).apply { marginEnd = context.dp(42) })
            card.addView(block(), LayoutParams(context.dp(60), context.dp(12)))
            addSpaced(card, context.dp(76), 8)
        }
    }

    private fun block() = View(context).apply { background = context.shape(Color.rgb(38, 52, 68)) }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) pulse.start() else pulse.cancel()
    }

    override fun onDetachedFromWindow() {
        pulse.cancel()
        super.onDetachedFromWindow()
    }
}
