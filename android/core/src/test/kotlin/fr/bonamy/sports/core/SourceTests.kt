package fr.bonamy.sports.core

import kotlin.test.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.util.concurrent.TimeUnit

class SourceTests {
    @Test fun `continuous channel section excludes detached timed fixtures`() {
        fun row(title: String, time: String = "") = """<tr><td class="matchtime">$time</td><td class="event-title">$title</td><td><a href="/$title/">Watch</a></td></tr>"""
        val html = """<h2>SEPTEMBER 14, 2026</h2><h2>WTA</h2><table>${row("WTA-match", "19:00")}</table>
            <h2>24/7 CHANNELS</h2><table>${row("TENNIS-CHANNEL")}</table>
            <section class="spacer"></section><div><table>${row("leftover-match", "11:00")}</table></div>
            <h2>SEPTEMBER 15, 2026</h2><h2>ATP</h2><table>${row("next-day-match", "12:00")}</table>"""
        val events = CatalogParser.parse(html, "https://example.test/")
        assertEquals(listOf("WTA-match", "TENNIS-CHANNEL", "next-day-match"), events.map { it.title })
        assertTrue(events[1].isChannel)
    }

    @Test fun `evening fixture times roll past midnight per competition table`() {
        fun row(title: String, time: String, timestamp: String = "") = """<tr data-timestamp="$timestamp"><td class="matchtime">$time</td><td class="event-title">$title</td><td><a href="/$title/">Watch</a></td></tr>"""
        val explicit = java.time.Instant.parse("2026-09-14T20:00:00Z").toEpochMilli()
        val html = """<h2>SEPTEMBER 14, 2026</h2><h2>WTA A</h2><table>
            ${row("evening", "22:00")}${row("midnight", "00:00")}${row("late", "01:30")}</table>
            <h2>WTA B</h2><table>${row("afternoon", "17:00")}${row("earlier", "16:00")}${row("explicit", "00:00", explicit.toString())}</table>"""
        assertEquals(listOf("2026-09-14T21:00:00Z", "2026-09-14T23:00:00Z", "2026-09-15T00:30:00Z",
            "2026-09-14T16:00:00Z", "2026-09-14T15:00:00Z", "2026-09-14T20:00:00Z").map { java.time.Instant.parse(it).toEpochMilli() },
            CatalogParser.parse(html, "https://example.test/").map { it.startsAt })
    }

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
        for (script in listOf("//igniteandship.com/wiki.js", "https://igniteandship.com/wiki.js?v=2")) {
            val next = PlayerPageParser.nextPages("""<script>fid="t1"</script><script src="$script"></script>
                <iframe title="Advertisement" src="https://ads.test/player"></iframe>""", "https://wikisport.info/court/t1.php")
            assertEquals(listOf("https://igniteandship.com/wiki.php?player=desktop&live=t1"), next)
        }
        assertTrue(PlayerPageParser.nextPages(
            """<script>fid="t1"</script><script src="https://user:pw@igniteandship.com/wiki.js"></script>""",
            "https://wikisport.info/court/t1.php").isEmpty())
    }

    @Test fun `numbered alternatives stay separate from selected embeds`() {
        assertEquals(listOf("https://wikisport.info/one"),
            PlayerPageParser.nextPages("""<a href="/two">Stream 2</a><iframe src="/one"></iframe>""", "https://wikisport.info/"))
    }

    @Test fun `win sports follows its embed and resolves the referenced playback URL`() = runBlocking {
        val channel = "https://freestreams-live1h.pk/winsports/"
        val embed = "https://la18hd.su/vivo/canales.php?stream=win"
        val media = "https://media.test/live/win.m3u8"
        val pages = mapOf(
            channel to """<iframe src="$embed"></iframe><iframe src="https://ads.test/ad"></iframe>""",
            embed to """<script>var playbackURL = "$media";
                var player = new Clappr.Player({source: playbackURL, parentId: "#player"});</script>""",
            media to "#EXTM3U\n#EXTINF:6,\nsegment.ts",
        )
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request
            val body = pages[request.url.toString()] ?: error("Unexpected request host: ${request.url.host}")
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody()).build()
        }.build()
        val stream = StreamResolver(PageClient(client)).resolve(StreamLink("Stream 1", channel))
        assertEquals(media, stream.url)
        assertEquals(listOf(channel, embed, media), requests.map { it.url.toString() })
        assertEquals(channel, requests[1].header("Referer"))
        assertEquals("https://la18hd.su/", stream.headers["Referer"])
        assertEquals("https://la18hd.su", stream.headers["Origin"])
    }

    private fun indexedPlayer(url: String): String {
        val pieces = url.mapIndexed { index, char ->
            val encoded = java.util.Base64.getEncoder().encodeToString("ab${char.code + 12345}cd".toByteArray())
            "[$index,\"$encoded\"]"
        }.reversed().joinToString(",")
        return """var playbackURL="",xq=[],_s="abc"; xq=[$pieces];
            xq.sort((a,b)=>a[0]-b[0]); var k=first()+second();
            xq.forEach(e=>{ let v=e[1]; playbackURL+=String.fromCharCode(parseInt(atob(v).replace(/\D/g,''))-k)});
            function first(){return 12000;} function second(){return 345;}
            new Clappr.Player({source: playbackURL, parentId: "#player"});"""
    }

    @Test fun `directv resolves shuffled encoded playlist data through its embed`() = runBlocking {
        val channel = "https://freestreams-live1h.pk/directv-sports/"
        val embed = "https://stream-xhd.com/live2.php?channel=directv"
        val media = "https://media.test/live/directv.m3u8?expires=1999999999"
        val pages = mapOf(channel to """<iframe src="$embed"></iframe>""",
            embed to indexedPlayer(media).replace("xq", "ve"), media to "#EXTM3U\n#EXTINF:6,\nsegment.ts")
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request(); requests += request
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body((pages[request.url.toString()] ?: error("Unexpected request")).toResponseBody()).build()
        }.build()
        val stream = StreamResolver(PageClient(client)).resolve(StreamLink("Stream 1", channel))
        assertEquals(media, stream.url)
        assertEquals(listOf(channel, embed, media), requests.map { it.url.toString() })
        assertEquals("https://stream-xhd.com/", stream.headers["Referer"])
        assertEquals(1999999999000, stream.expiresAtMillis)
    }

    @Test fun `indexed playlist decoder rejects changed operations and malformed data`() {
        val html = indexedPlayer("https://media.test/live.m3u8")
        assertNull(PlayerPageParser.playlist(html.replace("return 345;", "return getOffset();")))
        assertNull(PlayerPageParser.playlist(html.replace("-k)", "+k)")))
        assertNull(PlayerPageParser.playlist(html.replace("[0,", "[1,")))
        assertNull(PlayerPageParser.playlist(html.replace("source: playbackURL", "source: otherURL")))
        assertNull(PlayerPageParser.playlist(indexedPlayer("javascript:alert(1)")))
    }

    private fun encodedConfig(json: String): String {
        val encoder = java.util.Base64.getEncoder()
        val inner = encoder.encodeToString(json.toByteArray())
        val parts = inner.chunked(inner.length / 4)
        val shuffled = listOf(2, 0, 3, 1).joinToString("") { index ->
            val part = encoder.encodeToString(parts[index].toByteArray())
            part.take(3) + "X" + part.drop(3)
        }
        return """<script>window._econfig="${encoder.encodeToString(shuffled.toByteArray())}"</script>"""
    }

    @Test fun `tennis stream two follows barecrop config without switching to stream one`() = runBlocking {
        val channel = "https://freestreams-live1h.pk/tennis-channel/"
        val wrapper = "https://wikisport.info/court/ten20.php"
        val second = "https://wikisport.info/court/ten201.php"
        val frame = "https://wikisport.info/court/wik20.php"
        val player = "https://barecrop.net/player/wik20"
        val media = "https://media.test/tennis.m3u8"
        val pages = mapOf(
            channel to """<iframe src="$wrapper"></iframe>""",
            wrapper to """<a href="/court/ten20.php">Stream 1</a><a href="$second">Stream 2</a>""",
            second to """<iframe src="$frame"></iframe>""",
            frame to """<iframe src="$player"></iframe>""",
            player to encodedConfig("""{"stream_url":"https://p2p.test/tennis.m3u8","stream_url_nop2p":"$media"}"""),
            media to "#EXTM3U\n#EXTINF:6,\nsegment.ts",
        )
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request(); requests += request
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body((pages[request.url.toString()] ?: error("Unexpected request")).toResponseBody()).build()
        }.build()
        val resolver = StreamResolver(PageClient(client))
        val options = resolver.streams(Channel("Tennis Channel", listOf(StreamLink("Stream 1", channel))))
        assertEquals(listOf(wrapper, second), options.map { it.url })
        requests.clear()
        val stream = resolver.resolve(options[1])
        assertEquals(media, stream.url)
        assertEquals(listOf(second, frame, player, media), requests.map { it.url.toString() })
        assertEquals("https://barecrop.net/", stream.headers["Referer"])
    }

    @Test fun `encoded player config handles absent backup and rejects malformed data`() {
        assertEquals("https://media.test/a.m3u8", PlayerPageParser.playlist(encodedConfig("""{"stream_url":"https://media.test/a.m3u8"}""")))
        assertNull(PlayerPageParser.playlist("""window._econfig="not-base64";"""))
        assertNull(PlayerPageParser.playlist(encodedConfig("""{"stream_url":"javascript:alert(1)"}""")))
        assertNull(PlayerPageParser.playlist(encodedConfig("{}")))
    }

    @Test fun `encoded channel chains preserve selected source and player request context`() = runBlocking {
        val cases = listOf(
            listOf("canal-plus-fr/", "https://wikisport.info/play/canalfr1.php",
                "https://quellefrappe.click/ty/1/11", "https://traitaunt.net/embed/canal"),
            listOf("beinsp1-fr/", "https://freestreams-live1h.pk/beinfr1-s1/",
                "https://quellefrappe.click/ty/1/1", "https://traitaunt.net/embed/bein1"),
            listOf("beinsp2fr/", "https://freestreams-live1h.pk/beinfr2-s1/",
                "https://quellefrappe.click/ty/1/2", "https://traitaunt.net/embed/bein2"),
            listOf("hbotv/", "https://freestreams-live1h.pk/hbo-s1/",
                "https://dlive.sx/stream/stream-321.php", "https://assetrage.net/e/hbo"),
        )
        for ((index, entry) in cases.withIndex()) {
            val (path, wrapper, gateway, player) = entry
            val channel = SportsRepository.BASE + path
            val origin = java.net.URI(player).let { "${it.scheme}://${it.host}" }
            val media = "https://media.test/fr$index.m3u8"
            val pages = mapOf(
                channel to """<iframe src="$wrapper"></iframe>""",
                wrapper to """<a href="/other">Stream 2</a><iframe src="$gateway" width="100%" height="550" allowfullscreen></iframe>
                    <iframe src="https://ads.test/player"></iframe>""",
                gateway to """<iframe src="$player"></iframe><script src="https://ads.test/ad.js"></script>""",
                player to encodedConfig("""{"stream_url":"https://p2p.test/fr.m3u8","stream_url_nop2p":"$media"}"""),
                media to "#EXTM3U\n#EXTINF:6,\nsegment.ts",
            )
            val requests = mutableListOf<Request>()
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request(); requests += request
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body((pages[request.url.toString()] ?: error("Unexpected request")).toResponseBody()).build()
            }.build()
            val stream = StreamResolver(PageClient(client)).resolve(StreamLink("Stream 1", channel))
            assertEquals(listOf(channel, wrapper, gateway, player, media), requests.map { it.url.toString() })
            assertEquals(gateway, requests[3].header("Referer"))
            assertEquals(PageClient.USER_AGENT, requests[3].header("User-Agent"))
            assertEquals("$origin/", stream.headers["Referer"])
            assertEquals(origin, stream.headers["Origin"])
            assertEquals(stream.headers["Referer"], requests.last().header("Referer"))
            assertEquals(media, stream.url)
        }
    }

    @Test fun `playback variable must be a literal and actually used as the source`() {
        assertNull(PlayerPageParser.playlist("""var playbackURL = "https://cdn.test/a.m3u8"; source: otherURL,"""))
        assertNull(PlayerPageParser.playlist("""var playbackURL = "https://cdn.test/a.m3u8" + getToken(); source: playbackURL,"""))
    }

    @Test fun `literal array source uses only the player selected index`() {
        val media = "https://media.test/live/channel.m3u8"
        for (name in listOf("streamUrls", "rotated_\$42")) {
            assertEquals(media, PlayerPageParser.playlist(
                """const $name = ["https://other.test/other.m3u8", "$media"]; player.setup({file: $name[1],});"""))
            for (expression in listOf("$name[2]", "$name[-1]", "$name[getIndex()]", "$name[0] + evil()")) {
                assertNull(PlayerPageParser.playlist(
                    """const $name = ["$media"]; player.setup({file: $expression,});"""))
            }
        }
        for (data in listOf(
            "[\"http://insecure.test/live.m3u8\"]",
            "[\"https://127.0.0.1/live.m3u8\"]",
            "[\"https://cdn.test/live.mp4\"]",
            "[42]", "[evil()]", "[\"$media\", null]", "[\"$media\"] + evil()",
        )) assertNull(PlayerPageParser.playlist("""const urls = $data; player.setup({file: urls[0],});"""))
        assertNull(PlayerPageParser.playlist(
            """const urls = ["$media"]; player.setup({file: other[0],});"""))
    }

    @Test fun `giants stream one resolves its selected literal array source`() = runBlocking {
        val selected = "https://freestreams-live1h.pk/new-york-giants-live-stream/"
        val wrapper = "https://wikisport.info/nfl0/"
        val stream1 = wrapper + "01.php"
        val player = "https://in-stream.click/embed/giants"
        val media = "https://media.test/live/giants.m3u8"
        val pages = mapOf(
            selected to """<iframe src="$wrapper" allowfullscreen></iframe>""",
            wrapper to """<a href="01.php">Stream 1</a><a href="012.php">Stream 2</a><iframe src="01.php" allowfullscreen></iframe>""",
            stream1 to """<iframe src="$player" allowfullscreen></iframe>""",
            player to """const streamUrls = ["$media", "https://other.test/alternative.m3u8"];
                player.setup({file: streamUrls[0], autostart: false});""",
            media to "#EXTM3U\n#EXTINF:8,\nsegment.ts",
        )
        val requests = mutableListOf<Request>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request(); requests += request
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body((pages[request.url.toString()] ?: error("Unexpected request")).toResponseBody()).build()
        }.build()
        val result = StreamResolver(PageClient(client)).resolve(StreamLink("Stream 1", selected))
        assertEquals(media, result.url)
        assertEquals(listOf(selected, wrapper, stream1, player, media), requests.map { it.url.toString() })
        assertEquals("https://in-stream.click", result.headers["Origin"])
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
