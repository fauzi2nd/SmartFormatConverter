package org.roruku.smartformatconverter.ui.converter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.roruku.smartformatconverter.engine.ffmpeg.VideoAdvancedSettings
import org.roruku.smartformatconverter.engine.ffmpeg.VideoSettingsValidator
import kotlin.math.roundToInt

/**
 * Keeps the main converter usable on narrow screens; the advanced controls
 * are edited as a draft and committed only when Apply is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoAdvancedSettingsControl(
    settings: VideoAdvancedSettings,
    onApply: (VideoAdvancedSettings) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(settings) }
    var resolutionChoice by remember { mutableStateOf("source") }
    var customWidth by remember { mutableStateOf("1920") }
    var customHeight by remember { mutableStateOf("1080") }
    var fpsChoice by remember { mutableStateOf("source") }
    var customFps by remember { mutableStateOf("29.97") }
    var videoBitrateInput by remember { mutableStateOf("8000") }
    var audioBitrateInput by remember { mutableStateOf("192") }
    var formError by remember { mutableStateOf<String?>(null) }

    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            draft = settings
            resolutionChoice = if (settings.width == null) "source" else
                "${settings.width}x${settings.height}".takeIf {
                    it in setOf("3840x2160", "2560x1440", "1920x1080", "1280x720", "854x480")
                } ?: "custom"
            customWidth = (settings.width ?: 1920).toString()
            customHeight = (settings.height ?: 1080).toString()
            fpsChoice = settings.fps?.takeIf {
                it in setOf("23.976", "24", "25", "29.97", "30", "50", "59.94", "60")
            } ?: if (settings.fps == null) "source" else "custom"
            customFps = settings.fps ?: "29.97"
            videoBitrateInput = settings.videoBitrateKbps.toString()
            audioBitrateInput = settings.audioBitrateKbps.toString()
            formError = null
            open = true
        }
    ) {
        Icon(Icons.Default.Tune, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("Advanced Video Settings", maxLines = 1)
    }

    Text(
        text = "${if (settings.width == null) "Source" else "${settings.width}×${settings.height}"} · " +
            "${settings.fps ?: "Source"} FPS · " +
            "${settings.videoCodec.uppercase()} · ${settings.rateControl.uppercase()}" +
            if (settings.rateControl == "crf") " ${settings.crf}" else " ${settings.videoBitrateKbps} kbps",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall
    )

    if (!open) return

    ModalBottomSheet(onDismissRequest = { open = false }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Advanced Video Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Settings are saved per queued job. Source means the setting is left unchanged for each input file.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()
            SectionHeader("Video")

            val remux = draft.isRemux
            if (remux) {
                Text(
                    "Remux copies media streams; changing resolution, FPS, bitrate, pixel format or volume requires re-encoding. Switch to Custom first.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                ChoiceField(
                    label = "Resolution",
                    value = resolutionChoice,
                    options = listOf(
                        "source" to "Source / original",
                        "3840x2160" to "4K UHD · 3840 × 2160",
                        "2560x1440" to "1440p · 2560 × 1440",
                        "1920x1080" to "1080p · 1920 × 1080",
                        "1280x720" to "720p · 1280 × 720",
                        "854x480" to "480p · 854 × 480",
                        "custom" to "Custom"
                    ),
                    onChange = { selection ->
                        resolutionChoice = selection
                        draft = if (selection == "source") {
                            draft.copy(width = null, height = null)
                        } else {
                            val parts = if (selection == "custom") {
                                listOf(customWidth, customHeight)
                            } else {
                                selection.split('x')
                            }
                            draft.copy(
                                width = parts.getOrNull(0)?.toIntOrNull(),
                                height = parts.getOrNull(1)?.toIntOrNull()
                            )
                        }
                    }
                )
                if (resolutionChoice == "custom") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = customWidth,
                            onValueChange = {
                                customWidth = it
                                draft = draft.copy(width = it.toIntOrNull())
                            },
                            label = { Text("Width (px)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = customHeight,
                            onValueChange = {
                                customHeight = it
                                draft = draft.copy(height = it.toIntOrNull())
                            },
                            label = { Text("Height (px)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                ToggleOption(
                    "Prevent upscaling",
                    draft.preventUpscale,
                    onChange = { draft = draft.copy(preventUpscale = it) }
                )

                ChoiceField(
                    label = "Frame rate",
                    value = fpsChoice,
                    options = listOf(
                        "source" to "Source / original",
                        "23.976" to "23.976 FPS",
                        "24" to "24 FPS",
                        "25" to "25 FPS",
                        "29.97" to "29.97 FPS",
                        "30" to "30 FPS",
                        "50" to "50 FPS",
                        "59.94" to "59.94 FPS",
                        "60" to "60 FPS",
                        "custom" to "Custom"
                    ),
                    onChange = {
                        fpsChoice = it
                        draft = draft.copy(fps = when (it) {
                            "source" -> null
                            "custom" -> customFps
                            else -> it
                        })
                    }
                )
                if (fpsChoice == "custom") {
                    OutlinedTextField(
                        value = customFps,
                        onValueChange = {
                            customFps = it
                            draft = draft.copy(fps = it)
                        },
                        label = { Text("Custom FPS (1–240)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                val videoChoices = if (draft.format == "webm") {
                    listOf(
                        "auto" to "Auto (VP9)",
                        "vp9" to "VP9",
                        "av1" to "AV1"
                    )
                } else {
                    listOf(
                        "auto" to "Auto (H.264)",
                        "h264" to "H.264 / AVC",
                        "h265" to "H.265 / HEVC",
                        "av1" to "AV1",
                        "vp9" to "VP9 (MKV only)",
                        "copy" to "Copy video stream"
                    )
                }
                ChoiceField("Video codec", draft.videoCodec, videoChoices) {
                    draft = draft.copy(videoCodec = it)
                }
                ChoiceField(
                    "Video encoder",
                    draft.encoder,
                    listOf(
                        "auto" to "Auto (software default)",
                        "software" to "Software (CPU)",
                        "hardware" to "Hardware / MediaCodec (experimental)"
                    )
                ) {
                    draft = draft.copy(
                        encoder = it,
                        rateControl = if (it == "hardware" && draft.rateControl == "crf") {
                            "vbr"
                        } else draft.rateControl
                    )
                }

                ChoiceField(
                    "Rate control",
                    draft.rateControl,
                    listOf(
                        "crf" to "CRF · constant quality",
                        "vbr" to "VBR · target bitrate",
                        "cbr" to "CBR · target/peak bitrate"
                    )
                ) { draft = draft.copy(rateControl = it) }

                if (draft.rateControl == "crf") {
                    Text("CRF: ${draft.crf}", fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = draft.crf.toFloat(),
                        onValueChange = { draft = draft.copy(crf = it.roundToInt()) },
                        valueRange = 0f..51f,
                        steps = 50
                    )
                    Text(
                        "Lower CRF = higher quality and larger output. Default 23 for H.264.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    OutlinedTextField(
                        value = videoBitrateInput,
                        onValueChange = {
                            videoBitrateInput = it
                            draft = draft.copy(videoBitrateKbps = it.toIntOrNull() ?: 0)
                        },
                        label = { Text("Video bitrate (kbps)") },
                        supportingText = { Text("8000 = 8 Mbps") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                ChoiceField(
                    "Pixel format",
                    draft.pixelFormat,
                    listOf(
                        "auto" to "Auto",
                        "yuv420p" to "YUV 4:2:0 · 8-bit",
                        "yuv420p10le" to "YUV 4:2:0 · 10-bit",
                        "yuv422p" to "YUV 4:2:2 · 8-bit",
                        "yuv444p" to "YUV 4:4:4 · 8-bit"
                    )
                ) { draft = draft.copy(pixelFormat = it) }

                if (draft.encoder == "hardware") {
                    Text(
                        "Hardware encoding requires an encoder supported by the bundled FFmpeg binary and Android device. CRF and some pixel formats are unavailable.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            HorizontalDivider()
            SectionHeader("Audio")

            if (!remux) {
                val audioChoices = if (draft.format == "webm") {
                    listOf("auto" to "Auto (Opus)", "opus" to "Opus",
                        "vorbis" to "Vorbis", "copy" to "Copy audio", "none" to "No audio")
                } else {
                    listOf(
                        "auto" to "Auto (AAC or MP3 by container)",
                        "aac" to "AAC", "mp3" to "MP3",
                        "opus" to "Opus (MKV)", "vorbis" to "Vorbis (MKV)",
                        "flac" to "FLAC (MKV)", "pcm" to "PCM (MKV/AVI)",
                        "copy" to "Copy audio", "none" to "No audio"
                    )
                }
                ChoiceField("Audio codec", draft.audioCodec, audioChoices) {
                    draft = draft.copy(audioCodec = it)
                }
                if (draft.audioCodec != "none") {
                    ChoiceField(
                        "Audio stream",
                        draft.audioStream,
                        listOf(
                            "first" to "First audio track",
                            "second" to "Second audio track",
                            "all" to "All audio tracks",
                            "none" to "No audio"
                        )
                    ) { draft = draft.copy(audioStream = it) }
                }
                if (draft.audioCodec != "none" && draft.audioCodec != "copy") {
                    OutlinedTextField(
                        value = audioBitrateInput,
                        onValueChange = {
                            audioBitrateInput = it
                            draft = draft.copy(audioBitrateKbps = it.toIntOrNull() ?: 0)
                        },
                        label = { Text("Audio bitrate (kbps)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Volume: ${draft.volumePercent}%", fontWeight = FontWeight.SemiBold)
                    Slider(
                        value = draft.volumePercent.toFloat(),
                        onValueChange = { draft = draft.copy(volumePercent = it.roundToInt()) },
                        valueRange = 0f..200f
                    )
                    if (draft.volumePercent > 100) Text(
                        "Warning: raising volume can cause clipping.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    ChoiceField(
                        "Sample rate",
                        draft.sampleRate?.toString() ?: "source",
                        listOf(
                            "source" to "Source / original",
                            "32000" to "32 kHz", "44100" to "44.1 kHz",
                            "48000" to "48 kHz", "96000" to "96 kHz"
                        )
                    ) {
                        draft = draft.copy(sampleRate = it.toIntOrNull())
                    }
                    ChoiceField(
                        "Audio channels",
                        draft.channels?.toString() ?: "source",
                        listOf(
                            "source" to "Source / original",
                            "1" to "Mono", "2" to "Stereo", "6" to "5.1"
                        )
                    ) { draft = draft.copy(channels = it.toIntOrNull()) }
                }
            } else {
                Text("Audio is copied unchanged during Remux.")
            }

            HorizontalDivider()
            SectionHeader("Other")
            ChoiceField(
                "Subtitle",
                draft.subtitleMode,
                listOf(
                    "auto" to "Auto (preserve if compatible)",
                    "none" to "Do not include subtitles",
                    "copy" to "Copy subtitles (MKV only)"
                )
            ) { draft = draft.copy(subtitleMode = it) }

            ToggleOption(
                "Keep metadata",
                draft.preserveMetadata
            ) { draft = draft.copy(preserveMetadata = it) }
            if (draft.format == "mp4") {
                ToggleOption(
                    "MP4 Fast Start",
                    draft.fastStart
                ) { draft = draft.copy(fastStart = it) }
            }

            val validation = VideoSettingsValidator.validate(draft)
            val error = formError ?: validation
            if (error != null) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { open = false },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        if (resolutionChoice == "custom" &&
                            (customWidth.toIntOrNull() == null || customHeight.toIntOrNull() == null)) {
                            formError = "Enter a valid custom resolution"
                        } else {
                            val validationError = VideoSettingsValidator.validate(draft)
                            if (validationError == null) {
                                onApply(draft)
                                open = false
                            } else formError = validationError
                        }
                    },
                    enabled = validation == null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Apply")
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun ToggleOption(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Checkbox(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceField(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = options.firstOrNull { it.first == value }?.second ?: value,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            singleLine = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { (code, title) ->
                DropdownMenuItem(
                    text = { Text(title) },
                    onClick = {
                        onChange(code)
                        expanded = false
                    }
                )
            }
        }
    }
}
