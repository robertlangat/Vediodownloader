package com.robertlangat.socialvideodownloader.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robertlangat.socialvideodownloader.R
import com.robertlangat.socialvideodownloader.ads.Ads
import com.robertlangat.socialvideodownloader.ads.BannerAd
import com.robertlangat.socialvideodownloader.data.Platform
import com.robertlangat.socialvideodownloader.data.VideoDownloader
import com.robertlangat.socialvideodownloader.ui.theme.BrandRed
import com.robertlangat.socialvideodownloader.ui.theme.Charcoal
import com.robertlangat.socialvideodownloader.ui.theme.CharcoalRaised
import com.robertlangat.socialvideodownloader.ui.theme.TextMuted
import java.util.Locale

private val FieldShape = RoundedCornerShape(14.dp)
private val ButtonShape = RoundedCornerShape(14.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: DownloadViewModel, onOpenSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val permission = rememberStoragePermission()

    Scaffold(
        containerColor = Charcoal,
        topBar = {
            TopAppBar(
                title = { Text("Social Video Downloader", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("settings_button")) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Charcoal,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        },
        bottomBar = { BannerAd(Modifier.navigationBarsPadding()) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (!permission.granted) {
                PermissionCard(permission)
            }

            // URL field (white, rounded) with the red Paste shortcut
            TextField(
                value = state.input,
                onValueChange = viewModel::onInputChange,
                label = { Text("Paste video link") },
                placeholder = { Text(placeholderFor(state.selectedPlatform), maxLines = 1) },
                singleLine = true,
                isError = state.showInvalidHint,
                shape = FieldShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    errorContainerColor = Color.White,
                    focusedTextColor = Color(0xFF1C1C1E),
                    unfocusedTextColor = Color(0xFF1C1C1E),
                    errorTextColor = Color(0xFF1C1C1E),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    errorIndicatorColor = Color.Transparent,
                    cursorColor = BrandRed,
                    focusedLabelColor = Color(0xFF6B6B70),
                    unfocusedLabelColor = Color(0xFF6B6B70),
                    errorLabelColor = BrandRed,
                    focusedPlaceholderColor = Color(0xFF9A9AA0),
                    unfocusedPlaceholderColor = Color(0xFF9A9AA0),
                    focusedSupportingTextColor = TextMuted,
                    unfocusedSupportingTextColor = TextMuted,
                    errorSupportingTextColor = Color(0xFFFF8A80),
                    focusedTrailingIconColor = BrandRed,
                    unfocusedTrailingIconColor = BrandRed,
                    errorTrailingIconColor = BrandRed,
                ),
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
                    IconButton(
                        onClick = {
                            val text = clipboard.getText()?.text
                            if (text.isNullOrBlank()) toast(context, "Clipboard is empty") else viewModel.onPasted(text)
                        },
                        modifier = Modifier.testTag("paste_button"),
                    ) {
                        Icon(painterResource(R.drawable.ic_paste), contentDescription = "Paste")
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (state.canDownload) viewModel.startDownload() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("url_field"),
            )

            // Big red Download button
            Button(
                onClick = viewModel::startDownload,
                enabled = state.canDownload,
                shape = ButtonShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandRed,
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF4A2A2B),
                    disabledContentColor = Color(0xFFB98A8A),
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .testTag("download_button"),
            ) {
                Icon(painterResource(R.drawable.ic_download), contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("DOWNLOAD", fontSize = 18.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }

            StatusArea(state, viewModel, onOpenSettings)

            // Supported apps
            Text(
                "Supported apps",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                Platform.entries.forEach { p ->
                    PlatformTile(p, selected = state.selectedPlatform == p) { viewModel.selectPlatform(p) }
                }
            }

            Button(
                onClick = { openPlatform(context, state.selectedPlatform) },
                shape = ButtonShape,
                colors = ButtonDefaults.buttonColors(containerColor = BrandRed, contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("open_app_button"),
            ) {
                Text("OPEN ${state.selectedPlatform.label.uppercase(Locale.ROOT)}", fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            }

            Text(
                "Only download videos you own or have permission to save.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PlatformTile(platform: Platform, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .testTag("platform_${platform.name}")
            .semantics { this.selected = selected }
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(6.dp),
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .border(
                        BorderStroke(3.dp, if (selected) BrandRed else Color.Transparent),
                        RoundedCornerShape(22.dp),
                    )
                    .padding(5.dp),
            ) {
                Image(
                    painter = painterResource(platform.tileRes),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp)),
                )
            }
            if (selected) {
                Image(
                    painter = painterResource(R.drawable.ic_check_badge),
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            platform.label,
            color = if (selected) Color.White else TextMuted,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun PermissionCard(permission: StoragePermissionState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CharcoalRaised),
        border = BorderStroke(1.dp, BrandRed.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("permission_card"),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_folder), contentDescription = null, tint = BrandRed)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Allow saving videos", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(
                    "Let the app access video storage on your phone.",
                    color = TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = permission.request,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                modifier = Modifier.testTag("home_allow_storage"),
            ) { Text(if (permission.opensSettings) "SETTINGS" else "ALLOW") }
        }
    }
}

@Composable
private fun StatusArea(state: UiState, viewModel: DownloadViewModel, onOpenSettings: () -> Unit) {
    val phase = state.phase
    if (phase == Phase.Idle) return
    val context = LocalContext.current
    val activity = context.findActivity()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("status_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (phase) {
                is Phase.NetworkError, is Phase.Failed, is Phase.Unavailable -> MaterialTheme.colorScheme.errorContainer
                is Phase.Saved -> MaterialTheme.colorScheme.primaryContainer
                else -> CharcoalRaised
            }
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (phase) {
                Phase.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp, color = BrandRed)
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
                        LinearProgressIndicator(progress = { fraction }, color = BrandRed, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(color = BrandRed, modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedButton(onClick = viewModel::cancel, modifier = Modifier.testTag("cancel_button")) {
                        Text("Cancel", color = Color.White)
                    }
                }

                is Phase.Saved -> {
                    StatusText(
                        if (phase.location == VideoDownloader.DEFAULT_LOCATION) {
                            "Saved to Gallery: ${phase.location}/${phase.fileName}"
                        } else {
                            "Saved to ${phase.location}/${phase.fileName}"
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { Ads.showInterstitialThen(activity) { openVideo(context, phase.uri) } },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                            modifier = Modifier.testTag("open_video_button"),
                        ) { Text("Open video") }
                        OutlinedButton(
                            onClick = { Ads.showInterstitialThen(activity) { shareVideo(context, phase.uri) } },
                            modifier = Modifier.testTag("share_button"),
                        ) { Text("Share", color = Color.White) }
                    }
                }

                is Phase.NetworkError -> {
                    StatusText(phase.message)
                    Button(
                        onClick = viewModel::retry,
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        modifier = Modifier.testTag("retry_button"),
                    ) { Text("Retry") }
                }

                is Phase.Unavailable -> {
                    StatusText(phase.message)
                    OutlinedButton(
                        onClick = { openUrl(context, phase.originalUrl) },
                        modifier = Modifier.testTag("open_original_button"),
                    ) { Text("Open original post", color = Color.White) }
                }

                is Phase.AuthRequired -> {
                    StatusText(phase.message)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = {
                                val url = viewModel.authStartUrl(phase.platform, phase.authUrl)
                                if (url != null) openUrl(context, url) else onOpenSettings()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                            modifier = Modifier.testTag("connect_button"),
                        ) { Text("Connect ${phase.platform.label}") }
                        OutlinedButton(
                            onClick = { openUrl(context, phase.originalUrl) },
                            modifier = Modifier.testTag("open_original_button"),
                        ) { Text("Open post", color = Color.White) }
                    }
                }

                is Phase.Failed -> {
                    StatusText(phase.message)
                    if (phase.openSettings) {
                        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.testTag("open_settings_button")) {
                            Text("Open settings", color = Color.White)
                        }
                    }
                }

                Phase.Cancelled -> StatusText("Download cancelled.")
                Phase.Idle -> Unit
            }
        }
    }
}

@Composable
private fun StatusText(text: String) {
    Text(text, color = Color.White, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("status_text"))
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
