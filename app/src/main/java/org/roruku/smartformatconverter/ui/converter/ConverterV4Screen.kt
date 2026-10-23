package org.roruku.smartformatconverter.ui.converter

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.roruku.smartformatconverter.data.storage.StorageManager
import org.roruku.smartformatconverter.engine.ffmpeg.VideoAdvancedSettings

data class ConverterFormat(
    val tab: Int,
    val extension: String,
    val description: String,
)

private val formats = listOf(
    listOf(
        ConverterFormat(0, "mp4", "Universal"),
        ConverterFormat(0, "mkv", "Multi-track"),
        ConverterFormat(0, "webm", "Web"),
        ConverterFormat(0, "mov", "Editing"),
        ConverterFormat(0, "avi", "Legacy"),
    ),
    listOf(
        ConverterFormat(1, "jpg", "Foto"),
        ConverterFormat(1, "png", "Lossless"),
        ConverterFormat(1, "webp", "Compact"),
    ),
    listOf(
        ConverterFormat(2, "mp3", "Universal"),
        ConverterFormat(2, "aac", "Streaming"),
        ConverterFormat(2, "m4a", "AAC audio"),
        ConverterFormat(2, "wav", "PCM"),
        ConverterFormat(2, "flac", "Lossless"),
        ConverterFormat(2, "ogg", "Vorbis"),
        ConverterFormat(2, "opus", "Efficient"),
    )
)

/**
 * Format Factory-style catalog -> pinned media picker -> preview editor.
 *
 * The picker is part of this column, not a system modal, so it always sits
 * immediately above the Activity's persistent bottom navigation bar.
 */
@Composable
fun ConverterV4Screen(
    snackbarHostState: SnackbarHostState,
    initialTab: Int = 0,
    onEditorVisibilityChanged: (Boolean) -> Unit,
    viewModel: ConverterViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selected by remember { mutableStateOf<ConverterFormat?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(initialTab) {
        if (initialTab in 0..2) {
            viewModel.selectTab(initialTab)
            if (initialTab != 0) listState.scrollToItem(initialTab + 1)
        }
    }
    LaunchedEffect(state.isEditorOpen) {
        onEditorVisibilityChanged(state.isEditorOpen)
    }
    DisposableEffect(Unit) {
        onDispose { onEditorVisibilityChanged(false) }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    BackHandler(state.isEditorOpen || selected != null) {
        if (state.isEditorOpen) viewModel.closeEditor()
        else selected = null
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { StorageManager.persistUriPermission(context, it, writeAccess = false) }
        if (uris.isNotEmpty()) viewModel.addSelectedUris(uris)
    }
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            StorageManager.persistUriPermission(context, uri)
            viewModel.scanAndAddFolder(uri)
        }
    }

    if (state.isEditorOpen) {
        ConverterPreviewEditor(
            state = state,
            onBack = viewModel::closeEditor,
            onStart = viewModel::startConversion,
            onRemove = viewModel::removeSelectedFile,
            onUpdatePreset = viewModel::updatePreset,
            onUpdateVideoSettings = viewModel::updateVideoSettings,
            onUpdateVideoFileSettings = viewModel::updateVideoSettingsForFile,
            onClearVideoFileSettings = viewModel::clearVideoSettingsOverride
        )
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Converter",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Pilih format yang ingin dihasilkan",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(Icons.Default.SwapHoriz, contentDescription = null)
                }
            }
            item {
                FormatCategory("Video Converter", "Video & container formats", 0,
                    formats[0], selected) { selected = it }
            }
            item {
                HorizontalDivider()
                FormatCategory("Image Converter", "Foto dan gambar", 1,
                    formats[1], selected) { selected = it }
            }
            item {
                HorizontalDivider()
                FormatCategory("Audio Converter", "Musik dan rekaman", 2,
                    formats[2], selected) { selected = it }
            }
        }

        AnimatedVisibility(visible = selected != null) {
            selected?.let { format ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = 12.dp,
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = formatIcon(format.tab),
                                contentDescription = null
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Convert to ${format.extension.uppercase()}",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    "${categoryName(format.tab)} · Pilih sumber media",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(onClick = { selected = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Tutup")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.selectFormat(format.tab, format.extension)
                                    filePicker.launch(arrayOf(when (format.tab) {
                                        0 -> "video/*"
                                        1 -> "image/*"
                                        else -> "audio/*"
                                    }))
                                },
                                enabled = !state.isScanning,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.InsertDriveFile, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Files", maxLines = 1)
                            }
                            Button(
                                onClick = {
                                    viewModel.selectFormat(format.tab, format.extension)
                                    folderPicker.launch(null)
                                },
                                enabled = !state.isScanning,
                                modifier = Modifier.weight(1f)
                            ) {
                                if (state.isScanning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.width(20.dp).height(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                                }
                                Spacer(Modifier.width(6.dp))
                                Text("Folder", maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun categoryName(tab: Int): String = when (tab) {
    0 -> "Video"
    1 -> "Image"
    else -> "Audio"
}

private fun formatIcon(tab: Int) = when (tab) {
    0 -> Icons.Default.Movie
    1 -> Icons.Default.Image
    else -> Icons.Default.Audiotrack
}

@Composable
private fun FormatCategory(
    title: String,
    description: String,
    tab: Int,
    choices: List<ConverterFormat>,
    selected: ConverterFormat?,
    onSelect: (ConverterFormat) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(formatIcon(tab), contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        BoxWithConstraints {
            val cols = if (maxWidth < 340.dp) 2 else 3
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.chunked(cols).forEach { chunk ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        chunk.forEach { format ->
                            val active = selected == format
                            Surface(
                                onClick = { onSelect(format) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                color = if (active) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(
                                    if (active) 2.dp else 1.dp,
                                    if (active) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(96.dp)
                                        .padding(6.dp)
                                ) {
                                    Icon(formatIcon(tab), contentDescription = null)
                                    Spacer(Modifier.height(5.dp))
                                    Text(
                                        format.extension.uppercase(),
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1
                                    )
                                    Text(
                                        format.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        repeat(cols - chunk.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
