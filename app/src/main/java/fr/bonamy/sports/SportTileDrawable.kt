package fr.bonamy.sports

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/** Blend artwork into a continuous dark tile without stretching the sport itself. */
internal class SportTileDrawable(
    private val bitmap: Bitmap,
    private val topOffset: Float,
    private val artworkScale: Float,
    private val cornerRadius: Float,
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(2, 9, 18) }
    private val maskPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
    private val imageBounds = RectF()
    private val outline = Path()
    private lateinit var verticalMask: LinearGradient
    private lateinit var horizontalMask: LinearGradient

    override fun onBoundsChange(bounds: Rect) {
        val scale = bounds.width().toFloat() / bitmap.width * artworkScale
        val left = bounds.left + (bounds.width() - bitmap.width * scale) / 2f
        val top = bounds.top + topOffset
        imageBounds.set(left, top, left + bitmap.width * scale, top + bitmap.height * scale)
        outline.reset()
        outline.addRoundRect(RectF(bounds), cornerRadius, cornerRadius, Path.Direction.CW)
        val colors = intArrayOf(Color.TRANSPARENT, Color.WHITE, Color.WHITE, Color.TRANSPARENT)
        val stops = floatArrayOf(0f, .08f, .88f, 1f)
        verticalMask = LinearGradient(0f, imageBounds.top, 0f, imageBounds.bottom, colors, stops, Shader.TileMode.CLAMP)
        horizontalMask = LinearGradient(imageBounds.left, 0f, imageBounds.right, 0f, colors, stops, Shader.TileMode.CLAMP)
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRoundRect(RectF(bounds), cornerRadius, cornerRadius, backgroundPaint)
        val clip = canvas.save()
        canvas.clipPath(outline)
        val layer = canvas.saveLayer(imageBounds, null)
        canvas.drawBitmap(bitmap, null, imageBounds, paint)
        maskPaint.shader = verticalMask
        canvas.drawRect(imageBounds, maskPaint)
        maskPaint.shader = horizontalMask
        canvas.drawRect(imageBounds, maskPaint)
        canvas.restoreToCount(layer)
        canvas.restoreToCount(clip)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; backgroundPaint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Required by Drawable on older Android versions")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
