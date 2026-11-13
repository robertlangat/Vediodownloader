package com.robertlangat.socialvideodownloader.data

import android.content.Context

/** Small persistent store for app preferences. */
class Settings(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

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
        const val KEY_SAVE_TREE = "save_tree_uri"
    }
}
