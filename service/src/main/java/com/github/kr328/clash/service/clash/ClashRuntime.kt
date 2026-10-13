package com.github.kr328.clash.service.clash

import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.service.clash.module.Module
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val globalLock = Mutex()

interface ClashRuntimeScope {
    fun <E, T : Module<E>> install(module: T): T
}

interface ClashRuntime {
    fun launch()
    fun requestGc()
}

fun CoroutineScope.clashRuntime(block: suspend ClashRuntimeScope.() -> Unit): ClashRuntime {
    return object : ClashRuntime {
        override fun launch() {
            launch(Dispatchers.IO) {
                globalLock.withLock {
                    Log.d("ClashRuntime: initialize")

                    try {
                        Clash.reset()
                        Clash.clearOverride(Clash.OverrideSlot.Session)

                        val scope = object : ClashRuntimeScope {
                            override fun <E, T : Module<E>> install(module: T): T {
                                // Each module runs in a coroutine of its own, on several threads at
                                // once: nothing may be shared here without a guard. A plain list of
                                // the modules, written and never read, crashed the process (D-293).
                                launch {
                                    module.execute()
                                }

                                return module
                            }
                        }

                        scope.block()

                        cancel()
                    } finally {
                        withContext(NonCancellable) {
                            Clash.reset()
                            Clash.clearOverride(Clash.OverrideSlot.Session)

                            Log.d("ClashRuntime: destroyed")
                        }
                    }
                }
            }
        }

        override fun requestGc() {
            Clash.forceGc()
        }
    }
}