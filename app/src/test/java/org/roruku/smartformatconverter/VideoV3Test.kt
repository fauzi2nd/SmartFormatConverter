package org.roruku.smartformatconverter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.roruku.smartformatconverter.engine.media.BitrateSelection
import org.roruku.smartformatconverter.engine.media.VideoAdvancedSettings
import org.roruku.smartformatconverter.engine.media.VideoSettingsValidator
import org.roruku.smartformatconverter.engine.media3.VideoEncoderCatalog
import org.roruku.smartformatconverter.data.repository.Media3OutputPolicy
import org.roruku.smartformatconverter.data.repository.FormatCapabilityRegistry
import org.roruku.smartformatconverter.data.db.MediaType

/** Test the engine actually shipped, not the retired FFmpeg shell command builder. */
class VideoV3Test {
    @Test
    fun automaticCodecResolvesPerContainerInAllPreviews() {
        assertEquals("video/avc", VideoEncoderCatalog.mimeType("mp4", "auto"))
        assertEquals("video/x-vnd.on2.vp9", VideoEncoderCatalog.mimeType("webm", "auto"))
        assertEquals("video/x-vnd.on2.vp8", VideoEncoderCatalog.mimeType("webm", "vp8"))
        assertEquals("video/hevc", VideoEncoderCatalog.mimeType("mp4", "h265"))
    }

    @Test
    fun registryAndOutputPolicyAgree() {
        assertEquals(listOf("mp4", "webm"),
            FormatCapabilityRegistry.formats(MediaType.VIDEO).map { it.extension })
        assertEquals("video/webm",
            FormatCapabilityRegistry.find(MediaType.VIDEO, "WEBM")?.mimeType)
        assertNull(Media3OutputPolicy.rejectionReason(MediaType.VIDEO, "webm"))
        assertNotNull(Media3OutputPolicy.rejectionReason(MediaType.VIDEO, "avi"))
        assertNotNull(Media3OutputPolicy.rejectionReason(MediaType.AUDIO, "mp3"))
        assertEquals("jpg", FormatCapabilityRegistry.find(MediaType.IMAGE, "jpeg")?.extension)
    }

    @Test
    fun mp4AndWebmCodecsMustMatchContainer() {
        assertNull(VideoSettingsValidator.validate(VideoAdvancedSettings(format = "mp4")))
        assertNull(VideoSettingsValidator.validate(VideoAdvancedSettings(format = "webm")))
        assertNull(VideoSettingsValidator.validate(
            VideoAdvancedSettings(format = "webm", videoCodec = "vp9", audioCodec = "opus")))
        assertNotNull(VideoSettingsValidator.validate(
            VideoAdvancedSettings(format = "webm", videoCodec = "h264")))
        assertNotNull(VideoSettingsValidator.validate(
            VideoAdvancedSettings(format = "mp4", videoCodec = "vp9")))
        assertNotNull(VideoSettingsValidator.validate(
            VideoAdvancedSettings(format = "webm", audioCodec = "aac")))
        assertNotNull(Media3OutputPolicy.unsupportedVideoSettings(
            VideoAdvancedSettings(format = "webm", audioCodec = "copy")))
    }

    @Test
    fun unsupportedLegacySettingsAreRejectedEvenIfSyntacticallyValid() {
        val legacy = VideoAdvancedSettings(fps = "60", rateControl = "cbr")
        assertNull(VideoSettingsValidator.validate(legacy))
        assertNotNull(Media3OutputPolicy.unsupportedVideoSettings(legacy))
        assertNull(Media3OutputPolicy.unsupportedVideoSettings(
            VideoAdvancedSettings().applyPreset("Original Quality / Remux")))
        assertNotNull(Media3OutputPolicy.unsupportedVideoSettings(
            VideoAdvancedSettings(format = "webm").applyPreset("Original Quality / Remux")))
        assertNotNull(Media3OutputPolicy.unsupportedVideoSettings(
            VideoAdvancedSettings(audioStream = "all")))
        assertNotNull(Media3OutputPolicy.unsupportedVideoSettings(
            VideoAdvancedSettings(subtitleMode = "copy")))
    }

    @Test
    fun smartAutoNeverSilentlyDownscalesWebm() {
        val webm = VideoAdvancedSettings(format = "webm",
            allowEncoderDownscale = true).applyPreset("Smart Auto")
        assertEquals(false, webm.allowEncoderDownscale)
        assertNull(webm.width)
        assertNull(webm.height)
        val stale = VideoAdvancedSettings(format = "webm", width = 1280, height = 720,
            fps = "30", videoCodec = "vp8", encoder = "software")
            .applyPreset("Smart Auto")
        assertNull(stale.width)
        assertNull(stale.height)
        assertNull(stale.fps)
        assertEquals("auto", stale.videoCodec)
        assertEquals("auto", stale.encoder)
    }

    @Test
    fun presetsDelegateTargetsToAdaptivePlanner() {
        val base = VideoAdvancedSettings()
        assertEquals("vbr", base.rateControl)
        for (name in listOf("High Quality", "Balanced", "Small File", "Smart Auto")) {
            val preset = base.applyPreset(name)
            assertEquals(name, preset.preset)
            assertEquals(BitrateSelection.AUTO, preset.videoBitrateKbps)
            assertEquals(BitrateSelection.AUTO, preset.audioBitrateKbps)
        }
    }

    @Test
    fun invalidDimensionsAndBitratesAreRejected() {
        assertNotNull(VideoSettingsValidator.validate(
            VideoAdvancedSettings(width = 1921, height = 1080)))
        assertNotNull(VideoSettingsValidator.validate(
            VideoAdvancedSettings(videoBitrateKbps = -2)))
        assertNotNull(VideoSettingsValidator.validate(
            VideoAdvancedSettings(audioBitrateKbps = -2)))
        assertNull(VideoSettingsValidator.validate(
            VideoAdvancedSettings(videoBitrateKbps = BitrateSelection.AUTO)))
        assertNull(VideoSettingsValidator.validate(
            VideoAdvancedSettings(videoBitrateKbps = BitrateSelection.SOURCE,
                audioBitrateKbps = BitrateSelection.SOURCE)))
        assertNotNull(VideoSettingsValidator.validate(
            VideoAdvancedSettings(fps = "999")))
    }

    @Test
    fun bitrateSelectionSurvivesPersistedVideoSnapshots() {
        val settings = VideoAdvancedSettings(
            videoBitrateKbps = BitrateSelection.SOURCE,
            audioBitrateKbps = BitrateSelection.AUTO
        )
        assertEquals(settings, VideoAdvancedSettings.fromJson(settings.toJson()))
        assertNull(VideoSettingsValidator.validate(settings))
    }

    @Test
    fun persistedSettingsKeepTheirCodecChoice() {
        val original = VideoAdvancedSettings(
            format = "webm", videoCodec = "vp9", audioCodec = "opus",
            videoBitrateKbps = 3300, rateControl = "vbr"
        )
        val restored = VideoAdvancedSettings.fromJson(original.toJson())
        assertEquals(original, restored)
        assertNull(VideoSettingsValidator.validate(restored))
        assertNull(Media3OutputPolicy.unsupportedVideoSettings(restored))
    }
}
