package org.roruku.smartformatconverter.ui.downloader

import android.app.Application
import org.roruku.smartformatconverter.R
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.roruku.smartformatconverter.data.db.ConversionJobEntity
import org.roruku.smartformatconverter.data.db.MediaType
import org.roruku.smartformatconverter.data.repository.ConversionRepository
import org.roruku.smartformatconverter.data.preferences.UserPreferencesRepository
import org.roruku.smartformatconverter.engine.downloader.DirectDownloadSnapshot
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
    private val preferences = UserPreferencesRepository(application)
    private var inspectionJob: Job? = null

    private val _uiState = MutableStateFlow(DownloaderUiState())
    val uiState: StateFlow<DownloaderUiState> = _uiState.asStateFlow()

    fun updateUrlInput(url: String) {
        if (_uiState.value.urlInput == url) return
        inspectionJob?.cancel()
        // Stale format info must never remain selectable after URL changes.
        _uiState.update { it.copy(
            urlInput = url,
            isLoadingInfo = false,
            mediaInfo = null,
            selectedFormat = null,
            message = null
        ) }
    }

    fun extractInfo() {
        val url = _uiState.value.urlInput.trim()
        if (url.isBlank()) return
        inspectionJob?.cancel()
        inspectionJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingInfo = true, mediaInfo = null,
                selectedFormat = null, message = null) }
            try {
                val result = HttpDownloaderEngine.extractMediaInfo(getApplication(), url)
                // An inspection result from an older URL must not replace a new one.
                if (_uiState.value.urlInput.trim() != url) return@launch
                result.fold(
                    onSuccess = { info ->
                        _uiState.update { it.copy(
                            isLoadingInfo = false, mediaInfo = info,
                            selectedFormat = info.availableFormats.firstOrNull()
                        ) }
                    },
                    onFailure = { error ->
                        _uiState.update { it.copy(
                            isLoadingInfo = false, mediaInfo = null, selectedFormat = null,
                            message = error.message ?: getApplication<Application>()
                                .getString(R.string.download_inspection_failed)
                        ) }
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_uiState.value.urlInput.trim() == url) {
                    _uiState.update { it.copy(isLoadingInfo = false,
                        message = e.message ?: getApplication<Application>()
                            .getString(R.string.download_inspection_failed)) }
                }
            }
        }
    }

    fun selectFormat(format: HttpMediaFormatOption) {
        _uiState.update { state ->
            if (format in (state.mediaInfo?.availableFormats ?: emptyList()))
                state.copy(selectedFormat = format)
            else state
        }
    }

    fun startDownload() {
        val current = _uiState.value
        val info = current.mediaInfo ?: return
        val format = current.selectedFormat ?: return
        if (current.isLoadingInfo || current.urlInput.trim() != info.url ||
            format !in info.availableFormats) {
            _uiState.update { it.copy(message = getApplication<Application>()
                .getString(R.string.downloader_url_changed)) }
            return
        }

        viewModelScope.launch {
            val context = getApplication<Application>()
            try {
                val settings = preferences.userSettingsFlow.first()
                if (settings.defaultOutputUri.isNullOrBlank()) {
                    _uiState.update { it.copy(
                        message = context.getString(R.string.vm_parent_folder_conversion_required)
                    ) }
                    return@launch
                }
                // Direct files are downloaded as-is, not transcoded to the
                // requested extension; the only offered format is the source.
                val job = ConversionJobEntity(
                    fileName = "${info.title}.${format.extension}",
                    inputUri = info.url,
                    mediaType = MediaType.DOWNLOAD,
                    inputFormat = format.extension,
                    outputFormat = format.extension,
                    presetName = "Direct",
                    fileSize = format.fileSizeEstimate,
                    customParamsJson = DirectDownloadSnapshot(
                        extension = format.extension,
                        etag = info.etag,
                        lastModified = info.lastModified
                    ).toJson()
                )
                repository.addJob(job)
                MediaConversionService.startService(context)
                _uiState.update { it.copy(
                    urlInput = "", mediaInfo = null, selectedFormat = null,
                    message = context.getString(R.string.download_queued_message, info.title)
                ) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(message = context.getString(
                    R.string.downloader_inspect_error,
                    e.message ?: context.getString(R.string.download_inspection_failed)
                )) }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
