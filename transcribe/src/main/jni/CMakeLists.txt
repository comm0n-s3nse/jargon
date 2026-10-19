cmake_minimum_required(VERSION 3.22)
project(transcribe_jni LANGUAGES C CXX)

# Always optimized: Gradle's debug build would compile ggml at -O0, which ran parakeet 40x slower.
set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)

# transcribe.cpp (with its own vendored ggml) sits at the repository root, next to whisper.cpp and
# parakeet.cpp. It guards its own installers and tests on being top-level, but one include path (below)
# still assumes it is, so that one is repaired after it is added.
set(TRANSCRIBE_ROOT ${CMAKE_CURRENT_LIST_DIR}/../../../../transcribe.cpp)

# Static, so its ggml lives inside libtranscribe_jni.so and cannot be confused with the other
# ggml versions inside the whisper and parakeet libraries.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_USE_SYSTEM_BLAS OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_USE_OPENMP OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_METAL OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_VULKAN OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_CUDA OFF CACHE BOOL "" FORCE)
set(TRANSCRIBE_HIP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
# dot-product and half-precision instructions, as for parakeet: on essentially every 64-bit phone
# since 2019. Not i8mm: transcribe.cpp's own notes say it turns its fast kernels off.
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)

add_subdirectory(${TRANSCRIBE_ROOT} transcribe-build)

# Its library target finds its own public header through CMAKE_SOURCE_DIR, which assumes it is the
# top-level project; embedded here that points at this directory. Give it the right path.
target_include_directories(transcribe PUBLIC ${TRANSCRIBE_ROOT}/include)

add_library(transcribe_jni SHARED transcribe_jni.cpp)
target_include_directories(transcribe_jni PRIVATE ${TRANSCRIBE_ROOT}/include)
find_library(LOG_LIB log)
target_link_libraries(transcribe_jni transcribe ${LOG_LIB})
# keep ggml's symbols private to this library
target_link_options(transcribe_jni PRIVATE -Wl,--exclude-libs,ALL)
