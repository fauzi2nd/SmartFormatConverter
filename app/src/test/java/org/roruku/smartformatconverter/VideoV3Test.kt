package org.roruku.smartformatconverter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.roruku.smartformatconverter.engine.media.BitrateSelection
import org.roruku.smartformatconverter.engine.media.VideoAdvancedSettings
import org.roruku.smartformatconverter.engine.media.VideoSettingsValidator
import org.roruku.smartformatconverter.data.repository.Media3OutputPolicy
import org.roruku.smartformatconverter.data.repository.FormatCapabilityRegistry
import org.roruku.smartformatconverter.data.db.MediaType

/** Test the engine actually shipped, not the retired FFmpeg shell command builder. */
class VideoV3Test {
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
    fun presetBitrateIsAppliedNotDeadCrf() {
        val base = VideoAdvancedSettings()
        assertEquals("vbr", base.rateControl)
        assertEquals(12000, base.applyPreset("High Quality").videoBitrateKbps)
        assertEquals(6000, base.applyPreset("Balanced").videoBitrateKbps)
        assertEquals(2500, base.applyPreset("Small File").videoBitrateKbps)
        assertEquals(BitrateSelection.AUTO, base.applyPreset("Smart Auto").videoBitrateKbps)
        assertEquals(BitrateSelection.AUTO, base.applyPreset("Smart Auto").audioBitrateKbps)
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
