#ifndef CACTUS_FFI_H
#define CACTUS_FFI_H

#include <stddef.h>

#if __GNUC__ >= 4
  #define CACTUS_FFI_EXPORT __attribute__ ((visibility ("default")))
  #define CACTUS_FFI_LOCAL  __attribute__ ((visibility ("hidden")))
#else
  #define CACTUS_FFI_EXPORT
  #define CACTUS_FFI_LOCAL
#endif

#ifdef __cplusplus
extern "C" {
#endif

typedef void* cactus_model_t;

CACTUS_FFI_EXPORT cactus_model_t cactus_init(const char* model_path, size_t context_size);

CACTUS_FFI_EXPORT int cactus_complete(
    cactus_model_t model,
    const char* messages_json,
    char* response_buffer,
    size_t buffer_size,
    const char* options_json
);

CACTUS_FFI_EXPORT void cactus_destroy(cactus_model_t model);

#ifdef __cplusplus
}
#endif

#endif 