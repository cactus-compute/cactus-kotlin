#include <jni.h>
#include <android/log.h>
#include <string>

#include "cactus_ffi.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "CactusJNI", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "CactusJNI", __VA_ARGS__)

// External functions from cactus_util.so
extern "C" {
    char* register_app(const char* encrypted_payload);
    char* get_device_id();
    void set_android_data_directory(const char* data_directory);
}

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

// Utility JNI functions for device management
JNIEXPORT jstring JNICALL
Java_com_cactus_utils_DeviceInfo_1androidKt_nativeRegisterApp(JNIEnv *env, jclass clazz, jstring encrypted_payload) {
    const char *payload = env->GetStringUTFChars(encrypted_payload, 0);
    
    LOGI("Calling register_app with payload");
    
    char* result = register_app(payload);
    
    env->ReleaseStringUTFChars(encrypted_payload, payload);
    
    if (result == nullptr) {
        LOGE("register_app returned null");
        return nullptr;
    }
    
    jstring jresult = env->NewStringUTF(result);
    
    return jresult;
}

JNIEXPORT jstring JNICALL
Java_com_cactus_utils_DeviceInfo_1androidKt_nativeGetDeviceId(JNIEnv *env, jclass clazz) {
    LOGI("Calling get_device_id");
    
    char* result = get_device_id();
    
    if (result == nullptr) {
        LOGI("get_device_id returned null");
        return nullptr;
    }
    
    jstring jresult = env->NewStringUTF(result);
    
    return jresult;
}

JNIEXPORT void JNICALL
Java_com_cactus_utils_DeviceInfo_1androidKt_nativeSetAndroidDataDirectory(JNIEnv *env, jclass clazz, jstring data_directory) {
    const char *dir_str = env->GetStringUTFChars(data_directory, 0);
    
    LOGI("Setting Android data directory: %s", dir_str);
    
    set_android_data_directory(dir_str);
    
    env->ReleaseStringUTFChars(data_directory, dir_str);
}

}
