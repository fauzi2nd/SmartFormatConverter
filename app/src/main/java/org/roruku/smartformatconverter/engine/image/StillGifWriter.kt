package org.roruku.smartformatconverter.engine.image

import java.io.OutputStream

/**
 * Bounded-memory, single-frame GIF89a writer. GIF is limited to a 256-colour
 * palette; dimensions are preserved instead of silently downsizing images
 * larger than 2048 pixels. Rows are consumed one at a time.
 *
 * RGB332 is deterministic and requires neither a full-image palette search nor
 * another copy of the source bitmap in memory. This does not preserve animated
 * input and does not provide lossless true-colour image quality.
 */
internal object StillGifWriter {
    fun write(output: OutputStream, width: Int, height: Int, row: (Int, IntArray) -> Unit) {
        require(width in 1..65535 && height in 1..65535) {
            "GIF dimensions must be between 1 and 65535 pixels"
        }
        output.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShort(output, width)
        writeShort(output, height)
        output.write(0xf7) // global colour table, 8 bits per channel, 256 entries
        output.write(0) // background colour index
        output.write(0) // pixel aspect ratio

        for (index in 0..255) {
            val red = ((index shr 5) * 255 + 3) / 7
            val green = (((index shr 2) and 7) * 255 + 3) / 7
            val blue = ((index and 3) * 255 + 1) / 3
            output.write(red)
            output.write(green)
            output.write(blue)
        }

        output.write(0x2c) // image descriptor
        writeShort(output, 0)
        writeShort(output, 0)
        writeShort(output, width)
        writeShort(output, height)
        output.write(0) // non-interlaced; use global colour table only
        output.write(8) // LZW minimum code size

        val codes = GifLzwBlocks(output)
        codes.emit(256) // clear dictionary
        var countSinceClear = 0
        val pixels = IntArray(width)
        for (y in 0 until height) {
            row(y, pixels)
            for (argb in pixels) {
                // GIF has a maximum of 256 colours; convert RGB to RGB332.
                val paletteIndex = ((argb shr 16 and 0xff) shr 5 shl 5) or
                    ((argb shr 8 and 0xff) shr 5 shl 2) or ((argb and 0xff) shr 6)
                codes.emit(paletteIndex)
                countSinceClear++
                // The decoder dictionary stays below 512 entries, so all
                // emitted LZW codes are nine bits. Frequent clear codes trade
                // compression ratio for simple and predictable memory use.
                if (countSinceClear == 200) {
                    codes.emit(256)
                    countSinceClear = 0
                }
            }
        }
        codes.emit(257) // end of information
        codes.finish()
        output.write(0x3b) // GIF trailer
    }

    private fun writeShort(output: OutputStream, value: Int) {
        output.write(value and 0xff)
        output.write((value shr 8) and 0xff)
    }

    private class GifLzwBlocks(private val out: OutputStream) {
        private val block = ByteArray(255)
        private var blockSize = 0
        private var bits = 0
        private var bitCount = 0

        fun emit(code: Int) {
            bits = bits or (code shl bitCount)
            bitCount += 9
            while (bitCount >= 8) {
                writeByte(bits and 0xff)
                bits = bits ushr 8
                bitCount -= 8
            }
        }

        fun finish() {
            if (bitCount > 0) writeByte(bits and 0xff)
            flushBlock()
            out.write(0) // terminate GIF data sub-blocks
        }

        private fun writeByte(value: Int) {
            block[blockSize++] = value.toByte()
            if (blockSize == block.size) flushBlock()
        }

        private fun flushBlock() {
            if (blockSize == 0) return
            out.write(blockSize)
            out.write(block, 0, blockSize)
            blockSize = 0
        }
    }
}
