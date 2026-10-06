package com.github.kr328.clash.core.model

import android.os.Parcel
import android.os.Parcelable
import com.github.kr328.clash.core.util.Parcelizer
import kotlinx.serialization.Serializable

/**
 * То, что клиент обязан применить сам: на Android туннель создаёт `VpnService`,
 * и раздел `tun:` профиля ядро не использует (FACTS.md, F-69).
 */
@Serializable
data class ProfileNetwork(
    /** `tun.exclude-package` — приложения ходят мимо туннеля. */
    val excludePackage: List<String> = emptyList(),
    /** `tun.include-package` — если список не пуст, в туннель попадают только они. */
    val includePackage: List<String> = emptyList(),
    /** `tun.route-exclude-address` — сети, которые не должны попадать в туннель. */
    val routeExcludeAddress: List<String> = emptyList(),
    /** `tun.stack` — `system`, `gvisor` или `mixed`. Пусто — в профиле не задан. */
    val stack: String = "",
    /** `tun.congestion-controller` — алгоритм TCP у стека `mips`. Пусто — в профиле не задан. */
    val congestionController: String = "",
    /** В профиле явно написано `dns.enable: false`. */
    val dnsDisabled: Boolean = false,
    /** `mode` — режим, заданный провайдером. Пусто — не задан, то есть правила. */
    val mode: String = "",
    // Parcelable нужен, чтобы список ходил через службу к экрану «Приложения».
) : Parcelable {
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        Parcelizer.encodeToParcel(serializer(), parcel, this)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ProfileNetwork> {
        override fun createFromParcel(parcel: Parcel): ProfileNetwork =
            Parcelizer.decodeFromParcel(serializer(), parcel)

        override fun newArray(size: Int): Array<ProfileNetwork?> = arrayOfNulls(size)
    }
}
