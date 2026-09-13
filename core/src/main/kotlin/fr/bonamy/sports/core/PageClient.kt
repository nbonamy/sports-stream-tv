package fr.bonamy.sports.core

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class Page(val url: String, val body: String)
class SourceUnavailable(message: String) : IOException(message)

class PageClient(private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS)
    .callTimeout(18, TimeUnit.SECONDS).build()) {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): Page = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT)
            .apply { headers.forEach { (key, value) -> header(key, value) } }.build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val page = response.use {
                        if (!it.isSuccessful) throw SourceUnavailable("The provider returned HTTP ${it.code}.")
                        val source = it.body?.source() ?: throw SourceUnavailable("Empty response from provider.")
                        val limit = 2L * 1024 * 1024
                        source.request(limit + 1)
                        if (source.buffer.size > limit) throw SourceUnavailable("Unexpected response from provider.")
                        Page(it.request.url.toString(), source.readUtf8())
                    }
                    if (continuation.isActive) continuation.resume(page)
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
            }
        })
    }
    companion object { const val USER_AGENT = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36" }
}
