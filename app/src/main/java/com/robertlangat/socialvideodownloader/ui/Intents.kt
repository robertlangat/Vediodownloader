package com.robertlangat.socialvideodownloader.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.robertlangat.socialvideodownloader.R
import com.robertlangat.socialvideodownloader.data.Platform

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

fun toast(context: Context, msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        toast(context, "No app can open this link")
    }
}

fun openVideo(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, "video/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        toast(context, "No video player found")
    }
}

fun shareVideo(context: Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("video/mp4")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(send, "Share video").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Opens the platform's app if installed, otherwise its website. */
fun openPlatform(context: Context, platform: Platform) {
    val packages = when (platform) {
        Platform.TIKTOK -> listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")
        Platform.INSTAGRAM -> listOf("com.instagram.android")
        Platform.FACEBOOK -> listOf("com.facebook.katana", "com.facebook.lite")
    }
    for (pkg in packages) {
        val launch = context.packageManager.getLaunchIntentForPackage(pkg) ?: continue
        try {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
        }
    }
    openUrl(context, platform.website)
}

val Platform.website: String
    get() = when (this) {
        Platform.TIKTOK -> "https://www.tiktok.com/"
        Platform.INSTAGRAM -> "https://www.instagram.com/"
        Platform.FACEBOOK -> "https://www.facebook.com/"
    }

val Platform.tileRes: Int
    get() = when (this) {
        Platform.TIKTOK -> R.drawable.tile_tiktok
        Platform.INSTAGRAM -> R.drawable.tile_instagram
        Platform.FACEBOOK -> R.drawable.tile_facebook
    }
