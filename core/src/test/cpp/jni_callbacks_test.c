// Host JNI simulation: run with run_jni_callbacks_test.sh after the Go build.
#include <assert.h>
#include <stdio.h>
#include <stdarg.h>
#include "../../main/cpp/main.c"
#define c_string test_string_class
#include "../../main/cpp/jni_helper.c"
#undef c_string

static int pending, fail_call, fail_new, fail_message, detached, native_thread;
static char token;
#define OBJECT ((jobject) &token)
static jboolean check(JNIEnv *env) { (void)env; return pending; }
static void clear(JNIEnv *env) { (void)env; pending = 0; }
static jthrowable occurred(JNIEnv *env) { (void)env; assert(pending); return (jthrowable)OBJECT; }
static void delete_ref(JNIEnv *env, jobject obj) { (void)env; (void)obj; }
static jbyteArray new_array(JNIEnv *env, jsize len) {
    (void)env; (void)len; assert(!pending); return (jbyteArray)OBJECT;
}
static void set_bytes(JNIEnv *env, jbyteArray a, jsize s, jsize n, const jbyte *bytes) {
    (void)env; (void)a; (void)s; (void)n; (void)bytes; assert(!pending);
}
static jobject new_object(JNIEnv *env, jclass cls, jmethodID method, ...) {
    (void)env; (void)cls; (void)method; assert(!pending);
    if (fail_new) { pending = 1; return NULL; }
    return OBJECT;
}
static jboolean call_bool(JNIEnv *env, jobject obj, jmethodID method, ...) {
    (void)env; (void)obj; (void)method; assert(!pending);
    pending = fail_call; return JNI_TRUE;
}
static jint throw_exception(JNIEnv *env, jthrowable exception) {
    (void)env; (void)exception; assert(!pending); pending = 1; return 0;
}
static jint call_int(JNIEnv *env, jobject obj, jmethodID method, ...) {
    (void)env; (void)obj; (void)method; assert(!pending);
    pending = fail_call; return 1234;
}
static void call_void(JNIEnv *env, jobject obj, jmethodID method, ...) {
    (void)env; (void)obj; (void)method; assert(!pending); pending = fail_call;
}
static jobject call_object(JNIEnv *env, jobject obj, jmethodID method, ...) {
    (void)env; (void)obj; (void)method; assert(!pending);
    if (fail_message) { pending = 1; return NULL; }
    return OBJECT;
}
static jsize array_length(JNIEnv *env, jarray array) { (void)env; (void)array; assert(!pending); return 4; }
static void get_bytes(JNIEnv *env, jbyteArray a, jsize s, jsize n, jbyte *bytes) {
    (void)env; (void)a; (void)s; assert(!pending); assert(n == 4); memcpy(bytes, "oops", 4);
}
static const struct JNINativeInterface_ table = {
    .ExceptionCheck = check, .ExceptionClear = clear, .ExceptionOccurred = occurred,
    .DeleteLocalRef = delete_ref, .NewByteArray = new_array, .SetByteArrayRegion = set_bytes,
    .NewObject = new_object, .CallBooleanMethod = call_bool, .CallIntMethod = call_int,
    .CallVoidMethod = call_void, .CallObjectMethod = call_object, .CallStaticIntMethod = call_int,
    .Throw = throw_exception, .GetArrayLength = array_length, .GetByteArrayRegion = get_bytes,
};
static JNIEnv fake_env = &table;
static jint get_env(JavaVM *vm, void **env, jint version) {
    (void)vm; (void)version; *env = &fake_env; return native_thread ? JNI_EDETACHED : JNI_OK;
}
static jint attach(JavaVM *vm, void **env, void *args) {
    (void)vm; (void)args; *env = &fake_env; return JNI_OK;
}
static jint detach(JavaVM *vm) { (void)vm; assert(!pending); detached++; return JNI_OK; }
static const struct JNIInvokeInterface_ vm_table = {
    .GetEnv = get_env, .AttachCurrentThread = attach, .DetachCurrentThread = detach,
};
static JavaVM fake_vm = &vm_table;
int __android_log_write(int level, const char *tag, const char *text) {
    (void)level; (void)tag; (void)text; return 0;
}

int main(void) {
    global_vm = &fake_vm;
    for (native_thread = 0; native_thread <= 1; native_thread++) {
        fail_call = 0;
        assert(call_tun_interface_mark_socket_impl(OBJECT, 7) == 1);
        assert(call_tun_interface_query_socket_uid_impl(OBJECT, 6, "127.0.0.1:1", "[::1]:2") == 1234);
        assert(open_content_impl("content://test", NULL, 0) == 1234);
        fail_call = 1;
        assert(call_tun_interface_mark_socket_impl(OBJECT, 7) == 0); assert(!pending);
        assert(call_tun_interface_query_socket_uid_impl(OBJECT, 6, "a", "b") == -1); assert(!pending);
        call_completable_complete_impl(OBJECT, NULL); assert(!pending);
        call_completable_complete_impl(OBJECT, "error"); assert(!pending);
        call_fetch_callback_report_impl(OBJECT, "{}"); assert(!pending);
        call_fetch_callback_complete_impl(OBJECT, "error"); assert(!pending);
        call_fetch_callback_complete_impl(OBJECT, NULL); assert(!pending);
        assert(call_logcat_interface_received_impl(OBJECT, "{}") == 1); assert(!pending);
        char error[5] = {0};
        assert(open_content_impl("content://test", error, sizeof(error)) == -1);
        assert(strcmp(error, "oops") == 0); assert(!pending);
        fail_message = 1;
        assert(open_content_impl("content://test", error, sizeof(error)) == -1);
        assert(error[4] == 0); assert(!pending);
        fail_message = 0; fail_new = 1;
        assert(call_tun_interface_query_socket_uid_impl(OBJECT, 6, "a", "b") == -1); assert(!pending);
        call_completable_complete_impl(OBJECT, "error"); assert(!pending);
        call_fetch_callback_report_impl(OBJECT, "{}"); assert(!pending);
        call_fetch_callback_complete_impl(OBJECT, "error"); assert(!pending);
        assert(call_logcat_interface_received_impl(OBJECT, "{}") == 1); assert(!pending);
        assert(open_content_impl("content://test", error, sizeof(error)) == -1); assert(!pending);
        fail_new = 0;
    }
    assert(detached > 0);
    native_thread = 0;
    raise_clash_exception_impl("core panic in queryGroup");
    assert(pending);
    assert(jni_new_string(&fake_env, "fallback") == NULL);
    clear(&fake_env);
    puts("JNI callback simulation PASS (attached Java and Go threads)");
}
