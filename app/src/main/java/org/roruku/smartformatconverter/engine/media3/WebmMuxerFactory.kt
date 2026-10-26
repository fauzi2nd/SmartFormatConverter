package org.roruku.smartformatconverter.engine.media3

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Metadata
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
internal class WebmMuxerFactory : Muxer.Factory {
    override fun create(path: String): Muxer =
        wrap(WebmMuxer.Builder(SeekableMuxerOutput.of(path)).build())

    /**
     * Transformer forwards Format.metadata through Muxer.addMetadataEntry().
     * Media3 1.11.1 WebmMuxer unconditionally throws UnsupportedOperationException
     * for this call, which aborts otherwise valid MP4 -> WebM exports with code
     * 1001. WebM output supports the encoded VP9/Opus tracks, but not Media3's
     * container-level metadata API; discard those entries at this boundary.
     */
    internal fun wrap(delegate: Muxer): Muxer = object : Muxer by delegate {
        override fun addTrack(format: Format): Int {
            val safeFormat = if (format.language.isNullOrBlank()) {
                format.buildUpon().setLanguage("und").build()
            } else {
                format
            }
            return delegate.addTrack(safeFormat)
        }

        override fun addMetadataEntry(metadataEntry: Metadata.Entry) {
            // WebmMuxer.addMetadataEntry is unsupported. Metadata is not encoded
            // in WebM; video/audio sample writing remains delegated unchanged.
        }
    }

    override fun getSupportedSampleMimeTypes(trackType: Int): ImmutableList<String> =
        when (trackType) {
            C.TRACK_TYPE_VIDEO -> ImmutableList.of(MimeTypes.VIDEO_VP9)
            C.TRACK_TYPE_AUDIO -> ImmutableList.of(MimeTypes.AUDIO_OPUS)
            else -> ImmutableList.of()
        }
}
