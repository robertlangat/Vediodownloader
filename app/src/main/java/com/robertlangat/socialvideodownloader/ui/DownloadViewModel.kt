package com.robertlangat.socialvideodownloader.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.robertlangat.socialvideodownloader.SvdApp
import com.robertlangat.socialvideodownloader.data.DownloadUnavailableException
import com.robertlangat.socialvideodownloader.data.Extraction
import com.robertlangat.socialvideodownloader.data.FolderAccessException
import com.robertlangat.socialvideodownloader.data.LinkParser
import com.robertlangat.socialvideodownloader.data.ParsedLink
import com.robertlangat.socialvideodownloader.data.Platform
import com.robertlangat.socialvideodownloader.data.RetryableException
import com.robertlangat.socialvideodownloader.data.VideoDownloader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

sealed interface Phase {
    data object Idle : Phase
    data object Checking : Phase
    data class Downloading(val downloaded: Long, val total: Long) : Phase
    data class Saved(val uri: Uri, val fileName: String, val location: String) : Phase
    data class NetworkError(val message: String) : Phase
    data class Unavailable(
        val message: String,
        val originalUrl: String,
        val openLabel: String = "Open original post",
        val canRetry: Boolean = false,
    ) : Phase

    data class LoginRequired(val platform: Platform, val message: String, val originalUrl: String) : Phase
    data class Failed(val message: String, val openSettings: Boolean = false) : Phase
    data object Cancelled : Phase
}

data class UiState(
    val selectedPlatform: Platform = Platform.TIKTOK,
    val input: String = "",
    val detected: ParsedLink? = null,
    val phase: Phase = Phase.Idle,
    val saveFolderLabel: String = VideoDownloader.DEFAULT_LOCATION,
    val customFolder: Boolean = false,
) {
    val isBusy: Boolean get() = phase is Phase.Checking || phase is Phase.Downloading
    val canDownload: Boolean get() = detected != null && !isBusy
    val showInvalidHint: Boolean get() = input.isNotBlank() && detected == null
}

class DownloadViewModel(app: Application) : AndroidViewModel(app) {

    private val container = (app as SvdApp).container
    private val settings = container.settings

    private val _state = MutableStateFlow(
        UiState(
            saveFolderLabel = folderLabel(settings.saveTreeUri),
            customFolder = settings.saveTreeUri != null,
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var job: Job? = null

    fun selectPlatform(platform: Platform) {
        _state.update { it.copy(selectedPlatform = platform) }
    }

    fun onInputChange(text: String) {
        val parsed = LinkParser.parse(text)
        _state.update { s ->
            val phase = if (s.isBusy) s.phase else if (text != s.input) Phase.Idle else s.phase
            s.copy(
                input = text,
                detected = parsed,
                selectedPlatform = parsed?.platform ?: s.selectedPlatform,
                phase = phase,
            )
        }
    }

    /** Called with text from the share sheet or the Paste shortcut. */
    fun onPasted(text: String) {
        val url = LinkParser.extractUrl(text) ?: text.trim()
        onInputChange(url)
    }

    fun startDownload() {
        val link = _state.value.detected ?: return
        if (_state.value.isBusy) return
        job = viewModelScope.launch {
            _state.update { it.copy(phase = Phase.Checking) }
            try {
                when (val result = container.extractor.extract(link)) {
                    is Extraction.Found -> download(link, result)
                    is Extraction.LoginRequired -> _state.update {
                        it.copy(phase = Phase.LoginRequired(link.platform, result.message, link.url))
                    }

                    is Extraction.Unavailable -> _state.update {
                        it.copy(
                            phase = Phase.Unavailable(
                                result.message,
                                link.url,
                                openLabel = "Open in ${link.platform.label}",
                                canRetry = result.canRetry,
                            )
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RetryableException) {
                _state.update { it.copy(phase = Phase.NetworkError(e.message ?: "Network error.")) }
            } catch (e: DownloadUnavailableException) {
                _state.update {
                    it.copy(phase = Phase.Unavailable(e.message ?: "Unavailable.", link.url, "Open in ${link.platform.label}"))
                }
            } catch (e: FolderAccessException) {
                _state.update { it.copy(phase = Phase.Failed(e.message ?: "Can't write to the save folder.", openSettings = true)) }
            } catch (e: IOException) {
                _state.update { it.copy(phase = Phase.NetworkError(e.message ?: "Network error.")) }
            } catch (e: Exception) {
                _state.update { it.copy(phase = Phase.Failed(e.message ?: "Something went wrong.")) }
            }
        }
    }

    private suspend fun download(link: ParsedLink, found: Extraction.Found) {
        _state.update { it.copy(phase = Phase.Downloading(0, -1)) }
        val name = VideoDownloader.buildFileName(null, link.platform)
        val folder = settings.saveTreeUri?.let { Uri.parse(it) }
        val saved = container.downloader.download(found.videoUrl, name, "video/mp4", folder, found.headers) { done, total ->
            _state.update { s ->
                if (s.phase is Phase.Downloading) s.copy(phase = Phase.Downloading(done, total)) else s
            }
        }
        _state.update { it.copy(phase = Phase.Saved(saved.uri, saved.displayName, saved.location)) }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _state.update { it.copy(phase = Phase.Cancelled) }
    }

    fun retry() = startDownload()

    /** null = default Movies/SocialVideoDownloader (Gallery) folder. */
    fun setSaveFolder(tree: Uri?) {
        settings.saveTreeUri = tree?.toString()
        _state.update { it.copy(saveFolderLabel = folderLabel(settings.saveTreeUri), customFolder = tree != null) }
    }

    private fun folderLabel(tree: String?): String =
        tree?.let { VideoDownloader.folderLabel(Uri.parse(it)) } ?: VideoDownloader.DEFAULT_LOCATION
}
