package org.roruku.smartformatconverter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.roruku.smartformatconverter.data.db.MediaType
import org.roruku.smartformatconverter.data.preferences.FilenameFormat
import org.roruku.smartformatconverter.data.storage.MediaSelectionPolicy
import org.roruku.smartformatconverter.data.storage.OutputPathPolicy

class OutputPathPolicyTest {
    @Test
    fun singleFileGoesIntoConverterRoot() {
        assertEquals("Converter", OutputPathPolicy.outputDirectory(null))
        assertEquals("clip.mp4", OutputPathPolicy.outputFileName("clip.mov", "mp4"))
    }

    @Test
    fun batchPreservesOriginalFolderStructure() {
        assertEquals(
            "Converter/Hasumi/Day1",
            OutputPathPolicy.outputDirectory("Hasumi/Day1")
        )
        assertEquals(
            "IMG_001.jpg",
            OutputPathPolicy.outputFileName("IMG_001.png", "jpg")
        )
    }

    @Test
    fun defaultOriginalNamesDoNotReceiveLegacyPrefix() {
        assertEquals(
            "video.mp4",
            OutputPathPolicy.outputFileName(
                originalName = "video.mkv",
                extension = "mp4",
                format = FilenameFormat.ORIGINAL,
                customPrefix = "Converted_"
            )
        )
        assertEquals(
            "New_video.mp4",
            OutputPathPolicy.outputFileName(
                originalName = "video.mkv",
                extension = "mp4",
                format = FilenameFormat.CUSTOM_PREFIX,
                customPrefix = "New_"
            )
        )
    }

    @Test
    fun traversalComponentsAreExcluded() {
        assertEquals(
            "Converter/Hasumi/Day1",
            OutputPathPolicy.outputDirectory("../Hasumi/./Day1")
        )
    }

    @Test
    fun mediaFilterWorksEvenWithGenericProviderMimeType() {
        assertEquals(
            MediaType.VIDEO,
            MediaSelectionPolicy.detect("application/octet-stream", "video.MKV")
        )
        assertEquals(
            MediaType.IMAGE,
            MediaSelectionPolicy.detect("application/octet-stream", "picture.HEIC")
        )
        assertEquals(
            MediaType.AUDIO,
            MediaSelectionPolicy.detect("application/octet-stream", "song.OPUS")
        )
        assertNull(MediaSelectionPolicy.detect("application/pdf", "document.pdf"))
    }
}
