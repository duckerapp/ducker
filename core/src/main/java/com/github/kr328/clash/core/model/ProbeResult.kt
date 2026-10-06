package com.github.kr328.clash.core.model

import android.os.Parcel
import android.os.Parcelable
import com.github.kr328.clash.core.util.Parcelizer
import kotlinx.serialization.Serializable

/** Один замер узла: либо задержка, либо причина неудачи (D-57). */
@Serializable
data class ProbeResult(
    val delay: Int = 0,
    val error: String = "",
) : Parcelable {
    val ok: Boolean get() = error.isEmpty()

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        Parcelizer.encodeToParcel(serializer(), parcel, this)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ProbeResult> {
        override fun createFromParcel(parcel: Parcel): ProbeResult {
            return Parcelizer.decodeFromParcel(serializer(), parcel)
        }

        override fun newArray(size: Int): Array<ProbeResult?> = arrayOfNulls(size)
    }
}
