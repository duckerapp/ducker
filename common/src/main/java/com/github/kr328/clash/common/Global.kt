package com.github.kr328.clash.common

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import com.github.kr328.clash.common.util.backgroundScope

object Global : CoroutineScope by backgroundScope("Global") {
    val application: Application
        get() = application_

    private lateinit var application_: Application

    fun init(application: Application) {
        this.application_ = application
    }
}
