cmake_minimum_required(VERSION 3.22.1)
project("beatrice_native")

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Enable ARM NEON optimizations for 64-bit ARM
if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    add_compile_options(-march=armv8.2-a+fp16 -O3 -ffast-math)
endif()

# Find Google Oboe prefab package
find_package(oboe REQUIRED CONFIG)

# Add native Beatrice library
add_library(
    beatrice_native
    SHARED
    native-audio-jni.cpp
    AudioEngine.cpp
    BeatriceEngine.cpp
)

target_include_directories(
    beatrice_native
    PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

# Link Oboe, Android system log, AAudio, OpenSLES
target_link_libraries(
    beatrice_native
    PRIVATE
    oboe::oboe
    android
    log
    OpenSLES
    aaudio
)
