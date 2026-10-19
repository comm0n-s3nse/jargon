// Thin JNI layer over transcribe.cpp's C API (the third engine, after whisper.cpp and parakeet.cpp).
//
// One Handle is one loaded model plus a session on it. transcribe.cpp fixes the thread count when a
// session is created, but the keyboard's tuner changes it, so the session is recreated when the count
// changes. That is cheap: the big part, the model, stays loaded.
#include <jni.h>

#include <cstring>
#include <string>

#include "transcribe.h"

namespace {

struct Handle {
    transcribe_model *model = nullptr;
    transcribe_session *session = nullptr;
    int threads = 0;
};

// The reason the last call on this thread failed, for the Kotlin side to read.
thread_local std::string last_error;

bool open_session(Handle *h, int threads) {
    transcribe_session_params sp;
    transcribe_session_params_init(&sp);
    sp.n_threads = threads;
    transcribe_session *s = nullptr;
    const transcribe_status st = transcribe_session_init(h->model, &sp, &s);
    if (st != TRANSCRIBE_OK || !s) {
        last_error = transcribe_status_string(st);
        return false;
    }
    if (h->session) transcribe_session_free(h->session);
    h->session = s;
    h->threads = threads;
    return true;
}

} // namespace

extern "C" {

// Returns 0 on failure; the reason is then available from lastError().
JNIEXPORT jlong JNICALL
Java_com_transcribecpp_TranscribeLib_load(JNIEnv *env, jobject, jstring path, jint threads) {
    const char *p = env->GetStringUTFChars(path, nullptr);
    transcribe_model *model = nullptr;
    const transcribe_status st = transcribe_model_load_file(p, nullptr, &model);
    env->ReleaseStringUTFChars(path, p);
    if (st != TRANSCRIBE_OK || !model) {
        last_error = transcribe_status_string(st);
        return 0;
    }
    auto *h = new Handle();
    h->model = model;
    if (!open_session(h, threads > 0 ? threads : 4)) {
        transcribe_model_free(model);
        delete h;
        return 0;
    }
    return reinterpret_cast<jlong>(h);
}

JNIEXPORT jstring JNICALL
Java_com_transcribecpp_TranscribeLib_lastError(JNIEnv *env, jobject) {
    return env->NewStringUTF(last_error.c_str());
}

// What the model can do, as "translate=1;detect=1;languages=en,de,..." for the Kotlin side to parse.
JNIEXPORT jstring JNICALL
Java_com_transcribecpp_TranscribeLib_capabilities(JNIEnv *env, jobject, jlong ptr) {
    auto *h = reinterpret_cast<Handle *>(ptr);
    transcribe_capabilities caps;
    transcribe_capabilities_init(&caps);
    std::string out;
    if (transcribe_model_get_capabilities(h->model, &caps) == TRANSCRIBE_OK) {
        out = std::string("translate=") + (caps.supports_translate ? "1" : "0") +
              ";detect=" + (caps.supports_language_detect ? "1" : "0") + ";languages=";
        for (int i = 0; i < caps.n_languages; i++) {
            if (i) out += ",";
            out += caps.languages[i];
        }
    }
    return env->NewStringUTF(out.c_str());
}

// Returns the text, or null on failure (then lastError()). language: a code like "pt", or null/"auto"
// to let the model detect it. targetLanguage: when set, the task is translation into that language.
JNIEXPORT jstring JNICALL
Java_com_transcribecpp_TranscribeLib_transcribe(JNIEnv *env, jobject, jlong ptr, jint threads,
                                                jfloatArray audio, jstring language,
                                                jstring targetLanguage) {
    auto *h = reinterpret_cast<Handle *>(ptr);
    if (threads > 0 && threads != h->threads && !open_session(h, threads)) return nullptr;

    const char *lang = language ? env->GetStringUTFChars(language, nullptr) : nullptr;
    const char *target = targetLanguage ? env->GetStringUTFChars(targetLanguage, nullptr) : nullptr;

    transcribe_run_params rp;
    transcribe_run_params_init(&rp);
    rp.language = (lang && *lang && std::strcmp(lang, "auto") != 0) ? lang : nullptr;
    if (target && *target) {
        rp.task = TRANSCRIBE_TASK_TRANSLATE;
        rp.target_language = target;
    }

    jfloat *pcm = env->GetFloatArrayElements(audio, nullptr);
    const jsize n = env->GetArrayLength(audio);
    const transcribe_status st = transcribe_run(h->session, pcm, n, &rp);
    env->ReleaseFloatArrayElements(audio, pcm, JNI_ABORT);
    if (lang) env->ReleaseStringUTFChars(language, lang);
    if (target) env->ReleaseStringUTFChars(targetLanguage, target);

    if (st != TRANSCRIBE_OK) {
        last_error = transcribe_status_string(st);
        return nullptr;
    }
    const char *text = transcribe_full_text(h->session);
    return env->NewStringUTF(text ? text : "");
}

JNIEXPORT void JNICALL
Java_com_transcribecpp_TranscribeLib_free(JNIEnv *, jobject, jlong ptr) {
    auto *h = reinterpret_cast<Handle *>(ptr);
    if (!h) return;
    if (h->session) transcribe_session_free(h->session); // before the model: it only borrows it
    if (h->model) transcribe_model_free(h->model);
    delete h;
}

} // extern "C"
