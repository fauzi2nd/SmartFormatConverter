package org.roruku.smartformatconverter

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.roruku.smartformatconverter.ui.converter.ConverterAudioPickerPreview
import org.roruku.smartformatconverter.ui.converter.ConverterCatalogPreview
import org.roruku.smartformatconverter.ui.converter.ConverterMediaPickerPreview
import org.roruku.smartformatconverter.ui.converter.ConverterWebmTwoFilesPreview
import org.roruku.smartformatconverter.ui.converter.PreviewEditorSample
import org.roruku.smartformatconverter.ui.converter.VideoAdvancedExpandedPreview
import org.roruku.smartformatconverter.ui.previews.DownloaderEmptyStatePreview
import org.roruku.smartformatconverter.ui.previews.DownloaderLoadingStatePreview
import org.roruku.smartformatconverter.ui.previews.HomeEmptyStatePreview
import org.roruku.smartformatconverter.ui.previews.QueueActiveStatePreview
import org.roruku.smartformatconverter.ui.previews.QueueEmptyStatePreview
import org.roruku.smartformatconverter.ui.previews.SettingsCustomFilenamePreview
import org.roruku.smartformatconverter.ui.previews.SettingsDefaultStatePreview
import java.io.File

/**
 * Render Compose design states on the JVM. No emulator or Roborazzi Gradle plugin required.
 * Output is written to app/preview/ with clear module and state prefixes.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h840dp-xxhdpi")
class ComposePreviewScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val recordOptions = RoborazziOptions(
        taskType = RoborazziTaskType.Record
    )

    private fun screenshot(name: String, content: @Composable () -> Unit) {
        composeRule.setContent(content)
        composeRule.waitForIdle()
        val output = File("preview/$name.png")
        output.parentFile?.mkdirs()
        composeRule.onRoot().captureRoboImage(
            filePath = output.absolutePath,
            roborazziOptions = recordOptions
        )
    }

    // --- 1. Home Screen ---
    @Test
    fun homeScreen() = screenshot("home-screen") {
        HomeEmptyStatePreview()
    }

    // --- 2. Converter Screen (Catalog & Pickers) ---
    @Test
    fun converterCatalog() = screenshot("converter-catalog") {
        ConverterCatalogPreview()
    }

    @Test
    fun converterPickerVideo() = screenshot("converter-picker-video") {
        ConverterMediaPickerPreview()
    }

    @Test
    fun converterPickerAudio() = screenshot("converter-picker-audio") {
        ConverterAudioPickerPreview()
    }

    // --- 3. Converter Editor & Batch Previews ---
    @Test
    fun converterEditorVideo() = screenshot("converter-editor-video") {
        PreviewEditorSample(tab = 0)
    }

    @Test
    fun converterEditorImage() = screenshot("converter-editor-image") {
        PreviewEditorSample(tab = 1)
    }

    @Test
    fun converterEditorAudio() = screenshot("converter-editor-audio") {
        PreviewEditorSample(tab = 2)
    }

    @Test
    fun converterEditorWebmBatch() = screenshot("converter-editor-webm-batch") {
        ConverterWebmTwoFilesPreview()
    }

    @Test
    fun converterSheetVideoAdvanced() = screenshot("sheet-video-advanced") {
        VideoAdvancedExpandedPreview()
    }

    // --- 4. Downloader Screen ---
    @Test
    fun downloaderEmpty() = screenshot("downloader-empty") {
        DownloaderEmptyStatePreview()
    }

    @Test
    fun downloaderLoading() = screenshot("downloader-loading") {
        DownloaderLoadingStatePreview()
    }

    // --- 5. Queue Screen ---
    @Test
    fun queueEmpty() = screenshot("queue-empty") {
        QueueEmptyStatePreview()
    }

    @Test
    fun queueActive() = screenshot("queue-active") {
        QueueActiveStatePreview()
    }

    // --- 6. Settings Screen ---
    @Test
    fun settingsDefault() = screenshot("settings-default") {
        SettingsDefaultStatePreview()
    }

    @Test
    fun settingsCustomNaming() = screenshot("settings-custom-naming") {
        SettingsCustomFilenamePreview()
    }
}
