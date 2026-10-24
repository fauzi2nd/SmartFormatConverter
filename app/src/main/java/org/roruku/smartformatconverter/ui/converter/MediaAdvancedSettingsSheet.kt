package org.roruku.smartformatconverter.ui.converter

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import org.roruku.smartformatconverter.R
import org.roruku.smartformatconverter.engine.ffmpeg.AudioAdvancedSettings
import org.roruku.smartformatconverter.engine.ffmpeg.AudioSettingsValidator
import org.roruku.smartformatconverter.engine.ffmpeg.ImageAdvancedSettings
import org.roruku.smartformatconverter.engine.ffmpeg.ImageSettingsValidator
import org.roruku.smartformatconverter.ui.theme.SmartFormatConverterTheme
import kotlin.math.roundToInt

/** Image settings have an immutable snapshot per job; JPEG and WebP controls differ. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageAdvancedSettingsControl(
    settings: ImageAdvancedSettings,
    onApply: (ImageAdvancedSettings) -> Unit,
    previewExpanded: Boolean = false
) {
    var open by remember { mutableStateOf(previewExpanded) }
    var draft by remember { mutableStateOf(settings) }
    var widthText by remember { mutableStateOf(settings.width?.toString().orEmpty()) }
    var heightText by remember { mutableStateOf(settings.height?.toString().orEmpty()) }

    OutlinedButton(
        onClick = {
            draft = settings
            widthText = settings.width?.toString().orEmpty()
            heightText = settings.height?.toString().orEmpty()
            open = true
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text(stringResource(R.string.advanced_image_title)) }
    Text(
        text = when (settings.format) {
            "jpg", "jpeg" -> stringResource(R.string.setting_jpeg_quality, settings.jpegQuality)
            "webp" -> if (settings.webpLossless) stringResource(R.string.setting_lossless_webp)
                      else stringResource(R.string.setting_webp_quality, settings.webpQuality)
            else -> stringResource(R.string.setting_png_lossless)
        },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall
    )
    if (!open) return

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.advanced_image_title),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.setting_all_media_note),
                style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            when (draft.format) {
                "jpg", "jpeg" -> {
                    Text(stringResource(R.string.setting_jpeg_quality, draft.jpegQuality))
                    Slider(
                        value = draft.jpegQuality.toFloat(),
                        onValueChange = { draft = draft.copy(jpegQuality = it.roundToInt()) },
                        valueRange = 1f..100f
                    )
                    Text(stringResource(R.string.setting_chroma_subsampling),
                        style = MaterialTheme.typography.titleSmall)
                    MediaOptionChooser(
                        selected = draft.jpegSubsampling,
                        options = listOf(
                            "444" to stringResource(R.string.setting_jpeg_chroma_444),
                            "422" to stringResource(R.string.setting_jpeg_chroma_422),
                            "420" to stringResource(R.string.setting_jpeg_chroma_420)
                        ),
                        onChange = { draft = draft.copy(jpegSubsampling = it) }
                    )
                    Text(stringResource(R.string.setting_jpeg_native_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MediaSwitch(
                        stringResource(R.string.setting_jpeg_exif),
                        draft.preserveMetadata
                    ) { draft = draft.copy(preserveMetadata = it) }
                }
                "webp" -> {
                    val supportsLosslessWebp = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    MediaSwitch(
                        stringResource(R.string.setting_lossless_webp),
                        draft.webpLossless,
                        enabled = supportsLosslessWebp
                    ) { draft = draft.copy(webpLossless = it) }
                    if (!supportsLosslessWebp) {
                        Text(stringResource(R.string.setting_webp_lossless_unsupported),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (!draft.webpLossless) {
                        Text(stringResource(R.string.setting_webp_quality, draft.webpQuality))
                        Slider(
                            value = draft.webpQuality.toFloat(),
                            onValueChange = { draft = draft.copy(webpQuality = it.roundToInt()) },
                            valueRange = 1f..100f
                        )
                    }
                }
                else -> Text(stringResource(R.string.setting_png_lossless))
            }

            HorizontalDivider()
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AspectRatio,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            stringResource(R.string.setting_resize),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val presets = listOf(
                            R.string.setting_preset_original to ("" to ""),
                            R.string.setting_preset_1080p to ("1920" to "1080"),
                            R.string.setting_preset_720p to ("1280" to "720"),
                            R.string.setting_preset_square to ("1080" to "1080"),
                            R.string.setting_preset_icon to ("512" to "512")
                        )
                        items(presets.size) { idx ->
                            val (resId, dims) = presets[idx]
                            val isSelected = widthText == dims.first && heightText == dims.second
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    widthText = dims.first
                                    heightText = dims.second
                                },
                                label = { Text(stringResource(resId), style = MaterialTheme.typography.labelSmall) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = widthText,
                            onValueChange = { widthText = it.filter(Char::isDigit).take(5) },
                            label = { Text(stringResource(R.string.setting_image_width)) },
                            suffix = { Text("px", style = MaterialTheme.typography.labelSmall) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            trailingIcon = {
                                if (widthText.isNotEmpty()) {
                                    IconButton(onClick = { widthText = "" }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Icon(
                            imageVector = if (draft.keepAspectRatio) Icons.Default.Link else Icons.Default.LinkOff,
                            contentDescription = null,
                            tint = if (draft.keepAspectRatio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(20.dp)
                        )
                        OutlinedTextField(
                            value = heightText,
                            onValueChange = { heightText = it.filter(Char::isDigit).take(5) },
                            label = { Text(stringResource(R.string.setting_image_height)) },
                            suffix = { Text("px", style = MaterialTheme.typography.labelSmall) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            trailingIcon = {
                                if (heightText.isNotEmpty()) {
                                    IconButton(onClick = { heightText = "" }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    MediaSwitch(stringResource(R.string.setting_maintain_ratio), draft.keepAspectRatio) {
                        draft = draft.copy(keepAspectRatio = it)
                    }
                    MediaSwitch(stringResource(R.string.setting_no_upscale), draft.preventUpscale) {
                        draft = draft.copy(preventUpscale = it)
                    }
                }
            }
            val proposed = draft.copy(
                width = widthText.toIntOrNull(),
                height = heightText.toIntOrNull()
            )
            val invalidEntry = (widthText.isNotBlank() && proposed.width == null) ||
                (heightText.isNotBlank() && proposed.height == null)
            val validation = when {
                invalidEntry -> stringResource(R.string.setting_custom_validation)
                proposed.format == "webp" && proposed.webpLossless &&
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.R ->
                    stringResource(R.string.setting_webp_lossless_unsupported)
                else -> ImageSettingsValidator.validate(proposed)
            }
            if (validation != null) Text(validation, color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { open = false }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = { onApply(proposed.copy(preset = "Custom")); open = false },
                    enabled = validation == null,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.apply)) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (LocalInspectionMode.current) {
        content()
    } else {
        ModalBottomSheet(onDismissRequest = { open = false }) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioAdvancedSettingsControl(
    settings: AudioAdvancedSettings,
    onApply: (AudioAdvancedSettings) -> Unit,
    previewExpanded: Boolean = false
) {
    var open by remember { mutableStateOf(previewExpanded) }
    var draft by remember { mutableStateOf(settings) }
    OutlinedButton(
        onClick = { draft = settings; open = true },
        modifier = Modifier.fillMaxWidth()
    ) { Text(stringResource(R.string.advanced_audio_title)) }
    Text(
        if (settings.format in AudioSettingsValidator.losslessFormats)
            stringResource(R.string.setting_lossless_audio)
        else stringResource(R.string.setting_audio_bitrate, settings.bitrateKbps),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall
    )
    if (!open) return
    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.advanced_audio_title),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.setting_all_media_note),
                style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            if (draft.format in AudioSettingsValidator.losslessFormats) {
                Text(stringResource(R.string.setting_lossless_audio))
            } else {
                Text(stringResource(R.string.setting_audio_bitrate, draft.bitrateKbps))
                Slider(
                    value = draft.bitrateKbps.toFloat(),
                    onValueChange = {
                        draft = draft.copy(bitrateKbps = (it.roundToInt() / 16 * 16).coerceIn(32, 320))
                    },
                    valueRange = 32f..320f
                )
            }
            Text(stringResource(R.string.setting_sample_rate))
            val sampleOptions = if (draft.format == "opus") listOf(
                "48000" to stringResource(R.string.setting_khz48)
            ) else listOf(
                "source" to stringResource(R.string.setting_original),
                "32000" to stringResource(R.string.setting_khz32),
                "44100" to stringResource(R.string.setting_khz441),
                "48000" to stringResource(R.string.setting_khz48),
                "96000" to stringResource(R.string.setting_khz96)
            )
            MediaOptionChooser(
                selected = if (draft.format == "opus") "48000"
                           else draft.sampleRate?.toString() ?: "source",
                options = sampleOptions,
                onChange = { draft = draft.copy(sampleRate = it.toIntOrNull()) }
            )
            if (draft.format == "opus") {
                Text(stringResource(R.string.setting_audio_source_rate),
                    style = MaterialTheme.typography.bodySmall)
            }
            Text(stringResource(R.string.setting_channels))
            MediaOptionChooser(
                selected = draft.channels?.toString() ?: "source",
                options = listOf(
                    "source" to stringResource(R.string.setting_original),
                    "1" to stringResource(R.string.setting_mono),
                    "2" to stringResource(R.string.setting_stereo)
                ),
                onChange = { draft = draft.copy(channels = it.toIntOrNull()) }
            )
            Text(stringResource(R.string.setting_volume_percent, draft.volumePercent))
            Slider(
                value = draft.volumePercent.toFloat(),
                onValueChange = { draft = draft.copy(volumePercent = it.roundToInt()) },
                valueRange = 0f..200f
            )
            if (draft.volumePercent > 100) {
                Text(stringResource(R.string.advanced_volume_warning),
                    color = MaterialTheme.colorScheme.error)
            }
            MediaSwitch(stringResource(R.string.setting_metadata), draft.preserveMetadata) {
                draft = draft.copy(preserveMetadata = it)
            }
            val invalid = AudioSettingsValidator.validate(draft)
            if (invalid != null) Text(invalid, color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { open = false }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = { onApply(draft.copy(preset = "Custom")); open = false },
                    enabled = invalid == null,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.apply)) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    if (LocalInspectionMode.current) content()
    else ModalBottomSheet(onDismissRequest = { open = false }) { content() }
}

@Composable
private fun MediaSwitch(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun MediaOptionChooser(
    selected: String,
    options: List<Pair<String, String>>,
    onChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { expanded = true }
        ) {
            Text(options.firstOrNull { it.first == selected }?.second ?: selected)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (code, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = { onChange(code); expanded = false }
                )
            }
        }
    }
}

@Preview(name = "Image Advanced — JPEG", showBackground = true, widthDp = 393, heightDp = 850)
@Composable
fun ImageAdvancedPreview() {
    SmartFormatConverterTheme {
        ImageAdvancedSettingsControl(ImageAdvancedSettings(), onApply = {}, previewExpanded = true)
    }
}

@Preview(name = "Image Advanced — PNG", showBackground = true, widthDp = 393, heightDp = 800)
@Composable
fun PngAdvancedPreview() {
    SmartFormatConverterTheme {
        ImageAdvancedSettingsControl(ImageAdvancedSettings(format = "png"), onApply = {}, previewExpanded = true)
    }
}

@Preview(name = "Image Advanced — WebP", showBackground = true, widthDp = 393, heightDp = 850)
@Composable
fun WebpAdvancedPreview() {
    SmartFormatConverterTheme {
        ImageAdvancedSettingsControl(ImageAdvancedSettings(format = "webp"), onApply = {}, previewExpanded = true)
    }
}

@Preview(name = "Audio Advanced — MP3", showBackground = true, widthDp = 393, heightDp = 850)
@Composable
fun AudioAdvancedPreview() {
    SmartFormatConverterTheme {
        AudioAdvancedSettingsControl(AudioAdvancedSettings(), onApply = {}, previewExpanded = true)
    }
}

@Preview(name = "Audio Advanced — FLAC", showBackground = true, widthDp = 393, heightDp = 850)
@Composable
fun FlacAdvancedPreview() {
    SmartFormatConverterTheme {
        AudioAdvancedSettingsControl(AudioAdvancedSettings(format = "flac"), onApply = {}, previewExpanded = true)
    }
}
