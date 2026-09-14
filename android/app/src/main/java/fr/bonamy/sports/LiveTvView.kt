package fr.bonamy.sports

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import fr.bonamy.sports.core.Channel
import fr.bonamy.sports.core.TvCountry

/** Country and channel grids share the same TV focus and scrolling behavior. */
internal class LiveTvView(context: Context, country: TvCountry?, onRefresh: () -> Unit) : LinearLayout(context) {
    private val grid = context.column()
    private val scroll = ScrollView(context).apply { isVerticalScrollBarEnabled = false; addView(grid) }
    private val refresh = context.action("Refresh", onRefresh)

    init {
        orientation = VERTICAL
        val heading = context.row()
        heading.addView(ImageView(context).apply {
            setImageResource(R.drawable.live_tv_cutout)
            scaleType = ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(context.dp(132), context.dp(96)).apply { marginEnd = context.dp(22) })
        heading.addView(context.column().apply {
            addSpaced(context.label(country?.name ?: "LiveTV", 30f).apply { bold() }, bottom = 6)
            addView(context.label(if (country == null) "Choose a country" else "LiveTV", 18f, MUTED))
        }, LayoutParams(0, -2, 1f))
        heading.addView(refresh, LayoutParams(context.dp(104), context.dp(42)))
        addSpaced(heading, context.dp(96), 20)
        addView(scroll, LayoutParams(-1, 0, 1f))
    }

    fun loading() {
        grid.removeAllViews()
        grid.addView(ScheduleLoadingView(context))
        refresh.requestFocus()
    }

    fun unavailable() {
        grid.removeAllViews()
        grid.addSpaced(context.label("Channels unavailable · select Refresh to try again", 15f, MUTED))
        refresh.requestFocus()
    }

    fun countries(countries: List<TvCountry>, selected: String?, onCountry: (TvCountry) -> Unit) {
        tiles(countries, 5, selected, { it.code }) { country ->
            context.column().apply {
                gravity = Gravity.CENTER
                addSpaced(context.label(country.code, 26f, ACCENT).apply { bold(); gravity = Gravity.CENTER }, bottom = 10)
                addView(context.label(country.name, 13f).apply { gravity = Gravity.CENTER; maxLines = 1 })
                contentDescription = country.name
                setOnClickListener { onCountry(country) }
            }
        }
    }

    fun channels(country: TvCountry, selected: String?, onChannel: (Channel) -> Unit) {
        tiles(country.channels, 3, selected, { it.id }) { channel ->
            context.row().apply {
                addView(context.label("▶", 14f, ACCENT), LayoutParams(context.dp(28), -2))
                addView(context.label(channel.name, 17f).apply { bold(); maxLines = 2 }, LayoutParams(0, -2, 1f))
                contentDescription = channel.name
                setOnClickListener { onChannel(channel) }
            }
        }
    }

    private fun <T> tiles(items: List<T>, columns: Int, selected: String?, key: (T) -> String, tile: (T) -> View) {
        grid.removeAllViews()
        var target: View? = null
        items.chunked(columns).forEach { group ->
            val line = context.row()
            group.forEach { item ->
                val card = tile(item).apply {
                    isFocusable = true; isClickable = true
                    background = context.focusBackground()
                    setPadding(context.dp(18), context.dp(10), context.dp(18), context.dp(10))
                }
                if (target == null || key(item) == selected) target = card
                line.addView(card, LayoutParams(0, context.dp(94), 1f).apply { marginEnd = context.dp(12) })
            }
            repeat(columns - group.size) { line.addView(View(context), LayoutParams(0, 1, 1f).apply { marginEnd = context.dp(12) }) }
            grid.addSpaced(line, bottom = 12)
        }
        scroll.post { target?.requestFocus() }
    }
}
