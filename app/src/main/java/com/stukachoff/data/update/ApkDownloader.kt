package com.stukachoff.data.update

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Int) : DownloadState()
    data class Verifying(val message: String = "Проверяю подпись...") : DownloadState()
    data class Ready(val apkFile: File) : DownloadState()
    data class Failed(val reason: String) : DownloadState()
}

@Singleton
class ApkDownloader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val cacheDir = File(context.cacheDir, "updates").also { it.mkdirs() }

    fun download(release: ReleaseInfo): Flow<DownloadState> = flow {
        emit(DownloadState.Downloading(0))

        val apkFile = File(cacheDir, "stukachoff-${release.tagName}.apk")

        // Скачиваем APK
        runCatching {
            downloadFile(release.apkDownloadUrl, apkFile) { progress ->
                emit(DownloadState.Downloading(progress))
            }
        }.onFailure {
            emit(DownloadState.Failed("Ошибка загрузки: ${it.message}"))
            return@flow
        }

        // Верифицируем SHA-256 если есть checksums.txt
        emit(DownloadState.Verifying())

        if (release.checksumsUrl.isNotBlank()) {
            val verified = verifyChecksum(apkFile, release.checksumsUrl, release.tagName)
            if (!verified) {
                apkFile.delete()
                emit(DownloadState.Failed(
                    "⚠️ SHA-256 не совпадает! Файл повреждён или подменён. Установка отменена."
                ))
                return@flow
            }
        }

        emit(DownloadState.Ready(apkFile))
    }.flowOn(Dispatchers.IO)

    private suspend fun downloadFile(
        url: String,
        dest: File,
        onProgress: suspend (Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        connection.connect()
        val totalBytes = connection.contentLengthLong
        var downloadedBytes = 0L

        connection.inputStream.use { input ->
            dest.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var bytes: Int
                while (input.read(buffer).also { bytes = it } != -1) {
                    output.write(buffer, 0, bytes)
                    downloadedBytes += bytes
                    if (totalBytes > 0) {
                        onProgress((downloadedBytes * 100 / totalBytes).toInt())
                    }
                }
            }
        }
    }

    private suspend fun verifyChecksum(
        apkFile: File,
        checksumsUrl: String,
        tagName: String
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            // Скачиваем checksums.txt
            val checksumsText = java.net.URL(checksumsUrl).readText()

            // Находим строку для нашего APK
            val apkName = apkFile.name
            val expectedHash = checksumsText.lines()
                .firstOrNull { it.contains(apkName) }
                ?.split("\\s+".toRegex())
                ?.firstOrNull()
                ?: return@runCatching false

            // Считаем SHA-256 скачанного файла
            val digest = MessageDigest.getInstance("SHA-256")
            apkFile.inputStream().use { stream ->
                val buffer = ByteArray(8192)
                var bytes: Int
                while (stream.read(buffer).also { bytes = it } != -1) {
                    digest.update(buffer, 0, bytes)
                }
            }
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }

            expectedHash.equals(actualHash, ignoreCase = true)
        }.getOrDefault(true) // Если не смогли скачать checksums — пропускаем проверку
    }
}
