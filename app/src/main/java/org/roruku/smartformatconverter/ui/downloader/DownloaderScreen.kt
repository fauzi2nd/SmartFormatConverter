package org.roruku.smartformatconverter.ui.downloader

import android.content.ClipboardManager
import android.content.Context
import android.text.format.Formatter
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.roruku.smartformatconverter.R
import org.roruku.smartformatconverter.engine.downloader.HttpDownloadableMediaInfo
import org.roruku.smartformatconverter.engine.downloader.HttpMediaFormatOption
import org.roruku.smartformatconverter.engine.downloader.DirectMediaPolicy
import org.roruku.smartformatconverter.ui.theme.SmartFormatConverterTheme

@Composable
fun DownloaderScreen(
    snackbarHostState: SnackbarHostState,
    viewModel: DownloaderViewModel? = if (LocalInspectionMode.current) null else viewModel(),
) {
    if (viewModel == null) {
        DownloaderScreenContent(
            uiState = DownloaderUiState(),
            onUpdateUrlInput = {},
            onExtractInfo = {},
            onSelectFormat = {},
            onStartDownload = {}
        )
    } else {
        val uiState by viewModel.uiState.collectAsState()
        LaunchedEffect(uiState.message) {
            uiState.message?.let { message ->
                snackbarHostState.showSnackbar(message)
                viewModel.clearMessage()
            }
        }
        DownloaderScreenContent(
            uiState = uiState,
            onUpdateUrlInput = viewModel::updateUrlInput,
            onExtractInfo = viewModel::extractInfo,
            onSelectFormat = viewModel::selectFormat,
            onStartDownload = viewModel::startDownload
        )
    }
}

@Composable
fun DownloaderScreenContent(
    uiState: DownloaderUiState,
    onUpdateUrlInput: (String) -> Unit,
    onExtractInfo: () -> Unit,
    onSelectFormat: (HttpMediaFormatOption) -> Unit,
    onStartDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val info = uiState.mediaInfo
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(R.string.downloader_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(R.string.direct_download_help),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = uiState.urlInput,
            onValueChange = onUpdateUrlInput,
            label = { Text(stringResource(R.string.media_url)) },
            placeholder = { Text(stringResource(R.string.media_url_placeholder)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as ClipboardManager
                        val clip = clipboard.primaryClip
                        if (clip != null && clip.itemCount > 0) {
                            val text = clip.getItemAt(0).coerceToText(context).toString()
                            onUpdateUrlInput(text.trim())
                        }
                    }) {
                        Icon(Icons.Default.ContentPaste,
                            contentDescription = stringResource(R.string.action_paste_text))
                    }
                    if (uiState.urlInput.isNotEmpty()) {
                        IconButton(onClick = { onUpdateUrlInput("") }) {
                            Icon(Icons.Default.Clear,
                                contentDescription = stringResource(R.string.action_clear_text))
                        }
                    }
                }
            }
        )

        Button(
            onClick = onExtractInfo,
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.urlInput.isNotBlank() && !uiState.isLoadingInfo
        ) {
            if (uiState.isLoadingInfo) {
                CircularProgressIndicator(
                    modifier = Modifier.width(20.dp).height(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(if (uiState.isLoadingInfo)
                R.string.inspecting_url else R.string.inspect_url))
        }

        if (info == null && !uiState.isLoadingInfo) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Info, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(stringResource(R.string.downloader_pending_inspection),
                            fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.direct_download_only),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (info != null && !uiState.isLoadingInfo) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(info.title, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        stringResource(R.string.downloader_host, info.author
                            ?: stringResource(R.string.value_unknown)),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(stringResource(R.string.downloader_choose_format),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold)

                    // This list always reflects actual direct source formats; no
                    // pretend "audio extraction" or resolution choices.
                    info.availableFormats.forEach { fmt ->
                        val selected = fmt == uiState.selectedFormat
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onSelectFormat(fmt)
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selected, onClick = {
                                    onSelectFormat(fmt)
                                })
                                val icon = when {
                                    fmt.isAudioOnly -> Icons.Default.AudioFile
                                    DirectMediaPolicy.mimeTypeForExtension(fmt.extension)
                                        .startsWith("video/") -> Icons.Default.Movie
                                    else -> Icons.Default.InsertDriveFile
                                }
                                Icon(icon, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.downloader_source_format,
                                            fmt.extension.uppercase()),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(stringResource(R.string.downloader_direct_file),
                                        style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        if (fmt.fileSizeEstimate > 0)
                                            stringResource(R.string.downloader_file_size,
                                                Formatter.formatShortFileSize(
                                                    context, fmt.fileSizeEstimate))
                                        else stringResource(R.string.downloader_size_unknown),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        stringResource(R.string.downloader_network_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onStartDownload,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = uiState.selectedFormat != null &&
                            uiState.urlInput.trim() == info.url
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.start_download))
                    }
                }
            }
        }
    }
}

private val previewDirectFormat = HttpMediaFormatOption(
    formatId = "direct_mp4", extension = "mp4", resolution = "Source file",
    note = "Direct download (no transcoding)", fileSizeEstimate = 150L * 1024L * 1024L
)

@Preview(name = "Downloader — empty", showBackground = true, widthDp = 393, heightDp = 780)
@Composable
fun DownloaderEmptyPreview() {
    SmartFormatConverterTheme {
        DownloaderScreenContent(
            uiState = DownloaderUiState(),
            onUpdateUrlInput = {}, onExtractInfo = {}, onSelectFormat = {},
            onStartDownload = {}
        )
    }
}

@Preview(name = "Downloader — inspecting", showBackground = true, widthDp = 393, heightDp = 780)
@Composable
fun DownloaderInspectingPreview() {
    SmartFormatConverterTheme {
        DownloaderScreenContent(
            uiState = DownloaderUiState(
                urlInput = "https://example.com/video.mp4", isLoadingInfo = true),
            onUpdateUrlInput = {}, onExtractInfo = {}, onSelectFormat = {},
            onStartDownload = {}
        )
    }
}

@Preview(name = "Downloader — media detected", showBackground = true, widthDp = 393, heightDp = 780)
@Composable
fun DownloaderScreenPreview() {
    SmartFormatConverterTheme {
        DownloaderScreenContent(
            uiState = DownloaderUiState(
                urlInput = "https://example.com/video.mp4",
                mediaInfo = HttpDownloadableMediaInfo(
                    url = "https://example.com/video.mp4",
                    title = "Sample Online Video",
                    author = "example.com",
                    availableFormats = listOf(previewDirectFormat)
                ),
                selectedFormat = previewDirectFormat
            ),
            onUpdateUrlInput = {}, onExtractInfo = {}, onSelectFormat = {},
            onStartDownload = {}
        )
    }
}
