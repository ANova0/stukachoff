package com.stukachoff.domain.checker

import android.os.Build
import com.stukachoff.domain.model.CheckResult
import com.stukachoff.domain.model.CheckStatus
import com.stukachoff.domain.model.HarmSeverity

class AndroidVersionChecker {

    fun classify(sdkVersion: Int): CheckStatus =
        if (sdkVersion >= Build.VERSION_CODES.Q) CheckStatus.GREEN else CheckStatus.RED

    fun check(): CheckResult.Fixable = CheckResult.Fixable(
        id = "android_version",
        title = "Версия Android",
        status = classify(Build.VERSION.SDK_INT),
        harm = "Android 9 и ниже: любое приложение читает все активные TCP-соединения " +
                "включая SSH-сессии на VPS. На Android 10+ это заблокировано на уровне ядра.",
        harmSeverity = HarmSeverity.HIGH
    )
}
