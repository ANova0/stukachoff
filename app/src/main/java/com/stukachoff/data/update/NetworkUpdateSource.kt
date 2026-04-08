package com.stukachoff.data.update

import com.stukachoff.BuildConfig
import com.stukachoff.data.prefs.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface NetworkUpdateSource {
    suspend fun fetchLatestRelease(): UpdateCheckResult
}

@Singleton
class NetworkUpdateSourceImpl @Inject constructor(
    private val prefs: AppPreferences
) : NetworkUpdateSource {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    override suspend fun fetchLatestRelease(): UpdateCheckResult {
        // Уважаем режим приватности
        if (prefs.privacyModeEnabled) return UpdateCheckResult.NoNetwork

        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("https://api.github.com/repos/ANova0/stukachoff/releases/latest")
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@runCatching UpdateCheckResult.Error(
                    "GitHub API: ${response.code}"
                )

                val body = response.body?.string()
                    ?: return@runCatching UpdateCheckResult.Error("Пустой ответ")
                val json = JSONObject(body)
                val tagName = json.getString("tag_name")
                val latestVersionCode = parseVersionCode(tagName)

                if (latestVersionCode <= BuildConfig.VERSION_CODE) {
                    return@runCatching UpdateCheckResult.UpToDate
                }

                val assets = json.getJSONArray("assets")
                var apkUrl = ""
                var checksumsUrl = ""

                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")
                    when {
                        name.endsWith(".apk") ->
                            apkUrl = asset.getString("browser_download_url")
                        name == "checksums.txt" ->
                            checksumsUrl = asset.getString("browser_download_url")
                    }
                }

                if (apkUrl.isEmpty()) return@runCatching UpdateCheckResult.Error(
                    "APK не найден в релизе"
                )

                UpdateCheckResult.UpdateAvailable(
                    ReleaseInfo(
                        tagName        = tagName,
                        versionCode    = latestVersionCode,
                        apkDownloadUrl = apkUrl,
                        checksumsUrl   = checksumsUrl,
                        releaseNotes   = json.optString("body", "").take(500)
                    )
                )
            }.getOrElse { UpdateCheckResult.Error(it.message ?: "Неизвестная ошибка") }
        }
    }
}
