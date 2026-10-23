package org.roruku.smartformatconverter.data.storage

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.roruku.smartformatconverter.data.db.ConversionJobEntity
import org.roruku.smartformatconverter.data.db.MediaType

enum class BatchOutputStructure {
    FLAT,
    MIRRORED
}

data class BatchScanConfig(
    val targetVideoFormat: String = "mp4",
    val targetImageFormat: String = "jpg",
    val targetAudioFormat: String = "mp3",
    val outputStructure: BatchOutputStructure = BatchOutputStructure.MIRRORED,
    val presetName: String = "Balanced",
)

object BatchConversionScanner {

    fun scanDocumentTree(
        context: Context,
        treeUri: Uri,
        config: BatchScanConfig,
        excludeSubfolderName: String = "Converted",
    ): List<ConversionJobEntity> {
        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val results = mutableListOf<ConversionJobEntity>()
        scanDocumentRecursive(context, rootDoc, "", config, excludeSubfolderName, results)
        return results
    }

    private fun scanDocumentRecursive(
        context: Context,
        dir: DocumentFile,
        relativeDirectoryPath: String,
        config: BatchScanConfig,
        excludeSubfolderName: String,
        results: MutableList<ConversionJobEntity>
    ) {
        if ((!dir.isDirectory) || (dir.name?.equals(excludeSubfolderName, ignoreCase = true) == true)) {
            return
        }

        val files = dir.listFiles()
        for (file in files) {
            if (file.isDirectory) {
                val folderName = file.name ?: "subfolder"
                if (!folderName.equals(excludeSubfolderName, ignoreCase = true)) {
                    val nextRelativePath = if (relativeDirectoryPath.isBlank()) {
                        folderName
                    } else {
                        "$relativeDirectoryPath/$folderName"
                    }
                    scanDocumentRecursive(context, file, nextRelativePath, config, excludeSubfolderName, results)
                }
            } else if (file.isFile) {
                val mimeType = file.type ?: StorageManager.getMimeType(context, file.uri)
                val mediaType = determineMediaType(mimeType) ?: continue

                val targetFormat = when (mediaType) {
                    MediaType.VIDEO -> config.targetVideoFormat
                    MediaType.IMAGE -> config.targetImageFormat
                    MediaType.AUDIO -> config.targetAudioFormat
                    MediaType.DOWNLOAD -> continue
                }

                val relPath = if (config.outputStructure == BatchOutputStructure.MIRRORED) relativeDirectoryPath else null

                val job = ConversionJobEntity(
                    fileName = file.name ?: "media_file",
                    inputUri = file.uri.toString(),
                    mediaType = mediaType,
                    inputFormat = mimeType.substringAfter('/'),
                    outputFormat = targetFormat,
                    presetName = config.presetName,
                    fileSize = file.length(),
                    relativeFolderPath = relPath
                )
                results.add(job)
            }
        }
    }

    private fun determineMediaType(mimeType: String): MediaType? {
        return when {
            mimeType.startsWith("video/") -> MediaType.VIDEO
            mimeType.startsWith("image/") -> MediaType.IMAGE
            mimeType.startsWith("audio/") -> MediaType.AUDIO
            else -> null
        }
    }
}
