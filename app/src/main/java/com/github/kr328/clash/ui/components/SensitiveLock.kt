package com.github.kr328.clash.ui.components

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import android.view.Window
import android.view.WindowManager
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import com.github.kr328.clash.R
import java.util.WeakHashMap

/**
 * The lock on sensitive actions (stage 15): the system's own fingerprint, face or screen-lock dialog before what shows or
 * gives away the subscription link — «Изменить», the QR code, «Отправить на телевизор» — and before the config editor.
 * Not the whole app: widgets and the tile bypass it anyway.
 *
 * One unlock holds while the app stays on screen; [relock] when it leaves. A phone without a screen lock has nothing to
 * check against, and the action goes ahead: the setting says so.
 */
class SensitiveLock(private val activity: Activity) {
    private var unlocked = false
    private var asking = false

    /** [action] at once when the lock is off or already passed, else after the system dialog says yes. */
    fun guard(on: Boolean, @StringRes reason: Int, action: () -> Unit) {
        if (!on || unlocked || !available(activity)) return action()
        if (asking) return

        val builder = BiometricPrompt.Builder(activity)
            .setTitle(activity.getString(R.string.lock_prompt_title))
            .setSubtitle(activity.getString(reason))

        // Weak biometrics as well: face unlock is that class on most phones, and nothing here is encrypted with the key.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }

        asking = true

        builder.build().authenticate(CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                asking = false
                unlocked = true
                action()
            }

            // Cancelled, or too many tries: nothing opens. A wrong finger alone is not an end — the dialog stays.
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                asking = false
            }
        })
    }

    /**
     * The app left the screen: the next sensitive action asks again. Not while the dialog is up — on Android 10 the
     * screen-lock check is an activity of its own and stops ours.
     */
    fun relock() {
        if (!asking) unlocked = false
    }

    companion object {
        /** A screen lock is set: the system dialog has something to check. */
        fun available(context: Context): Boolean =
            context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
    }
}

/**
 * No screenshots, screen recording or preview in recents while this is composed with [on] (stage 15's lock): FLAG_SECURE
 * on the window it is drawn in — a sheet is a dialog window of its own — and on the activity's, which recents shows.
 */
@Composable
internal fun SecureWindow(on: Boolean) {
    if (!on) return

    val view = LocalView.current

    DisposableEffect(view) {
        val windows = listOfNotNull((view.parent as? DialogWindowProvider)?.window, view.context.activity()?.window).distinct()

        windows.forEach(::hold)
        onDispose { windows.forEach(::release) }
    }
}

// Two secure screens can overlap for a moment — «Изменить» closing as the editor opens: the flag goes once both have.
private val holds = WeakHashMap<Window, Int>()

private fun hold(window: Window) {
    val count = holds[window] ?: 0

    holds[window] = count + 1
    if (count == 0) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
}

private fun release(window: Window) {
    val count = (holds[window] ?: return) - 1

    if (count > 0) {
        holds[window] = count
    } else {
        holds.remove(window)
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
