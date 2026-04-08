package com.stukachoff.data.update

import com.stukachoff.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Проверяет наличие новой версии на GitHub Releases.
 * Реальный HTTP-запрос только в full flavor (есть INTERNET).
 * В core flavor — возвращает NoInternet чтобы UI предложил открыть браузер.
 */
@Singleton
class UpdateChecker @Inject constructor(
    private val networkUpdateSource: NetworkUpdateSource
) {
    suspend fun check(): UpdateCheckResult {
        return networkUpdateSource.fetchLatestRelease()
    }
}

data class ReleaseInfo(
    val tagName: String,           // "v1.1.0"
    val versionCode: Int,
    val apkDownloadUrl: String,
    val checksumsUrl: String,
    val releaseNotes: String
)

sealed class UpdateCheckResult {
    data class UpdateAvailable(val release: ReleaseInfo) : UpdateCheckResult()
    object UpToDate : UpdateCheckResult()
    object NoInternet : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

fun parseVersionCode(tag: String): Int {
    // "v1.2.3" → 10203, "v2.0.0" → 20000
    return try {
        val clean = tag.trimStart('v', 'V')
        val parts = clean.split(".").map { it.toIntOrNull() ?: 0 }
        val major = parts.getOrElse(0) { 0 }
        val minor = parts.getOrElse(1) { 0 }
        val patch = parts.getOrElse(2) { 0 }
        major * 10000 + minor * 100 + patch
    } catch (_: Exception) { 0 }
}
