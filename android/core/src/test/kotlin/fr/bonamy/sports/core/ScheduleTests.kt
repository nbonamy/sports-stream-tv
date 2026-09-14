package fr.bonamy.sports.core

import java.time.Instant
import kotlin.test.*

class ScheduleTests {
    private val now = Instant.parse("2026-09-13T18:00:00Z").toEpochMilli()
    private fun event(start: Long?) = SportsEvent("A vs B", "League", "", start, listOf(StreamLink("Sky F1", "https://example.test/channel")))

    @Test fun `schedule moves upcoming into current at kickoff and ages out old events`() {
        assertEquals(ScheduleSection.UPCOMING, Schedule.section(event(now + 1), Sport.FOOTBALL, now))
        assertEquals(ScheduleSection.CURRENT, Schedule.section(event(now), Sport.FOOTBALL, now))
        assertEquals(ScheduleSection.EARLIER, Schedule.section(event(now - 3 * 3_600_000), Sport.FOOTBALL, now))
        assertEquals(ScheduleSection.UNKNOWN, Schedule.section(event(null), Sport.FOOTBALL, now))
        assertEquals(ScheduleSection.CHANNELS, Schedule.section(event(null).copy(isChannel = true), Sport.TENNIS, now))
    }

    @Test fun `countdowns round remaining minutes up and format hours and days`() {
        assertEquals("in 1h29", Schedule.countdown(now + 89 * 60_000, now))
        assertEquals("in 1m", Schedule.countdown(now + 1, now))
        assertEquals("in 1h00", Schedule.countdown(now + 59 * 60_000 + 1, now))
        assertEquals("in 2d 3h", Schedule.countdown(now + 51 * 3_600_000, now))
    }

    @Test fun `dated tennis rows combine source date and UTC plus one time`() {
        val html = """<h2>SUNDAY, SEPTEMBER 13</h2><h3>ATP</h3><table>
            <tr><td class="matchtime">19:00</td><td class="event-title">A vs B</td><td><a href="/watch">Watch</a></td></tr></table>"""
        val parsed = CatalogParser.parse(html, "https://example.test/", Instant.ofEpochMilli(now)).single()
        assertEquals(now, parsed.startsAt)
        assertEquals("ATP", parsed.competition)
    }

    @Test fun `american sports use ET with daylight saving and preserve each event channels`() {
        val html = """<h2>NFL STREAMS</h2><h2>SUNDAY, SEPTEMBER 13</h2>
            <section class="elementor-top-section"><p>1:00 PM ET</p><h2><span class="teamz">A @ B</span></h2>
            <a href="/one">CBS</a><a href="/two">CBS #2</a></section>
            <section class="elementor-top-section"><p>4:25 PM ET</p><h2><span class="teamz">C @ D</span></h2>
            <a href="/three">FOX</a></section>"""
        val parsed = CatalogParser.parse(html, "https://example.test/", Instant.ofEpochMilli(now))
        assertEquals(2, parsed.size)
        assertEquals(Instant.parse("2026-09-13T17:00:00Z").toEpochMilli(), parsed.first().startsAt)
        assertEquals(1, parsed.first().channels.size)
        assertEquals(2, parsed.first().channels.single().links.size)
        assertEquals("NFL STREAMS", parsed.last().competition)
        assertEquals("FOX", parsed.last().channels.single().name)
    }

    @Test fun `date headings preserve stale dates and cross year boundaries`() {
        fun parse(heading: String, reference: String) = CatalogParser.parse("""<h2>$heading</h2><table><tr>
            <td class="matchtime">01:00</td><td class="event-title">A vs B</td><td><a href="/watch">Watch</a></td></tr></table>""",
            "https://example.test/", Instant.parse(reference)).single().startsAt
        assertEquals(Instant.parse("2027-01-01T00:00:00Z").toEpochMilli(), parse("FRIDAY, JANUARY 1", "2026-12-31T12:00:00Z"))
        assertEquals(Instant.parse("2026-08-30T00:00:00Z").toEpochMilli(), parse("SUNDAY, AUGUST 30", "2026-09-13T12:00:00Z"))
    }

    @Test fun `stream choices preserve provider numbering and exclude external links`() {
        val options = StreamResolver.streamOptions("""<a href="/two">Stream 2</a><a href="/one"><span>Stream 1</span></a>
            <a href="https://ads.test/three">Stream 3</a><a href="/junk">Download</a>""", "https://player.test/one")
        assertEquals(listOf("Stream 1", "Stream 2"), options.map { it.label })
        assertEquals(listOf("https://player.test/one", "https://player.test/two"), options.map { it.url })
        assertEquals(listOf("https://player.test/embed"), PlayerPageParser.nextPages(
            """<a href="/two">Stream 2</a><iframe src="/embed"></iframe>""", "https://player.test/one"))
    }
}
