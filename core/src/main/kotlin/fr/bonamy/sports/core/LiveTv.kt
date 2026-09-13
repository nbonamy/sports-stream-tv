package fr.bonamy.sports.core

import org.jsoup.Jsoup
import java.net.URI
import java.util.Locale

data class TvCountry(val code: String, val channels: List<Channel>) {
    val name: String get() = Locale("", if (code == "UK") "GB" else code).getDisplayCountry(Locale.ENGLISH)
}

object LiveTvParser {
    fun parse(html: String, pageUrl: String): List<TvCountry> {
        val host = URI(pageUrl).host
        val groups = Jsoup.parse(html, pageUrl).select(".dropdown").mapNotNull { group ->
            val code = group.selectFirst(".dropbtn")?.text()?.trim().orEmpty()
            if (!Regex("[A-Z]{2}").matches(code)) return@mapNotNull null
            val channels = group.select(".dropdown-content a[href]").mapNotNull { link ->
                val name = link.text().trim()
                val uri = runCatching { URI(link.absUrl("href")) }.getOrNull() ?: return@mapNotNull null
                if (name.isBlank() || uri.host != host || uri.scheme !in listOf("http", "https") ||
                    uri.fragment != null || uri.userInfo != null) return@mapNotNull null
                Channel(name, listOf(StreamLink(name, uri.toString().replaceFirst("http://", "https://"))))
            }.distinctBy { it.id }
            channels.takeIf { it.isNotEmpty() }?.let { TvCountry(code, it) }
        }
        val priority = listOf("FR", "US", "UK")
        return groups.groupBy { it.code }.map { (code, countries) ->
            TvCountry(code, countries.flatMap { it.channels }.distinctBy { it.id })
        }.sortedBy { priority.indexOf(it.code).takeIf { index -> index >= 0 } ?: priority.size }
    }
}

class LiveTvRepository(private val http: PageClient = PageClient()) {
    suspend fun countries(): List<TvCountry> {
        val page = http.get(SportsRepository.BASE + "live-tv/")
        return LiveTvParser.parse(page.body, page.url).ifEmpty {
            throw SourceUnavailable("No TV channels are available right now.")
        }
    }
}
