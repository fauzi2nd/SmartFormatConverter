package org.roruku.smartformatconverter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.roruku.smartformatconverter.data.db.Converters
import org.roruku.smartformatconverter.data.db.JobStatus
import org.roruku.smartformatconverter.data.db.MediaType
import org.roruku.smartformatconverter.data.repository.Media3OutputPolicy
import org.roruku.smartformatconverter.data.repository.MediaOutputIntegrity
import org.roruku.smartformatconverter.data.storage.BatchOutputStructure
import org.roruku.smartformatconverter.data.storage.BatchScanConfig
import org.roruku.smartformatconverter.engine.media.AudioAdvancedSettings
import org.roruku.smartformatconverter.engine.media.VideoAdvancedSettings
import org.roruku.smartformatconverter.engine.media3.AudioConversionConfig
import org.roruku.smartformatconverter.engine.media3.AudioOutputFormat
import org.roruku.smartformatconverter.engine.media3.VideoConversionConfig
import org.roruku.smartformatconverter.engine.media3.VideoOutputFormat
import org.roruku.smartformatconverter.engine.image.ChromaSubsampling
import org.roruku.smartformatconverter.engine.image.ExifTransform
import org.roruku.smartformatconverter.engine.image.ImageConversionConfig
import org.roruku.smartformatconverter.engine.image.ImageConverterEngine
import org.roruku.smartformatconverter.engine.image.ImageOutputFormat
import java.io.File

class EngineUnitTest {

    @Test
    fun testMedia3AdvancedSettingsCompatibility() {
        assertNull(Media3OutputPolicy.unsupportedVideoSettings(VideoAdvancedSettings()))
        assertNull(Media3OutputPolicy.unsupportedVideoSettings(
            VideoAdvancedSettings().applyPreset("Original Quality / Remux")))
        assertNotNull(Media3OutputPolicy.unsupportedVideoSettings(
            VideoAdvancedSettings(format = "webm").applyPreset("Original Quality / Remux")))
        assertNull(Media3OutputPolicy.unsupportedVideoSettings(
            VideoAdvancedSettings(encoder = "software")))
        assertNull(Media3OutputPolicy.unsupportedVideoSettings(
            VideoAdvancedSettings(encoder = "hardware")))
        assertNull(Media3OutputPolicy.unsupportedVideoSettings(
            VideoAdvancedSettings(format = "webm", videoCodec = "vp8", encoder = "software")))
        assertNotNull(Media3OutputPolicy.unsupportedVideoSettings(
            VideoAdvancedSettings(encoder = "invalid")))
        assertNotNull(Media3OutputPolicy.unsupportedVideoSettings(
            VideoAdvancedSettings(fps = "60")))
        assertNotNull(Media3OutputPolicy.unsupportedVideoSettings(
            VideoAdvancedSettings(volumePercent = 150)))
        assertNotNull(Media3OutputPolicy.unsupportedVideoSettings(
            VideoAdvancedSettings(subtitleMode = "copy")))
        assertNull(Media3OutputPolicy.unsupportedAudioSettings(AudioAdvancedSettings()))
        assertNull(Media3OutputPolicy.unsupportedAudioSettings(
            AudioAdvancedSettings(volumePercent = 150)))
    }


    @Test
    fun testMediaOutputIntegrityTrackPolicy() {
        assertEquals(true, MediaOutputIntegrity.containsExpectedTrack(MediaType.VIDEO, listOf("video/avc", "audio/mp4a-latm")))
        assertEquals(false, MediaOutputIntegrity.containsExpectedTrack(MediaType.VIDEO, listOf("audio/mp4a-latm")))
        assertEquals(true, MediaOutputIntegrity.containsExpectedTrack(MediaType.AUDIO, listOf("audio/mp4a-latm")))
        assertEquals(false, MediaOutputIntegrity.containsExpectedTrack(MediaType.AUDIO, listOf("video/avc")))
        assertEquals(false, MediaOutputIntegrity.containsExpectedTrack(MediaType.VIDEO, emptyList()))
        assertNotNull(MediaOutputIntegrity.validate(File("unavailable-test-file.mp4"), MediaType.DOWNLOAD))
        assertNotNull(MediaOutputIntegrity.validate(File("unavailable-test-file.mp4"), MediaType.VIDEO))
    }



    @Test
    fun testMedia3OutputPolicyRejectsLegacyContainers() {
        assertEquals(null, Media3OutputPolicy.rejectionReason(MediaType.VIDEO, "mp4"))
        assertEquals(null, Media3OutputPolicy.rejectionReason(MediaType.AUDIO, "M4A"))
        assertEquals(null, Media3OutputPolicy.rejectionReason(MediaType.IMAGE, "jpeg"))
        assertEquals(null, Media3OutputPolicy.rejectionReason(MediaType.DOWNLOAD, "mkv"))
        assertNotNull(Media3OutputPolicy.rejectionReason(MediaType.VIDEO, "mkv"))
        assertNotNull(Media3OutputPolicy.rejectionReason(MediaType.AUDIO, "mp3"))
        assertEquals(null, Media3OutputPolicy.rejectionReason(MediaType.AUDIO, "flac"))
        assertNotNull(Media3OutputPolicy.rejectionReason(MediaType.IMAGE, "tiff"))
    }

    @Test
    fun testDownsampleDoesNotChangeOriginalSizeExport() {
        val original = ImageConversionConfig(outputFormat = ImageOutputFormat.JPEG)
        assertEquals(1, ImageConverterEngine.calculateDecodeSampleSize(12000, 8000, original))
        val resized = original.copy(customWidth = 1000, customHeight = 1000)
        assertEquals(4, ImageConverterEngine.calculateDecodeSampleSize(12000, 8000, resized))
        val cropping = resized.copy(cropToExactRatio = true)
        assertEquals(1, ImageConverterEngine.calculateDecodeSampleSize(12000, 8000, cropping))
    }

    @Test
    fun testMedia3VideoPresetsUseAdaptiveTargets() {
        val base = org.roruku.smartformatconverter.engine.media.VideoAdvancedSettings()
        for (name in listOf("High Quality", "Balanced", "Small File", "Smart Auto")) {
            val preset = base.applyPreset(name)
            assertEquals(name, preset.preset)
            assertEquals(0, preset.videoBitrateKbps)
        }
    }

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

        assertEquals("m4a", AudioOutputFormat.M4A.extension)
        assertEquals("audio/mp4", AudioOutputFormat.M4A.mimeType)
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
    fun testMedia3ConversionModels() {
        val video = VideoConversionConfig(
            outputFormat = VideoOutputFormat.WEBM,
            bitrateKbps = 6000,
            audioBitrateKbps = 192
        )
        assertEquals(VideoOutputFormat.WEBM, video.outputFormat)
        assertEquals(6000, video.bitrateKbps)
        assertEquals(192, video.audioBitrateKbps)

        val audio = AudioConversionConfig(outputFormat = AudioOutputFormat.M4A, bitrateKbps = 256)
        assertEquals(AudioOutputFormat.M4A, audio.outputFormat)
        assertEquals(256, audio.bitrateKbps)
    }

    @Test
    fun testMediaOutputSignatureAndCodecChecks() {
        val mp4 = byteArrayOf(0, 0, 0, 24, 102, 116, 121, 112, 105, 115, 111, 109)
        val webm = byteArrayOf(0x1a, 0x45, 0xdf.toByte(), 0xa3.toByte())
        assertEquals(true, MediaOutputIntegrity.signatureMatches("mp4", mp4))
        assertEquals(true, MediaOutputIntegrity.signatureMatches("m4a", mp4))
        assertEquals(false, MediaOutputIntegrity.signatureMatches("webm", mp4))
        assertEquals(true, MediaOutputIntegrity.signatureMatches("webm", webm))
        assertEquals(false, MediaOutputIntegrity.signatureMatches("mp4", webm))
        assertEquals(true, MediaOutputIntegrity.codecMatches(
            MediaType.VIDEO, "webm", listOf("video/x-vnd.on2.vp9", "audio/opus")))
        assertEquals(false, MediaOutputIntegrity.codecMatches(
            MediaType.VIDEO, "webm", listOf("video/avc", "audio/mp4a-latm")))
        assertEquals(true, MediaOutputIntegrity.codecMatches(
            MediaType.VIDEO, "mp4", listOf("video/hevc", "audio/mp4a-latm")))
        assertEquals(false, MediaOutputIntegrity.codecMatches(
            MediaType.AUDIO, "m4a", listOf("audio/opus")))
    }

    @Test
    fun testNewMediaContainerSignaturesAndCodecs() {
        val ogg = "OggS".toByteArray(Charsets.US_ASCII)
        val flac = "fLaC".toByteArray(Charsets.US_ASCII)
        val gif = "GIF89a".toByteArray(Charsets.US_ASCII)
        val wav = "RIFF".toByteArray() + byteArrayOf(0,0,0,0) + "WAVE".toByteArray()
        assertEquals(true, MediaOutputIntegrity.signatureMatches("ogg", ogg))
        assertEquals(true, MediaOutputIntegrity.signatureMatches("flac", flac))
        assertEquals(true, MediaOutputIntegrity.signatureMatches("gif", gif))
        assertEquals(true, MediaOutputIntegrity.signatureMatches("wav", wav))
        assertEquals(false, MediaOutputIntegrity.signatureMatches("flac", wav))
        assertEquals(true, MediaOutputIntegrity.codecMatches(MediaType.AUDIO, "ogg", listOf("audio/opus")))
        assertEquals(true, MediaOutputIntegrity.codecMatches(MediaType.AUDIO, "wav", listOf("audio/raw")))
        assertEquals(true, MediaOutputIntegrity.codecMatches(MediaType.AUDIO, "flac", listOf("audio/flac")))
        assertEquals(false, MediaOutputIntegrity.codecMatches(MediaType.AUDIO, "ogg", listOf("audio/mp4a-latm")))
    }

    @Test
    fun testMp4StreamCopyNeverDropsTracksSilently() {
        val guard = org.roruku.smartformatconverter.engine.media3.Mp4RemuxEngine
        assertEquals(true, guard.supportedTracks(listOf("video/avc", "audio/mp4a-latm")))
        assertEquals(true, guard.supportedTracks(listOf("video/hevc")))
        assertEquals(false, guard.supportedTracks(listOf("video/x-vnd.on2.vp9", "audio/opus")))
        assertEquals(false, guard.supportedTracks(listOf("video/avc", "audio/ac3")))
        assertEquals(false, guard.supportedTracks(listOf("video/avc", "audio/mp4a-latm", "text/vtt")))
        assertEquals(false, guard.supportedTracks(listOf("video/avc", "audio/mp4a-latm", "audio/mp4a-latm")))
    }

    @Test
    fun testBatchScanConfigDefaults() {
        val config = BatchScanConfig()
        assertEquals("mp4", config.targetVideoFormat)
        assertEquals("jpg", config.targetImageFormat)
        assertEquals("m4a", config.targetAudioFormat)
        assertEquals(BatchOutputStructure.MIRRORED, config.outputStructure)
    }
}
