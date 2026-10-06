package com.github.kr328.clash.store

import android.content.Context
import com.github.kr328.clash.common.store.Store
import com.github.kr328.clash.common.store.asStoreProvider

class TipsStore(context: Context) {
    private val store = Store(
        context
            .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .asStoreProvider()
    )

    var requestDonate: Boolean by store.boolean(
        key = "request_donate",
        defaultValue = true,
    )

    var primaryVersion: Int by store.int(
        key = "primary_version",
        defaultValue = -1,
    )

    /** Окно «Разрешить работу в фоне?» после первого подключения уже показывали (D-106). */
    var backgroundAsked: Boolean by store.boolean(
        key = "background_asked",
        defaultValue = false,
    )

    /**
     * Подсказку про оболочку телефона на главной уже открывали (D-107). Ключ новый, не
     * `transsion_hint_seen` из D-106: подсказка теперь для многих оболочек и с другим текстом.
     */
    var backgroundHintSeen: Boolean by store.boolean(
        key = "background_hint_seen",
        defaultValue = false,
    )

    /** До какого момента не показывать карточку «работа в фоне не разрешена» (D-109). */
    var backgroundSnoozedUntil: Long by store.long(
        key = "background_snoozed_until",
        defaultValue = 0,
    )

    /** Панель перед системным окном VPN уже показывали и человек пошёл дальше. */
    var vpnExplained: Boolean by store.boolean(
        key = "vpn_explained",
        defaultValue = false,
    )

    companion object {
        const val CURRENT_PRIMARY_VERSION = 1

        private const val FILE_NAME = "tips"
    }
}