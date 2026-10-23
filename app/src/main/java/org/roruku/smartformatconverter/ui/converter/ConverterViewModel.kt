package org.roruku.smartformatconverter.ui.converter

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.roruku.smartformatconverter.data.db.ConversionJobEntity
import org.roruku.smartformatconverter.data.db.MediaType
import org.roruku.smartformatconverter.data.repository.ConversionRepository
import org.roruku.smartformatconverter.data.storage.BatchScanConfig
import org.roruku.smartformatconverter.data.storage.BatchConversionScanner
import org.roruku.smartformatconverter.data.storage.MediaDetails
import org.roruku.smartformatconverter.data.storage.StorageManager
import org.roruku.smartformatconverter.service.MediaConversionService

data class SelectedMediaFile(
    val uri: Uri,
    val details: MediaDetails,
)

data class ConverterUiState(
    val selectedTab: Int = 0,
    val selectedFiles: List<SelectedMediaFile> = emptyList(),
    val targetFormat: String = "mp4",
    val presetName: String = "Balanced",
    val customBitrateKbps: Int = 2000,
    val customResolution: String = "1920x1080",
    val customCrf: Int = 23,
    val customAudioBitrateKbps: Int = 192,
    val isConverting: Boolean = false,
    val message: String? = null
)

class ConverterViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ConversionRepository(application)

    private val _uiState = MutableStateFlow(ConverterUiState())
    val uiState: StateFlow<ConverterUiState> = _uiState.asStateFlow()

    fun selectTab(tabIndex: Int) {
        val defaultFormat = when (tabIndex) {
            0 -> "mp4"
            1 -> "jpg"
            2 -> "mp3"
            else -> "mp4"
        }
        _uiState.update {
            it.copy(
                selectedTab = tabIndex,
                targetFormat = defaultFormat,
                selectedFiles = emptyList()
            )
        }
    }

    fun addSelectedUris(uris: List<Uri>) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val newFiles = uris.map { uri ->
                val details = StorageManager.parseMediaDetails(context, uri)
                SelectedMediaFile(uri, details)
            }
            _uiState.update { current ->
                current.copy(selectedFiles = current.selectedFiles + newFiles)
            }
        }
    }

    fun scanAndAddFolder(treeUri: Uri) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val config = BatchScanConfig(
                targetVideoFormat = _uiState.value.targetFormat,
                targetImageFormat = _uiState.value.targetFormat,
                targetAudioFormat = _uiState.value.targetFormat,
                presetName = _uiState.value.presetName
            )
            val scannedJobs = withContext(Dispatchers.IO) {
                BatchConversionScanner.scanDocumentTree(context, treeUri, config)
            }

            val newFiles = scannedJobs.map { job ->
                SelectedMediaFile(
                    uri = job.inputUri.toUri(),
                    details = MediaDetails(
                        fileName = job.fileName,
                        fileSize = job.fileSize,
                        mimeType = job.inputFormat
                    )
                )
            }

            _uiState.update { current ->
                current.copy(
                    selectedFiles = current.selectedFiles + newFiles,
                    message = "Scanned ${newFiles.size} media files from folder!"
                )
            }
        }
    }

    fun removeSelectedFile(file: SelectedMediaFile) {
        _uiState.update { current ->
            current.copy(selectedFiles = current.selectedFiles.filter { it.uri != file.uri })
        }
    }

    fun clearSelectedFiles() {
        _uiState.update { it.copy(selectedFiles = emptyList()) }
    }

    fun updateTargetFormat(format: String) {
        _uiState.update { it.copy(targetFormat = format) }
    }

    fun updatePreset(preset: String) {
        _uiState.update { it.copy(presetName = preset) }
    }

    fun updateCustomBitrate(bitrate: Int) {
        _uiState.update { it.copy(customBitrateKbps = bitrate) }
    }

    fun startConversion() {
        val files = _uiState.value.selectedFiles
        if (files.isEmpty()) return

        val mediaType = when (_uiState.value.selectedTab) {
            0 -> MediaType.VIDEO
            1 -> MediaType.IMAGE
            2 -> MediaType.AUDIO
            else -> MediaType.VIDEO
        }

        viewModelScope.launch {
            val context = getApplication<Application>()
            val jobs = files.map { file ->
                ConversionJobEntity(
                    fileName = file.details.fileName,
                    inputUri = file.uri.toString(),
                    mediaType = mediaType,
                    inputFormat = file.details.mimeType?.substringAfter('/') ?: "unknown",
                    outputFormat = _uiState.value.targetFormat,
                    presetName = _uiState.value.presetName,
                    fileSize = file.details.fileSize
                )
            }

            repository.addJobs(jobs)
            MediaConversionService.startService(context)

            _uiState.update {
                it.copy(
                    selectedFiles = emptyList(),
                    message = "Added ${jobs.size} jobs to queue!"
                )
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
