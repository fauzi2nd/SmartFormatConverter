package org.roruku.smartformatconverter.engine.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
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
    val preventUpscale: Boolean = true,
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
    private const val TAG = "ImageConverterEngine"

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
                bitmap = resizeBitmap(bitmap!!, targetW, targetH, config.maintainAspectRatio, config.preventUpscale)
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
                    // Some Android devices/ABIs cannot load our optional TurboJPEG JNI.
                    // A missing native library must not make ordinary JPEG conversion fail.
                    // Android Bitmap JPEG fallback may use chroma subsampling other than 4:4:4.
                    val nativeSuccess = JpegNativeEncoder.isNative444Available() &&
                        JpegNativeEncoder.encodeJpeg444(
                            bitmap = finalBitmap,
                            outputPath = outputFile.absolutePath,
                            quality = config.jpegQuality,
                            subsampling = config.chromaSubsampling,
                            whiteBackgroundForAlpha = true
                        )
                    if (!nativeSuccess || !validateJpegFile(outputFile)) {
                        Log.w(TAG, "Native JPEG encoding unavailable or invalid; using Android JPEG fallback (subsampling not guaranteed)")
                        outputFile.delete()
                        val compressed = FileOutputStream(outputFile).use { stream ->
                            finalBitmap.compress(
                                Bitmap.CompressFormat.JPEG,
                                config.jpegQuality.coerceIn(1, 100),
                                stream
                            )
                        }
                        if (!compressed || !validateJpegFile(outputFile)) {
                            outputFile.delete()
                            return@withContext Result.failure(IllegalStateException(
                                "JPEG encoding failed (native and Android fallback)"
                            ))
                        }
                    }
                }
                ImageOutputFormat.PNG -> {
                    val written = FileOutputStream(outputFile).use { outStream ->
                        finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
                    }
                    if (!written) throw IllegalStateException("PNG encoder rejected the image")
                }
                ImageOutputFormat.WEBP -> {
                    val written = FileOutputStream(outputFile).use { outStream ->
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
                    if (!written) throw IllegalStateException("WebP encoder rejected the image")
                }
            }
            if (!outputFile.exists() || outputFile.length() == 0L) {
                throw IllegalStateException("Image encoder produced an empty file")
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
            // A full second decode doubles peak heap use and can crash on large images.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            bounds.outWidth > 0 && bounds.outHeight > 0
        } catch (_: Exception) {
            false
        }
    }

    private fun compositeOverWhiteBackground(src: Bitmap): Bitmap {
        val result = createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
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

    private fun resizeBitmap(
        src: Bitmap,
        targetW: Int,
        targetH: Int,
        maintainRatio: Boolean,
        preventUpscale: Boolean
    ): Bitmap {
        require(targetW > 0 && targetH > 0) { "Image dimensions must be positive" }
        val boundedW = if (preventUpscale) targetW.coerceAtMost(src.width) else targetW
        val boundedH = if (preventUpscale) targetH.coerceAtMost(src.height) else targetH
        val (finalW, finalH) = if (maintainRatio) {
            val scale = minOf(
                boundedW.toDouble() / src.width,
                boundedH.toDouble() / src.height
            )
            Pair((src.width * scale).toInt().coerceAtLeast(1),
                (src.height * scale).toInt().coerceAtLeast(1))
        } else {
            Pair(boundedW, boundedH)
        }
        if (src.width == finalW && src.height == finalH) return src
        val resized = src.scale(finalW, finalH, true)
        src.recycle()
        return resized
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
