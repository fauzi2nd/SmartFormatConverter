# Google Play Store Metadata & Declarations

This document provides ready-to-use declaration answers, foreground service rationales, and store listing copy for publishing **Smart Format Converter** on Google Play Console.

---

## 1. Google Play Data Safety Section

| Question | Answer | Details / Explanation |
| :--- | :--- | :--- |
| **Does your app collect or share any user data?** | **No** | The app is an offline-capable utility. All conversion happens 100% on-device. |
| **Is all data in transit encrypted?** | **No (with exception)** | All HTTPS connections are encrypted. However, the app allows downloading user-entered plain HTTP URLs in the Downloader tab (`usesCleartextTraffic="true"`), during which transit for that specific requested media file is unencrypted. |
| **Do you provide a way for users to request data deletion?** | **Yes** | Users can delete conversion history and temporary cache files at any time via the in-app Settings and Queue screens. |

---

## 2. Foreground Service Policy Declarations

Google Play requires explicit declarations for each foreground service type declared in `AndroidManifest.xml`.

### A. Foreground Service Type: `mediaProcessing`
* **Permission**: `android.permission.FOREGROUND_SERVICE_MEDIA_PROCESSING`
* **Justification for Play Console**:
  > "Smart Format Converter transcodes user-selected video, audio, and image files directly on the device using AndroidX Media3 and native graphics codecs. Transcoding media files (especially high-definition video or large multi-file batches) requires continuous hardware-accelerated processing that frequently exceeds background execution limits. The foreground service ensures that encoding tasks are not abruptly killed by Android's low-memory killer when the user switches apps or locks their screen, preventing partially-written corrupted files. A user-visible persistent notification displaying live percentage progress and job status is actively maintained during the entire operation."
* **Video Demonstration URL Requirement**: Upload a 30-second screen recording showing a user queuing a video conversion, minimizing the app, showing the persistent notification progress in the shade, and tapping the notification to return to the completed file.

### B. Foreground Service Type: `dataSync`
* **Permission**: `android.permission.FOREGROUND_SERVICE_DATA_SYNC`
* **Justification for Play Console**:
  > "Smart Format Converter enables users to input direct HTTP/HTTPS media URLs to download files directly into their device's user-selected Storage Access Framework (SAF) folder. The `dataSync` foreground service maintains the active network streaming connection for large video/audio files, displays real-time download speed and byte progress in the notification shade, and ensures downloads complete without interruption when the app is in the background."
* **Video Demonstration URL Requirement**: Record a short clip demonstrating a user entering an HTTP download link, navigating to the home screen, showing the active download notification progressing, and opening the downloaded file.

---

## 3. Store Listing Copy

### App Title
**Smart Format Converter**

### Short Description (Max 80 characters)
Private video, audio and image conversion with direct file downloads.

### Full Description
**Smart Format Converter** is a modern, privacy-first multimedia conversion and downloader tool for Android. Convert your videos, music, and photos with hardware acceleration—100% on your device, without uploading your personal files to any cloud server!

**Key Features:**
* 🎬 **Video Converter**: Export MP4 (H.264/H.265 where supported by the device) from compatible input files using AndroidX Media3.
* 🎵 **Audio Converter & Extractor**: Convert decodable input audio or extract audio from compatible video files into M4A (AAC).
* 🖼️ **Image Resizer & Converter**: Convert between JPEG, PNG, and WebP. Adjust quality, resolution, center-crop, and maintain aspect ratios with live preview.
* ⚡ **Android MediaCodec**: Uses device-supported encoders, with performance, power use and fallback behavior varying by chipset.
* 🔒 **On-Device Conversion**: Local files are converted without cloud processing. Direct file downloads use the network only for the URLs you request.
* 📁 **Storage Access Framework**: Fully compatible with modern Android scoped storage. Save converted media directly to your chosen folder.
* 🌐 **Direct Media Downloader**: Download direct HTTP/HTTPS media links, with byte-range resume when supported by the source server.

---

## 4. App Classification & Content Rating

* **App Category**: Tools / Video Players & Editors
* **Target Audience**: Choose and verify the intended age groups in Play Console before submission; this draft does not establish Families eligibility.
* **Contains Ads**: No
* **App Access**: All features are available without special access or credentials.
* **Privacy Policy URL**: Publish and verify a publicly accessible HTTPS privacy-policy webpage before filling this field. A guessed GitHub Pages URL is not evidence that a live policy page exists.

---

## Important Pre-publication Checks

- This text is a **draft**, not a verified certification of Play policy compliance. Review Data Safety against the final manifest, dependencies, actual network activity and retained data.
- Avoid claims of universally supported input codecs, WebM/MP3 output, 5× speedups or guaranteed battery improvements: these have not been established by device benchmarks.
- The downloader sends requests to user-chosen hosts; those hosts can observe ordinary network information such as the source IP address. HTTP transport is unencrypted.
- Include links/screenshots/recordings required by Google Play only after those actual assets exist.
