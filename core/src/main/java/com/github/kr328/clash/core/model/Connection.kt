package com.github.kr328.clash.core.model

import android.os.Parcel
import android.os.Parcelable
import com.github.kr328.clash.core.util.Parcelizer
import kotlinx.serialization.Serializable

/**
 * One open connection of the core for «Подключения» (stage 15). [uid] is the app's — from the core when a rule looked it
 * up, else asked of Android; -1 or 0 — not known. [process] is the package the core found, when it did. [inner] — the
 * core's own (node checks, provider downloads). [chain] names the node first, then the groups it was reached through.
 */
@Serializable
data class Connection(
    val id: String,
    val host: String = "",
    val address: String = "",
    val port: Int = 0,
    val network: String = "",
    val uid: Int = -1,
    val process: String = "",
    val inner: Boolean = false,
    val rule: String = "",
    val rulePayload: String = "",
    val chain: List<String> = emptyList(),
    val upload: Long = 0,
    val download: Long = 0,
    val start: Long = 0,
) : Parcelable {
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        Parcelizer.encodeToParcel(serializer(), parcel, this)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Connection> {
        override fun createFromParcel(parcel: Parcel): Connection {
            return Parcelizer.decodeFromParcel(serializer(), parcel)
        }

        override fun newArray(size: Int): Array<Connection?> = arrayOfNulls(size)
    }
}
