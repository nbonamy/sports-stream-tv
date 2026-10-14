package fr.bonamy.sports

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.exoplayer.hls.HlsDataSourceFactory
import fr.bonamy.sports.core.MediaSegments
import java.io.ByteArrayOutputStream
import java.io.IOException

@UnstableApi
class SegmentDataSource(private val upstream: DataSource) : DataSource by upstream {
    private var buffered = ByteArray(0)
    private var position = 0
    private var decoded = false

    override fun open(dataSpec: DataSpec): Long {
        buffered = ByteArray(0); position = 0; decoded = false
        try {
            val length = upstream.open(dataSpec)
            val prefix = ByteArray(8)
            var size = 0
            while (size < prefix.size) {
                val n = upstream.read(prefix, size, prefix.size - size)
                if (n == C.RESULT_END_OF_INPUT) break
                size += n
            }
            buffered = prefix.copyOf(size)
            if (!MediaSegments.isPng(buffered)) return length
            val bytes = ByteArrayOutputStream().apply { write(prefix) }
            val block = ByteArray(8192)
            while (true) {
                val n = upstream.read(block, 0, block.size)
                if (n == C.RESULT_END_OF_INPUT) break
                if (n > 32 * 1024 * 1024 - bytes.size()) throw IOException("Media segment too large")
                bytes.write(block, 0, n)
            }
            buffered = MediaSegments.decode(bytes.toByteArray())
            decoded = true
            return buffered.size.toLong()
        } catch (error: Exception) {
            try { close() } catch (_: IOException) { /* Preserve the original error. */ }
            throw error
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (position < buffered.size) {
            val n = minOf(length, buffered.size - position)
            buffered.copyInto(buffer, offset, position, position + n)
            position += n
            return n
        }
        return if (decoded) C.RESULT_END_OF_INPUT else upstream.read(buffer, offset, length)
    }

    override fun close() {
        buffered = ByteArray(0); position = 0; decoded = false
        upstream.close()
    }

    companion object {
        fun factory(upstream: DataSource.Factory) = HlsDataSourceFactory { type ->
            val source = upstream.createDataSource()
            if (type == C.DATA_TYPE_MEDIA) SegmentDataSource(source) else source
        }
    }
}
