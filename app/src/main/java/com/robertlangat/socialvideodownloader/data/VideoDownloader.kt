package com.robertlangat.socialvideodownloader.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/** The user-chosen SAF folder can no longer be written. */
class FolderAccessException(message: String) : Exception(message)

/** The file URL answered 403/404/410: the video is gone or the link expired. */
class DownloadUnavailableException(message: String) : Exception(message)

data class SavedVideo(val uri: Uri, val displayName: String, val location: String)

/**
 * Streams a video into the shared Movies/SocialVideoDownloader collection with MediaStore,
 * so it shows up in Gallery apps. The item stays IS_PENDING until the last byte is written
 * and is deleted again if the download fails or is cancelled.
 */
class VideoDownloader(
    private val context: Context,
    private val http: OkHttpClient,
) {
    suspend fun download(
        url: String,
        displayName: String,
        mimeType: String,
        folder: Uri? = null,
        headers: Map<String, String> = emptyMap(),
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): SavedVideo = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url).get()
        headers.forEach { (k, v) -> builder.header(k, v) }
        val request = builder.build()
        val call = http.newCall(request)
        // Abort the socket as soon as the user taps Cancel, even while a read is blocked.
        val watcher = launch {
            try {
                awaitCancellation()
            } finally {
                call.cancel()
            }
        }
        try {
            val response = try {
                call.await()
            } catch (e: IOException) {
                ensureActive()
                throw RetryableException("The download couldn't start. Check your connection and try again.", e)
            }
            response.use { resp -> saveBody(resp, displayName, mimeType, folder, onProgress) }
        } finally {
            watcher.cancel()
        }
    }

    private suspend fun saveBody(
        resp: Response,
        displayName: String,
        mimeType: String,
        folder: Uri?,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): SavedVideo {
        when {
            resp.code == 403 ->
                throw RetryableException("The site refused the download (HTTP 403). Tap Retry; if it keeps happening, sign in under Settings.")

            resp.code == 404 || resp.code == 410 ->
                throw DownloadUnavailableException(
                    "The video file is no longer available (HTTP ${resp.code}). It may have been removed or made private."
                )

            !resp.isSuccessful ->
                throw RetryableException("The server refused the download (HTTP ${resp.code}). Try again.")
        }
        val body = resp.body ?: throw RetryableException("The server sent an empty response.")
        val total = body.contentLength()

        val resolver = context.contentResolver
        val useMediaStore = folder == null
        val itemUri: Uri
        if (useMediaStore) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/$ALBUM")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            itemUri = resolver.insert(collection, values)
                ?: throw IOException("Couldn't create the video file on this phone (is storage full?).")
        } else {
            itemUri = try {
                val tree: Uri = folder!!
                val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
                DocumentsContract.createDocument(resolver, parent, mimeType, displayName)
            } catch (e: Exception) {
                null
            } ?: throw FolderAccessException(
                "Couldn't write to the chosen save folder. Choose the folder again in Settings, or switch back to the default folder."
            )
        }

        var completed = false
        try {
            var downloaded = 0L
            val out = resolver.openOutputStream(itemUri)
                ?: throw IOException("Couldn't open the video file for writing.")
            out.use {
                body.byteStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    var lastReport = 0L
                    onProgress(0, total)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = try {
                            input.read(buffer)
                        } catch (e: IOException) {
                            currentCoroutineContext().ensureActive()
                            throw RetryableException("The download was interrupted. Check your connection and retry.", e)
                        }
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastReport > 100) {
                            lastReport = now
                            onProgress(downloaded, total)
                        }
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            if (total > 0 && downloaded < total) {
                throw RetryableException("The download was interrupted. Check your connection and retry.")
            }
            if (downloaded == 0L) {
                throw RetryableException("The server sent an empty file. Try again.")
            }
            onProgress(downloaded, if (total > 0) total else downloaded)

            if (useMediaStore) {
                val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                resolver.update(itemUri, done, null, null)
            }
            completed = true
            val location = if (useMediaStore) "${Environment.DIRECTORY_MOVIES}/$ALBUM" else folderLabel(folder!!)
            return SavedVideo(itemUri, displayName, location)
        } finally {
            if (!completed) {
                try {
                    if (useMediaStore) resolver.delete(itemUri, null, null)
                    else DocumentsContract.deleteDocument(resolver, itemUri)
                } catch (_: Exception) {
                }
            }
        }
    }

    companion object {
        const val ALBUM = "SocialVideoDownloader"
        const val DEFAULT_LOCATION = "Movies/SocialVideoDownloader"

        /** "primary:Download/Videos" -> "Phone storage/Download/Videos" */
        fun folderLabel(tree: Uri): String = try {
            val id = DocumentsContract.getTreeDocumentId(tree)
            val volume = id.substringBefore(':')
            val path = id.substringAfter(':', "")
            val root = if (volume == "primary") "Phone storage" else "SD card"
            if (path.isBlank()) root else "$root/$path"
        } catch (e: Exception) {
            "Chosen folder"
        }

        fun buildFileName(suggested: String?, platform: Platform, now: Long = System.currentTimeMillis()): String {
            val base = suggested
                ?.substringBeforeLast('.')
                ?.replace(Regex("""[^\p{L}\p{N}._ -]"""), "_")
                ?.trim()
                ?.take(80)
                ?.ifBlank { null }
                ?: "${platform.apiName}_$now"
            return "$base.mp4"
        }
    }
}
