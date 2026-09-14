package fr.bonamy.sports

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.lang.reflect.Proxy
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LivePlaybackTests {
    @Before fun remoteMode() { InstrumentationRegistry.getInstrumentation().setInTouchMode(false) }

    private class Playback {
        var ready = true
        var isLive = true
        var dynamic = true
        var canSeek = true
        var playing = true
        var position = 239_000L
        var target = 240_000L
        var empty = false
        val actions = mutableListOf<String>()
        private val timeline = object : Timeline() {
            override fun getWindowCount() = if (empty) 0 else 1
            override fun getPeriodCount() = 1
            override fun getWindow(index: Int, window: Window, projectionUs: Long) = window.apply {
                defaultPositionUs = if (target == C.TIME_UNSET) C.TIME_UNSET else target * 1_000
                durationUs = 300_000_000L
            }
            override fun getPeriod(index: Int, period: Period, setIds: Boolean) = period
            override fun getIndexOfPeriod(uid: Any) = 0
            override fun getUidOfPeriod(index: Int): Any = "period"
        }
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
            when (method.name) {
                "getPlaybackState" -> if (ready) Player.STATE_READY else Player.STATE_BUFFERING
                "getCurrentTimeline" -> timeline
                "isCurrentMediaItemLive" -> isLive
                "isCurrentMediaItemDynamic" -> dynamic
                "isCommandAvailable" -> canSeek
                "getCurrentMediaItemIndex" -> 0
                "getCurrentPosition" -> position
                "getPlayWhenReady" -> playing
                "seekToDefaultPosition" -> { actions += "seek"; position = target; null }
                "play" -> { actions += "play"; playing = true; null }
                else -> error("Unexpected player call: ${method.name}")
            }
        } as Player
    }

    @Test fun liveUsesTheProviderTargetAndReturnsToItAfterPausing() {
        val playback = Playback()
        assertEquals(LiveState.LIVE, LivePlayback.state(playback.player))
        LivePlayback.goLive(playback.player)
        assertTrue(playback.actions.isEmpty())
        playback.playing = false
        assertEquals(LiveState.BEHIND, LivePlayback.state(playback.player))
        playback.position = 200_000
        LivePlayback.goLive(playback.player)
        assertEquals(listOf("seek", "play"), playback.actions)
        assertEquals(240_000L, playback.position)
        assertEquals(LiveState.LIVE, LivePlayback.state(playback.player))
        playback.position = 220_000
        assertEquals(LiveState.BEHIND, LivePlayback.state(playback.player))
    }

    @Test fun unavailableTargetsAndNonLiveMediaNeverOfferAJump() {
        val cases: List<Playback.() -> Unit> = listOf(
            { ready = false }, { isLive = false }, { dynamic = false },
            { canSeek = false }, { empty = true }, { target = C.TIME_UNSET })
        cases.forEach { configure ->
            val playback = Playback().apply { playing = false; configure() }
            assertEquals(LiveState.HIDDEN, LivePlayback.state(playback.player))
            LivePlayback.goLive(playback.player)
            assertTrue(playback.actions.isEmpty())
        }
    }

    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup)
        (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()

    @Test fun remoteMovesWithinTransportThenReturnsToStreamNavigation() {
        Robolectric.buildActivity(Activity::class.java).setup().visible().use { controller ->
            val activity = controller.get()
            var next = 0
            var jumped = 0
            lateinit var chrome: PlayerChrome
            chrome = PlayerChrome(activity, "Tennis Channel", "LiveTV · US", {}, {}, { next++ }, {}, {}, {
                jumped++; chrome.showPlayback(true); chrome.setLiveState(LiveState.LIVE)
            })
            activity.setContentView(chrome)
            chrome.setStreams(0, 2)
            chrome.showPlayback(false)
            chrome.setLiveState(LiveState.BEHIND)
            fun key(code: Int) { assertTrue(chrome.handleKey(KeyEvent(KeyEvent.ACTION_DOWN, code))) }
            fun find(description: String) = views(chrome).single { it.contentDescription == description }
            key(KeyEvent.KEYCODE_DPAD_DOWN)
            assertTrue(find("Play").isFocused)
            key(KeyEvent.KEYCODE_DPAD_RIGHT)
            val live = find("Go live")
            assertTrue(live.isFocused)
            assertEquals(0, next)
            snapshot(chrome, "player-behind-live")
            live.performClick()
            assertEquals(1, jumped)
            assertTrue(find("Pause").isFocused)
            assertFalse(find("Live").isFocusable)
            snapshot(chrome, "player-live")
            key(KeyEvent.KEYCODE_DPAD_RIGHT)
            assertEquals(0, next)
            key(KeyEvent.KEYCODE_DPAD_UP)
            key(KeyEvent.KEYCODE_DPAD_RIGHT)
            assertEquals(1, next)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(3_500))
            assertFalse(find("Live").isShown)
            chrome.showConnecting()
            assertEquals(View.GONE, find("Live").visibility)
            chrome.showUnavailable()
            assertEquals(View.GONE, find("Live").visibility)
        }
    }

    private fun snapshot(root: View, name: String) {
        root.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1920, 1080)
        shadowOf(Looper.getMainLooper()).idle()
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bitmap))
        File("build/previews/$name.png").apply { parentFile?.mkdirs() }.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
