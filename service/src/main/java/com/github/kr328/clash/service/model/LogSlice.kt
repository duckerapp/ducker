package com.github.kr328.clash.service.model

import android.os.Parcel
import android.os.Parcelable
import com.github.kr328.clash.core.model.LogMessage
import com.github.kr328.clash.core.util.Parcelizer
import kotlinx.serialization.Serializable

/**
 * A reader's portion of the core log (D-290): the lines after its place and the place to ask from
 * next time. [epoch] names one life of the service process — the numbering starts over with it.
 */
@Serializable
data class LogSlice(
    val epoch: Long,
    val next: Long,
    val lines: List<LogMessage>,
) : Parcelable {
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        Parcelizer.encodeToParcel(serializer(), parcel, this)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<LogSlice> {
        override fun createFromParcel(parcel: Parcel): LogSlice {
            return Parcelizer.decodeFromParcel(serializer(), parcel)
        }

        override fun newArray(size: Int): Array<LogSlice?> {
            return arrayOfNulls(size)
        }
    }
}
