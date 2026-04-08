package com.stukachoff.data.update

import com.stukachoff.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateChecker @Inject constructor(
    private val networkUpdateSource: NetworkUpdateSource
) {
    suspend fun check(): UpdateCheckResult = networkUpdateSource.fetchLatestRelease()
}

data class ReleaseInfo(
    val tagName: String,
    val versionCode: Int,
    val apkDownloadUrl: String,
    val checksumsUrl: String,
    val releaseNotes: String
)

sealed class UpdateCheckResult {
    data class UpdateAvailable(val release: ReleaseInfo) : UpdateCheckResult()
    object UpToDate    : UpdateCheckResult()
    object NoNetwork   : UpdateCheckResult()   // Режим приватности включён
    data class Error(val message: String) : UpdateCheckResult()
}

fun parseVersionCode(tag: String): Int = try {
    val parts = tag.trimStart('v', 'V').split(".").map { it.toIntOrNull() ?: 0 }
    parts.getOrElse(0) { 0 } * 10000 +
    parts.getOrElse(1) { 0 } * 100 +
    parts.getOrElse(2) { 0 }
} catch (_: Exception) { 0 }
