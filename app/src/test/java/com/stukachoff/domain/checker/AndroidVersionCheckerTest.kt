package com.stukachoff.domain.checker

import com.stukachoff.domain.model.CheckStatus
import org.junit.Assert.*
import org.junit.Test

class AndroidVersionCheckerTest {

    private val checker = AndroidVersionChecker()

    @Test
    fun `Android 10 returns GREEN`() {
        assertEquals(CheckStatus.GREEN, checker.classify(29))
    }

    @Test
    fun `Android 11 returns GREEN`() {
        assertEquals(CheckStatus.GREEN, checker.classify(30))
    }

    @Test
    fun `Android 13 returns GREEN`() {
        assertEquals(CheckStatus.GREEN, checker.classify(33))
    }

    @Test
    fun `Android 9 returns RED`() {
        assertEquals(CheckStatus.RED, checker.classify(28))
    }

    @Test
    fun `Android 8 returns RED`() {
        assertEquals(CheckStatus.RED, checker.classify(26))
    }

    @Test
    fun `check result has correct id`() {
        val result = checker.check()
        assertEquals("android_version", result.id)
    }
}
