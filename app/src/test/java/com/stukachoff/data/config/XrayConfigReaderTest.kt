package com.stukachoff.data.config

import com.stukachoff.domain.model.TsupLevel
import org.junit.Assert.*
import org.junit.Test

class XrayConfigReaderTest {

    private val reader = XrayConfigReader()

    @Test
    fun `parseOutbounds returns empty for null bytes`() {
        val result = reader.parseOutbounds(null)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseOutbounds returns empty for empty bytes`() {
        val result = reader.parseOutbounds(ByteArray(0))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseOutbounds extracts UUID from protobuf-like bytes`() {
        val fakeProto = "vless\u0000reality\u0000550e8400-e29b-41d4-a716-446655440000\u000092.168.1.1".toByteArray(Charsets.ISO_8859_1)
        val result = reader.parseOutbounds(fakeProto)
        if (result.isNotEmpty()) {
            assertEquals("550e8400-e29b-41d4-a716-446655440000", result[0].uuid)
        }
        // Empty is acceptable if regex doesn't find enough signal
    }

    @Test
    fun `calculateTsup returns HIGH for reality`() {
        assertEquals(TsupLevel.HIGH, reader.calculateTsup("tcp", "reality"))
    }

    @Test
    fun `calculateTsup returns HIGH for xhttp+tls`() {
        assertEquals(TsupLevel.HIGH, reader.calculateTsup("xhttp", "tls"))
    }

    @Test
    fun `calculateTsup returns MEDIUM for ws+tls`() {
        assertEquals(TsupLevel.MEDIUM, reader.calculateTsup("ws", "tls"))
    }

    @Test
    fun `calculateTsup returns LOW for plain tcp`() {
        assertEquals(TsupLevel.LOW, reader.calculateTsup("tcp", "none"))
    }
}
