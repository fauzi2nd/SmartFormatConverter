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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import kotlinx.coroutines.launch
import org.roruku.smartformatconverter.engine.ffmpeg.VideoAdvancedSettings
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
    "High Quality" -> stringResource(R.string.preset_high_quality)
    "Balanced" -> stringResource(R.string.preset_balanced)
    "Small File" -> stringResource(R.string.preset_small_file)
    else -> stringResource(R.string.preset_custom)
}

@Composable
fun ZoomableImage(uri: Uri) {
    val context = LocalContext.current
    var scale by remember(uri) { mutableFloatStateOf(1f) }
    var pan by remember(uri) { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
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

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SourcePlayer(uri: Uri, audio: Boolean) {
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
        Column(
            modifier = Modifier.fillMaxWidth().height(180.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Audiotrack, contentDescription = null)
            Spacer(Modifier.height(16.dp))
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx -> PlayerControlView(ctx).apply { this.player = player } },
                update = { it.player = player },
                modifier = Modifier.fillMaxWidth().height(82.dp)
            )
        }
    } else {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = true
                    this.player = player
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
        )
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
                    "${editorCategoryName(state.selectedTab)} → ${state.targetFormat.uppercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("${files.size} files", style = MaterialTheme.typography.labelMedium)
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
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // One active player only; neighboring pager items
                                // have placeholders and never decode simultaneously.
                                if (page == pager.currentPage) {
                                    if (isPreview) {
                                        SourceMediaDesignPreview(state.selectedTab)
                                    } else {
                                        when (state.selectedTab) {
                                            0 -> SourcePlayer(media.uri, audio = false)
                                            1 -> ZoomableImage(media.uri)
                                            else -> SourcePlayer(media.uri, audio = true)
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
                            "${pager.currentPage + 1} / ${files.size}",
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
                if (state.selectedTab == 0) {
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
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Description, contentDescription = null)
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
                        val presets = listOf(
                            "Original Quality / Remux", "High Quality",
                            "Balanced", "Small File", "Custom"
                        )
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
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    if (state.selectedTab == 1) stringResource(R.string.image_settings) else stringResource(R.string.audio_settings),
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    if (state.selectedTab == 1) {
                                        stringResource(R.string.image_settings_desc)
                                    } else {
                                        stringResource(R.string.audio_settings_desc)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item {
                    val outputFolder = OutputPathPolicy.outputDirectory(
                        selectedFile?.relativeFolderPath
                    )
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(stringResource(R.string.output_location), style = MaterialTheme.typography.labelMedium)
                            Text(
                                "<Folder induk>/${outputFolder}/",
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
                    else "${stringResource(R.string.start_conversion)} (${files.size})"
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
            onClearVideoFileSettings = {}
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
            onClearVideoFileSettings = {}
        )
    }
}
