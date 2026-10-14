package fr.bonamy.sports

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.json.JSONObject
import java.util.Base64
import java.io.ByteArrayOutputStream

@UnstableApi
@RunWith(RobolectricTestRunner::class)
class SegmentDataSourceTests {
    private val spec = DataSpec(Uri.parse("https://media.test/segment.png"))
    private fun read(source: DataSource): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(17)
        while (true) {
            val n = source.read(buffer, 0, buffer.size)
            if (n == C.RESULT_END_OF_INPUT) break
            output.write(buffer, 0, n)
        }
        return output.toByteArray()
    }

    @Test fun `HLS media factory delivers decoded segments and reopens cleanly`() {
        val json = javaClass.getResourceAsStream("/streams/media-cases.json")!!.bufferedReader().use { it.readText() }
        val fixture = JSONObject(json).getJSONArray("cases").getJSONObject(0)
        val encoded = Base64.getDecoder().decode(fixture.getString("inputBase64"))
        val expected = Base64.getDecoder().decode(fixture.getString("expectedBase64"))
        val factory = SegmentDataSource.factory(DataSource.Factory { ByteArrayDataSource(encoded) })
        val source = factory.createDataSource(C.DATA_TYPE_MEDIA)
        repeat(2) {
            source.open(spec)
            try { assertArrayEquals(expected, read(source)) } finally { source.close() }
        }
        // Keys and manifests must never go through a media-envelope decoder.
        val key = factory.createDataSource(C.DATA_TYPE_DRM)
        key.open(spec)
        try { assertArrayEquals(encoded, read(key)) } finally { key.close() }
    }

    @Test fun `ordinary media retains ranges and small reads`() {
        val bytes = ByteArray(400) { it.toByte() }
        val source = SegmentDataSource.factory(DataSource.Factory { ByteArrayDataSource(bytes) }).createDataSource(C.DATA_TYPE_MEDIA)
        val range = DataSpec.Builder().setUri(spec.uri).setPosition(11).setLength(27).build()
        assertEquals(27L, source.open(range))
        try {
            assertEquals(0, source.read(ByteArray(1), 0, 0))
            assertArrayEquals(bytes.copyOfRange(11, 38), read(source))
        } finally { source.close() }
    }
}
