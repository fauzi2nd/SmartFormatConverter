package org.roruku.smartformatconverter.engine.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ImageConversionConfig(
    val outputFormat: ImageOutputFormat,
    val jpegQuality: Int = 100,
    val chromaSubsampling: ChromaSubsampling = ChromaSubsampling.S444,
    val webpLossless: Boolean = false,
    val webpQuality: Int = 90,
    val preserveExif: Boolean = true,
    val customWidth: Int? = null,
    val customHeight: Int? = null,
    val maintainAspectRatio: Boolean = true,
)

enum class ImageOutputFormat(val extension: String, val mimeType: String) {
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp")
}

data class ExifTransform(
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
)

object ImageConverterEngine {

    suspend fun convertImage(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        config: ImageConversionConfig,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            coroutineContext.ensureActive()
            onProgress(0.1f)

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(inputUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            } ?: return@withContext Result.failure(IllegalArgumentException("Cannot open input URI"))

            val originalWidth = options.outWidth
            val originalHeight = options.outHeight

            if (originalWidth <= 0 || originalHeight <= 0) {
                return@withContext Result.failure(IllegalArgumentException("Invalid or unsupported image bounds"))
            }

            coroutineContext.ensureActive()
            onProgress(0.3f)

            val decodeOptions = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            var bitmap: Bitmap? = context.contentResolver.openInputStream(inputUri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext Result.failure(IllegalStateException("Failed to decode image bitmap"))

            coroutineContext.ensureActive()
            onProgress(0.5f)

            val transform = getExifTransform(context, inputUri)
            if (transform.rotationDegrees != 0 || transform.flipHorizontal) {
                bitmap = transformBitmap(bitmap!!, transform)
            }

            coroutineContext.ensureActive()
            onProgress(0.7f)

            if (config.customWidth != null || config.customHeight != null) {
                val targetW = config.customWidth ?: originalWidth
                val targetH = config.customHeight ?: originalHeight
                bitmap = resizeBitmap(bitmap!!, targetW, targetH, config.maintainAspectRatio)
            }

            val finalBitmap: Bitmap = if (config.outputFormat == ImageOutputFormat.JPEG && bitmap!!.hasAlpha()) {
                compositeOverWhiteBackground(bitmap)
            } else {
                bitmap!!
            }

            coroutineContext.ensureActive()
            onProgress(0.85f)

            when (config.outputFormat) {
                ImageOutputFormat.JPEG -> {
                    if (!JpegNativeEncoder.isNative444Available()) {
                        return@withContext Result.failure(IllegalStateException("Native libjpeg 4:4:4 encoder unavailable"))
                    }

                    val nativeSuccess = JpegNativeEncoder.encodeJpeg444(
                        bitmap = finalBitmap,
                        outputPath = outputFile.absolutePath,
                        quality = config.jpegQuality,
                        subsampling = config.chromaSubsampling,
                        whiteBackgroundForAlpha = true
                    )

                    if (!nativeSuccess || !outputFile.exists() || outputFile.length() == 0L || !validateJpegFile(outputFile)) {
                        if (outputFile.exists()) {
                            outputFile.delete()
                        }
                        return@withContext Result.failure(IllegalStateException("Native libjpeg encoding failed for ${outputFile.name}"))
                    }
                }
                ImageOutputFormat.PNG -> {
                    FileOutputStream(outputFile).use { outStream ->
                        finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
                    }
                }
                ImageOutputFormat.WEBP -> {
                    FileOutputStream(outputFile).use { outStream ->
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            val format = if (config.webpLossless) {
                                Bitmap.CompressFormat.WEBP_LOSSLESS
                            } else {
                                Bitmap.CompressFormat.WEBP_LOSSY
                            }
                            finalBitmap.compress(format, config.webpQuality.coerceIn(1, 100), outStream)
                        } else {
                            @Suppress("DEPRECATION")
                            finalBitmap.compress(Bitmap.CompressFormat.WEBP, config.webpQuality.coerceIn(1, 100), outStream)
                        }
                    }
                }
            }

            if (config.preserveExif && config.outputFormat == ImageOutputFormat.JPEG) {
                copyExifMetadata(context, inputUri, outputFile)
            }

            if (finalBitmap != bitmap) {
                bitmap.recycle()
            }
            finalBitmap.recycle()

            onProgress(1.0f)
            Result.success(outputFile)
        } catch (e: OutOfMemoryError) {
            Result.failure(Exception("Image too large to convert in memory. Out of Memory error.", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun validateJpegFile(file: File): Boolean {
        if (!file.exists() || file.length() < 100L) return false
        return try {
            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
            }
            val decodedBitmap = BitmapFactory.decodeFile(file.absolutePath, options)
            val isValid = decodedBitmap != null && decodedBitmap.width > 0 && decodedBitmap.height > 0
            decodedBitmap?.recycle()
            isValid
        } catch (_: Exception) {
            false
        }
    }

    private fun compositeOverWhiteBackground(src: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(src, 0f, 0f, null)
        return result
    }

    private fun transformBitmap(src: Bitmap, transform: ExifTransform): Bitmap {
        if (transform.rotationDegrees == 0 && !transform.flipHorizontal) return src
        val matrix = Matrix().apply {
            if (transform.rotationDegrees != 0) {
                postRotate(transform.rotationDegrees.toFloat())
            }
            if (transform.flipHorizontal) {
                postScale(-1f, 1f)
            }
        }
        val transformed = Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        if (transformed != src) {
            src.recycle()
        }
        return transformed
    }

    private fun resizeBitmap(src: Bitmap, targetW: Int, targetH: Int, maintainRatio: Boolean): Bitmap {
        val (finalW, finalH) = if (maintainRatio) {
            val ratio = src.width.toFloat() / src.height.toFloat()
            if (targetW / ratio <= targetH) {
                Pair(targetW, (targetW / ratio).toInt())
            } else {
                Pair((targetH * ratio).toInt(), targetH)
            }
        } else {
            Pair(targetW, targetH)
        }
        return Bitmap.createScaledBitmap(src, finalW, finalH, true)
    }

    private fun getExifTransform(context: Context, uri: Uri): ExifTransform {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                val orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
                return when (orientation) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> ExifTransform(90, false)
                    ExifInterface.ORIENTATION_ROTATE_180 -> ExifTransform(180, false)
                    ExifInterface.ORIENTATION_ROTATE_270 -> ExifTransform(270, false)
                    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> ExifTransform(0, true)
                    ExifInterface.ORIENTATION_FLIP_VERTICAL -> ExifTransform(180, true)
                    ExifInterface.ORIENTATION_TRANSPOSE -> ExifTransform(90, true)
                    ExifInterface.ORIENTATION_TRANSVERSE -> ExifTransform(270, true)
                    else -> ExifTransform(0, false)
                }
            }
        } catch (_: Exception) {
            // Ignore
        }
        return ExifTransform(0, false)
    }

    private fun copyExifMetadata(context: Context, inputUri: Uri, outputFile: File) {
        try {
            context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
                val sourceExif = ExifInterface(inputStream)
                val destExif = ExifInterface(outputFile.absolutePath)

                val attributes = arrayOf(
                    ExifInterface.TAG_MAKE,
                    ExifInterface.TAG_MODEL,
                    ExifInterface.TAG_DATETIME,
                    ExifInterface.TAG_DATETIME_ORIGINAL,
                    ExifInterface.TAG_DATETIME_DIGITIZED,
                    ExifInterface.TAG_FLASH,
                    ExifInterface.TAG_FOCAL_LENGTH,
                    ExifInterface.TAG_GPS_LATITUDE,
                    ExifInterface.TAG_GPS_LATITUDE_REF,
                    ExifInterface.TAG_GPS_LONGITUDE,
                    ExifInterface.TAG_GPS_LONGITUDE_REF,
                    ExifInterface.TAG_GPS_ALTITUDE,
                    ExifInterface.TAG_GPS_ALTITUDE_REF,
                    ExifInterface.TAG_WHITE_BALANCE,
                    ExifInterface.TAG_EXPOSURE_TIME,
                    ExifInterface.TAG_F_NUMBER,
                    ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY
                )

                for (attr in attributes) {
                    val value = sourceExif.getAttribute(attr)
                    if (value != null) {
                        destExif.setAttribute(attr, value)
                    }
                }
                destExif.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                destExif.saveAttributes()
            }
        } catch (_: Exception) {
            // Ignore
        }
    }
}
