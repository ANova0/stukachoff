package com.stukachoff.data.update

/**
 * Интерфейс источника данных об обновлениях.
 * Core flavor: возвращает NoInternet (нет INTERNET permission).
 * Full flavor: делает реальный HTTPS-запрос к GitHub API.
 */
interface NetworkUpdateSource {
    suspend fun fetchLatestRelease(): UpdateCheckResult
}
