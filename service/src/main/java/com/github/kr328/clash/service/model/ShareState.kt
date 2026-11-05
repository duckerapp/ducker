package com.github.kr328.clash.service.model

import android.os.Parcel
import android.os.Parcelable
import com.github.kr328.clash.core.util.Parcelizer
import kotlinx.serialization.Serializable

/**
 * «Раздача по Wi-Fi» for its card (D-419–D-421). [running] — the VPN is up, so the switch works; [closed] — the
 * subscription's provider closed local ports (D-218). While [on]: the phone's [address] and [port] on the shared
 * network; [own] — the phone's own network (hotspot, USB or Bluetooth tethering), never a password there. [password] is
 * the «Пароль» switch for any other Wi-Fi, remembered; [login] and [secret] are asked while it applies. [stopped] — why
 * it went off by itself, until it is turned on again or the VPN stops.
 */
@Serializable
data class ShareState(
    val running: Boolean = false,
    val closed: Boolean = false,
    val on: Boolean = false,
    val address: String = "",
    val port: Int = 0,
    val own: Boolean = false,
    val password: Boolean = true,
    val login: String = "",
    val secret: String = "",
    val stopped: Stop? = null,
) : Parcelable {
    enum class Stop {
        /** The network it was open on went away or was replaced (D-420). */
        NetworkChanged,

        /** Turned on with neither Wi-Fi nor the phone's own network. */
        NoNetwork,

        /** The port did not open. */
        Failed,
    }

    /** A login and a password are asked now. */
    val locked: Boolean get() = on && !own && password

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        Parcelizer.encodeToParcel(serializer(), parcel, this)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ShareState> {
        override fun createFromParcel(parcel: Parcel): ShareState {
            return Parcelizer.decodeFromParcel(serializer(), parcel)
        }

        override fun newArray(size: Int): Array<ShareState?> {
            return arrayOfNulls(size)
        }
    }
}
