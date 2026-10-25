package org.roruku.smartformatconverter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.roruku.smartformatconverter.data.repository.SourceFormatLabels

class SourceFormatLabelsTest {
    @Test fun mp3MimeAlwaysDisplaysMp3() {
        assertEquals("mp3", SourceFormatLabels.resolve("1.01. 一輪花.mp3", "audio/mpeg"))
        assertEquals("mp3", SourceFormatLabels.resolve("Song.mp3", "mpeg"))
        assertEquals("mp3", SourceFormatLabels.resolve("Untitled", "audio/mpeg"))
    }
    @Test fun otherFormatsAndLegacyValuesArePreserved() {
        assertEquals("flac", SourceFormatLabels.resolve("Song.flac", "audio/flac"))
        assertEquals("m4a", SourceFormatLabels.resolve("Audio.m4a", "audio/mp4"))
        assertEquals("mp4", SourceFormatLabels.resolve("Video.mp4", "video/mp4"))
        assertEquals("wav", SourceFormatLabels.resolve("Audio.wav", "audio/x-wav"))
        assertEquals("mkv", SourceFormatLabels.resolve("Video.mkv", "video/x-matroska"))
    }
}
