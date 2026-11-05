package com.github.kr328.clash.service.model

import android.os.Parcel
import android.os.Parcelable
import com.github.kr328.clash.core.util.Parcelizer
import kotlinx.serialization.Serializable

/**
 * «Мобильная сеть» for its page (D-423, D-424): [on] — «Переключение по сети»; [profile] — the subscription for the
 * mobile network, empty when not chosen; [nodes] — members of its main group to try, in order. [now] — the network
 * the phone is on. [wifi] — the subscription Wi-Fi brings back: the one saved when the mobile choice came, else the
 * active one; empty when there is neither.
 */
@Serializable
data class MobileState(
    val on: Boolean = false,
    val profile: String = "",
    val nodes: List<String> = emptyList(),
    val now: NetworkKind = NetworkKind.None,
    val wifi: String = "",
) : Parcelable {
    /** What Home and the VPN notification say about it. */
    enum class Shown {
        None,

        /** The mobile choice is in effect: «VPN работает · мобильная сеть». */
        InEffect,

        /** In effect, and none of the list's nodes answered when it came: the first one stays. */
        NoneAnswered,
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        Parcelizer.encodeToParcel(serializer(), parcel, this)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<MobileState> {
        override fun createFromParcel(parcel: Parcel): MobileState {
            return Parcelizer.decodeFromParcel(serializer(), parcel)
        }

        override fun newArray(size: Int): Array<MobileState?> {
            return arrayOfNulls(size)
        }
    }
}

/**
 * Which side of the switch a network is on (D-423). Ethernet counts as Wi-Fi; a Wi-Fi Android counts as metered — another
 * phone's hotspot, or one the person marked so — counts as the mobile network (Q-116).
 */
enum class NetworkKind { None, Wifi, Mobile }
