package org.roruku.smartformatconverter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.roruku.smartformatconverter.engine.media.VideoEncoderSelection
import org.roruku.smartformatconverter.engine.media.VideoAdvancedSettings
import org.roruku.smartformatconverter.engine.media.VideoSettingsValidator

class VideoEncoderSelectionTest {
    @Test fun filtersHardwareSoftwareAndExactDeviceCodecNames() {
        val name = "c2.android.vp9.encoder"
        assertTrue(VideoEncoderSelection.matches("auto", name, false))
        assertTrue(VideoEncoderSelection.matches("software", name, false))
        assertFalse(VideoEncoderSelection.matches("hardware", name, false))
        assertFalse(VideoEncoderSelection.matches("software", "vendor.hw.encoder", true))
        assertTrue(VideoEncoderSelection.matches(
            VideoEncoderSelection.specific(name), name, false))
        assertFalse(VideoEncoderSelection.matches(
            VideoEncoderSelection.specific(name), "c2.other.vp9.encoder", false))
    }

    @Test fun specificChoiceIsPersistedAndValidated() {
        val selection = VideoEncoderSelection.specific("c2.android.vp9.encoder")
        val settings = VideoAdvancedSettings(format="webm", encoder=selection)
        assertEquals(selection, VideoAdvancedSettings.fromJson(settings.toJson()).encoder)
        assertEquals(null, VideoSettingsValidator.validate(settings))
        assertTrue(VideoSettingsValidator.validate(
            settings.copy(encoder="codec:")
        ) != null)
        assertTrue(VideoSettingsValidator.validate(
            settings.copy(encoder="random.encoder")
        ) != null)
    }

    @Test fun existingJobsRemainBackwardsCompatible() {
        for (choice in listOf("auto", "hardware", "software")) {
            val s=VideoAdvancedSettings(encoder=choice)
            assertEquals(choice, VideoAdvancedSettings.fromJson(s.toJson()).encoder)
            assertEquals(null, VideoSettingsValidator.validate(s))
        }
    }
}
