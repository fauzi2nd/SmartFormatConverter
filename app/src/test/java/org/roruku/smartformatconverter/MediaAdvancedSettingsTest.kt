package org.roruku.smartformatconverter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.roruku.smartformatconverter.engine.ffmpeg.AudioAdvancedSettings
import org.roruku.smartformatconverter.engine.ffmpeg.AudioCommandBuilder
import org.roruku.smartformatconverter.engine.ffmpeg.AudioSettingsValidator
import org.roruku.smartformatconverter.engine.ffmpeg.ImageAdvancedSettings
import org.roruku.smartformatconverter.engine.ffmpeg.ImageSettingsValidator
import org.roruku.smartformatconverter.data.repository.ProgressEstimator

class MediaAdvancedSettingsTest {
    private fun hasOption(args: List<String>, option: String, value: String) =
        args.windowed(2).any { it[0] == option && it[1] == value }

    @Test
    fun jpegDefaultRetains444AndNeverUpscales() {
        val settings = ImageAdvancedSettings()
        assertEquals("jpg", settings.format)
        assertEquals(100, settings.jpegQuality)
        assertEquals("444", settings.jpegSubsampling)
        assertTrue(settings.preventUpscale)
        assertNull(ImageSettingsValidator.validate(settings))
    }

    @Test
    fun imageValidationCatchesInvalidDimensionsAndChroma() {
        assertNotNull(ImageSettingsValidator.validate(
            ImageAdvancedSettings(width = 1024, height = null)))
        assertNotNull(ImageSettingsValidator.validate(
            ImageAdvancedSettings(width = 0, height = 720)))
        assertNotNull(ImageSettingsValidator.validate(
            ImageAdvancedSettings(jpegSubsampling = "999")))
        assertNotNull(ImageSettingsValidator.validate(
            ImageAdvancedSettings(format = "bmp")))
        assertNull(ImageSettingsValidator.validate(
            ImageAdvancedSettings(format = "webp", webpLossless = true,
                width = 1920, height = 1080)))
    }

    @Test
    fun imagePresetsAreFormatAware() {
        val webp = ImageAdvancedSettings(format = "webp").applyPreset("High Quality")
        assertFalse(webp.webpLossless)
        assertEquals(100, webp.webpQuality)
        val compressed = webp.applyPreset("Small File")
        assertFalse(compressed.webpLossless)
        assertEquals(75, compressed.webpQuality)
        val jpeg = ImageAdvancedSettings().applyPreset("Small File")
        assertEquals("420", jpeg.jpegSubsampling)
    }

    @Test
    fun audioCodecAndLosslessControlsAreCorrectForEveryOutput() {
        val formats = mapOf(
            "mp3" to "libmp3lame", "aac" to "aac", "m4a" to "aac",
            "wav" to "pcm_s16le", "flac" to "flac", "ogg" to "libvorbis",
            "opus" to "libopus"
        )
        for ((format, codec) in formats) {
            val s = AudioAdvancedSettings(format = format)
            assertNull(AudioSettingsValidator.validate(s))
            val args = AudioCommandBuilder.build("/in", "/out.$format", s)
            assertTrue(hasOption(args, "-c:a", codec))
            assertTrue(args.contains("/out.$format"))
            if (format in AudioSettingsValidator.losslessFormats) {
                assertFalse(args.contains("-b:a"))
            } else {
                assertTrue(hasOption(args, "-b:a", "192k"))
            }
        }
    }

    @Test
    fun audioVolumeSampleRateChannelsAndMetadataApply() {
        val s = AudioAdvancedSettings(format = "m4a", bitrateKbps = 256,
            sampleRate = 48000, channels = 1, volumePercent = 150,
            preserveMetadata = false)
        val args = AudioCommandBuilder.build("/in", "/out.m4a", s)
        assertTrue(hasOption(args, "-b:a", "256k"))
        assertTrue(hasOption(args, "-ar", "48000"))
        assertTrue(hasOption(args, "-ac", "1"))
        assertTrue(hasOption(args, "-af", "volume=1.50"))
        assertTrue(hasOption(args, "-map_metadata", "-1"))
    }

    @Test
    fun opusRequires48KhzAndHasExplicitRate() {
        assertNotNull(AudioSettingsValidator.validate(
            AudioAdvancedSettings(format = "opus", sampleRate = 44100)))
        val args = AudioCommandBuilder.build("/in", "/out.opus",
            AudioAdvancedSettings(format = "opus"))
        assertTrue(hasOption(args, "-ar", "48000"))
    }

    @Test
    fun progressEstimatorUsesRealElapsedTime() {
        var clock = 0L
        val estimator = ProgressEstimator { clock }
        assertEquals("Measuring…", estimator.measure(0.1f).eta)
        clock = 2_000_000_000L
        val half = estimator.measure(0.5f)
        assertEquals("00:02", half.eta)
        assertEquals("25.0%/s", half.speed)
        clock = 4_000_000_000L
        val nearDone = estimator.measure(0.75f)
        assertEquals("00:02", nearDone.eta)
    }
}
