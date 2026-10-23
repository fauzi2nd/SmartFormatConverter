cmake_minimum_required(VERSION 3.22.1)

project("smartformatconverter_native")

set(ENABLE_STATIC ON CACHE BOOL "" FORCE)
set(ENABLE_SHARED OFF CACHE BOOL "" FORCE)
set(WITH_TURBOJPEG ON CACHE BOOL "" FORCE)
set(WITH_SIMD OFF CACHE BOOL "" FORCE)

add_subdirectory(
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/libjpeg-turbo
    ${CMAKE_CURRENT_BINARY_DIR}/libjpeg-turbo_build
    EXCLUDE_FROM_ALL
)

add_library(
        jpeg_encoder_native
        SHARED
        jpeg_encoder.cpp
)

target_include_directories(
        jpeg_encoder_native
        PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/libjpeg-turbo
        ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/libjpeg-turbo/src
        ${CMAKE_CURRENT_BINARY_DIR}/libjpeg-turbo_build
)

find_library(log-lib log)
find_library(jnigraphics-lib jnigraphics)

target_link_libraries(
        jpeg_encoder_native
        turbojpeg-static
        ${log-lib}
        ${jnigraphics-lib}
)
