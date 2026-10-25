package org.roruku.smartformatconverter

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import org.roruku.smartformatconverter.engine.media3.PlatformCompressedAudioExportEngine

class AdtsHeaderTest {
    @Test fun aacLc44100StereoCorrectHeader() {
        val header = PlatformCompressedAudioExportEngine.adtsHeader(100, 44100, 2)
        assertArrayEquals(byteArrayOf(
            0xff.toByte(), 0xf1.toByte(), 0x50.toByte(), 0x80.toByte(),
            0x0d.toByte(), 0x7f.toByte(), 0xfc.toByte()
        ), header)
    }
}
