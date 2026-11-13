package com.robertlangat.socialvideodownloader

import android.app.Application
import android.content.Context
import com.robertlangat.socialvideodownloader.data.WebVideoExtractor
import com.robertlangat.socialvideodownloader.data.Settings
import com.robertlangat.socialvideodownloader.data.VideoDownloader
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class SvdApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

class AppContainer(context: Context) {
    val settings = Settings(context)

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    val extractor = WebVideoExtractor(context.applicationContext)
    val downloader = VideoDownloader(context.applicationContext, http)
}
