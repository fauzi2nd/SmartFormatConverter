package org.roruku.smartformatconverter.engine.media3

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Writes Matroska TrackEntry/DefaultDuration (nanoseconds per frame) without
 * changing any container offsets, clusters, cues, or encoded packet bytes.
 *
 * Media3 1.11.1 WebmMuxer omits this element, so ffprobe reports
 * avg_frame_rate=0/0 even when frame timestamps have a stable cadence.
 *
 * After WebmOpusBitDepthRepair has replaced the invalid 7-byte Opus BitDepth
 * with a 7-byte EBML Void, remove that Void from Audio and exchange the freed
 * bytes for a video DefaultDuration. Remove video's optional 3-byte FlagLacing
 * and add a 2-byte Void. Audio shrinks 7 bytes; video grows 7 bytes. The
 * complete Tracks payload and every offset after it remain byte-identical.
 * Leave unusual files unchanged instead of risking invalid seeks.
 */
internal object WebmDefaultDurationWriter {
    private const val SEGMENT = 0x18538067L
    private const val TRACKS = 0x1654AE6BL
    private const val TRACK_ENTRY = 0xAEL
    private const val CODEC_ID = 0x86L
    private const val AUDIO = 0xE1L
    private const val FLAG_LACING = 0x9CL
    private const val DEFAULT_DURATION = 0x23E383L
    private const val VOID = 0xECL
    private const val CLUSTER = 0x1F43B675L
    private const val HEADER_MAX = 1024 * 1024
    private val repairedBitDepthVoid = byteArrayOf(
        0xEC.toByte(), 0x85.toByte(), 0, 0, 0, 0, 0
    )

    private data class Element(
        val id: Long, val start: Int, val payload: Int, val end: Long, val sizeWidth: Int
    )

    private fun width(first: Int, max: Int): Int? =
        (1..max).firstOrNull { first and (0x80 ushr (it - 1)) != 0 }

    private fun parse(bytes: ByteArray, offset: Long, parentEnd: Long): Element? {
        if (offset < 0 || offset >= bytes.size) return null
        val pos = offset.toInt()
        val idWidth = width(bytes[pos].toInt() and 255, 4) ?: return null
        val sizePos = pos + idWidth
        if (sizePos >= bytes.size) return null
        var id = 0L
        for (i in 0 until idWidth) {
            id = (id shl 8) or (bytes[pos + i].toLong() and 255)
        }
        val sizeWidth = width(bytes[sizePos].toInt() and 255, 8) ?: return null
        if (sizePos + sizeWidth > bytes.size) return null
        var payloadSize = (bytes[sizePos].toLong() and (255 ushr sizeWidth).toLong())
        for (i in 1 until sizeWidth) {
            payloadSize = (payloadSize shl 8) or
                (bytes[sizePos + i].toLong() and 255)
        }
        val payload = sizePos + sizeWidth
        val unknownSize = payloadSize == (1L shl (sizeWidth * 7)) - 1
        val end = if (unknownSize) parentEnd else payload + payloadSize
        if (end < payload || end > parentEnd) return null
        return Element(id, pos, payload, end, sizeWidth)
    }

    private fun children(bytes: ByteArray, item: Element): List<Element>? {
        if (item.end > bytes.size) return null
        val result = mutableListOf<Element>()
        var pos = item.payload.toLong()
        while (pos < item.end) {
            val child = parse(bytes, pos, item.end) ?: return null
            if (child.end <= pos) return null
            result.add(child)
            pos = child.end
        }
        return result
    }

    private fun raw(bytes: ByteArray, item: Element): ByteArray =
        bytes.copyOfRange(item.start, item.end.toInt())

    private fun packSize(value: Int, sizeWidth: Int): ByteArray? {
        if (value < 0 || sizeWidth !in 1..8 ||
            value.toLong() >= (1L shl (7 * sizeWidth)) - 1) return null
        return ((1L shl (7 * sizeWidth)) or value.toLong())
            .let { encoded -> ByteArray(sizeWidth) { i ->
                (encoded ushr (8 * (sizeWidth - 1 - i))).toByte()
            } }
    }

    private fun element(id: Long, sizeWidth: Int, payload: ByteArray): ByteArray? {
        val size = packSize(payload.size, sizeWidth) ?: return null
        val bytes = byteArrayOf(
            *((id.toString(16).let { if (it.length % 2 == 0) it else "0$it" })
                .chunked(2).map { it.toInt(16).toByte() }.toByteArray())
        )
        return bytes + size + payload
    }

    private fun join(parts: List<ByteArray>): ByteArray {
        val output = ByteArray(parts.sumOf { it.size })
        var position = 0
        for (part in parts) {
            part.copyInto(output, position)
            position += part.size
        }
        return output
    }

    /**
     * Emits metadata only for near-CFR video. Widely variable timestamps
     * (true VFR) cannot be truthfully represented by a single DefaultDuration.
     */
    fun frameDurationNs(timestampsUs: List<Long>): Long? {
        if (timestampsUs.size < 3) return null
        val deltas = timestampsUs.zipWithNext { a, b -> b - a }
        if (deltas.any { it <= 0 }) return null
        val meanUs = deltas.average()
        if (meanUs !in 4_166.0..2_000_000.0) return null
        // Allow small Matroska 1-ms timestamp quantization and occasional
        // encoder jitter, but reject strongly variable frame cadences.
        val outliers = deltas.count {
            abs(it - meanUs) > maxOf(2_000.0, meanUs * 0.10)
        }
        if (outliers > maxOf(1, deltas.size / 20)) return null
        val nanos = (meanUs * 1000.0).roundToLong()
        return nanos.takeIf { it in 1..0xFFFF_FFFFL }
    }

    /**
     * @return true when video avg_frame_rate metadata was written.
     * Zero-copy encoded samples are preserved. Files without the safe audio
     * padding are deliberately left unchanged.
     */
    fun write(file: File, durationNs: Long): Boolean {
        if (!file.isFile || durationNs !in 1..0xFFFF_FFFFL) return false
        RandomAccessFile(file, "rw").use { access ->
            val total = access.length()
            val header = ByteArray(minOf(total, HEADER_MAX.toLong()).toInt())
            access.readFully(header)
            var rootAt = 0L
            var segment: Element? = null
            while (rootAt < header.size) {
                val item = parse(header, rootAt, total) ?: return false
                if (item.id == SEGMENT) {
                    segment = item
                    break
                }
                rootAt = item.end
            }
            val container = segment ?: return false
            var position = container.payload.toLong()
            var tracks: Element? = null
            while (position < container.end && position < header.size) {
                val item = parse(header, position, container.end) ?: return false
                if (item.id == TRACKS) {
                    tracks = item
                    break
                }
                if (item.id == CLUSTER) break
                position = item.end
            }
            val table = tracks ?: return false
            val trackEntries = children(header, table) ?: return false
            val newEntries = mutableListOf<ByteArray>()
            var changedAudio = false
            var changedVideo = false
            for (track in trackEntries) {
                if (track.id != TRACK_ENTRY) {
                    newEntries.add(raw(header, track))
                    continue
                }
                val fields = children(header, track) ?: return false
                val codec = fields.firstOrNull { it.id == CODEC_ID }?.let {
                    String(header, it.payload, (it.end - it.payload).toInt(), Charsets.US_ASCII)
                }
                val modified = mutableListOf<ByteArray>()
                if (codec == "A_OPUS") {
                    for (field in fields) {
                        if (field.id != AUDIO) {
                            modified.add(raw(header, field))
                            continue
                        }
                        val audioFields = children(header, field) ?: return false
                        val candidates = audioFields.filter {
                            it.id == VOID && raw(header, it).contentEquals(repairedBitDepthVoid)
                        }
                        if (candidates.size != 1 || changedAudio) return false
                        val newAudio = join(audioFields.filter { it != candidates[0] }.map {
                            raw(header, it)
                        })
                        modified.add(element(AUDIO, field.sizeWidth, newAudio) ?: return false)
                        changedAudio = true
                    }
                } else if (codec == "V_VP9") {
                    if (fields.any { it.id == DEFAULT_DURATION }) return false
                    val lacing = fields.filter { it.id == FLAG_LACING }
                    if (lacing.size != 1 || raw(header, lacing[0]).size != 3 ||
                        changedVideo) return false
                    modified.addAll(fields.filter { it != lacing[0] }.map { raw(header, it) })
                    modified.add(byteArrayOf(
                        0x23, 0xE3.toByte(), 0x83.toByte(), 0x84.toByte(),
                        (durationNs ushr 24).toByte(), (durationNs ushr 16).toByte(),
                        (durationNs ushr 8).toByte(), durationNs.toByte()
                    ))
                    modified.add(byteArrayOf(VOID.toByte(), 0x80.toByte()))
                    changedVideo = true
                } else {
                    modified.addAll(fields.map { raw(header, it) })
                }
                newEntries.add(element(TRACK_ENTRY, track.sizeWidth, join(modified))
                    ?: return false)
            }
            if (!changedAudio || !changedVideo) return false
            val newTracks = element(TRACKS, table.sizeWidth, join(newEntries))
                ?: return false
            if (newTracks.size.toLong() != table.end - table.start) return false
            // Write once and only after all checks. Cluster offsets, SeekHead,
            // Cues, compressed frame bytes and global file size are unchanged.
            access.seek(table.start.toLong())
            access.write(newTracks)
            return true
        }
    }
}
