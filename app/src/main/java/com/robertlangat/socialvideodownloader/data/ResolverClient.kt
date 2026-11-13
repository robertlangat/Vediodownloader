package com.robertlangat.socialvideodownloader.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

sealed interface ResolveResult {
    /** The provider returned a direct, authorised download URL for the video file. */
    data class Ready(
        val downloadUrl: String,
        val fileName: String?,
        val mimeType: String?,
        val title: String?,
    ) : ResolveResult

    /** The post exists but cannot be downloaded (private, removed, not yours, platform does not allow it...). */
    data class Unavailable(
        val reason: String,
        val message: String,
        val originalUrl: String,
    ) : ResolveResult

    /** The user has to connect their account on the backend before this link can be resolved. */
    data class AuthRequired(
        val platform: Platform?,
        val message: String,
        val authUrl: String?,
        val originalUrl: String,
    ) : ResolveResult
}

/** Network or server problem that is worth retrying. */
class RetryableException(message: String, cause: Throwable? = null) : IOException(message, cause)

/** Configuration problem (e.g. no backend URL) that a retry will not fix. */
class ConfigurationException(message: String) : Exception(message)

/**
 * Client for the resolver backend (see backend/README.md for the API contract):
 *
 *   POST {backend}/api/resolve   {"url": "...", "platform": "instagram"}
 */
class ResolverClient(
    private val http: OkHttpClient,
    private val settings: Settings,
) {
    suspend fun resolve(link: ParsedLink): ResolveResult = withContext(Dispatchers.IO) {
        val base = settings.backendUrl
        if (base.isBlank()) {
            throw ConfigurationException(
                "Downloads aren't set up yet. Open Settings → Advanced and add the download service address."
            )
        }
        val body = JSONObject()
            .put("url", link.url)
            .put("platform", link.platform.apiName)
            .toString()
            .toRequestBody(JSON)
        val builder = Request.Builder()
            .url("$base/api/resolve")
            .post(body)
            .header("Accept", "application/json")
        settings.sessionToken?.let { builder.header("Authorization", "Bearer $it") }

        val response = try {
            http.newCall(builder.build()).await()
        } catch (e: IOException) {
            throw RetryableException("Couldn't reach the download service. Check your connection and try again.", e)
        }
        response.use { resp ->
            val text = try {
                resp.body?.string().orEmpty()
            } catch (e: IOException) {
                throw RetryableException("The connection was interrupted while checking the link.", e)
            }
            if (resp.code >= 500 || resp.code == 429) {
                throw RetryableException("The download service is busy or unavailable (HTTP ${resp.code}). Try again.")
            }
            val json = try {
                JSONObject(text)
            } catch (e: JSONException) {
                throw RetryableException("The download service sent an unexpected response (HTTP ${resp.code}).")
            }
            parse(json, link)
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun parse(json: JSONObject, link: ParsedLink): ResolveResult {
            val message = json.optString("message").ifBlank { null }
            val original = json.optString("originalUrl").ifBlank { link.url }
            return when (json.optString("status")) {
                "ready" -> {
                    val url = json.optString("downloadUrl")
                    if (url.isBlank()) throw RetryableException("The download service did not return a file URL.")
                    ResolveResult.Ready(
                        downloadUrl = url,
                        fileName = json.optString("fileName").ifBlank { null },
                        mimeType = json.optString("mimeType").ifBlank { null },
                        title = json.optString("title").ifBlank { null },
                    )
                }

                "unavailable" -> {
                    val reason = json.optString("reason").ifBlank { "unavailable" }
                    ResolveResult.Unavailable(reason, message ?: defaultMessage(reason), original)
                }

                "auth_required" -> ResolveResult.AuthRequired(
                    platform = Platform.fromApiName(json.optString("platform")) ?: link.platform,
                    message = message ?: "Connect your ${link.platform.label} account to download your own videos.",
                    authUrl = json.optString("authUrl").ifBlank { null },
                    originalUrl = original,
                )

                "retry", "error_retryable" -> throw RetryableException(message ?: "Temporary problem. Try again.")

                else -> ResolveResult.Unavailable(
                    "error",
                    message ?: "This link couldn't be checked.",
                    original,
                )
            }
        }

        fun defaultMessage(reason: String): String = when (reason) {
            "private" -> "This post is private, so it can't be downloaded."
            "removed" -> "This post was removed or the link is broken."
            "not_owned" -> "This video isn't in an account you've connected, so it can't be downloaded."
            "not_downloadable" -> "The platform doesn't allow this video to be downloaded."
            "not_video" -> "This post doesn't contain a video."
            else -> "This video isn't available for download."
        }
    }
}
