#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <stdio.h>
#include <stdlib.h>
#include <string>
#include <memory>
#include <limits>
#include "turbojpeg.h"

#define LOG_TAG "JpegNativeEncoder"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

struct BitmapLocker {
    JNIEnv* env;
    jobject bitmap;
    bool locked;

    BitmapLocker(JNIEnv* env, jobject bitmap) : env(env), bitmap(bitmap), locked(false) {}

    int lock(void** pixels) {
        int res = AndroidBitmap_lockPixels(env, bitmap, pixels);
        if (res >= 0) {
            locked = true;
        }
        return res;
    }

    ~BitmapLocker() {
        if (locked) {
            AndroidBitmap_unlockPixels(env, bitmap);
        }
    }
};

struct JniStringHolder {
    JNIEnv* env;
    jstring jstr;
    const char* str;

    JniStringHolder(JNIEnv* env, jstring jstr) : env(env), jstr(jstr), str(nullptr) {
        if (jstr) {
            str = env->GetStringUTFChars(jstr, nullptr);
        }
    }

    ~JniStringHolder() {
        if (str && jstr) {
            env->ReleaseStringUTFChars(jstr, str);
        }
    }
};

struct Tj3Holder {
    tjhandle handle;

    Tj3Holder(tjhandle h) : handle(h) {}

    ~Tj3Holder() {
        if (handle) {
            tj3Destroy(handle);
        }
    }
};

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
    if (!bitmap || !jOutputPath) {
        LOGE("Invalid null parameters passed to nativeEncodeJpeg");
        return JNI_FALSE;
    }

    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        LOGE("Failed getting AndroidBitmapInfo");
        return JNI_FALSE;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        LOGE("Unsupported bitmap format (%d). Must be RGBA_8888", info.format);
        return JNI_FALSE;
    }

    int width = (int)info.width;
    int height = (int)info.height;

    if (width <= 0 || height <= 0 || width > 32768 || height > 32768) {
        LOGE("Invalid or out-of-range bitmap dimensions: %dx%d", width, height);
        return JNI_FALSE;
    }

    size_t numPixels = (size_t)width * (size_t)height;
    if (numPixels > (size_t)(SIZE_MAX / 4)) {
        LOGE("Integer overflow in pixel allocation calculation");
        return JNI_FALSE;
    }

    size_t bufferSize = numPixels * 4;

    BitmapLocker bitmapLocker(env, bitmap);
    void* pixels = nullptr;
    if (bitmapLocker.lock(&pixels) < 0 || !pixels) {
        LOGE("Failed locking bitmap pixels");
        return JNI_FALSE;
    }

    JniStringHolder outputPathHolder(env, jOutputPath);
    if (!outputPathHolder.str) {
        LOGE("Failed getting output path string");
        return JNI_FALSE;
    }

    std::string outputPathStr = outputPathHolder.str;
    if (outputPathStr.empty()) {
        LOGE("Output path string is empty");
        return JNI_FALSE;
    }

    int stride = (int)info.stride;
    if (stride < width * 4) {
        LOGE("Invalid bitmap row stride: %d (expected >= %d)", stride, width * 4);
        return JNI_FALSE;
    }

    uint8_t* argbBytes = (uint8_t*)pixels;
    uint8_t* rgbaBuffer = (uint8_t*)malloc(bufferSize);
    if (!rgbaBuffer) {
        LOGE("Failed allocating %zu bytes for RGBA buffer", bufferSize);
        return JNI_FALSE;
    }

    std::unique_ptr<uint8_t, void(*)(void*)> rgbaBufferGuard(rgbaBuffer, free);

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

    tjhandle rawHandle = tj3Init(TJINIT_COMPRESS);
    if (!rawHandle) {
        LOGE("Failed initializing TurboJPEG 3 compressor instance");
        return JNI_FALSE;
    }
    Tj3Holder tjCompressor(rawHandle);

    int tjSubsamp = TJSAMP_444;
    if (subsamplingMode == 1) {
        tjSubsamp = TJSAMP_422;
    } else if (subsamplingMode == 2) {
        tjSubsamp = TJSAMP_420;
    }

    int qualityClamped = quality < 1 ? 1 : (quality > 100 ? 100 : quality);

    if (tj3Set(tjCompressor.handle, TJPARAM_QUALITY, qualityClamped) != 0) {
        LOGE("tj3Set TJPARAM_QUALITY failed: %s", tj3GetErrorStr(tjCompressor.handle));
        return JNI_FALSE;
    }

    if (tj3Set(tjCompressor.handle, TJPARAM_SUBSAMP, tjSubsamp) != 0) {
        LOGE("tj3Set TJPARAM_SUBSAMP failed: %s", tj3GetErrorStr(tjCompressor.handle));
        return JNI_FALSE;
    }

    unsigned char* jpegBuf = nullptr;
    size_t jpegSize = 0;

    int compressResult = tj3Compress8(
            tjCompressor.handle,
            rgbaBuffer,
            width,
            width * 4,
            height,
            TJPF_RGBA,
            &jpegBuf,
            &jpegSize
    );

    if (compressResult != 0 || !jpegBuf || jpegSize == 0) {
        LOGE("tj3Compress8 failed: %s", tj3GetErrorStr(tjCompressor.handle));
        if (jpegBuf) {
            tj3Free(jpegBuf);
        }
        return JNI_FALSE;
    }

    std::unique_ptr<unsigned char, void(*)(void*)> jpegBufGuard(jpegBuf, tj3Free);

    std::string tmpPathStr = outputPathStr + ".tmp";
    FILE* outFile = fopen(tmpPathStr.c_str(), "wb");
    if (!outFile) {
        LOGE("Failed opening temp destination file: %s", tmpPathStr.c_str());
        return JNI_FALSE;
    }

    size_t written = fwrite(jpegBuf, 1, jpegSize, outFile);
    int flushRes = fflush(outFile);
    int closeRes = fclose(outFile);

    if (written != jpegSize || flushRes != 0 || closeRes != 0) {
        LOGE("File write/flush/close error for %s (written %zu / %zu bytes)", tmpPathStr.c_str(), written, jpegSize);
        remove(tmpPathStr.c_str());
        return JNI_FALSE;
    }

    if (rename(tmpPathStr.c_str(), outputPathStr.c_str()) != 0) {
        FILE* src = fopen(tmpPathStr.c_str(), "rb");
        FILE* dst = fopen(outputPathStr.c_str(), "wb");
        bool copyOk = false;
        if (src && dst) {
            char copyBuffer[8192];
            size_t bytesRead = 0;
            copyOk = true;
            while ((bytesRead = fread(copyBuffer, 1, sizeof(copyBuffer), src)) > 0) {
                if (fwrite(copyBuffer, 1, bytesRead, dst) != bytesRead) {
                    copyOk = false;
                    break;
                }
            }
        }
        if (src) fclose(src);
        if (dst) fclose(dst);
        remove(tmpPathStr.c_str());

        if (!copyOk) {
            LOGE("Failed renaming and copying temp file to final destination %s", outputPathStr.c_str());
            remove(outputPathStr.c_str());
            return JNI_FALSE;
        }
    }

    LOGI("TurboJPEG 3 native encode succeeded for %s (%dx%d, Quality=%d, SubsamplingMode=%d, Bytes=%zu)",
         outputPathStr.c_str(), width, height, qualityClamped, subsamplingMode, jpegSize);
    return JNI_TRUE;
}
