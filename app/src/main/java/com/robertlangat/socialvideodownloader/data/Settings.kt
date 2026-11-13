package com.robertlangat.socialvideodownloader.data

import android.content.Context
import com.robertlangat.socialvideodownloader.BuildConfig

/** Small persistent store for the backend URL and the backend session token. */
class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Backend base URL without a trailing slash, or "" when none is configured. */
    var backendUrl: String
        get() = (prefs.getString(KEY_BACKEND_URL, null)?.takeIf { it.isNotBlank() } ?: BuildConfig.BACKEND_URL)
            .trim().trimEnd('/')
        set(value) {
            prefs.edit().putString(KEY_BACKEND_URL, value.trim().trimEnd('/')).apply()
        }

    /** Opaque session id issued by the backend after the user connects an account. */
    var sessionToken: String?
        get() = prefs.getString(KEY_SESSION, null)
        set(value) {
            prefs.edit().putString(KEY_SESSION, value).apply()
        }

    var connectedPlatforms: Set<String>
        get() = prefs.getStringSet(KEY_CONNECTED, emptySet()) ?: emptySet()
        set(value) {
            prefs.edit().putStringSet(KEY_CONNECTED, value).apply()
        }

    /** SAF tree URI of a user-chosen save folder, or null for the default Movies/SocialVideoDownloader. */
    var saveTreeUri: String?
        get() = prefs.getString(KEY_SAVE_TREE, null)
        set(value) {
            prefs.edit().putString(KEY_SAVE_TREE, value).apply()
        }

    fun clear() {
        prefs.edit().clear().commit()
    }

    companion object {
        const val PREFS = "svd_settings"
        const val KEY_BACKEND_URL = "backend_url"
        const val KEY_SESSION = "session_token"
        const val KEY_CONNECTED = "connected_platforms"
        const val KEY_SAVE_TREE = "save_tree_uri"
    }
}
