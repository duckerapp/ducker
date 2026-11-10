package com.github.kr328.clash.service.clash

import com.github.kr328.clash.common.log.Log
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.service.clash.module.Module
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.measureTime

private val globalLock = Mutex()

interface ClashRuntimeScope {
    /** How long the core's reset before this start took: part of the connect's timing line (D-454). */
    val resetMs: Long

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
                        val reset = measureTime { Clash.reset() }.inWholeMilliseconds
                        Clash.clearOverride(Clash.OverrideSlot.Session)

                        val scope = object : ClashRuntimeScope {
                            override val resetMs = reset

                            override fun <E, T : Module<E>> install(module: T): T {
                                // Each module runs in a coroutine of its own, on several threads at
                                // once: nothing may be shared here without a guard. A plain list of
                                // the modules, written and never read, crashed the process (D-293).
                                launch {
                                    moduleFailure { module.execute() }?.let {
                                        Log.e("${module.javaClass.simpleName} failed: ${it.message}", it)
                                    }
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

/**
 * Runs a module and hands back its own failure instead of throwing it. A child that fails cancels the runtime, and
 * TunService's scope has no handler: one module's exception took the whole `:background` process down with the
 * tunnel. The module is gone; the rest go on. Cancellation still passes.
 */
internal suspend fun moduleFailure(block: suspend () -> Unit): Exception? =
    try {
        block()

        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        e
    }
