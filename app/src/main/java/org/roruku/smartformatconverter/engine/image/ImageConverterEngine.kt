package org.roruku.smartformatconverter.engine.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.heifwriter.HeifWriter
import androidx.heifwriter.AvifWriter
import com.squareup.gifencoder.GifEncoder
import com.squareup.gifencoder.ImageOptions
import java.io.BufferedOutputStream
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import org.roruku.smartformatconverter.data.storage.StorageManager

enum class ChromaSubsampling(val modeId: Int, val description: String) {
    S444(0, "4:4:4 (Full Quality)"),
    S422(1, "4:2:2 (High Quality)"),
    S420(2, "4:2:0 (Standard Compression)")
}

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
    val cropToExactRatio: Boolean = false,
)

enum class ImageOutputFormat(val extension: String, val mimeType: String) {
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp"),
    HEIC("heic", "image/heic"),
    AVIF("avif", "image/avif"),
    GIF("gif", "image/gif")
}

data class ExifTransform(
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
)

data class ResizePlan(
    val cropX: Int,
    val cropY: Int,
    val cropW: Int,
    val cropH: Int,
    val outputW: Int,
    val outputH: Int,
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
        var workingBitmap: Bitmap? = null
        var encodedBitmap: Bitmap? = null
        var completed = false
        try {
            coroutineContext.ensureActive()
            onProgress(0.1f)

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            StorageManager.openReadableInputStream(context, inputUri).use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }

            val originalWidth = options.outWidth
            val originalHeight = options.outHeight

            if (originalWidth <= 0 || originalHeight <= 0) {
                return@withContext Result.failure(IllegalArgumentException("Invalid or unsupported image bounds"))
            }

            coroutineContext.ensureActive()
            onProgress(0.3f)

            // For a requested downscale, decode a smaller intermediate instead
            // of allocating every source pixel in the Java bitmap heap.
            // Keep the original resolution when no resize was requested.
            val sampleSize = calculateDecodeSampleSize(
                originalWidth, originalHeight, config
            )
            val decodeOptions = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inSampleSize = sampleSize
            }
            var bitmap: Bitmap? = StorageManager.openReadableInputStream(context, inputUri).use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext Result.failure(IllegalStateException("Image decoder could not decode this file; it may be corrupt or unsupported"))
            workingBitmap = bitmap

            coroutineContext.ensureActive()
            onProgress(0.5f)

            val transform = getExifTransform(context, inputUri)
            if (transform.rotationDegrees != 0 || transform.flipHorizontal) {
                bitmap = transformBitmap(bitmap!!, transform)
                workingBitmap = bitmap
            }

            coroutineContext.ensureActive()
            onProgress(0.7f)

            if (config.customWidth != null || config.customHeight != null) {
                val targetW = config.customWidth ?: originalWidth
                val targetH = config.customHeight ?: originalHeight
                bitmap = resizeBitmap(
                    bitmap!!,
                    targetW,
                    targetH,
                    config.maintainAspectRatio,
                    config.preventUpscale,
                    config.cropToExactRatio
                )
                workingBitmap = bitmap
            }

            val finalBitmap: Bitmap = if (config.outputFormat in setOf(ImageOutputFormat.JPEG, ImageOutputFormat.HEIC, ImageOutputFormat.GIF) && bitmap!!.hasAlpha()) {
                compositeOverWhiteBackground(bitmap)
            } else {
                bitmap!!
            }

            encodedBitmap = finalBitmap
            coroutineContext.ensureActive()
            onProgress(0.85f)

            when (config.outputFormat) {
                ImageOutputFormat.JPEG -> {
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
                            "JPEG encoding failed"
                        ))
                    }
                }
                ImageOutputFormat.PNG -> {
                    val written = FileOutputStream(outputFile).use { outStream ->
                        finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, outStream)
                    }
                    if (!written) throw IllegalStateException("PNG encoder rejected the image")
                }
                ImageOutputFormat.WEBP -> {
                    if (config.webpLossless &&
                        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R
                    ) {
                        throw UnsupportedOperationException(
                            "Lossless WebP encoding requires Android 11 (API 30) or newer"
                        )
                    }
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
                ImageOutputFormat.HEIC -> {
                    require(android.os.Build.VERSION.SDK_INT >= 29) {
                        "HEIC export needs Android 10 or newer"
                    }
                    HeifWriter.Builder(
                        outputFile.absolutePath, finalBitmap.width, finalBitmap.height,
                        HeifWriter.INPUT_MODE_BITMAP
                    ).setMaxImages(1).setQuality(config.jpegQuality.coerceIn(1, 100))
                        .build().use { writer ->
                            writer.start()
                            writer.addBitmap(finalBitmap)
                            writer.stop(120_000L)
                        }
                }
                ImageOutputFormat.AVIF -> {
                    require(android.os.Build.VERSION.SDK_INT >= 34) {
                        "AVIF export needs Android 14 or newer"
                    }
                    AvifWriter.Builder(
                        outputFile.absolutePath, finalBitmap.width, finalBitmap.height,
                        AvifWriter.INPUT_MODE_BITMAP
                    ).setMaxImages(1).setQuality(config.jpegQuality.coerceIn(1, 100))
                        .build().use { writer ->
                            writer.start()
                            writer.addBitmap(finalBitmap)
                            writer.stop(120_000L)
                        }
                }
                ImageOutputFormat.GIF -> {
                    // This converts a still image to one GIF89a frame.
                    // Animated-source preservation is NOT claimed.
                    require(finalBitmap.width <= 2048 && finalBitmap.height <= 2048) {
                        "GIF export is limited to 2048×2048 to control memory use"
                    }
                    val width = finalBitmap.width
                    val height = finalBitmap.height
                    val pixels = IntArray(width * height)
                    finalBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                    val rgb = Array(height) { y ->
                        IntArray(width) { x -> pixels[y * width + x] and 0x00FFFFFF }
                    }
                    BufferedOutputStream(FileOutputStream(outputFile)).use { out ->
                        GifEncoder(out, width, height, 0)
                            .addImage(rgb, ImageOptions())
                            .finishEncoding()
                    }
                }
            }
            if (!outputFile.exists() || outputFile.length() == 0L) {
                throw IllegalStateException("Image encoder produced an empty file")
            }

            if (config.preserveExif && config.outputFormat == ImageOutputFormat.JPEG) {
                copyExifMetadata(context, inputUri, outputFile)
            }

            completed = true
            onProgress(1.0f)
            Result.success(outputFile)
        } catch (e: CancellationException) {
            // Propagate pause/cancel instead of returning a normal failure result.
            throw e
        } catch (e: OutOfMemoryError) {
            Result.failure(Exception("Image too large to convert in memory. Out of Memory error.", e))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            // Compressed output is private until it has been fully encoded.
            // Interrupted or failed jobs must never leave partial cache files.
            if (!completed) outputFile.delete()
            if (encodedBitmap !== workingBitmap) encodedBitmap?.recycle()
            workingBitmap?.recycle()
        }
    }

    /**
     * Decode at least twice the target resolution to retain interpolation quality.
     * Exact cropping can change the needed source region, so use full decode there.
     */
    fun calculateDecodeSampleSize(
        sourceWidth: Int,
        sourceHeight: Int,
        config: ImageConversionConfig
    ): Int {
        val targetW = config.customWidth ?: return 1
        val targetH = config.customHeight ?: return 1
        if (targetW <= 0 || targetH <= 0 ||
            !config.maintainAspectRatio || config.cropToExactRatio) return 1
        var sampleSize = 1
        while (sampleSize < 128 &&
            sourceWidth.toLong() / (sampleSize * 2) >= targetW.toLong() * 2 &&
            sourceHeight.toLong() / (sampleSize * 2) >= targetH.toLong() * 2) {
            sampleSize *= 2
        }
        return sampleSize
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

    fun calculateResizePlan(
        srcW: Int,
        srcH: Int,
        targetW: Int,
        targetH: Int,
        maintainRatio: Boolean,
        preventUpscale: Boolean,
        cropToExactRatio: Boolean = false
    ): ResizePlan {
        require(srcW > 0 && srcH > 0 && targetW > 0 && targetH > 0) { "Image dimensions must be positive" }
        if (maintainRatio && cropToExactRatio) {
            val targetRatio = targetW.toDouble() / targetH.toDouble()
            val srcRatio = srcW.toDouble() / srcH.toDouble()
            val (cropW, cropH) = if (srcRatio > targetRatio) {
                val w = (srcH * targetRatio).toInt().coerceIn(1, srcW)
                Pair(w, srcH)
            } else {
                val h = (srcW / targetRatio).toInt().coerceIn(1, srcH)
                Pair(srcW, h)
            }
            val cropX = ((srcW - cropW) / 2).coerceAtLeast(0)
            val cropY = ((srcH - cropH) / 2).coerceAtLeast(0)

            val (outW, outH) = if (preventUpscale) {
                val scale = minOf(1.0, targetW.toDouble() / cropW)
                Pair((cropW * scale).toInt().coerceAtLeast(1), (cropH * scale).toInt().coerceAtLeast(1))
            } else {
                Pair(targetW, targetH)
            }
            return ResizePlan(cropX, cropY, cropW, cropH, outW, outH)
        }

        val boundedW = if (preventUpscale) targetW.coerceAtMost(srcW) else targetW
        val boundedH = if (preventUpscale) targetH.coerceAtMost(srcH) else targetH
        val (finalW, finalH) = if (maintainRatio) {
            val scale = minOf(
                boundedW.toDouble() / srcW,
                boundedH.toDouble() / srcH
            )
            Pair((srcW * scale).toInt().coerceAtLeast(1), (srcH * scale).toInt().coerceAtLeast(1))
        } else {
            Pair(boundedW, boundedH)
        }
        return ResizePlan(0, 0, srcW, srcH, finalW, finalH)
    }

    private fun resizeBitmap(
        src: Bitmap,
        targetW: Int,
        targetH: Int,
        maintainRatio: Boolean,
        preventUpscale: Boolean,
        cropToExactRatio: Boolean = false
    ): Bitmap {
        val plan = calculateResizePlan(
            srcW = src.width,
            srcH = src.height,
            targetW = targetW,
            targetH = targetH,
            maintainRatio = maintainRatio,
            preventUpscale = preventUpscale,
            cropToExactRatio = cropToExactRatio
        )

        var current = if (plan.cropW != src.width || plan.cropH != src.height) {
            val cropped = Bitmap.createBitmap(src, plan.cropX, plan.cropY, plan.cropW, plan.cropH)
            if (cropped !== src) {
                src.recycle()
            }
            cropped
        } else {
            src
        }

        if (current.width != plan.outputW || current.height != plan.outputH) {
            val resized = current.scale(plan.outputW, plan.outputH, true)
            if (resized !== current) {
                current.recycle()
            }
            current = resized
        }

        return current
    }

    private fun getExifTransform(context: Context, uri: Uri): ExifTransform {
        try {
            StorageManager.openReadableInputStream(context, uri).use { stream ->
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
            StorageManager.openReadableInputStream(context, inputUri).use { inputStream ->
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
