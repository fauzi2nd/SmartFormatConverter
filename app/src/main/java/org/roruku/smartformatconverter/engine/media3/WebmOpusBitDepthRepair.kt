package org.roruku.smartformatconverter.engine.media3

import java.io.File
import java.io.RandomAccessFile

/**
 * Repairs an AndroidX Media3 1.11.1 WebmMuxer interoperability defect.
 *
 * In WebmElements.createAudioTrackEntryElement(), BIT_DEPTH is written using
 * createFloatElement(..., format.pcmEncoding). But Matroska BitDepth (0x6264)
 * is an unsigned INTEGER, and encoded Opus should not carry PCM BitDepth at
 * all. A real device wrote 62 64 84 BF 80 00 00 (float -1.0); FFmpeg failed
 * to initialize its Opus decoder despite valid OpusHead and Channels.
 *
 * Replace this *seven-byte audio-track metadata element only* with a seven-
 * byte global EBML Void. This is byte-for-byte size-neutral and does not
 * transcode, shift clusters or invalidate any seek positions/cues.
 *
 * Runs after the Media3 muxer is closed and before the export is reported
 * complete. Any failure is fatal rather than publishing an incompatible file.
 */
internal object WebmOpusBitDepthRepair {
    private const val MAX_HEADER_BYTES = 1024 * 1024
    private const val SEGMENT = 0x18538067L
    private const val TRACKS = 0x1654AE6BL
    private const val TRACK_ENTRY = 0xAEL
    private const val CODEC_ID = 0x86L
    private const val AUDIO = 0xE1L
    private const val BIT_DEPTH = 0x6264L
    private val VOID_7 = byteArrayOf(0xEC.toByte(), 0x85.toByte(), 0, 0, 0, 0, 0)

    private data class Element(val id: Long, val start: Long, val payload: Long, val end: Long)

    private fun vintWidth(first: Int, max: Int): Int {
        for (width in 1..max) {
            if (first and (0x80 ushr (width - 1)) != 0) return width
        }
        throw IllegalArgumentException("Invalid EBML variable-width integer")
    }

    private fun parse(bytes: ByteArray, offset: Long, parentEnd: Long): Element {
        require(offset >= 0L && offset < bytes.size) {
            "WebM metadata extends beyond the supported header scan"
        }
        val start = offset.toInt()
        val idWidth = vintWidth(bytes[start].toInt() and 0xff, 4)
        require(start + idWidth < bytes.size) { "Truncated EBML element ID" }
        var id = 0L
        for (i in 0 until idWidth) id = (id shl 8) or (bytes[start + i].toLong() and 0xff)
        val sizeStart = start + idWidth
        val sizeWidth = vintWidth(bytes[sizeStart].toInt() and 0xff, 8)
        require(sizeStart.toLong() + sizeWidth <= bytes.size) { "Truncated EBML element size" }
        var size = (bytes[sizeStart].toLong() and (0xff ushr sizeWidth).toLong())
        for (i in 1 until sizeWidth) {
            size = (size shl 8) or (bytes[sizeStart + i].toLong() and 0xff)
        }
        val unknownSize = size == ((1L shl (7 * sizeWidth)) - 1L)
        val payload = sizeStart.toLong() + sizeWidth
        val end = if (unknownSize) parentEnd else payload + size
        require(end >= payload && end <= parentEnd) {
            "Invalid WebM element boundary at offset $offset"
        }
        return Element(id, offset, payload, end)
    }

    private fun children(bytes: ByteArray, parent: Element, visit: (Element) -> Unit) {
        var position = parent.payload
        while (position < parent.end) {
            val element = parse(bytes, position, parent.end)
            visit(element)
            require(element.end > position) { "Invalid zero-length EBML header" }
            position = element.end
        }
    }

    private fun findOpusBitDepthOffsets(bytes: ByteArray, fileSize: Long): List<Long> {
        val root = Element(0, 0, 0, fileSize)
        var segment: Element? = null
        // Only the EBML header and Segment are expected at the root.
        var position = 0L
        while (position < fileSize && position < bytes.size) {
            val element = parse(bytes, position, fileSize)
            if (element.id == SEGMENT) {
                segment = element
                break
            }
            position = element.end
        }
        val container = requireNotNull(segment) { "Missing WebM Segment" }
        var tracks: Element? = null
        position = container.payload
        while (position < container.end && position < bytes.size) {
            val element = parse(bytes, position, container.end)
            if (element.id == TRACKS) {
                tracks = element
                break
            }
            // Tracks should precede the sample clusters. Never scan encoded
            // video data or treat arbitrary payload bytes as EBML metadata.
            if (element.id == 0x1F43B675L) break
            position = element.end
        }
        val trackTable = requireNotNull(tracks) { "Missing WebM Tracks before samples" }
        require(trackTable.end <= bytes.size) { "WebM Tracks exceed header scan limit" }

        val offsets = mutableListOf<Long>()
        children(bytes, trackTable) { track ->
            if (track.id != TRACK_ENTRY) return@children
            var codecId: String? = null
            val bitDepths = mutableListOf<Element>()
            children(bytes, track) { child ->
                when (child.id) {
                    CODEC_ID -> {
                        require(child.end <= bytes.size) { "Truncated WebM CodecID" }
                        codecId = String(bytes, child.payload.toInt(),
                            (child.end - child.payload).toInt(), Charsets.US_ASCII)
                    }
                    AUDIO -> {
                        children(bytes, child) { audioElement ->
                            if (audioElement.id == BIT_DEPTH) bitDepths.add(audioElement)
                        }
                    }
                }
            }
            if (codecId == "A_OPUS") {
                for (bitDepth in bitDepths) {
                    // Media3 always emits a four-byte IEEE-754 payload with
                    // a 2-byte ID and 1-byte length. Never rewrite an element
                    // whose shape we haven't verified against real files.
                    require(bitDepth.end - bitDepth.start == VOID_7.size.toLong() &&
                        bitDepth.payload - bitDepth.start == 3L &&
                        bitDepth.end <= bytes.size) {
                        "Unexpected WebM Opus BitDepth encoding; refuse unsafe repair"
                    }
                    offsets.add(bitDepth.start)
                }
            }
        }
        return offsets
    }

    /** Returns how many malformed Opus BitDepth elements were neutralized. */
    fun repair(file: File): Int {
        require(file.isFile && file.length() > 0) { "WebM output is missing" }
        RandomAccessFile(file, "rw").use { output ->
            val length = output.length()
            val header = ByteArray(minOf(length, MAX_HEADER_BYTES.toLong()).toInt())
            output.readFully(header)
            val offsets = findOpusBitDepthOffsets(header, length)
            // No writes until the entire EBML layout has passed validation.
            for (offset in offsets) {
                output.seek(offset)
                output.write(VOID_7)
            }
            return offsets.size
        }
    }
}
