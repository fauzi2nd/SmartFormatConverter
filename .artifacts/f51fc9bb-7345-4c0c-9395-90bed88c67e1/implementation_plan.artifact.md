# SmartFormatConverter V4 Implementation Plan

Implement the Format Factory-style UI catalog, bottom action sheet, preview workspace with horizontal slideshow, per-file and global output settings, and robust media conversion workflow.

## User Review Required

> [!IMPORTANT]
> **Format Factory Catalog Design**: The top tab row ("All/Video/Image/Audio") will be completely removed in favor of a unified scrollable category list (Video Converter, Image Converter, Audio Converter) showing all supported formats in a responsive grid.

> [!IMPORTANT]
> **Bottom Action Sheet**: Tapping any format card selects it and displays a sticky bottom action sheet right above the bottom navigation bar with **Files** and **Folder** pickers.

> [!IMPORTANT]
> **Preview & Convert Workspace**: Selecting media transitions immediately to a dedicated preview and edit workspace featuring media playback/preview, horizontal pager slideshow with thumbnails, and collapsible output configuration sections (Video, Audio, Subtitle & Metadata).

## Proposed Changes

### [UI & Converter Module]

#### [MODIFY] [ConverterScreen.kt](file:///C:/Users/space/AndroidStudioProjects/SmartFormatConverter/app/src/main/java/org/roruku/smartformatconverter/ui/converter/ConverterScreen.kt)
- Replace tab-based view with the Format Factory catalog (Video, Image, Audio categories with format grid).
- Implement persistent bottom action sheet for format actions (Files/Folder).
- Implement Preview & Convert editor view with Media Preview player, HorizontalPager slideshow, and expandable output settings.
- Support "Apply to all files" vs "This file only" configuration scope.

#### [MODIFY] [ConverterViewModel.kt](file:///C:/Users/space/AndroidStudioProjects/SmartFormatConverter/app/src/main/java/org/roruku/smartformatconverter/ui/converter/ConverterViewModel.kt)
- Manage selected output format state, bottom sheet visibility, selected media files, current preview index, and per-file configuration overrides (`Map<Uri, FileConfiguration>`).
- Handle recursive folder scanning with category filtering (ignoring `Converter` / `Converted` folders).
- Manage job submission with final snapshotted configurations.

## Verification Plan

### Automated Tests
- Run `./gradlew.bat :app:assembleDebug` to verify compilation.
- Run `./gradlew.bat :app:testDebugUnitTest` for unit tests.
- Run `./gradlew.bat :app:lintDebug` for lint checks.

### Manual Verification
- Deploy to device/emulator.
- Verify Format Factory catalog display.
- Verify bottom action sheet opens above navigation bar upon selecting a format.
- Verify media picker and transition to Preview & Convert workspace.
- Test horizontal pager slideshow, video playback, and per-file settings.
