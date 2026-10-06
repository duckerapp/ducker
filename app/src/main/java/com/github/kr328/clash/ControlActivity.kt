package com.github.kr328.clash

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.github.kr328.clash.common.constants.Intents
import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.remote.StatusClient
import com.github.kr328.clash.util.startClashService
import com.github.kr328.clash.util.stopClashService

/** Shared command behavior; the concrete component supplies the access policy. */
abstract class ControlActivity : Activity() {
    protected abstract val commandsAllowed: Boolean

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    protected fun control() {
        val action = intent.action
        if (action !in setOf(Intents.ACTION_START_CLASH, Intents.ACTION_STOP_CLASH, Intents.ACTION_TOGGLE_CLASH)) {
            return finish()
        }
        // Before querying status, requesting VPN consent, or delivering any command.
        if (!commandsAllowed) {
            toast(R.string.external_control_denied)
            return finish()
        }
        when (action) {
            Intents.ACTION_TOGGLE_CLASH -> if (running()) stopClash() else startClash()
            Intents.ACTION_START_CLASH -> if (running()) toast(R.string.external_started) else startClash()
            Intents.ACTION_STOP_CLASH -> stopClash()
        }
        finish()
    }

    /** A running service may still be loading its profile. */
    private fun running(): Boolean = StatusClient(this).status().first

    private fun startClash() {
        if (startClashService() != null) {
            toast(R.string.external_vpn_denied)
            startActivity(HomeActivity::class.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        toast(R.string.external_started)
    }

    private fun stopClash() {
        stopClashService()
        toast(R.string.external_stopped)
    }

    protected fun toast(id: Int) = Toast.makeText(this, id, Toast.LENGTH_LONG).show()

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
