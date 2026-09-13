package fr.bonamy.sports.core

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) = runBlocking {
    val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS).callTimeout(18, TimeUnit.SECONDS).build()
    val http = PageClient(client)
    try {
        if (args.firstOrNull() == "--live-tv") {
            val countries = LiveTvRepository(http).countries()
            println("LiveTV: ${countries.size} countries; ${countries.sumOf { it.channels.size }} channel listings")
            countries.forEach { println("${it.code}: ${it.channels.size} channels") }
            return@runBlocking
        }
        val repository = SportsRepository(http)
        for (sport in if (args.isEmpty()) Sport.featured else emptyList()) {
            val events = repository.events(sport)
            val groups = events.groupingBy { Schedule.section(it, sport, System.currentTimeMillis()) }.eachCount()
            println("${sport.label}: ${events.size} events; ${events.sumOf { it.channels.size }} channels; $groups")
        }
        val resolver = StreamResolver(http, trace = { println(it) })
        val channel = args.firstOrNull()?.let { Channel("Requested channel", listOf(StreamLink("Stream 1", it))) }
            ?: Channel("Tennis Channel", listOf(StreamLink("Stream 1", SportsRepository.BASE + "tennis-channel/")))
        val options = resolver.streams(channel)
        println("${channel.name}: ${options.size} selectable streams")
        var playable = false
        for (option in options) {
            val stream = try { resolver.resolve(option) } catch (_: SourceUnavailable) {
                println("${option.label}: unavailable"); continue
            }
            var manifest = http.get(stream.url, stream.headers)
            check(manifest.body.trimStart().startsWith("#EXTM3U"))
            println("${option.label}: valid HLS manifest; signed expiry present=${stream.expiresAtMillis != null}")
            var playlistDepth = 0
            while (manifest.body.contains("#EXT-X-STREAM-INF:")) {
                check(playlistDepth++ < 4) { "Too many nested playlists" }
                val variant = manifest.body.lineSequence().first { it.isNotBlank() && !it.startsWith("#") }
                manifest = http.get(URI(manifest.url).resolve(variant).toString(), stream.headers)
                check(manifest.body.trimStart().startsWith("#EXTM3U"))
            }
            check(manifest.body.contains("#EXTINF:")) { "No media segments in playlist" }
            val segment = manifest.body.lineSequence().first { it.isNotBlank() && !it.startsWith("#") }
            val segmentUrl = URI(manifest.url).resolve(segment).toString()
            val request = Request.Builder().url(segmentUrl).apply { stream.headers.forEach { (key, value) -> header(key, value) } }.build()
            client.newCall(request).execute().use {
                check(it.isSuccessful) { "Media segment HTTP ${it.code}" }
                val bytes = it.body!!.byteStream().readNBytes(188 * 3)
                check(bytes.isNotEmpty())
                println("First media segment: HTTP ${it.code}, ${bytes.size} bytes sampled; MPEG-TS sync=${bytes.first() == 0x47.toByte()}")
            }
            playable = true
            break
        }
        check(playable) { "None of the channel's streams could be verified" }
    } finally {
        client.dispatcher.executorService.shutdownNow()
        client.connectionPool.evictAll()
    }
}
