#pragma once

#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <malloc.h>
#include <android/log.h>

extern void initialize_jni(JavaVM *vm, JNIEnv *env);
extern jstring jni_new_string(JNIEnv *env, const char *str);
extern char *jni_get_string(JNIEnv *env, jstring str);
extern int jni_catch_exception(JNIEnv *env);
extern JNIEnv *jni_attach_thread();
extern void jni_pop_frame(JNIEnv **env);
extern void release_string(char **str);

// A thread stays attached until it exits (D-324); the local frame popped on return frees what
// the call created, as detaching after every call used to.
#define ATTACH_JNI() __attribute__((unused, cleanup(jni_pop_frame))) \
                    JNIEnv *env = jni_attach_thread()

#define scoped_string __attribute__((cleanup(release_string))) char*

#define find_class(name) (*env)->FindClass(env, name)
#define find_method(cls, name, signature) (*env)->GetMethodID(env, cls, name, signature)
#define new_global(obj) (*env)->NewGlobalRef(env, obj)
#define del_global(obj) (*env)->DeleteGlobalRef(env, obj)
#define get_string(jstr) jni_get_string(env, jstr)
#define new_string(cstr) jni_new_string(env, cstr)