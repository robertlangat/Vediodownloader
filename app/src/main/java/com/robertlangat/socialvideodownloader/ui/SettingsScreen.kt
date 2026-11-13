package com.robertlangat.socialvideodownloader.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.robertlangat.socialvideodownloader.BuildConfig
import com.robertlangat.socialvideodownloader.R
import com.robertlangat.socialvideodownloader.ads.Ads
import com.robertlangat.socialvideodownloader.data.Platform
import com.robertlangat.socialvideodownloader.data.VideoDownloader
import com.robertlangat.socialvideodownloader.ui.theme.BrandRed
import com.robertlangat.socialvideodownloader.ui.theme.Charcoal
import com.robertlangat.socialvideodownloader.ui.theme.CharcoalRaised
import com.robertlangat.socialvideodownloader.ui.theme.TextMuted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: DownloadViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberStoragePermission()
    var advancedOpen by rememberSaveable { mutableStateOf(false) }
    var serviceUrl by rememberSaveable { mutableStateOf(state.backendUrl) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
                viewModel.setSaveFolder(uri)
                toast(context, "Videos will be saved to ${VideoDownloader.folderLabel(uri)}")
            } catch (e: SecurityException) {
                toast(context, "That folder can't be used. Pick another one.")
            }
        }
    }

    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = Charcoal,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Charcoal,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .navigationBarsPadding()
                .testTag("settings_screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ---- Storage ----
            Section("Storage") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_folder), contentDescription = null, tint = BrandRed)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Save videos on this phone", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (permission.granted) "Allowed" else "Not allowed yet",
                            color = if (permission.granted) Color(0xFF81C784) else Color(0xFFFF8A80),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("storage_status"),
                        )
                    }
                    if (!permission.granted) {
                        Button(
                            onClick = permission.request,
                            colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("allow_storage_button"),
                        ) { Text(if (permission.opensSettings) "Open settings" else "Allow") }
                    }
                }
                Muted("Gives the app access to video storage so it can save and find your downloads.")

                HorizontalDivider(color = Color(0xFF3A3A3E))

                Text("Save folder", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text(state.saveFolderLabel, color = TextMuted, modifier = Modifier.testTag("save_folder_label"))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { folderPicker.launch(null) },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("choose_folder_button"),
                    ) { Text("Choose folder") }
                    if (state.customFolder) {
                        OutlinedButton(
                            onClick = { viewModel.setSaveFolder(null) },
                            modifier = Modifier.testTag("default_folder_button"),
                        ) { Text("Use default", color = Color.White) }
                    }
                }
                Muted("The default folder (Movies/SocialVideoDownloader) shows up in your Gallery right away.")
            }

            // ---- Accounts ----
            Section("Connected accounts") {
                Muted("Connect the accounts whose videos you want to save. Instagram: your Professional account. Facebook: Pages you manage.")
                listOf(Platform.INSTAGRAM, Platform.FACEBOOK).forEach { p ->
                    val connected = p.apiName in state.connected
                    OutlinedButton(
                        onClick = {
                            val start = viewModel.authStartUrl(p)
                            if (start != null) {
                                openUrl(context, start)
                            } else {
                                advancedOpen = true
                                toast(context, "Add the download service address under Advanced first")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("connect_${p.apiName}"),
                    ) {
                        Text(if (connected) "${p.label}: connected ✓ (reconnect)" else "Connect ${p.label}", color = Color.White)
                    }
                }
                if (state.connected.isNotEmpty()) {
                    TextButton(onClick = viewModel::disconnectAll) { Text("Disconnect all accounts", color = BrandRed) }
                }
                Muted("TikTok doesn't offer video downloads to other apps, so there's nothing to connect.")
            }

            // ---- Ads & privacy ----
            Section("Ads & privacy") {
                Muted("Ads keep this app free.")
                if (Ads.privacyOptionsRequired) {
                    OutlinedButton(onClick = { context.findActivity()?.let { Ads.showPrivacyOptions(it) } }) {
                        Text("Privacy options", color = Color.White)
                    }
                }
            }

            // ---- Advanced (download service) ----
            Card(
                colors = CardDefaults.cardColors(containerColor = CharcoalRaised),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { advancedOpen = !advancedOpen }
                            .testTag("advanced_toggle"),
                    ) {
                        Text("Advanced", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Icon(
                            if (advancedOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                    if (advancedOpen) {
                        Muted("Address of the download service that checks Instagram and Facebook links (see the README).")
                        OutlinedTextField(
                            value = serviceUrl,
                            onValueChange = { serviceUrl = it },
                            label = { Text("Download service address") },
                            placeholder = { Text("https://your-service.example.com") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandRed,
                                focusedLabelColor = BrandRed,
                                cursorColor = BrandRed,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("backend_url_field"),
                        )
                        Button(
                            onClick = {
                                viewModel.saveBackendUrl(serviceUrl)
                                toast(context, "Saved")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BrandRed),
                            modifier = Modifier.testTag("settings_save"),
                        ) { Text("Save") }
                    }
                }
            }

            Text(
                "Social Video Downloader ${BuildConfig.VERSION_NAME}",
                color = TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CharcoalRaised),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = BrandRed, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, color = TextMuted, style = MaterialTheme.typography.bodySmall)
}
