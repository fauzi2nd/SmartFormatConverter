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
    var widthDraft by remember(settings.width) { mutableStateOf(settings.width?.toString().orEmpty()) }
    var heightDraft by remember(settings.height) { mutableStateOf(settings.height?.toString().orEmpty()) }
    var resolutionOpen by remember { mutableStateOf(false) }
    var customDimensions by remember { mutableStateOf(false) }
    var modeOpen by remember { mutableStateOf(false) }
    var backgroundOpen by remember { mutableStateOf(false) }
    var encoderInfoOpen by remember { mutableStateOf(false) }

    fun commit(next: ImageAdvancedSettings) {
        if (ImageSettingsValidator.validate(next) == null) onApply(next.copy(preset = "Custom"))
    }
    val resolution = if (customDimensions && settings.width != null && settings.height != null) "Custom" else when {
        settings.width == null || settings.height == null -> "Original"
        settings.width == 1920 && settings.height == 1080 -> "1080p FHD"
        settings.width == 1280 && settings.height == 720 -> "720p HD"
        settings.width == 512 && settings.height == 512 -> "512 × 512"
        else -> "Custom"
    }
    val mode = when {
        !settings.keepAspectRatio -> "Stretch"
        settings.cropToExactRatio -> "Fill & Crop"
        else -> "Fit"
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.setting_quality_compression),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        when (settings.format) {
            "jpg", "jpeg", "heic", "avif" -> {
                Text("Quality: ${settings.jpegQuality}", style = MaterialTheme.typography.bodyMedium)
                Slider(value = settings.jpegQuality.toFloat(), valueRange = 1f..100f,
                    onValueChange = { commit(settings.copy(jpegQuality = it.roundToInt())) })
            }
            "webp" -> {
                val losslessSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                MediaSwitch("Lossless WebP", settings.webpLossless, enabled = losslessSupported) {
                    commit(settings.copy(webpLossless = it))
                }
                if (!settings.webpLossless) {
                    Text("Quality: ${settings.webpQuality}", style = MaterialTheme.typography.bodyMedium)
                    Slider(value = settings.webpQuality.toFloat(), valueRange = 1f..100f,
                        onValueChange = { commit(settings.copy(webpQuality = it.roundToInt())) })
                }
            }
            "png" -> Text("Lossless • Original pixel quality",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            "gif" -> Text("Single-frame GIF • maximum 256 colors",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (settings.format in setOf("jpg", "jpeg")) {
            TextButton(onClick = { encoderInfoOpen = !encoderInfoOpen }) {
                Text(if (encoderInfoOpen) "Hide encoder information" else "ⓘ JPEG encoder information")
            }
            if (encoderInfoOpen) Text("Android selects JPEG chroma subsampling. Exact 4:4:4 output is not guaranteed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            MediaSwitch("Preserve EXIF", settings.preserveMetadata) {
                commit(settings.copy(preserveMetadata = it, preserveGps = if (it) settings.preserveGps else false))
            }
            if (settings.preserveMetadata) {
                MediaSwitch("Include GPS location", settings.preserveGps) {
                    commit(settings.copy(preserveGps = it))
                }
            }
        }
        if (settings.format in setOf("jpg", "jpeg", "heic", "gif")) {
            Box {
                OutlinedButton(onClick = { backgroundOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Background: ${settings.matteColor.replaceFirstChar { it.uppercase() }}")
                }
                DropdownMenu(expanded = backgroundOpen, onDismissRequest = { backgroundOpen = false }) {
                    listOf("white", "black").forEach { color ->
                        DropdownMenuItem(text = { Text(color.replaceFirstChar { it.uppercase() }) },
                            onClick = { backgroundOpen = false; commit(settings.copy(matteColor = color)) })
                    }
                }
            }
        }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.AspectRatio, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.setting_resize),
                style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }
        Text("Resolution", style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(onClick = { resolutionOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text(resolution, modifier = Modifier.weight(1f))
                Text("▾")
            }
            DropdownMenu(expanded = resolutionOpen, onDismissRequest = { resolutionOpen = false }) {
                listOf("Original", "1080p FHD", "720p HD", "512 × 512", "Custom").forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = {
                        resolutionOpen = false
                        customDimensions = option == "Custom"
                        val dimensions = when (option) {
                            "Original" -> null
                            "1080p FHD" -> 1920 to 1080
                            "720p HD" -> 1280 to 720
                            "512 × 512" -> 512 to 512
                            else -> (settings.width ?: 1920) to (settings.height ?: 1080)
                        }
                        widthDraft = dimensions?.first?.toString().orEmpty()
                        heightDraft = dimensions?.second?.toString().orEmpty()
                        commit(settings.copy(width = dimensions?.first, height = dimensions?.second))
                    })
                }
            }
        }
        if (resolution == "Original") {
            Text("Original dimensions preserved. No resizing applied.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("Resize mode", style = MaterialTheme.typography.labelMedium)
            Box {
                OutlinedButton(onClick = { modeOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(mode, modifier = Modifier.weight(1f))
                    Text("▾")
                }
                DropdownMenu(expanded = modeOpen, onDismissRequest = { modeOpen = false }) {
                    listOf("Fit", "Fill & Crop", "Stretch").forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = {
                            modeOpen = false
                            commit(settings.copy(keepAspectRatio = option != "Stretch",
                                cropToExactRatio = option == "Fill & Crop"))
                        })
                    }
                }
            }
            if (resolution == "Custom") {
                OutlinedTextField(value = widthDraft,
                    onValueChange = { input ->
                        widthDraft = input.filter(Char::isDigit).take(5)
                        val w = widthDraft.toIntOrNull()
                        val h = heightDraft.toIntOrNull()
                        if (w != null && h != null) commit(settings.copy(width = w, height = h))
                    }, label = { Text("Width (px)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = heightDraft,
                    onValueChange = { input ->
                        heightDraft = input.filter(Char::isDigit).take(5)
                        val w = widthDraft.toIntOrNull()
                        val h = heightDraft.toIntOrNull()
                        if (w != null && h != null) commit(settings.copy(width = w, height = h))
                    }, label = { Text("Height (px)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth())
            }
            MediaSwitch(stringResource(R.string.setting_no_upscale), settings.preventUpscale) {
                commit(settings.copy(preventUpscale = it))
            }
        }
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
