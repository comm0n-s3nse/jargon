// Thin JNI layer over parakeet.cpp's C API. The context is an opaque pointer owned by Kotlin.
#include <jni.h>

#include "ggml_graph.hpp" // pk::set_num_threads, not exposed by the C API
#include "parakeet_capi.h"

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_parakeetcpp_ParakeetLib_load(JNIEnv *env, jobject, jstring path) {
    const char *p = env->GetStringUTFChars(path, nullptr);
    parakeet_ctx *ctx = parakeet_capi_load(p);
    env->ReleaseStringUTFChars(path, p);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_parakeetcpp_ParakeetLib_loadError(JNIEnv *env, jobject) {
    return env->NewStringUTF(parakeet_capi_load_error());
}

JNIEXPORT void JNICALL
Java_com_parakeetcpp_ParakeetLib_free(JNIEnv *, jobject, jlong ctx) {
    parakeet_capi_free(reinterpret_cast<parakeet_ctx *>(ctx));
}

// Returns the transcript, or null on failure (the caller then asks for lastError).
JNIEXPORT jstring JNICALL
Java_com_parakeetcpp_ParakeetLib_transcribe(JNIEnv *env, jobject, jlong ctx_ptr, jint threads,
                                            jfloatArray audio, jstring language) {
    auto *ctx = reinterpret_cast<parakeet_ctx *>(ctx_ptr);
    if (threads > 0) pk::set_num_threads(threads);
    jfloat *samples = env->GetFloatArrayElements(audio, nullptr);
    const jsize n = env->GetArrayLength(audio);
    const char *lang = language ? env->GetStringUTFChars(language, nullptr) : nullptr;
    char *text = parakeet_capi_transcribe_pcm_lang(ctx, samples, n, 16000, 0, lang);
    if (lang) env->ReleaseStringUTFChars(language, lang);
    env->ReleaseFloatArrayElements(audio, samples, JNI_ABORT);
    if (!text) return nullptr;
    jstring result = env->NewStringUTF(text);
    parakeet_capi_free_string(text);
    return result;
}

} // extern "C"
