package org.roruku.smartformatconverter.engine.media3

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
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
    override fun create(path: String): Muxer {
        val delegate = WebmMuxer.Builder(SeekableMuxerOutput.of(path)).build()
        return object : Muxer by delegate {
            override fun addTrack(format: Format): Int {
                val safeFormat = if (format.language.isNullOrBlank()) {
                    format.buildUpon().setLanguage("und").build()
                } else {
                    format
                }
                return delegate.addTrack(safeFormat)
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
