package org.roruku.smartformatconverter

import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.container.Mp4OrientationData
import androidx.media3.muxer.BufferInfo
import androidx.media3.muxer.Muxer
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.roruku.smartformatconverter.engine.media3.WebmMuxerFactory

/**
 * Regression for MP4 files with embedded metadata causing Media3 error 1001.
 * No device encoder is needed; test the exact muxer adapter boundary.
 */
@OptIn(UnstableApi::class)
class WebmMuxerFactoryTest {
    @Test
    fun unsupportedMp4MetadataIsNotForwardedToWebmMuxer() {
        val delegate = TrackingMuxer()
        val muxer = WebmMuxerFactory().wrap(delegate)

        // Media3's WebmMuxer throws UnsupportedOperationException for this API.
        // The adapter must not pass the metadata on and abort a valid export.
        muxer.addMetadataEntry(Mp4OrientationData(90))

        assertEquals(0, delegate.metadataCalls)
        muxer.close()
        assertTrue(delegate.closed)
    }

    @Test
    fun missingTrackLanguageIsNormalizedWithoutDroppingSamples() {
        val delegate = TrackingMuxer()
        val muxer = WebmMuxerFactory().wrap(delegate)
        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.VIDEO_VP9)
            .setWidth(320)
            .setHeight(240)
            .build()

        assertEquals(0, muxer.addTrack(format))
        assertEquals("und", delegate.trackFormat?.language)

        muxer.writeSampleData(0, ByteBuffer.allocate(1), BufferInfo(0L, 1, 0))
        assertEquals(1, delegate.sampleCalls)
        muxer.close()
        assertTrue(delegate.closed)
    }

    private class TrackingMuxer : Muxer {
        var trackFormat: Format? = null
        var metadataCalls = 0
        var sampleCalls = 0
        var closed = false

        override fun addTrack(format: Format): Int {
            trackFormat = format
            return 0
        }

        override fun addMetadataEntry(metadataEntry: Metadata.Entry) {
            metadataCalls++
            throw UnsupportedOperationException("Media3 WebmMuxer does not support metadata")
        }

        override fun writeSampleData(trackId: Int, byteBuffer: ByteBuffer, bufferInfo: BufferInfo) {
            sampleCalls++
        }

        override fun close() {
            closed = true
        }
    }
}
