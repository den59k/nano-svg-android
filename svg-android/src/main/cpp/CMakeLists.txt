cmake_minimum_required(VERSION 3.22.1)
project("svg-android")

# nanosvg implementation (C file that defines NSVG_IMPLEMENTATION)
add_library(svg-android SHARED
    nanosvg_impl.c
    svg_jni.cpp
)

target_include_directories(svg-android PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/../../../../nanosvg/src
)

target_link_libraries(svg-android
    android
    log
    m
)
