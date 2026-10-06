package com.github.kr328.clash.ui.components

/** Вкладок три: главная, прокси, подписки. */
internal const val TABS = 3

/**
 * Геометрия подсветки в панели вкладок (D-76).
 *
 * Вынесена отдельно и без единого имени из Compose: это единственная часть панели,
 * которую можно проверить обычным тестом, а ошибка здесь видна как съехавшая пилюля.
 */
internal fun dockSlotWidth(width: Float, tabs: Int = TABS): Float =
    if (tabs <= 0) 0f else (width / tabs).coerceAtLeast(0f)

/**
 * Левый край подсветки для положения [position] — непрерывного номера страницы
 * от 0 до `tabs - 1`. Выход за край (пружина в конце списка) подсветку не двигает:
 * она упирается в крайнюю вкладку.
 */
internal fun dockPillOffset(
    width: Float,
    position: Float,
    tabs: Int = TABS,
    rtl: Boolean = false,
): Float {
    if (tabs <= 0) return 0f

    val slot = dockSlotWidth(width, tabs)
    val offset = slot * position.coerceIn(0f, (tabs - 1).toFloat())

    return if (rtl) width - offset - slot else offset
}
