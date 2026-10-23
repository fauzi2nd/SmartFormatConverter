package org.roruku.smartformatconverter.ui.queue

import android.text.format.Formatter
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
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.roruku.smartformatconverter.data.db.ConversionJobEntity
import org.roruku.smartformatconverter.data.db.JobStatus
import org.roruku.smartformatconverter.data.db.MediaType
import org.roruku.smartformatconverter.ui.theme.SmartFormatConverterTheme

@Composable
fun QueueScreen(
    viewModel: QueueViewModel? = if (LocalInspectionMode.current) null else viewModel(),
) {
    if (viewModel == null) {
        QueueScreenContent(
            uiState = QueueUiState(),
            onClearCompleted = {},
            onSetFilter = {},
            onCancelJob = {},
            onRetryJob = {},
            onDeleteJob = {},
        )
    } else {
        val uiState by viewModel.uiState.collectAsState()

        QueueScreenContent(
            uiState = uiState,
            onClearCompleted = { viewModel.clearCompleted() },
            onSetFilter = { viewModel.setFilter(it) },
            onCancelJob = { viewModel.cancelJob(it) },
            onRetryJob = { viewModel.retryJob(it) },
            onDeleteJob = { viewModel.deleteJob(it) },
        )
    }
}

@Composable
fun QueueScreenContent(
    uiState: QueueUiState,
    onClearCompleted: () -> Unit,
    onSetFilter: (QueueFilterTab) -> Unit,
    onCancelJob: (String) -> Unit,
    onRetryJob: (String) -> Unit,
    onDeleteJob: (ConversionJobEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Job Queue",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Button(onClick = onClearCompleted) {
                Text("Clear Completed")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        PrimaryTabRow(selectedTabIndex = uiState.selectedFilter.ordinal) {
            Tab(
                selected = uiState.selectedFilter == QueueFilterTab.ALL,
                onClick = { onSetFilter(QueueFilterTab.ALL) },
                text = { Text("All") }
            )
            Tab(
                selected = uiState.selectedFilter == QueueFilterTab.ACTIVE,
                onClick = { onSetFilter(QueueFilterTab.ACTIVE) },
                text = { Text("Active") }
            )
            Tab(
                selected = uiState.selectedFilter == QueueFilterTab.COMPLETED,
                onClick = { onSetFilter(QueueFilterTab.COMPLETED) },
                text = { Text("Completed") }
            )
            Tab(
                selected = uiState.selectedFilter == QueueFilterTab.FAILED,
                onClick = { onSetFilter(QueueFilterTab.FAILED) },
                text = { Text("Failed") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.jobs.isEmpty()) {
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
                        text = "No jobs in queue.",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Converted or downloading items will appear here.",
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
                items(uiState.jobs) { job ->
                    JobCardItem(
                        job = job,
                        onCancel = { onCancelJob(job.id) },
                        onRetry = { onRetryJob(job.id) },
                        onDelete = { onDeleteJob(job) }
                    )
                }
            }
        }
    }
}

@Composable
fun JobCardItem(
    job: ConversionJobEntity,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val icon = when (job.mediaType) {
        MediaType.VIDEO -> Icons.Default.Movie
        MediaType.IMAGE -> Icons.Default.Image
        MediaType.AUDIO -> Icons.Default.AudioFile
        MediaType.DOWNLOAD -> Icons.Default.Download
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = job.fileName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "${job.inputFormat.uppercase()} → ${job.outputFormat.uppercase()} • ${job.status.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                when (job.status) {
                    JobStatus.RUNNING, JobStatus.PREPARING, JobStatus.PENDING -> {
                        IconButton(onClick = onCancel) {
                            Icon(imageVector = Icons.Default.Cancel, contentDescription = "Cancel")
                        }
                    }
                    JobStatus.FAILED, JobStatus.CANCELLED, JobStatus.INTERRUPTED -> {
                        IconButton(onClick = onRetry) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Retry")
                        }
                        IconButton(onClick = onDelete) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                    JobStatus.COMPLETED -> {
                        IconButton(onClick = onDelete) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                    else -> {}
                }
            }

            if ((job.status == JobStatus.RUNNING) || (job.status == JobStatus.PREPARING)) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { job.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Speed: ${job.speed ?: "N/A"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "ETA: ${job.eta ?: "N/A"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (job.status == JobStatus.COMPLETED && job.outputFileSize > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Output Size: ${Formatter.formatShortFileSize(context, job.outputFileSize)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (job.status == JobStatus.FAILED && !job.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Error: ${job.errorMessage}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun QueueScreenPreview() {
    SmartFormatConverterTheme {
        QueueScreenContent(
            uiState = QueueUiState(
                selectedFilter = QueueFilterTab.ALL,
                jobs = listOf(
                    ConversionJobEntity(
                        fileName = "big_buck_bunny.mp4",
                        inputUri = "",
                        mediaType = MediaType.VIDEO,
                        inputFormat = "mp4",
                        outputFormat = "mkv",
                        status = JobStatus.RUNNING,
                        progress = 0.65f,
                        speed = "2.4x",
                        eta = "00:45"
                    ),
                    ConversionJobEntity(
                        fileName = "vacation_photo.png",
                        inputUri = "",
                        mediaType = MediaType.IMAGE,
                        inputFormat = "png",
                        outputFormat = "webp",
                        status = JobStatus.COMPLETED,
                        outputFileSize = 1024L * 800L
                    ),
                    ConversionJobEntity(
                        fileName = "audio_track.wav",
                        inputUri = "",
                        mediaType = MediaType.AUDIO,
                        inputFormat = "wav",
                        outputFormat = "mp3",
                        status = JobStatus.FAILED,
                        errorMessage = "Unsupported codec in input container"
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
