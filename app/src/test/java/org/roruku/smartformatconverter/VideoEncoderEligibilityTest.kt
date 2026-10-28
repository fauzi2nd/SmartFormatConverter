package org.roruku.smartformatconverter

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.roruku.smartformatconverter.engine.media.VideoEncoderEligibility

class VideoEncoderEligibilityTest {
    private val software = listOf(
        VideoEncoderEligibility.Candidate("c2.android.vp9.encoder", false, true)
    )
    private val unsupported1080 = listOf(
        VideoEncoderEligibility.Candidate("c2.android.vp9.encoder", false, false)
    )

    @Test fun noHardwareForVp9IsRejectedBeforeQueue() {
        assertNotNull(VideoEncoderEligibility.rejection(
            "video/x-vnd.on2.vp9", "hardware", 720, 960, 30f, software
        ))
        assertNull(VideoEncoderEligibility.rejection(
            "video/x-vnd.on2.vp9", "auto", 720, 960, 30f, software
        ))
    }

    @Test fun reportedUnsupported1080BlocksBut720Works() {
        assertNotNull(VideoEncoderEligibility.rejection(
            "video/x-vnd.on2.vp9", "auto", 1080, 1440, 30f, unsupported1080
        ))
        assertNull(VideoEncoderEligibility.rejection(
            "video/x-vnd.on2.vp9", "software", 720, 960, 30f, software
        ))
    }

    @Test fun unknownSizeSupportIsNotMistakenForRejection() {
        val unknown = listOf(VideoEncoderEligibility.Candidate("vp9-software", false, null))
        assertNull(VideoEncoderEligibility.rejection("video/x-vnd.on2.vp9",
            "auto", 1320, 1760, 30f, unknown))
    }

    @Test fun unavailableExactEncoderIsRejected() {
        assertNotNull(VideoEncoderEligibility.rejection(
            "video/x-vnd.on2.vp9", "codec:missing.vendor", 720, 960, 30f, software
        ))
    }
}
