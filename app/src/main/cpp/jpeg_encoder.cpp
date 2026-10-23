
#include <jni.h>
#include <android/bitmap.h>
#include <android/log.h>

#include <turbojpeg.h>

#include <algorithm>
#include <cerrno>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <limits>
#include <memory>
#include <new>
#include <string>
#include <vector>

#include <fcntl.h>
#include <sys/stat.h>
#include <unistd.h>

#define LOG_TAG "JpegNativeEncoder"

#define LOGI(...) \
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

#define LOGE(...) \
    __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

    constexpr uint32_t MAX_IMAGE_DIMENSION = 32768;

// --------------------------------------------------
// JNI STRING RESOURCE
// --------------------------------------------------

    class UtfString final {
    public:
        UtfString(JNIEnv* env, jstring value)
                : env_(env),
                  value_(value),
                  chars_(nullptr) {
            if (env_ && value_) {
                chars_ = env_->GetStringUTFChars(value_, nullptr);
            }
        }

        ~UtfString() {
            if (chars_ && value_) {
                env_->ReleaseStringUTFChars(value_, chars_);
            }
        }

        UtfString(const UtfString&) = delete;
        UtfString& operator=(const UtfString&) = delete;

        [[nodiscard]] const char* get() const {
            return chars_;
        }

    private:
        JNIEnv* env_;
        jstring value_;
        const char* chars_;
    };

// --------------------------------------------------
// BITMAP LOCK RESOURCE
// --------------------------------------------------

    class LockedBitmap final {
    public:
        LockedBitmap(JNIEnv* env, jobject bitmap)
                : env_(env),
                  bitmap_(bitmap),
                  pixels_(nullptr),
                  locked_(false) {}

        bool lock() {
            if (!env_ || !bitmap_) {
                return false;
            }

            const int result =
                    AndroidBitmap_lockPixels(env_, bitmap_, &pixels_);

            locked_ = result == ANDROID_BITMAP_RESULT_SUCCESS;

            return locked_ && pixels_ != nullptr;
        }

        ~LockedBitmap() {
            if (locked_) {
                AndroidBitmap_unlockPixels(env_, bitmap_);
            }
        }

        LockedBitmap(const LockedBitmap&) = delete;
        LockedBitmap& operator=(const LockedBitmap&) = delete;

        [[nodiscard]] const uint8_t* pixels() const {
            return static_cast<const uint8_t*>(pixels_);
        }

    private:
        JNIEnv* env_;
        jobject bitmap_;
        void* pixels_;
        bool locked_;
    };

// --------------------------------------------------
// TURBO JPEG RESOURCE
// --------------------------------------------------

    class TurboJpegCompressor final {
    public:
        TurboJpegCompressor()
                : handle_(tj3Init(TJINIT_COMPRESS)) {}

        ~TurboJpegCompressor() {
            if (handle_) {
                tj3Destroy(handle_);
            }
        }

        TurboJpegCompressor(const TurboJpegCompressor&) = delete;
        TurboJpegCompressor& operator=(const TurboJpegCompressor&) = delete;

        [[nodiscard]] tjhandle get() const {
            return handle_;
        }

    private:
        tjhandle handle_;
    };

// --------------------------------------------------
// TURBO JPEG OUTPUT RESOURCE
// --------------------------------------------------

    struct TurboJpegBufferDeleter {
        void operator()(unsigned char* ptr) const {
            if (ptr) {
                tj3Free(ptr);
            }
        }
    };

    using TurboJpegBuffer =
            std::unique_ptr<unsigned char, TurboJpegBufferDeleter>;

// --------------------------------------------------
// FILE DESCRIPTOR RESOURCE
// --------------------------------------------------

    class FileDescriptor final {
    public:
        explicit FileDescriptor(int fd = -1)
                : fd_(fd) {}

        ~FileDescriptor() {
            if (fd_ >= 0) {
                ::close(fd_);
            }
        }

        FileDescriptor(const FileDescriptor&) = delete;
        FileDescriptor& operator=(const FileDescriptor&) = delete;

        [[nodiscard]] int get() const {
            return fd_;
        }

        bool closeChecked() {
            if (fd_ < 0) {
                return false;
            }

            const int fd = fd_;
            fd_ = -1;

            return ::close(fd) == 0;
        }

    private:
        int fd_;
    };

// --------------------------------------------------
// INPUT VALIDATION
// --------------------------------------------------

    bool validateBitmapInfo(const AndroidBitmapInfo& info) {
        if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
            LOGE("Unsupported bitmap format: %d", info.format);
            return false;
        }

        if (info.width == 0 || info.height == 0 ||
            info.width > MAX_IMAGE_DIMENSION ||
            info.height > MAX_IMAGE_DIMENSION) {
            LOGE(
                    "Invalid bitmap dimensions: %u x %u",
                    info.width,
                    info.height
            );
            return false;
        }

        const uint64_t minimumStride =
                static_cast<uint64_t>(info.width) * 4ULL;

        if (static_cast<uint64_t>(info.stride) < minimumStride) {
            LOGE(
                    "Invalid bitmap stride: %u",
                    info.stride
            );
            return false;
        }

        const uint64_t totalSourceBytes =
                static_cast<uint64_t>(info.stride) *
                static_cast<uint64_t>(info.height);

        if (totalSourceBytes >
            static_cast<uint64_t>(
                    std::numeric_limits<size_t>::max()
            )) {
            LOGE("Bitmap source size overflow");
            return false;
        }

        const uint64_t totalDestinationBytes =
                static_cast<uint64_t>(info.width) *
                static_cast<uint64_t>(info.height) * 4ULL;

        if (totalDestinationBytes >
            static_cast<uint64_t>(
                    std::numeric_limits<size_t>::max()
            )) {
            LOGE("Bitmap destination size overflow");
            return false;
        }

        return true;
    }

// --------------------------------------------------
// SUBSAMPLING VALIDATION
// --------------------------------------------------

    bool resolveSubsampling(
            jint mode,
            int& turboJpegSubsampling
    ) {
        switch (mode) {
            case 0:
                turboJpegSubsampling = TJSAMP_444;
                return true;

            case 1:
                turboJpegSubsampling = TJSAMP_422;
                return true;

            case 2:
                turboJpegSubsampling = TJSAMP_420;
                return true;

            default:
                LOGE("Unsupported subsampling mode: %d", mode);
                return false;
        }
    }

// --------------------------------------------------
// ALPHA COMPOSITING
// --------------------------------------------------

    uint8_t compositeOverWhite(
            uint8_t color,
            uint8_t alpha
    ) {
        const unsigned int value =
                static_cast<unsigned int>(color) *
                static_cast<unsigned int>(alpha) +
                255U * (255U - static_cast<unsigned int>(alpha));

        return static_cast<uint8_t>((value + 127U) / 255U);
    }

// --------------------------------------------------
// BITMAP TO RGBA BUFFER
// --------------------------------------------------

    bool convertBitmapToRgba(
            const AndroidBitmapInfo& info,
            const uint8_t* source,
            bool compositeWhite,
            std::vector<uint8_t>& destination
    ) {
        if (!source) {
            return false;
        }

        const auto width = static_cast<size_t>(info.width);
        const auto height = static_cast<size_t>(info.height);
        const auto sourceStride = static_cast<size_t>(info.stride);

        const size_t destinationStride = width * 4U;
        const size_t destinationSize =
                destinationStride * height;

        try {
            destination.resize(destinationSize);
        } catch (const std::bad_alloc&) {
            LOGE("Insufficient memory for RGBA buffer");
            return false;
        }

        for (size_t y = 0; y < height; ++y) {
            const uint8_t* sourceRow =
                    source + y * sourceStride;

            uint8_t* destinationRow =
                    destination.data() + y * destinationStride;

            for (size_t x = 0; x < width; ++x) {
                const size_t offset = x * 4U;

                const uint8_t r = sourceRow[offset + 0];
                const uint8_t g = sourceRow[offset + 1];
                const uint8_t b = sourceRow[offset + 2];
                const uint8_t a = sourceRow[offset + 3];

                if (compositeWhite) {
                    destinationRow[offset + 0] =
                            compositeOverWhite(r, a);

                    destinationRow[offset + 1] =
                            compositeOverWhite(g, a);

                    destinationRow[offset + 2] =
                            compositeOverWhite(b, a);
                } else {
                    // JPEG cannot store transparency.
                    // Preserve RGB components when white
                    // compositing is disabled.
                    destinationRow[offset + 0] = r;
                    destinationRow[offset + 1] = g;
                    destinationRow[offset + 2] = b;
                }

                destinationRow[offset + 3] = 255;
            }
        }

        return true;
    }

// --------------------------------------------------
// SAFE TEMPORARY FILE WRITING
// --------------------------------------------------

    bool writeJpegAtomically(
            const std::string& outputPath,
            const unsigned char* data,
            size_t size
    ) {
        if (outputPath.empty() || !data || size == 0) {
            return false;
        }

        // The temporary file is created in the same
        // directory as the destination.
        //
        // mkstemp avoids collisions between concurrent
        // conversion jobs and rejects existing temp names.

        std::string tempTemplate =
                outputPath + ".tmp.XXXXXX";

        std::vector<char> tempPath(
                tempTemplate.begin(),
                tempTemplate.end()
        );

        tempPath.push_back('\0');

        const int rawFd = ::mkstemp(tempPath.data());

        if (rawFd < 0) {
            LOGE(
                    "Failed creating temporary file: %s",
                    std::strerror(errno)
            );
            return false;
        }

        const std::string temporaryPath(tempPath.data());

        FileDescriptor output(rawFd);

        size_t written = 0;
        bool success = true;

        while (written < size) {
            const size_t remaining = size - written;

            const size_t chunk = std::min(
                    remaining,
                    static_cast<size_t>(1024U * 1024U)
            );

            const ssize_t result = ::write(
                    output.get(),
                    data + written,
                    chunk
            );

            if (result < 0 && errno == EINTR) {
                continue;
            }

            if (result <= 0) {
                LOGE(
                        "Failed writing JPEG: %s",
                        std::strerror(errno)
                );
                success = false;
                break;
            }

            written += static_cast<size_t>(result);
        }

        if (success && ::fsync(output.get()) != 0) {
            LOGE(
                    "Failed syncing JPEG file: %s",
                    std::strerror(errno)
            );
            success = false;
        }

        if (!output.closeChecked()) {
            LOGE("Failed closing JPEG temporary file");
            success = false;
        }

        if (!success) {
            ::unlink(temporaryPath.c_str());
            return false;
        }

        // Atomic replacement within the same filesystem.
        // The caller must supply an output path that is
        // safe to replace and is not an original source file.

        if (::rename(
                temporaryPath.c_str(),
                outputPath.c_str()
        ) != 0) {
            LOGE(
                    "Failed committing JPEG output: %s",
                    std::strerror(errno)
            );

            ::unlink(temporaryPath.c_str());
            return false;
        }

        return true;
    }

} // namespace

// ==================================================
// JNI ENTRY POINT
// ==================================================

extern "C"
JNIEXPORT jboolean JNICALL
Java_org_roruku_smartformatconverter_engine_image_JpegNativeEncoder_nativeEncodeJpeg(
        JNIEnv* env,
        jclass,
        jobject bitmap,
        jstring jOutputPath,
        jint quality,
        jint subsamplingMode,
        jboolean preserveTransparencyWhite
) {
    // ----------------------------------------------
    // Validate JNI arguments
    // ----------------------------------------------

    if (!env || !bitmap || !jOutputPath) {
        LOGE("Invalid JNI arguments");
        return JNI_FALSE;
    }

    if (quality < 1 || quality > 100) {
        LOGE("JPEG quality must be between 1 and 100");
        return JNI_FALSE;
    }

    int subsampling = TJSAMP_444;

    if (!resolveSubsampling(
            subsamplingMode,
            subsampling
    )) {
        return JNI_FALSE;
    }

    // ----------------------------------------------
    // Validate bitmap metadata
    // ----------------------------------------------

    AndroidBitmapInfo info{};

    if (AndroidBitmap_getInfo(
            env,
            bitmap,
            &info
    ) != ANDROID_BITMAP_RESULT_SUCCESS) {
        LOGE("Failed reading Android bitmap info");
        return JNI_FALSE;
    }

    if (!validateBitmapInfo(info)) {
        return JNI_FALSE;
    }

    // ----------------------------------------------
    // Validate output path
    // ----------------------------------------------

    UtfString outputPathHolder(env, jOutputPath);

    const char* outputPath = outputPathHolder.get();

    if (!outputPath || outputPath[0] == '\0') {
        LOGE("Invalid output path");
        return JNI_FALSE;
    }

    const std::string destination(outputPath);

    // ----------------------------------------------
    // Lock bitmap
    // ----------------------------------------------

    LockedBitmap lockedBitmap(env, bitmap);

    if (!lockedBitmap.lock()) {
        LOGE("Failed locking Android bitmap");
        return JNI_FALSE;
    }

    // ----------------------------------------------
    // Convert pixels
    // ----------------------------------------------

    std::vector<uint8_t> rgbaBuffer;

    if (!convertBitmapToRgba(
            info,
            lockedBitmap.pixels(),
            preserveTransparencyWhite == JNI_TRUE,
            rgbaBuffer
    )) {
        return JNI_FALSE;
    }

    // ----------------------------------------------
    // Initialize TurboJPEG 3
    // ----------------------------------------------

    TurboJpegCompressor compressor;

    if (!compressor.get()) {
        LOGE("Failed initializing TurboJPEG compressor");
        return JNI_FALSE;
    }

    if (tj3Set(
            compressor.get(),
            TJPARAM_QUALITY,
            static_cast<int>(quality)
    ) != 0) {
        LOGE(
                "Failed setting JPEG quality: %s",
                tj3GetErrorStr(compressor.get())
        );
        return JNI_FALSE;
    }

    if (tj3Set(
            compressor.get(),
            TJPARAM_SUBSAMP,
            subsampling
    ) != 0) {
        LOGE(
                "Failed setting JPEG subsampling: %s",
                tj3GetErrorStr(compressor.get())
        );
        return JNI_FALSE;
    }

    // ----------------------------------------------
    // Compress JPEG
    // ----------------------------------------------

    unsigned char* rawJpegBuffer = nullptr;
    size_t jpegSize = 0;

    const int compressionResult = tj3Compress8(
            compressor.get(),
            rgbaBuffer.data(),
            static_cast<int>(info.width),
            static_cast<int>(info.width * 4U),
            static_cast<int>(info.height),
            TJPF_RGBA,
            &rawJpegBuffer,
            &jpegSize
    );

    TurboJpegBuffer jpegBuffer(rawJpegBuffer);

    if (compressionResult != 0 ||
        !jpegBuffer ||
        jpegSize == 0) {
        LOGE(
                "TurboJPEG compression failed: %s",
                tj3GetErrorStr(compressor.get())
        );
        return JNI_FALSE;
    }

    // ----------------------------------------------
    // Commit output
    // ----------------------------------------------

    if (!writeJpegAtomically(
            destination,
            jpegBuffer.get(),
            jpegSize
    )) {
        LOGE("Failed saving JPEG output");
        return JNI_FALSE;
    }

    LOGI(
            "JPEG encoded: %ux%u, quality=%d, subsampling=%d, bytes=%zu",
            info.width,
            info.height,
            quality,
            subsamplingMode,
            jpegSize
    );

    return JNI_TRUE;
}
