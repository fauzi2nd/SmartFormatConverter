package org.roruku.smartformatconverter.ui.converter

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import org.roruku.smartformatconverter.R
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.roruku.smartformatconverter.data.db.ConversionJobEntity
import org.roruku.smartformatconverter.data.db.MediaType
import org.roruku.smartformatconverter.data.preferences.UserPreferencesRepository
import org.roruku.smartformatconverter.data.repository.ConversionRepository
import org.roruku.smartformatconverter.data.storage.BatchConversionScanner
import org.roruku.smartformatconverter.data.storage.BatchScanConfig
import org.roruku.smartformatconverter.data.storage.MediaDetails
import org.roruku.smartformatconverter.data.storage.MediaSelectionPolicy
import org.roruku.smartformatconverter.data.storage.StorageManager
import org.roruku.smartformatconverter.service.MediaConversionService
import org.roruku.smartformatconverter.engine.ffmpeg.VideoAdvancedSettings
import org.roruku.smartformatconverter.engine.ffmpeg.VideoSettingsValidator

data class SelectedMediaFile(
    val uri: Uri,
    val details: MediaDetails,
    // null = +Files (flat output); non-null = Folder (mirrored batch path)
    val relativeFolderPath: String? = null,
)

data class ConverterUiState(
    val selectedTab: Int = 0,
    val selectedFiles: List<SelectedMediaFile> = emptyList(),
    val targetFormat: String = "mp4",
    val presetName: String = "Custom",
    val videoSettings: VideoAdvancedSettings = VideoAdvancedSettings(),
    val isEditorOpen: Boolean = false,
    val perFileVideoSettings: Map<String, VideoAdvancedSettings> = emptyMap(),
    val isScanning: Boolean = false,
    val isConverting: Boolean = false,
    val message: String? = null,
)

class ConverterViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ConversionRepository(application)
    private val preferences = UserPreferencesRepository(application)

    private val _uiState = MutableStateFlow(ConverterUiState())
    val uiState: StateFlow<ConverterUiState> = _uiState.asStateFlow()

    fun selectTab(tabIndex: Int) {
        if (tabIndex !in 0..2 || _uiState.value.selectedTab == tabIndex) return
        val defaultFormat = when (tabIndex) {
            0 -> "mp4"
            1 -> "jpg"
            else -> "mp3"
        }
        _uiState.update {
            it.copy(
                selectedTab = tabIndex,
                targetFormat = defaultFormat,
                selectedFiles = emptyList(),
                perFileVideoSettings = emptyMap(),
                isEditorOpen = false,
                isScanning = false,
                message = null
            )
        }
    }

    /** A Format Factory tile sets the output category before launching SAF. */
    fun selectFormat(tabIndex: Int, format: String) {
        if (tabIndex !in 0..2) return
        _uiState.update { state ->
            state.copy(
                selectedTab = tabIndex,
                targetFormat = format.lowercase(),
                selectedFiles = emptyList(),
                isEditorOpen = false,
                perFileVideoSettings = emptyMap(),
                videoSettings = if (tabIndex == 0) {
                    state.videoSettings.copy(
                        format = format.lowercase(),
                        videoCodec = "auto",
                        audioCodec = "auto"
                    )
                } else state.videoSettings,
                message = null
            )
        }
    }

    fun closeEditor() {
        _uiState.update {
            it.copy(isEditorOpen = false, selectedFiles = emptyList(),
                perFileVideoSettings = emptyMap(), message = null)
        }
    }

    /** A single-file override leaves the batch configuration intact. */
    fun updateVideoSettingsForFile(uri: Uri, settings: VideoAdvancedSettings) {
        _uiState.update { state ->
            state.copy(perFileVideoSettings = state.perFileVideoSettings +
                (uri.toString() to settings.copy(format = state.targetFormat)))
        }
    }

    fun clearVideoSettingsOverride(uri: Uri) {
        _uiState.update { state ->
            state.copy(perFileVideoSettings = state.perFileVideoSettings - uri.toString())
        }
    }

    fun addSelectedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val selectedTab = _uiState.value.selectedTab
        val expectedType = typeForTab(selectedTab)
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val filtered = withContext(Dispatchers.IO) {
                    uris.mapNotNull { uri ->
                        val details = StorageManager.parseMediaDetails(context, uri)
                        if (MediaSelectionPolicy.detect(details.mimeType, details.fileName) == expectedType) {
                            SelectedMediaFile(uri = uri, details = details)
                        } else {
                            null
                        }
                    }
                }
                if (_uiState.value.selectedTab != selectedTab) return@launch
                mergeSelectedFiles(filtered)
                if (filtered.isNotEmpty()) {
                    _uiState.update { it.copy(isEditorOpen = true) }
                }
                val rejected = uris.size - filtered.size
                _uiState.update {
                    it.copy(message = if (rejected > 0) {
                        messageFor(R.string.vm_files_added_skipped, filtered.size, rejected)
                    } else {
                        messageFor(R.string.vm_files_added, filtered.size)
                    })
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed selecting media files", e)
                _uiState.update { it.copy(message = messageFor(R.string.vm_files_read_failed, e.message ?: messageFor(R.string.vm_unknown_reason))) }
            }
        }
    }

    fun scanAndAddFolder(treeUri: Uri) {
        val current = _uiState.value
        if (current.isScanning) return
        val selectedTab = current.selectedTab
        val mediaType = typeForTab(selectedTab)

        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            try {
                val context = getApplication<Application>()
                val settings = preferences.userSettingsFlow.first()
                val outputUri = settings.defaultOutputUri
                if (outputUri.isNullOrBlank()) {
                    _uiState.update {
                        it.copy(message = messageFor(R.string.vm_parent_folder_scan_required))
                    }
                    return@launch
                }

                val config = BatchScanConfig(
                    targetVideoFormat = current.targetFormat,
                    targetImageFormat = current.targetFormat,
                    targetAudioFormat = current.targetFormat,
                    presetName = current.presetName
                )
                val scannedJobs = withContext(Dispatchers.IO) {
                    BatchConversionScanner.scanDocumentTree(
                        context = context,
                        treeUri = treeUri,
                        config = config,
                        mediaTypeFilter = mediaType,
                        outputParentUri = outputUri.toUri(),
                    )
                }

                if (_uiState.value.selectedTab != selectedTab) return@launch

                val files = scannedJobs.map { job ->
                    SelectedMediaFile(
                        uri = job.inputUri.toUri(),
                        details = MediaDetails(
                            fileName = job.fileName,
                            fileSize = job.fileSize,
                            mimeType = StorageManager.getMimeType(context, job.inputUri.toUri())
                        ),
                        relativeFolderPath = job.relativeFolderPath ?: ""
                    )
                }
                val oldCount = _uiState.value.selectedFiles.size
                mergeSelectedFiles(files)
                if (files.isNotEmpty()) {
                    _uiState.update { it.copy(isEditorOpen = true) }
                }
                val added = _uiState.value.selectedFiles.size - oldCount
                _uiState.update {
                    it.copy(message = if (added == 0) {
                        messageFor(R.string.vm_scan_empty)
                    } else {
                        messageFor(R.string.vm_scan_added, added, mediaTypeLabel(mediaType))
                    })
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed scanning batch folder", e)
                _uiState.update { it.copy(message = messageFor(R.string.vm_scan_failed, e.message ?: messageFor(R.string.vm_unknown_reason))) }
            } finally {
                _uiState.update { it.copy(isScanning = false) }
            }
        }
    }

    private fun mergeSelectedFiles(files: List<SelectedMediaFile>) {
        _uiState.update { state ->
            val existingUris = state.selectedFiles.map { it.uri.toString() }.toHashSet()
            val unique = files.filter { existingUris.add(it.uri.toString()) }
            state.copy(selectedFiles = state.selectedFiles + unique)
        }
    }

    fun removeSelectedFile(file: SelectedMediaFile) {
        _uiState.update { current ->
            current.copy(
                selectedFiles = current.selectedFiles.filter { it.uri != file.uri },
                perFileVideoSettings = current.perFileVideoSettings - file.uri.toString()
            )
        }
    }

    fun clearSelectedFiles() {
        _uiState.update { it.copy(selectedFiles = emptyList(), perFileVideoSettings = emptyMap()) }
    }

    fun updatePreset(preset: String) {
        _uiState.update { state ->
            state.copy(
                presetName = preset,
                videoSettings = if (state.selectedTab == 0) {
                    state.videoSettings.applyPreset(preset)
                } else {
                    state.videoSettings
                },
                perFileVideoSettings = emptyMap()
            )
        }
    }

    /** Any advanced edit converts a quick preset into an explicit Custom snapshot. */
    fun updateVideoSettings(settings: VideoAdvancedSettings) {
        _uiState.update { state ->
            state.copy(
                presetName = "Custom",
                videoSettings = settings.copy(
                    format = state.targetFormat,
                    preset = "Custom"
                ),
                perFileVideoSettings = emptyMap()
            )
        }
    }

    fun startConversion() {
        val state = _uiState.value
        if (state.selectedFiles.isEmpty() || state.isConverting || state.isScanning) return
        val mediaType = typeForTab(state.selectedTab)
        // Resolve and validate a separate immutable configuration per source
        // file before submitting a batch. Nothing is read from UI state later.
        val videoSnapshots = if (mediaType == MediaType.VIDEO) {
            state.selectedFiles.associate { file ->
                val config = (state.perFileVideoSettings[file.uri.toString()]
                    ?: state.videoSettings).copy(format = state.targetFormat)
                file.uri.toString() to config
            }
        } else emptyMap()

        for (config in videoSnapshots.values) {
            val error = VideoSettingsValidator.validate(config)
            if (error != null) {
                _uiState.update { it.copy(message = error) }
                return
            }
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isConverting = true) }
            try {
                val settings = preferences.userSettingsFlow.first()
                if (settings.defaultOutputUri.isNullOrBlank()) {
                    _uiState.update {
                        it.copy(message = messageFor(R.string.vm_parent_folder_conversion_required))
                    }
                    return@launch
                }

                val context = getApplication<Application>()
                val jobs = state.selectedFiles.map { file ->
                    ConversionJobEntity(
                        fileName = file.details.fileName,
                        inputUri = file.uri.toString(),
                        mediaType = mediaType,
                        inputFormat = file.details.mimeType?.substringAfter('/') ?: "unknown",
                        outputFormat = state.targetFormat,
                        presetName = state.presetName,
                        fileSize = file.details.fileSize,
                        relativeFolderPath = file.relativeFolderPath,
                        customParamsJson = videoSnapshots[file.uri.toString()]?.toJson(),
                    )
                }
                repository.addJobs(jobs)
                MediaConversionService.startService(context)
                _uiState.update {
                    it.copy(
                        selectedFiles = emptyList(),
                        perFileVideoSettings = emptyMap(),
                        isEditorOpen = false,
                        message = messageFor(R.string.vm_jobs_queued, jobs.size)
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed starting conversion", e)
                _uiState.update {
                    it.copy(message = messageFor(R.string.vm_queue_failed, e.message ?: messageFor(R.string.vm_unknown_reason)))
                }
            } finally {
                _uiState.update { it.copy(isConverting = false) }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun messageFor(@StringRes resId: Int, vararg arguments: Any): String =
        getApplication<Application>().getString(resId, *arguments)

    private fun mediaTypeLabel(type: MediaType): String = messageFor(
        when (type) {
            MediaType.VIDEO -> R.string.media_video
            MediaType.IMAGE -> R.string.media_image
            MediaType.AUDIO -> R.string.media_audio
            MediaType.DOWNLOAD -> R.string.media_download
        }
    )

    private fun typeForTab(index: Int): MediaType = when (index) {
        0 -> MediaType.VIDEO
        1 -> MediaType.IMAGE
        else -> MediaType.AUDIO
    }

    private companion object {
        const val TAG = "ConverterViewModel"
    }
}
