package org.roruku.smartformatconverter.engine.image

import android.graphics.Bitmap
import android.util.Log

enum class ChromaSubsampling(val modeId: Int, val description: String) {
    S444(0, "4:4:4 (Full Quality, No Subsampling)"),
    S422(1, "4:2:2 (High Quality)"),
    S420(2, "4:2:0 (Standard Compression)")
}

object JpegNativeEncoder {
    private const val TAG = "JpegNativeEncoder"

    private var isNativeLibraryLoaded = false

    init {
        try {
            System.loadLibrary("jpeg_encoder_native")
            isNativeLibraryLoaded = true
            try { Log.i(TAG, "Native JPEG 4:4:4 encoder library loaded successfully") } catch (_: Throwable) {}
        } catch (e: UnsatisfiedLinkError) {
            try { Log.w(TAG, "Native library jpeg_encoder_native not loaded, fallback enabled", e) } catch (_: Throwable) {}
            isNativeLibraryLoaded = false
        } catch (_: Throwable) {
            isNativeLibraryLoaded = false
        }
    }

    fun isNative444Available(): Boolean = isNativeLibraryLoaded

    fun encodeJpeg444(
        bitmap: Bitmap,
        outputPath: String,
        quality: Int = 100,
        subsampling: ChromaSubsampling = ChromaSubsampling.S444,
        whiteBackgroundForAlpha: Boolean = true,
    ): Boolean {
        if (!isNativeLibraryLoaded) {
            return false
        }
        return try {
            nativeEncodeJpeg(
                bitmap = bitmap,
                jOutputPath = outputPath,
                quality = quality.coerceIn(1, 100),
                subsamplingMode = subsampling.modeId,
                preserveTransparencyWhite = whiteBackgroundForAlpha
            )
        } catch (e: Exception) {
            try { Log.e(TAG, "Native JPEG encoding exception", e) } catch (_: Throwable) {}
            false
        }
    }

    @JvmStatic
    private external fun nativeEncodeJpeg(
        bitmap: Bitmap,
        jOutputPath: String,
        quality: Int,
        subsamplingMode: Int,
        preserveTransparencyWhite: Boolean
    ): Boolean
}
