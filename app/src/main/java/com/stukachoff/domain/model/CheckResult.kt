package com.stukachoff.domain.model

sealed class CheckResult {
    abstract val id: String
    abstract val title: String

    data class AlwaysVisible(
        override val id: String,
        override val title: String,
        val explanation: String,
        val knowsWhat: String,
        val doesntKnow: String
    ) : CheckResult()

    data class Fixable(
        override val id: String,
        override val title: String,
        val status: CheckStatus,
        val harm: String,
        val harmSeverity: HarmSeverity,
        val affectedClients: List<String> = emptyList()
    ) : CheckResult() {
        val requiresFix: Boolean get() = status == CheckStatus.RED || status == CheckStatus.YELLOW
    }
}

enum class CheckStatus { GREEN, YELLOW, RED }

enum class HarmSeverity { INFO, MEDIUM, HIGH, CRITICAL }
