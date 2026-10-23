package org.roruku.smartformatconverter.ui.downloader

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.roruku.smartformatconverter.data.db.ConversionJobEntity
import org.roruku.smartformatconverter.data.db.MediaType
import org.roruku.smartformatconverter.data.repository.ConversionRepository
import org.roruku.smartformatconverter.engine.downloader.HttpDownloadableMediaInfo
import org.roruku.smartformatconverter.engine.downloader.HttpDownloaderEngine
import org.roruku.smartformatconverter.engine.downloader.HttpMediaFormatOption
import org.roruku.smartformatconverter.service.MediaConversionService

data class DownloaderUiState(
    val urlInput: String = "",
    val isLoadingInfo: Boolean = false,
    val mediaInfo: HttpDownloadableMediaInfo? = null,
    val selectedFormat: HttpMediaFormatOption? = null,
    val message: String? = null,
)

class DownloaderViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ConversionRepository(application)

    private val _uiState = MutableStateFlow(DownloaderUiState())
    val uiState: StateFlow<DownloaderUiState> = _uiState.asStateFlow()

    fun updateUrlInput(url: String) {
        _uiState.update { it.copy(urlInput = url) }
    }

    fun extractInfo() {
        val url = _uiState.value.urlInput.trim()
        if (url.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingInfo = true, message = null) }
            val context = getApplication<Application>()
            val result = HttpDownloaderEngine.extractMediaInfo(context, url)

            if (result.isSuccess) {
                val info = result.getOrThrow()
                _uiState.update {
                    it.copy(
                        isLoadingInfo = false,
                        mediaInfo = info,
                        selectedFormat = info.availableFormats.firstOrNull()
                    )
                }
            } else {
                val err = result.exceptionOrNull()?.message ?: "Failed to inspect URL"
                _uiState.update {
                    it.copy(
                        isLoadingInfo = false,
                        mediaInfo = null,
                        message = err
                    )
                }
            }
        }
    }

    fun selectFormat(format: HttpMediaFormatOption) {
        _uiState.update { it.copy(selectedFormat = format) }
    }

    fun startDownload() {
        val info = _uiState.value.mediaInfo ?: return
        val format = _uiState.value.selectedFormat ?: return

        viewModelScope.launch {
            val context = getApplication<Application>()
            val job = ConversionJobEntity(
                fileName = "${info.title}.${format.extension}",
                inputUri = info.url,
                mediaType = MediaType.DOWNLOAD,
                inputFormat = "http_stream",
                outputFormat = format.extension,
                presetName = format.resolution
            )

            repository.addJob(job)
            MediaConversionService.startService(context)

            _uiState.update {
                it.copy(
                    urlInput = "",
                    mediaInfo = null,
                    selectedFormat = null,
                    message = "Download queued: ${info.title}"
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
