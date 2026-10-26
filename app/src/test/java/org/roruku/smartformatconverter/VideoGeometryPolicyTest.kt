package org.roruku.smartformatconverter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.roruku.smartformatconverter.engine.media3.VideoGeometryPolicy

class VideoGeometryPolicyTest {
    @Test fun rotationOnlyPreservesDisplayDimensions() {
        assertNull(VideoGeometryPolicy.unexpectedResize(
            1080, 1440, 90, 1440, 1080, 0))
        assertNull(VideoGeometryPolicy.unexpectedResize(
            1080, 1440, 270, 1080, 1440, 90))
    }

    @Test fun unrequestedDeviceFallbackIsDetected() {
        val mismatch = VideoGeometryPolicy.unexpectedResize(
            1080, 1440, 90, 1080, 810, 0)
        assertNotNull(mismatch)
        assertEquals(true, mismatch!!.contains("1440x1080"))
        assertEquals(true, mismatch.contains("1080x810"))
    }

    @Test fun unknownSourceGeometryDoesNotInventAnError() {
        assertNull(VideoGeometryPolicy.unexpectedResize(0, 0, 0, 1080, 810, 0))
    }
}
