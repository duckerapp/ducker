package com.github.kr328.clash.core.model

import android.os.Parcel
import android.os.Parcelable
import com.github.kr328.clash.core.util.Parcelizer
import kotlinx.serialization.Serializable

/**
 * Группа прокси, прочитанная из файла профиля, а не у работающего ядра.
 *
 * Нужна, чтобы показать выбор узла при выключенном подключении: задержек и живого
 * состояния здесь нет — только имена, которые записал провайдер.
 */
@Serializable
data class ProfileGroup(
    val name: String,
    val type: String,
    val proxies: List<String>,
    /** Иконка группы из профиля. Пустая строка — провайдер её не задал. */
    val icon: String = "",
    /** Что выбрано сейчас: из сохранённого выбора, иначе первый участник. */
    val now: String = "",
) : Parcelable {
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        Parcelizer.encodeToParcel(serializer(), parcel, this)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ProfileGroup> {
        override fun createFromParcel(parcel: Parcel): ProfileGroup {
            return Parcelizer.decodeFromParcel(serializer(), parcel)
        }

        override fun newArray(size: Int): Array<ProfileGroup?> = arrayOfNulls(size)
    }
}
