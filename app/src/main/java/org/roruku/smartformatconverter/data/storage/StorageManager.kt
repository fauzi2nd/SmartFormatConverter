package org.roruku.smartformatconverter.data.storage

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.InputStream
import java.io.OutputStream

data class MediaDetails(
    val fileName: String,
    val fileSize: Long,
    val mimeType: String?,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val rotation: Int = 0,
    val bitrate: Int = 0,
    val audioChannels: Int = 0,
    val sampleRate: Int = 0,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
)

object StorageManager {
    private const val TAG = "StorageManager"

    fun persistUriPermission(context: Context, uri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist URI permission for $uri", e)
        }
    }

    fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index != -1) {
                            result = cursor.getString(index)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to query display name for content URI", e)
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "unnamed_file"
    }

    fun getFileSize(context: Context, uri: Uri): Long {
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            try {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if ((index != -1) && (!cursor.isNull(index))) {
                            return cursor.getLong(index)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to query file size for content URI", e)
            }
        } else if (uri.scheme == ContentResolver.SCHEME_FILE) {
            uri.path?.let { path ->
                val file = File(path)
                if (file.exists()) return file.length()
            }
        }
        return 0L
    }

    fun getMimeType(context: Context, uri: Uri): String {
        return context.contentResolver.getType(uri) ?: run {
            val extension = getFileName(context, uri).substringAfterLast('.', "").lowercase()
            when (extension) {
                "mp4" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                "mov" -> "video/quicktime"
                "avi" -> "video/x-msvideo"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "webp" -> "image/webp"
                "mp3" -> "audio/mpeg"
                "aac" -> "audio/aac"
                "m4a" -> "audio/mp4"
                "flac" -> "audio/flac"
                "wav" -> "audio/wav"
                "ogg" -> "audio/ogg"
                else -> "*/*"
            }
        }
    }

    fun createTempFileInCache(context: Context, prefix: String, extension: String): File {
        val cacheDir = File(context.cacheDir, "temp_conversion")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val safeExt = if (extension.startsWith(".")) extension else ".$extension"
        return File.createTempFile("${prefix}_${System.currentTimeMillis()}", safeExt, cacheDir)
    }

    fun createDestinationDocumentFile(
        context: Context,
        treeUri: Uri,
        relativeSubfolder: String?,
        fileName: String,
        mimeType: String
    ): DocumentFile? {
        try {
            var rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return null
            if (!relativeSubfolder.isNullOrBlank()) {
                val parts = relativeSubfolder.split('/', '\\').filter { it.isNotBlank() }
                for (part in parts) {
                    val existing = rootDoc.findFile(part)
                    rootDoc = if (existing != null && existing.isDirectory) {
                        existing
                    } else {
                        rootDoc.createDirectory(part) ?: return null
                    }
                }
            }
            return rootDoc.createFile(mimeType, fileName)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating destination document file", e)
            return null
        }
    }

    fun copyUriToTempFile(context: Context, uri: Uri, destFile: File): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    copyStream(input, output)
                }
            }
            destFile.exists() && destFile.length() > 0
        } catch (e: Exception) {
            Log.e(TAG, "Failed copying URI $uri to temp file ${destFile.absolutePath}", e)
            false
        }
    }

    fun copyFileToDocumentUri(context: Context, sourceFile: File, destDocumentFile: DocumentFile): Boolean {
        return try {
            context.contentResolver.openOutputStream(destDocumentFile.uri)?.use { output ->
                sourceFile.inputStream().use { input ->
                    copyStream(input, output)
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed copying file ${sourceFile.absolutePath} to document ${destDocumentFile.uri}", e)
            false
        }
    }

    private fun copyStream(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(64 * 1024)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
        }
        output.flush()
    }

    fun parseMediaDetails(context: Context, uri: Uri): MediaDetails {
        val fileName = getFileName(context, uri)
        val fileSize = getFileSize(context, uri)
        val mimeType = getMimeType(context, uri)

        var durationMs = 0L
        var width = 0
        var height = 0
        var rotation = 0
        var bitrate = 0
        val audioChannels = 0
        val sampleRate = 0

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.let {
                durationMs = it.toLongOrNull() ?: 0L
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.let {
                width = it.toIntOrNull() ?: 0
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.let {
                height = it.toIntOrNull() ?: 0
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.let {
                rotation = it.toIntOrNull() ?: 0
            }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.let {
                bitrate = it.toIntOrNull() ?: 0
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaMetadataRetriever failed to extract metadata for $uri", e)
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // Ignore
            }
        }

        return MediaDetails(
            fileName = fileName,
            fileSize = fileSize,
            mimeType = mimeType,
            durationMs = durationMs,
            width = width,
            height = height,
            rotation = rotation,
            bitrate = bitrate,
            audioChannels = audioChannels,
            sampleRate = sampleRate
        )
    }

    fun clearCache(context: Context) {
        try {
            val cacheDir = File(context.cacheDir, "temp_conversion")
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache directory", e)
        }
    }
}
