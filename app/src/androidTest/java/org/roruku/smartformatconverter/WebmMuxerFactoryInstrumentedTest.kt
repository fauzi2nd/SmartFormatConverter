package org.roruku.smartformatconverter

import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.muxer.BufferInfo
import androidx.media3.muxer.Muxer
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.roruku.smartformatconverter.engine.media3.WebmMuxerFactory

/** Device-side coverage for Format language handling by the WebM muxer adapter. */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class WebmMuxerFactoryInstrumentedTest {
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
        var sampleCalls = 0
        var closed = false

        override fun addTrack(format: Format): Int {
            trackFormat = format
            return 0
        }

        override fun addMetadataEntry(metadataEntry: Metadata.Entry) {
            throw UnsupportedOperationException("Metadata is not supported")
        }

        override fun writeSampleData(trackId: Int, byteBuffer: ByteBuffer, bufferInfo: BufferInfo) {
            sampleCalls++
        }

        override fun close() {
            closed = true
        }
    }
}
