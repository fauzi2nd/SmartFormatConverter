package org.roruku.smartformatconverter

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.roruku.smartformatconverter.data.db.MediaType
import org.roruku.smartformatconverter.data.repository.MediaOutputIntegrity
import org.roruku.smartformatconverter.engine.media.VideoAdvancedSettings
import org.roruku.smartformatconverter.engine.media3.Media3DeviceCapabilities
import org.roruku.smartformatconverter.engine.media3.Media3TransformerEngine
import org.roruku.smartformatconverter.engine.media3.VideoConversionConfig
import org.roruku.smartformatconverter.engine.media3.VideoOutputFormat

/**
 * Device-only tests: create a real H.264 MP4 from a tiny PNG image, then
 * exercise the shipping conversion engine. CI compiles these tests; actual
 * execution needs an emulator/device with appropriate MediaCodec encoders.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class Media3VideoSmokeInstrumentedTest {
    @Test
    fun h264Mp4OutputIsStructurallyValid() = runBlocking {
        runVideoExport(VideoOutputFormat.MP4)
    }

    @Test
    fun webmVp9OutputIsStructurallyValidWhenEncodersExist() = runBlocking {
        assumeTrue(Media3DeviceCapabilities.canExport(MediaType.VIDEO, "webm"))
        runVideoExport(VideoOutputFormat.WEBM)
    }

    @Test
    fun invalidVideoInputFailsWithoutLeavingOutput() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        val source = File(ctx.cacheDir, "invalid_video_$id.mp4")
        val output = File(ctx.cacheDir, "invalid_video_output_$id.mp4")
        try {
            source.writeText("invalid MP4 data, not a media file")
            val result = withTimeout(60_000L) {
                Media3TransformerEngine.convertVideo(
                    context = ctx,
                    inputUri = Uri.fromFile(source),
                    tempOutputFile = output,
                    config = VideoConversionConfig(outputFormat = VideoOutputFormat.MP4),
                    onProgress = { _, _, _ -> }
                )
            }
            assertTrue("Invalid source must fail", result.isFailure)
            assertTrue("Partial output must be cleaned", !output.exists())
        } finally {
            source.delete()
            output.delete()
        }
    }

    private suspend fun runVideoExport(format: VideoOutputFormat) {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        val image = File(ctx.cacheDir, "video_fixture_$id.png")
        val source = File(ctx.cacheDir, "video_source_$id.mp4")
        val output = File(ctx.cacheDir, "video_export_$id.${format.extension}")
        val bitmap = Bitmap.createBitmap(160, 120, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.BLUE)
            image.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()

            withTimeout(120_000L) { createVideoFixture(ctx, image, source) }
            assertNull(MediaOutputIntegrity.validate(source, MediaType.VIDEO, "mp4"))

            val advanced = VideoAdvancedSettings(
                format = format.extension,
                videoCodec = if (format == VideoOutputFormat.WEBM) "vp9" else "h264",
                audioCodec = "none",
                rateControl = "vbr"
            )
            val result = withTimeout(180_000L) {
                Media3TransformerEngine.convertVideo(
                    context = ctx,
                    inputUri = Uri.fromFile(source),
                    tempOutputFile = output,
                    config = VideoConversionConfig(outputFormat = format),
                    advancedSettings = advanced,
                    onProgress = { _, _, _ -> }
                )
            }
            assertTrue(result.exceptionOrNull()?.toString().orEmpty(), result.isSuccess)
            assertNull(MediaOutputIntegrity.validate(output, MediaType.VIDEO, format.extension))
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
            image.delete()
            source.delete()
            output.delete()
        }
    }

    private suspend fun createVideoFixture(
        context: android.content.Context, image: File, output: File
    ) = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine<Unit> { continuation ->
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.fromFile(image))
                .setImageDurationMs(1_200L)
                .build()
            val edited = EditedMediaItem.Builder(mediaItem).setFrameRate(12).build()
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, result: ExportResult) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }
                    override fun onError(composition: Composition, result: ExportResult, error: ExportException) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                })
                .build()
            continuation.invokeOnCancellation {
                Handler(Looper.getMainLooper()).post { transformer.cancel() }
            }
            try {
                transformer.start(edited, output.absolutePath)
            } catch (error: Exception) {
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    }
}
