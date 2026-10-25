# Privacy Policy for Smart Format Converter

*Last updated: October 25, 2026*

Smart Format Converter ("the Application") is an open-source, privacy-first multimedia utility application designed to convert, transcode, and resize video, audio, and image files on Android devices.

We believe that your personal files, media, and data belong exclusively to you. This Privacy Policy outlines how the Application handles your data and permissions.

---

### 1. Zero Data Collection & On-Device Processing

* **No Personal Data Collection**: The Application does not collect, record, log, or harvest any personal information, device identifiers, email addresses, phone numbers, or usage analytics.
* **100% On-Device Execution**: All media transcoding, image conversion, metadata parsing, and compression tasks are executed entirely locally on your device hardware (using native Android Media3 and Android Graphics APIs). Your files are never uploaded to any remote server or cloud service for processing.
* **No Third-Party SDKs or Trackers**: The Application contains zero tracking libraries, zero analytics SDKs (e.g., no Google Analytics, Firebase Analytics, or Facebook SDK), and zero advertising networks.

---

### 2. Permissions and Their Use

The Application requests only the minimal permissions technically required to perform user-initiated operations:

| Permission | Purpose & Scope |
| :--- | :--- |
| **`INTERNET`** | Used **exclusively** when you explicitly input a direct URL in the Downloader tab to fetch a media file. No background telemetry, ads, or tracking data are ever transmitted. |
| **`FOREGROUND_SERVICE`**<br>`(mediaProcessing & dataSync)` | Ensures that active media conversions (`mediaProcessing`) and large direct file downloads (`dataSync`) are not prematurely terminated by the Android operating system when you switch to another app or turn off your screen. A persistent progress notification is always shown while active. |
| **`POST_NOTIFICATIONS`** *(Android 13+)* | Displays progress, completion, and error alerts for active conversion and download tasks in the system notification shade. You can toggle notification behavior anytime in Settings. |
| **Storage Access Framework (SAF)** | The Application does **not** request broad storage permissions (such as `MANAGE_EXTERNAL_STORAGE` or `READ_MEDIA_*`). File and folder access is strictly isolated to documents and directory trees that you explicitly choose via Android's native system picker. |

---

### 3. Data Storage and Retention

* **Job Queue & History**: Details of queued and completed conversion tasks (such as filename, output format, file size, and status) are stored locally on your device in a private SQLite/Room database. This database is never synced to external cloud services.
* **Temporary Cache**: Intermediate files created during conversion are temporarily stored in your application's private cache directory and deleted immediately upon completion or cancellation. You can also manually clear this cache at any time via Settings.

---

### 4. Children's Privacy

The Application does not collect information from anyone, including children under the age of 13. It is fully safe for users of all ages.

---

### 5. Open Source Transparency

Smart Format Converter is committed to complete transparency. The source code of the Application is publicly available on GitHub at [https://github.com/fauzi2nd/SmartFormatConverter](https://github.com/fauzi2nd/SmartFormatConverter) for independent inspection and auditing.

---

### 6. Contact and Inquiries

If you have any questions or feedback regarding this Privacy Policy or the security of the Application, please open an issue on the official GitHub repository:
* **Repository**: [https://github.com/fauzi2nd/SmartFormatConverter/issues](https://github.com/fauzi2nd/SmartFormatConverter/issues)
