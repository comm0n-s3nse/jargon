cmake_minimum_required(VERSION 3.22)
project(parakeet_jni LANGUAGES C CXX)

# Gradle's debug build would compile all of ggml at -O0, which ran this 40x slower than
# optimized. Always build it optimized, whatever the app's build type.
set(CMAKE_BUILD_TYPE Release CACHE STRING "" FORCE)

# parakeet.cpp (with its own ggml) sits at the repository root, next to whisper.cpp.
set(PARAKEET_ROOT ${CMAKE_CURRENT_LIST_DIR}/../../../../parakeet.cpp)

# Static everything, so ggml lives inside libparakeet_jni.so and cannot be confused with the
# different ggml version inside whisper's library.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(PARAKEET_BUILD_CLI OFF CACHE BOOL "" FORCE)
set(PARAKEET_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(PARAKEET_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(PARAKEET_WITH_CED OFF CACHE BOOL "" FORCE)
set(PARAKEET_WITH_VOICEDETECT OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
# dot-product instructions are on essentially every 64-bit phone since 2019; i8mm is faster
# but would crash older chips, so it needs runtime dispatch before it can be turned on
set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)

# parakeet.cpp's CMakeLists assumes it is the top-level project and finds its own files through
# CMAKE_SOURCE_DIR, which is this directory when it is embedded. So do those three things here:
# apply its patches to ggml (idempotent), and make its bundled headers (dr_wav.h) findable.
find_program(BASH_EXECUTABLE bash)
if(BASH_EXECUTABLE AND EXISTS "${PARAKEET_ROOT}/scripts/apply_ggml_patches.sh")
  execute_process(
    COMMAND "${BASH_EXECUTABLE}" "${PARAKEET_ROOT}/scripts/apply_ggml_patches.sh"
    WORKING_DIRECTORY "${PARAKEET_ROOT}"
    RESULT_VARIABLE _patch_result
    ERROR_VARIABLE _patch_errors
  )
  if(_patch_result)
    message(WARNING "apply_ggml_patches.sh returned ${_patch_result}: ${_patch_errors}")
  endif()
endif()
include_directories(${PARAKEET_ROOT}/third_party)

add_subdirectory(${PARAKEET_ROOT} parakeet-build)

add_library(parakeet_jni SHARED parakeet_jni.cpp)
target_include_directories(parakeet_jni PRIVATE ${PARAKEET_ROOT}/include ${PARAKEET_ROOT}/src)
find_library(LOG_LIB log)
target_link_libraries(parakeet_jni parakeet ${LOG_LIB})
# keep ggml's symbols private to this library
target_link_options(parakeet_jni PRIVATE -Wl,--exclude-libs,ALL)
