package com.github.kr328.clash.common.compat

import android.app.Application
import android.os.Build
import java.io.File

val Application.currentProcessName: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Application.getProcessName()
    } else {
        runCatching { File("/proc/self/cmdline").readText().substringBefore('\u0000') }
            .getOrNull()?.takeIf { it.isNotEmpty() } ?: packageName
    }
