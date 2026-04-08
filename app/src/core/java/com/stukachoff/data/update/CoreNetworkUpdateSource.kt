package com.stukachoff.data.update

import javax.inject.Inject

/**
 * Core flavor: нет INTERNET permission — не можем делать запросы.
 * UI покажет предложение открыть GitHub в браузере.
 */
class CoreNetworkUpdateSource @Inject constructor() : NetworkUpdateSource {
    override suspend fun fetchLatestRelease(): UpdateCheckResult = UpdateCheckResult.NoInternet
}
