package org.roruku.smartformatconverter.data.storage

import org.roruku.smartformatconverter.data.preferences.FilenameFormat

/**
 * One output root per user-selected parent folder:
 * parent/Converter/<batch-relative-path>/<original-name>.<target-ext>.
 *
 * Files added via +Files use an empty relative path, and therefore go straight
 * into parent/Converter. This is independent of the order in the job queue.
 */
object OutputPathPolicy {
    const val OUTPUT_FOLDER = "Converter"
    val EXCLUDED_INPUT_FOLDERS = setOf("converter", "converted")

    fun safeComponent(value: String): String =
        value.trim()
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .trim('.', ' ')
            .take(180)
            .ifBlank { "untitled" }

    fun safeRelativePath(relative: String?): String =
        relative.orEmpty()
            .replace('\\', '/')
            .split('/')
            .filter { it.isNotBlank() && it != "." && it != ".." }
            .map(::safeComponent)
            .joinToString("/")

    fun outputDirectory(relative: String?): String =
        listOf(OUTPUT_FOLDER, safeRelativePath(relative))
            .filter { it.isNotBlank() }
            .joinToString("/")

    fun outputFileName(
        originalName: String,
        extension: String,
        format: FilenameFormat = FilenameFormat.ORIGINAL,
        customPrefix: String = ""
    ): String {
        val cleanedName = safeComponent(originalName)
        val base = cleanedName.substringBeforeLast('.', cleanedName).ifBlank { "untitled" }
        val prefix = if (format == FilenameFormat.CUSTOM_PREFIX) safePrefix(customPrefix) else ""
        val ext = safeComponent(extension.removePrefix(".").lowercase())
        return "${prefix}${base}.${ext}"
    }

    private fun safePrefix(value: String): String =
        value.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").take(60)
}
