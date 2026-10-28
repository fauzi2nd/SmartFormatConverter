package org.roruku.smartformatconverter.engine.media3

/**
 * Repairs the STREAMINFO total-samples field of a complete, native FLAC file.
 *
 * Some Android MediaCodec FLAC encoders emit STREAMINFO with zero total samples.
 * The stream can decode, but ExoPlayer then reports an unknown duration and
 * cannot reliably seek. The value is the number of PCM frames PER CHANNEL,
 * not the number of interleaved 16-bit samples.
 *
 * https://www.rfc-editor.org/rfc/rfc9639.html#section-8.2
 */
internal object FlacStreamInfoSamples {
    private const val STREAMINFO_TOTAL_LENGTH = 4 + 4 + 34
    private const val SAMPLE_FIELDS_START = 18
    private const val TOTAL_SAMPLES_BITS = 36
    private const val TOTAL_MASK = (1L shl TOTAL_SAMPLES_BITS) - 1

    fun totalSamples(header: ByteArray): Long? {
        if (!isStreamInfo(header)) return null
        var data = 0L
        for (i in SAMPLE_FIELDS_START until SAMPLE_FIELDS_START + 8) {
            data = (data shl 8) or (header[i].toLong() and 0xff)
        }
        return data and TOTAL_MASK
    }

    /**
     * Set only when zero/unknown; preserve existing encoder metadata, sample
     * rate, channel count, bit depth and its MD5 checksum unchanged.
     */
    fun withKnownTotalSamples(header: ByteArray, sampleFrames: Long): ByteArray? {
        if (!isStreamInfo(header) || sampleFrames !in 1L..TOTAL_MASK) return null
        if (totalSamples(header) != 0L) return header.copyOf()
        val result = header.copyOf()
        var value = 0L
        for (i in SAMPLE_FIELDS_START until SAMPLE_FIELDS_START + 8) {
            value = (value shl 8) or (result[i].toLong() and 0xff)
        }
        value = (value and TOTAL_MASK.inv()) or sampleFrames
        for (i in SAMPLE_FIELDS_START + 7 downTo SAMPLE_FIELDS_START) {
            result[i] = (value and 0xff).toByte()
            value = value ushr 8
        }
        return result
    }

    private fun isStreamInfo(bytes: ByteArray): Boolean {
        if (bytes.size < STREAMINFO_TOTAL_LENGTH) return false
        if (bytes[0] != 'f'.code.toByte() || bytes[1] != 'L'.code.toByte() ||
            bytes[2] != 'a'.code.toByte() || bytes[3] != 'C'.code.toByte()) return false
        val blockType = bytes[4].toInt() and 0x7f
        val length = (bytes[5].toInt() and 0xff shl 16) or
            ((bytes[6].toInt() and 0xff) shl 8) or (bytes[7].toInt() and 0xff)
        return blockType == 0 && length == 34
    }
}
