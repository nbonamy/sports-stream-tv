package fr.bonamy.sports.core

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import kotlin.math.abs

object MediaSegments {
    private const val LIMIT = 32 * 1024 * 1024
    private val png = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
    fun isPng(bytes: ByteArray): Boolean = bytes.size >= 8 && png.indices.all { bytes[it] == png[it] }
    private fun invalid(): Nothing = throw IOException("Unsupported media segment")
    private fun unsigned(bytes: ByteArray, offset: Int): Long =
        (0..3).fold(0L) { value, i -> (value shl 8) or (bytes[offset + i].toLong() and 255) }
    private fun checkCancelled() { if (Thread.currentThread().isInterrupted) throw InterruptedIOException() }
    private fun inflate(stream: InputStream, limit: Int): ByteArray = stream.use {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            checkCancelled()
            val n = it.read(buffer)
            if (n < 0) break
            if (n > limit - out.size()) invalid()
            out.write(buffer, 0, n)
        }
        out.toByteArray()
    }

    /** Decode the TIKTIKPX envelope without image APIs altering its RGB values. */
    fun decode(bytes: ByteArray): ByteArray {
        checkCancelled()
        if (!isPng(bytes)) return bytes
        if (bytes.size > LIMIT) invalid()
        var width = 0; var height = 0; var bpp = 0; var offset = 8; var ended = false
        val packed = ByteArrayOutputStream()
        while (offset + 12 <= bytes.size) {
            val sizeLong = unsigned(bytes, offset)
            if (sizeLong > bytes.size - offset - 12) invalid()
            val size = sizeLong.toInt()
            val type = String(bytes, offset + 4, 4, Charsets.US_ASCII)
            val data = offset + 8
            when (type) {
                "IHDR" -> {
                    if (offset != 8 || size != 13) invalid()
                    val w = unsigned(bytes, data); val h = unsigned(bytes, data + 4)
                    bpp = when (bytes[data + 9].toInt()) { 2 -> 3; 6 -> 4; else -> 0 }
                    if (w == 0L || h == 0L || w > LIMIT || h > LIMIT || bpp == 0 ||
                        bytes[data + 8].toInt() != 8 || bytes[data + 10].toInt() != 0 ||
                        bytes[data + 11].toInt() != 0 || bytes[data + 12].toInt() != 0 ||
                        (w * bpp + 1) * h > LIMIT) invalid()
                    width = w.toInt(); height = h.toInt()
                }
                "IDAT" -> { if (bpp == 0) invalid(); packed.write(bytes, data, size) }
                "IEND" -> { if (size != 0) invalid(); ended = true; break }
            }
            offset += size + 12
        }
        if (!ended || bpp == 0 || packed.size() == 0) invalid()
        val stride = width * bpp
        val expected = (stride + 1) * height
        val raw = inflate(InflaterInputStream(ByteArrayInputStream(packed.toByteArray())), expected)
        if (raw.size != expected) invalid()
        val rgb = ByteArray(width * height * 3)
        var previous = ByteArray(stride); var row = ByteArray(stride); var src = 0; var dst = 0
        for (y in 0 until height) {
            checkCancelled()
            val filter = raw[src++].toInt() and 255
            if (filter > 4) invalid()
            for (i in 0 until stride) {
                val a = if (i >= bpp) row[i - bpp].toInt() and 255 else 0
                val b = previous[i].toInt() and 255
                val c = if (i >= bpp) previous[i - bpp].toInt() and 255 else 0
                val predictor = when (filter) {
                    1 -> a
                    2 -> b
                    3 -> (a + b) shr 1
                    4 -> {
                        val p = a + b - c; val pa = abs(p - a); val pb = abs(p - b); val pc = abs(p - c)
                        if (pa <= pb && pa <= pc) a else if (pb <= pc) b else c
                    }
                    else -> 0
                }
                row[i] = ((raw[src++].toInt() and 255) + predictor).toByte()
            }
            for (i in 0 until stride step bpp) {
                rgb[dst++] = row[i]; rgb[dst++] = row[i + 1]; rgb[dst++] = row[i + 2]
            }
            val reuse = previous; previous = row; row = reuse
        }
        if (rgb.size < 12 || String(rgb, 0, 8, Charsets.US_ASCII) != "TIKTIKPX") invalid()
        val size = unsigned(rgb, 8)
        if (size == 0L || size > rgb.size - 12 || rgb[12].toInt() != 31 || (rgb[13].toInt() and 255) != 139) invalid()
        val ts = inflate(GZIPInputStream(ByteArrayInputStream(rgb, 12, size.toInt())), LIMIT)
        if (ts.size < 188 || ts.size % 188 != 0 || (ts.indices step 188).any { ts[it].toInt() != 0x47 }) invalid()
        return ts
    }
}
