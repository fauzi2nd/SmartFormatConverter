package org.roruku.smartformatconverter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.roruku.smartformatconverter.engine.ffmpeg.AudioAdvancedSettings
import org.roruku.smartformatconverter.engine.ffmpeg.AudioSettingsValidator
import org.roruku.smartformatconverter.engine.ffmpeg.ImageAdvancedSettings
import org.roruku.smartformatconverter.engine.ffmpeg.ImageSettingsValidator
import org.roruku.smartformatconverter.engine.image.ImageConverterEngine
import org.roruku.smartformatconverter.data.repository.ProgressEstimator
import org.roruku.smartformatconverter.data.repository.ActiveConversionJobs
import kotlinx.coroutines.Job

class MediaAdvancedSettingsTest {
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
    fun imageSettingsSupportCropToExactRatio() {
        val defaultSettings = ImageAdvancedSettings()
        assertFalse(defaultSettings.cropToExactRatio)

        val cropSettings = defaultSettings.copy(
            width = 1080,
            height = 1080,
            keepAspectRatio = true,
            cropToExactRatio = true,
            preventUpscale = false
        )
        assertTrue(cropSettings.cropToExactRatio)
        assertTrue(cropSettings.keepAspectRatio)
        assertEquals(1080, cropSettings.width)
        assertEquals(1080, cropSettings.height)
        assertNull(ImageSettingsValidator.validate(cropSettings))
    }

    @Test
    fun calculateResizePlanHandlesCenterCropWithAndWithoutUpscale() {
        val planNoUpscale = ImageConverterEngine.calculateResizePlan(
            srcW = 800,
            srcH = 1600,
            targetW = 1080,
            targetH = 1080,
            maintainRatio = true,
            preventUpscale = true,
            cropToExactRatio = true
        )
        assertEquals(0, planNoUpscale.cropX)
        assertEquals(400, planNoUpscale.cropY)
        assertEquals(800, planNoUpscale.cropW)
        assertEquals(800, planNoUpscale.cropH)
        assertEquals(800, planNoUpscale.outputW)
        assertEquals(800, planNoUpscale.outputH)

        val planWithUpscale = ImageConverterEngine.calculateResizePlan(
            srcW = 800,
            srcH = 1600,
            targetW = 1080,
            targetH = 1080,
            maintainRatio = true,
            preventUpscale = false,
            cropToExactRatio = true
        )
        assertEquals(0, planWithUpscale.cropX)
        assertEquals(400, planWithUpscale.cropY)
        assertEquals(800, planWithUpscale.cropW)
        assertEquals(800, planWithUpscale.cropH)
        assertEquals(1080, planWithUpscale.outputW)
        assertEquals(1080, planWithUpscale.outputH)

        val planLandscapeSquare = ImageConverterEngine.calculateResizePlan(
            srcW = 4000,
            srcH = 3000,
            targetW = 1080,
            targetH = 1080,
            maintainRatio = true,
            preventUpscale = true,
            cropToExactRatio = true
        )
        assertEquals(500, planLandscapeSquare.cropX)
        assertEquals(0, planLandscapeSquare.cropY)
        assertEquals(3000, planLandscapeSquare.cropW)
        assertEquals(3000, planLandscapeSquare.cropH)
        assertEquals(1080, planLandscapeSquare.outputW)
        assertEquals(1080, planLandscapeSquare.outputH)

        val planFit = ImageConverterEngine.calculateResizePlan(
            srcW = 4000,
            srcH = 3000,
            targetW = 1920,
            targetH = 1080,
            maintainRatio = true,
            preventUpscale = true,
            cropToExactRatio = false
        )
        assertEquals(0, planFit.cropX)
        assertEquals(0, planFit.cropY)
        assertEquals(4000, planFit.cropW)
        assertEquals(3000, planFit.cropH)
        assertEquals(1440, planFit.outputW)
        assertEquals(1080, planFit.outputH)
    }

    @Test
    fun onlyImplementedAudioOutputIsValid() {
        assertNull(AudioSettingsValidator.validate(AudioAdvancedSettings(format = "m4a")))
        for (legacy in listOf("mp3", "aac", "flac", "opus", "wav", "ogg")) {
            assertNotNull(AudioSettingsValidator.validate(AudioAdvancedSettings(format = legacy)))
        }
    }

    @Test
    fun unsupportedLegacyAudioEffectsAreRejected() {
        val unsupported = AudioAdvancedSettings(
            format = "m4a", bitrateKbps = 256, sampleRate = 48000, channels = 1,
            volumePercent = 150, preserveMetadata = false)
        assertNull(AudioSettingsValidator.validate(unsupported))
        assertNotNull(
            org.roruku.smartformatconverter.data.repository.Media3OutputPolicy.unsupportedAudioSettings(unsupported)
        )
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

    @Test
    fun activeConversionJobsEnforcesGenerationAndSingleActiveWorker() {
        ActiveConversionJobs.cancelAll()
        val job1 = Job()
        val gen1 = ActiveConversionJobs.beginExecution("job-xyz", job1)
        assertTrue(ActiveConversionJobs.isCurrent("job-xyz", gen1))
        assertTrue(ActiveConversionJobs.isJobActive("job-xyz"))

        // Starting a new attempt cancels the old job and bumps generation
        val job2 = Job()
        val gen2 = ActiveConversionJobs.beginExecution("job-xyz", job2)
        assertTrue(job1.isCancelled)
        assertFalse(ActiveConversionJobs.isCurrent("job-xyz", gen1))
        assertTrue(ActiveConversionJobs.isCurrent("job-xyz", gen2))
        assertEquals(gen1 + 1L, gen2)

        // Cancel / Pause immediately invalidates current generation
        ActiveConversionJobs.cancel("job-xyz")
        assertTrue(job2.isCancelled)
        assertFalse(ActiveConversionJobs.isCurrent("job-xyz", gen2))
        assertFalse(ActiveConversionJobs.isJobActive("job-xyz"))
    }

    @Test
    fun activeConversionJobsRapidPauseResumeCycleIsIdempotentAndSafe() {
        ActiveConversionJobs.cancelAll()
        val initialJob = Job()
        val g1 = ActiveConversionJobs.beginExecution("job-rapid", initialJob)

        // Fast pause
        ActiveConversionJobs.cancel("job-rapid")
        assertTrue(initialJob.isCancelled)
        assertFalse(ActiveConversionJobs.isCurrent("job-rapid", g1))

        // Fast resume
        val resumedJob = Job()
        val g2 = ActiveConversionJobs.beginExecution("job-rapid", resumedJob)
        assertTrue(g2 > g1)
        assertTrue(ActiveConversionJobs.isCurrent("job-rapid", g2))

        // Fast pause again
        ActiveConversionJobs.cancel("job-rapid")
        assertTrue(resumedJob.isCancelled)
        assertFalse(ActiveConversionJobs.isCurrent("job-rapid", g2))
        assertFalse(ActiveConversionJobs.isJobActive("job-rapid"))
    }

    @Test
    fun calculateResizePlanHandlesOddAndTinyDimensionsSafely() {
        val planTinyNoUpscale = ImageConverterEngine.calculateResizePlan(
            srcW = 10,
            srcH = 10,
            targetW = 1080,
            targetH = 1080,
            maintainRatio = true,
            preventUpscale = true,
            cropToExactRatio = true
        )
        assertEquals(10, planTinyNoUpscale.outputW)
        assertEquals(10, planTinyNoUpscale.outputH)

        val planOdd = ImageConverterEngine.calculateResizePlan(
            srcW = 501,
            srcH = 333,
            targetW = 1080,
            targetH = 1080,
            maintainRatio = true,
            preventUpscale = true,
            cropToExactRatio = true
        )
        assertEquals(333, planOdd.cropW)
        assertEquals(333, planOdd.cropH)
        assertEquals(333, planOdd.outputW)
        assertEquals(333, planOdd.outputH)
        assertEquals((501 - 333) / 2, planOdd.cropX)
        assertEquals(0, planOdd.cropY)
    }
}
