package org.roruku.smartformatconverter

import org.junit.Assert.*
import org.junit.Test
import org.roruku.smartformatconverter.engine.media.AdaptiveResolutionPolicy
import org.roruku.smartformatconverter.engine.media.VideoPresetResolutionPolicy
import org.roruku.smartformatconverter.engine.media.VideoSourceGeometry

class VideoPresetResolutionPolicyTest {
    private val source = AdaptiveResolutionPolicy.Size(1320, 1760)
    private val planner = VideoPresetResolutionPolicy

    @Test fun rotatedEncodedFrameIsCanonicalPortraitOnce() {
        val geometry = VideoSourceGeometry.resolve(
            1440, 1080, 1080, 1440, 90
        )
        assertEquals(AdaptiveResolutionPolicy.Size(1080, 1440), geometry.display)
        assertTrue(geometry.fromEncodedTrack)
        assertEquals(AdaptiveResolutionPolicy.Size(1080, 1440),
            VideoSourceGeometry.resolve(0, 0, 1080, 1440, 90).display)
        assertEquals(AdaptiveResolutionPolicy.Size(1920, 1080),
            VideoSourceGeometry.resolve(1920, 1080, 1920, 1080, 0).display)
    }

    @Test fun presetRangesChooseDifferentStandardPortraitTiers() {
        val supported: (AdaptiveResolutionPolicy.Size) -> Boolean? = {
            it.width <= 828 && it.height <= 1104
        }
        assertEquals(AdaptiveResolutionPolicy.Size(720, 960),
            planner.choose("High Quality", source, supported).size)
        assertEquals(AdaptiveResolutionPolicy.Size(480, 640),
            planner.choose("Balanced", source, supported).size)
        assertEquals(AdaptiveResolutionPolicy.Size(240, 320),
            planner.choose("Small File", source, supported).size)
        val smart = planner.choose("Smart Auto", source, supported)
        assertTrue(smart.size!!.width > 720)
        assertTrue(smart.size.width <= 828)
    }

    @Test fun highQualityCanUse4kWhenTheSourceAndDeviceSupportIt() {
        val source4k = AdaptiveResolutionPolicy.Size(2160, 3840)
        assertEquals(source4k, planner.choose("High Quality", source4k) { true }.size)
    }

    @Test fun noUpscaleAndBelowTargetFallbackAreExplicit() {
        val lowSource = AdaptiveResolutionPolicy.Size(480, 640)
        assertEquals(lowSource,
            planner.choose("High Quality", lowSource) { true }.size)
        val forcedFallback = planner.choose("High Quality", source) {
            it.width <= 480
        }
        assertEquals(AdaptiveResolutionPolicy.Size(480, 640), forcedFallback.size)
        assertTrue(forcedFallback.belowTarget)
    }

    @Test fun noReportedCompatibleSizeDoesNotInventOne() {
        assertNull(planner.choose("Balanced", source) { false }.size)
    }

    @Test fun landscapeMaintainsLandscapeForAllPresets() {
        val landscape = AdaptiveResolutionPolicy.Size(1920, 1080)
        assertEquals(AdaptiveResolutionPolicy.Size(854, 480),
            planner.choose("Balanced", landscape) { true }.size)
        assertEquals(AdaptiveResolutionPolicy.Size(426, 240),
            planner.choose("Small File", landscape) { true }.size)
    }
}
