package com.robertlangat.socialvideodownloader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.robertlangat.socialvideodownloader.ads.Ads
import com.robertlangat.socialvideodownloader.ui.DownloadViewModel
import com.robertlangat.socialvideodownloader.ui.HomeScreen
import com.robertlangat.socialvideodownloader.ui.SettingsScreen
import com.robertlangat.socialvideodownloader.ui.theme.SocialVideoDownloaderTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DownloadViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            SocialVideoDownloaderTheme {
                var showSettings by rememberSaveable { mutableStateOf(false) }
                if (showSettings) {
                    SettingsScreen(viewModel, onBack = { showSettings = false })
                } else {
                    HomeScreen(viewModel, onOpenSettings = { showSettings = true })
                }
            }
        }
        Ads.gatherConsentAndInit(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { viewModel.onPasted(it) }
            Intent.ACTION_VIEW -> {
                val data = intent.data ?: return
                if (data.scheme == AUTH_SCHEME && data.host == "auth") viewModel.onAuthCallback(data)
            }
        }
    }

    companion object {
        const val AUTH_SCHEME = "socialvideodl"
    }
}
