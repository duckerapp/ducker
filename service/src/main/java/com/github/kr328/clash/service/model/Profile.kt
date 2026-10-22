@file:UseSerializers(UUIDSerializer::class)

package com.github.kr328.clash.service.model

import android.os.Parcel
import android.os.Parcelable
import com.github.kr328.clash.core.util.Parcelizer
import com.github.kr328.clash.service.util.UUIDSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.util.*

@Serializable
data class Profile(
    val uuid: UUID,
    val name: String,
    val type: Type,
    val source: String,
    val active: Boolean,
    val interval: Long,
    val upload: Long,
    var download: Long,
    val total: Long,
    val expire: Long,
    val updatedAt: Long,
    val imported: Boolean,
    val pending: Boolean,
    val ageSecretKey: String? = null,
    /** Когда подписка добавлена в приложение. Нужен, чтобы показать долю оставшегося срока. */
    val createdAt: Long = 0,
    /**
     * Сведения от провайдера: как он назвал подписку, что хочет сказать, куда отправить
     * и не упёрся ли человек в лимит устройств. Приходят заголовками ответа, лежат файлом
     * в каталоге профиля и подмешиваются сюда при чтении (D-32).
     *
     * Поля с умолчаниями: у профиля из файла или от провайдера, который ничего не прислал,
     * они просто пустые, и интерфейс такие элементы не показывает.
     */
    val profileTitle: String = "",
    val announce: String = "",
    val supportUrl: String = "",
    val profileWebPageUrl: String = "",
    val brandName: String = "",
    val brandLogo: String = "",
    val buyUrl: String = "",
    val overrideUser: Boolean = false,
    val globalHidden: Boolean = false,
    val refillAt: Long = 0,
    val hwidBlocked: Boolean = false,
    val hwidReason: String = "",
    val usedTemplate: Boolean = false,
    val lastError: String = "",
    val templateFromConfig: Boolean = false,
    /** “Провайдер” is chosen: the provider's hours win over [interval] while it sends them (D-311). */
    val followProvider: Boolean = false,
    /** The provider's `profile-update-interval` of the last download, in milliseconds; 0 — not sent. */
    val providerInterval: Long = 0,
    /** How many backup addresses the provider gave for this link (D-345). */
    val backups: Int = 0,
    /** Those addresses in full: «Поделиться» and the TV transfer carry them (stage 21, package 3). */
    val mirrors: List<String> = emptyList(),
    /** The host of the link the provider moved the subscription from, and when (Q-80); shown for a week. */
    val movedFrom: String = "",
    val movedAt: Long = 0,
    /** The link and its mirrors with the outcome of their last try (Q-82); empty without mirrors. */
    val addresses: List<SubscriptionAddress> = emptyList(),
    /** When they were last tried. */
    val triedAt: Long = 0,
) : Parcelable {
    enum class Type {
        File, Url, External
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        Parcelizer.encodeToParcel(serializer(), parcel, this)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Profile> {
        override fun createFromParcel(parcel: Parcel): Profile {
            return Parcelizer.decodeFromParcel(serializer(), parcel)
        }

        override fun newArray(size: Int): Array<Profile?> {
            return arrayOfNulls(size)
        }
    }
}
