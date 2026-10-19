# parakeet module (spike)

JNI wrapper that runs [parakeet.cpp](https://github.com/mudler/parakeet.cpp) (MIT) models, such
as Fluid Speech Mini, as a second engine next to whisper.

parakeet.cpp is not vendored here. To build, put it at the repository root:

    git clone https://github.com/mudler/parakeet.cpp parakeet.cpp
    git -C parakeet.cpp checkout 5c9b0fb
    git -C parakeet.cpp submodule update --init --depth 1 third_party/ggml

Things that took finding out (see `src/main/jni/CMakeLists.txt`):

- parakeet.cpp's CMake assumes it is the top-level project, so the wrapper applies its ggml
  patches and adds its bundled-header path itself.
- Gradle's debug build compiles C++ at `-O0`; the wrapper forces Release (40x faster).
- ggml is linked statically and its symbols are hidden, so it cannot clash with the different
  ggml version inside whisper's library. Both engines run in one process.
- Built for armv8.2-a+dotprod. i8mm is faster but crashes older chips, so it needs runtime
  dispatch before it can be enabled.
