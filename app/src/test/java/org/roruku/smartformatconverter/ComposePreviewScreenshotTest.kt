package org.roruku.smartformatconverter

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import org.roruku.smartformatconverter.ui.theme.SmartFormatConverterTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
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
@OptIn(com.github.takahirom.roborazzi.ExperimentalRoborazziApi::class)
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
        composeRule.setContent {
            SmartFormatConverterTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background) { content() }
            }
        }
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

    /**
     * Capture explicit sections of the production LazyColumn, not a sequence
     * of swipes that can all land at the same bottom position.
     */
    private fun screenshotEditorSections(prefix: String, content: @Composable () -> Unit) {
        composeRule.setContent {
            SmartFormatConverterTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background) { content() }
            }
        }
        composeRule.waitForIdle()
        fun capture(section: String) {
            val output = File("preview/$prefix-$section.png")
            output.parentFile?.mkdirs()
            composeRule.onRoot().captureRoboImage(
                filePath = output.absolutePath,
                roborazziOptions = recordOptions
            )
        }
        capture("top")
        listOf(
            "preset" to "editor_preset",
            "settings" to "editor_video_settings",
            "summary" to "editor_output_summary"
        ).forEach { (section, tag) ->
            composeRule.onNodeWithTag("converter_editor_scroll")
                .performScrollToNode(hasTestTag(tag))
            composeRule.waitForIdle()
            capture(section)
        }
    }

    @Test
    fun converterEditorVideoSections() = screenshotEditorSections("converter-editor-video") {
        PreviewEditorSample(tab = 0)
    }

    @Test
    fun converterEditorWebmBatchSections() = screenshotEditorSections("converter-editor-webm-batch") {
        ConverterWebmTwoFilesPreview()
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
