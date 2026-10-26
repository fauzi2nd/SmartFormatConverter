package org.roruku.smartformatconverter.engine.media3

/**
 * Pure OpusHead validation/normalization, exercised by local JVM tests.
 * Android encoders sometimes set the channel-count byte to zero while their
 * MediaCodec format (or known source track) still reports mono/stereo.
 * The encoded packets are never changed; only header metadata is repaired.
 */
internal object WebmOpusHeaderPolicy {
    data class Result(val header: ByteArray, val channels: Int, val recoveredChannels: Boolean)

    fun normalize(opusHead: ByteArray, encodedFormatChannels: Int, sourceChannels: Int = 0): Result {
        require(opusHead.size >= 19 && opusHead.copyOfRange(0, 8)
            .contentEquals("OpusHead".toByteArray(Charsets.US_ASCII))) {
            "WebM Opus codec private data is missing a complete OpusHead"
        }
        require((opusHead[8].toInt() and 0xFF) in 1..15) {
            "Invalid OpusHead version"
        }
        val mappingFamily = opusHead[18].toInt() and 0xFF
        val headerChannels = opusHead[9].toInt() and 0xFF
        val formatChannels = encodedFormatChannels.takeIf { it in 1..255 }
        val fallback = formatChannels?.takeIf { it in 1..2 }
            ?: sourceChannels.takeIf { it in 1..2 }
        val channels = if (headerChannels > 0) headerChannels else fallback
            ?: throw IllegalArgumentException(
                "OpusHead declares 0 channels and neither encoder nor source provides reliable mono/stereo information"
            )
        if (headerChannels > 0 && formatChannels != null && formatChannels != headerChannels) {
            throw IllegalArgumentException(
                "OpusHead channel count ($headerChannels) disagrees with encoder ($formatChannels)"
            )
        }
        if (mappingFamily == 0) {
            require(channels in 1..2 && opusHead.size == 19) {
                "Opus channel mapping family 0 requires mono/stereo and a 19-byte OpusHead"
            }
        } else {
            require(opusHead.size >= 21 + channels) {
                "Opus channel mapping $mappingFamily has an incomplete channel mapping table"
            }
            require(headerChannels > 0) {
                "Cannot safely recover a zero channel count for multichannel Opus"
            }
        }
        val normalized = opusHead.copyOf()
        normalized[9] = channels.toByte()
        return Result(normalized, channels, headerChannels == 0)
    }
}
