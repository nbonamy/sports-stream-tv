package fr.bonamy.sports.core

import kotlin.test.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

class LiveTvTests {
    private fun group(code: String, links: String) = """<div class="dropdown"><button class="dropbtn">$code</button>
        <div class="dropdown-content">$links</div></div>"""

    @Test fun `directory puts personal countries first and preserves remaining provider order`() {
        val html = listOf("UK", "CA", "US", "PT", "FR").joinToString("") {
            group(it, """<a href="/$it-one/">First &amp; One</a><a href="/$it-two/">Second</a>""")
        }
        val countries = LiveTvParser.parse(html, "https://example.test/live-tv/")
        assertEquals(listOf("FR", "US", "UK", "CA", "PT"), countries.map { it.code })
        assertEquals("United Kingdom", countries[2].name)
        assertEquals(listOf("First & One", "Second"), countries.first().channels.map { it.name })
        assertEquals("https://example.test/FR-one/", countries.first().channels.first().links.single().url)
    }

    @Test fun `directory excludes unrelated links and deduplicates only within each country`() {
        val links = """<a href="http://example.test/tennis/">Tennis Channel</a>
            <a href="/tennis/">Duplicate</a><a href="https://ads.test/ad">Ad</a>
            <a href="https://user:pw@example.test/private">Credentials</a>
            <a href="javascript:alert(1)">Script</a><a href="#chat">Chat</a><a href="/empty"> </a>"""
        val countries = LiveTvParser.parse("""<a href="/menu">Menu</a>""" + group("UK", links) +
            group("FR", links) + group("UK", """<a href="/news/">News</a>""") +
            group("INVALID", links) + group("CA", """<a href="https://ads.test/">Ad</a>"""),
            "https://example.test/live-tv/")
        assertEquals(listOf("FR", "UK"), countries.map { it.code })
        assertEquals(listOf("Tennis Channel", "News"), countries[1].channels.map { it.name })
        assertEquals(1, countries[0].channels.size)
        assertEquals("https://example.test/tennis/", countries[0].channels[0].id)
    }

    @Test fun `repository reads the directory and fails cleanly for an empty provider page`() = runBlocking<Unit> {
        var html = group("FR", """<a href="/canal/">Canal+</a>""")
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals(SportsRepository.BASE + "live-tv/", chain.request().url.toString())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(html.toResponseBody()).build()
        }.build()
        val repository = LiveTvRepository(PageClient(client))
        assertEquals("Canal+", repository.countries().single().channels.single().name)
        html = "<h1>Temporarily unavailable</h1>"
        assertFailsWith<SourceUnavailable> { repository.countries() }
    }

    @Test fun `nhl moves to more sports without changing the other home sports`() {
        assertEquals(listOf(Sport.FOOTBALL, Sport.TENNIS, Sport.RUGBY, Sport.F1, Sport.GOLF,
            Sport.NFL, Sport.NBA, Sport.MLB), Sport.featured)
        assertEquals(Sport.NHL, Sport.more.first())
        assertEquals(Sport.entries.toSet(), (Sport.featured + Sport.more).toSet())
    }
}
