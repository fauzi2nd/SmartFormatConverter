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
Fast, private, and offline video, audio, and image converter with zero ads.

### Full Description
**Smart Format Converter** is a modern, privacy-first multimedia conversion and downloader tool for Android. Convert your videos, music, and photos with hardware acceleration—100% on your device, without uploading your personal files to any cloud server!

**Key Features:**
* 🎬 **Video Converter**: Convert video files (.mp4, .mkv, .webm, .mov, .avi) to modern, universally compatible MP4 and WebM formats using hardware-accelerated Media3 encoding.
* 🎵 **Audio Converter & Extractor**: Convert audio (.m4a, .aac, .flac, .wav, .opus, .mp3) or extract high-quality audio tracks directly from your favorite video clips.
* 🖼️ **Image Resizer & Converter**: Convert between JPEG, PNG, and WebP. Adjust quality, resolution, center-crop, and maintain aspect ratios with live preview.
* ⚡ **Hardware Acceleration**: Powered by Android's native GPU/VPU codecs for up to 5x faster conversions and minimal battery consumption.
* 🔒 **100% Private & Offline**: No cloud uploads, no account required, no tracking, and zero ads. Your files never leave your phone.
* 📁 **Storage Access Framework**: Fully compatible with modern Android scoped storage. Save converted media directly to your chosen folder.
* 🌐 **Direct Media Downloader**: Download direct media links straight to your device with resume capability.

---

## 4. App Classification & Content Rating

* **App Category**: Tools / Video Players & Editors
* **Target Audience**: Everyone
* **Contains Ads**: No
* **App Access**: All features are available without special access or credentials.
* **Privacy Policy URL**: Host `PRIVACY_POLICY.md` on GitHub Pages (e.g., `https://fauzi2nd.github.io/SmartFormatConverter/PRIVACY_POLICY.html` or direct GitHub raw URL: `https://raw.githubusercontent.com/fauzi2nd/SmartFormatConverter/master/PRIVACY_POLICY.md`).
