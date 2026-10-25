package org.roruku.smartformatconverter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.roruku.smartformatconverter.engine.media.BitrateSelection

class BitrateSelectionTest {
    @Test
    fun standardValuesRemainExplicitTargets() {
        assertEquals(2500, BitrateSelection.videoTargetKbps(2500, null))
        assertEquals(12000, BitrateSelection.videoTargetKbps(12000, 3200))
        assertEquals(320, BitrateSelection.audioTargetKbps(320, 128, 192))
    }

    @Test
    fun autoUsesEncoderDefaultForVideoAndRecommendedAudioTarget() {
        assertNull(BitrateSelection.videoTargetKbps(BitrateSelection.AUTO, 3300))
        assertEquals(192, BitrateSelection.audioTargetKbps(BitrateSelection.AUTO, 256, 192))
        assertEquals(128, BitrateSelection.audioRecommendedKbps(opus = false, channels = 1))
        assertEquals(192, BitrateSelection.audioRecommendedKbps(opus = false, channels = 2))
        assertEquals(96, BitrateSelection.audioRecommendedKbps(opus = true, channels = 1))
        assertEquals(160, BitrateSelection.audioRecommendedKbps(opus = true, channels = 2))
    }

    @Test
    fun sourceUsesOwnTrackBitrateOrFallsBackToAuto() {
        assertEquals(3350, BitrateSelection.videoTargetKbps(BitrateSelection.SOURCE, 3350))
        assertNull(BitrateSelection.videoTargetKbps(BitrateSelection.SOURCE, 0))
        assertNull(BitrateSelection.videoTargetKbps(BitrateSelection.SOURCE, 999999))
        assertEquals(256, BitrateSelection.audioTargetKbps(BitrateSelection.SOURCE, 256, 192))
        assertEquals(192, BitrateSelection.audioTargetKbps(BitrateSelection.SOURCE, null, 192))
        assertEquals(192, BitrateSelection.audioTargetKbps(BitrateSelection.SOURCE, 900, 192))
    }

    @Test
    fun dropdownPresetsIncludeTypicalStreamingRates() {
        assertEquals(listOf(64, 96, 128, 160, 192, 256, 320),
            BitrateSelection.audioStandardsKbps)
        assertEquals(true, 2500 in BitrateSelection.videoStandardsKbps)
        assertEquals(true, 8000 in BitrateSelection.videoStandardsKbps)
        assertEquals(true, 12000 in BitrateSelection.videoStandardsKbps)
    }
}
