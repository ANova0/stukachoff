package com.stukachoff.domain.checker

import org.junit.Assert.*
import org.junit.Test

class MtuAnalyzerTest {

    @Test
    fun `MTU 1500 is STANDARD`() {
        assertEquals(MtuSignature.STANDARD, MtuAnalyzer.analyze(1500))
    }

    @Test
    fun `MTU 1420 is WIREGUARD`() {
        assertEquals(MtuSignature.WIREGUARD, MtuAnalyzer.analyze(1420))
    }

    @Test
    fun `MTU 1280 is AMNEZIA`() {
        assertEquals(MtuSignature.AMNEZIA, MtuAnalyzer.analyze(1280))
    }

    @Test
    fun `MTU 1000 is LOW_ANOMALY`() {
        assertEquals(MtuSignature.LOW_ANOMALY, MtuAnalyzer.analyze(1000))
    }

    @Test
    fun `MTU 1450 is STANDARD`() {
        assertEquals(MtuSignature.STANDARD, MtuAnalyzer.analyze(1450))
    }
}
