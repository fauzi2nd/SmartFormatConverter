package org.roruku.smartformatconverter.data.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
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

    /**
     * Scan only the selected media category. An explicit output-parent URI allows
     * the selected root's relative path to be preserved under parent/Converter/.
     *
     * E.g. parent=Torrents, selected=Torrents/Hasumi =>
     *      Torrents/Converter/Hasumi/<file>.
     *
     * Choosing the parent itself preserves child paths but does not insert the
     * parent name twice.
     */
    fun scanDocumentTree(
        context: Context,
        treeUri: Uri,
        config: BatchScanConfig,
        mediaTypeFilter: MediaType,
        outputParentUri: Uri? = null,
    ): List<ConversionJobEntity> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        if (!root.isDirectory) return emptyList()

        val rootName = root.name.orEmpty()
        if (isExcludedFolder(rootName) || isInsideOutputTree(treeUri, outputParentUri)) {
            return emptyList()
        }

        val relativeRoot = selectedRootRelativePath(treeUri, outputParentUri, rootName)
        val results = mutableListOf<ConversionJobEntity>()
        scanRecursive(context, root, relativeRoot, config, mediaTypeFilter, results)
        return results
    }

    private fun scanRecursive(
        context: Context,
        directory: DocumentFile,
        relativePath: String,
        config: BatchScanConfig,
        mediaTypeFilter: MediaType,
        result: MutableList<ConversionJobEntity>
    ) {
        if (!directory.isDirectory || isExcludedFolder(directory.name.orEmpty())) return

        for (entry in directory.listFiles()) {
            val name = entry.name ?: continue
            if (entry.isDirectory) {
                if (!isExcludedFolder(name)) {
                    val nextPath = listOf(relativePath, name)
                        .filter { it.isNotBlank() }
                        .joinToString("/")
                    scanRecursive(context, entry, nextPath, config, mediaTypeFilter, result)
                }
                continue
            }
            if (!entry.isFile) continue

            val mime = entry.type ?: StorageManager.getMimeType(context, entry.uri)
            val detected = MediaSelectionPolicy.detect(mime, name)
            if (detected != mediaTypeFilter) continue

            val targetFormat = when (detected) {
                MediaType.VIDEO -> config.targetVideoFormat
                MediaType.IMAGE -> config.targetImageFormat
                MediaType.AUDIO -> config.targetAudioFormat
                MediaType.DOWNLOAD -> continue
            }

            result.add(
                ConversionJobEntity(
                    fileName = name,
                    inputUri = entry.uri.toString(),
                    mediaType = detected,
                    inputFormat = mime.substringAfter('/'),
                    outputFormat = targetFormat,
                    presetName = config.presetName,
                    fileSize = entry.length(),
                    relativeFolderPath = if (config.outputStructure == BatchOutputStructure.MIRRORED) {
                        OutputPathPolicy.safeRelativePath(relativePath)
                    } else {
                        ""
                    }
                )
            )
        }
    }

    private fun isExcludedFolder(name: String): Boolean =
        name.lowercase() in OutputPathPolicy.EXCLUDED_INPUT_FOLDERS

    private fun selectedRootRelativePath(
        selected: Uri,
        parent: Uri?,
        selectedName: String
    ): String {
        if (parent != null && parent.authority == selected.authority) {
            val selectedId = documentTreeId(selected)
            val parentId = documentTreeId(parent)
            if (selectedId != null && parentId != null) {
                if (selectedId.equals(parentId, ignoreCase = true)) return ""
                if (selectedId.startsWith("$parentId/", ignoreCase = true)) {
                    return OutputPathPolicy.safeRelativePath(selectedId.substring(parentId.length + 1))
                }
            }
        }
        return OutputPathPolicy.safeRelativePath(selectedName)
    }

    private fun isInsideOutputTree(selected: Uri, parent: Uri?): Boolean {
        val selectedId = documentTreeId(selected) ?: return false
        val parentId = parent?.takeIf { it.authority == selected.authority }
            ?.let(::documentTreeId) ?: return false

        if (!selectedId.startsWith("$parentId/", ignoreCase = true)) return false
        val relative = selectedId.substring(parentId.length + 1)
        return relative.substringBefore('/').equals(
            OutputPathPolicy.OUTPUT_FOLDER,
            ignoreCase = true
        ) || relative.substringBefore('/').equals("Converted", ignoreCase = true)
    }

    private fun documentTreeId(uri: Uri): String? =
        try {
            DocumentsContract.getTreeDocumentId(uri)
        } catch (_: IllegalArgumentException) {
            null
        }
}
