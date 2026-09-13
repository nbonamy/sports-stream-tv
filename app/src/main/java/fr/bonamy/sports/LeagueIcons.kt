package fr.bonamy.sports

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI

/** Only the provider's league artwork is fetched; missing artwork leaves the row text intact. */
internal object LeagueIcons {
    private val cache = object : LruCache<String, Bitmap>(2 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val requests = Semaphore(4)

    suspend fun load(url: String): Bitmap? {
        cache.get(url)?.let { return it }
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host !in setOf("cdn.livesoccertv.com", "a.espncdn.com", "static.flashscore.com", "freestreams-live1h.pk")) return null
        return requests.withPermit {
            withContext(Dispatchers.IO) {
                val connection = uri.toURL().openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 5000; connection.readTimeout = 5000
                    connection.instanceFollowRedirects = false
                    if (connection.responseCode != 200 || connection.contentLength > 512 * 1024) return@withContext null
                    val data = connection.inputStream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (output.size() + count > 512 * 1024) return@withContext null
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = 1
                        while (bounds.outWidth / inSampleSize > 256 || bounds.outHeight / inSampleSize > 256) inSampleSize *= 2
                    }
                    BitmapFactory.decodeByteArray(data, 0, data.size, options)?.also { cache.put(url, it) }
                } catch (_: java.io.IOException) { null }
                finally { connection.disconnect() }
            }
        }
    }
}
