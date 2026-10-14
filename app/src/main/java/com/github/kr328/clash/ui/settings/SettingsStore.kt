package com.github.kr328.clash.ui.settings

import android.content.Context

/**
 * Настройки внешнего вида приложения.
 *
 * Своё хранилище, а не `UiStore` из модуля `design`: тот модуль мы заменяем целиком (D-2),
 * и тянуть за собой его настройки — значит потом переносить их ещё раз.
 * Настройки сети и VPN остаются в `ServiceStore`: они нужны службе, а не интерфейсу.
 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** `system`, `light` или `dark`. */
    var theme: String
        get() = prefs.getString(KEY_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
        set(value) = prefs.edit().putString(KEY_THEME, value).apply()

    /** Настоящий чёрный фон вместо тёмно-серого — заметная экономия на OLED (D-21). */
    var oled: Boolean
        get() = prefs.getBoolean(KEY_OLED, false)
        set(value) = prefs.edit().putBoolean(KEY_OLED, value).apply()

    /**
     * Цвета системы (Material You). По умолчанию выключены: иначе фирменный зелёный
     * исчезнет на большинстве телефонов (D-21).
     */
    var dynamicColor: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC, false)
        set(value) = prefs.edit().putBoolean(KEY_DYNAMIC, value).apply()

    /** New key: absent on both fresh and existing installations, so access starts disabled. */
    var allowExternalControl: Boolean
        get() = prefs.getBoolean(KEY_EXTERNAL_CONTROL, false)
        set(value) = prefs.edit().putBoolean(KEY_EXTERNAL_CONTROL, value).apply()

    /** Получать выпуски на несколько дней раньше остальных. */
    var betaUpdates: Boolean
        get() = prefs.getBoolean(KEY_BETA, false)
        set(value) = prefs.edit().putBoolean(KEY_BETA, value).apply()

    /** Не показывать приложение в списке недавних. */
    var hideFromRecents: Boolean
        get() = prefs.getBoolean(KEY_HIDE_RECENTS, false)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_RECENTS, value).apply()

    /**
     * Узлы, которые человек поднял наверх звёздочкой.
     *
     * Список общий, а не по профилю: имена узлов приходят от провайдера и у одного
     * человека обычно одна подписка. Набор из `SharedPreferences` возвращается тем же
     * объектом, что лежит внутри, поэтому наружу отдаём копию — иначе правка на месте
     * не сохранится.
     */
    var favorites: Set<String>
        get() = prefs.getStringSet(KEY_FAVORITES, null)?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_FAVORITES, value.toSet()).apply()

    /** Subscriptions marked as favourites, by number: on “Подписки” they come first after the active one. */
    var favoriteProfiles: Set<String>
        get() = prefs.getStringSet(KEY_FAVORITE_PROFILES, null)?.toSet() ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_FAVORITE_PROFILES, value.toSet()).apply()

    /** Размер шрифта в редакторе конфига, sp (D-124): кому-то мелко, кому-то нужно больше строк. */
    var configTextSize: Float
        get() = prefs.getFloat(KEY_CONFIG_TEXT, CONFIG_TEXT_DEFAULT)
        set(value) = prefs.edit().putFloat(KEY_CONFIG_TEXT, value).apply()

    companion object {
        private const val FILE_NAME = "ducker"
        private const val KEY_CONFIG_TEXT = "config_text_size"

        const val CONFIG_TEXT_DEFAULT = 13f

        private const val KEY_THEME = "theme"
        private const val KEY_OLED = "oled"
        private const val KEY_DYNAMIC = "dynamic_color"
        private const val KEY_HIDE_RECENTS = "hide_from_recents"
        private const val KEY_EXTERNAL_CONTROL = "allow_external_control"
        private const val KEY_FAVORITES = "favorite_nodes"
        private const val KEY_FAVORITE_PROFILES = "favorite_profiles"
        private const val KEY_BETA = "beta_updates"

        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
    }
}
