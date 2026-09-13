package fr.bonamy.sports.core

import org.jsoup.Jsoup
import java.net.URI

enum class Sport(val label: String, val path: String) {
    SOCCER("Soccer", "football-streamz5/"), TENNIS("Tennis", "tennis-live-stream10/")
}
data class StreamLink(val label: String, val url: String)
data class SportsEvent(
    val title: String,
    val competition: String,
    val timeLabel: String,
    val startsAt: Long?,
    val links: List<StreamLink>,
) {
    val id: String get() = links.first().url + "|" + title
}

object CatalogParser {
    fun parse(html: String, pageUrl: String): List<SportsEvent> {
        val doc = Jsoup.parse(html, pageUrl)
        var heading = ""
        val events = mutableListOf<SportsEvent>()
        // Walk headings and tables in document order; sidebar/navigation links never become events.
        doc.select("h2, h3, table").forEach { element ->
            if (element.tagName() != "table") {
                heading = element.text()
            } else {
                element.select("tr").forEach rowLoop@ { row ->
                    val title = row.selectFirst(".event-title")?.text()?.trim().orEmpty()
                    if (title.isBlank()) return@rowLoop
                    val links = row.select("a[href]").mapNotNull { a ->
                        val raw = a.absUrl("href")
                        val uri = runCatching { URI(raw) }.getOrNull() ?: return@mapNotNull null
                        if (uri.host != URI(pageUrl).host || uri.scheme !in listOf("http", "https")) return@mapNotNull null
                        StreamLink(a.text().ifBlank { "Watch" }, raw.replaceFirst("http://", "https://"))
                    }.distinctBy { it.url }
                    if (links.isEmpty()) return@rowLoop
                    val timestamp = row.attr("data-timestamp").toLongOrNull()
                    val rawTime = row.selectFirst(".matchtime")?.text().orEmpty()
                    events += SportsEvent(title,
                        row.selectFirst(".leaguename")?.text() ?: heading,
                        if (timestamp == null && rawTime.isNotBlank()) "$rawTime · source time (UTC+1)" else "",
                        timestamp, links)
                }
            }
        }
        return events.distinctBy { it.id }
    }
}

class SportsRepository(private val http: PageClient = PageClient()) {
    suspend fun events(sport: Sport): List<SportsEvent> {
        val page = http.get(BASE + sport.path)
        val parsed = CatalogParser.parse(page.body, page.url)
        if (parsed.isEmpty()) throw SourceUnavailable("The schedule is unavailable. Try refreshing in a moment.")
        return parsed
    }
    companion object {
        const val BASE = "https://freestreams-live1h.pk/"
        val tennisChannel = SportsEvent("Tennis Channel +1", "TENNIS CHANNEL", "Channel", null,
            listOf(StreamLink("Watch channel", BASE + "tennis-channel-1/")))
    }
}
