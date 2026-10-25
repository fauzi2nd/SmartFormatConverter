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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.roruku.smartformatconverter.R
import org.roruku.smartformatconverter.engine.ffmpeg.VideoAdvancedSettings
import org.roruku.smartformatconverter.engine.ffmpeg.VideoSettingsValidator
import org.roruku.smartformatconverter.ui.theme.SmartFormatConverterTheme

/**
 * Keeps the main converter usable on narrow screens; the advanced controls
 * are edited as a draft and committed only when Apply is pressed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoAdvancedSettingsControl(
    settings: VideoAdvancedSettings,
    onApply: (VideoAdvancedSettings) -> Unit,
    previewExpanded: Boolean = false
) {
    var open by remember { mutableStateOf(previewExpanded) }
    var draft by remember { mutableStateOf(settings) }
    var resolutionChoice by remember { mutableStateOf("source") }
    var customWidth by remember { mutableStateOf("1920") }
    var customHeight by remember { mutableStateOf("1080") }
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
            videoBitrateInput = settings.videoBitrateKbps.toString()
            audioBitrateInput = settings.audioBitrateKbps.toString()
            formError = null
            open = true
        }
    ) {
        Icon(Icons.Default.Tune, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.advanced_video_settings), maxLines = 1)
    }

    Text(
        text = stringResource(
            R.string.advanced_summary,
            if (settings.width == null) stringResource(R.string.source_value)
                else "${settings.width}×${settings.height}",
            stringResource(R.string.source_value),
            settings.videoCodec.uppercase(),
            "VBR",
            stringResource(R.string.advanced_rate_kbps, settings.videoBitrateKbps)
        ),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall
    )

    if (!open) return

    // Reuse the real Advanced form in design-time Preview without a window-level
    // ModalBottomSheet, which Android Studio's layout renderer cannot reliably show.
    val formContent: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(R.string.advanced_video_settings),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                stringResource(R.string.advanced_job_snapshot_explanation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()
            SectionHeader(stringResource(R.string.media_video))

            val remux = draft.isRemux
            if (remux) {
                Text(
                    stringResource(R.string.advanced_remux_explanation),
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                ChoiceField(
                    label = stringResource(R.string.video_setting_resolution),
                    value = resolutionChoice,
                    options = listOf(
                        "source" to stringResource(R.string.source_original),
                        "3840x2160" to stringResource(R.string.option_4k),
                        "2560x1440" to stringResource(R.string.option_1440p),
                        "1920x1080" to stringResource(R.string.option_1080p),
                        "1280x720" to stringResource(R.string.option_720p),
                        "854x480" to stringResource(R.string.option_480p),
                        "custom" to stringResource(R.string.preset_custom)
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
                            label = { Text(stringResource(R.string.width_px)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = customHeight,
                            onValueChange = {
                                customHeight = it
                                draft = draft.copy(height = it.toIntOrNull())
                            },
                            label = { Text(stringResource(R.string.height_px)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                ToggleOption(
                    stringResource(R.string.video_setting_prevent_upscaling),
                    draft.preventUpscale,
                    onChange = { draft = draft.copy(preventUpscale = it) }
                )

                val videoChoices = if (draft.format == "webm") listOf(
                    "auto" to stringResource(R.string.advanced_vp9),
                    "vp9" to stringResource(R.string.advanced_vp9)
                ) else listOf(
                    "auto" to stringResource(R.string.video_option_auto_h264),
                    "h264" to stringResource(R.string.video_option_h264_avc),
                    "h265" to stringResource(R.string.video_option_h265_hevc)
                )
                ChoiceField(stringResource(R.string.video_setting_video_codec), draft.videoCodec, videoChoices) {
                    draft = draft.copy(videoCodec = it)
                }

                OutlinedTextField(
                    value = videoBitrateInput,
                    onValueChange = {
                        videoBitrateInput = it
                        draft = draft.copy(
                            videoBitrateKbps = it.toIntOrNull() ?: 0,
                            rateControl = "vbr"
                        )
                    },
                    label = { Text(stringResource(R.string.video_bitrate_kbps)) },
                    supportingText = { Text(stringResource(R.string.video_bitrate_supporting)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider()
            SectionHeader(stringResource(R.string.media_audio))

            if (!remux) {
                val audioChoices = if (draft.format == "webm") listOf(
                    "auto" to stringResource(R.string.advanced_opus),
                    "opus" to stringResource(R.string.advanced_opus),
                    "none" to stringResource(R.string.audio_option_none)
                ) else listOf(
                    "auto" to stringResource(R.string.audio_option_auto_aac),
                    "aac" to stringResource(R.string.advanced_aac),
                    "none" to stringResource(R.string.audio_option_none)
                )
                ChoiceField(stringResource(R.string.video_setting_audio_codec), draft.audioCodec, audioChoices) {
                    draft = when (it) {
                        "none" -> draft.copy(audioCodec = "none", audioStream = "none")
                        else -> draft.copy(audioCodec = it, audioStream = "first")
                    }
                    formError = null
                }
                if (draft.audioCodec != "none") {
                    OutlinedTextField(
                        value = audioBitrateInput,
                        onValueChange = {
                            audioBitrateInput = it
                            draft = draft.copy(audioBitrateKbps = it.toIntOrNull() ?: 0)
                        },
                        label = { Text(stringResource(R.string.audio_bitrate_kbps)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Text(stringResource(R.string.audio_unchanged_remux))
            }

            HorizontalDivider()
            SectionHeader(stringResource(R.string.video_setting_other))

            Text(
                stringResource(R.string.media3_video_muxer_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val validation = VideoSettingsValidator.validate(draft)
            val error = formError ?: validation
            val validResolutionError = stringResource(R.string.error_valid_resolution)
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
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = {
                        if (resolutionChoice == "custom" &&
                            (customWidth.toIntOrNull() == null || customHeight.toIntOrNull() == null)) {
                            formError = validResolutionError
                        } else {
                            val validationError = VideoSettingsValidator.validate(draft)
                            if (validationError == null) {
                                onApply(draft.copy(preserveMetadata = true, fastStart = true))
                                open = false
                            } else formError = validationError
                        }
                    },
                    enabled = validation == null,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.apply))
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (LocalInspectionMode.current) {
        formContent()
    } else {
        ModalBottomSheet(onDismissRequest = { open = false }) {
            formContent()
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
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


@Preview(name = "Advanced Video - Collapsed", showBackground = true, widthDp = 393, heightDp = 240)
@Composable
fun VideoAdvancedCollapsedPreview() {
    SmartFormatConverterTheme {
        Column(Modifier.padding(16.dp)) {
            VideoAdvancedSettingsControl(
                settings = VideoAdvancedSettings(),
                onApply = {}
            )
        }
    }
}

@Preview(name = "Advanced Video - Expanded", showBackground = true, widthDp = 393, heightDp = 860)
@Composable
fun VideoAdvancedExpandedPreview() {
    SmartFormatConverterTheme {
        VideoAdvancedSettingsControl(
            settings = VideoAdvancedSettings(),
            onApply = {},
            previewExpanded = true
        )
    }
}

@Preview(name = "Advanced Video - Remux", showBackground = true, widthDp = 393, heightDp = 860)
@Composable
fun VideoAdvancedRemuxPreview() {
    SmartFormatConverterTheme {
        VideoAdvancedSettingsControl(
            settings = VideoAdvancedSettings().applyPreset("Original Quality / Remux"),
            onApply = {},
            previewExpanded = true
        )
    }
}
