package com.github.kr328.clash.common.compat

import android.app.Application

val Application.currentProcessName: String
    get() = Application.getProcessName()
