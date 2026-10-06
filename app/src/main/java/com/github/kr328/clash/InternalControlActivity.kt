package com.github.kr328.clash

import android.os.Bundle

/** Android enforces exported=false; no Intent field grants internal access. */
class InternalControlActivity : ControlActivity() {
    override val commandsAllowed: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        control()
    }
}
