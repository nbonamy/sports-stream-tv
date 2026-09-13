package fr.bonamy.sports.core

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.concurrent.TimeUnit

fun main() = runBlocking {
    val repository = SportsRepository()
    for (sport in Sport.featured) {
        val events = repository.events(sport)
        val groups = events.groupingBy { Schedule.section(it, sport, System.currentTimeMillis()) }.eachCount()
        println("${sport.label}: ${events.size} events; ${events.sumOf { it.channels.size }} channels; $groups")
    }
    val resolver = StreamResolver(trace = { println(it) })
    val options = resolver.streams(SportsRepository.tennisChannel.channels.first())
    println("Tennis Channel +1: ${options.size} selectable streams")
    var playable = false
    for (option in options) {
        val stream = try { resolver.resolve(option) } catch (_: SourceUnavailable) {
            println("${option.label}: unavailable"); continue
        }
        val manifest = PageClient().get(stream.url, stream.headers)
        check(manifest.body.trimStart().startsWith("#EXTM3U"))
        println("${option.label}: valid HLS manifest; signed expiry present=${stream.expiresAtMillis != null}")
        val segment = manifest.body.lineSequence().first { it.isNotBlank() && !it.startsWith("#") }
        val segmentUrl = URI(manifest.url).resolve(segment).toString()
        val request = Request.Builder().url(segmentUrl).apply { stream.headers.forEach { (key, value) -> header(key, value) } }.build()
        OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build().newCall(request).execute().use {
            check(it.isSuccessful) { "Media segment HTTP ${it.code}" }
            val bytes = it.body!!.byteStream().readNBytes(188 * 3)
            check(bytes.isNotEmpty())
            println("First media segment: HTTP ${it.code}, ${bytes.size} bytes sampled; MPEG-TS sync=${bytes.first() == 0x47.toByte()}")
        }
        playable = true
        break
    }
    check(playable) { "None of the channel's streams could be verified" }
}
