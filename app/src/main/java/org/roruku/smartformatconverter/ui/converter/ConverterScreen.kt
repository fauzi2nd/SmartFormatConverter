package org.roruku.smartformatconverter.ui.converter

import android.net.Uri
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.roruku.smartformatconverter.data.storage.MediaDetails
import org.roruku.smartformatconverter.data.storage.OutputPathPolicy
import org.roruku.smartformatconverter.data.storage.StorageManager
import org.roruku.smartformatconverter.ui.theme.SmartFormatConverterTheme
import org.roruku.smartformatconverter.engine.ffmpeg.VideoAdvancedSettings

@Composable
fun ConverterScreen(
    snackbarHostState: SnackbarHostState,
    initialTab: Int = 0,
    viewModel: ConverterViewModel? = if (LocalInspectionMode.current) null else viewModel(),
) {
    if (viewModel == null) {
        ConverterScreenContent(
            uiState = ConverterUiState(selectedTab = initialTab),
            onSelectTab = {},
            onUpdateTargetFormat = {},
            onUpdatePreset = {},
            onUpdateVideoSettings = {},
            onRemoveSelectedFile = {},
            onAddSelectedUris = {},
            onScanAndAddFolder = {},
            onStartConversion = {},
        )
    } else {
        val uiState by viewModel.uiState.collectAsState()

        LaunchedEffect(initialTab) {
            viewModel.selectTab(initialTab)
        }

        LaunchedEffect(uiState.message) {
            uiState.message?.let { message ->
                snackbarHostState.showSnackbar(message)
                viewModel.clearMessage()
            }
        }

        ConverterScreenContent(
            uiState = uiState,
            onSelectTab = viewModel::selectTab,
            onUpdateTargetFormat = viewModel::updateTargetFormat,
            onUpdatePreset = viewModel::updatePreset,
            onUpdateVideoSettings = viewModel::updateVideoSettings,
            onRemoveSelectedFile = viewModel::removeSelectedFile,
            onAddSelectedUris = viewModel::addSelectedUris,
            onScanAndAddFolder = viewModel::scanAndAddFolder,
            onStartConversion = viewModel::startConversion,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScreenContent(
    uiState: ConverterUiState,
    onSelectTab: (Int) -> Unit,
    onUpdateTargetFormat: (String) -> Unit,
    onUpdatePreset: (String) -> Unit,
    onUpdateVideoSettings: (VideoAdvancedSettings) -> Unit,
    onRemoveSelectedFile: (SelectedMediaFile) -> Unit,
    onAddSelectedUris: (List<Uri>) -> Unit,
    onScanAndAddFolder: (Uri) -> Unit,
    onStartConversion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    // OPEN_DOCUMENT returns persistable read permissions, unlike
    // GET_CONTENT, so queued jobs remain accessible after screen changes.
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { StorageManager.persistUriPermission(context, it, writeAccess = false) }
        if (uris.isNotEmpty()) onAddSelectedUris(uris)
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        treeUri?.let {
            StorageManager.persistUriPermission(context, it)
            onScanAndAddFolder(it)
        }
    }

    val currentMimeType = when (uiState.selectedTab) {
        0 -> "video/*"
        1 -> "image/*"
        else -> "audio/*"
    }
    val availableFormats = when (uiState.selectedTab) {
        0 -> listOf("mp4", "mkv", "webm", "mov", "avi")
        1 -> listOf("jpg", "png", "webp")
        else -> listOf("mp3", "aac", "m4a", "wav", "flac", "ogg", "opus")
    }
    val presets = listOf(
        "Original Quality / Remux",
        "High Quality",
        "Balanced",
        "Small File",
        "Custom"
    )

    var formatExpanded by remember { mutableStateOf(false) }
    var presetExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Media Converter",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(10.dp))

        PrimaryTabRow(selectedTabIndex = uiState.selectedTab) {
            listOf("Video", "Image", "Audio").forEachIndexed { index, label ->
                Tab(
                    selected = uiState.selectedTab == index,
                    onClick = { onSelectTab(index) },
                    text = { Text(label, maxLines = 1) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = formatExpanded,
                onExpandedChange = { formatExpanded = !formatExpanded },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = uiState.targetFormat.uppercase(),
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    label = { Text("Format") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = formatExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = formatExpanded,
                    onDismissRequest = { formatExpanded = false }
                ) {
                    availableFormats.forEach { format ->
                        DropdownMenuItem(
                            text = { Text(format.uppercase()) },
                            onClick = {
                                onUpdateTargetFormat(format)
                                formatExpanded = false
                            }
                        )
                    }
                }
            }

            if (uiState.selectedTab == 0) {
                ExposedDropdownMenuBox(
                    expanded = presetExpanded,
                    onExpandedChange = { presetExpanded = !presetExpanded },
                    modifier = Modifier.weight(1f)
                ) {
                    OutlinedTextField(
                        value = uiState.presetName,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        label = { Text("Preset") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = presetExpanded)
                        },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = presetExpanded,
                        onDismissRequest = { presetExpanded = false }
                    ) {
                        presets.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset) },
                                onClick = {
                                    onUpdatePreset(preset)
                                    presetExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (uiState.selectedTab == 0) {
            VideoAdvancedSettingsControl(
                settings = uiState.videoSettings,
                onApply = onUpdateVideoSettings
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Text(
            text = "Selected Files (${uiState.selectedFiles.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(8.dp))

        // The action buttons have their own row so they cannot squeeze
        // the title or render their labels vertically on narrow phones.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { folderPickerLauncher.launch(null) },
                modifier = Modifier.weight(1f),
                enabled = !uiState.isScanning
            ) {
                Icon(Icons.Default.Folder, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Folder", maxLines = 1)
            }
            Button(
                onClick = { filePickerLauncher.launch(arrayOf(currentMimeType)) },
                modifier = Modifier.weight(1f),
                enabled = !uiState.isScanning
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Files", maxLines = 1)
            }
        }

        Text(
            text = if (uiState.isScanning) {
                "Scanning selected batch folder…"
            } else {
                "Files → ${OutputPathPolicy.OUTPUT_FOLDER}/  •  Folder → ${OutputPathPolicy.OUTPUT_FOLDER}/<batch name>/"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 10.dp)
        )

        if (uiState.selectedFiles.isEmpty()) {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No media files selected",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Use Files for individual media or Folder for a matching-media batch.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.selectedFiles, key = { it.uri.toString() }) { file ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = file.details.fileName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                                Text(
                                    text = Formatter.formatShortFileSize(
                                        context,
                                        file.details.fileSize
                                    ),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    text = if (file.relativeFolderPath == null) {
                                        "Files • Converter/"
                                    } else {
                                        "Batch • ${OutputPathPolicy.outputDirectory(file.relativeFolderPath)}/"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            IconButton(onClick = { onRemoveSelectedFile(file) }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove file")
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onStartConversion,
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.selectedFiles.isNotEmpty() &&
                !uiState.isScanning && !uiState.isConverting
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (uiState.isConverting) {
                    "Adding jobs…"
                } else {
                    "Start Conversion (${uiState.selectedFiles.size})"
                },
                maxLines = 1
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ConverterScreenPreview() {
    SmartFormatConverterTheme {
        ConverterScreenContent(
            uiState = ConverterUiState(
                selectedTab = 0,
                targetFormat = "mp4",
                selectedFiles = listOf(
                    SelectedMediaFile(
                        uri = Uri.parse("content://preview/one"),
                        details = MediaDetails(
                            fileName = "sample_video.mov",
                            fileSize = 1024L * 1024L * 85L,
                            mimeType = "video/quicktime"
                        )
                    ),
                    SelectedMediaFile(
                        uri = Uri.parse("content://preview/two"),
                        details = MediaDetails(
                            fileName = "drone_footage.mkv",
                            fileSize = 1024L * 1024L * 320L,
                            mimeType = "video/x-matroska"
                        ),
                        relativeFolderPath = "Hasumi"
                    )
                )
            ),
            onSelectTab = {},
            onUpdateTargetFormat = {},
            onUpdatePreset = {},
            onUpdateVideoSettings = {},
            onRemoveSelectedFile = {},
            onAddSelectedUris = {},
            onScanAndAddFolder = {},
            onStartConversion = {}
        )
    }
}
