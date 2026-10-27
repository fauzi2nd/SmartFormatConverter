# Roborazzi screenshots from terminal (without Gradle plugin)

AGP 9.x does not expose the legacy TestedExtension expected by older Roborazzi Gradle plugins. This project uses **the Roborazzi library directly**, a Robolectric JVM test and Compose TestRule. No emulator is needed.

## Windows / PowerShell

From the project root:

```powershell
git pull origin master
.\gradlew.bat :app:testDebugUnitTest --tests "org.roruku.smartformatconverter.ComposePreviewScreenshotTest" --rerun-tasks
```

Screenshots are written to **app/preview/** as PNG files:
- video-preview.png
- webm-two-files.png
- image-preview.png
- audio-preview.png

These tests take Compose design-state snapshots. They do **not** play real media, test encoders, or validate the entire scrollable screen below the viewport. The tests currently capture the first 393dp × 840dp viewport.

## Requirements

- Android SDK and a compatible JDK configured for Gradle.
- Network access to download test dependencies on first run.
- The `testDebugUnitTest` task must compile and pass. Check the test report in `app/build/reports/tests/testDebugUnitTest/` if it fails.

No `org.gradle.java.home` was added: that absolute Windows installation path differs between systems. Set `JAVA_HOME` in your terminal if Gradle is using the wrong JDK.

The old `recordRoborazziDebug`, `compareRoborazziDebug`, and `verifyRoborazziDebug` tasks are **not available** in library-only mode. Use the JVM screenshot test command above.
