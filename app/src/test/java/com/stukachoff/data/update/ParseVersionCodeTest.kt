package com.stukachoff.data.update

import org.junit.Assert.*
import org.junit.Test

class ParseVersionCodeTest {

    @Test
    fun `v1_0_0 parses correctly`() {
        assertEquals(10000, parseVersionCode("v1.0.0"))
    }

    @Test
    fun `v1_1_0 parses correctly`() {
        assertEquals(10100, parseVersionCode("v1.1.0"))
    }

    @Test
    fun `v2_0_0 parses correctly`() {
        assertEquals(20000, parseVersionCode("v2.0.0"))
    }

    @Test
    fun `v1_2_3 parses correctly`() {
        assertEquals(10203, parseVersionCode("v1.2.3"))
    }

    @Test
    fun `uppercase V prefix works`() {
        assertEquals(10000, parseVersionCode("V1.0.0"))
    }

    @Test
    fun `tag without prefix works`() {
        assertEquals(10100, parseVersionCode("1.1.0"))
    }

    @Test
    fun `invalid tag returns 0`() {
        assertEquals(0, parseVersionCode("invalid"))
    }

    @Test
    fun `newer version has higher code`() {
        assertTrue(parseVersionCode("v1.1.0") > parseVersionCode("v1.0.0"))
        assertTrue(parseVersionCode("v2.0.0") > parseVersionCode("v1.9.9"))
    }
}
