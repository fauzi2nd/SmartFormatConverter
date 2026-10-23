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
import org.roruku.smartformatconverter.data.storage.StorageManager
import org.roruku.smartformatconverter.ui.theme.SmartFormatConverterTheme

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
            uiState.message?.let { msg ->
                snackbarHostState.showSnackbar(msg)
                viewModel.clearMessage()
            }
        }

        ConverterScreenContent(
            uiState = uiState,
            onSelectTab = { viewModel.selectTab(it) },
            onUpdateTargetFormat = { viewModel.updateTargetFormat(it) },
            onUpdatePreset = { viewModel.updatePreset(it) },
            onRemoveSelectedFile = { viewModel.removeSelectedFile(it) },
            onAddSelectedUris = { viewModel.addSelectedUris(it) },
            onScanAndAddFolder = { viewModel.scanAndAddFolder(it) },
            onStartConversion = { viewModel.startConversion() },
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
    onRemoveSelectedFile: (SelectedMediaFile) -> Unit,
    onAddSelectedUris: (List<Uri>) -> Unit,
    onScanAndAddFolder: (Uri) -> Unit,
    onStartConversion: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            onAddSelectedUris(uris)
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            StorageManager.persistUriPermission(context, treeUri)
            onScanAndAddFolder(treeUri)
        }
    }

    val currentMimeType = when (uiState.selectedTab) {
        0 -> "video/*"
        1 -> "image/*"
        2 -> "audio/*"
        else -> "*/*"
    }

    val availableFormats = when (uiState.selectedTab) {
        0 -> listOf("mp4", "mkv", "webm", "mov", "avi")
        1 -> listOf("jpg", "png", "webp")
        2 -> listOf("mp3", "aac", "m4a", "wav", "flac", "ogg", "opus")
        else -> listOf("mp4")
    }

    val presets = listOf("Original Quality / Remux", "High Quality", "Balanced", "Small File", "Custom")

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Media Converter",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))

        PrimaryTabRow(selectedTabIndex = uiState.selectedTab) {
            Tab(
                selected = uiState.selectedTab == 0,
                onClick = { onSelectTab(0) },
                text = { Text("Video") }
            )
            Tab(
                selected = uiState.selectedTab == 1,
                onClick = { onSelectTab(1) },
                text = { Text("Image") }
            )
            Tab(
                selected = uiState.selectedTab == 2,
                onClick = { onSelectTab(2) },
                text = { Text("Audio") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Options Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Target Format Menu
            var formatExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = formatExpanded,
                onExpandedChange = { formatExpanded = !formatExpanded },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = uiState.targetFormat.uppercase(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Format") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = formatExpanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = formatExpanded,
                    onDismissRequest = { formatExpanded = false }
                ) {
                    availableFormats.forEach { fmt ->
                        DropdownMenuItem(
                            text = { Text(fmt.uppercase()) },
                            onClick = {
                                onUpdateTargetFormat(fmt)
                                formatExpanded = false
                            }
                        )
                    }
                }
            }

            // Quality Preset Menu
            var presetExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = presetExpanded,
                onExpandedChange = { presetExpanded = !presetExpanded },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = uiState.presetName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Preset") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = presetExpanded) },
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = presetExpanded,
                    onDismissRequest = { presetExpanded = false }
                ) {
                    presets.forEach { prs ->
                        DropdownMenuItem(
                            text = { Text(prs) },
                            onClick = {
                                onUpdatePreset(prs)
                                presetExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Selected Files Section Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Selected Files (${uiState.selectedFiles.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { folderPickerLauncher.launch(null) }) {
                    Icon(imageVector = Icons.Default.Folder, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Folder")
                }
                Button(onClick = { filePickerLauncher.launch(currentMimeType) }) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Files")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.selectedFiles.isEmpty()) {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "No media files selected.",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap 'Files' or 'Folder' above to scan media to convert.",
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
                items(uiState.selectedFiles) { file ->
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
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
                                    text = Formatter.formatShortFileSize(context, file.details.fileSize),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onRemoveSelectedFile(file) }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove file"
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onStartConversion() },
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.selectedFiles.isNotEmpty()
        ) {
            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Start Conversion (${uiState.selectedFiles.size})")
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
                presetName = "Balanced",
                selectedFiles = listOf(
                    SelectedMediaFile(
                        uri = Uri.EMPTY,
                        details = MediaDetails(
                            fileName = "sample_video.mov",
                            fileSize = 1024L * 1024L * 85L,
                            mimeType = "video/quicktime"
                        )
                    ),
                    SelectedMediaFile(
                        uri = Uri.EMPTY,
                        details = MediaDetails(
                            fileName = "drone_footage.mkv",
                            fileSize = 1024L * 1024L * 320L,
                            mimeType = "video/x-matroska"
                        )
                    )
                )
            ),
            onSelectTab = {},
            onUpdateTargetFormat = {},
            onUpdatePreset = {},
            onRemoveSelectedFile = {},
            onAddSelectedUris = {},
            onScanAndAddFolder = {},
            onStartConversion = {}
        )
    }
}
