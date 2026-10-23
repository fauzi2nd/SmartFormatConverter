package org.roruku.smartformatconverter.ui.converter

import android.net.Uri
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
    val videoThumbLoader = remember(context) {
        ImageLoader.Builder(context).components {
            add(VideoFrameDecoder.Factory())
        }.build()
    }
    DisposableEffect(videoThumbLoader) {
        onDispose { videoThumbLoader.shutdown() }
    }
    BackHandler { onBack() }
    val pager = rememberPagerState(pageCount = { files.size })
    val coroutineScope = rememberCoroutineScope()
    var applyToAll by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }

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
                Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Preview & Convert", fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium)
                Text(
                    "${when(state.selectedTab){0->"Video";1->"Image";else->"Audio"}} → ${state.targetFormat.uppercase()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("${files.size} files", style = MaterialTheme.typography.labelMedium)
        }
        HorizontalDivider()

        if (files.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Tidak ada media. Kembali dan pilih file lain.")
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
                                    when (state.selectedTab) {
                                        0 -> SourcePlayer(media.uri, audio = false)
                                        1 -> ZoomableImage(media.uri)
                                        else -> SourcePlayer(media.uri, audio = true)
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
                                    Icon(Icons.Default.Close, contentDescription = "Hapus dari daftar")
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
                            "Geser preview untuk file berikutnya",
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
                                    when (state.selectedTab) {
                                        0 -> AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(file.uri)
                                                .videoFrameMillis(1000)
                                                .size(240, 140)
                                                .build(),
                                            imageLoader = videoThumbLoader,
                                            contentDescription = "Video thumbnail",
                                            modifier = Modifier.fillMaxWidth().height(56.dp),
                                            contentScale = ContentScale.Crop
                                        )
                                        1 -> AsyncImage(
                                            model = file.uri,
                                            contentDescription = "Image thumbnail",
                                            modifier = Modifier.fillMaxWidth().height(56.dp),
                                            contentScale = ContentScale.Crop
                                        )
                                        else -> Box(
                                            Modifier.fillMaxWidth().height(56.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.Audiotrack,
                                                contentDescription = null)
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
                        "Output Settings",
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
                if (state.selectedTab == 0) {
                    item {
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            listOf("Semua file", "File ini saja").forEachIndexed { i, label ->
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
                                Text("Format Output", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    state.targetFormat.uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Text("Dipilih di katalog", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if (state.selectedTab == 0) {
                    item {
                        Text("Preset", fontWeight = FontWeight.SemiBold)
                        val presets = listOf(
                            "Original Quality / Remux", "High Quality",
                            "Balanced", "Small File", "Custom"
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(presets.size) { i ->
                                val choice = presets[i]
                                val chosen = resolvedVideoSettings.preset == choice
                                if (chosen) Button(onClick = {}) { Text(choice) }
                                else OutlinedButton(onClick = {
                                    if (applyToAll) {
                                        onUpdatePreset(choice)
                                    } else {
                                        selectedFile?.let {
                                            onUpdateVideoFileSettings(
                                                it.uri, resolvedVideoSettings.applyPreset(choice))
                                        }
                                    }
                                }) { Text(choice) }
                            }
                        }
                    }
                    item {
                        Text("Video, Audio & Subtitle",
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
                            ) { Text("Kembali ke pengaturan semua file") }
                        }
                    }
                } else {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    if (state.selectedTab == 1) "Image Settings" else "Audio Settings",
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    if (state.selectedTab == 1) {
                                        "JPEG: quality 100 dan subsampling 4:4:4; PNG/WebP memakai konfigurasi engine saat ini."
                                    } else {
                                        "Codec mengikuti format terpilih; konfigurasi audio default dari engine."
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
                            Text("Output location", style = MaterialTheme.typography.labelMedium)
                            Text(
                                "<Folder induk>/${outputFolder}/",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                "Nama asli dipertahankan · ekstensi .${state.targetFormat}",
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
                    if (state.isConverting) "Menyiapkan antrean..."
                    else "Start Conversion (${files.size})"
                )
            }
        }
    }
}

@Composable
private fun ZoomableImage(uri: Uri) {
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
            contentDescription = "Pratinjau gambar sumber",
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
private fun SourcePlayer(uri: Uri, audio: Boolean) {
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
