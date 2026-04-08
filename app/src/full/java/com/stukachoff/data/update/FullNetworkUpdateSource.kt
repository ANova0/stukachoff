package com.stukachoff.data.update

import com.stukachoff.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class FullNetworkUpdateSource @Inject constructor() : NetworkUpdateSource {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun fetchLatestRelease(): UpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://api.github.com/repos/ANova0/stukachoff/releases/latest")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@runCatching UpdateCheckResult.Error(
                "GitHub API: ${response.code}"
            )

            val body = response.body?.string() ?: return@runCatching UpdateCheckResult.Error("Empty response")
            val json = JSONObject(body)
            val tagName = json.getString("tag_name")
            val latestVersionCode = parseVersionCode(tagName)

            if (latestVersionCode <= BuildConfig.VERSION_CODE) {
                return@runCatching UpdateCheckResult.UpToDate
            }

            // Находим нужный APK asset (core или full в зависимости от flavor)
            val assets = json.getJSONArray("assets")
            val flavorSuffix = if (BuildConfig.FLAVOR == "core") "core" else "full"
            var apkUrl = ""
            var checksumsUrl = ""

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                when {
                    name.contains(flavorSuffix) && name.endsWith(".apk") ->
                        apkUrl = asset.getString("browser_download_url")
                    name == "checksums.txt" ->
                        checksumsUrl = asset.getString("browser_download_url")
                }
            }

            if (apkUrl.isEmpty()) return@runCatching UpdateCheckResult.Error("APK не найден в релизе")

            val releaseNotes = json.optString("body", "").take(500)

            UpdateCheckResult.UpdateAvailable(
                ReleaseInfo(
                    tagName        = tagName,
                    versionCode    = latestVersionCode,
                    apkDownloadUrl = apkUrl,
                    checksumsUrl   = checksumsUrl,
                    releaseNotes   = releaseNotes
                )
            )
        }.getOrElse {
            UpdateCheckResult.Error(it.message ?: "Неизвестная ошибка")
        }
    }
}
