package org.roruku.smartformatconverter

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.roruku.smartformatconverter.ui.converter.PreviewEditorSample
import org.roruku.smartformatconverter.ui.converter.ConverterWebmTwoFilesPreview
import java.io.File

/**
 * Render Compose design states on the JVM. No emulator or Roborazzi Gradle plugin required.
 * Output is under app/preview; files are intentionally ignored by regular APK builds.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w393dp-h840dp-xxhdpi")
class ComposePreviewScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    private fun screenshot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        composeRule.setContent(content)
        composeRule.waitForIdle()
        val output = File("preview/$name.png")
        output.parentFile?.mkdirs()
        composeRule.onRoot().captureRoboImage(filePath = output.absolutePath)
    }

    @Test fun videoPreview() = screenshot("video-preview") { PreviewEditorSample(tab = 0) }
    @Test fun webmTwoFiles() = screenshot("webm-two-files") { ConverterWebmTwoFilesPreview() }
    @Test fun imagePreview() = screenshot("image-preview") { PreviewEditorSample(tab = 1) }
    @Test fun audioPreview() = screenshot("audio-preview") { PreviewEditorSample(tab = 2) }
}
