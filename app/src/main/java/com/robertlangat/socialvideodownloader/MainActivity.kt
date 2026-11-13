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
import com.robertlangat.socialvideodownloader.data.Platform
import com.robertlangat.socialvideodownloader.ui.HomeScreen
import com.robertlangat.socialvideodownloader.ui.LoginScreen
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
                // "home", "settings", or "login:<PLATFORM>"
                var screen by rememberSaveable { mutableStateOf("home") }
                var returnTo by rememberSaveable { mutableStateOf("home") }
                val openLogin: (Platform) -> Unit = { p ->
                    returnTo = screen
                    screen = "login:${p.name}"
                }
                when {
                    screen.startsWith("login:") -> LoginScreen(Platform.valueOf(screen.removePrefix("login:"))) {
                        screen = returnTo
                    }

                    screen == "settings" -> SettingsScreen(viewModel, onBack = { screen = "home" }, onSignIn = openLogin)
                    else -> HomeScreen(viewModel, onOpenSettings = { screen = "settings" }, onSignIn = openLogin)
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
        if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { viewModel.onPasted(it) }
        }
    }
}
