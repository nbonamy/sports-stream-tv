package fr.bonamy.sports.core

import com.google.gson.Gson
import java.io.InputStreamReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamContractTests {
    private data class PlaylistContract(val version: Int, val cases: List<PlaylistCase>)
    private data class PlaylistCase(val name: String, val html: String, val expectedUrl: String?)

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
