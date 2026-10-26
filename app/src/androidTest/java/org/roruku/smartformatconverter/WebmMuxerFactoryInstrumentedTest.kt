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
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
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

    @Test
    fun wrappedAndroidOpusHeaderBecomesStandardWebmCodecPrivate() {
        val opusHead = byteArrayOf(
            79, 112, 117, 115, 72, 101, 97, 100, // OpusHead
            1, 2, 56, 1, // version, stereo, pre-skip 312
            -128, -69, 0, 0, // input sample rate 48000
            0, 0, 0 // output gain, channel mapping family 0
        )
        val littleEndianLength = ByteBuffer.allocate(8)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putLong(opusHead.size.toLong()).array()
        val androidCsd = "AOPUSHDR".toByteArray(Charsets.US_ASCII) +
            littleEndianLength + opusHead

        val delegate = TrackingMuxer()
        val muxer = WebmMuxerFactory().wrap(delegate)
        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_OPUS)
            .setChannelCount(2)
            .setSampleRate(48_000)
            .setInitializationData(listOf(androidCsd))
            .build()

        assertEquals(0, muxer.addTrack(format))
        assertEquals("und", delegate.trackFormat?.language)
        assertArrayEquals(opusHead, delegate.trackFormat!!.initializationData[0])
        muxer.close()
    }

    @Test
    fun standardOpusHeadRemainsUnchanged() {
        val opusHead = byteArrayOf(
            79, 112, 117, 115, 72, 101, 97, 100,
            1, 1, 56, 1, -128, -69, 0, 0, 0, 0, 0
        )
        val delegate = TrackingMuxer()
        val muxer = WebmMuxerFactory().wrap(delegate)
        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_OPUS)
            .setChannelCount(1)
            .setSampleRate(48_000)
            .setInitializationData(listOf(opusHead))
            .build()

        muxer.addTrack(format)
        assertArrayEquals(opusHead, delegate.trackFormat!!.initializationData[0])
        muxer.close()
    }

    @Test
    fun zeroChannelOpusHeadUsesValidEncoderFormatChannelCount() {
        val opusHead = byteArrayOf(
            79, 112, 117, 115, 72, 101, 97, 100,
            1, 0, 56, 1, -128, -69, 0, 0, 0, 0, 0
        )
        val delegate = TrackingMuxer()
        val muxer = WebmMuxerFactory().wrap(delegate)
        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_OPUS)
            .setChannelCount(2)
            .setSampleRate(48_000)
            .setInitializationData(listOf(opusHead))
            .build()

        muxer.addTrack(format)
        assertEquals(2, delegate.trackFormat!!.channelCount)
        assertEquals(2, delegate.trackFormat!!.initializationData[0][9].toInt())
        muxer.close()
    }

    @Test
    fun zeroChannelOpusHeadUsesKnownSourceChannelsWhenEncoderOmitsThem() {
        val opusHead = byteArrayOf(
            79, 112, 117, 115, 72, 101, 97, 100,
            1, 0, 56, 1, -128, -69, 0, 0, 0, 0, 0
        )
        val delegate = TrackingMuxer()
        val muxer = WebmMuxerFactory(sourceAudioChannels = 2).wrap(delegate)
        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_OPUS)
            .setSampleRate(48_000)
            .setInitializationData(listOf(opusHead))
            .build()

        muxer.addTrack(format)
        assertEquals(2, delegate.trackFormat!!.channelCount)
        assertEquals(2, delegate.trackFormat!!.initializationData[0][9].toInt())
        muxer.close()
    }

    @Test
    fun malformedOpusHeaderIsRejectedBeforeWritingTrack() {
        val delegate = TrackingMuxer()
        val muxer = WebmMuxerFactory().wrap(delegate)
        val format = Format.Builder()
            .setSampleMimeType(MimeTypes.AUDIO_OPUS)
            .setChannelCount(2)
            .setSampleRate(48_000)
            .setInitializationData(listOf("not-opus".toByteArray()))
            .build()
        assertThrows(IllegalArgumentException::class.java) {
            muxer.addTrack(format)
        }
        assertEquals(null, delegate.trackFormat)
        muxer.close()
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
