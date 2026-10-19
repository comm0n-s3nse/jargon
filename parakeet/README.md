# parakeet module

JNI wrapper that runs [parakeet.cpp](https://github.com/mudler/parakeet.cpp) (MIT) models, such
as Fluid Speech Mini, as a second engine next to whisper.

parakeet.cpp is a git submodule at the repository root, pinned to commit `5c9b0fb`, and it
brings its own ggml:

    git submodule update --init parakeet.cpp
    git -C parakeet.cpp submodule update --init --depth 1 third_party/ggml

Things that took finding out (see `src/main/jni/CMakeLists.txt`):

- parakeet.cpp's CMake assumes it is the top-level project, so the wrapper applies its ggml
  patches and adds its bundled-header path itself.
- Gradle's debug build compiles C++ at `-O0`; the wrapper forces Release (about 40x faster).
- ggml is linked statically and its symbols are hidden, so it cannot clash with the different
  ggml version inside whisper's library. Both engines run in one process.
- Built for armv8.2-a+dotprod+fp16, which phones have had since about 2019. The keyboard checks
  `/proc/cpuinfo` and does not offer parakeet models on a phone without those instructions,
  since running them would crash. An i8mm build was about 6% faster on 7.5 s of speech and no
  faster on 22 s, so it is not worth a second build and runtime dispatch.
