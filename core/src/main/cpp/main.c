#include <jni.h>
#include <stdint.h>
#include <stddef.h>
#include <string.h>

#include "bridge_helper.h"
#include "libclash.h"
#include "jni_helper.h"
#include "trace.h"

#include "version.h" // 添加当前编译core版本号变量


JNIEXPORT void JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeInit(JNIEnv *env, jobject thiz,
                                                          jstring home,
                                                          jstring version_name, jint sdk_version) {
    TRACE_METHOD();

    scoped_string _home = get_string(home);
    scoped_string _version_name = get_string(version_name);
    char* _git_version = make_String(GIT_VERSION);

    coreInit(_home, _version_name, _git_version, sdk_version);
}

JNIEXPORT void JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeReset(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    reset();
}

JNIEXPORT void JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeForceGc(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    forceGc();
}

JNIEXPORT void JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeSuspend(JNIEnv *env, jobject thiz,
                                                             jboolean suspended) {
    TRACE_METHOD();

    suspend((int) suspended);
}


JNIEXPORT jstring JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeQueryTunnelState(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    scoped_string response = queryTunnelState();

    return new_string(response);
}

JNIEXPORT jlong JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeQueryTrafficNow(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    uint64_t upload = 0l, download = 0l;

    queryNow(&upload, &download);

    return (jlong) (down_scale_traffic(upload) << 32u | down_scale_traffic(download));
}

JNIEXPORT jlong JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeQueryTrafficTotal(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    uint64_t upload = 0l, download = 0l;

    queryTotal(&upload, &download);

    return (jlong) (down_scale_traffic(upload) << 32u | down_scale_traffic(download));
}

JNIEXPORT void JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeNotifyDnsChanged(JNIEnv *env, jobject thiz,
                                                                      jstring dns_list) {
    TRACE_METHOD();

    scoped_string _dns_list = get_string(dns_list);

    notifyDnsChanged(_dns_list);
}

JNIEXPORT void JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeNotifyTimeZoneChanged(JNIEnv *env, jobject thiz,
                                                                           jstring name, jint offset) {
    TRACE_METHOD();

    scoped_string _name = get_string(name);

    notifyTimeZoneChanged(_name, offset);
}

JNIEXPORT void JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeNotifyInstalledAppChanged(JNIEnv *env,
                                                                               jobject thiz,
                                                                               jstring uid_list) {
    TRACE_METHOD();

    scoped_string _uid_list = get_string(uid_list);

    notifyInstalledAppsChanged(_uid_list);
}

JNIEXPORT jint JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeStartTun(JNIEnv *env, jobject thiz,
                                                              jint fd,
                                                              jstring stack,
                                                              jstring congestion,
                                                              jstring gateway,
                                                              jstring portal,
                                                              jstring dns,
                                                              jobject cb) {
    TRACE_METHOD();

    scoped_string _stack = get_string(stack);
    scoped_string _congestion = get_string(congestion);
    scoped_string _gateway = get_string(gateway);
    scoped_string _portal = get_string(portal);
    scoped_string _dns = get_string(dns);
    jobject _interface = new_global(cb);

    return startTun(fd, _stack, _congestion, _gateway, _portal, _dns, _interface);
}

JNIEXPORT void JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeProtectTun(JNIEnv *env, jobject thiz, jobject cb) {
    TRACE_METHOD();

    protectTun(new_global(cb));
}

JNIEXPORT void JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeStopTun(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    stopTun();
}

JNIEXPORT jstring JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeStartHttp(JNIEnv *env, jobject thiz,
                                                               jstring listen_at) {
    TRACE_METHOD();

    scoped_string _listen_at = get_string(listen_at);

    scoped_string listened = startHttp(_listen_at);

    if (listened == NULL)
        return NULL;

    return new_string(listened);
}

JNIEXPORT void JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeStopHttp(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    stopHttp();
}

JNIEXPORT jint JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeStartShare(JNIEnv *env, jobject thiz,
                                                                jstring network, jint port,
                                                                jstring user, jstring pass) {
    TRACE_METHOD();

    scoped_string _network = get_string(network);
    scoped_string _user = get_string(user);
    scoped_string _pass = get_string(pass);

    return startShare(_network, port, _user, _pass);
}

JNIEXPORT void JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeStopShare(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    stopShare();
}

JNIEXPORT jstring JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeQueryConnections(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    scoped_string response = queryConnections();

    return new_string(response);
}

JNIEXPORT void JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeCloseAllConnections(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    closeAllConnections();
}

JNIEXPORT void JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeCloseConnections(JNIEnv *env, jobject thiz,
                                                                     jstring ids) {
    TRACE_METHOD();

    scoped_string _ids = get_string(ids);

    closeConnections(_ids);
}

JNIEXPORT void JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeHealthCheckAuto(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    healthCheckAuto();
}

JNIEXPORT jint JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeSetTunnelMode(JNIEnv *env, jobject thiz,
                                                                   jstring mode) {
    TRACE_METHOD();

    scoped_string _mode = get_string(mode);

    return (jint) setTunnelMode(_mode);
}

JNIEXPORT void JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeResetMux(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    resetMux();
}

JNIEXPORT void JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeProbeMux(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    probeMux();
}

JNIEXPORT jint JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeSetLogLevel(JNIEnv *env, jobject thiz,
                                                                 jstring level) {
    TRACE_METHOD();

    scoped_string _level = get_string(level);

    return (jint) setLogLevel(_level);
}

JNIEXPORT jstring JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeProbeProxy(JNIEnv *env, jobject thiz,
                                                                jstring name, jstring url,
                                                                jint timeout) {
    TRACE_METHOD();

    scoped_string _name = get_string(name);
    scoped_string _url = get_string(url);

    scoped_string response = probeProxy(_name, _url, (int) timeout);

    return new_string(response);
}

JNIEXPORT jstring JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeQueryProfileNetwork(JNIEnv *env, jobject thiz,
                                                                         jstring path) {
    TRACE_METHOD();

    scoped_string _path = get_string(path);

    scoped_string response = queryProfileNetwork(_path);

    return new_string(response);
}

JNIEXPORT jstring JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeQueryProfileGroups(JNIEnv *env, jobject thiz,
                                                                        jstring path) {
    TRACE_METHOD();

    scoped_string _path = get_string(path);

    scoped_string response = queryProfileGroups(_path);

    return new_string(response);
}

JNIEXPORT jint JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeFetchMissingProviders(JNIEnv *env, jobject thiz,
                                                                           jstring path) {
    TRACE_METHOD();

    scoped_string _path = get_string(path);

    return (jint) fetchMissingProviders(_path);
}

JNIEXPORT jstring JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeQueryGroupNames(JNIEnv *env, jobject thiz,
                                                                     jboolean exclude_not_selectable) {
    TRACE_METHOD();

    scoped_string response = queryGroupNames((int) exclude_not_selectable);

    return new_string(response);
}

JNIEXPORT jstring JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeQueryGroup(JNIEnv *env, jobject thiz,
                                                                jstring name, jstring mode) {
    TRACE_METHOD();

    scoped_string _name = get_string(name);
    scoped_string _mode = get_string(mode);

    scoped_string response = queryGroup(_name, _mode);

    if (response == NULL)
        return NULL;

    return new_string(response);
}

JNIEXPORT void JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeHealthCheck(JNIEnv *env, jobject thiz,
                                                                 jobject completable,
                                                                 jstring name) {
    TRACE_METHOD();

    jobject _completable = new_global(completable);
    scoped_string _name = get_string(name);

    healthCheck(_completable, _name);
}

JNIEXPORT void JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeHealthCheckChosen(JNIEnv *env, jobject thiz,
                                                                       jobject completable,
                                                                       jstring name) {
    TRACE_METHOD();

    jobject _completable = new_global(completable);
    scoped_string _name = get_string(name);

    healthCheckChosen(_completable, _name);
}

JNIEXPORT void JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeHealthCheckAll(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    healthCheckAll();
}

JNIEXPORT jboolean JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativePatchSelector(JNIEnv *env, jobject thiz,
                                                                   jstring selector, jstring name) {
    TRACE_METHOD();

    scoped_string _selector = get_string(selector);
    scoped_string _name = get_string(name);

    return (jboolean) patchSelector(_selector, _name);
}

JNIEXPORT void JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeLoad(JNIEnv *env, jobject thiz,
                                                          jobject completable, jstring path) {
    TRACE_METHOD();

    jobject _completable = new_global(completable);
    scoped_string _path = get_string(path);

    load(_completable, _path);
}

JNIEXPORT void JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeFetchAndValid(JNIEnv *env, jobject thiz,
                                                                    jobject callback,
                                                                    jstring path,
                                                                    jstring url, jboolean force) {
    TRACE_METHOD();

    jobject _completable = new_global(callback);
    scoped_string _path = get_string(path);
    scoped_string _url = get_string(url);

    fetchAndValid(_completable, _path, _url, force);
}

JNIEXPORT jstring JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeConvertAndApplyTemplate(JNIEnv *env,
                                                                            jobject thiz,
                                                                            jstring content,
                                                                            jstring template_,
                                                                            jstring direct_host) {
    TRACE_METHOD();

    scoped_string _content = get_string(content);
    scoped_string _template = get_string(template_);
    scoped_string _direct_host = get_string(direct_host);

    scoped_string response = convertAndApplyTemplate(_content, _template, _direct_host);

    return new_string(response);
}

JNIEXPORT jstring JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeConvertWithSources(JNIEnv *env,
                                                                       jobject thiz,
                                                                       jstring source,
                                                                       jstring template_,
                                                                       jstring direct_host) {
    TRACE_METHOD();

    scoped_string _source = get_string(source);
    scoped_string _template = get_string(template_);
    scoped_string _direct_host = get_string(direct_host);

    scoped_string response = convertWithSources(_source, _template, _direct_host);

    return new_string(response);
}

JNIEXPORT void JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeSetSubscriptionHeaders(JNIEnv *env,
                                                                           jobject thiz,
                                                                           jstring headers) {
    TRACE_METHOD();

    if (headers == NULL) {
        setSubscriptionHeaders(NULL);
        return;
    }

    scoped_string _headers = get_string(headers);

    setSubscriptionHeaders(_headers);
}

JNIEXPORT void JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeSetSubscriptionMirrors(JNIEnv *env,
                                                                           jobject thiz,
                                                                           jstring plan) {
    TRACE_METHOD();

    if (plan == NULL) {
        setSubscriptionMirrors(NULL);
        return;
    }

    scoped_string _plan = get_string(plan);

    setSubscriptionMirrors(_plan);
}

JNIEXPORT jstring JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeValidateConfig(JNIEnv *env, jobject thiz,
                                                                    jstring path) {
    TRACE_METHOD();

    scoped_string _path = get_string(path);

    scoped_string response = validateConfig(_path);

    return new_string(response);
}

JNIEXPORT jint JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeSetUserRules(JNIEnv *env, jobject thiz,
                                                                  jstring rules) {
    TRACE_METHOD();

    scoped_string _rules = get_string(rules);

    return (jint) setUserRules(_rules);
}

JNIEXPORT void JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeSetAgeSecretKey(JNIEnv *env, jobject thiz,
                                                                      jstring key) {
    TRACE_METHOD();

    if (key == NULL) {
        setAgeSecretKey(NULL);
        return;
    }

    scoped_string _key = get_string(key);

    setAgeSecretKey(_key);
}

JNIEXPORT jstring JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeGenX25519KeyPair(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    scoped_string response = genX25519KeyPair();

    if (response == NULL)
        return NULL;

    return new_string(response);
}

JNIEXPORT jstring JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeGenHybridKeyPair(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    scoped_string response = genHybridKeyPair();

    if (response == NULL)
        return NULL;

    return new_string(response);
}

JNIEXPORT jboolean JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeVeritySecretKeys(JNIEnv *env, jobject thiz,
                                                                      jstring secret_keys) {
    TRACE_METHOD();

    if (secret_keys == NULL)
        return 0;

    scoped_string _secret_keys = get_string(secret_keys);

    return (jboolean) veritySecretKeys(_secret_keys);
}

JNIEXPORT jstring JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeToPublicKeys(JNIEnv *env, jobject thiz,
                                                                  jstring secret_keys) {
    TRACE_METHOD();

    if (secret_keys == NULL)
        return NULL;

    scoped_string _secret_keys = get_string(secret_keys);
    scoped_string response = toPublicKeys(_secret_keys);

    if (response == NULL)
        return NULL;

    return new_string(response);
}

JNIEXPORT jboolean JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeVerityPublicKeys(JNIEnv *env, jobject thiz,
                                                                      jstring public_keys) {
    TRACE_METHOD();

    if (public_keys == NULL)
        return 0;

    scoped_string _public_keys = get_string(public_keys);

    return (jboolean) verityPublicKeys(_public_keys);
}

JNIEXPORT jstring JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeQueryProviders(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();

    scoped_string response = queryProviders();

    return new_string(response);
}

JNIEXPORT void JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeUpdateProvider(JNIEnv *env, jobject thiz,
                                                                    jobject completable,
                                                                    jstring type,
                                                                    jstring name) {
    TRACE_METHOD();

    jobject _completable = new_global(completable);
    scoped_string _type = get_string(type);
    scoped_string _name = get_string(name);

    updateProvider(_completable, _type, _name);
}

JNIEXPORT jstring JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeReadOverride(JNIEnv *env, jobject thiz,
                                                                  jint slot) {
    TRACE_METHOD();

    scoped_string response = readOverride(slot);

    return new_string(response);
}

JNIEXPORT void JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeWriteOverride(JNIEnv *env, jobject thiz,
                                                                   jint slot,
                                                                   jstring content) {
    TRACE_METHOD();

    scoped_string _content = get_string(content);

    writeOverride(slot, _content);
}

JNIEXPORT void JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeClearOverride(JNIEnv *env, jobject thiz,
                                                                   jint slot) {
    TRACE_METHOD();

    clearOverride(slot);
}

JNIEXPORT void JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeSubscribeLogcat(JNIEnv *env, jobject thiz,
                                                                     jobject callback) {
    TRACE_METHOD();

    jobject _callback = new_global(callback);

    subscribeLogcat(_callback);
}

JNIEXPORT void JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeSubscribeRecorder(JNIEnv *env, jobject thiz,
                                                                       jobject callback) {
    TRACE_METHOD();

    jobject _callback = new_global(callback);

    subscribeRecorder(_callback);
}


static jmethodID m_tun_interface_mark_socket;
static jmethodID m_tun_interface_query_socket_uid;
static jmethodID m_completable_complete;
static jmethodID m_completable_complete_exceptionally;
static jmethodID m_logcat_interface_received;
static jmethodID m_clash_exception;
static jmethodID m_fetch_callback_report;
static jmethodID m_fetch_callback_complete;
static jmethodID m_open;
static jmethodID m_get_message;
static jclass c_clash_exception;
static jclass c_content;
static jobject o_unit;

// All callbacks may run on threads attached by Go. Never leave a Java exception
// pending when returning to Go or detaching that thread.
static int catch_callback_exception(JNIEnv *env, const char *name) {
    if (!jni_catch_exception(env)) return 0;
    __android_log_write(ANDROID_LOG_ERROR, TAG, name);
    return 1;
}

static int call_tun_interface_mark_socket_impl(void *tun_interface, int fd) {
    TRACE_METHOD();
    ATTACH_JNI();
    jboolean protected = (*env)->CallBooleanMethod(env, (jobject) tun_interface,
                                                  m_tun_interface_mark_socket, (jint) fd);
    return catch_callback_exception(env, "JNI markSocket failed") ? 0 : protected;
}

static int call_tun_interface_query_socket_uid_impl(void *tun_interface, int protocol,
                                                    const char *source, const char *target) {
    TRACE_METHOD();
    ATTACH_JNI();
    jstring _source = new_string(source);
    if (catch_callback_exception(env, "JNI source allocation failed")) return -1;
    jstring _target = new_string(target);
    if (catch_callback_exception(env, "JNI target allocation failed")) {
        (*env)->DeleteLocalRef(env, _source);
        return -1;
    }
    jint uid = (*env)->CallIntMethod(env, (jobject) tun_interface,
                                    m_tun_interface_query_socket_uid, (jint) protocol,
                                    _source, _target);
    int failed = catch_callback_exception(env, "JNI querySocketUid failed");
    (*env)->DeleteLocalRef(env, _source);
    (*env)->DeleteLocalRef(env, _target);
    return failed ? -1 : uid;
}

static void call_completable_complete_impl(void *completable, const char *exception) {
    TRACE_METHOD();
    ATTACH_JNI();
    if (exception == NULL) {
        (*env)->CallBooleanMethod(env, (jobject) completable, m_completable_complete, o_unit);
    } else {
        jstring message = new_string(exception);
        if (catch_callback_exception(env, "JNI completion message allocation failed")) return;
        jobject failure = (*env)->NewObject(env, c_clash_exception, m_clash_exception, message);
        (*env)->DeleteLocalRef(env, message);
        if (catch_callback_exception(env, "JNI exception allocation failed")) return;
        (*env)->CallBooleanMethod(env, (jobject) completable,
                                  m_completable_complete_exceptionally, failure);
        (*env)->DeleteLocalRef(env, failure);
    }
    catch_callback_exception(env, "JNI completion failed");
}

static void call_fetch_callback_report_impl(void *fetch_callback, const char *status_json) {
    TRACE_METHOD();
    ATTACH_JNI();
    jstring status = new_string(status_json);
    if (catch_callback_exception(env, "JNI fetch status allocation failed")) return;
    (*env)->CallVoidMethod(env, (jobject) fetch_callback, m_fetch_callback_report, status);
    catch_callback_exception(env, "JNI fetch.report failed");
    (*env)->DeleteLocalRef(env, status);
}

static void call_fetch_callback_complete_impl(void *fetch_callback, const char *error) {
    TRACE_METHOD();
    ATTACH_JNI();
    jstring message = error == NULL ? NULL : new_string(error);
    if (catch_callback_exception(env, "JNI fetch error allocation failed")) return;
    (*env)->CallVoidMethod(env, (jobject) fetch_callback, m_fetch_callback_complete, message);
    catch_callback_exception(env, "JNI fetch.complete failed");
    if (message != NULL) (*env)->DeleteLocalRef(env, message);
}

static int call_logcat_interface_received_impl(void *callback, const char *payload) {
    TRACE_METHOD();
    ATTACH_JNI();
    jstring message = new_string(payload);
    if (catch_callback_exception(env, "JNI log message allocation failed")) return 1;
    (*env)->CallVoidMethod(env, (jobject) callback, m_logcat_interface_received, message);
    // CancellationException is the normal unsubscribe signal; no stack dump.
    int failed = jni_catch_exception(env);
    (*env)->DeleteLocalRef(env, message);
    return failed;
}

static void content_error(char *error, int length, const char *message) {
    if (length <= 0) return;
    strncpy(error, message, length - 1);
    error[length - 1] = 0;
}

static int open_content_impl(const char *url, char *error, int error_length) {
    TRACE_METHOD();
    ATTACH_JNI();
    jstring address = new_string(url);
    if (catch_callback_exception(env, "JNI content URL allocation failed")) {
        content_error(error, error_length, "content URL allocation failed");
        return -1;
    }
    int fd = (*env)->CallStaticIntMethod(env, c_content, m_open, address);
    (*env)->DeleteLocalRef(env, address);
    if (!(*env)->ExceptionCheck(env)) return fd;

    jthrowable exception = (*env)->ExceptionOccurred(env);
    (*env)->ExceptionClear(env);
    jstring message = (jstring) (*env)->CallObjectMethod(env, exception, m_get_message);
    (*env)->DeleteLocalRef(env, exception);
    if (catch_callback_exception(env, "JNI exception.getMessage failed") || message == NULL) {
        content_error(error, error_length, "content open failed");
        return -1;
    }
    scoped_string text = get_string(message);
    (*env)->DeleteLocalRef(env, message);
    if (catch_callback_exception(env, "JNI exception message conversion failed") || text == NULL) {
        content_error(error, error_length, "content open failed");
    } else {
        content_error(error, error_length, text);
    }
    return -1;
}

static void release_jni_object_impl(void *obj) {
    TRACE_METHOD();

    ATTACH_JNI();

    del_global((jobject) obj);
}

// Called only by synchronous exports on the original Java caller's thread.
static void raise_clash_exception_impl(const char *message) {
    ATTACH_JNI();
    jstring text = new_string(message);
    if ((*env)->ExceptionCheck(env)) return;
    jthrowable exception = (jthrowable) (*env)->NewObject(env, c_clash_exception, m_clash_exception, text);
    (*env)->DeleteLocalRef(env, text);
    if ((*env)->ExceptionCheck(env)) return;
    (*env)->Throw(env, exception);
    (*env)->DeleteLocalRef(env, exception);
}

JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *reserved) {
    TRACE_METHOD();

    JNIEnv *env = NULL;

    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK)
        return JNI_ERR;

    initialize_jni(vm, env);

    jclass c_tun_interface = find_class("com/github/kr328/clash/core/bridge/TunInterface");
    jclass c_completable = find_class("kotlinx/coroutines/CompletableDeferred");
    jclass c_fetch_callback = find_class("com/github/kr328/clash/core/bridge/FetchCallback");
    jclass c_logcat_interface = find_class("com/github/kr328/clash/core/bridge/LogcatInterface");
    jclass _c_clash_exception = find_class("com/github/kr328/clash/core/bridge/ClashException");
    jclass _c_content = find_class("com/github/kr328/clash/core/bridge/Content");
    jclass c_throwable = find_class("java/lang/Throwable");
    jclass c_unit = find_class("kotlin/Unit");

    m_tun_interface_mark_socket = find_method(c_tun_interface, "markSocket",
                                              "(I)Z");
    m_tun_interface_query_socket_uid = find_method(c_tun_interface, "querySocketUid",
                                                   "(ILjava/lang/String;Ljava/lang/String;)I");
    m_completable_complete = find_method(c_completable, "complete",
                                         "(Ljava/lang/Object;)Z");
    m_fetch_callback_report = find_method(c_fetch_callback, "report",
                                          "(Ljava/lang/String;)V");
    m_fetch_callback_complete = find_method(c_fetch_callback, "complete",
                                            "(Ljava/lang/String;)V");
    m_completable_complete_exceptionally = find_method(c_completable, "completeExceptionally",
                                                       "(Ljava/lang/Throwable;)Z");
    m_logcat_interface_received = find_method(c_logcat_interface, "received",
                                              "(Ljava/lang/String;)V");
    m_clash_exception = find_method(_c_clash_exception, "<init>",
                                    "(Ljava/lang/String;)V");
    m_get_message = find_method(c_throwable, "getMessage",
                                "()Ljava/lang/String;");
    m_open = (*env)->GetStaticMethodID(env, _c_content, "open",
                                       "(Ljava/lang/String;)I");

    o_unit = (*env)->GetStaticObjectField(env, c_unit,
                                          (*env)->GetStaticFieldID(env, c_unit, "INSTANCE",
                                                                   "Lkotlin/Unit;"));

    c_clash_exception = (jclass) new_global(_c_clash_exception);
    c_content = (jclass) new_global(_c_content);
    o_unit = new_global(o_unit);

    mark_socket_func = &call_tun_interface_mark_socket_impl;
    query_socket_uid_func = &call_tun_interface_query_socket_uid_impl;
    complete_func = &call_completable_complete_impl;
    fetch_report_func = &call_fetch_callback_report_impl;
    fetch_complete_func = &call_fetch_callback_complete_impl;
    logcat_received_func = &call_logcat_interface_received_impl;
    open_content_func = &open_content_impl;
    release_object_func = &release_jni_object_impl;
    raise_exception_func = &raise_clash_exception_impl;

    return JNI_VERSION_1_6;
}

JNIEXPORT jstring JNICALL
Java_com_github_kr328_clash_core_bridge_Bridge_nativeCoreVersion(JNIEnv *env, jobject thiz) {
    TRACE_METHOD();
    
    char* Version = make_String(GIT_VERSION);

    return new_string(Version);
}
