package org.roruku.smartformatconverter.ui.queue

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.ClipData
import android.provider.OpenableColumns
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.roruku.smartformatconverter.R
import org.roruku.smartformatconverter.engine.downloader.DirectMediaPolicy
import org.roruku.smartformatconverter.data.db.ConversionJobEntity
import org.roruku.smartformatconverter.data.db.JobStatus
import org.roruku.smartformatconverter.data.db.MediaType
import org.roruku.smartformatconverter.ui.converter.SourcePlayer
import org.roruku.smartformatconverter.ui.converter.ZoomableImage
import org.roruku.smartformatconverter.ui.theme.SmartFormatConverterTheme

/** Classify downloaded output by its actual extension, never by DOWNLOAD itself. */
enum class CompletedMediaKind { IMAGE, VIDEO, AUDIO, UNKNOWN }

fun completedMediaKind(job: ConversionJobEntity): CompletedMediaKind {
    val extension = job.outputFormat.lowercase()
    return when (extension) {
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif" -> CompletedMediaKind.IMAGE
        "mp4", "mkv", "webm", "mov", "avi", "m4v", "3gp" -> CompletedMediaKind.VIDEO
        "mp3", "aac", "m4a", "wav", "flac", "ogg", "opus" -> CompletedMediaKind.AUDIO
        else -> when (job.mediaType) {
            MediaType.IMAGE -> CompletedMediaKind.IMAGE
            MediaType.VIDEO -> CompletedMediaKind.VIDEO
            MediaType.AUDIO -> CompletedMediaKind.AUDIO
            MediaType.DOWNLOAD -> CompletedMediaKind.UNKNOWN
        }
    }
}

/** Share one video-frame decoder across the entire Queue rather than each list item. */
@Composable
fun rememberCompletedThumbnailLoader(): ImageLoader? {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val loader = remember(context, inspection) {
        if (inspection) null else ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }.build()
    }
    DisposableEffect(loader) { onDispose { loader?.shutdown() } }
    return loader
}

@Composable
fun CompletedOutputThumbnail(
    job: ConversionJobEntity,
    imageLoader: ImageLoader?,
    modifier: Modifier = Modifier
) {
    val kind = completedMediaKind(job)
    val uri = job.outputUri?.let(Uri::parse)
    val inspection = LocalInspectionMode.current
    val context = LocalContext.current
    var failed by remember(job.outputUri) { mutableStateOf(false) }
    val artwork by produceState<ByteArray?>(null, job.outputUri, kind, inspection) {
        value = if (!inspection && kind == CompletedMediaKind.AUDIO && uri != null) {
            withContext(Dispatchers.IO) {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    retriever.embeddedPicture
                } catch (_: Exception) {
                    null
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                }
            }
        } else null
    }
    val imageModel: Any? = when (kind) {
        CompletedMediaKind.IMAGE, CompletedMediaKind.VIDEO -> uri
        CompletedMediaKind.AUDIO -> artwork
        CompletedMediaKind.UNKNOWN -> null
    }
    Box(
        modifier = modifier.size(68.dp).clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (!inspection && imageModel != null && !failed) {
            val request = remember(context, imageModel, kind) {
                ImageRequest.Builder(context).data(imageModel).size(256)
                    .apply {
                        if (kind == CompletedMediaKind.VIDEO) videoFrameMillis(0)
                    }
                    .build()
            }
            AsyncImage(
                model = request,
                imageLoader = imageLoader ?: ImageLoader(context),
                contentDescription = stringResource(R.string.queue_output_thumbnail),
                contentScale = ContentScale.Crop,
                onError = { failed = true },
                modifier = Modifier.fillMaxSize()
            )
            if (kind == CompletedMediaKind.VIDEO) {
                Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.White,
                    modifier = Modifier.size(28.dp))
            }
        } else {
            val icon = when (kind) {
                CompletedMediaKind.VIDEO -> Icons.Default.Movie
                CompletedMediaKind.AUDIO -> Icons.Default.AudioFile
                CompletedMediaKind.IMAGE -> Icons.Default.Image
                CompletedMediaKind.UNKNOWN -> Icons.Default.BrokenImage
            }
            Icon(icon, contentDescription = stringResource(R.string.queue_output_thumbnail),
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
        }
    }
}

/**
 * Full-screen in-app result viewer. Uses the saved SAF outputUri, not the input
 * URI; ExoPlayer is paused on lifecycle stop and released on dialog dismissal.
 */
@Composable
fun CompletedOutputViewer(job: ConversionJobEntity, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val inspection = LocalInspectionMode.current
    val kind = completedMediaKind(job)
    val chooserTitle = stringResource(R.string.queue_external_viewer_title)
    val shareTitle = stringResource(R.string.queue_share_output)
    val outputUri = job.outputUri?.let(Uri::parse)
    var resultName by remember(job.id, job.outputUri) { mutableStateOf(job.fileName) }
    var readState by remember(job.id, job.outputUri) { mutableStateOf<Boolean?>(null) }
    var externalError by remember(job.id) { mutableStateOf(false) }
    LaunchedEffect(job.outputUri, inspection) {
        if (inspection) {
            readState = true
        } else {
            val result = withContext(Dispatchers.IO) {
                if (outputUri == null) false to job.fileName
                else try {
                    var name = job.fileName
                    context.contentResolver.query(
                        outputUri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (column >= 0) {
                                name = cursor.getString(column)?.takeIf(String::isNotBlank) ?: name
                            }
                        }
                    }
                    val readable = context.contentResolver
                        .openFileDescriptor(outputUri, "r")?.use { true } ?: false
                    readable to name
                } catch (_: Exception) { false to job.fileName }
            }
            readState = result.first
            resultName = result.second
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.queue_result_preview),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary)
                        Text(resultName, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close,
                            contentDescription = stringResource(R.string.queue_close_preview))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center) {
                    when {
                        readState == null -> CircularProgressIndicator()
                        readState == false -> Text(
                            stringResource(R.string.queue_result_unavailable),
                            modifier = Modifier.padding(24.dp))
                        outputUri == null -> Text(stringResource(R.string.queue_result_unavailable))
                        inspection -> Text(stringResource(R.string.queue_preview_on_device))
                        kind == CompletedMediaKind.IMAGE -> ZoomableImage(
                            outputUri, modifier = Modifier.fillMaxSize()
                        )
                        kind == CompletedMediaKind.VIDEO -> SourcePlayer(outputUri, audio = false)
                        kind == CompletedMediaKind.AUDIO -> SourcePlayer(outputUri, audio = true)
                        else -> Text(stringResource(R.string.queue_preview_unsupported))
                    }
                }
                if (readState == true && outputUri != null && !inspection) {
                    OutlinedButton(
                        onClick = {
                            try {
                                val mime = DirectMediaPolicy.mimeTypeForExtension(job.outputFormat)
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = mime
                                    putExtra(Intent.EXTRA_STREAM, outputUri)
                                    clipData = ClipData.newUri(
                                        context.contentResolver, resultName, outputUri
                                    )
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(
                                    share, shareTitle
                                ))
                                externalError = false
                            } catch (_: Exception) {
                                externalError = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.queue_share_output))
                    }
                    OutlinedButton(
                        onClick = {
                            try {
                                val mime = DirectMediaPolicy.mimeTypeForExtension(job.outputFormat)
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(outputUri, mime)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(
                                    intent,
                                    chooserTitle
                                ))
                                externalError = false
                            } catch (_: ActivityNotFoundException) {
                                externalError = true
                            } catch (_: SecurityException) {
                                externalError = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(stringResource(R.string.queue_open_with_app))
                    }
                    if (externalError) {
                        Text(stringResource(R.string.queue_external_viewer_unavailable),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
                Text(
                    stringResource(R.string.queue_preview_source_output),
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Preview(name = "Completed result dialog", widthDp = 393, heightDp = 780)
@Composable
private fun CompletedOutputViewerPreview() {
    SmartFormatConverterTheme {
        CompletedOutputViewer(
            job = ConversionJobEntity(
                fileName = "holiday.mp4", inputUri = "content://example/input",
                outputUri = "content://example/output",
                outputFormat = "mp4", inputFormat = "mov",
                mediaType = MediaType.VIDEO, status = JobStatus.COMPLETED
            ),
            onDismiss = {}
        )
    }
}
