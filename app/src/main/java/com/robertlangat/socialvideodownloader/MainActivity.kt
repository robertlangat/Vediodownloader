package com.robertlangat.socialvideodownloader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.robertlangat.socialvideodownloader.ui.DownloadViewModel
import com.robertlangat.socialvideodownloader.ui.HomeScreen
import com.robertlangat.socialvideodownloader.ui.theme.SocialVideoDownloaderTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DownloadViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            SocialVideoDownloaderTheme {
                HomeScreen(viewModel)
            }
        }
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
