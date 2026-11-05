package com.example.update

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/** What the update UI should currently show. */
sealed interface UpdateUiState {
    /** Nothing to show. */
    data object Idle : UpdateUiState

    data object Checking : UpdateUiState

    data object UpToDate : UpdateUiState

    /** A newer release exists. `info.mandatory` is the effective flag. */
    data class Available(val info: UpdateInfo) : UpdateUiState

    data class Downloading(
        val info: UpdateInfo,
        val bytesRead: Long,
        val totalBytes: Long,
    ) : UpdateUiState {
        /** 0f..1f, or -1f when the total size is unknown. */
        val fraction: Float
            get() = if (totalBytes > 0) (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f) else -1f
    }

    /** APK downloaded and verified; waiting for [UpdateViewModel.install]. */
    data class ReadyToInstall(val info: UpdateInfo, val file: File) : UpdateUiState

    /** "Install unknown apps" must be enabled; see [UpdateViewModel.openInstallPermissionSettings]. */
    data class NeedsInstallPermission(val info: UpdateInfo, val file: File) : UpdateUiState

    /** [info] is set when the failed step can be retried via [UpdateViewModel.startDownload]. */
    data class Error(val message: String, val info: UpdateInfo? = null) : UpdateUiState
}

class UpdateViewModel(
    application: Application,
    private val repository: UpdateRepository,
    private val downloader: ApkDownloader,
    private val installer: ApkInstaller,
) : AndroidViewModel(application) {

    /** Used by the default ViewModel factory (`viewModel<UpdateViewModel>()`). */
    constructor(application: Application) : this(
        application,
        UpdateRepository(application),
        ApkDownloader(application),
        ApkInstaller(application),
    )

    val installedVersionCode: Long = BuildConfig.VERSION_CODE.toLong()
    val installedVersionName: String = BuildConfig.VERSION_NAME

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    init {
        // Remove partial downloads and APKs of versions that are already installed.
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { downloader.deleteObsolete(installedVersionCode) }
        }
    }

    /**
     * Looks for a newer release.
     * @param silent for automatic checks at app start: failures and "up to date" leave the state
     *   at Idle instead of showing errors or progress.
     */
    fun checkForUpdate(silent: Boolean = false) {
        if (checkJob?.isActive == true || downloadJob?.isActive == true) return
        val current = _state.value
        if (current is UpdateUiState.ReadyToInstall || current is UpdateUiState.NeedsInstallPermission) return

        checkJob = viewModelScope.launch {
            try {
                if (!silent) _state.value = UpdateUiState.Checking

                repository.checkForUpdate(installedVersionCode).fold(
                    onSuccess = { result ->
                        when (result) {
                            UpdateCheckResult.UpToDate ->
                                _state.value = if (silent) UpdateUiState.Idle else UpdateUiState.UpToDate

                            is UpdateCheckResult.Available -> {
                                val info = result.info.copy(
                                    mandatory = result.info.isMandatoryFor(installedVersionCode)
                                )
                                // Reuse an intact earlier download instead of fetching again.
                                val cached = withContext(Dispatchers.IO) { downloader.findValidCached(info) }
                                _state.value =
                                    if (cached != null) UpdateUiState.ReadyToInstall(info, cached)
                                    else UpdateUiState.Available(info)
                            }
                        }
                    },
                    onFailure = { e ->
                        Log.w(TAG, "Update check failed", e)
                        _state.value = if (silent) UpdateUiState.Idle else UpdateUiState.Error(e.toUserMessage())
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Last line of defence: nothing in an update check may crash the app.
                Log.w(TAG, "Update check crashed", e)
                _state.value = if (silent) UpdateUiState.Idle else UpdateUiState.Error(e.toUserMessage())
            }
        }
    }

    /** Downloads the offered update. Also used to retry after a failed download. */
    fun startDownload() {
        if (downloadJob?.isActive == true) return
        val info = when (val s = _state.value) {
            is UpdateUiState.Available -> s.info
            is UpdateUiState.Error -> s.info ?: return
            else -> return
        }

        downloadJob = viewModelScope.launch {
            _state.value = UpdateUiState.Downloading(info, 0L, info.fileSizeBytes)
            try {
                val url = repository.resolveDownloadUrl(info).getOrThrow()
                downloader.download(info, url).collect { event ->
                    when (event) {
                        is DownloadState.Progress ->
                            _state.value = UpdateUiState.Downloading(info, event.bytesRead, event.totalBytes)

                        is DownloadState.Completed -> onDownloaded(info, event.file)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Update download failed", e)
                _state.value = UpdateUiState.Error(e.toUserMessage(), info)
            }
        }
    }

    /** Aborts a running download and returns to the "update available" state. */
    fun cancelDownload() {
        val s = _state.value as? UpdateUiState.Downloading ?: return
        downloadJob?.cancel()
        _state.value = UpdateUiState.Available(s.info)
    }

    /** Opens the system installer for the downloaded APK. */
    fun install() {
        val (info, file) = when (val s = _state.value) {
            is UpdateUiState.ReadyToInstall -> s.info to s.file
            is UpdateUiState.NeedsInstallPermission -> s.info to s.file
            else -> return
        }

        when (val result = installer.install(file)) {
            // Stay in ReadyToInstall: the user may cancel the system dialog and tap Install again.
            InstallResult.Launched -> _state.value = UpdateUiState.ReadyToInstall(info, file)
            InstallResult.NeedsPermission -> _state.value = UpdateUiState.NeedsInstallPermission(info, file)
            is InstallResult.Failed -> {
                Log.w(TAG, "Install launch failed: ${result.message}")
                _state.value = UpdateUiState.Error(result.message)
            }
        }
    }

    /** Opens "Install unknown apps" settings for this app. */
    fun openInstallPermissionSettings() {
        if (!installer.openUnknownSourcesSettings()) {
            _state.value = UpdateUiState.Error("Could not open the install permission settings")
        }
    }

    /**
     * Call when the app returns to the foreground (e.g. ON_RESUME). If the user just granted
     * "Install unknown apps", installation continues automatically.
     */
    fun onAppResumed() {
        if (_state.value is UpdateUiState.NeedsInstallPermission && installer.canInstallPackages()) {
            install()
        }
    }

    /** Hides the update UI. Ignored while downloading and for mandatory updates. */
    fun dismiss() {
        when (val s = _state.value) {
            is UpdateUiState.Downloading -> return
            is UpdateUiState.Available -> if (s.info.mandatory) return
            is UpdateUiState.ReadyToInstall -> if (s.info.mandatory) return
            is UpdateUiState.NeedsInstallPermission -> if (s.info.mandatory) return
            else -> Unit
        }
        _state.value = UpdateUiState.Idle
    }

    private suspend fun onDownloaded(info: UpdateInfo, file: File) {
        val problem = withContext(Dispatchers.IO) {
            installer.validate(file, info).also { if (it != null) file.delete() }
        }
        _state.value =
            if (problem == null) UpdateUiState.ReadyToInstall(info, file)
            else UpdateUiState.Error(problem, info)
    }

    private fun Throwable.toUserMessage(): String = when (this) {
        is UpdateException -> message ?: "Update failed"
        is FirebaseFirestoreException -> when (code) {
            FirebaseFirestoreException.Code.UNAVAILABLE -> "Can't reach the update server. Check your connection."
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> "Update server denied access (check Firestore rules)."
            else -> "Update server error (${code.name})"
        }
        is StorageException -> when (errorCode) {
            StorageException.ERROR_OBJECT_NOT_FOUND -> "The update file was not found on the server."
            StorageException.ERROR_NOT_AUTHENTICATED,
            StorageException.ERROR_NOT_AUTHORIZED -> "Update file access denied (check Storage rules)."
            StorageException.ERROR_RETRY_LIMIT_EXCEEDED -> "Network problem. Please try again."
            else -> "Update storage error ($errorCode)"
        }
        is IOException -> "Network error. Check your connection and try again."
        else -> message ?: "Unexpected update error"
    }

    private companion object {
        const val TAG = "UpdateViewModel"
    }
}
