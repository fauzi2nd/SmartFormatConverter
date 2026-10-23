package org.roruku.smartformatconverter.ui.downloader

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.roruku.smartformatconverter.engine.downloader.HttpDownloadableMediaInfo
import org.roruku.smartformatconverter.engine.downloader.HttpMediaFormatOption
import org.roruku.smartformatconverter.ui.theme.SmartFormatConverterTheme

@Composable
fun DownloaderScreen(
    snackbarHostState: SnackbarHostState,
    viewModel: DownloaderViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.message) {
        uiState.message?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    DownloaderScreenContent(
        uiState = uiState,
        onUpdateUrlInput = { viewModel.updateUrlInput(it) },
        onExtractInfo = { viewModel.extractInfo() },
        onSelectFormat = { viewModel.selectFormat(it) },
        onStartDownload = { viewModel.startDownload() },
    )
}

@Composable
fun DownloaderScreenContent(
    uiState: DownloaderUiState,
    onUpdateUrlInput: (String) -> Unit,
    onExtractInfo: () -> Unit,
    onSelectFormat: (HttpMediaFormatOption) -> Unit,
    onStartDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "URL Downloader",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Download media from supported web URLs",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // URL Input Row
        OutlinedTextField(
            value = uiState.urlInput,
            onValueChange = onUpdateUrlInput,
            label = { Text("Media URL") },
            placeholder = { Text("https://...") },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(imageVector = Icons.Default.Link, contentDescription = null) },
            trailingIcon = {
                Row {
                    if (uiState.urlInput.isNotEmpty()) {
                        IconButton(onClick = { onUpdateUrlInput("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                        }
                    } else {
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipData = clipboard.primaryClip
                            if (clipData != null && clipData.itemCount > 0) {
                                val text = clipData.getItemAt(0).text?.toString() ?: ""
                                onUpdateUrlInput(text)
                            }
                        }) {
                            Icon(imageVector = Icons.Default.ContentPaste, contentDescription = "Paste")
                        }
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onExtractInfo,
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.urlInput.isNotBlank() && !uiState.isLoadingInfo
        ) {
            if (uiState.isLoadingInfo) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp).width(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Inspecting URL...")
            } else {
                Text("Inspect URL")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val info = uiState.mediaInfo
        if (info != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = info.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Source: ${info.author ?: "Unknown"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Select Format & Quality:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(info.availableFormats) { fmt ->
                            val isSelected = uiState.selectedFormat == fmt
                            OutlinedCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectFormat(fmt) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { onSelectFormat(fmt) }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "${fmt.resolution} (${fmt.extension.uppercase()})",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = fmt.note,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onStartDownload,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.selectedFormat != null
                    ) {
                        Icon(imageVector = Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Download")
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DownloaderScreenPreview() {
    val sampleFormat = HttpMediaFormatOption(
        formatId = "video_best",
        extension = "mp4",
        resolution = "1080p HD",
        note = "Source Quality Video Stream",
        fileSizeEstimate = 1024L * 1024L * 150L,
        isAudioOnly = false
    )
    SmartFormatConverterTheme {
        DownloaderScreenContent(
            uiState = DownloaderUiState(
                urlInput = "https://example.com/video.mp4",
                isLoadingInfo = false,
                mediaInfo = HttpDownloadableMediaInfo(
                    url = "https://example.com/video.mp4",
                    title = "Sample Online Video",
                    author = "Example Network",
                    availableFormats = listOf(
                        sampleFormat,
                        HttpMediaFormatOption(
                            formatId = "audio_mp3",
                            extension = "mp3",
                            resolution = "Audio Only",
                            note = "Extract MP3 Audio Stream",
                            fileSizeEstimate = 1024L * 1024L * 15L,
                            isAudioOnly = true
                        )
                    )
                ),
                selectedFormat = sampleFormat
            ),
            onUpdateUrlInput = {},
            onExtractInfo = {},
            onSelectFormat = {},
            onStartDownload = {}
        )
    }
}
