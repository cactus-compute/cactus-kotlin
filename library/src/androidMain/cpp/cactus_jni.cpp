#include <jni.h>
#include <android/log.h>
#include <string>

#include "cactus_ffi.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "CactusJNI", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "CactusJNI", __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_cactus_CactusLibrary_cactus_1init(JNIEnv *env, jclass clazz, jstring model_path, jint context_size) {
    const char *path = env->GetStringUTFChars(model_path, 0);
    LOGI("Initializing cactus with model: %s, context_size: %d", path, context_size);
    
    cactus_model_t model = cactus_init(path, static_cast<size_t>(context_size));
    
    env->ReleaseStringUTFChars(model_path, path);
    
    return reinterpret_cast<jlong>(model);
}

JNIEXPORT jint JNICALL
Java_com_cactus_CactusLibrary_cactus_1complete(JNIEnv *env, jclass clazz, jlong model,
                                                jstring messages_json, jbyteArray response_buffer,
                                                jint buffer_size, jstring options_json) {
    const char *messages = env->GetStringUTFChars(messages_json, 0);
    const char *options = options_json ? env->GetStringUTFChars(options_json, 0) : nullptr;
    
    jbyte *buffer = env->GetByteArrayElements(response_buffer, 0);
    
    int result = cactus_complete(reinterpret_cast<cactus_model_t>(model), messages,
                                reinterpret_cast<char*>(buffer), buffer_size, options);
    
    env->ReleaseByteArrayElements(response_buffer, buffer, 0);
    env->ReleaseStringUTFChars(messages_json, messages);
    if (options) env->ReleaseStringUTFChars(options_json, options);
    
    return result;
}

JNIEXPORT void JNICALL
Java_com_cactus_CactusLibrary_cactus_1destroy(JNIEnv *env, jclass clazz, jlong model) {
    LOGI("Destroying cactus model");
    
    cactus_destroy(reinterpret_cast<cactus_model_t>(model));
}

}
