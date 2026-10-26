package org.roruku.smartformatconverter

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.roruku.smartformatconverter.engine.media3.WebmOpusHeaderPolicy

class WebmOpusHeaderPolicyTest {
    private fun header(channels: Int): ByteArray = byteArrayOf(
        79, 112, 117, 115, 72, 101, 97, 100,
        1, channels.toByte(), 56, 1, -128, -69, 0, 0, 0, 0, 0
    )

    @Test fun standardStereoHeaderRemainsUnchanged() {
        val source = header(2)
        val result = WebmOpusHeaderPolicy.normalize(source, 2)
        assertArrayEquals(source, result.header)
        assertEquals(2, result.channels)
        assertFalse(result.recoveredChannels)
    }

    @Test fun zeroChannelHeaderUsesEncoderMetadataWithoutMutatingInput() {
        val source = header(0)
        val result = WebmOpusHeaderPolicy.normalize(source, 2)
        assertEquals(2, result.channels)
        assertEquals(2, result.header[9].toInt())
        assertEquals(0, source[9].toInt())
        assertTrue(result.recoveredChannels)
    }

    @Test fun zeroChannelHeaderUsesKnownSourceOnlyWhenFormatIsUnspecified() {
        assertEquals(1, WebmOpusHeaderPolicy.normalize(header(0), -1, 1).channels)
        assertEquals(2, WebmOpusHeaderPolicy.normalize(header(0), 0, 2).channels)
    }

    @Test fun zeroChannelHeaderWithoutReliableMetadataFails() {
        assertThrows(IllegalArgumentException::class.java) {
            WebmOpusHeaderPolicy.normalize(header(0), -1, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebmOpusHeaderPolicy.normalize(header(0), -1, 6)
        }
    }

    @Test fun exportedHeaderGateRejectsMissingOrZeroChannelCodecPrivate() {
        assertEquals(null, WebmOpusHeaderPolicy.verifyExportedHeader(header(2), 2))
        assertTrue(WebmOpusHeaderPolicy.verifyExportedHeader(header(0), 2) != null)
        assertTrue(WebmOpusHeaderPolicy.verifyExportedHeader(null, 2) != null)
        assertTrue(WebmOpusHeaderPolicy.verifyExportedHeader(header(2), 0) != null)
        assertTrue(WebmOpusHeaderPolicy.verifyExportedHeader(header(1), 2) != null)
    }

    @Test fun mismatchedAndInvalidHeadersAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            WebmOpusHeaderPolicy.normalize(header(1), 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebmOpusHeaderPolicy.normalize(header(3), 3)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebmOpusHeaderPolicy.normalize("not-opus".toByteArray(), 2)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WebmOpusHeaderPolicy.normalize(header(2).copyOfRange(0, 18), 2)
        }
    }
}
