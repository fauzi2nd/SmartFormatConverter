package org.roruku.smartformatconverter.ui.converter

import android.os.Build
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import org.roruku.smartformatconverter.engine.media.BitrateSelection
import org.roruku.smartformatconverter.engine.media.AudioAdvancedSettings
import org.roruku.smartformatconverter.engine.media.AudioSettingsValidator
import org.roruku.smartformatconverter.engine.media.ImageAdvancedSettings
import org.roruku.smartformatconverter.engine.media.ImageSettingsValidator
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
    ) {
        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.advanced_image_title))
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        val qualitySummary = when (settings.format) {
            "jpg", "jpeg" -> stringResource(R.string.setting_jpeg_quality, settings.jpegQuality)
            "webp" -> if (settings.webpLossless) stringResource(R.string.setting_lossless_webp)
                      else stringResource(R.string.setting_webp_quality, settings.webpQuality)
            "heic", "avif" -> "Image quality: " + settings.jpegQuality + "%"
            "gif" -> "GIF: one still frame (256 colors; transparent pixels become white)"
            else -> stringResource(R.string.setting_png_lossless)
        }
        Text(
            text = qualitySummary,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        if (settings.width != null || settings.height != null) {
            val dims = "${settings.width ?: "auto"}×${settings.height ?: "auto"}"
            val crop = if (settings.cropToExactRatio) " · " + stringResource(R.string.setting_crop_to_exact) else ""
            val noUpscale = if (settings.preventUpscale) " · " + stringResource(R.string.setting_no_upscale) else ""
            Text(
                text = "${stringResource(R.string.setting_resize)}: $dims$crop$noUpscale",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
    if (!open) return

    val content: @Composable () -> Unit = {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(stringResource(R.string.advanced_image_title),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.setting_all_media_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()

            // Quality & Compression Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
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
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            stringResource(R.string.setting_quality_compression),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    when (draft.format) {
                        "jpg", "jpeg" -> {
                            Text(stringResource(R.string.setting_jpeg_quality, draft.jpegQuality),
                                style = MaterialTheme.typography.bodyMedium)
                            Slider(
                                value = draft.jpegQuality.toFloat(),
                                onValueChange = { draft = draft.copy(jpegQuality = it.roundToInt()) },
                                valueRange = 1f..100f
                            )
                            Text(stringResource(R.string.setting_chroma_subsampling),
                                style = MaterialTheme.typography.titleSmall)
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
                                Text(stringResource(R.string.setting_webp_quality, draft.webpQuality),
                                    style = MaterialTheme.typography.bodyMedium)
                                Slider(
                                    value = draft.webpQuality.toFloat(),
                                    onValueChange = { draft = draft.copy(webpQuality = it.roundToInt()) },
                                    valueRange = 1f..100f
                                )
                            }
                        }
                        "heic", "avif" -> {
                            Text("Image quality: ${draft.jpegQuality}%", style = MaterialTheme.typography.bodyMedium)
                            Slider(
                                value = draft.jpegQuality.toFloat(),
                                onValueChange = { draft = draft.copy(jpegQuality = it.roundToInt()) },
                                valueRange = 1f..100f
                            )
                            Text("HEIC/AVIF depends on a compatible device encoder. EXIF preservation is not guaranteed.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        "gif" -> Text(
                            "Still-frame GIF only: source dimensions are preserved (GIF limit: 65535 px per axis), 256 colors, white background for transparency. Very large files may exceed device memory. Animation is not preserved.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        else -> Text(stringResource(R.string.setting_png_lossless),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            // Resize & Dimensions Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
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
                                    draft = draft.copy(
                                        cropToExactRatio = dims.first.isNotEmpty() && dims.first == dims.second
                                    )
                                },
                                label = { Text(stringResource(resId), style = MaterialTheme.typography.labelSmall) },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    labelColor = MaterialTheme.colorScheme.onSurface,
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                                    selectedBorderColor = MaterialTheme.colorScheme.primary,
                                    borderWidth = if (isSelected) 1.5.dp else 1.dp
                                )
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
                    if (draft.keepAspectRatio && widthText.isNotBlank() && heightText.isNotBlank()) {
                        MediaSwitch(stringResource(R.string.setting_crop_to_exact), draft.cropToExactRatio) {
                            draft = draft.copy(cropToExactRatio = it)
                        }
                    }
                    MediaSwitch(
                        label = stringResource(R.string.setting_no_upscale),
                        checked = draft.preventUpscale,
                        subtitle = stringResource(R.string.setting_no_upscale_subtitle)
                    ) {
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
        onClick = {
            if (open) {
                open = false
            } else {
                draft = settings
                open = true
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            if (open) "Hide advanced audio settings"
            else stringResource(R.string.advanced_audio_title)
        )
    }
    Text(
        if (settings.format in AudioSettingsValidator.losslessFormats)
            stringResource(R.string.setting_lossless_audio)
        else when (settings.bitrateKbps) {
            BitrateSelection.AUTO -> stringResource(R.string.bitrate_auto)
            BitrateSelection.SOURCE -> stringResource(R.string.bitrate_source)
            else -> stringResource(R.string.setting_audio_bitrate, settings.bitrateKbps)
        },
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall
    )
    if (!open) return
    val content: @Composable () -> Unit = {
        Column(
            // Inline with Converter Preview's LazyColumn: do not introduce
            // another vertically scrollable container or a modal sheet.
            modifier = Modifier.fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(stringResource(R.string.advanced_audio_title),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.setting_all_media_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()

            // Bitrate & Format Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
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
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            stringResource(R.string.setting_audio_format_bitrate),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (draft.format in AudioSettingsValidator.losslessFormats) {
                        Text(stringResource(R.string.setting_lossless_audio),
                            style = MaterialTheme.typography.bodyMedium)
                    } else {
                        ChoiceField(
                            label = stringResource(R.string.audio_bitrate_choice),
                            value = draft.bitrateKbps.toString(),
                            options = bitrateMenuOptions(
                                draft.bitrateKbps, BitrateSelection.audioStandardsKbps
                            ),
                            onChange = { draft = draft.copy(bitrateKbps = it.toInt()) }
                        )
                        Text(
                            stringResource(R.string.bitrate_mode_explanation),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        stringResource(R.string.media3_audio_automatic_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                stringResource(R.string.media3_audio_unsupported_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val invalid = AudioSettingsValidator.validate(draft)
            if (invalid != null) Text(invalid, color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { open = false }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = { onApply(draft.copy(preset = "Custom", sampleRate = null, channels = null, volumePercent = 100, preserveMetadata = true)); open = false },
                    enabled = invalid == null,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.apply)) }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    // Show controls directly inside the Audio Settings card, consistent
    // with the inline video controls and preserving page scroll position.
    content()
}

@Composable
private fun MediaSwitch(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    subtitle: String? = null,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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

@Preview(name = "Audio Advanced — M4A", showBackground = true, widthDp = 393, heightDp = 850)
@Composable
fun AudioAdvancedPreview() {
    SmartFormatConverterTheme {
        AudioAdvancedSettingsControl(AudioAdvancedSettings(), onApply = {}, previewExpanded = true)
    }
}

@Preview(name = "Audio Advanced — M4A High Quality", showBackground = true, widthDp = 393, heightDp = 850)
@Composable
fun M4aHighQualityPreview() {
    SmartFormatConverterTheme {
        AudioAdvancedSettingsControl(AudioAdvancedSettings(format = "m4a", bitrateKbps = 320), onApply = {}, previewExpanded = true)
    }
}
