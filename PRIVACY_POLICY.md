# Privacy Policy for Smart Format Converter

*Last updated: October 25, 2026*

Smart Format Converter ("the Application") is an open-source, privacy-first multimedia utility application designed to convert, transcode, and resize video, audio, and image files on Android devices.

We believe that your personal files, media, and data belong exclusively to you. This Privacy Policy outlines how the Application handles your data and permissions.

---

### 1. Zero Data Collection & On-Device Processing

* **No Personal Data Collection**: The Application does not collect, record, log, or harvest any personal information, device identifiers, email addresses, phone numbers, or usage analytics.
* **Local Conversion**: Media transcoding, image conversion, metadata parsing, and compression are performed on your device (using Android Media3 and Android Graphics APIs). The application does not upload local files to a cloud transcoding service. Downloader requests fetch content from the user-provided internet address.
* **No Analytics or Advertising SDKs**: The current application code contains no analytics or advertising integrations. Open-source dependencies such as AndroidX Media3, Room and Coil are used to implement application features. Review the final dependency graph before release.

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
* **Temporary Cache**: Media conversions use private temporary cache files, with cleanup during normal completion, failure and cancellation handling. Partial direct HTTP downloads may be retained to support resume until a job is deleted or the cache is cleared. Android may also clear app cache when device storage is low.

---

### 4. Children's Privacy

The Application is not designed to collect information from children. App-store age targeting and suitability should be reviewed before publication; the direct URL downloader accesses sites selected by the user.

---

### 5. Open Source Transparency

Smart Format Converter is committed to complete transparency. The source code of the Application is publicly available on GitHub at [https://github.com/fauzi2nd/SmartFormatConverter](https://github.com/fauzi2nd/SmartFormatConverter) for independent inspection and auditing.

---

### 6. Contact and Inquiries

If you have any questions or feedback regarding this Privacy Policy or the security of the Application, please open an issue on the official GitHub repository:
* **Repository**: [https://github.com/fauzi2nd/SmartFormatConverter/issues](https://github.com/fauzi2nd/SmartFormatConverter/issues)

### 7. Network Requests from the Downloader

Downloading a direct HTTP/HTTPS URL sends requests to the specified host (and may follow permitted redirects). The destination server can observe network information normally available to web servers, such as the device's IP address. Ordinary HTTP connections are not encrypted. File conversion itself does not require internet access.
