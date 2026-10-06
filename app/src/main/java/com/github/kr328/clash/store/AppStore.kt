package com.github.kr328.clash.store

import android.content.Context
import com.github.kr328.clash.common.store.Store
import com.github.kr328.clash.common.store.asStoreProvider

class AppStore(context: Context) {
    private val store = Store(
        context
            .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            .asStoreProvider()
    )

    var updatedAt: Long by store.long(
        key = "updated_at",
        defaultValue = -1,
    )

    /**
     * Порядок подписок, заданный перетаскиванием (этап 13, пакет 5): номера через запятую. Здесь, а
     * не в базе службы: порядок нужен только вкладке «Подписки», и базе не нужна миграция.
     */
    var profileOrder: String by store.string(
        key = "profile_order",
        defaultValue = "",
    )

    companion object {
        private const val FILE_NAME = "app"
    }
}