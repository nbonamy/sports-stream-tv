package fr.bonamy.sports.core

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

// Declaration order is the home screen order. Additional sports live under More.
enum class Sport(val label: String, val path: String, val windowHours: Long = 4) {
    FOOTBALL("Football", "football-streamz5/", 3), TENNIS("Tennis", "tennis-live-stream10/", 6),
    RUGBY("Rugby", "rugby-live-stream10/", 3), F1("F1", "f1-live-stream99/"),
    GOLF("Golf", "golf-live-stream98/", 12),
    NFL("NFL", "nfl-live-stream20/", 5), NBA("NBA", "nba-stream70/"),
    MLB("MLB", "mlb-stream2/", 5), NHL("NHL", "nhl-live-stream33/"),
    MMA("MMA", "ufc-live-stream2/"), BOXING("Boxing", "boxing-live-stream10/"),
    MOTORSPORT("Motorsport", "motorsports-streams5/"),
    NCAAF("College Football", "ncaaf-live-stream01/", 5), BASKETBALL("Basketball", "basketball-live-stream2/"),
    VOLLEYBALL("Volleyball", "volleyball-live-streams/"), HANDBALL("Handball", "handball-live-streaming/");
    companion object { val featured = entries.take(8); val more = entries.drop(8) }
}
data class StreamLink(val label: String, val url: String)
data class Channel(val name: String, val links: List<StreamLink>) {
    val id: String get() = links.first().url
}
data class SportsEvent(
    val title: String,
    val competition: String,
    val timeLabel: String,
    val startsAt: Long?,
    val links: List<StreamLink>,
    val isChannel: Boolean = false,
    val leagueIconUrl: String? = null,
) {
    val id: String get() = links.first().url + "|" + title
    val channels: List<Channel> get() = links.groupBy { link ->
        val label = link.label.trim()
        if (Regex("(?i)(LINK\\s*#?\\d+|WATCH)").matches(label)) {
            URI(link.url).path.trim('/').split('-').joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
        } else label.replace(Regex("\\s+#\\d+$"), "")
    }.map { (name, alternatives) -> Channel(name, alternatives) }
}

object CatalogParser {
    private val sourceZone = ZoneOffset.ofHours(1)
    fun parse(html: String, pageUrl: String, now: Instant = Instant.now()): List<SportsEvent> {
        val doc = Jsoup.parse(html, pageUrl)
        var heading = ""
        var date: LocalDate? = null
        val events = mutableListOf<SportsEvent>()
        doc.select("h2, h3, table, .teamz").forEach { element ->
            when {
                element.hasClass("teamz") -> {
                    val section = element.parents().firstOrNull { it.hasClass("elementor-top-section") } ?: return@forEach
                    val links = links(section, pageUrl)
                    if (links.isEmpty()) return@forEach
                    val rawTime = Regex("\\d{1,2}:\\d{2}\\s*[AP]M\\s*ET", RegexOption.IGNORE_CASE).find(section.text())?.value.orEmpty()
                    val localTime = rawTime.replace(Regex("(?i)\\s*ET$"), "").uppercase(Locale.US)
                    val time = runCatching { LocalTime.parse(localTime, DateTimeFormatter.ofPattern("h:mm a", Locale.US)) }.getOrNull()
                    val start = if (date != null && time != null) date!!.atTime(time).atZone(ZoneId.of("America/New_York")).toInstant().toEpochMilli() else null
                    events += SportsEvent(element.text(), heading, rawTime, start, links)
                }
                element.tagName() != "table" -> {
                    if (element.selectFirst(".teamz") != null) return@forEach
                    val parsedDate = parseDate(element.text(), now)
                    if (parsedDate != null) date = parsedDate else heading = element.text()
                }
                else -> {
                    var previousMinutes: Int? = null
                    var dayOffset = 0L
                    element.select("tr").forEach rowLoop@ { row ->
                        val title = row.selectFirst(".event-title")?.text()?.trim().orEmpty()
                        if (title.isBlank()) return@rowLoop
                        val links = links(row, pageUrl)
                        if (links.isEmpty()) return@rowLoop
                        val rawTime = row.selectFirst(".matchtime")?.text()?.trim().orEmpty()
                        val competition = row.selectFirst(".leaguename")?.text()?.takeIf { it.isNotBlank() } ?: heading
                        // Dated fixtures end at the continuous-channel section; detached
                        // leftover rows must not inherit the date of the earlier schedule.
                        if (Regex("(?i)\\b24\\s*/\\s*7\\s+CHANNELS?\\b").containsMatchIn(competition) && rawTime.isNotBlank()) return@rowLoop
                        val time = runCatching { LocalTime.parse(rawTime, DateTimeFormatter.ofPattern("H:mm")) }.getOrNull()
                        if (time != null) {
                            val minutes = time.hour * 60 + time.minute
                            // A large backwards jump is the evening table crossing midnight.
                            if (previousMinutes?.let { it - minutes > 12 * 60 } == true) dayOffset++
                            previousMinutes = minutes
                        }
                        val timestamp = row.attr("data-timestamp").toLongOrNull()?.takeIf { it > 0 }
                            ?.let { if (it < 100_000_000_000L) it * 1000 else it }
                            ?: if (date != null && time != null) date!!.plusDays(dayOffset).atTime(time).toInstant(sourceZone).toEpochMilli() else null
                        events += SportsEvent(title, competition,
                            if (timestamp == null && rawTime.isNotBlank()) "$rawTime · source time (UTC+1)" else "",
                            timestamp, links, rawTime.isBlank() && timestamp == null && (title.contains("CHANNEL", true) || heading.contains("24/7")),
                            row.selectFirst("img.leagueimg")?.absUrl("src")?.takeIf { it.startsWith("https://") })
                    }
                }
            }
        }
        return events.distinctBy { it.id }
    }

    private fun links(element: Element, pageUrl: String) = element.select("a[href]").mapNotNull { a ->
        val source = runCatching { URI(a.attr("href")) }.getOrNull() ?: return@mapNotNull null
        val uri = runCatching { URI(a.absUrl("href")) }.getOrNull() ?: return@mapNotNull null
        if (uri.host != URI(pageUrl).host || uri.scheme !in listOf("http", "https") ||
            uri.fragment != null || uri.userInfo != null || source.userInfo != null) return@mapNotNull null
        StreamLink(a.text().ifBlank { "Watch" }, uri.toString().replaceFirst("http://", "https://"))
    }.distinctBy { it.url }

    // Pages omit the year. Choose the nearest occurrence, including across New Year.
    private fun parseDate(text: String, now: Instant): LocalDate? {
        val match = Regex("(?i)\\b(JANUARY|FEBRUARY|MARCH|APRIL|MAY|JUNE|JULY|AUGUST|SEPTEMBER|OCTOBER|NOVEMBER|DECEMBER)\\s+(\\d{1,2})(?:,?\\s+(20\\d{2}))?\\b").find(text) ?: return null
        val today = now.atZone(sourceZone).toLocalDate()
        val year = match.groupValues[3].toIntOrNull()
        return (if (year != null) listOf(year) else listOf(today.year - 1, today.year, today.year + 1)).mapNotNull {
            runCatching { LocalDate.of(it, Month.valueOf(match.groupValues[1].uppercase()), match.groupValues[2].toInt()) }.getOrNull()
        }.minByOrNull { kotlin.math.abs(it.toEpochDay() - today.toEpochDay()) }
    }
}

class SportsRepository(private val http: PageClient = PageClient()) {
    suspend fun events(sport: Sport): List<SportsEvent> {
        val page = http.get(BASE + sport.path)
        val parsed = CatalogParser.parse(page.body, page.url)
        if (parsed.isEmpty()) throw SourceUnavailable("No schedule is available for ${sport.label} right now.")
        // The NBA page sometimes carries only WNBA fixtures during the offseason.
        return if (sport == Sport.NBA) parsed.filterNot { it.competition.contains("WNBA", true) } else parsed
    }
    companion object {
        const val BASE = ProviderConfig.BASE
    }
}
