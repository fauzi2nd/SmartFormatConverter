package org.roruku.smartformatconverter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.roruku.smartformatconverter.data.db.Converters
import org.roruku.smartformatconverter.data.db.JobStatus
import org.roruku.smartformatconverter.data.db.MediaType
import org.roruku.smartformatconverter.data.storage.BatchOutputStructure
import org.roruku.smartformatconverter.data.storage.BatchScanConfig
import org.roruku.smartformatconverter.engine.ffmpeg.AudioCodec
import org.roruku.smartformatconverter.engine.ffmpeg.AudioConversionConfig
import org.roruku.smartformatconverter.engine.ffmpeg.AudioOutputFormat
import org.roruku.smartformatconverter.engine.ffmpeg.VideoCodec
import org.roruku.smartformatconverter.engine.ffmpeg.VideoConversionConfig
import org.roruku.smartformatconverter.engine.ffmpeg.VideoOutputFormat
import org.roruku.smartformatconverter.engine.image.ChromaSubsampling
import org.roruku.smartformatconverter.engine.image.ExifTransform
import org.roruku.smartformatconverter.engine.image.ImageConversionConfig
import org.roruku.smartformatconverter.engine.image.ImageConverterEngine
import org.roruku.smartformatconverter.engine.image.ImageOutputFormat
import java.io.File

class EngineUnitTest {

    @Test
    fun testRoomConverters() {
        val converters = Converters()

        assertEquals("VIDEO", converters.fromMediaType(MediaType.VIDEO))
        assertEquals(MediaType.IMAGE, converters.toMediaType("IMAGE"))
        assertEquals(MediaType.VIDEO, converters.toMediaType("INVALID_MEDIA_TYPE"))

        assertEquals("RUNNING", converters.fromJobStatus(JobStatus.RUNNING))
        assertEquals(JobStatus.COMPLETED, converters.toJobStatus("COMPLETED"))
        assertEquals(JobStatus.PENDING, converters.toJobStatus("UNKNOWN_STATUS"))
    }

    @Test
    fun testOutputFormats() {
        assertEquals("mp4", VideoOutputFormat.MP4.extension)
        assertEquals("video/mp4", VideoOutputFormat.MP4.mimeType)

        assertEquals("jpg", ImageOutputFormat.JPEG.extension)
        assertEquals("image/jpeg", ImageOutputFormat.JPEG.mimeType)

        assertEquals("mp3", AudioOutputFormat.MP3.extension)
        assertEquals("audio/mpeg", AudioOutputFormat.MP3.mimeType)
    }

    @Test
    fun testImageConfigDefaults() {
        val config = ImageConversionConfig(outputFormat = ImageOutputFormat.JPEG)
        assertEquals(ImageOutputFormat.JPEG, config.outputFormat)
        assertEquals(100, config.jpegQuality)
        assertEquals(ChromaSubsampling.S444, config.chromaSubsampling)
        assertEquals(true, config.preserveExif)
        assertEquals(true, config.maintainAspectRatio)
    }

    @Test
    fun testExifTransformDefaults() {
        val transform = ExifTransform(rotationDegrees = 90, flipHorizontal = true)
        assertEquals(90, transform.rotationDegrees)
        assertEquals(true, transform.flipHorizontal)
    }

    @Test
    fun testValidateJpegNonExistentFile() {
        val nonExistentFile = File("non_existent_file_path.jpg")
        assertFalse(ImageConverterEngine.validateJpegFile(nonExistentFile))
    }

    @Test
    fun testPureKotlinJpegEncodingConfiguration() {
        val config = ImageConversionConfig(
            outputFormat = ImageOutputFormat.JPEG,
            jpegQuality = 90
        )
        assertEquals(90, config.jpegQuality)
        assertEquals(ImageOutputFormat.JPEG, config.outputFormat)
    }

    @Test
    fun testFFmpegVideoConfig() {
        val config = VideoConversionConfig(
            outputFormat = VideoOutputFormat.MP4,
            videoCodec = VideoCodec.H264,
            audioCodec = AudioCodec.AAC,
            crf = 23,
            resolution = "1280x720"
        )
        assertEquals(VideoOutputFormat.MP4, config.outputFormat)
        assertEquals(VideoCodec.H264, config.videoCodec)
        assertEquals(AudioCodec.AAC, config.audioCodec)
        assertEquals(23, config.crf)
        assertEquals("1280x720", config.resolution)
    }

    @Test
    fun testFFmpegAudioConfig() {
        val config = AudioConversionConfig(
            outputFormat = AudioOutputFormat.MP3,
            audioCodec = AudioCodec.MP3,
            bitrateKbps = 192,
            sampleRate = 44100
        )
        assertEquals(AudioOutputFormat.MP3, config.outputFormat)
        assertEquals(192, config.bitrateKbps)
        assertEquals(44100, config.sampleRate)
    }

    @Test
    fun testBatchScanConfigDefaults() {
        val config = BatchScanConfig()
        assertEquals("mp4", config.targetVideoFormat)
        assertEquals("jpg", config.targetImageFormat)
        assertEquals("mp3", config.targetAudioFormat)
        assertEquals(BatchOutputStructure.MIRRORED, config.outputStructure)
    }
}
