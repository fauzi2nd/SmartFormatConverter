package org.roruku.smartformatconverter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.roruku.smartformatconverter.engine.image.StillGifWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class StillGifWriterTest {
    @Test fun gifPreservesDimensionsBeyondOld2048Limit() {
        val output = ByteArrayOutputStream()
        StillGifWriter.write(output, 3001, 3) { y, row ->
            row.fill(if (y == 1) 0xff00ff00.toInt() else 0xffff0000.toInt())
        }
        val bytes = output.toByteArray()
        assertTrue(bytes.copyOfRange(0, 6).contentEquals("GIF89a".toByteArray()))
        val image = ImageIO.read(ByteArrayInputStream(bytes))
        assertEquals(3001, image.width)
        assertEquals(3, image.height)
        assertEquals(0xff00ff00.toInt(), image.getRGB(100, 1))
        assertEquals(0xffff0000.toInt(), image.getRGB(3000, 2))
    }

    @Test fun smallGifPaletteAndLzwRoundTrip() {
        val output = ByteArrayOutputStream()
        StillGifWriter.write(output, 3, 2) { y, row ->
            if (y == 0) {
                row[0] = 0xff000000.toInt()
                row[1] = 0xffffffff.toInt()
                row[2] = 0xffff0000.toInt()
            } else {
                row[0] = 0xff00ff00.toInt()
                row[1] = 0xff0000ff.toInt()
                row[2] = 0xff000000.toInt()
            }
        }
        val img = ImageIO.read(ByteArrayInputStream(output.toByteArray()))
        assertEquals(3, img.width)
        assertEquals(2, img.height)
        assertEquals(0xff0000ff.toInt(), img.getRGB(1, 1))
        assertEquals(0xffffffff.toInt(), img.getRGB(1, 0))
    }

    @Test fun transparentPixelsBecomeWhiteWithoutSecondBitmap() {
        val output = ByteArrayOutputStream()
        StillGifWriter.write(output, 1, 1) { _, row ->
            row[0] = 0x00000000
        }
        val image = ImageIO.read(ByteArrayInputStream(output.toByteArray()))
        assertEquals(0xffffffff.toInt(), image.getRGB(0, 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun refusesDimensionsBeyondGifSpec() {
        StillGifWriter.write(ByteArrayOutputStream(), 65536, 2) { _, _ -> }
    }
}
