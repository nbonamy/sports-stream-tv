package fr.bonamy.sports.core

import kotlinx.coroutines.runBlocking
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.test.*

class PlayerDiscoveryTests {
    @Test fun `invalid and local destinations are rejected before a request is sent`() = runBlocking {
        var requested = false
        val http = PageClient(OkHttpClient.Builder().addInterceptor {
            requested = true; error("No request should be made")
        }.build())
        for (url in listOf("http://public.test/", "https://localhost/", "https://box.local/",
            "https://127.0.0.1/", "https://192.168.1.4/", "https://100.64.0.1/", "https://[::1]/",
            "https://[fd00::1]/", "https://user:password@public.test/", "https:///wiki.php",
            "https://public.test/a bad path", "file:///etc/hosts")) {
            assertFalse(PlayerDestination.accepts(url), url)
            assertFailsWith<SourceUnavailable> { http.getPlayerPage(url) }
        }
        assertFalse(requested)
        assertTrue(PlayerDestination.accepts("https://new-player.test/embed"))
    }

    @Test fun `public hostnames resolving to local addresses are blocked before connecting`() = runBlocking {
        var lookedUp = false
        val client = OkHttpClient.Builder().dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                lookedUp = true
                return listOf(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
            }
        }).build()
        try {
            assertFailsWith<UnknownHostException> { PageClient(client).getPlayerPage("https://new-player.test/embed") }
            assertTrue(lookedUp)
        } finally {
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    }
    @Test fun `unseen domains work through discovery and resolution without changing stream selection`() = runBlocking {
        for (suffix in listOf("first", "rotated")) {
            val channel = "https://catalog.test/channel"
            val wrapper = "https://wrapper-$suffix.test/options"
            val selected = "https://wrapper-$suffix.test/two"
            val embed = "https://player-$suffix.test/embed"
            val media = "https://media-$suffix.test/live.m3u8"
            val requests = mutableListOf<Request>()
            val pages = mapOf(
                channel to """<iframe title="Advertisement" src="https://unrelated.test/ad"></iframe>
                    <iframe allowfullscreen src="$wrapper"></iframe>""",
                wrapper to """<a href="/one">Stream 1</a><a href="/two">Stream 2</a>""",
                selected to """<a href="/one">Stream 1</a><iframe src="$embed"></iframe>""",
                embed to """<script>var media = "$media"; new Clappr.Player({source: media, parentId: "#player"});</script>""",
                media to "#EXTM3U\n#EXTINF:6,\nsegment.ts",
            )
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                requests += chain.request()
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body((pages[chain.request().url.toString()] ?: error("Unexpected request")).toResponseBody()).build()
            }.build()
            val resolver = StreamResolver(PageClient(client))
            val options = resolver.streams(Channel("Channel", listOf(StreamLink("Watch", channel))))
            assertEquals(listOf("https://wrapper-$suffix.test/one", selected), options.map { it.url })
            assertEquals(listOf(channel, wrapper), requests.map { it.url.toString() })
            requests.clear()
            val result = resolver.resolve(options[1])
            assertEquals(listOf(selected, embed, media), requests.map { it.url.toString() })
            assertEquals(media, result.url)
            assertEquals(selected, requests[1].header("Referer"))
            assertEquals("https://player-$suffix.test/", result.headers["Referer"])
        }
    }

    @Test fun `player frames precede generic frames and exclude hidden ads and tracking`() {
        val html = """<iframe src="https://generic.test/frame"></iframe>
            <iframe title="Advertisement" src="https://other.test/ad"></iframe>
            <div style="display:none"><iframe src="https://hidden.test/frame"></iframe></div>
            <iframe width="1" height="1" src="https://tracking.test/pixel"></iframe>
            <div class="chat-container"><iframe src="https://chat.test/frame"></iframe></div>
            <iframe id="video-player" allowfullscreen src="https://unseen.test/stream"></iframe>
            <iframe src="about:blank" data-src="https://lazy.test/embed"></iframe>"""
        assertEquals(listOf("https://unseen.test/stream", "https://lazy.test/embed", "https://generic.test/frame"),
            PlayerPageParser.nextPages(html, "https://catalog.test/channel"))
    }

    @Test fun `unknown player scripts do not become streams and traversal remains bounded`() = runBlocking<Unit> {
        val requests = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val url = chain.request().url.toString(); requests += url
            val html = (0..19).joinToString("") { """<iframe src="https://unknown$it.test/embed"></iframe>""" } +
                """<script src="https://unrelated.test/run.js"></script>"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(html.toResponseBody()).build()
        }.build()
        assertFailsWith<SourceUnavailable> {
            StreamResolver(PageClient(client)).resolve(StreamLink("Stream 1", "https://catalog.test/channel"))
        }
        assertTrue(requests.size <= 12)
        assertEquals(requests.distinct(), requests)
        assertFalse(requests.any { it.endsWith(".js") })
    }
}
