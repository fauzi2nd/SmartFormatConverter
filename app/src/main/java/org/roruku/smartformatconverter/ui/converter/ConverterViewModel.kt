package org.roruku.smartformatconverter.ui.converter

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.annotation.PluralsRes
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
import org.roruku.smartformatconverter.data.repository.Media3OutputPolicy
import org.roruku.smartformatconverter.data.repository.SourceFormatLabels
import org.roruku.smartformatconverter.engine.media3.Media3DeviceCapabilities
import org.roruku.smartformatconverter.engine.media3.MediaProbe
import org.roruku.smartformatconverter.engine.media3.VideoEncoderCatalog
import org.roruku.smartformatconverter.engine.media.AdaptiveResolutionPolicy
import org.roruku.smartformatconverter.data.storage.BatchConversionScanner
import org.roruku.smartformatconverter.data.storage.BatchScanConfig
import org.roruku.smartformatconverter.data.storage.MediaDetails
import org.roruku.smartformatconverter.data.storage.MediaSelectionPolicy
import org.roruku.smartformatconverter.data.storage.StorageManager
import org.roruku.smartformatconverter.service.MediaConversionService
import org.roruku.smartformatconverter.engine.media.VideoAdvancedSettings
import org.roruku.smartformatconverter.engine.media.VideoSettingsValidator
import org.roruku.smartformatconverter.engine.media.ImageAdvancedSettings
import org.roruku.smartformatconverter.engine.media.ImageSettingsValidator
import org.roruku.smartformatconverter.engine.media.AudioAdvancedSettings
import org.roruku.smartformatconverter.engine.media.AudioSettingsValidator

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
    val presetName: String = "Smart Auto",
    val videoSettings: VideoAdvancedSettings = VideoAdvancedSettings().applyPreset("Smart Auto"),
    val imageSettings: ImageAdvancedSettings = ImageAdvancedSettings().applyPreset("Smart Auto"),
    val audioSettings: AudioAdvancedSettings = AudioAdvancedSettings().applyPreset("Smart Auto"),
    val isEditorOpen: Boolean = false,
    val perFileVideoSettings: Map<String, VideoAdvancedSettings> = emptyMap(),
    val perFileImageSettings: Map<String, ImageAdvancedSettings> = emptyMap(),
    val perFileAudioSettings: Map<String, AudioAdvancedSettings> = emptyMap(),
    val isScanning: Boolean = false,
    val isConverting: Boolean = false,
    val message: String? = null,
)

class ConverterViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ConversionRepository(application)
    private val preferences = UserPreferencesRepository(application)

    private val _uiState = MutableStateFlow(ConverterUiState())
    val uiState: StateFlow<ConverterUiState> = _uiState.asStateFlow()

    /** Fresh selections use source-aware Smart Auto; manual edits become Custom. */
    private fun defaultVideo(format: String) =
        VideoAdvancedSettings(format = format).applyPreset("Smart Auto")

    private fun defaultImage(format: String) =
        ImageAdvancedSettings(format = format).applyPreset("Smart Auto")

    private fun defaultAudio(format: String) =
        AudioAdvancedSettings(format = format).applyPreset("Smart Auto")

    fun selectTab(tabIndex: Int) {
        if (tabIndex !in 0..2 || _uiState.value.selectedTab == tabIndex) return
        val defaultFormat = when (tabIndex) {
            0 -> "mp4"
            1 -> "jpg"
            else -> "m4a"
        }
        _uiState.update {
            it.copy(
                selectedTab = tabIndex,
                targetFormat = defaultFormat,
                presetName = "Smart Auto",
                videoSettings = defaultVideo("mp4"),
                imageSettings = defaultImage("jpg"),
                audioSettings = defaultAudio("m4a"),
                selectedFiles = emptyList(),
                perFileVideoSettings = emptyMap(),
                perFileImageSettings = emptyMap(),
                perFileAudioSettings = emptyMap(),
                isEditorOpen = false,
                isScanning = false,
                message = null
            )
        }
    }

    /** A Format Factory tile sets the output category before launching SAF. */
    fun selectFormat(tabIndex: Int, format: String) {
        if (tabIndex !in 0..2) return
        val normalizedFormat = format.lowercase()
        _uiState.update { state ->
            state.copy(
                selectedTab = tabIndex,
                targetFormat = normalizedFormat,
                selectedFiles = emptyList(),
                isEditorOpen = false,
                perFileVideoSettings = emptyMap(),
                perFileImageSettings = emptyMap(),
                perFileAudioSettings = emptyMap(),
                presetName = "Smart Auto",
                videoSettings = if (tabIndex == 0) defaultVideo(normalizedFormat)
                    else state.videoSettings,
                imageSettings = if (tabIndex == 1) defaultImage(normalizedFormat)
                    else state.imageSettings,
                audioSettings = if (tabIndex == 2) defaultAudio(normalizedFormat)
                    else state.audioSettings,
                message = null
            )
        }
    }

    fun closeEditor() {
        _uiState.update {
            it.copy(isEditorOpen = false, selectedFiles = emptyList(),
                perFileVideoSettings = emptyMap(),
                perFileImageSettings = emptyMap(),
                perFileAudioSettings = emptyMap(), message = null)
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

    /** Per-file settings overrides are independent of the batch defaults. */
    fun updateImageSettings(value: ImageAdvancedSettings) {
        _uiState.update { state -> state.copy(
            presetName = "Custom",
            imageSettings = value.copy(format = state.targetFormat, preset = "Custom"),
            perFileImageSettings = emptyMap()
        ) }
    }

    fun updateAudioSettings(value: AudioAdvancedSettings) {
        _uiState.update { state -> state.copy(
            presetName = "Custom",
            audioSettings = value.copy(format = state.targetFormat, preset = "Custom"),
            perFileAudioSettings = emptyMap()
        ) }
    }

    fun updateImagePreset(name: String) {
        _uiState.update { state -> state.copy(
            presetName = name,
            imageSettings = state.imageSettings.applyPreset(name),
            perFileImageSettings = emptyMap()
        ) }
    }

    fun updateAudioPreset(name: String) {
        _uiState.update { state -> state.copy(
            presetName = name,
            audioSettings = state.audioSettings.applyPreset(name),
            perFileAudioSettings = emptyMap()
        ) }
    }

    fun updateImageSettingsForFile(uri: Uri, settings: ImageAdvancedSettings) {
        _uiState.update { state -> state.copy(
            perFileImageSettings = state.perFileImageSettings +
                (uri.toString() to settings.copy(format = state.targetFormat))
        ) }
    }

    fun updateAudioSettingsForFile(uri: Uri, settings: AudioAdvancedSettings) {
        _uiState.update { state -> state.copy(
            perFileAudioSettings = state.perFileAudioSettings +
                (uri.toString() to settings.copy(format = state.targetFormat))
        ) }
    }

    fun clearMediaSettingsOverride(uri: Uri) {
        _uiState.update { state -> state.copy(
            perFileImageSettings = state.perFileImageSettings - uri.toString(),
            perFileAudioSettings = state.perFileAudioSettings - uri.toString()
        ) }
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
                        messageFor(
                            R.string.vm_files_added_skipped,
                            quantityFor(R.plurals.vm_matching_added, filtered.size, filtered.size),
                            quantityFor(R.plurals.vm_media_skipped, rejected, rejected)
                        )
                    } else {
                        quantityFor(R.plurals.vm_files_added, filtered.size, filtered.size)
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
                        quantityFor(R.plurals.vm_scan_added, added, added, mediaTypeLabel(mediaType))
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

    fun renameSelectedFile(uri: Uri, newBaseName: String) {
        val requested = newBaseName.trim()
        if (requested.isEmpty() || requested == "." || requested == ".." ||
            requested.any { it == '/' || it == '\\' || it == ':' || it == '*' || it == '?' || it == '"' || it == '<' || it == '>' || it == '|' || it.code < 32 }) {
            _uiState.update { it.copy(message = "Invalid filename") }
            return
        }
        _uiState.update { state ->
            val original = state.selectedFiles.firstOrNull { it.uri == uri }
                ?: return@update state
            val extension = original.details.fileName.substringAfterLast('.', "")
            val outputName = if (extension.isBlank()) requested else "$requested.$extension"
            if (state.selectedFiles.any { it.uri != uri &&
                    it.details.fileName.equals(outputName, ignoreCase = true) &&
                    it.relativeFolderPath == original.relativeFolderPath }) {
                state.copy(message = "Another selected file already uses this name")
            } else {
                state.copy(selectedFiles = state.selectedFiles.map { file ->
                    if (file.uri == uri) file.copy(details = file.details.copy(fileName = outputName))
                    else file
                })
            }
        }
    }

    fun removeSelectedFile(file: SelectedMediaFile) {
        _uiState.update { current ->
            current.copy(
                selectedFiles = current.selectedFiles.filter { it.uri != file.uri },
                perFileVideoSettings = current.perFileVideoSettings - file.uri.toString(),
                perFileImageSettings = current.perFileImageSettings - file.uri.toString(),
                perFileAudioSettings = current.perFileAudioSettings - file.uri.toString()
            )
        }
    }

    fun clearSelectedFiles() {
        _uiState.update { it.copy(selectedFiles = emptyList(), perFileVideoSettings = emptyMap(),
            perFileImageSettings = emptyMap(), perFileAudioSettings = emptyMap()) }
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
        (Media3OutputPolicy.rejectionReason(mediaType, state.targetFormat)
            ?: Media3DeviceCapabilities.unsupportedReason(mediaType, state.targetFormat))?.let { reason ->
            _uiState.update { it.copy(message = reason) }
            return
        }
        // Resolve and validate a separate immutable configuration per source
        // file before submitting a batch. Nothing is read from UI state later.
        val videoSnapshots = if (mediaType == MediaType.VIDEO) {
            state.selectedFiles.associate { file ->
                val config = (state.perFileVideoSettings[file.uri.toString()]
                    ?: state.videoSettings).copy(format = state.targetFormat)
                file.uri.toString() to config
            }
        } else emptyMap()

        val imageSnapshots = if (mediaType == MediaType.IMAGE) {
            state.selectedFiles.associate { file ->
                val value = (state.perFileImageSettings[file.uri.toString()]
                    ?: state.imageSettings).copy(format = state.targetFormat)
                file.uri.toString() to value
            }
        } else emptyMap()
        val audioSnapshots = if (mediaType == MediaType.AUDIO) {
            state.selectedFiles.associate { file ->
                val value = (state.perFileAudioSettings[file.uri.toString()]
                    ?: state.audioSettings).copy(format = state.targetFormat)
                file.uri.toString() to value
            }
        } else emptyMap()

        imageSnapshots.values.forEach { config ->
            ImageSettingsValidator.validate(config)?.let { error ->
                _uiState.update { it.copy(message = error) }
                return
            }
        }
        audioSnapshots.values.forEach { config ->
            (AudioSettingsValidator.validate(config)
                ?: Media3OutputPolicy.unsupportedAudioSettings(config))?.let { error ->
                _uiState.update { it.copy(message = error) }
                return
            }
        }

        for (config in videoSnapshots.values) {
            val error = VideoSettingsValidator.validate(config)
                ?: Media3OutputPolicy.unsupportedVideoSettings(config)
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
                val resolvedVideoSnapshots = videoSnapshots.toMutableMap()
                if (mediaType == MediaType.VIDEO) {
                    // Re-evaluate with the same planner as preview and store
                    // explicit output dimensions in each queued job snapshot.
                    for (file in state.selectedFiles) {
                        val key = file.uri.toString()
                        val advanced = videoSnapshots[key] ?: continue
                        if (advanced.isRemux) continue
                        val profile = MediaProbe.probeMedia(context, file.uri)
                        val dimensions = AdaptiveResolutionPolicy.displaySize(
                            profile.videoWidth.takeIf { it > 0 } ?: file.details.width,
                            profile.videoHeight.takeIf { it > 0 } ?: file.details.height,
                            profile.videoRotation
                        )
                        val mime = VideoEncoderCatalog.mimeType(state.targetFormat, advanced.videoCodec)
                        if (advanced.usesAutoResolution) {
                            if (dimensions == null) {
                                _uiState.update { it.copy(message =
                                    "${file.details.fileName}: cannot determine source resolution.") }
                                return@launch
                            }
                            val chosen = withContext(Dispatchers.Default) {
                                VideoEncoderCatalog.bestAutoResolution(
                                    mime, advanced.encoder, dimensions, profile.frameRate
                                )
                            }.size
                            if (chosen == null) {
                                _uiState.update { it.copy(message =
                                    "${file.details.fileName}: no compatible $mime resolution reported by device.") }
                                return@launch
                            }
                            resolvedVideoSnapshots[key] = if (chosen == dimensions) advanced
                                else advanced.copy(width = chosen.width, height = chosen.height)
                        } else {
                            val reason = withContext(Dispatchers.Default) {
                                VideoEncoderCatalog.incompatibilityReason(
                                    mime, advanced.encoder,
                                    advanced.width ?: dimensions?.width ?: 0,
                                    advanced.height ?: dimensions?.height ?: 0,
                                    profile.frameRate
                                )
                            }
                            if (reason != null) {
                                _uiState.update { it.copy(message = "${file.details.fileName}: $reason") }
                                return@launch
                            }
                        }
                    }
                }
                val jobs = state.selectedFiles.map { file ->
                    ConversionJobEntity(
                        fileName = file.details.fileName,
                        inputUri = file.uri.toString(),
                        mediaType = mediaType,
                        inputFormat = SourceFormatLabels.resolve(file.details.fileName, file.details.mimeType),
                        outputFormat = state.targetFormat,
                        presetName = state.presetName,
                        fileSize = file.details.fileSize,
                        relativeFolderPath = file.relativeFolderPath,
                        customParamsJson = when (mediaType) {
                            MediaType.VIDEO -> resolvedVideoSnapshots[file.uri.toString()]?.toJson()
                            MediaType.IMAGE -> imageSnapshots[file.uri.toString()]?.toJson()
                            MediaType.AUDIO -> audioSnapshots[file.uri.toString()]?.toJson()
                            MediaType.DOWNLOAD -> null
                        },
                    )
                }
                repository.addJobs(jobs)
                MediaConversionService.startService(context)
                _uiState.update {
                    it.copy(
                        selectedFiles = emptyList(),
                        perFileVideoSettings = emptyMap(),
                perFileImageSettings = emptyMap(),
                perFileAudioSettings = emptyMap(),
                        isEditorOpen = false,
                        message = quantityFor(R.plurals.vm_jobs_queued, jobs.size, jobs.size)
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

    private fun quantityFor(@PluralsRes resId: Int, quantity: Int, vararg arguments: Any): String =
        getApplication<Application>().resources.getQuantityString(resId, quantity, *arguments)

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
