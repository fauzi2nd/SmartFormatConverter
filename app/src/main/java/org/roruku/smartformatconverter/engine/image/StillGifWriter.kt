package org.roruku.smartformatconverter.engine.image

import java.io.OutputStream

/**
 * Bounded-memory, single-frame GIF89a writer. GIF is limited to a 256-colour
 * palette; dimensions are preserved instead of silently downsizing images
 * larger than 2048 pixels. Rows are consumed one at a time.
 *
 * Indexed RGB palette is deterministic and requires neither a full-image palette search nor
 * another copy of the source bitmap in memory. This does not preserve animated
 * input and does not provide lossless true-colour image quality.
 */
internal object StillGifWriter {
    fun write(output: OutputStream, width: Int, height: Int, paletteBits: Int = 8, row: (Int, IntArray) -> Unit) {
        require(paletteBits in setOf(4, 6, 8))
        require(width in 1..65535 && height in 1..65535) {
            "GIF dimensions must be between 1 and 65535 pixels"
        }
        output.write("GIF89a".toByteArray(Charsets.US_ASCII))
        writeShort(output, width)
        writeShort(output, height)
        output.write(0xf0 or (paletteBits - 1)) // global table with 2^paletteBits colors
        output.write(0) // background colour index
        output.write(0) // pixel aspect ratio

        val redBits = if (paletteBits == 8) 3 else 2
        val greenBits = if (paletteBits == 8) 3 else if (paletteBits == 6) 2 else 1
        val blueBits = paletteBits - redBits - greenBits
        val redMax = (1 shl redBits) - 1
        val greenMax = (1 shl greenBits) - 1
        val blueMax = (1 shl blueBits) - 1
        for (index in 0 until (1 shl paletteBits)) {
            val red = ((index shr (greenBits + blueBits)) * 255) / redMax
            val green = (((index shr blueBits) and greenMax) * 255) / greenMax
            val blue = ((index and blueMax) * 255) / blueMax
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
        output.write(paletteBits) // LZW minimum code size

        val clearCode = 1 shl paletteBits
        val codes = GifLzwBlocks(output, paletteBits + 1)
        codes.emit(clearCode) // clear dictionary
        var countSinceClear = 0
        val pixels = IntArray(width)
        for (y in 0 until height) {
            row(y, pixels)
            for (argb in pixels) {
                // GIF has a maximum of 256 colours; convert RGB to RGB332.
                // Composite transparent pixels against white in this row.
                // Do not create a second full-resolution bitmap for large GIFs.
                val alpha = (argb ushr 24) and 0xff
                val r = (((argb ushr 16) and 0xff) * alpha + 255 * (255 - alpha) + 127) / 255
                val g = (((argb ushr 8) and 0xff) * alpha + 255 * (255 - alpha) + 127) / 255
                val b = ((argb and 0xff) * alpha + 255 * (255 - alpha) + 127) / 255
                val paletteIndex = ((r shr (8 - redBits)) shl (greenBits + blueBits)) or
                    ((g shr (8 - greenBits)) shl blueBits) or (b shr (8 - blueBits))
                codes.emit(paletteIndex)
                countSinceClear++
                // The decoder dictionary stays below 512 entries, so all
                // emitted LZW codes are nine bits. Frequent clear codes trade
                // compression ratio for simple and predictable memory use.
                if (countSinceClear == (1 shl paletteBits) - 3) {
                    codes.emit(clearCode)
                    countSinceClear = 0
                }
            }
        }
        codes.emit(clearCode + 1) // end of information
        codes.finish()
        output.write(0x3b) // GIF trailer
    }

    private fun writeShort(output: OutputStream, value: Int) {
        output.write(value and 0xff)
        output.write((value shr 8) and 0xff)
    }

    private class GifLzwBlocks(private val out: OutputStream, private val codeWidth: Int) {
        private val block = ByteArray(255)
        private var blockSize = 0
        private var bits = 0
        private var bitCount = 0

        fun emit(code: Int) {
            bits = bits or (code shl bitCount)
            bitCount += codeWidth
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
