package org.roruku.smartformatconverter.ui.converter

import android.net.Uri
import androidx.core.net.toUri
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import org.roruku.smartformatconverter.R
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.withFrameNanos
import androidx.media3.common.VideoSize
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import org.roruku.smartformatconverter.engine.media.VideoAdvancedSettings
import org.roruku.smartformatconverter.engine.media.ImageAdvancedSettings
import org.roruku.smartformatconverter.engine.media.AudioAdvancedSettings
import org.roruku.smartformatconverter.data.storage.OutputPathPolicy
import org.roruku.smartformatconverter.data.storage.MediaDetails
import org.roruku.smartformatconverter.ui.theme.SmartFormatConverterTheme

@Composable
fun editorCategoryName(tab: Int): String = when (tab) {
    0 -> stringResource(R.string.category_video)
    1 -> stringResource(R.string.category_image)
    else -> stringResource(R.string.category_audio)
}

@Composable
fun presetDisplayName(preset: String): String = when (preset) {
    "Original Quality / Remux" -> stringResource(R.string.preset_original_remux)
    "Smart Auto" -> "Smart Auto"
    "High Quality" -> stringResource(R.string.preset_high_quality)
    "Balanced" -> stringResource(R.string.preset_balanced)
    "Small File" -> stringResource(R.string.preset_small_file)
    else -> stringResource(R.string.preset_custom)
}

@Composable
fun ZoomableImage(
    uri: Uri,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var scale by remember(uri) { mutableFloatStateOf(1f) }
    var pan by remember(uri) { mutableStateOf(Offset.Zero) }
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = remember(uri) {
                ImageRequest.Builder(context)
                    .data(uri)
                    .size(1600)
                    .crossfade(true)
                    .build()
            },
            contentDescription = stringResource(R.string.source_image_preview),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(uri) {
                    detectTransformGestures { _, offset, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        pan = if (scale > 1f) pan + offset else Offset.Zero
                    }
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = pan.x
                    translationY = pan.y
                }
        )
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes % 60, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

@Suppress("DEPRECATION")
private fun calculateVideoDisplayRatio(videoSize: VideoSize): Float {
    if (videoSize.width <= 0 || videoSize.height <= 0) return 16f / 9f
    val pixelRatio = if (videoSize.pixelWidthHeightRatio > 0f) videoSize.pixelWidthHeightRatio else 1f
    val rawRatio = (videoSize.width.toFloat() / videoSize.height.toFloat()) * pixelRatio
    val isRotated = videoSize.unappliedRotationDegrees == 90 || videoSize.unappliedRotationDegrees == 270
    val displayRatio = if (isRotated) 1f / rawRatio else rawRatio
    return displayRatio.coerceIn(9f / 21f, 21f / 9f)
}

@Composable
private fun ModernVideoPlayer(
    player: ExoPlayer,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var currentPos by remember { mutableLongStateOf(player.currentPosition.coerceAtLeast(0L)) }
    var duration by remember { mutableLongStateOf(player.duration.coerceAtLeast(0L)) }
    var isMuted by remember { mutableStateOf(player.volume == 0f) }
    var showControls by remember { mutableStateOf(true) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPos by remember { mutableFloatStateOf(0f) }
    var videoRatio by remember {
        mutableFloatStateOf(calculateVideoDisplayRatio(player.videoSize))
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(state: Int) {
                duration = player.duration.coerceAtLeast(0L)
            }
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoRatio = calculateVideoDisplayRatio(videoSize)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player, isPlaying) {
        while (isActive) {
            if (!isScrubbing) {
                currentPos = player.currentPosition.coerceAtLeast(0L)
            }
            duration = player.duration.coerceAtLeast(0L)
            delay(250)
        }
    }

    LaunchedEffect(showControls, isPlaying, isScrubbing) {
        if (showControls && isPlaying && !isScrubbing) {
            delay(3500)
            showControls = false
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(videoRatio)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black)
            .clickable { showControls = !showControls },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.player = player
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                // Center Transport Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            player.seekTo((player.currentPosition - 10000L).coerceAtLeast(0L))
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Replay10,
                            contentDescription = stringResource(R.string.player_rewind_10),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    FilledIconButton(
                        onClick = {
                            if (isPlaying) player.pause() else player.play()
                        },
                        modifier = Modifier.size(54.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) stringResource(R.string.player_pause) else stringResource(R.string.player_play),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            player.seekTo((player.currentPosition + 10000L).coerceAtMost(duration))
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Forward10,
                            contentDescription = stringResource(R.string.player_forward_10),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Bottom Scrub & Info Bar
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    val hasKnownDuration = duration > 0L
                    val displayPos = if (hasKnownDuration && isScrubbing) scrubPos.toLong() else currentPos
                    val currentVal = if (hasKnownDuration && isScrubbing) {
                        scrubPos
                    } else if (hasKnownDuration) {
                        currentPos.toFloat().coerceIn(0f, duration.toFloat())
                    } else {
                        0f
                    }
                    Slider(
                        value = currentVal,
                        onValueChange = {
                            if (hasKnownDuration) {
                                isScrubbing = true
                                scrubPos = it
                            }
                        },
                        onValueChangeFinished = {
                            if (hasKnownDuration) {
                                player.seekTo(scrubPos.toLong())
                                currentPos = scrubPos.toLong()
                                isScrubbing = false
                            }
                        },
                        enabled = hasKnownDuration,
                        valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                            disabledThumbColor = Color.Gray,
                            disabledActiveTrackColor = Color.Gray,
                            disabledInactiveTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(20.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val durationText = if (hasKnownDuration) formatDuration(duration) else "--:--"
                        Text(
                            text = "${formatDuration(displayPos)} / $durationText",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                        IconButton(
                            onClick = {
                                isMuted = !isMuted
                                player.volume = if (isMuted) 0f else 1f
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isMuted) stringResource(R.string.player_unmute) else stringResource(R.string.player_mute),
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernAudioPlayer(
    player: ExoPlayer,
    modifier: Modifier = Modifier,
    title: String? = null
) {
    var isPlaying by remember { mutableStateOf(player.isPlaying) }
    var currentPos by remember { mutableLongStateOf(player.currentPosition.coerceAtLeast(0L)) }
    var duration by remember { mutableLongStateOf(player.duration.coerceAtLeast(0L)) }
    var isMuted by remember { mutableStateOf(player.volume == 0f) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPos by remember { mutableFloatStateOf(0f) }
    var rotationAngle by remember { mutableFloatStateOf(0f) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(state: Int) {
                duration = player.duration.coerceAtLeast(0L)
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player, isPlaying) {
        while (isActive) {
            if (!isScrubbing) {
                currentPos = player.currentPosition.coerceAtLeast(0L)
            }
            duration = player.duration.coerceAtLeast(0L)
            delay(250)
        }
    }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect
        var lastFrameTime = 0L
        while (isActive) {
            withFrameNanos { frameTimeNanos ->
                if (lastFrameTime != 0L) {
                    val dtSec = (frameTimeNanos - lastFrameTime) / 1_000_000_000f
                    rotationAngle = (rotationAngle + dtSec * 90f) % 360f
                }
                lastFrameTime = frameTimeNanos
            }
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .rotate(rotationAngle),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.category_audio),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (isPlaying) "Playing…" else "Paused",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = {
                        isMuted = !isMuted
                        player.volume = if (isMuted) 0f else 1f
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isMuted) stringResource(R.string.player_unmute) else stringResource(R.string.player_mute),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            val hasKnownDuration = duration > 0L
            val displayPos = if (hasKnownDuration && isScrubbing) scrubPos.toLong() else currentPos
            val currentVal = if (hasKnownDuration && isScrubbing) {
                scrubPos
            } else if (hasKnownDuration) {
                currentPos.toFloat().coerceIn(0f, duration.toFloat())
            } else {
                0f
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = currentVal,
                    onValueChange = {
                        if (hasKnownDuration) {
                            isScrubbing = true
                            scrubPos = it
                        }
                    },
                    onValueChangeFinished = {
                        if (hasKnownDuration) {
                            player.seekTo(scrubPos.toLong())
                            currentPos = scrubPos.toLong()
                            isScrubbing = false
                        }
                    },
                    enabled = hasKnownDuration,
                    valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        disabledThumbColor = MaterialTheme.colorScheme.outlineVariant,
                        disabledActiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                        disabledInactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerLowest
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(displayPos),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (hasKnownDuration) formatDuration(duration) else "--:--",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        player.seekTo((player.currentPosition - 10000L).coerceAtLeast(0L))
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.Default.Replay10,
                        contentDescription = stringResource(R.string.player_rewind_10),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.width(16.dp))

                FilledIconButton(
                    onClick = {
                        if (isPlaying) player.pause() else player.play()
                    },
                    modifier = Modifier.size(52.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) stringResource(R.string.player_pause) else stringResource(R.string.player_play),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(Modifier.width(16.dp))

                IconButton(
                    onClick = {
                        player.seekTo((player.currentPosition + 10000L).coerceAtMost(duration))
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        Icons.Default.Forward10,
                        contentDescription = stringResource(R.string.player_forward_10),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SourcePlayer(
    uri: Uri,
    audio: Boolean,
    modifier: Modifier = Modifier,
    title: String? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = false
        }
    }
    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    if (audio) {
        ModernAudioPlayer(player = player, modifier = modifier, title = title)
    } else {
        ModernVideoPlayer(player = player, modifier = modifier)
    }
}


/** Renderer used only by Android Studio Preview; never pretends to play a file. */
@Composable
fun SourceMediaDesignPreview(tab: Int) {
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                when (tab) {
                    0 -> Icons.Default.Movie
                    1 -> Icons.Default.Image
                    else -> Icons.Default.Audiotrack
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                when (tab) {
                    0 -> stringResource(R.string.video_source_preview)
                    1 -> stringResource(R.string.image_source_preview)
                    else -> stringResource(R.string.audio_player_preview)
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                stringResource(R.string.playback_available_on_device),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Preview source media before conversion. The fixed action area is outside the
 * scrolling content. MainActivity hides main navigation while this editor is active.
 */
@Composable
fun ConverterPreviewEditor(
    state: ConverterUiState,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onRemove: (SelectedMediaFile) -> Unit,
    onUpdatePreset: (String) -> Unit,
    onUpdateVideoSettings: (VideoAdvancedSettings) -> Unit,
    onUpdateVideoFileSettings: (Uri, VideoAdvancedSettings) -> Unit,
    onClearVideoFileSettings: (Uri) -> Unit,
    onUpdateImageSettings: (ImageAdvancedSettings) -> Unit,
    onUpdateAudioSettings: (AudioAdvancedSettings) -> Unit,
    onUpdateImagePreset: (String) -> Unit,
    onUpdateAudioPreset: (String) -> Unit,
    onUpdateImageFileSettings: (Uri, ImageAdvancedSettings) -> Unit,
    onUpdateAudioFileSettings: (Uri, AudioAdvancedSettings) -> Unit,
    onClearMediaSettingsOverride: (Uri) -> Unit,
) {
    val files = state.selectedFiles
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    // Design-time preview must not instantiate decoders, players or file IO.
    val videoThumbLoader = remember(context, isPreview) {
        if (isPreview) null else ImageLoader.Builder(context).components {
            add(VideoFrameDecoder.Factory())
        }.build()
    }
    DisposableEffect(videoThumbLoader) {
        onDispose { videoThumbLoader?.shutdown() }
    }
    BackHandler(enabled = !isPreview) { onBack() }
    val pager = rememberPagerState(pageCount = { files.size })
    val coroutineScope = rememberCoroutineScope()
    var applyToAll by remember { mutableStateOf(true) }

    LaunchedEffect(files.size) {
        if (files.isNotEmpty() && pager.currentPage >= files.size) {
            pager.scrollToPage(files.lastIndex)
        }
    }

    val selectedFile = files.getOrNull(pager.currentPage)
    val resolvedVideoSettings = selectedFile?.let { file ->
        state.perFileVideoSettings[file.uri.toString()] ?: state.videoSettings
    } ?: state.videoSettings
    val resolvedImageSettings = selectedFile?.let { file ->
        state.perFileImageSettings[file.uri.toString()] ?: state.imageSettings
    } ?: state.imageSettings
    val resolvedAudioSettings = selectedFile?.let { file ->
        state.perFileAudioSettings[file.uri.toString()] ?: state.audioSettings
    } ?: state.audioSettings

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.preview_and_convert), fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.editor_category_format, editorCategoryName(state.selectedTab), state.targetFormat.uppercase()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(pluralStringResource(R.plurals.files_count_format, files.size, files.size), style = MaterialTheme.typography.labelMedium)
        }
        HorizontalDivider()

        if (files.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_media_selected))
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    HorizontalPager(
                        state = pager,
                        modifier = Modifier.fillMaxWidth(),
                        beyondViewportPageCount = 0
                    ) { page ->
                        val media = files[page]
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // One active player only; neighboring pager items
                                // have placeholders and never decode simultaneously.
                                if (page == pager.currentPage) {
                                    if (isPreview) {
                                        SourceMediaDesignPreview(state.selectedTab)
                                    } else {
                                        when (state.selectedTab) {
                                            0 -> SourcePlayer(media.uri, audio = false, title = media.details.fileName)
                                            1 -> ZoomableImage(media.uri, modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                                            else -> SourcePlayer(media.uri, audio = true, title = media.details.fileName)
                                        }
                                    }
                                } else {
                                    Box(
                                        Modifier.fillMaxWidth().aspectRatio(16f/9f),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = when(state.selectedTab) {
                                                0 -> Icons.Default.Movie
                                                1 -> Icons.Default.Image
                                                else -> Icons.Default.Audiotrack
                                            },
                                            contentDescription = null
                                        )
                                    }
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        media.details.fileName,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        buildString {
                                            append(Formatter.formatShortFileSize(
                                                context, media.details.fileSize))
                                            if (media.details.width > 0 && media.details.height > 0) {
                                                append(" · ${media.details.width}×${media.details.height}")
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onRemove(media) }) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove_from_list))
                                }
                            }
                        }
                    }
                }

                item {
                    Row(verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()) {
                        Text(
                            stringResource(R.string.media_index_format, pager.currentPage + 1, files.size),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            stringResource(R.string.swipe_preview_next_file),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(files, key = { _, f -> f.uri.toString() }) { index, file ->
                            Surface(
                                onClick = { coroutineScope.launch { pager.animateScrollToPage(index) } },
                                shape = RoundedCornerShape(10.dp),
                                color = if (pager.currentPage == index)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainer,
                                border = BorderStroke(
                                    if (pager.currentPage == index) 2.dp else 1.dp,
                                    if (pager.currentPage == index) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                            ) {
                                Column(
                                    modifier = Modifier.width(100.dp).padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    when {
                                        !isPreview && state.selectedTab == 0 && videoThumbLoader != null ->
                                            AsyncImage(
                                                model = ImageRequest.Builder(context)
                                                    .data(file.uri)
                                                    .videoFrameMillis(1000)
                                                    .size(240, 140)
                                                    .build(),
                                                imageLoader = videoThumbLoader,
                                                contentDescription = stringResource(R.string.video_thumbnail_desc),
                                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                                contentScale = ContentScale.Crop
                                            )
                                        !isPreview && state.selectedTab == 1 -> AsyncImage(
                                            model = file.uri,
                                            contentDescription = stringResource(R.string.image_thumbnail_desc),
                                            modifier = Modifier.fillMaxWidth().height(56.dp),
                                            contentScale = ContentScale.Crop
                                        )
                                        else -> Box(
                                            Modifier.fillMaxWidth().height(56.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                when (state.selectedTab) {
                                                    0 -> Icons.Default.Movie
                                                    1 -> Icons.Default.Image
                                                    else -> Icons.Default.Audiotrack
                                                },
                                                contentDescription = null
                                            )
                                        }
                                    }
                                    Text(
                                        file.details.fileName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    HorizontalDivider()
                    Text(
                        stringResource(R.string.output_settings),
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
                run {
                    item {
                        val allFilesText = stringResource(R.string.all_files)
                        val thisFileOnlyText = stringResource(R.string.this_file_only)
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            listOf(allFilesText, thisFileOnlyText).forEachIndexed { i, label ->
                                SegmentedButton(
                                    selected = applyToAll == (i == 0),
                                    onClick = { applyToAll = i == 0 },
                                    shape = SegmentedButtonDefaults.itemShape(index = i, count = 2)
                                ) { Text(label, maxLines = 1) }
                            }
                        }
                    }
                }
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.output_format), style = MaterialTheme.typography.labelMedium)
                                Text(
                                    state.targetFormat.uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text(stringResource(R.string.chosen_in_catalog), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if (state.selectedTab == 0) {
                    item {
                        Text(stringResource(R.string.preset), fontWeight = FontWeight.SemiBold)
                        // MP4 supports strict stream copy when source codecs and tracks match.
                        val presets = if (state.targetFormat == "mp4") {
                            listOf("Smart Auto", "Original Quality / Remux", "High Quality", "Balanced", "Small File", "Custom")
                        } else listOf("High Quality", "Balanced", "Small File", "Custom")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(presets.size) { i ->
                                val choice = presets[i]
                                val chosen = resolvedVideoSettings.preset == choice
                                if (chosen) Button(onClick = {}) { Text(presetDisplayName(choice)) }
                                else OutlinedButton(onClick = {
                                    if (applyToAll) {
                                        onUpdatePreset(choice)
                                    } else {
                                        selectedFile?.let {
                                             onUpdateVideoFileSettings(
                                                it.uri, resolvedVideoSettings.applyPreset(choice))
                                        }
                                    }
                                }) { Text(presetDisplayName(choice)) }
                            }
                        }
                    }
                    item {
                        Text(stringResource(R.string.video_audio_subtitle),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold)
                        VideoAdvancedSettingsControl(
                            settings = resolvedVideoSettings.copy(format = state.targetFormat),
                            onApply = { changed ->
                                if (applyToAll) onUpdateVideoSettings(changed)
                                else selectedFile?.let {
                                    onUpdateVideoFileSettings(
                                        it.uri, changed.copy(preset = "Custom"))
                                }
                            }
                        )
                        if (!applyToAll && selectedFile != null &&
                            state.perFileVideoSettings.containsKey(selectedFile.uri.toString())) {
                            OutlinedButton(
                                onClick = { onClearVideoFileSettings(selectedFile.uri) }
                            ) { Text(stringResource(R.string.back_to_all_settings)) }
                        }
                    }
                } else {
                    // PNG compression is lossless; a quality preset would be misleading.
                    if (state.targetFormat != "png") {
                    item {
                        Text(stringResource(R.string.preset), fontWeight = FontWeight.SemiBold)
                        val presets = listOf("High Quality", "Balanced", "Small File", "Custom")
                        val chosenPreset = if (state.selectedTab == 1)
                            resolvedImageSettings.preset else resolvedAudioSettings.preset
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(presets.size) { i ->
                                val choice = presets[i]
                                val choose: () -> Unit = {
                                    if (state.selectedTab == 1) {
                                        if (applyToAll) onUpdateImagePreset(choice)
                                        else selectedFile?.let { file ->
                                            onUpdateImageFileSettings(file.uri,
                                                resolvedImageSettings.applyPreset(choice))
                                        }
                                    } else {
                                        if (applyToAll) onUpdateAudioPreset(choice)
                                        else selectedFile?.let { file ->
                                            onUpdateAudioFileSettings(file.uri,
                                                resolvedAudioSettings.applyPreset(choice))
                                        }
                                    }
                                }
                                if (chosenPreset == choice) Button(onClick = {}) {
                                    Text(presetDisplayName(choice))
                                } else OutlinedButton(onClick = choose) {
                                    Text(presetDisplayName(choice))
                                }
                            }
                        }
                    }
                    }
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (state.selectedTab == 1) Icons.Default.Image else Icons.Default.Audiotrack,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        if (state.selectedTab == 1) stringResource(R.string.image_settings)
                                        else stringResource(R.string.audio_settings),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (state.selectedTab == 1) {
                                    ImageAdvancedSettingsControl(
                                        settings = resolvedImageSettings.copy(format = state.targetFormat),
                                        onApply = { changed ->
                                            if (applyToAll) onUpdateImageSettings(changed)
                                            else selectedFile?.let {
                                                onUpdateImageFileSettings(it.uri, changed)
                                            }
                                        }
                                    )
                                } else {
                                    AudioAdvancedSettingsControl(
                                        settings = resolvedAudioSettings.copy(format = state.targetFormat),
                                        onApply = { changed ->
                                            if (applyToAll) onUpdateAudioSettings(changed)
                                            else selectedFile?.let {
                                                onUpdateAudioFileSettings(it.uri, changed)
                                            }
                                        }
                                    )
                                }
                                if (!applyToAll && selectedFile != null) {
                                    val hasOverride = if (state.selectedTab == 1) {
                                        state.perFileImageSettings.containsKey(selectedFile.uri.toString())
                                    } else {
                                        state.perFileAudioSettings.containsKey(selectedFile.uri.toString())
                                    }
                                    if (hasOverride) {
                                        OutlinedButton(onClick = {
                                            onClearMediaSettingsOverride(selectedFile.uri)
                                        }) {
                                            Text(stringResource(R.string.back_to_all_settings))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    val outputFolder = OutputPathPolicy.outputDirectory(
                        selectedFile?.relativeFolderPath
                    )
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(stringResource(R.string.output_location), style = MaterialTheme.typography.labelMedium)
                            Text(
                                stringResource(R.string.parent_output_location, outputFolder),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                stringResource(R.string.original_filename_preserved, state.targetFormat),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }

        Surface(
            tonalElevation = 4.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = onStart,
                enabled = files.isNotEmpty() && !state.isConverting,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.isConverting) stringResource(R.string.preparing_queue)
                    else stringResource(R.string.start_conversion_count, files.size)
                )
            }
        }
    }
}

fun previewFiles(tab: Int): List<SelectedMediaFile> {
    val filenames = when (tab) {
        0 -> listOf("Hasumi_001.mov", "Hasumi_002.mkv", "Hasumi_003.mp4")
        1 -> listOf("IMG_001.png", "IMG_002.webp", "IMG_003.heic")
        else -> listOf("Track_01.flac", "Track_02.wav", "Track_03.m4a")
    }
    val mime = when (tab) {
        0 -> "video/mp4"
        1 -> "image/jpeg"
        else -> "audio/flac"
    }
    return filenames.mapIndexed { i, filename ->
        SelectedMediaFile(
            uri = "content://preview/${tab}/${i + 1}".toUri(),
            details = MediaDetails(
                fileName = filename,
                fileSize = (i + 1) * 12L * 1024L * 1024L,
                mimeType = mime,
                width = if (tab == 0 || tab == 1) 1920 else 0,
                height = if (tab == 0 || tab == 1) 1080 else 0
            ),
            relativeFolderPath = "Hasumi"
        )
    }
}

@Composable
fun PreviewEditorSample(tab: Int) {
    SmartFormatConverterTheme {
        ConverterPreviewEditor(
            state = ConverterUiState(
                selectedTab = tab,
                selectedFiles = previewFiles(tab),
                targetFormat = when (tab) {
                    0 -> "mp4"
                    1 -> "jpg"
                    else -> "flac"
                }
            ),
            onBack = {},
            onStart = {},
            onRemove = {},
            onUpdatePreset = {},
            onUpdateVideoSettings = {},
            onUpdateVideoFileSettings = { _, _ -> },
            onClearVideoFileSettings = {},
            onUpdateImageSettings = {},
            onUpdateAudioSettings = {},
            onUpdateImagePreset = {},
            onUpdateAudioPreset = {},
            onUpdateImageFileSettings = { _, _ -> },
            onUpdateAudioFileSettings = { _, _ -> },
            onClearMediaSettingsOverride = {}
        )
    }
}

@Preview(name = "Preview & Convert - Video", showBackground = true, widthDp = 393, heightDp = 840)
@Composable
fun ConverterVideoEditorPreview() {
    PreviewEditorSample(tab = 0)
}

@Preview(name = "Preview & Convert - Image", showBackground = true, widthDp = 393, heightDp = 840)
@Composable
fun ConverterImageEditorPreview() {
    PreviewEditorSample(tab = 1)
}

@Preview(name = "Preview & Convert - Audio", showBackground = true, widthDp = 393, heightDp = 840)
@Composable
fun ConverterAudioEditorPreview() {
    PreviewEditorSample(tab = 2)
}

@Preview(name = "Preview & Convert - Empty", showBackground = true, widthDp = 393, heightDp = 840)
@Composable
fun ConverterEmptyEditorPreview() {
    SmartFormatConverterTheme {
        ConverterPreviewEditor(
            state = ConverterUiState(selectedTab = 0, selectedFiles = emptyList()),
            onBack = {},
            onStart = {},
            onRemove = {},
            onUpdatePreset = {},
            onUpdateVideoSettings = {},
            onUpdateVideoFileSettings = { _, _ -> },
            onClearVideoFileSettings = {},
            onUpdateImageSettings = {},
            onUpdateAudioSettings = {},
            onUpdateImagePreset = {},
            onUpdateAudioPreset = {},
            onUpdateImageFileSettings = { _, _ -> },
            onUpdateAudioFileSettings = { _, _ -> },
            onClearMediaSettingsOverride = {}
        )
    }
}
