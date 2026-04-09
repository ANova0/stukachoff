package com.stukachoff.data.network

import com.stukachoff.domain.model.CheckResult
import com.stukachoff.domain.model.CheckStatus
import com.stukachoff.domain.model.HarmSeverity

object SystemProxyAnalyzer {
    fun check(): CheckResult.Fixable {
        val httpProxy = System.getProperty("http.proxyHost")
        val socksProxy = System.getProperty("socksProxyHost")
        val proxySet = !httpProxy.isNullOrBlank() || !socksProxy.isNullOrBlank()
        return CheckResult.Fixable(
            id           = "system_proxy",
            title        = "Системный прокси",
            status       = if (proxySet) CheckStatus.RED else CheckStatus.GREEN,
            harm         = if (proxySet)
                "IP прокси ${httpProxy ?: socksProxy} виден всем приложениям без разрешений"
            else
                "Системный прокси не установлен (TUN-режим)",
            harmSeverity = HarmSeverity.MEDIUM
        )
    }
}
