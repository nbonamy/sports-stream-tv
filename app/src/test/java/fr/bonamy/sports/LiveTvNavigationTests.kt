package fr.bonamy.sports

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.platform.app.InstrumentationRegistry
import fr.bonamy.sports.core.LiveTvParser
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
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
class LiveTvNavigationTests {
    @Before fun remoteMode() { InstrumentationRegistry.getInstrumentation().setInTouchMode(false) }
    private fun views(root: View): List<View> = listOf(root) + if (root is ViewGroup)
        (0 until root.childCount).flatMap { views(root.getChildAt(it)) } else emptyList()
    private fun MainActivity.allViews() = views(window.decorView)
    private fun MainActivity.focusedView() = allViews().singleOrNull { it.isFocused }
    private fun MainActivity.click(description: String) = allViews().single { it.contentDescription == description }.performClick()
    private fun MainActivity.snapshot(name: String) {
        val root = window.decorView
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

    @Test fun homeAndMoreKeepTheirRemoteBackNavigation() {
        Robolectric.buildActivity(MainActivity::class.java).setup().visible().use { controller ->
            val activity = controller.get()
            val tiles = activity.allViews().filter { it.tag is String }.map { it.tag }
            assertEquals(listOf("FOOTBALL", "TENNIS", "RUGBY", "F1", "GOLF", "NFL", "NBA", "MLB", "LIVE_TV", "MORE"), tiles)
            activity.snapshot("home-livetv")
            activity.click("+ More")
            assertTrue(activity.allViews().any { it.contentDescription == "NHL" })
            assertFalse(activity.allViews().filterIsInstance<TextView>().any { it.text.contains("All sports") })
            assertFalse(activity.allViews().single { it.contentDescription == "Back" }.isFocusable)
            activity.snapshot("more-sports")
            activity.onBackPressedDispatcher.onBackPressed()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("MORE", activity.focusedView()?.tag)
        }
    }

    @Test fun directoryNavigationRestoresCountryAndHomeFocus() {
        Robolectric.buildActivity(MainActivity::class.java).setup().visible().use { controller ->
            val activity = controller.get()
            val countries = LiveTvParser.parse(listOf("UK", "US", "FR", "CA", "PT", "ES").joinToString("") { code ->
                val names = if (code == "FR") listOf("beIN Sports 1", "beIN Sports 2", "Canal+", "Canal+ Sport", "Eurosport 1", "L’Equipe", "TF1") else listOf("Sports", "News")
                """<div class="dropdown"><button class="dropbtn">$code</button><div class="dropdown-content">""" +
                    names.mapIndexed { index, name -> """<a href="/$code/$index">$name</a>""" }.joinToString("") + "</div></div>"
            }, "https://example.test/live-tv/")
            // Seed the cache so navigation tests never contact a streaming provider.
            MainActivity::class.java.getDeclaredField("tvCountries").apply { isAccessible = true }.set(activity, countries)
            activity.click("LiveTV")
            activity.snapshot("livetv-countries")
            assertEquals("France", activity.focusedView()?.contentDescription)
            activity.click("France")
            activity.snapshot("livetv-channels")
            assertEquals("beIN Sports 1", activity.focusedView()?.contentDescription)
            activity.onBackPressedDispatcher.onBackPressed()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("France", activity.focusedView()?.contentDescription)
            activity.onBackPressedDispatcher.onBackPressed()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("LIVE_TV", activity.focusedView()?.tag)
        }
    }

    @Test fun unavailableDirectoryKeepsRefreshReachable() {
        Robolectric.buildActivity(MainActivity::class.java).setup().visible().use { controller ->
            var refreshed = false
            val view = LiveTvView(controller.get(), null) { refreshed = true }
            controller.get().setContentView(view)
            view.loading()
            view.unavailable()
            assertEquals("Refresh", (view.findFocus() as TextView).text.toString())
            view.findFocus().performClick()
            assertTrue(refreshed)
        }
    }
}
