package org.roruku.smartformatconverter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.roruku.smartformatconverter.engine.media.AdaptiveAudioPlanner
import org.roruku.smartformatconverter.engine.media.AdaptivePresetPlanner
import org.roruku.smartformatconverter.engine.media.BitrateSelection
import org.roruku.smartformatconverter.engine.media.VideoAdvancedSettings
import org.roruku.smartformatconverter.engine.media.VideoSourceProfile

class AdaptivePresetPlannerTest {
    private fun source(
        width: Int = 1920, height: Int = 1080, fps: Float = 30f,
        videoKbps: Int = 12_000, size: Long = 93_000_000L,
        durationMs: Long = 60_000L, hasAudio: Boolean = true
    ) = VideoSourceProfile(
        width = width, height = height, frameRate = fps,
        videoBitrateKbps = videoKbps, audioBitrateKbps = 192,
        audioChannels = 2, durationMs = durationMs,
        sizeBytes = size, hasAudio = hasAudio
    )

    @Test fun bitrateFollowsPresetAndResolution() {
        val high = AdaptivePresetPlanner.plan(
            VideoAdvancedSettings().applyPreset("High Quality"), source())
        val balanced = AdaptivePresetPlanner.plan(
            VideoAdvancedSettings().applyPreset("Balanced"), source())
        val small = AdaptivePresetPlanner.plan(
            VideoAdvancedSettings().applyPreset("Small File"), source())
        assertEquals(8_000, high.videoKbps)
        assertEquals(4_800, balanced.videoKbps)
        assertEquals(2_720, small.videoKbps)
        val lowRes = AdaptivePresetPlanner.plan(
            VideoAdvancedSettings().applyPreset("High Quality"),
            source(width = 854, height = 480))
        assertTrue(lowRes.videoKbps < high.videoKbps)
    }

    @Test fun profilesAreCodecAwareAndFrameRateAware() {
        val high = VideoAdvancedSettings().applyPreset("High Quality")
        val h264 = AdaptivePresetPlanner.plan(high, source())
        val vp9 = AdaptivePresetPlanner.plan(high.copy(format = "webm"), source())
        val hevc = AdaptivePresetPlanner.plan(high.copy(videoCodec = "h265"), source())
        assertTrue(vp9.videoKbps < h264.videoKbps)
        assertTrue(hevc.videoKbps < h264.videoKbps)
        assertTrue(AdaptivePresetPlanner.plan(high,
            source(fps = 60f)).videoKbps > h264.videoKbps)
    }

    @Test fun preventUpscaleUsesOriginalDimensionsForEstimate() {
        val standard = AdaptivePresetPlanner.plan(
            VideoAdvancedSettings().applyPreset("High Quality"), source())
        val rejectedResize = AdaptivePresetPlanner.plan(
            VideoAdvancedSettings().applyPreset("High Quality")
                .copy(width = 1920, height = 2160, preventUpscale = true),
            source(width = 1920, height = 1080))
        assertEquals(standard.videoKbps, rejectedResize.videoKbps)
        val downscale = AdaptivePresetPlanner.plan(
            VideoAdvancedSettings().applyPreset("High Quality")
                .copy(width = 1280, height = 720, preventUpscale = true), source())
        assertTrue(downscale.videoKbps < standard.videoKbps)
    }

    @Test fun sourceLimitsAutoButNotExplicitManualTargets() {
        val lowBitrate = source(videoKbps = 1_000)
        val auto = VideoAdvancedSettings().applyPreset("High Quality")
        val limited = AdaptivePresetPlanner.plan(auto, lowBitrate)
        assertTrue(limited.sourceLimited)
        assertTrue(limited.videoKbps <= 1_250)
        val custom = AdaptivePresetPlanner.plan(
            auto.copy(preset = "Custom", videoBitrateKbps = 15_000), lowBitrate)
        assertEquals(15_000, custom.videoKbps)
        val original = AdaptivePresetPlanner.plan(
            auto.copy(videoBitrateKbps = BitrateSelection.SOURCE), lowBitrate)
        assertEquals(1_000, original.videoKbps)
    }

    @Test fun estimatesAreConditionalAndWarnOnGrowth() {
        val manuallyLarge = AdaptivePresetPlanner.plan(
            VideoAdvancedSettings(videoBitrateKbps = 20_000),
            source(size = 8_000_000L))
        assertTrue(manuallyLarge.mayGrow)
        assertNotNull(manuallyLarge.estimatedBytes)
        assertTrue(manuallyLarge.lowBytes!! < manuallyLarge.highBytes!!)
        assertNull(AdaptivePresetPlanner.plan(
            VideoAdvancedSettings().applyPreset("Smart Auto"),
            source(durationMs = 0L)).estimatedBytes)
        assertFalse(AdaptivePresetPlanner.plan(
            VideoAdvancedSettings().applyPreset("Small File"),
            source()).mayGrow)
    }

    @Test fun audioAdaptsAndUserBitratesTakePrecedence() {
        val smart = AdaptiveAudioPlanner.targetKbps(0, 192, 2, true, "Smart Auto")
        val mono = AdaptiveAudioPlanner.targetKbps(0, 192, 1, true, "Smart Auto")
        assertTrue(mono < smart)
        assertEquals(64, AdaptiveAudioPlanner.targetKbps(0, 64, 2, false, "High Quality"))
        assertEquals(256, AdaptiveAudioPlanner.targetKbps(256, 64, 2, true, "Small File"))
        assertEquals(64, AdaptiveAudioPlanner.targetKbps(-1, 64, 2, true, "High Quality"))
    }

    @Test fun codecAndMissingMetadataKeepSafeFallback() {
        assertEquals("vp9", AdaptivePresetPlanner.outputCodec("WEBM", "auto"))
        assertEquals("hevc", AdaptivePresetPlanner.outputCodec("mp4", "h265"))
        assertEquals("h264", AdaptivePresetPlanner.outputCodec("mp4", "auto"))
        val unknown = AdaptivePresetPlanner.plan(
            VideoAdvancedSettings().applyPreset("Smart Auto"),
            VideoSourceProfile())
        assertTrue(unknown.videoKbps >= 100)
        assertNull(unknown.estimatedBytes)
    }
}
