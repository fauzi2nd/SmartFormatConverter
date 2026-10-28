package org.roruku.smartformatconverter

import org.junit.Assert.*
import org.junit.Test
import org.roruku.smartformatconverter.engine.media.AdaptiveAudioPlanner
import org.roruku.smartformatconverter.engine.media.AudioAdvancedSettings
import org.roruku.smartformatconverter.engine.media.AudioSettingsValidator
import org.roruku.smartformatconverter.engine.media.BitrateSelection

class AudioPresetStrategyTest {
    @Test fun everyLossyPresetUsesCodecAwareBitrate() {
        for (name in listOf("Smart Auto", "High Quality", "Balanced", "Small File")) {
            val fromCustom = AudioAdvancedSettings(
                format = "m4a", bitrateKbps = 320, sampleRate = 48000,
                channels = 1, normalizePeak = true, volumePercent = 150
            ).applyPreset(name)
            assertEquals(BitrateSelection.AUTO, fromCustom.bitrateKbps)
            assertNull(fromCustom.sampleRate)
            assertNull(fromCustom.channels)
            assertFalse(fromCustom.normalizePeak)
            assertEquals(100, fromCustom.volumePercent)
        }
        val highAac = AdaptiveAudioPlanner.targetKbps(BitrateSelection.AUTO, 320, 2, false, "High Quality")
        val highOpus = AdaptiveAudioPlanner.targetKbps(BitrateSelection.AUTO, 320, 2, true, "High Quality")
        val balance = AdaptiveAudioPlanner.targetKbps(BitrateSelection.AUTO, 320, 2, false, "Balanced")
        val small = AdaptiveAudioPlanner.targetKbps(BitrateSelection.AUTO, 320, 2, false, "Small File")
        assertEquals(288, highAac)
        assertEquals(192, highOpus)
        assertTrue(highAac > balance && balance > small)
        assertEquals(128, AdaptiveAudioPlanner.targetKbps(BitrateSelection.AUTO, 128, 2, false, "High Quality"))
        assertEquals(320, AdaptiveAudioPlanner.targetKbps(320, 128, 2, false, "Balanced"))
    }

    @Test fun losslessPresetsNeverUseLossyBitrate() {
        for (format in listOf("wav", "flac")) {
            val state = AudioAdvancedSettings(format = format).applyPreset("Small File")
            assertEquals("Lossless", state.preset)
            assertEquals(BitrateSelection.AUTO, state.bitrateKbps)
            assertTrue(state.isLossless)
        }
    }

    @Test fun effectsAndFlacLevelRemainInImmutableJobSnapshot() {
        val settings = AudioAdvancedSettings(
            format = "flac", sampleRate = 48000, channels = 1, normalizePeak = true,
            volumePercent = 75, flacCompressionLevel = 5, preserveMetadata = false
        )
        assertEquals(settings, AudioAdvancedSettings.fromJson(settings.toJson()))
        assertNull(AudioSettingsValidator.validate(settings))
        assertNotNull(AudioSettingsValidator.validate(settings.copy(format = "aac")))
        assertNotNull(AudioSettingsValidator.validate(settings.copy(sampleRate = 96000)))
        assertNotNull(AudioSettingsValidator.validate(settings.copy(
            format = "ogg", flacCompressionLevel = null, sampleRate = 32000
        )))
    }
}
