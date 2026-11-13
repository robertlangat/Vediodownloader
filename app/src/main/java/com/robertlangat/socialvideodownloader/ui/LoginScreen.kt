package com.robertlangat.socialvideodownloader.ui

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import com.robertlangat.socialvideodownloader.data.Platform
import com.robertlangat.socialvideodownloader.data.SignIn
import com.robertlangat.socialvideodownloader.data.UserAgents
import com.robertlangat.socialvideodownloader.ui.theme.BrandRed
import com.robertlangat.socialvideodownloader.ui.theme.Charcoal

/** Shows the platform's own sign-in page. The login stays in this phone's WebView cookies. */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(platform: Platform, onDone: () -> Unit) {
    BackHandler(onBack = onDone)
    DisposableEffect(Unit) { onDispose { CookieManager.getInstance().flush() } }
    Scaffold(
        containerColor = Charcoal,
        topBar = {
            TopAppBar(
                title = { Text("Sign in to ${platform.label}", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    TextButton(onClick = onDone, modifier = Modifier.testTag("login_done")) {
                        Text("DONE", color = BrandRed, fontWeight = FontWeight.Bold)
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
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = UserAgents.forPlatform(ctx, platform)
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val scheme = request.url.scheme ?: return true
                            return scheme != "http" && scheme != "https"
                        }
                    }
                    loadUrl(SignIn.loginUrl(platform))
                }
            },
            onRelease = { it.destroy() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("login_webview"),
        )
    }
}
