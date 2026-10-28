package org.roruku.smartformatconverter.ui.queue

import org.roruku.smartformatconverter.data.repository.SourceFormatLabels
import org.roruku.smartformatconverter.engine.media.VideoAdvancedSettings

import android.text.format.Formatter
import android.net.Uri
import android.provider.OpenableColumns
import android.media.MediaMetadataRetriever
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.produceState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.ImageLoader
import org.roruku.smartformatconverter.R
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
            onPauseJob = {},
            onResumeJob = {},
            onRetryJob = {},
            onRetryWithResolutionFallback = {},
            onDeleteJob = {},
        )
    } else {
        val uiState by viewModel.uiState.collectAsState()

        QueueScreenContent(
            uiState = uiState,
            onClearCompleted = { viewModel.clearCompleted() },
            onSetFilter = { viewModel.setFilter(it) },
            onCancelJob = { viewModel.cancelJob(it) },
            onPauseJob = { viewModel.pauseJob(it) },
            onResumeJob = { viewModel.resumeJob(it) },
            onRetryJob = { viewModel.retryJob(it) },
            onRetryWithResolutionFallback = { viewModel.retryWithCompatibleResolution(it) },
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
    onPauseJob: (String) -> Unit,
    onResumeJob: (String) -> Unit,
    onRetryJob: (String) -> Unit,
    onRetryWithResolutionFallback: (String) -> Unit,
    onDeleteJob: (ConversionJobEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedOutput by remember { mutableStateOf<ConversionJobEntity?>(null) }
    var selectedDetails by remember { mutableStateOf<ConversionJobEntity?>(null) }
    val thumbnailLoader = rememberCompletedThumbnailLoader()
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
                text = stringResource(R.string.queue_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            FilledTonalButton(
                onClick = onClearCompleted,
                enabled = uiState.hasCompletedJobs,
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.clear_completed),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QueueFilterChip(
                selected = uiState.selectedFilter == QueueFilterTab.ALL,
                label = stringResource(R.string.tab_all),
                count = uiState.allCount,
                onClick = { onSetFilter(QueueFilterTab.ALL) }
            )
            QueueFilterChip(
                selected = uiState.selectedFilter == QueueFilterTab.ACTIVE,
                label = stringResource(R.string.tab_active),
                count = uiState.activeCount,
                onClick = { onSetFilter(QueueFilterTab.ACTIVE) }
            )
            QueueFilterChip(
                selected = uiState.selectedFilter == QueueFilterTab.COMPLETED,
                label = stringResource(R.string.tab_completed),
                count = uiState.completedCount,
                onClick = { onSetFilter(QueueFilterTab.COMPLETED) }
            )
            QueueFilterChip(
                selected = uiState.selectedFilter == QueueFilterTab.FAILED,
                label = stringResource(R.string.tab_failed),
                count = uiState.failedCount,
                onClick = { onSetFilter(QueueFilterTab.FAILED) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.jobs.isEmpty()) {
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.HourglassEmpty,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.no_jobs_in_queue),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.jobs_appear_here),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.jobs, key = { it.id }) { job ->
                    JobCardItem(
                        job = job,
                        onCancel = { onCancelJob(job.id) },
                        onPause = { onPauseJob(job.id) },
                        onResume = { onResumeJob(job.id) },
                        onRetry = { onRetryJob(job.id) },
                        onRetryWithResolutionFallback = {
                            onRetryWithResolutionFallback(job.id)
                        },
                        onDelete = { onDeleteJob(job) },
                        onOpenResult = { selectedOutput = job },
                        onOpenDetails = { selectedDetails = job },
                        thumbnailLoader = thumbnailLoader
                    )
                }
            }
        }
    }
    selectedOutput?.let { job ->
        CompletedOutputViewer(job = job, onDismiss = { selectedOutput = null })
    }
    selectedDetails?.let { job ->
        QueueResultDetails(job = job, onDismiss = { selectedDetails = null }, onPlay = {
            selectedDetails = null
            selectedOutput = job
        })
    }
}

@Composable
fun JobCardItem(
    job: ConversionJobEntity,
    onCancel: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onRetryWithResolutionFallback: () -> Unit = {},
    onDelete: () -> Unit,
    onOpenResult: () -> Unit = {},
    onOpenDetails: () -> Unit = {},
    thumbnailLoader: ImageLoader? = null,
) {
    val context = LocalContext.current
    val icon = when (job.mediaType) {
        MediaType.VIDEO -> Icons.Default.Movie
        MediaType.IMAGE -> Icons.Default.Image
        MediaType.AUDIO -> Icons.Default.AudioFile
        MediaType.DOWNLOAD -> Icons.Default.Download
    }

    val canOpenResult = job.status == JobStatus.COMPLETED && !job.outputUri.isNullOrBlank()
    val outputName by produceState(initialValue = defaultQueueOutputName(job), job.outputUri, job.fileName, job.status) {
        value = if (canOpenResult) withContext(Dispatchers.IO) {
            readQueueOutputName(context, job)
        } else defaultQueueOutputName(job)
    }
    val alreadyAllowed = runCatching {
        job.customParamsJson?.let(VideoAdvancedSettings::fromJson)?.allowEncoderDownscale == true
    }.getOrDefault(false)
    val canOfferCompatibleRetry =
        job.status == JobStatus.FAILED && job.mediaType == MediaType.VIDEO &&
        job.outputFormat.equals("webm", ignoreCase = true) &&
        job.errorMessage.orEmpty().contains("4003") &&
        !alreadyAllowed
    var confirmCompatibleRetry by remember(job.id) { mutableStateOf(false) }
    if (confirmCompatibleRetry) {
        AlertDialog(
            onDismissRequest = { confirmCompatibleRetry = false },
            title = { Text(stringResource(R.string.queue_compatible_retry_title)) },
            text = { Text(stringResource(R.string.queue_compatible_retry_explanation)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmCompatibleRetry = false
                    onRetryWithResolutionFallback()
                }) { Text(stringResource(R.string.queue_compatible_retry_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCompatibleRetry = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canOpenResult) { onOpenDetails() },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (job.status == JobStatus.COMPLETED) {
                    CompletedOutputThumbnail(job = job, imageLoader = thumbnailLoader)
                } else {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = outputName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(
                                R.string.queue_job_format_flow,
                                SourceFormatLabels.resolve(job.fileName, job.inputFormat).uppercase(),
                                job.outputFormat.uppercase()
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        JobStatusBadge(status = job.status)
                    }
                    if (canOpenResult) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Tap for details",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    when (job.status) {
                        JobStatus.RUNNING, JobStatus.PREPARING, JobStatus.PENDING -> {
                            IconButton(onClick = onPause, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = Icons.Default.PauseCircle,
                                    contentDescription = stringResource(R.string.action_pause),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Cancel,
                                    contentDescription = stringResource(R.string.cancel),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        JobStatus.PAUSED -> {
                            IconButton(onClick = onResume, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = Icons.Default.PlayCircle,
                                    contentDescription = stringResource(R.string.action_resume),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Cancel,
                                    contentDescription = stringResource(R.string.cancel),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        JobStatus.FAILED, JobStatus.CANCELLED, JobStatus.INTERRUPTED -> {
                            IconButton(onClick = onRetry, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.action_retry),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        JobStatus.COMPLETED -> {
                            if (canOpenResult) {
                                IconButton(onClick = onOpenResult, modifier = Modifier.size(36.dp)) {
                                    Icon(Icons.Default.PlayCircle, contentDescription = "Play or preview result",
                                        modifier = Modifier.size(22.dp))
                                }
                            }
                            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.action_delete),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (canOfferCompatibleRetry) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { confirmCompatibleRetry = true }) {
                    Text(stringResource(R.string.queue_compatible_retry_button))
                }
            }

            when (job.status) {
                JobStatus.RUNNING, JobStatus.PREPARING -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    if (job.mediaType == MediaType.DOWNLOAD && job.fileSize <= 0L) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { job.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    val isIndeterminateDownload = job.mediaType == MediaType.DOWNLOAD && job.fileSize <= 0L
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val percentText = if (isIndeterminateDownload) {
                            stringResource(R.string.queue_downloading_indeterminate)
                        } else {
                            "${(job.progress * 100).toInt()}%"
                        }
                        Text(
                            text = percentText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(
                                R.string.queue_speed,
                                job.speed?.takeIf { it.isNotBlank() } ?: stringResource(R.string.queue_measuring)
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(
                                R.string.queue_eta,
                                if (isIndeterminateDownload) {
                                    stringResource(R.string.value_unknown)
                                } else {
                                    job.eta?.takeIf { it.isNotBlank() } ?: stringResource(R.string.queue_measuring)
                                }
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                JobStatus.PAUSED -> {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.pause_resume_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                JobStatus.COMPLETED -> {
                    if (job.outputFileSize > 0) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.queue_output_size, Formatter.formatShortFileSize(context, job.outputFileSize)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                JobStatus.FAILED -> {
                    if (!job.errorMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.queue_error_description, job.errorMessage),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                else -> {}
            }
        }
    }
}


private fun defaultQueueOutputName(job: ConversionJobEntity): String {
    if (job.status != JobStatus.COMPLETED || job.outputUri.isNullOrBlank()) return job.fileName
    val ext = job.outputFormat.trim().trimStart('.')
    if (ext.isBlank()) return job.fileName
    return job.fileName.substringBeforeLast('.', job.fileName) + "." + ext.lowercase()
}

private fun readQueueOutputName(context: Context, job: ConversionJobEntity): String {
    val fallback = defaultQueueOutputName(job)
    val uri = job.outputUri?.let(Uri::parse) ?: return fallback
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) cursor.getString(index)?.takeIf { it.isNotBlank() } else null
                } else null
            }
    }.getOrNull() ?: fallback
}

private data class QueueTechnicalDetails(
    val name: String,
    val durationMs: Long?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val channels: Int?,
    val width: Int?,
    val height: Int?,
    val codec: String?,
)

private fun readQueueTechnicalDetails(context: Context, job: ConversionJobEntity): QueueTechnicalDetails {
    val uri = job.outputUri?.let(Uri::parse)
    val name = readQueueOutputName(context, job)
    if (uri == null) return QueueTechnicalDetails(name, null, null, null, null, null, null, null)
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        fun number(key: Int) = retriever.extractMetadata(key)?.toIntOrNull()
        QueueTechnicalDetails(
            name, retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
            number(MediaMetadataRetriever.METADATA_KEY_BITRATE),
            number(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE),
            null,
            number(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),
            number(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT),
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
        )
    } catch (_: Exception) {
        QueueTechnicalDetails(name, null, null, null, null, null, null, null)
    } finally { runCatching { retriever.release() } }
}

@Composable
private fun QueueResultDetails(job: ConversionJobEntity, onDismiss: () -> Unit, onPlay: () -> Unit) {
    val context = LocalContext.current
    val details by produceState<QueueTechnicalDetails?>(null, job.id, job.outputUri) {
        value = withContext(Dispatchers.IO) { readQueueTechnicalDetails(context, job) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Output details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(details?.name ?: defaultQueueOutputName(job), fontWeight = FontWeight.Bold)
                Text("Source: ${job.fileName}")
                Text("Format: ${job.inputFormat.uppercase()} → ${job.outputFormat.uppercase()}")
                Text("Size: ${Formatter.formatShortFileSize(context, job.outputFileSize)}")
                details?.let { info ->
                    info.codec?.let { Text("Codec / MIME: $it") }
                    info.bitrate?.let { Text("Bitrate: ${it / 1000} kbps") }
                    info.sampleRate?.let { Text("Sample rate: $it Hz") }
                    info.channels?.let { Text("Channels: $it") }
                    info.durationMs?.let { ms ->
                        Text("Duration: ${ms / 60000}:${(ms / 1000 % 60).toString().padStart(2, '0')}")
                    }
                    if (info.width != null && info.height != null) {
                        Text("Resolution: ${info.width} × ${info.height}")
                    }
                }
                if (details == null) Text("Reading output metadata…")
            }
        },
        confirmButton = {
            TextButton(onClick = onPlay) { Text("Play / Preview") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun QueueFilterChip(
    selected: Boolean,
    label: String,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayText = if (count > 0) "$label ($count)" else label
    Surface(
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(
            if (selected) 1.5.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        ),
        modifier = modifier.animateContentSize()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                text = displayText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
private fun JobStatusBadge(status: JobStatus, modifier: Modifier = Modifier) {
    val (bgColor, textColor) = when (status) {
        JobStatus.COMPLETED ->
            Color(0xFF1E382B) to Color(0xFF81C784)
        JobStatus.RUNNING, JobStatus.PREPARING ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        JobStatus.PAUSED, JobStatus.PENDING ->
            Color(0xFF3E3219) to Color(0xFFFFD54F)
        JobStatus.FAILED, JobStatus.CANCELLED, JobStatus.INTERRUPTED ->
            Color(0xFF3E1F24) to Color(0xFFE57373)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Text(
            text = localizedJobStatus(status),
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
        )
    }
}

@Preview(name = "Queue Screen", showBackground = true, widthDp = 393, heightDp = 800)
@Composable
fun QueueScreenPreview() {
    SmartFormatConverterTheme {
        QueueScreenContent(
            uiState = QueueUiState(
                selectedFilter = QueueFilterTab.ALL,
                allCount = 3,
                activeCount = 1,
                completedCount = 1,
                failedCount = 1,
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
                        outputUri = "content://preview/completed/holiday",
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
            onPauseJob = {},
            onResumeJob = {},
            onRetryJob = {},
            onRetryWithResolutionFallback = {},
            onDeleteJob = {}
        )
    }
}

@Composable
private fun localizedJobStatus(status: JobStatus): String = stringResource(
    when (status) {
        JobStatus.PENDING -> R.string.job_status_pending
        JobStatus.PREPARING -> R.string.job_status_preparing
        JobStatus.RUNNING -> R.string.job_status_running
        JobStatus.PAUSED -> R.string.job_status_paused
        JobStatus.COMPLETED -> R.string.job_status_completed
        JobStatus.FAILED -> R.string.job_status_failed
        JobStatus.CANCELLED -> R.string.job_status_cancelled
        JobStatus.INTERRUPTED -> R.string.job_status_interrupted
    }
)
