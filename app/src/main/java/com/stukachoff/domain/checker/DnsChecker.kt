package com.stukachoff.domain.checker

import com.stukachoff.domain.model.CheckResult

interface DnsChecker {
    suspend fun check(): CheckResult.Fixable
}
