package com.stukachoff.ui.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stukachoff.data.update.*
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed class UpdateUiState {
    object Idle : UpdateUiState()
    object Checking : UpdateUiState()
    object UpToDate : UpdateUiState()
    object NoNetwork : UpdateUiState()                      // режим приватности включён
    data class UpdateAvailable(val release: ReleaseInfo) : UpdateUiState()
    data class Downloading(val progress: Int) : UpdateUiState()
    data class Verifying(val message: String) : UpdateUiState()
    data class ReadyToInstall(val apkFile: File) : UpdateUiState()
    data class Error(val message: String) : UpdateUiState()
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updateChecker: UpdateChecker,
    private val apkDownloader: ApkDownloader
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state = _state.asStateFlow()

    fun checkForUpdates() {
        viewModelScope.launch {
            _state.value = UpdateUiState.Checking
            _state.value = when (val result = updateChecker.check()) {
                is UpdateCheckResult.UpdateAvailable -> UpdateUiState.UpdateAvailable(result.release)
                is UpdateCheckResult.UpToDate        -> UpdateUiState.UpToDate
                is UpdateCheckResult.NoNetwork       -> UpdateUiState.NoNetwork
                is UpdateCheckResult.Error           -> UpdateUiState.Error(result.message)
            }
        }
    }

    fun downloadAndInstall(release: ReleaseInfo) {
        viewModelScope.launch {
            apkDownloader.download(release).collect { downloadState ->
                _state.value = when (downloadState) {
                    is DownloadState.Downloading -> UpdateUiState.Downloading(downloadState.progress)
                    is DownloadState.Verifying   -> UpdateUiState.Verifying(downloadState.message)
                    is DownloadState.Ready       -> UpdateUiState.ReadyToInstall(downloadState.apkFile)
                    is DownloadState.Failed      -> UpdateUiState.Error(downloadState.reason)
                    is DownloadState.Idle        -> UpdateUiState.Idle
                }
            }
        }
    }

    fun installApk(apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openGitHubReleases() {
        val intent = Intent(Intent.ACTION_VIEW,
            Uri.parse("https://github.com/ANova0/stukachoff/releases/latest")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }
}
