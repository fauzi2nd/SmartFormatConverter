# Roborazzi — UI preview from terminal

Roborazzi runs screenshot tests on the JVM with Robolectric, **without an Android emulator or APK installation**. It scans the existing Jetpack Compose `@Preview` functions and exports PNG previews. The preview media uses sample placeholders; it does not decode actual videos.

## Requirements

- JDK 17+ and configured Android SDK (`local.properties` or `ANDROID_HOME`).
- Gradle wrapper shipped with this repository.
- Internet connection the first time, to resolve Robolectric and Roborazzi test dependencies.

## Windows PowerShell (project root)

```powershell
.\gradlew.bat :app:recordRoborazziDebug
```

Linux / macOS / terminal:

```bash
./gradlew :app:recordRoborazziDebug
```

Screenshots: `app/build/outputs/roborazzi/`

HTML report: `app/build/reports/roborazzi/index.html`

The generated previews include `ConverterWebmTwoFilesPreview`, the requested two-file WEBM flow.

To compare an updated UI against screenshots already recorded:

```powershell
.\gradlew.bat :app:compareRoborazziDebug
```

To verify that the rendered UI matches existing screenshots:

```powershell
.\gradlew.bat :app:verifyRoborazziDebug
```

To run normal app build only:

```powershell
.\gradlew.bat :app:assembleDebug
```

Roborazzi's screenshot tests are **opt-in**: APK builds do not need to run the `recordRoborazziDebug` task.

## Notes

- Rendered Previews are design-state screenshots, not actual ExoPlayer playback or device encoder capability checks.
- Roborazzi's generated Compose Preview testing is experimental. If an upstream AGP/Kotlin incompatibility occurs, check the Gradle failure logs before changing dependency versions.
- The first record command creates or updates reference images. The verify command uses the recorded images as baselines.
