package com.example.update

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * Stateful entry point. Place it once in your UI (e.g. in MainActivity's setContent, inside your
 * theme) and trigger checks via [UpdateViewModel.checkForUpdate]:
 *
 *     val updateViewModel: UpdateViewModel = viewModel()
 *     LaunchedEffect(Unit) { updateViewModel.checkForUpdate(silent = true) }
 *     UpdateDialogHost(updateViewModel)
 *
 * It also forwards ON_RESUME to the ViewModel so installation continues automatically after the
 * user grants "Install unknown apps".
 */
@Composable
fun UpdateDialogHost(viewModel: UpdateViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onAppResumed() }

    UpdateDialog(
        state = state,
        installedVersionName = viewModel.installedVersionName,
        onUpdate = viewModel::startDownload,
        onCancelDownload = viewModel::cancelDownload,
        onInstall = viewModel::install,
        onOpenInstallSettings = viewModel::openInstallPermissionSettings,
        onDismiss = viewModel::dismiss,
    )
}

/** Stateless dialog that renders an [UpdateUiState]. Renders nothing for [UpdateUiState.Idle]. */
@Composable
fun UpdateDialog(
    state: UpdateUiState,
    installedVersionName: String,
    onUpdate: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstall: () -> Unit,
    onOpenInstallSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        UpdateUiState.Idle -> Unit

        UpdateUiState.Checking -> AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text("Checking for updates") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text("Contacting update server…")
                }
            },
            confirmButton = {},
        )

        UpdateUiState.UpToDate -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("You're up to date") },
            text = { Text("Version $installedVersionName is the latest available.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
        )

        is UpdateUiState.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            properties = blockingIf(state.info.mandatory),
            title = { Text(if (state.info.mandatory) "Update required" else "Update available") },
            text = { UpdateDetails(state.info, installedVersionName) },
            confirmButton = { TextButton(onClick = onUpdate) { Text("Download") } },
            dismissButton = if (state.info.mandatory) null else {
                { TextButton(onClick = onDismiss) { Text("Later") } }
            },
        )

        is UpdateUiState.Downloading -> AlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
            title = { Text("Downloading ${state.info.versionName}") },
            text = { DownloadProgress(state) },
            confirmButton = {},
            dismissButton = { TextButton(onClick = onCancelDownload) { Text("Cancel") } },
        )

        is UpdateUiState.ReadyToInstall -> AlertDialog(
            onDismissRequest = onDismiss,
            properties = blockingIf(state.info.mandatory),
            title = { Text("Ready to install") },
            text = {
                Text(
                    "Version ${state.info.versionName} has been downloaded and verified. " +
                        "Android will ask you to confirm the installation."
                )
            },
            confirmButton = { TextButton(onClick = onInstall) { Text("Install") } },
            dismissButton = if (state.info.mandatory) null else {
                { TextButton(onClick = onDismiss) { Text("Later") } }
            },
        )

        is UpdateUiState.NeedsInstallPermission -> AlertDialog(
            onDismissRequest = onDismiss,
            properties = blockingIf(state.info.mandatory),
            title = { Text("Permission needed") },
            text = {
                Text(
                    "To install updates, allow this app to install unknown apps in Settings, " +
                        "then come back. The installation will continue automatically."
                )
            },
            confirmButton = { TextButton(onClick = onOpenInstallSettings) { Text("Open settings") } },
            dismissButton = if (state.info.mandatory) null else {
                { TextButton(onClick = onDismiss) { Text("Cancel") } }
            },
        )

        is UpdateUiState.Error -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Update problem") },
            text = { Text(state.message) },
            confirmButton = {
                if (state.info != null) {
                    TextButton(onClick = onUpdate) { Text("Retry") }
                } else {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            },
            dismissButton = if (state.info != null) {
                { TextButton(onClick = onDismiss) { Text("Close") } }
            } else null,
        )
    }
}

@Composable
private fun UpdateDetails(info: UpdateInfo, installedVersionName: String) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "$installedVersionName  →  ${info.versionName}",
            style = MaterialTheme.typography.titleSmall,
        )
        if (info.fileSizeBytes > 0) {
            Text(
                text = "Download size: ${Formatter.formatShortFileSize(context, info.fileSizeBytes)}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (info.mandatory) {
            Text(
                text = "This update is required to keep using the app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (info.releaseNotes.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(text = info.releaseNotes, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun DownloadProgress(state: UpdateUiState.Downloading) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.fraction < 0f) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                text = Formatter.formatShortFileSize(context, state.bytesRead),
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            LinearProgressIndicator(progress = { state.fraction }, modifier = Modifier.fillMaxWidth())
            Text(
                text = "${Formatter.formatShortFileSize(context, state.bytesRead)} / " +
                    "${Formatter.formatShortFileSize(context, state.totalBytes)}  ·  " +
                    "${(state.fraction * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Mandatory updates can't be dismissed with back or an outside tap. */
private fun blockingIf(mandatory: Boolean) = DialogProperties(
    dismissOnBackPress = !mandatory,
    dismissOnClickOutside = !mandatory,
)
