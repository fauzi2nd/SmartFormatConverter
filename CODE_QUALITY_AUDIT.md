# Code quality and resource hygiene audit

## Scope

Branch: `refactor/warnings-resources-ci-hygiene`, based on `master` at
`8c41b8a`. This audit covers user-facing Android UI strings,
accessibility descriptions, Android Lint, GitHub Actions and JVM test
warnings, plus orphaned Converter V3 state. Conversion algorithms, output
paths, job schema, and FFmpeg command arguments are intentionally unchanged.

## Changes applied

- Centralized the Advanced Video settings choices, hints, rate information,
  and form labels in `res/values/strings.xml`.
- Replaced string-to-string description mapping in the Converter catalog with
  `@StringRes` resource IDs; codec/container identifiers are still stable,
  non-localized machine-readable tokens.
- Localized dynamic editor media counts, action labels, source metadata,
  Downloader status messages, Queue status and metrics, and foreground
  conversion notification text. Message formatting uses positional resource
  placeholders (including a properly escaped progress percent).
- Removed stale, unreferenced Converter V3 bitrate/resolution/CRF state and
  obsolete update methods after moving to V4.
- Added an explicit Android 13+ `POST_NOTIFICATIONS` permission check before
  posting progress updates, instead of suppressing the associated Android
  Lint finding. Foreground-service startup behavior is unchanged.
- Enabled `android.lint.warningsAsErrors` so future Android Lint warnings
  fail CI, in addition to existing error checks.
- Updated GitHub Actions `checkout` / `setup-java` to v5, which uses the
  newer runner JavaScript runtime. Removed redundant upload-based Java setup
  caching on the persistent self-hosted Windows runner (Gradle local caches
  remain active).
- Explicitly granted JVM test native access on JDK 25, as required when
  the JPEG JNI availability test invokes `System.loadLibrary`.
- Used `--warning-mode all` during diagnosis so Gradle warnings include
  plugin-origin stack traces rather than being silenced.

## Warning provenance: Android Gradle Plugin

The verified `--warning-mode all` stack trace shows repeated warnings
about the deprecated `Configuration.setVisible(boolean)` API originating at:

`com.android.build.gradle.internal.plugins.BasePlugin$Companion.createAndroidJdkImageConfiguration`
(`BasePlugin.kt:362`)

This is a call made **inside Android Gradle Plugin 9.4.1**, not by a
project-authored Gradle script. Android documents a fix in the AGP 9.5.0
prerelease series; at the audit date (2026-10-24), the available 9.5
artifacts were alpha builds, not a confirmed stable migration target.
Do **not** change to an alpha AGP or suppress Gradle warnings to manufacture
a clean log. Upgrade to a compatible stable AGP release when available,
then verify Kotlin/KSP, native CMake, Gradle wrapper and CI together.

References:
- https://developer.android.com/build/releases/agp-9-5-0-release-notes
- https://docs.gradle.org/9.8.2/userguide/upgrading_version_9.html#deprecate-visible-property

## Strict Android Lint cleanup and native dependency exception

Enabling `warningsAsErrors` exposed 25 existing findings on the initial
audit. The project-owned issues have been corrected individually:

- Removed the redundant activity label and the seven unused colors inherited
  from the Android Studio template.
- Moved adaptive icon resources out of the redundant `mipmap-anydpi-v26`
  qualifier (the app already has `minSdk=26`).
- Fixed the Compose Modifier argument order and replaced identified Java-style
  Kotlin calls with Android KTX equivalents.
- Converted file-count and status strings to proper plural resources;
  corrected typographical ellipses; annotated just the legitimate AV1 codec
  identifier to avoid the dictionary typo false positive.

**Important unresolved external binary risk:** RxFFmpeg 4.9.0 bundles
`x86_64/librxffmpeg-core.so` without 16 KB page alignment.
`app/lint.xml` narrowly excludes `Aligned16KB` reports *only when they
contain the exact pinned coordinate* `RxFFmpeg:4.9.0`. This is a scoped
known-issue waiver, not a compatibility fix, and every other library or
code issue is still checked with `warningsAsErrors`. A GitHub Actions
workflow warning calls out the risk on every run. Remove this exception
after updating or rebuilding RxFFmpeg's ELF binaries and verifying on a
16 KB Android device.

## What is deliberately NOT localized

Not every string literal is a UI hardcode. The following are stable
technical contracts and must not be translated:

- Codec IDs, container extensions, MIME types, FFmpeg arguments, file names,
  SAF URI schemes, HTTP headers, Gradle plugin identifiers, and SQL/JSON keys.
- Persisted job preset identifiers and serialized `VideoAdvancedSettings`
  values. Changing these would break existing jobs or unit tests.
- Internal diagnostic/log messages and previews' example media names.
  Validation diagnostics emitted from pure Kotlin engine code currently use
  English strings as part of their existing error contract. A future
  localization pass should introduce typed error IDs before translating
  them, not modify the engine's error text opportunistically.

## Verification

The CI workflow runs `assembleDebug`, `testDebugUnitTest` and
`lintDebug` (with warnings treated as errors). Android Studio Compose
`@Preview` renderings and actual device codec behavior require a real
device/UI inspection: successful compilation alone does not prove runtime
playback or conversion compatibility.

Known separate risk: bundled RxFFmpeg 4.9.0 native libraries are not yet
certified for Android's 16 KB page alignment. This is not a Kotlin warning
and is **not** resolved by this hygiene change.
