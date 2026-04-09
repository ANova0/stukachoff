package com.stukachoff.domain.checker

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.stukachoff.domain.model.CheckResult
import com.stukachoff.domain.model.CheckStatus
import com.stukachoff.domain.model.HarmSeverity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkProfileChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun check(): WorkProfileResult = withContext(Dispatchers.IO) {
        val pm = context.packageManager

        val hasKnox = isInstalled(pm, "com.samsung.knox.securefolder") ||
                isInstalled(pm, "com.sec.android.app.SecondaryLockScreen")
        val hasShelter = isInstalled(pm, "net.typeblog.shelter")
        val hasIsland = isInstalled(pm, "com.oasisfeng.island") ||
                isInstalled(pm, "com.oasisfeng.island.fdroid")

        val hasAnyIsolation = hasKnox || hasShelter || hasIsland

        WorkProfileResult(
            hasKnox = hasKnox,
            hasShelter = hasShelter,
            hasIsland = hasIsland,
            check = CheckResult.Fixable(
                id           = "work_profile",
                title        = "Изоляция профилей",
                status       = if (hasAnyIsolation) CheckStatus.GREEN else CheckStatus.YELLOW,
                harm         = if (hasAnyIsolation)
                    buildString {
                        if (hasKnox) append("Samsung Knox ")
                        if (hasShelter) append("Shelter ")
                        if (hasIsland) append("Island ")
                        append("— VPN-клиент в изолированном профиле сложнее детектировать")
                    }
                else
                    "Изоляция не обнаружена — враждебные приложения в одном профиле с VPN-клиентом",
                harmSeverity = HarmSeverity.INFO
            )
        )
    }

    private fun isInstalled(pm: PackageManager, pkg: String): Boolean = try {
        pm.getPackageInfo(pkg, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) { false }
}

data class WorkProfileResult(
    val hasKnox: Boolean,
    val hasShelter: Boolean,
    val hasIsland: Boolean,
    val check: CheckResult.Fixable
)
