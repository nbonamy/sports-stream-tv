package fr.bonamy.sports

import android.content.Context
import android.graphics.*
import android.view.View
import fr.bonamy.sports.core.Sport

/** Lightweight artwork drawn locally; browsing never downloads advertising or tracking images. */
internal class SportArt(context: Context, private val sport: Sport, private val title: String) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        paint.shader = LinearGradient(0f, 0f, w, h,
            if (sport == Sport.TENNIS) 0xFF214C70.toInt() else 0xFF244B48.toInt(),
            0xFF0F202F.toInt(), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(0f, 0f, w, h, context.dp(5).toFloat(), context.dp(5).toFloat(), paint)
        paint.shader = null; paint.style = Paint.Style.STROKE; paint.strokeWidth = context.dp(1).toFloat(); paint.color = 0x387BA9C3
        val left = w * .08f; val top = h * .12f; val right = w * .92f; val bottom = h * .88f
        canvas.drawRect(left, top, right, bottom, paint)
        canvas.drawLine(w / 2, top, w / 2, bottom, paint)
        if (sport == Sport.SOCCER) {
            canvas.drawCircle(w / 2, h / 2, h * .2f, paint)
            canvas.drawRect(left, h * .3f, w * .21f, h * .7f, paint)
            canvas.drawRect(w * .79f, h * .3f, right, h * .7f, paint)
        } else {
            canvas.drawLine(left, h * .24f, right, h * .24f, paint)
            canvas.drawLine(left, h * .76f, right, h * .76f, paint)
            canvas.drawRect(w * .28f, h * .24f, w * .72f, h * .76f, paint)
            canvas.drawLine(w * .28f, h / 2, w * .72f, h / 2, paint)
        }
        paint.style = Paint.Style.FILL
        val sides = title.split(Regex("\\s+vs\\.?\\s+", RegexOption.IGNORE_CASE))
        if (sides.size == 2) {
            sides.forEachIndexed { index, side ->
                val x = w * if (index == 0) .26f else .74f
                paint.color = 0xDD111F2E.toInt(); canvas.drawCircle(x, h / 2, h * .27f, paint)
                paint.color = 0xFFF5F6F8.toInt(); paint.typeface = resources.getFont(R.font.lato_bold)
                paint.textSize = context.dp(20).toFloat(); paint.textAlign = Paint.Align.CENTER
                val initials = side.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
                canvas.drawText(initials, x, h / 2 - (paint.ascent() + paint.descent()) / 2, paint)
            }
            paint.textSize = context.dp(11).toFloat(); paint.color = 0xFFA8BDD8.toInt()
            canvas.drawText("VS", w / 2, h / 2 - (paint.ascent() + paint.descent()) / 2, paint)
        } else {
            paint.color = 0xFFF5F6F8.toInt(); paint.typeface = resources.getFont(R.font.lato_bold)
            paint.textSize = context.dp(24).toFloat(); paint.textAlign = Paint.Align.CENTER
            canvas.drawText("TENNIS +1", w / 2, h / 2 - (paint.ascent() + paint.descent()) / 2, paint)
        }
    }
}
