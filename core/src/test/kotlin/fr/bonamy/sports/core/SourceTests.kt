package fr.bonamy.sports.core

import kotlin.test.*
import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.util.concurrent.TimeUnit

class SourceTests {
    @Test fun `catalog only includes match rows and preserves source alternatives`() {
        val html = """<h2>Premier League</h2><a href="/ad">Ad</a><table>
          <tr data-timestamp="1789340400000"><td class="event-title"><img src="/team.png">Team A vs Team B</td><td class="leaguename"><img class="leagueimg" src="https://cdn.livesoccertv.com/league.png">Premier League</td>
          <td><a href="/a">English</a><a href="http://example.test/b">French</a><a href="https://ads.test/x">Ad</a></td></tr>
          <tr><td>No event title</td><td><a href="/c">Skip</a></td></tr></table>"""
        val events = CatalogParser.parse(html, "https://example.test/soccer/")
        assertEquals(1, events.size)
        assertEquals("Team A vs Team B", events[0].title)
        assertEquals(1789340400000, events[0].startsAt)
        assertEquals("https://cdn.livesoccertv.com/league.png", events[0].leagueIconUrl)
        assertEquals(listOf("https://example.test/a", "https://example.test/b"), events[0].links.map { it.url })
    }

    @Test fun `tennis schedule does not invent a date or live status`() {
        val events = CatalogParser.parse("""<h2>US Open</h2><table><tr><td class="matchtime">19:00</td>
        <td class="event-title">A vs B</td><td><a href="/watch">Watch</a></td></tr></table>""", "https://example.test/")
        assertNull(events.single().startsAt)
        assertEquals("19:00 · source time (UTC+1)", events.single().timeLabel)
    }

    @Test fun `signed HLS data can be assembled without executing scripts`() {
        val html = """<span id="tail">99</span><script>var extra=["&amp;expires="];
            function url() { return(["https:\/\/cdn.test\/hls\/t1.m3u8?md5=abc"].join("") + extra.join("") + document.getElementById("tail").innerHTML); }
            </script>"""
        assertEquals("https://cdn.test/hls/t1.m3u8?md5=abc&amp;expires=99", PlayerPageParser.playlist(html))
    }

    @Test fun `changed executable expressions are rejected`() {
        assertNull(PlayerPageParser.playlist("""return(["https://cdn.test/a.m3u8"].join("") + fetchSecret());"""))
        assertNull(PlayerPageParser.playlist("""source: 'javascript:alert(1)'"""))
    }

    @Test fun `known dynamic embed is derived while ads are ignored`() {
        val next = PlayerPageParser.nextPages("""<script>fid="t1"</script><script src="//igniteandship.com/wiki.js"></script>
            <iframe src="https://ads.test/player"></iframe>""", "https://wikisport.info/court/t1.php")
        assertEquals(listOf("https://igniteandship.com/wiki.php?player=desktop&live=t1"), next)
    }

    @Test fun `alternate embed wins and nested frames are followed`() {
        assertEquals(listOf("https://wikisport.info/two", "https://wikisport.info/one"),
            PlayerPageParser.nextPages("""<a href="/two">Stream 2</a><iframe src="/one"></iframe>""", "https://wikisport.info/"))
    }

    @Test fun `HTTP headers are applied and errors do not become valid pages`() = runBlocking<Unit> {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("#EXTM3U"))
            val client = PageClient()
            assertEquals("#EXTM3U", client.get(server.url("/test").toString(), mapOf("Origin" to "https://player.test")).body)
            assertEquals("https://player.test", server.takeRequest().getHeader("Origin"))
            server.enqueue(MockResponse().setResponseCode(403))
            assertFailsWith<SourceUnavailable> { client.get(server.url("/denied").toString()) }
        }
    }

    @Test fun `cancelling navigation cancels pending network work`() = runBlocking<Unit> {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("late").setBodyDelay(2, TimeUnit.SECONDS))
            assertFailsWith<TimeoutCancellationException> {
                withTimeout(100) { PageClient().get(server.url("/slow").toString()) }
            }
        }
    }
}
