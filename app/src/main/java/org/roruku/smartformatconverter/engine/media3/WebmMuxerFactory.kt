package org.roruku.smartformatconverter.engine.media3

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.muxer.Muxer
import androidx.media3.muxer.SeekableMuxerOutput
import androidx.media3.muxer.WebmMuxer
import com.google.common.collect.ImmutableList

/** Transformer bridge: the default muxer writes MP4, not WebM. */
@OptIn(UnstableApi::class)
internal class WebmMuxerFactory : Muxer.Factory {
    override fun create(path: String): Muxer =
        WebmMuxer.Builder(SeekableMuxerOutput.of(path)).build()

    override fun getSupportedSampleMimeTypes(trackType: Int): ImmutableList<String> =
        when (trackType) {
            C.TRACK_TYPE_VIDEO -> ImmutableList.of(MimeTypes.VIDEO_VP9)
            C.TRACK_TYPE_AUDIO -> ImmutableList.of(MimeTypes.AUDIO_OPUS)
            else -> ImmutableList.of()
        }
}
