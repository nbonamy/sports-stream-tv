package fr.bonamy.sports

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.sin

/** Full-screen player controls. Loading, errors, and playback share the same navigation. */
internal class PlayerChrome(
    context: Context,
    title: String,
    channel: String,
    private val onBack: () -> Unit,
    private val onPrevious: () -> Unit,
    private val onNext: () -> Unit,
    private val onRetry: () -> Unit,
    private val onTogglePlay: () -> Unit,
    private val onGoLive: () -> Unit,
) : FrameLayout(context) {
    private enum class Mode { CONNECTING, PLAYING, PAUSED, UNAVAILABLE }
    private var mode = Mode.CONNECTING
    private var controlsVisible = true
    private val backdrop = View(context).apply { background = context.browserBackground() }
    private val connecting = ConnectingView(context)
    private val top = FrameLayout(context)
    private val bottom = FrameLayout(context)
    private val counter = context.label("Stream 1", 13f, MUTED)
    private val back = control(PlayerControlButton.Icon.PREVIOUS, "Back to channels", onBack).apply {
        isFocusable = false
    }
    private val previous = control(PlayerControlButton.Icon.PREVIOUS, "Previous stream", onPrevious)
    private val next = control(PlayerControlButton.Icon.NEXT, "Next stream", onNext)
    private val transport = control(PlayerControlButton.Icon.PAUSE, "Pause", onTogglePlay)
    private var liveState = LiveState.HIDDEN
    private val live = context.label("● LIVE", 13f).apply {
        bold(); gravity = Gravity.CENTER
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), context.shape(0xFF172431.toInt()))
            addState(intArrayOf(android.R.attr.state_pressed), context.shape(0xFF172431.toInt()))
            addState(intArrayOf(), context.shape(Color.TRANSPARENT))
        }
        setOnClickListener { if (liveState == LiveState.BEHIND) { reveal(); onGoLive() } }
        setOnFocusChangeListener { _, focused -> if (focused) reveal() }
    }
    private val retry = context.label("Retry", 15f).apply {
        gravity = Gravity.CENTER
        isFocusable = true; isClickable = true
        contentDescription = "Retry stream"
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), context.shape(0xFF172431.toInt()))
            addState(intArrayOf(android.R.attr.state_pressed), context.shape(0xFF172431.toInt()))
            addState(intArrayOf(), context.shape(Color.TRANSPARENT))
        }
        setOnClickListener { onRetry() }
    }
    private val failure = context.column().apply { gravity = Gravity.CENTER }
    private val hideControls = Runnable { hide() }
    private val controls get() = listOf(top, bottom, previous, next)

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        addView(backdrop, LayoutParams(-1, -1))
        top.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xF207111D.toInt(), Color.TRANSPARENT))
        val header = context.row()
        header.addView(back, LinearLayout.LayoutParams(context.dp(42), context.dp(42)).apply { marginEnd = context.dp(18) })
        val titles = context.column()
        titles.addSpaced(context.label(title, 20f).apply {
            bold(); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }, bottom = 7)
        titles.addView(context.label(channel, 13f, MUTED))
        header.addView(titles, LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(counter, LinearLayout.LayoutParams(-2, -2).apply { marginStart = context.dp(24) })
        top.addView(header, LayoutParams(-1, -2).apply {
            leftMargin = context.dp(30); rightMargin = context.dp(36); topMargin = context.dp(28)
        })
        addView(top, LayoutParams(-1, context.dp(116), Gravity.TOP))
        bottom.background = GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP, intArrayOf(0xD907111D.toInt(), Color.TRANSPARENT))
        val playbackControls = context.row().apply {
            addView(transport, LinearLayout.LayoutParams(context.dp(48), context.dp(48)))
            addView(live, LinearLayout.LayoutParams(context.dp(80), context.dp(40)).apply { marginStart = context.dp(12) })
        }
        bottom.addView(playbackControls, LayoutParams(-2, context.dp(48), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            .apply { bottomMargin = context.dp(28) })
        addView(bottom, LayoutParams(-1, context.dp(108), Gravity.BOTTOM))
        addView(previous, LayoutParams(context.dp(56), context.dp(56), Gravity.START or Gravity.CENTER_VERTICAL)
            .apply { leftMargin = context.dp(28) })
        addView(next, LayoutParams(context.dp(56), context.dp(56), Gravity.END or Gravity.CENTER_VERTICAL)
            .apply { rightMargin = context.dp(28) })
        addView(connecting, LayoutParams(context.dp(140), context.dp(140), Gravity.CENTER))
        failure.addView(retry, LinearLayout.LayoutParams(context.dp(100), context.dp(40)))
        addView(failure, LayoutParams(context.dp(320), -2, Gravity.CENTER))
        failure.translationY = context.dp(84).toFloat()
        showConnecting()
    }

    private fun control(icon: PlayerControlButton.Icon, description: String, clicked: () -> Unit) = PlayerControlButton(context, icon, description).apply {
        setOnClickListener { reveal(); clicked() }
        setOnFocusChangeListener { _, focused -> if (focused) reveal() }
    }

    fun setStreams(index: Int, count: Int) {
        counter.text = if (count > 0) "Stream ${index + 1} / $count" else "Stream ${index + 1}"
        previous.isEnabled = index > 0 && index < count
        next.isEnabled = index >= 0 && index < count - 1
        previous.isFocusable = previous.isEnabled
        next.isFocusable = next.isEnabled
        updateArrowVisibility()
    }

    private fun updateArrowVisibility() {
        listOf(previous, next).forEach { arrow ->
            arrow.visibility = if (!arrow.isEnabled) GONE else if (controlsVisible) VISIBLE else INVISIBLE
        }
    }

    fun setLiveState(state: LiveState) {
        val wasFocused = live.isFocused
        liveState = state
        live.visibility = if (state != LiveState.HIDDEN && (mode == Mode.PLAYING || mode == Mode.PAUSED)) VISIBLE else GONE
        live.isFocusable = state == LiveState.BEHIND
        live.isClickable = state == LiveState.BEHIND
        live.contentDescription = if (state == LiveState.BEHIND) "Go live" else "Live"
        live.setTextColor(if (state == LiveState.LIVE) Color.WHITE else MUTED)
        live.text = android.text.SpannableString("● LIVE").apply {
            setSpan(android.text.style.ForegroundColorSpan(if (state == LiveState.LIVE) 0xFFED6673.toInt() else MUTED),
                0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (wasFocused && (state != LiveState.BEHIND || live.visibility != VISIBLE)) {
            if (transport.visibility == VISIBLE) transport.requestFocus() else requestFocus()
        }
    }

    fun showConnecting(overVideo: Boolean = false) {
        mode = Mode.CONNECTING
        connecting.isError = false
        backdrop.alpha = if (overVideo) .55f else 1f
        backdrop.visibility = VISIBLE; connecting.visibility = VISIBLE; failure.visibility = GONE
        transport.visibility = GONE
        setLiveState(LiveState.HIDDEN)
        reveal()
        requestFocus()
    }

    fun showPlayback(playing: Boolean) {
        val changed = mode != if (playing) Mode.PLAYING else Mode.PAUSED
        mode = if (playing) Mode.PLAYING else Mode.PAUSED
        backdrop.visibility = GONE; connecting.visibility = GONE; failure.visibility = GONE
        transport.visibility = VISIBLE
        setLiveState(liveState)
        transport.icon = if (playing) PlayerControlButton.Icon.PAUSE else PlayerControlButton.Icon.PLAY
        transport.contentDescription = if (playing) "Pause" else "Play"
        if (changed) reveal()
    }

    fun showUnavailable(overVideo: Boolean = false) {
        mode = Mode.UNAVAILABLE
        connecting.isError = true
        backdrop.alpha = if (overVideo) .55f else 1f
        backdrop.visibility = VISIBLE; connecting.visibility = VISIBLE; failure.visibility = VISIBLE
        transport.visibility = GONE
        setLiveState(LiveState.HIDDEN)
        reveal()
        retry.requestFocus()
    }

    fun reveal() {
        removeCallbacks(hideControls)
        controlsVisible = true
        controls.forEach { it.animate().cancel(); it.visibility = VISIBLE; it.alpha = 1f }
        updateArrowVisibility()
        if (mode == Mode.PLAYING) postDelayed(hideControls, 3000)
    }

    private fun hide() {
        if (mode != Mode.PLAYING) return
        controlsVisible = false
        requestFocus()
        controls.forEach { view -> view.animate().alpha(0f).setDuration(250).withEndAction { view.visibility = INVISIBLE }.start() }
    }

    fun handleKey(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (event.repeatCount == 0) {
                    reveal()
                    if (transport.isFocused || live.isFocused) {
                        if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && live.isFocusable && live.visibility == VISIBLE) live.requestFocus()
                        if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) transport.requestFocus()
                    } else {
                        if (event.keyCode == KeyEvent.KEYCODE_DPAD_LEFT && previous.isEnabled) onPrevious()
                        if (event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && next.isEnabled) onNext()
                    }
                }
            }
            KeyEvent.KEYCODE_DPAD_UP -> { reveal(); requestFocus() }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                reveal()
                when (mode) {
                    Mode.UNAVAILABLE -> retry.requestFocus()
                    Mode.PLAYING, Mode.PAUSED -> transport.requestFocus()
                    else -> requestFocus()
                }
            }
            KeyEvent.KEYCODE_MENU -> reveal()
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> if (event.repeatCount == 0) onTogglePlay()
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (controlsVisible && findFocus() != this) return false
                if (event.repeatCount == 0) {
                    if (mode == Mode.UNAVAILABLE) onRetry()
                    else if (mode == Mode.PLAYING || mode == Mode.PAUSED) onTogglePlay()
                }
                reveal()
            }
            else -> return false
        }
        return true
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(hideControls)
        controls.forEach { it.animate().cancel() }
        super.onDetachedFromWindow()
    }
}

/** A quiet orbit and three breathing bars; no dialog, progress text, or fake percentage. */
private class ConnectingView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    var isError = false
        set(value) {
            field = value
            contentDescription = if (value) "Stream unavailable" else "Connecting stream"
            motion.duration = if (value) 2800 else 1800
            invalidate()
        }
    private val motion = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1800; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedValue as Float; invalidate() }
    }

    init { contentDescription = "Connecting stream" }

    override fun onDraw(canvas: Canvas) {
        val x = width / 2f; val y = height / 2f; val radius = context.dp(36).toFloat()
        val accent = if (isError) 0xFFED6673.toInt() else ACCENT
        val pulse = .5f + .5f * sin(phase * Math.PI.toFloat() * 2)
        paint.style = Paint.Style.FILL
        val glow = ((if (isError) 30 + (pulse * 24).toInt() else 48) shl 24) or (accent and 0xFFFFFF)
        paint.shader = RadialGradient(x, y, radius * 1.8f, intArrayOf(glow, Color.TRANSPARENT), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, radius * 1.8f, paint)
        paint.shader = null; paint.style = Paint.Style.STROKE; paint.strokeWidth = context.dp(2).toFloat(); paint.strokeCap = Paint.Cap.ROUND
        paint.color = 0x25000000 or (accent and 0xFFFFFF)
        canvas.drawCircle(x, y, radius, paint)
        paint.color = accent
        canvas.drawArc(x - radius, y - radius, x + radius, y + radius, phase * 360f - 90f, 78f, false, paint)
        paint.color = if (isError) 0x80ED6673.toInt() else 0x804FBAFF.toInt()
        canvas.drawArc(x - radius, y - radius, x + radius, y + radius, phase * 360f + 90f, 28f, false, paint)
        paint.strokeWidth = context.dp(4).toFloat()
        paint.color = if (isError) 0xFFFFB1B8.toInt() else 0xFFD6EEFF.toInt()
        if (isError) {
            canvas.drawLine(x, y - context.dp(10), x, y + context.dp(2), paint)
            canvas.drawPoint(x, y + context.dp(10), paint)
            return
        }
        repeat(3) { index ->
            val height = context.dp(5) + context.dp(7) * (.5f + .5f * sin((phase * Math.PI * 2 + index * .9).toFloat()))
            val barX = x + (index - 1) * context.dp(9)
            canvas.drawLine(barX, y - height, barX, y + height, paint)
        }
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) motion.start() else motion.cancel()
    }

    override fun onDetachedFromWindow() { motion.cancel(); super.onDetachedFromWindow() }
}
