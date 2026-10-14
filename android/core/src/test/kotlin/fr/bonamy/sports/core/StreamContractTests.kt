package fr.bonamy.sports.core

import com.google.gson.Gson
import java.io.InputStreamReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import java.io.IOException
import java.util.Base64

class StreamContractTests {
    private data class PlaylistContract(val version: Int, val cases: List<PlaylistCase>)
    private data class PlaylistCase(val name: String, val html: String, val expectedUrl: String?)
    private data class MediaContract(val version: Int, val cases: List<MediaCase>)
    private data class MediaCase(val name: String, val inputBase64: String, val expectedBase64: String?)

    @Test fun `shared media segment contract`() {
        val input = checkNotNull(javaClass.getResourceAsStream("/streams/media-cases.json"))
        val contract = input.use { Gson().fromJson(InputStreamReader(it), MediaContract::class.java) }
        assertEquals(1, contract.version)
        contract.cases.forEach { fixture ->
            val bytes = Base64.getDecoder().decode(fixture.inputBase64)
            if (fixture.expectedBase64 == null) assertFailsWith<IOException>(fixture.name) { MediaSegments.decode(bytes) }
            else assertContentEquals(Base64.getDecoder().decode(fixture.expectedBase64), MediaSegments.decode(bytes), fixture.name)
        }
    }

    @Test fun `shared playlist extraction contract`() {
        val input = checkNotNull(javaClass.getResourceAsStream("/streams/playlist-cases.json"))
        val contract = input.use { Gson().fromJson(InputStreamReader(it), PlaylistContract::class.java) }

        assertEquals(1, contract.version)
        assertTrue(contract.cases.isNotEmpty())
        contract.cases.forEach { fixture ->
            assertEquals(fixture.expectedUrl, PlayerPageParser.playlist(fixture.html), fixture.name)
        }
    }
}
