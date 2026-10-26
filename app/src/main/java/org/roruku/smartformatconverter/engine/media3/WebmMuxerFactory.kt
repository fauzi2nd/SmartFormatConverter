package org.roruku.smartformatconverter.engine.media3

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Metadata
import androidx.media3.common.util.CodecSpecificDataUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.muxer.Muxer
import androidx.media3.muxer.SeekableMuxerOutput
import androidx.media3.muxer.WebmMuxer
import com.google.common.collect.ImmutableList

/**
 * Media3's WebmMuxer (1.11.x) requires a non-null Format.language when writing
 * each track entry, including VIDEO tracks. Formats emitted by Transformer
 * generally leave video language unset. Normalize that optional metadata at the
 * muxer boundary instead of failing the entire VP9/Opus export with code 1001.
 * "und" means undetermined; never invent a spoken language.
 */
@OptIn(UnstableApi::class)
internal class WebmMuxerFactory(private val sourceAudioChannels: Int = 0) : Muxer.Factory {
    override fun create(path: String): Muxer =
        wrap(WebmMuxer.Builder(SeekableMuxerOutput.of(path)).build(), path)

    /**
     * Android Opus encoders may return csd-0 in a native AOPUSHDR envelope
     * (8-byte signature, 8-byte little-endian header length, OpusHead bytes).
     * Media3 1.11.1 WebmMuxer directly copies csd-0 into WebM CodecPrivate
     * without stripping this envelope. FFmpeg then rejects the Opus extradata.
     * Normalize it to the RFC 7845 OpusHead payload before the muxer writes it.
     *
     * Transformer also forwards container metadata through addMetadataEntry(),
     * but Media3's WebmMuxer does not implement that method.
     */
    internal fun wrap(delegate: Muxer, outputPath: String? = null): Muxer = object : Muxer by delegate {
        override fun addTrack(format: Format): Int {
            val normalized = if (format.sampleMimeType == MimeTypes.AUDIO_OPUS) {
                // This Media3 utility understands both OpusHead and Android's
                // AOPUSHDR-wrapped csd-0. Do not synthesize missing codec data:
                // a missing or invalid OpusHead must fail, not produce bad WebM.
                val opusHead = CodecSpecificDataUtil.getOpusInitializationData(format)
                val normalizedHead = WebmOpusHeaderPolicy.normalize(
                    opusHead, format.channelCount, sourceAudioChannels
                )
                if (normalizedHead.recoveredChannels) {
                    android.util.Log.w(
                        "WebmMuxerFactory",
                        "Recovered 0 OpusHead channels from encoded/source track: " +
                            normalizedHead.channels
                    )
                }
                format.buildUpon()
                    .setInitializationData(listOf(normalizedHead.header))
                    .setChannelCount(normalizedHead.channels)
                    // Opus packets in WebM are sampled at 48 kHz.
                    .setSampleRate(48_000)
                    .build()
            } else {
                format
            }
            val safeFormat = if (normalized.language.isNullOrBlank()) {
                normalized.buildUpon().setLanguage("und").build()
            } else {
                normalized
            }
            return delegate.addTrack(safeFormat)
        }

        override fun addMetadataEntry(metadataEntry: Metadata.Entry) {
            // WebmMuxer.addMetadataEntry is unsupported. Metadata is not encoded
            // in WebM; video/audio sample writing remains delegated unchanged.
        }

        override fun close() {
            delegate.close()
            if (outputPath != null) {
                // Media3 1.11.1 incorrectly serializes Opus Audio/BitDepth as
                // float pcmEncoding. Rewrite only the offending metadata
                // after closing, before the output is validated or published.
                val repaired = WebmOpusBitDepthRepair.repair(java.io.File(outputPath))
                if (repaired > 0) android.util.Log.i(
                    "WebmMuxerFactory", "Neutralized $repaired invalid Opus BitDepth element(s)"
                )
            }
        }
    }

    override fun getSupportedSampleMimeTypes(trackType: Int): ImmutableList<String> =
        when (trackType) {
            C.TRACK_TYPE_VIDEO -> ImmutableList.of(MimeTypes.VIDEO_VP9)
            C.TRACK_TYPE_AUDIO -> ImmutableList.of(MimeTypes.AUDIO_OPUS)
            else -> ImmutableList.of()
        }
}
