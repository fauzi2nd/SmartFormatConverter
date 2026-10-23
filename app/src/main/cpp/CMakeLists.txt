cmake_minimum_required(VERSION 3.22.1)

project("smartformatconverter_native")

set(ENABLE_STATIC ON CACHE BOOL "" FORCE)
set(ENABLE_SHARED OFF CACHE BOOL "" FORCE)
set(WITH_TURBOJPEG ON CACHE BOOL "" FORCE)
set(WITH_SIMD OFF CACHE BOOL "" FORCE)

set(SAVED_CMAKE_SOURCE_DIR ${CMAKE_SOURCE_DIR})
get_filename_component(LIBJPEG_TURBO_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/libjpeg-turbo" ABSOLUTE)
set(CMAKE_SOURCE_DIR ${LIBJPEG_TURBO_DIR})

add_subdirectory(
    ${LIBJPEG_TURBO_DIR}
    ${CMAKE_CURRENT_BINARY_DIR}/libjpeg-turbo_build
    EXCLUDE_FROM_ALL
)

set(CMAKE_SOURCE_DIR ${SAVED_CMAKE_SOURCE_DIR})

add_library(
        jpeg_encoder_native
        SHARED
        jpeg_encoder.cpp
)

target_include_directories(
        jpeg_encoder_native
        PRIVATE
        ${LIBJPEG_TURBO_DIR}
        ${LIBJPEG_TURBO_DIR}/src
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
