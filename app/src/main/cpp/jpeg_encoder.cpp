#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <stdio.h>
#include <stdlib.h>
#include "turbojpeg.h"

#define LOG_TAG "JpegNativeEncoder"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jboolean JNICALL
Java_org_roruku_smartformatconverter_engine_image_JpegNativeEncoder_nativeEncodeJpeg(
        JNIEnv* env,
        jclass clazz,
        jobject bitmap,
        jstring jOutputPath,
        jint quality,
        jint subsamplingMode, // 0 = 4:4:4, 1 = 4:2:2, 2 = 4:2:0
        jboolean preserveTransparencyWhite
) {
    AndroidBitmapInfo info;
    void* pixels = nullptr;

    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGE("Failed getting AndroidBitmapInfo");
        return JNI_FALSE;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Unsupported bitmap format. Must be RGBA_8888");
        return JNI_FALSE;
    }

    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0 || !pixels) {
        LOGE("Failed locking bitmap pixels");
        return JNI_FALSE;
    }

    const char* outputPath = env->GetStringUTFChars(jOutputPath, nullptr);
    if (!outputPath) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return JNI_FALSE;
    }

    int width = info.width;
    int height = info.height;
    int stride = info.stride;
    uint8_t* argbBytes = (uint8_t*)pixels;

    uint8_t* rgbaBuffer = (uint8_t*)malloc(width * height * 4);
    if (!rgbaBuffer) {
        LOGE("Failed allocating memory for RGBA buffer");
        env->ReleaseStringUTFChars(jOutputPath, outputPath);
        AndroidBitmap_unlockPixels(env, bitmap);
        return JNI_FALSE;
    }

    for (int y = 0; y < height; ++y) {
        uint8_t* srcRow = argbBytes + (y * stride);
        uint8_t* dstRow = rgbaBuffer + (y * width * 4);

        for (int x = 0; x < width; ++x) {
            uint8_t r = srcRow[x * 4 + 0];
            uint8_t g = srcRow[x * 4 + 1];
            uint8_t b = srcRow[x * 4 + 2];
            uint8_t a = srcRow[x * 4 + 3];

            if (preserveTransparencyWhite && a < 255) {
                float alpha = a / 255.0f;
                r = (uint8_t)(r * alpha + 255.0f * (1.0f - alpha));
                g = (uint8_t)(g * alpha + 255.0f * (1.0f - alpha));
                b = (uint8_t)(b * alpha + 255.0f * (1.0f - alpha));
                a = 255;
            }

            dstRow[x * 4 + 0] = r;
            dstRow[x * 4 + 1] = g;
            dstRow[x * 4 + 2] = b;
            dstRow[x * 4 + 3] = a;
        }
    }

    tjhandle tjCompressor = tj3Init(TJINIT_COMPRESS);
    if (!tjCompressor) {
        LOGE("Failed initializing TurboJPEG 3 compressor instance");
        free(rgbaBuffer);
        env->ReleaseStringUTFChars(jOutputPath, outputPath);
        AndroidBitmap_unlockPixels(env, bitmap);
        return JNI_FALSE;
    }

    int tjSubsamp = TJSAMP_444;
    if (subsamplingMode == 1) {
        tjSubsamp = TJSAMP_422;
    } else if (subsamplingMode == 2) {
        tjSubsamp = TJSAMP_420;
    }

    tj3Set(tjCompressor, TJPARAM_QUALITY, quality < 1 ? 1 : (quality > 100 ? 100 : quality));
    tj3Set(tjCompressor, TJPARAM_SUBSAMP, tjSubsamp);

    unsigned char* jpegBuf = nullptr;
    size_t jpegSize = 0;

    int compressResult = tj3Compress8(
            tjCompressor,
            rgbaBuffer,
            width,
            width * 4,
            height,
            TJPF_RGBA,
            &jpegBuf,
            &jpegSize
    );

    free(rgbaBuffer);

    if (compressResult != 0 || !jpegBuf || jpegSize == 0) {
        LOGE("tj3Compress8 failed: %s", tj3GetErrorStr(tjCompressor));
        tj3Destroy(tjCompressor);
        env->ReleaseStringUTFChars(jOutputPath, outputPath);
        AndroidBitmap_unlockPixels(env, bitmap);
        return JNI_FALSE;
    }

    FILE* outFile = fopen(outputPath, "wb");
    if (!outFile) {
        LOGE("Failed opening output destination file: %s", outputPath);
        tj3Free(jpegBuf);
        tj3Destroy(tjCompressor);
        env->ReleaseStringUTFChars(jOutputPath, outputPath);
        AndroidBitmap_unlockPixels(env, bitmap);
        return JNI_FALSE;
    }

    size_t written = fwrite(jpegBuf, 1, jpegSize, outFile);
    fclose(outFile);
    tj3Free(jpegBuf);
    tj3Destroy(tjCompressor);

    env->ReleaseStringUTFChars(jOutputPath, outputPath);
    AndroidBitmap_unlockPixels(env, bitmap);

    if (written != jpegSize) {
        LOGE("Short write encoding JPEG to %s (%zu / %zu bytes)", outputPath, written, jpegSize);
        return JNI_FALSE;
    }

    LOGI("TurboJPEG 3 native encode succeeded for %s (%dx%d, Quality=%d, SubsamplingMode=%d, Bytes=%zu)",
         outputPath, width, height, quality, subsamplingMode, jpegSize);
    return JNI_TRUE;
}
