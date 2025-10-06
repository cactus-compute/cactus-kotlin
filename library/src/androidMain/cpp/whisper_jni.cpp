#include <jni.h>
#include <android/log.h>
#include <string>
#include <cstring>
#include "whisper.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "WhisperJNI", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "WhisperJNI", __VA_ARGS__)

extern "C" {

// JNI functions for top-level external functions in WhisperService_androidKt
JNIEXPORT jlong JNICALL
Java_com_cactus_WhisperService_1androidKt_whisperInitFromFile(
        JNIEnv* env, jclass clazz, jstring model_path) {
    const char* path = env->GetStringUTFChars(model_path, 0);
    LOGI("Initializing whisper from file: %s", path);

    struct whisper_context* ctx = whisper_init_from_file(path);

    env->ReleaseStringUTFChars(model_path, path);

    if (ctx == nullptr) {
        LOGE("Failed to initialize whisper context");
        return 0;
    }

    LOGI("Whisper context initialized successfully");
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_cactus_WhisperService_1androidKt_whisperFree(
        JNIEnv* env, jclass clazz, jlong ctx) {
    LOGI("Freeing whisper context");
    
    struct whisper_context* context = reinterpret_cast<struct whisper_context*>(ctx);
    if (context != nullptr) {
        whisper_free(context);
    }
}

JNIEXPORT jlong JNICALL
Java_com_cactus_WhisperService_1androidKt_whisperFullDefaultParams(
        JNIEnv* env, jclass clazz, jint strategy) {
    LOGI("Getting default whisper params with strategy: %d", strategy);
    
    struct whisper_full_params* params = whisper_full_default_params_by_ref(
        static_cast<whisper_sampling_strategy>(strategy));
    
    if (params == nullptr) {
        LOGE("Failed to get default params");
        return 0;
    }
    
    return reinterpret_cast<jlong>(params);
}

JNIEXPORT void JNICALL
Java_com_cactus_WhisperService_1androidKt_whisperFreeParams(
        JNIEnv* env, jclass clazz, jlong params) {
    LOGI("Freeing whisper params");
    
    struct whisper_full_params* p = reinterpret_cast<struct whisper_full_params*>(params);
    if (p != nullptr) {
        whisper_free_params(p);
    }
}

JNIEXPORT jint JNICALL
Java_com_cactus_WhisperService_1androidKt_whisperFull(
        JNIEnv* env, jclass clazz, jlong ctx, jlong params, jfloatArray samples, jint n_samples) {
    struct whisper_context* context = reinterpret_cast<struct whisper_context*>(ctx);
    struct whisper_full_params* p = reinterpret_cast<struct whisper_full_params*>(params);
    
    if (context == nullptr || p == nullptr) {
        LOGE("Invalid context or params");
        return -1;
    }
    
    // Get the float array from Java
    jfloat* sample_data = env->GetFloatArrayElements(samples, 0);
    if (sample_data == nullptr) {
        LOGE("Failed to get float array elements");
        return -1;
    }
    
    LOGI("Processing %d audio samples", n_samples);
    
    // Call whisper_full - note: passing struct by value
    int result = whisper_full(context, *p, sample_data, n_samples);
    
    // Release the float array
    env->ReleaseFloatArrayElements(samples, sample_data, JNI_ABORT);
    
    if (result != 0) {
        LOGE("whisper_full failed with code: %d", result);
    } else {
        LOGI("whisper_full completed successfully");
    }
    
    return result;
}

JNIEXPORT jint JNICALL
Java_com_cactus_WhisperService_1androidKt_whisperFullNSegments(
        JNIEnv* env, jclass clazz, jlong ctx) {
    struct whisper_context* context = reinterpret_cast<struct whisper_context*>(ctx);
    
    if (context == nullptr) {
        LOGE("Invalid context");
        return 0;
    }
    
    int n_segments = whisper_full_n_segments(context);
    LOGI("Number of segments: %d", n_segments);
    
    return n_segments;
}

JNIEXPORT jstring JNICALL
Java_com_cactus_WhisperService_1androidKt_whisperFullGetSegmentText(
        JNIEnv* env, jclass clazz, jlong ctx, jint i_segment) {
    struct whisper_context* context = reinterpret_cast<struct whisper_context*>(ctx);
    
    if (context == nullptr) {
        LOGE("Invalid context");
        return nullptr;
    }
    
    const char* text = whisper_full_get_segment_text(context, i_segment);
    
    if (text == nullptr) {
        LOGE("Failed to get segment text for segment %d", i_segment);
        return nullptr;
    }
    
    return env->NewStringUTF(text);
}

JNIEXPORT jlong JNICALL
Java_com_cactus_WhisperService_1androidKt_whisperFullGetSegmentT0(
        JNIEnv* env, jclass clazz, jlong ctx, jint i_segment) {
    struct whisper_context* context = reinterpret_cast<struct whisper_context*>(ctx);
    
    if (context == nullptr) {
        LOGE("Invalid context");
        return 0;
    }
    
    return whisper_full_get_segment_t0(context, i_segment);
}

JNIEXPORT jlong JNICALL
Java_com_cactus_WhisperService_1androidKt_whisperFullGetSegmentT1(
        JNIEnv* env, jclass clazz, jlong ctx, jint i_segment) {
    struct whisper_context* context = reinterpret_cast<struct whisper_context*>(ctx);
    
    if (context == nullptr) {
        LOGE("Invalid context");
        return 0;
    }
    
    return whisper_full_get_segment_t1(context, i_segment);
}

}
