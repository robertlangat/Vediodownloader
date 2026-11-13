package com.robertlangat.socialvideodownloader.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robertlangat.socialvideodownloader.R
import com.robertlangat.socialvideodownloader.data.Platform
import com.robertlangat.socialvideodownloader.data.VideoDownloader
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: DownloadViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var showSettings by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Social Video Downloader") },
                actions = {
                    IconButton(onClick = { showSettings = true }, modifier = Modifier.testTag("settings_button")) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 1. Platform buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Platform.entries.forEach { p ->
                    val isSelected = state.selectedPlatform == p
                    val mod = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("platform_${p.name}")
                        .semantics { selected = isSelected }
                    val pad = PaddingValues(horizontal = 4.dp)
                    if (isSelected) {
                        Button(onClick = { viewModel.selectPlatform(p) }, modifier = mod, contentPadding = pad) {
                            Text(p.label, maxLines = 1, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        OutlinedButton(onClick = { viewModel.selectPlatform(p) }, modifier = mod, contentPadding = pad) {
                            Text(p.label, maxLines = 1)
                        }
                    }
                }
            }

            // 2. URL field with Paste shortcut
            OutlinedTextField(
                value = state.input,
                onValueChange = viewModel::onInputChange,
                label = { Text("Paste video link") },
                placeholder = { Text(placeholderFor(state.selectedPlatform)) },
                singleLine = true,
                isError = state.showInvalidHint,
                supportingText = {
                    when {
                        state.showInvalidHint -> Text(
                            "Not a supported link. Paste a TikTok, Instagram or Facebook video link.",
                            modifier = Modifier.testTag("invalid_hint"),
                        )

                        state.detected != null -> Text("${state.detected!!.platform.label} link detected")
                    }
                },
                trailingIcon = {
                    TextButton(
                        onClick = {
                            val text = clipboard.getText()?.text
                            if (text.isNullOrBlank()) {
                                toast(context, "Clipboard is empty")
                            } else {
                                viewModel.onPasted(text)
                            }
                        },
                        modifier = Modifier.testTag("paste_button"),
                    ) {
                        Icon(painterResource(R.drawable.ic_paste), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Paste")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (state.canDownload) viewModel.startDownload() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("url_field"),
            )

            // 3. Large Download button
            Button(
                onClick = viewModel::startDownload,
                enabled = state.canDownload,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .testTag("download_button"),
            ) {
                Icon(painterResource(R.drawable.ic_download), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Download", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }

            StatusArea(state, viewModel, context)

            if (state.backendUrl.isBlank()) {
                Text(
                    "Setup needed: no download service is configured. Tap the gear icon to add your backend URL.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                "Only download videos you own or have permission to save.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showSettings) {
        SettingsDialog(state, viewModel, onDismiss = { showSettings = false })
    }
}

@Composable
private fun StatusArea(state: UiState, viewModel: DownloadViewModel, context: Context) {
    val phase = state.phase
    if (phase == Phase.Idle) return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("status_card"),
        colors = CardDefaults.cardColors(
            containerColor = when (phase) {
                is Phase.NetworkError, is Phase.Failed, is Phase.Unavailable -> MaterialTheme.colorScheme.errorContainer
                is Phase.Saved -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (phase) {
                Phase.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.width(12.dp))
                    StatusText("Checking link…")
                }

                is Phase.Downloading -> {
                    val fraction = if (phase.total > 0) phase.downloaded.toFloat() / phase.total else null
                    StatusText(
                        if (fraction != null) {
                            "Downloading… ${(fraction * 100).toInt()}%  (${formatBytes(phase.downloaded)} of ${formatBytes(phase.total)})"
                        } else {
                            "Downloading… ${formatBytes(phase.downloaded)}"
                        }
                    )
                    if (fraction != null) {
                        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedButton(onClick = viewModel::cancel, modifier = Modifier.testTag("cancel_button")) {
                        Text("Cancel")
                    }
                }

                is Phase.Saved -> {
                    StatusText("Saved to Gallery: Movies/${VideoDownloader.ALBUM}/${phase.fileName}")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { openVideo(context, phase.uri) }, modifier = Modifier.testTag("open_video_button")) {
                            Text("Open video")
                        }
                        OutlinedButton(onClick = { shareVideo(context, phase.uri) }, modifier = Modifier.testTag("share_button")) {
                            Text("Share")
                        }
                    }
                }

                is Phase.NetworkError -> {
                    StatusText(phase.message)
                    Button(onClick = viewModel::retry, modifier = Modifier.testTag("retry_button")) { Text("Retry") }
                }

                is Phase.Unavailable -> {
                    StatusText(phase.message)
                    OutlinedButton(
                        onClick = { openUrl(context, phase.originalUrl) },
                        modifier = Modifier.testTag("open_original_button"),
                    ) { Text("Open original post") }
                }

                is Phase.AuthRequired -> {
                    StatusText(phase.message)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                val url = viewModel.authStartUrl(phase.platform, phase.authUrl)
                                if (url != null) openUrl(context, url) else toast(context, "Set the backend URL first")
                            },
                            modifier = Modifier.testTag("connect_button"),
                        ) { Text("Connect ${phase.platform.label}") }
                        OutlinedButton(
                            onClick = { openUrl(context, phase.originalUrl) },
                            modifier = Modifier.testTag("open_original_button"),
                        ) { Text("Open original post") }
                    }
                }

                is Phase.Failed -> StatusText(phase.message)
                Phase.Cancelled -> StatusText("Download cancelled.")
                Phase.Idle -> Unit
            }
        }
    }
}

@Composable
private fun StatusText(text: String) {
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("status_text"))
}

@Composable
private fun SettingsDialog(state: UiState, viewModel: DownloadViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var url by remember { mutableStateOf(state.backendUrl) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Backend URL") },
                    placeholder = { Text("https://your-backend.example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backend_url_field"),
                )
                HorizontalDivider()
                Text("Connected accounts", fontWeight = FontWeight.SemiBold)
                Text(
                    "Instagram and Facebook downloads work for videos in accounts or Pages you connect.",
                    style = MaterialTheme.typography.bodySmall,
                )
                listOf(Platform.INSTAGRAM, Platform.FACEBOOK).forEach { p ->
                    val connected = p.apiName in state.connected
                    OutlinedButton(
                        onClick = {
                            viewModel.saveBackendUrl(url)
                            val start = viewModel.authStartUrl(p)
                            if (start != null) openUrl(context, start) else toast(context, "Enter the backend URL first")
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (connected) "${p.label}: connected (reconnect)" else "Connect ${p.label}") }
                }
                if (state.connected.isNotEmpty()) {
                    TextButton(onClick = viewModel::disconnectAll) { Text("Disconnect all accounts") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                viewModel.saveBackendUrl(url)
                onDismiss()
            }, modifier = Modifier.testTag("settings_save")) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun placeholderFor(p: Platform) = when (p) {
    Platform.TIKTOK -> "https://www.tiktok.com/@user/video/…"
    Platform.INSTAGRAM -> "https://www.instagram.com/reel/…"
    Platform.FACEBOOK -> "https://www.facebook.com/watch?v=…"
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
    return String.format(Locale.US, "%.1f MB", kb / 1024.0)
}

private fun toast(context: Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        toast(context, "No app can open this link")
    }
}

private fun openVideo(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "video/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        toast(context, "No video player found")
    }
}

private fun shareVideo(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("video/mp4")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Share video").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
