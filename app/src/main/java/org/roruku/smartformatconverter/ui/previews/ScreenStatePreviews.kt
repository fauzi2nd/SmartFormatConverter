package org.roruku.smartformatconverter.ui.previews

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import org.roruku.smartformatconverter.data.db.ConversionJobEntity
import org.roruku.smartformatconverter.data.db.JobStatus
import org.roruku.smartformatconverter.data.db.MediaType
import org.roruku.smartformatconverter.data.preferences.FilenameFormat
import org.roruku.smartformatconverter.data.preferences.UserSettings
import org.roruku.smartformatconverter.ui.downloader.DownloaderScreenContent
import org.roruku.smartformatconverter.ui.downloader.DownloaderUiState
import org.roruku.smartformatconverter.ui.home.HomeScreenContent
import org.roruku.smartformatconverter.ui.home.HomeUiState
import org.roruku.smartformatconverter.ui.queue.QueueFilterTab
import org.roruku.smartformatconverter.ui.queue.QueueScreenContent
import org.roruku.smartformatconverter.ui.queue.QueueUiState
import org.roruku.smartformatconverter.ui.settings.SettingsScreenContent
import org.roruku.smartformatconverter.ui.theme.SmartFormatConverterTheme

/**
 * Design-time UI states for every main screen outside the Converter.
 * These previews never create a ViewModel, open Room, launch SAF or download.
 *
 * Converter catalog, picker, editors and Advanced Video settings each expose
 * their own local @Preview functions beside the actual production composables.
 */

@Preview(name = "Home - First Launch", showBackground = true, widthDp = 393, heightDp = 800)
@Composable
fun HomeEmptyStatePreview() {
    SmartFormatConverterTheme {
        HomeScreenContent(
            uiState = HomeUiState(
                freeStorageBytes = 32L * 1024L * 1024L * 1024L,
                totalStorageBytes = 128L * 1024L * 1024L * 1024L
            ),
            onNavigateToFormat = { _, _ -> },
            onNavigateToDownloader = {},
            onNavigateToQueue = {}
        )
    }
}

@Preview(name = "Downloader - No URL", showBackground = true, widthDp = 393, heightDp = 800)
@Composable
fun DownloaderEmptyStatePreview() {
    SmartFormatConverterTheme {
        DownloaderScreenContent(
            uiState = DownloaderUiState(),
            onUpdateUrlInput = {},
            onExtractInfo = {},
            onSelectFormat = {},
            onStartDownload = {}
        )
    }
}

@Preview(name = "Downloader - Loading", showBackground = true, widthDp = 393, heightDp = 800)
@Composable
fun DownloaderLoadingStatePreview() {
    SmartFormatConverterTheme {
        DownloaderScreenContent(
            uiState = DownloaderUiState(
                urlInput = "https://example.com/sample.mp4",
                isLoadingInfo = true
            ),
            onUpdateUrlInput = {},
            onExtractInfo = {},
            onSelectFormat = {},
            onStartDownload = {}
        )
    }
}

@Preview(name = "Queue - Empty", showBackground = true, widthDp = 393, heightDp = 800)
@Composable
fun QueueEmptyStatePreview() {
    SmartFormatConverterTheme {
        QueueScreenContent(
            uiState = QueueUiState(),
            onClearCompleted = {},
            onSetFilter = {},
            onCancelJob = {},
            onRetryJob = {},
            onDeleteJob = {}
        )
    }
}

@Preview(name = "Queue - Active Jobs", showBackground = true, widthDp = 393, heightDp = 800)
@Composable
fun QueueActiveStatePreview() {
    SmartFormatConverterTheme {
        QueueScreenContent(
            uiState = QueueUiState(
                selectedFilter = QueueFilterTab.ACTIVE,
                jobs = listOf(
                    ConversionJobEntity(
                        id = "preview-running",
                        fileName = "Hasumi_001.mov",
                        inputUri = "content://preview/video1",
                        mediaType = MediaType.VIDEO,
                        inputFormat = "mov",
                        outputFormat = "mp4",
                        status = JobStatus.RUNNING,
                        progress = 0.64f,
                        fileSize = 120L * 1024L * 1024L
                    ),
                    ConversionJobEntity(
                        id = "preview-pending",
                        fileName = "Hasumi_002.mkv",
                        inputUri = "content://preview/video2",
                        mediaType = MediaType.VIDEO,
                        inputFormat = "mkv",
                        outputFormat = "mp4",
                        status = JobStatus.PENDING,
                        fileSize = 280L * 1024L * 1024L
                    )
                )
            ),
            onClearCompleted = {},
            onSetFilter = {},
            onCancelJob = {},
            onRetryJob = {},
            onDeleteJob = {}
        )
    }
}

@Preview(name = "Settings - First Launch", showBackground = true, widthDp = 393, heightDp = 800)
@Composable
fun SettingsDefaultStatePreview() {
    SmartFormatConverterTheme {
        SettingsScreenContent(
            settings = UserSettings(),
            onUpdateDefaultOutputUri = {},
            onSelectOutputFolder = {},
            onUpdateCustomPrefix = {},
            onUpdateFilenameFormat = {},
            onUpdatePreserveMetadata = {},
            onUpdateHardwareAcceleration = {},
            onClearCache = {},
            onShowSnackbar = {}
        )
    }
}

@Preview(name = "Settings - Custom File Naming", showBackground = true, widthDp = 393, heightDp = 800)
@Composable
fun SettingsCustomFilenamePreview() {
    SmartFormatConverterTheme {
        SettingsScreenContent(
            settings = UserSettings(
                defaultOutputUri = "content://com.android.externalstorage.documents/tree/primary%3ATorrents",
                filenameFormat = FilenameFormat.CUSTOM_PREFIX,
                customPrefix = "MyMedia_",
                preserveMetadata = true,
                hardwareAcceleration = false
            ),
            onUpdateDefaultOutputUri = {},
            onSelectOutputFolder = {},
            onUpdateCustomPrefix = {},
            onUpdateFilenameFormat = {},
            onUpdatePreserveMetadata = {},
            onUpdateHardwareAcceleration = {},
            onClearCache = {},
            onShowSnackbar = {}
        )
    }
}
