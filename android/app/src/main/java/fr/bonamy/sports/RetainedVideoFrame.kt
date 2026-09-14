package fr.bonamy.sports

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.widget.ImageView
import androidx.media3.ui.PlayerView
import androidx.media3.common.util.UnstableApi

/** Hold one frame across decoder resets, which can clear a TV's video surface. */
@UnstableApi
internal class RetainedVideoFrame(context: Context) : ImageView(context) {
    private var generation = 0
    private var capturing = false

    init {
        scaleType = ScaleType.FIT_CENTER
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = GONE
    }

    fun capture(playerView: PlayerView) {
        if (drawable != null || capturing) return
        val surface = playerView.videoSurfaceView as? SurfaceView ?: return
        if (!surface.holder.surface.isValid || surface.width <= 0 || surface.height <= 0) return
        val scale = minOf(1f, 1280f / surface.width, 720f / surface.height)
        val bitmap = Bitmap.createBitmap((surface.width * scale).toInt().coerceAtLeast(1),
            (surface.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val requestGeneration = generation
        capturing = true
        try {
            PixelCopy.request(surface, bitmap, { result ->
                if (requestGeneration == generation) {
                    capturing = false
                    if (result == PixelCopy.SUCCESS) {
                        setImageBitmap(bitmap)
                        visibility = VISIBLE
                    } else bitmap.recycle()
                } else bitmap.recycle()
            }, Handler(Looper.getMainLooper()))
        } catch (_: IllegalArgumentException) {
            capturing = false
            bitmap.recycle()
        }
    }

    fun clear() {
        generation++
        capturing = false
        setImageDrawable(null)
        visibility = GONE
    }

    override fun onDetachedFromWindow() { clear(); super.onDetachedFromWindow() }
}
