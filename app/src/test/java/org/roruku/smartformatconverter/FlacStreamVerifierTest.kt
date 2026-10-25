package org.roruku.smartformatconverter

import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.roruku.smartformatconverter.data.repository.FlacStreamVerifier
import java.io.File

class FlacStreamVerifierTest {
    private fun fixture(validFrame: Boolean = true): File {
        val file = File.createTempFile("verify_flac_", ".flac")
        file.deleteOnExit()
        val info = ByteArray(34)
        val descriptor = (44100L shl 44) or (1L shl 41) or (15L shl 36) or 1234L
        for (n in 0 until 8) {
            info[10 + n] = ((descriptor ushr ((7 - n) * 8)) and 0xff).toByte()
        }
        file.outputStream().use { out ->
            out.write("fLaC".toByteArray())
            out.write(byteArrayOf(0x80.toByte(), 0x00, 0x00, 34))
            out.write(info)
            if (validFrame) out.write(byteArrayOf(0xff.toByte(), 0xf8.toByte(), 0x00, 0x00))
            else out.write(byteArrayOf(0x11, 0x22, 0x33, 0x44))
        }
        return file
    }

    @Test fun acceptsFlacMetadataAndFrameSync() {
        val file = fixture()
        try { assertNull(FlacStreamVerifier.validate(file)) } finally { file.delete() }
    }

    @Test fun rejectsMissingAudioFrame() {
        val file = fixture(false)
        try { assertNotNull(FlacStreamVerifier.validate(file)) } finally { file.delete() }
    }

    @Test fun rejectsTruncatedStream() {
        val file = File.createTempFile("broken_flac_", ".flac")
        try {
            file.writeText("fLaC")
            assertNotNull(FlacStreamVerifier.validate(file))
        } finally { file.delete() }
    }
}
