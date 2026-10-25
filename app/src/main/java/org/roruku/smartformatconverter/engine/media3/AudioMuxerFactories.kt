package org.roruku.smartformatconverter.engine.media3

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.muxer.AacMuxer
import androidx.media3.muxer.Muxer
import androidx.media3.muxer.OggMuxer
import com.google.common.collect.ImmutableList
import java.io.FileOutputStream
import java.nio.channels.Channels

/** Raw ADTS AAC is different from AAC in an MP4/M4A container. */
@OptIn(UnstableApi::class)
internal class AdtsAacMuxerFactory : Muxer.Factory {
    override fun create(path: String): Muxer = AacMuxer(FileOutputStream(path))
    override fun getSupportedSampleMimeTypes(trackType: Int): ImmutableList<String> =
        if (trackType == C.TRACK_TYPE_AUDIO) ImmutableList.of(MimeTypes.AUDIO_AAC)
        else ImmutableList.of()
}

/** Ogg-Opus, not WebM or raw .opus packets. */
@OptIn(UnstableApi::class)
internal class OggOpusMuxerFactory : Muxer.Factory {
    override fun create(path: String): Muxer =
        OggMuxer.Builder(Channels.newChannel(FileOutputStream(path))).build()

    override fun getSupportedSampleMimeTypes(trackType: Int): ImmutableList<String> =
        if (trackType == C.TRACK_TYPE_AUDIO) ImmutableList.of(MimeTypes.AUDIO_OPUS)
        else ImmutableList.of()
}
