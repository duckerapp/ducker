package com.github.kr328.clash.ui.settings

import android.content.Context
import android.content.pm.PackageManager

/**
 * Настройки внешнего вида приложения.
 *
 * Своё хранилище, а не `UiStore` из модуля `design`: тот модуль мы заменяем целиком (D-2),
 * и тянуть за собой его настройки — значит потом переносить их ещё раз.
 * Настройки сети и VPN остаются в `ServiceStore`: они нужны службе, а не интерфейсу.
 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    // A TV keeps a theme of its own. These settings go into Android's cloud backup, and a TV signed in to the same
    // Google account can get a phone's light theme back on install instead of its own dark default (D-254).
    private val themeKey by lazy {
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) KEY_THEME_TV else KEY_THEME
    }

    /** `system`, `light` или `dark`. */
    var theme: String
        get() = prefs.getString(themeKey, THEME_SYSTEM) ?: THEME_SYSTEM
        set(value) = prefs.edit().putString(themeKey, value).apply()

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

    /**
     * Fingerprint, face or the screen lock before the subscription link, the QR code and the config, and no screenshots
     * of them (stage 15). Off by default until the owner answers Q-108.
     */
    var lockSensitive: Boolean
        get() = prefs.getBoolean(KEY_LOCK_SENSITIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_LOCK_SENSITIVE, value).apply()

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

    /** The look: Air, or liquid glass on phones with Android 13+ ([STYLE_AIR], [STYLE_GLASS]). */
    var style: String
        get() = prefs.getString(KEY_STYLE, STYLE_AIR) ?: STYLE_AIR
        set(value) = prefs.edit().putString(KEY_STYLE, value).apply()

    /** The glass style: the backdrop behind Home's glass, one of `BACKDROP_*`. */
    var glassBackdrop: String
        get() = prefs.getString(KEY_GLASS_BACKDROP, BACKDROP_SUNSET) ?: BACKDROP_SUNSET
        set(value) = prefs.edit().putString(KEY_GLASS_BACKDROP, value).apply()

    /** Experiment: the own picture is dark on average, so the app takes the dark palette over it. */
    var glassImageDark: Boolean
        get() = prefs.getBoolean(KEY_GLASS_IMAGE_DARK, false)
        set(value) = prefs.edit().putBoolean(KEY_GLASS_IMAGE_DARK, value).apply()

    /** The own picture's top, under the status bar and the headers, is dark; null — not measured yet. */
    var glassImageTopDark: Boolean?
        get() = if (prefs.contains(KEY_GLASS_IMAGE_TOP_DARK)) prefs.getBoolean(KEY_GLASS_IMAGE_TOP_DARK, false) else null
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_GLASS_IMAGE_TOP_DARK) else putBoolean(KEY_GLASS_IMAGE_TOP_DARK, value)
        }.apply()

    // Clear glass suits the built-in gradients, which carry no detail; over a photo leaves and snow showed sharp under
    // the letters and the rim stretched them into streaks (F-388). So an own picture has its own defaults until the
    // person moves a slider; «Сбросить» returns to them.
    private val photo: Boolean get() = glassBackdrop == BACKDROP_IMAGE

    /** Experiment: how much the glass blurs what is under it, 0…100 %. */
    var glassBlur: Int
        get() = prefs.getInt(KEY_GLASS_BLUR, if (photo) PHOTO_BLUR else 0)
        set(value) = prefs.edit().putInt(KEY_GLASS_BLUR, value).apply()

    /** Experiment: refraction strength / distortion, 0…100 %. */
    var glassRefraction: Int
        get() = prefs.getInt(KEY_GLASS_REFRACTION, if (photo) PHOTO_REFRACTION else 50)
        set(value) = prefs.edit().putInt(KEY_GLASS_REFRACTION, value).apply()

    /** Experiment: chromatic aberration / dispersion, 0…100 %. */
    var glassDispersion: Int
        get() = prefs.getInt(KEY_GLASS_DISPERSION, if (photo) PHOTO_DISPERSION else 40)
        set(value) = prefs.edit().putInt(KEY_GLASS_DISPERSION, value).apply()

    /** Experiment: 3D convex lens depth, 0…100 %. */
    var glassDepth: Int
        get() = prefs.getInt(KEY_GLASS_DEPTH, 30)
        set(value) = prefs.edit().putInt(KEY_GLASS_DEPTH, value).apply()

    /** Experiment: directional specular highlight brightness, 0…100 %. */
    var glassHighlight: Int
        get() = prefs.getInt(KEY_GLASS_HIGHLIGHT, 60)
        set(value) = prefs.edit().putInt(KEY_GLASS_HIGHLIGHT, value).apply()

    /** Experiment: micro-frost surface noise / grain, 0…100 %. */
    var glassNoise: Int
        get() = prefs.getInt(KEY_GLASS_NOISE, 15)
        set(value) = prefs.edit().putInt(KEY_GLASS_NOISE, value).apply()

    /** Experiment: saturation vibrancy boost, 0…100 %. */
    var glassVibrancy: Int
        get() = prefs.getInt(KEY_GLASS_VIBRANCY, 40)
        set(value) = prefs.edit().putInt(KEY_GLASS_VIBRANCY, value).apply()

    /** Experiment: glass tinting / opacity, 0…100 % (0 = clear transparent, 50 = default theme veil, 100 = dark smoked glass). */
    var glassTint: Int
        get() = prefs.getInt(KEY_GLASS_TINT, if (photo) PHOTO_TINT else 50)
        set(value) = prefs.edit().putInt(KEY_GLASS_TINT, value).apply()

    /**
     * Returns the tuning card's values to their defaults, which depend on the backdrop. Not the blur: it has a card of
     * its own, and the tuning's reset took it too.
     */
    fun resetGlassTuning() {
        prefs.edit()
            .remove(KEY_GLASS_REFRACTION)
            .remove(KEY_GLASS_DISPERSION)
            .remove(KEY_GLASS_DEPTH)
            .remove(KEY_GLASS_HIGHLIGHT)
            .remove(KEY_GLASS_NOISE)
            .remove(KEY_GLASS_VIBRANCY)
            .remove(KEY_GLASS_TINT)
            .apply()
    }

    /** Размер шрифта в редакторе конфига, sp (D-124): кому-то мелко, кому-то нужно больше строк. */
    var configTextSize: Float
        get() = prefs.getFloat(KEY_CONFIG_TEXT, CONFIG_TEXT_DEFAULT)
        set(value) = prefs.edit().putFloat(KEY_CONFIG_TEXT, value).apply()

    companion object {
        private const val FILE_NAME = "ducker"
        private const val KEY_CONFIG_TEXT = "config_text_size"

        const val CONFIG_TEXT_DEFAULT = 13f

        private const val KEY_THEME = "theme"
        private const val KEY_THEME_TV = "theme_tv"
        private const val KEY_OLED = "oled"
        private const val KEY_DYNAMIC = "dynamic_color"
        private const val KEY_HIDE_RECENTS = "hide_from_recents"
        private const val KEY_LOCK_SENSITIVE = "lock_sensitive"
        private const val KEY_EXTERNAL_CONTROL = "allow_external_control"
        private const val KEY_FAVORITES = "favorite_nodes"
        private const val KEY_FAVORITE_PROFILES = "favorite_profiles"
        private const val KEY_BETA = "beta_updates"
        private const val KEY_STYLE = "style"
        private const val KEY_GLASS_BACKDROP = "glass_backdrop"
        private const val KEY_GLASS_BLUR = "glass_blur"
        private const val KEY_GLASS_IMAGE_DARK = "glass_image_dark"
        private const val KEY_GLASS_IMAGE_TOP_DARK = "glass_image_top_dark"
        private const val KEY_GLASS_REFRACTION = "glass_refraction"
        private const val KEY_GLASS_DISPERSION = "glass_dispersion"
        private const val KEY_GLASS_DEPTH = "glass_depth"
        private const val KEY_GLASS_HIGHLIGHT = "glass_highlight"
        private const val KEY_GLASS_NOISE = "glass_noise"
        private const val KEY_GLASS_VIBRANCY = "glass_vibrancy"
        private const val KEY_GLASS_TINT = "glass_tint"

        // An own picture's defaults: frosted glass, denser, a calmer rim (F-388).
        private const val PHOTO_BLUR = 35
        private const val PHOTO_TINT = 65
        private const val PHOTO_REFRACTION = 30
        private const val PHOTO_DISPERSION = 15

        /** The own picture, a copy fitted to the screen: the picked file may go from the gallery later. */
        const val GLASS_IMAGE_FILE = "glass_backdrop.jpg"

        const val STYLE_AIR = "air"
        const val STYLE_GLASS = "glass"

        const val BACKDROP_NONE = "none"
        const val BACKDROP_SUNSET = "sunset"
        const val BACKDROP_GREY = "grey"
        const val BACKDROP_BLACK_GRADIENT = "black_gradient"
        const val BACKDROP_GREEN = "green"
        const val BACKDROP_PINK = "pink"
        const val BACKDROP_BLACK = "black"
        const val BACKDROP_BLACK_WHITE = "black_white"
        const val BACKDROP_IMAGE = "image"

        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
    }
}
