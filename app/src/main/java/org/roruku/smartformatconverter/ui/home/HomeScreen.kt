package org.roruku.smartformatconverter.ui.home

import android.text.format.Formatter
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.roruku.smartformatconverter.R
import org.roruku.smartformatconverter.data.db.ConversionJobEntity
import org.roruku.smartformatconverter.data.db.JobStatus
import org.roruku.smartformatconverter.data.db.MediaType
import org.roruku.smartformatconverter.ui.theme.PixelBlue
import org.roruku.smartformatconverter.ui.theme.PixelCyan
import org.roruku.smartformatconverter.ui.theme.PixelViolet
import org.roruku.smartformatconverter.ui.theme.SmartFormatConverterTheme

/** Home shortcuts choose an actual target format, not just a media category. */
@Composable
fun HomeScreen(
    onNavigateToFormat: (Int, String) -> Unit,
    onNavigateToDownloader: () -> Unit,
    onNavigateToQueue: () -> Unit,
    viewModel: HomeViewModel? = if (LocalInspectionMode.current) null else viewModel(),
) {
    val uiState = if (viewModel != null) {
        val state by viewModel.uiState.collectAsState()
        state
    } else {
        HomeUiState()
    }
    HomeScreenContent(uiState, onNavigateToFormat, onNavigateToDownloader, onNavigateToQueue)
}

@Composable
fun HomeScreenContent(
    uiState: HomeUiState,
    onNavigateToFormat: (Int, String) -> Unit,
    onNavigateToDownloader: () -> Unit,
    onNavigateToQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 18.dp, end = 18.dp, top = 20.dp, bottom = 28.dp
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = stringResource(R.string.app_name).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = PixelCyan,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.home_heading),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.home_choose_format),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormatShortcut(
                        format = "MP4",
                        description = stringResource(R.string.home_mp4_desc),
                        icon = Icons.Default.Movie,
                        accent = PixelCyan,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToFormat(0, "mp4") }
                    )
                    FormatShortcut(
                        format = "JPG",
                        description = stringResource(R.string.home_jpg_desc),
                        icon = Icons.Default.Image,
                        accent = PixelBlue,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToFormat(1, "jpg") }
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormatShortcut(
                        format = "MP3",
                        description = stringResource(R.string.home_mp3_desc),
                        icon = Icons.Default.AudioFile,
                        accent = PixelViolet,
                        modifier = Modifier.weight(1f),
                        onClick = { onNavigateToFormat(2, "mp3") }
                    )
                    FormatShortcut(
                        format = stringResource(R.string.media_download),
                        description = stringResource(R.string.home_download_desc),
                        icon = Icons.Default.Download,
                        accent = PixelCyan,
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToDownloader
                    )
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.home_overview),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 17.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Metric(
                            title = stringResource(R.string.stat_completed),
                            value = uiState.completedCount.toString(),
                            tint = PixelCyan,
                            modifier = Modifier.weight(1f)
                        )
                        Metric(
                            title = stringResource(R.string.stat_failed),
                            value = uiState.failedCount.toString(),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                        Metric(
                            title = stringResource(R.string.home_converted),
                            value = Formatter.formatShortFileSize(context, uiState.totalBytesConverted),
                            tint = PixelBlue,
                            modifier = Modifier.weight(1.2f)
                        )
                    }
                }
            }
        }

        item {
            StorageCard(uiState.freeStorageBytes, uiState.totalStorageBytes)
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.recent_jobs),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = onNavigateToQueue) {
                    Text(stringResource(R.string.home_view_all))
                    Spacer(Modifier.width(3.dp))
                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(17.dp))
                }
            }
        }

        if (uiState.recentJobs.isEmpty()) {
            item {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.no_recent_conversions),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            items(uiState.recentJobs.take(3), key = { it.id }) { job ->
                RecentJobItem(job)
            }
        }
    }
}

@Composable
private fun FormatShortcut(
    format: String,
    description: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.height(132.dp),
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = format,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun Metric(title: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = tint,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun StorageCard(freeBytes: Long, totalBytes: Long) {
    val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
    val fraction = if (totalBytes > 0) usedBytes.toFloat() / totalBytes.toFloat() else 0f
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Storage,
                    contentDescription = null,
                    tint = PixelCyan,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = stringResource(R.string.internal_storage),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${(fraction * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = PixelCyan,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = stringResource(R.string.storage_free, Formatter.formatShortFileSize(context, freeBytes)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Text(
                    text = stringResource(R.string.storage_total, Formatter.formatShortFileSize(context, totalBytes)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun RecentJobItem(job: ConversionJobEntity) {
    val icon = when (job.mediaType) {
        MediaType.VIDEO -> Icons.Default.Movie
        MediaType.IMAGE -> Icons.Default.Image
        MediaType.AUDIO -> Icons.Default.AudioFile
        MediaType.DOWNLOAD -> Icons.Default.Download
    }
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = PixelCyan)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = job.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${job.inputFormat.uppercase()} → ${job.outputFormat.uppercase()}  •  ${job.status.name.lowercase().replaceFirstChar { it.uppercase() }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 393)
@Composable
fun HomeScreenPreview() {
    SmartFormatConverterTheme {
        HomeScreenContent(
            uiState = HomeUiState(
                completedCount = 12,
                failedCount = 1,
                totalBytesConverted = 1024L * 1024L * 450L,
                freeStorageBytes = 1024L * 1024L * 1024L * 32L,
                totalStorageBytes = 1024L * 1024L * 1024L * 128L,
                recentJobs = listOf(
                    ConversionJobEntity(
                        fileName = "sample_video.mp4",
                        inputUri = "",
                        mediaType = MediaType.VIDEO,
                        inputFormat = "mp4",
                        outputFormat = "mkv",
                        status = JobStatus.COMPLETED
                    )
                )
            ),
            onNavigateToFormat = { _, _ -> },
            onNavigateToDownloader = {},
            onNavigateToQueue = {}
        )
    }
}
