#!/usr/bin/env bash
set -euo pipefail
root=$(cd "$(dirname "$0")/../../.." && pwd)
java_dir=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk}
headers=$(mktemp -d)
trap 'rm -rf "$headers"' EXIT
mkdir -p "$headers/android"
cat > "$headers/android/log.h" <<'HEADER'
#pragma once
#define ANDROID_LOG_ERROR 6
int __android_log_write(int, const char *, const char *);
HEADER
printf '#define GIT_VERSION "host-test"\n' > "$headers/version.h"
cc -std=gnu11 -Wall -Wextra -Werror -Wno-unused-parameter -Wno-unused-function \
    -Wno-incompatible-pointer-types -ffunction-sections -fdata-sections \
    -I"$headers" -I"$java_dir/include" -I"$java_dir/include/linux" \
    -I"$root/src/main/golang/native" -I"$root/build/outputs/golang/metaDebug/arm64-v8a" \
    "$root/src/test/cpp/jni_callbacks_test.c" -Wl,--gc-sections -o "$headers/test"
"$headers/test"
