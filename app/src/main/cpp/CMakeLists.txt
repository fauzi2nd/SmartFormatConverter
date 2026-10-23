cmake_minimum_required(VERSION 3.22.1)

project(smartformatconverter_native LANGUAGES C CXX)

# libjpeg-turbo 3.2.1 explicitly does not support add_subdirectory().
# Build the unmodified upstream source as a separate CMake project instead.
include(ExternalProject)

if(NOT ANDROID)
    message(FATAL_ERROR "SmartFormatConverter native encoder requires the Android NDK")
endif()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

get_filename_component(
    LIBJPEG_TURBO_DIR
    "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/libjpeg-turbo"
    REALPATH
)

if(NOT EXISTS "${LIBJPEG_TURBO_DIR}/CMakeLists.txt")
    message(FATAL_ERROR
        "libjpeg-turbo source missing: ${LIBJPEG_TURBO_DIR}. "
        "Run: git submodule update --init --recursive"
    )
endif()

if(NOT CMAKE_TOOLCHAIN_FILE)
    message(FATAL_ERROR "Android NDK CMAKE_TOOLCHAIN_FILE is required")
endif()

set(TURBO_BUILD_DIR "${CMAKE_CURRENT_BINARY_DIR}/libjpeg-turbo_build")
set(TURBO_STATIC_LIB "${TURBO_BUILD_DIR}/libturbojpeg.a")

# The Android Gradle Plugin finds Ninja inside the Android SDK CMake package.
# That executable is not necessarily on the self-hosted runner's PATH.
# Pass its absolute location to the nested external CMake configuration.
if(NOT CMAKE_MAKE_PROGRAM OR NOT EXISTS "${CMAKE_MAKE_PROGRAM}")
    message(FATAL_ERROR "Android CMake Ninja executable not found: ${CMAKE_MAKE_PROGRAM}")
endif()
file(TO_CMAKE_PATH "${CMAKE_MAKE_PROGRAM}" TURBO_BUILD_PROGRAM)

# Do not change CMAKE_SOURCE_DIR, patch third_party sources or rely on
# globally cached options from another CMake project.
ExternalProject_Add(libjpeg_turbo_external
    SOURCE_DIR "${LIBJPEG_TURBO_DIR}"
    BINARY_DIR "${TURBO_BUILD_DIR}"
    DOWNLOAD_COMMAND ""
    UPDATE_COMMAND ""
    CMAKE_ARGS
        "-DCMAKE_MAKE_PROGRAM:FILEPATH=${TURBO_BUILD_PROGRAM}"
        "-DCMAKE_TOOLCHAIN_FILE:FILEPATH=${CMAKE_TOOLCHAIN_FILE}"
        "-DANDROID_ABI:STRING=${ANDROID_ABI}"
        "-DANDROID_PLATFORM:STRING=${ANDROID_PLATFORM}"
        "-DANDROID_STL:STRING=${ANDROID_STL}"
        "-DCMAKE_BUILD_TYPE:STRING=${CMAKE_BUILD_TYPE}"
        "-DCMAKE_POSITION_INDEPENDENT_CODE:BOOL=ON"
        "-DENABLE_STATIC:BOOL=ON"
        "-DENABLE_SHARED:BOOL=OFF"
        "-DWITH_TURBOJPEG:BOOL=ON"
        "-DWITH_SIMD:BOOL=OFF"
        "-DWITH_TOOLS:BOOL=OFF"
        "-DWITH_TESTS:BOOL=OFF"
    BUILD_COMMAND
        "${CMAKE_COMMAND}" --build <BINARY_DIR> --config $<CONFIG> --target turbojpeg-static
    INSTALL_COMMAND ""
    BUILD_BYPRODUCTS "${TURBO_STATIC_LIB}"
)

# The actual static archive is produced by the external CMake build.
add_library(turbojpeg_static STATIC IMPORTED GLOBAL)
set_target_properties(turbojpeg_static PROPERTIES
    IMPORTED_LOCATION "${TURBO_STATIC_LIB}"
)
add_dependencies(turbojpeg_static libjpeg_turbo_external)

add_library(jpeg_encoder_native SHARED jpeg_encoder.cpp)
target_compile_features(jpeg_encoder_native PRIVATE cxx_std_17)

target_include_directories(jpeg_encoder_native PRIVATE
    "${LIBJPEG_TURBO_DIR}"
    "${LIBJPEG_TURBO_DIR}/src"
    "${TURBO_BUILD_DIR}"
)

find_library(ANDROID_LOG_LIBRARY log REQUIRED)
find_library(ANDROID_JNIGRAPHICS_LIBRARY jnigraphics REQUIRED)
find_library(ANDROID_ZLIB_LIBRARY z REQUIRED)

target_link_libraries(jpeg_encoder_native PRIVATE
    turbojpeg_static
    ${ANDROID_LOG_LIBRARY}
    ${ANDROID_JNIGRAPHICS_LIBRARY}
    ${ANDROID_ZLIB_LIBRARY}
)

# ELF LOAD segments must be compatible with 16 KB memory pages.
# This flag applies only to our JNI library, not prebuilt RxFFmpeg .so files.
target_link_options(jpeg_encoder_native PRIVATE
    "-Wl,-z,max-page-size=16384"
)

message(STATUS "libjpeg-turbo: external Android build for ${ANDROID_ABI}")
message(STATUS "TurboJPEG static archive: ${TURBO_STATIC_LIB}")
