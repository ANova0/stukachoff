package com.stukachoff.data.apps

import android.content.Context
import android.content.pm.PackageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class AppPermissionRisk(
    val packageName: String,
    val appName: String,
    val dangerousPermissions: List<DangerousPermission>
)

enum class DangerousPermission(val label: String, val description: String) {
    READ_PHONE_STATE(
        "SIM / MCC",
        "Видит код оператора SIM — может скоррелировать российскую SIM с зарубежным IP"
    ),
    ACCESS_FINE_LOCATION(
        "Точная геолокация",
        "Через WiFi BSSID может определить физическое местоположение"
    ),
    QUERY_ALL_PACKAGES(
        "Все приложения",
        "Видит полный список установленных приложений включая VPN-клиенты"
    )
}

@Singleton
class PermissionAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Список пакетов которые проверяем на опасные разрешения
    private val watchedPackages = listOf(
        "com.vk.vkclient", "ru.vk.store",
        "ru.sberbankmobile",
        "com.idamob.tinkoff.android", "ru.tinkoff.mvno",
        "com.wildberries.ru",
        "ru.ozon.app.android",
        "ru.vtb24.mobilebanking.android",
        "com.avito.android",
        "com.hh.android",
        "ru.yandex.searchplugin", "com.yandex.browser", "com.yandex.bank",
        "ru.gosuslugi", "ru.gosuslugi.mob",
        "com.mts.mtsbankonline"
    )

    suspend fun analyzeInstalledApps(): List<AppPermissionRisk> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        watchedPackages.mapNotNull { pkg ->
            val info = try {
                pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
            } catch (_: PackageManager.NameNotFoundException) { return@mapNotNull null }

            val requested = info.requestedPermissions?.toSet() ?: emptySet()
            val dangerous = buildList {
                if ("android.permission.READ_PHONE_STATE" in requested)
                    add(DangerousPermission.READ_PHONE_STATE)
                if ("android.permission.ACCESS_FINE_LOCATION" in requested ||
                    "android.permission.ACCESS_COARSE_LOCATION" in requested)
                    add(DangerousPermission.ACCESS_FINE_LOCATION)
                if ("android.permission.QUERY_ALL_PACKAGES" in requested)
                    add(DangerousPermission.QUERY_ALL_PACKAGES)
            }

            if (dangerous.isEmpty()) return@mapNotNull null

            AppPermissionRisk(
                packageName          = pkg,
                appName              = runCatching {
                    pm.getApplicationLabel(info.applicationInfo).toString()
                }.getOrDefault(pkg),
                dangerousPermissions = dangerous
            )
        }
    }
}
