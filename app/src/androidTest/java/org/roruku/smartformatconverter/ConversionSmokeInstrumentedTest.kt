package org.roruku.smartformatconverter

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.roruku.smartformatconverter.engine.ffmpeg.AudioConversionConfig
import org.roruku.smartformatconverter.engine.ffmpeg.AudioOutputFormat
import org.roruku.smartformatconverter.engine.image.ImageConversionConfig
import org.roruku.smartformatconverter.engine.image.ImageConverterEngine
import org.roruku.smartformatconverter.engine.image.ImageOutputFormat
import org.roruku.smartformatconverter.engine.media3.Media3TransformerEngine

/**
 * Device-only smoke tests for real Android decoders, encoders and extractors.
 * Compile with assembleDebugAndroidTest; run with connectedDebugAndroidTest.
 */
@RunWith(AndroidJUnit4::class)
class ConversionSmokeInstrumentedTest {

    @Test
    fun imageConversionProducesValidJpeg() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "smoke_input_${UUID.randomUUID()}.png")
        val output = File(context.cacheDir, "smoke_output_${UUID.randomUUID()}.jpg")
        val bitmap = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.CYAN)
            source.outputStream().use { out ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
            }
            val result = withTimeout(60_000L) {
                ImageConverterEngine.convertImage(
                    context = context,
                    inputUri = Uri.fromFile(source),
                    outputFile = output,
                    config = ImageConversionConfig(outputFormat = ImageOutputFormat.JPEG),
                    onProgress = {}
                )
            }
            assertTrue(result.exceptionOrNull()?.toString().orEmpty(), result.isSuccess)
            assertTrue(output.isFile && output.length() > 0L)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(output.absolutePath, bounds)
            assertEquals(64, bounds.outWidth)
            assertEquals(48, bounds.outHeight)
        } finally {
            bitmap.recycle()
            source.delete()
            output.delete()
        }
    }

    @Test
    fun wavConversionProducesReadableM4aTrack() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "smoke_input_${UUID.randomUUID()}.wav")
        val output = File(context.cacheDir, "smoke_output_${UUID.randomUUID()}.m4a")
        try {
            writeWave(source)
            val result = withTimeout(120_000L) {
                Media3TransformerEngine.convertAudio(
                    context = context,
                    inputUri = Uri.fromFile(source),
                    tempOutputFile = output,
                    config = AudioConversionConfig(outputFormat = AudioOutputFormat.M4A),
                    onProgress = { _, _, _ -> }
                )
            }
            assertTrue(result.exceptionOrNull()?.toString().orEmpty(), result.isSuccess)
            assertTrue(output.isFile && output.length() > 0L)

            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(output.absolutePath)
                val mimes = (0 until extractor.trackCount).map {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME).orEmpty()
                }
                assertTrue("Expected an AAC audio track: $mimes", mimes.any { it == "audio/mp4a-latm" })
            } finally {
                extractor.release()
            }
        } finally {
            source.delete()
            output.delete()
        }
    }

    private fun writeWave(file: File) {
        val rate = 44_100
        val samples = rate // One second of 16-bit mono PCM.
        val dataBytes = samples * 2
        val buffer = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray(Charsets.US_ASCII))
        buffer.putInt(36 + dataBytes)
        buffer.put("WAVE".toByteArray(Charsets.US_ASCII))
        buffer.put("fmt ".toByteArray(Charsets.US_ASCII))
        buffer.putInt(16)
        buffer.putShort(1.toShort()) // PCM
        buffer.putShort(1.toShort()) // Mono
        buffer.putInt(rate)
        buffer.putInt(rate * 2)
        buffer.putShort(2.toShort())
        buffer.putShort(16.toShort())
        buffer.put("data".toByteArray(Charsets.US_ASCII))
        buffer.putInt(dataBytes)
        for (sample in 0 until samples) {
            val value = (kotlin.math.sin(2.0 * Math.PI * 440.0 * sample / rate) * 8000.0).toInt()
            buffer.putShort(value.toShort())
        }
        file.writeBytes(buffer.array())
    }
}
