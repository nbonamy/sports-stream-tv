package fr.bonamy.sports

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerReconnectionTests {
    @Test fun reconnectingDimsTheVideoAndFreshConnectionsCoverIt() {
        Robolectric.buildActivity(Activity::class.java).setup().visible().use { controller ->
            val activity = controller.get()
            val chrome = PlayerChrome(activity, "Tennis Channel", "LiveTV · US", {}, {}, {}, {}, {}, {})
            val root = FrameLayout(activity).apply {
                // A deterministic stand-in for a decoded frame; no network or media tokens.
                setBackgroundColor(Color.rgb(80, 180, 100))
                addView(chrome, FrameLayout.LayoutParams(-1, -1))
            }
            activity.setContentView(root)
            chrome.setStreams(0, 2)
            fun sample() = render(root).let { bitmap -> bitmap.getPixel(500, 500).also { bitmap.recycle() } }
            val initialLoading = sample()
            chrome.showPlayback(true)
            val video = sample()
            assertEquals(Color.rgb(80, 180, 100), video)
            chrome.showConnecting(overVideo = true)
            val reconnecting = sample()
            assertTrue(Color.green(reconnecting) < Color.green(video))
            assertTrue(Color.green(reconnecting) > Color.green(initialLoading) + 30)
            save(render(root), "player-reconnecting")
            // Repeated retry attempts keep the same translucency.
            chrome.showConnecting(overVideo = true)
            assertEquals(reconnecting, sample())
            chrome.showUnavailable(overVideo = true)
            assertEquals(reconnecting, sample())
            chrome.showPlayback(true)
            assertEquals(video, sample())
            // A new stream must not expose the old channel's image.
            chrome.showConnecting()
            assertEquals(initialLoading, sample())
            chrome.showUnavailable()
            assertEquals(initialLoading, sample())
        }
    }

    private fun render(root: View): Bitmap {
        root.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1920, 1080)
        shadowOf(Looper.getMainLooper()).idle()
        return Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) }
    }

    private fun save(bitmap: Bitmap, name: String) {
        File("build/previews/$name.png").apply { parentFile?.mkdirs() }.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
