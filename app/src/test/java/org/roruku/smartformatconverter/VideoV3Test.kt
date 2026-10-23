package org.roruku.smartformatconverter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.roruku.smartformatconverter.engine.ffmpeg.VideoAdvancedSettings
import org.roruku.smartformatconverter.engine.ffmpeg.VideoCommandBuilder
import org.roruku.smartformatconverter.engine.ffmpeg.VideoSettingsValidator

class VideoV3Test {
    private fun command(settings: VideoAdvancedSettings): List<String> =
        VideoCommandBuilder.build("/cache/input.mov", "/cache/output.mp4", settings)

    private fun hasPair(cmd: List<String>, key: String, value: String): Boolean =
        cmd.windowed(2).any { it[0] == key && it[1] == value }

    @Test
    fun defaultsMatchV3RequestAndPreserveSources() {
        val settings = VideoAdvancedSettings()
        assertEquals("mp4", settings.format)
        assertEquals("Custom", settings.preset)
        assertNull(settings.width)
        assertNull(settings.fps)
        assertEquals("auto", settings.videoCodec)
        assertEquals("auto", settings.encoder)
        assertEquals("crf", settings.rateControl)
        assertEquals(23, settings.crf)
        assertEquals(8000, settings.videoBitrateKbps)
        assertEquals(192, settings.audioBitrateKbps)
        assertEquals("first", settings.audioStream)
        assertEquals(100, settings.volumePercent)
        assertNull(VideoSettingsValidator.validate(settings))

        val cmd = command(settings)
        assertTrue(hasPair(cmd, "-c:v", "libx264"))
        assertTrue(hasPair(cmd, "-crf", "23"))
        assertTrue(hasPair(cmd, "-c:a", "aac"))
        assertTrue(hasPair(cmd, "-b:a", "192k"))
        assertTrue(hasPair(cmd, "-map", "0:a:0?"))
        assertTrue(hasPair(cmd, "-pix_fmt", "yuv420p"))
        assertTrue(hasPair(cmd, "-movflags", "+faststart"))
        assertFalse(cmd.contains("-vf"))
        assertFalse(cmd.contains("-r"))
        assertFalse(cmd.contains("-b:v"))
        assertFalse(cmd.contains("-filter:a"))
    }

    @Test
    fun customResolutionFpsAndQualityGenerateRealOptions() {
        val settings = VideoAdvancedSettings(
            width = 1920,
            height = 1080,
            fps = "29.97",
            crf = 18,
            preventUpscale = false,
            pixelFormat = "yuv420p"
        )
        val cmd = command(settings)
        assertTrue(hasPair(cmd, "-vf",
            "scale=w=1920:h=1080:force_original_aspect_ratio=decrease:force_divisible_by=2"))
        assertTrue(hasPair(cmd, "-r", "29.97"))
        assertTrue(hasPair(cmd, "-crf", "18"))
    }

    @Test
    fun bitrateModeAndAudioVolumeAreApplied() {
        val settings = VideoAdvancedSettings(
            rateControl = "cbr",
            videoBitrateKbps = 8000,
            audioStream = "second",
            volumePercent = 125,
            audioCodec = "aac",
            sampleRate = 48000,
            channels = 2
        )
        val cmd = command(settings)
        assertTrue(hasPair(cmd, "-b:v", "8000k"))
        assertTrue(hasPair(cmd, "-maxrate", "8000k"))
        assertTrue(hasPair(cmd, "-minrate", "8000k"))
        assertTrue(hasPair(cmd, "-bufsize", "16000k"))
        assertTrue(hasPair(cmd, "-map", "0:a:1"))
        assertTrue(hasPair(cmd, "-filter:a", "volume=1.25"))
        assertTrue(hasPair(cmd, "-ar", "48000"))
        assertTrue(hasPair(cmd, "-ac", "2"))
        assertFalse(cmd.contains("-crf"))
    }

    @Test
    fun incompatibleWebmSettingsAreRejected() {
        val invalid = VideoAdvancedSettings(format = "webm", videoCodec = "h264")
        assertNotNull(VideoSettingsValidator.validate(invalid))
        val valid = VideoAdvancedSettings(format = "webm")
        assertNull(VideoSettingsValidator.validate(valid))
        val cmd = command(valid).last()
        assertEquals("/cache/output.mp4", cmd) // supplied test output path is unchanged
    }

    @Test
    fun hardwareNeedsBitrateRateControl() {
        assertNotNull(VideoSettingsValidator.validate(VideoAdvancedSettings(encoder = "hardware")))
        val valid = VideoAdvancedSettings(encoder = "hardware", rateControl = "vbr")
        assertNull(VideoSettingsValidator.validate(valid))
        assertTrue(hasPair(command(valid), "-c:v", "h264_mediacodec"))
    }

    @Test
    fun audioCopyCannotApplyGain() {
        assertNotNull(VideoSettingsValidator.validate(
            VideoAdvancedSettings(audioCodec = "copy", volumePercent = 150)
        ))
        assertNull(VideoSettingsValidator.validate(VideoAdvancedSettings(audioCodec = "copy")))
        assertTrue(hasPair(command(VideoAdvancedSettings(audioCodec = "copy")), "-c:a", "copy"))
    }

    @Test
    fun remuxSkipsEncodingFlags() {
        val preset = VideoAdvancedSettings().applyPreset("Original Quality / Remux")
        assertNull(VideoSettingsValidator.validate(preset))
        val cmd = command(preset)
        assertTrue(hasPair(cmd, "-c", "copy"))
        assertTrue(hasPair(cmd, "-map", "0:v:0"))
        assertTrue(hasPair(cmd, "-map", "0:a:0?"))
        assertFalse(hasPair(cmd, "-map", "0:s?")) // MP4 auto skips unknown subtitles.
        assertFalse(hasPair(cmd, "-map", "0"))
        assertFalse(cmd.contains("-crf"))
        assertFalse(cmd.contains("-vf"))
    }

    @Test
    fun remuxRespectsMuteAndCanKeepMkvSubtitles() {
        val mkv = VideoAdvancedSettings(
            format = "mkv",
            audioCodec = "none",
            audioStream = "none",
            subtitleMode = "copy"
        ).applyPreset("Original Quality / Remux")
        val cmd = command(mkv)
        assertNull(VideoSettingsValidator.validate(mkv))
        assertFalse(cmd.contains("0:a:0?"))
        assertTrue(hasPair(cmd, "-map", "0:s?"))
    }

    @Test
    fun invalidCustomParametersAreRejected() {
        assertNotNull(VideoSettingsValidator.validate(
            VideoAdvancedSettings(width = 1921, height = 1080)
        ))
        assertNotNull(VideoSettingsValidator.validate(
            VideoAdvancedSettings(fps = "999")
        ))
        assertNotNull(VideoSettingsValidator.validate(
            VideoAdvancedSettings(pixelFormat = "arbitrary_ffmpeg_option")
        ))
    }

    @Test
    fun quickPresetsUpdateCrfWithoutChangingCustomFps() {
        val settings = VideoAdvancedSettings(fps = "60").applyPreset("High Quality")
        assertEquals("60", settings.fps)
        assertEquals(18, settings.crf)
        assertEquals("High Quality", settings.preset)
    }
}
