package fr.bonamy.sports

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout

/** Deterministic UI checks without depending on an external stream. Excluded from release builds. */
class PlayerPreviewActivity : Activity() {
    private lateinit var chrome: PlayerChrome
    private var index = 0
    private var playing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val count = intent.getIntExtra("streams", 3).coerceAtLeast(1)
        fun move(direction: Int) {
            index = Math.floorMod(index + direction, count)
            chrome.setStreams(index, count)
        }
        chrome = PlayerChrome(this, "DP World Tour: Irish Open – Final Round", "Sky Sports+",
            onBack = { finish() }, onPrevious = { move(-1) }, onNext = { move(1) },
            onRetry = { chrome.showConnecting() },
            onTogglePlay = { playing = !playing; chrome.showPlayback(playing) })
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(chrome, FrameLayout.LayoutParams(-1, -1))
        }
        setContentView(root)
        chrome.setStreams(index, count)
        chrome.requestFocus()
        when (intent.getStringExtra("state")) {
            "playing" -> { playing = true; chrome.showPlayback(true) }
            "paused" -> chrome.showPlayback(false)
            "unavailable" -> chrome.showUnavailable()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        (::chrome.isInitialized && chrome.handleKey(event)) || super.dispatchKeyEvent(event)
}
