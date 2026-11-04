package com.github.kr328.clash.service

import android.app.Service
import com.github.kr328.clash.common.util.backgroundScope
import com.github.kr328.clash.service.util.cancelAndJoinBlocking
import kotlinx.coroutines.CoroutineScope

/**
 * A failed task is logged, not fatal: a profile update that could not open the database on a full disk
 * took down the whole service process, and the VPN with it.
 */
abstract class BaseService(name: String) : Service(), CoroutineScope by backgroundScope(name) {
    override fun onDestroy() {
        super.onDestroy()

        cancelAndJoinBlocking()
    }
}