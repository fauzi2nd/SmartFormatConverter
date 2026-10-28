package org.roruku.smartformatconverter

import kotlin.math.abs
import org.junit.Assert.*
import org.junit.Test
import org.roruku.smartformatconverter.engine.media.AdaptiveAutoResolution
import org.roruku.smartformatconverter.engine.media.AdaptiveResolutionPolicy
import org.roruku.smartformatconverter.engine.media.VideoAdvancedSettings

class AdaptiveAutoResolutionTest {
    private val src = AdaptiveResolutionPolicy.Size(1320, 1760)

    @Test fun picksHighestReportedCompatiblePortraitSize() {
        val choice = AdaptiveAutoResolution.choose(src) { size ->
            size.width <= 810 && size.height <= 1080
        }
        assertTrue(abs(3 * choice.size!!.height - 4 * choice.size.width) <= 6)
        assertTrue(choice.size.width >= 720)
        assertTrue(choice.size.width <= 810)
        assertTrue(choice.verified)
    }

    @Test fun retainsSourceWhenCompatible() {
        assertEquals(src, AdaptiveAutoResolution.choose(src) { true }.size)
    }

    @Test fun noReportedCompatibilityReturnsNull() {
        assertNull(AdaptiveAutoResolution.choose(src) { false }.size)
    }

    @Test fun changingPresetResetsStaleEncoderCodecAndGeometry() {
        val old = VideoAdvancedSettings(format = "webm", preset = "Custom",
            width = 720, height = 960, fps = "60", videoCodec = "vp8",
            encoder = "software", videoBitrateKbps = 2000)
        for (name in listOf("Smart Auto", "High Quality", "Balanced", "Small File")) {
            val preset = old.applyPreset(name)
            assertNull(preset.width)
            assertNull(preset.height)
            assertNull(preset.fps)
            assertEquals("auto", preset.encoder)
            assertEquals("auto", preset.videoCodec)
            assertTrue(preset.usesAutoResolution)
            assertEquals("webm", preset.format)
        }
        val custom = old.applyPreset("Custom")
        assertNull(custom.width)
        assertEquals("auto", custom.encoder)
        assertFalse(custom.usesAutoResolution)
    }
}
