package com.stukachoff.domain.checker

import com.stukachoff.domain.model.VpnStatus

interface VpnStatusChecker {
    suspend fun check(): VpnStatus
}
