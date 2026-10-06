#include "jni_helper.h"

#include <malloc.h>
#include <string.h>

static JavaVM *global_vm;

static jclass c_string;
static jmethodID m_new_string;
static jmethodID m_get_bytes;

void initialize_jni(JavaVM *vm, JNIEnv *env) {
    global_vm = vm;

    c_string = (jclass) new_global(find_class("java/lang/String"));
    m_new_string = find_method(c_string, "<init>", "([B)V");
    m_get_bytes = find_method(c_string, "getBytes", "()[B");
}

JavaVM *global_java_vm() {
    return global_vm;
}

char *jni_get_string(JNIEnv *env, jstring str) {
    if (str == NULL) return NULL;
    jbyteArray array = (*env)->CallObjectMethod(env, str, m_get_bytes);
    if ((*env)->ExceptionCheck(env) || array == NULL) return NULL;
    int length = (*env)->GetArrayLength(env, array);
    char *content = (char *) malloc(length + 1);
    if (content == NULL) {
        (*env)->DeleteLocalRef(env, array);
        return NULL;
    }
    (*env)->GetByteArrayRegion(env, array, 0, length, (jbyte *) content);
    (*env)->DeleteLocalRef(env, array);
    if ((*env)->ExceptionCheck(env)) {
        free(content);
        return NULL;
    }
    content[length] = 0;
    return content;
}

jstring jni_new_string(JNIEnv *env, const char *str) {
    // A recovered synchronous Go panic has already raised ClashException.
    if (str == NULL || (*env)->ExceptionCheck(env)) return NULL;
    int length = strlen(str);
    jbyteArray array = (*env)->NewByteArray(env, length);
    if ((*env)->ExceptionCheck(env) || array == NULL) return NULL;
    (*env)->SetByteArrayRegion(env, array, 0, length, (const jbyte *) str);
    if ((*env)->ExceptionCheck(env)) {
        (*env)->DeleteLocalRef(env, array);
        return NULL;
    }
    jstring result = (jstring) (*env)->NewObject(env, c_string, m_new_string, array);
    (*env)->DeleteLocalRef(env, array);
    return result;
}

int jni_catch_exception(JNIEnv *env) {
    int result = (*env)->ExceptionCheck(env);

    if (result) {
        (*env)->ExceptionClear(env);
    }

    return result;
}

void jni_attach_thread(struct _scoped_jni *jni) {
    JavaVM *vm = global_java_vm();

    if ((*vm)->GetEnv(vm, (void **) &jni->env, JNI_VERSION_1_6) == JNI_OK) {
        jni->require_release = 0;
        return;
    }

    if ((*vm)->AttachCurrentThread(vm, &jni->env, NULL) != JNI_OK) {
        abort();
    }

    jni->require_release = 1;
}

void jni_detach_thread(struct _scoped_jni *jni) {
    JavaVM *vm = global_java_vm();

    if (jni->require_release) {
        (*vm)->DetachCurrentThread(vm);
    }
}

void release_string(char **str) {
    free(*str);
}
