package com.github.kr328.clash.store

import android.content.Context
import org.json.JSONObject

/**
 * Последние известные задержки узлов (D-72).
 *
 * Ядро помнит замеры только пока загружено, и после остановки туннеля, смены вкладки
 * или перезапуска приложения список показывал прочерки, а через секунду — новые цифры.
 * Здесь они переживают всё это: экран сразу показывает то, что знали в прошлый раз,
 * а свежий замер просто уточняет значение.
 *
 * Значение — миллисекунды. [NO_ANSWER] значит «замер был и не удался»: это разные вещи,
 * и ядро их не различает (одно и то же 0xffff, `adapter/adapter.go:118`).
 */
class DelayStore(context: Context) {
    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    /** Когда последний раз что-то мерили. По нему решаем, не пора ли обновить. */
    val measuredAt: Long
        get() = prefs.getLong(KEY_AT, 0)

    /**
     * Замеры относятся к узлам одной подписки. Сменилась активная — прежние значения
     * уже не про неё, и хранить их нельзя (D-81).
     *
     * Не «лишние данные», а неправильные: узлы разных провайдеров сплошь и рядом зовутся
     * одинаково («Германия #1»), и чужой замер показался бы как свой. Заодно хранилище
     * перестаёт расти: раньше в нём оставались имена всех подписок, что когда-либо
     * стояли в приложении.
     */
    fun switchTo(profileId: String) {
        if (prefs.getString(KEY_PROFILE, null) == profileId) return

        prefs.edit().clear().putString(KEY_PROFILE, profileId).apply()
    }

    /**
     * Основная группа и её узел, как их последний раз показала главная. При выключенном VPN
     * главная берёт узел отсюда и не ждёт, пока ядро загрузит профиль (аудит, perf 5.2).
     * Со сменой подписки пропадает вместе с замерами — см. [switchTo].
     */
    var main: Pair<String, String>?
        get() {
            val group = prefs.getString(KEY_MAIN_GROUP, null) ?: return null
            val node = prefs.getString(KEY_MAIN_NODE, null) ?: return null

            return group to node
        }
        set(value) = prefs.edit()
            .putString(KEY_MAIN_GROUP, value?.first)
            .putString(KEY_MAIN_NODE, value?.second)
            .apply()

    fun all(): Map<String, Int> {
        val raw = prefs.getString(KEY_VALUES, null) ?: return emptyMap()

        return runCatching {
            val json = JSONObject(raw)

            json.keys().asSequence()
                .associateWith { json.getInt(it) }
                .filterValues { valid(it) }
        }.getOrDefault(emptyMap())
    }

    /** Дописывает замеры к прежним: узлы, которых в этот раз не мерили, остаются как были. */
    fun merge(values: Map<String, Int>) {
        // Мусор в хранилище живёт до следующего замера и показывается как настоящая
        // цифра, поэтому чужие значения сюда не попадают вовсе.
        val clean = values.filterValues { valid(it) }

        if (clean.isEmpty()) return

        val json = JSONObject(all() + clean as Map<*, *>)

        prefs.edit()
            .putString(KEY_VALUES, json.toString())
            .putLong(KEY_AT, System.currentTimeMillis())
            .apply()
    }

    /**
     * Результат замера группы: имя узла → значение от ядра.
     *
     * Отказ записывается только со второго промаха подряд. Один промах ничего не значит:
     * замер мог уйти в момент, когда туннель поднимался или ложился, и узел при этом
     * живой. Ложное «нет ответа» красным пугает сильнее, чем прочерк.
     */
    fun record(values: Map<String, Int>) {
        if (values.isEmpty()) return

        val misses = misses().toMutableMap()
        val measured = mutableMapOf<String, Int>()

        values.forEach { (name, core) ->
            val (value, count) = verdict(core, misses[name] ?: 0)

            if (value != null) measured[name] = value

            if (count == 0) misses -= name else misses[name] = count
        }

        prefs.edit().putString(KEY_MISSES, JSONObject(misses as Map<*, *>).toString()).apply()

        merge(measured)
    }

    private fun misses(): Map<String, Int> {
        val raw = prefs.getString(KEY_MISSES, null) ?: return emptyMap()

        return runCatching {
            val json = JSONObject(raw)

            json.keys().asSequence().associateWith { json.getInt(it) }
        }.getOrDefault(emptyMap())
    }

    companion object {
        private const val FILE_NAME = "ducker_delays"
        private const val KEY_VALUES = "values"
        private const val KEY_AT = "measured_at"
        private const val KEY_MISSES = "misses"
        private const val KEY_PROFILE = "profile"
        private const val KEY_MAIN_GROUP = "main_group"
        private const val KEY_MAIN_NODE = "main_node"

        /** Замера не было вовсе. */
        const val UNKNOWN = 0

        /** Замер был, узел не ответил. */
        const val NO_ANSWER = -1

        /** Ядро отдаёт это и мёртвому узлу, и неизмеренному. */
        private const val CORE_UNKNOWN = 65535

        /** Значение ядра в наше: всё, что не похоже на настоящий замер, — «не знаем». */
        fun ofCore(value: Int): Int = if (value in 1 until CORE_UNKNOWN) value else UNKNOWN

        /**
         * Что показать: свежий замер, иначе прошлый. Прочерк остаётся только там, где
         * узел не мерили никогда.
         */
        fun shown(core: Int, stored: Int?): Int {
            val fresh = ofCore(core)

            return if (fresh > 0) fresh else stored ?: UNKNOWN
        }

        /**
         * From here on the number is the time limit of the check, not a delay. With
         * `unified-delay` the core asks twice and reports the second answer; when the second
         * one does not fit into the limit, its error is dropped and the time since the first
         * request is returned — the limit plus a millisecond, "5001 мс" (`adapter/adapter.go`,
         * F-271). The node did answer, only slower than that.
         *
         * ponytail: the core's default limit, 5 s (`adapter/provider/healthcheck.go`). A profile
         * with a shorter `timeout` still shows its limit as a delay; curing that means asking
         * the core for the limit of every group.
         */
        const val SLOW = 5000

        /** Сколько промахов подряд считается настоящим отказом узла. */
        const val MISSES_BEFORE_NO_ANSWER = 2

        /**
         * Что записать по итогам одного замера: значение для хранилища (или `null`,
         * если записывать нечего) и новое число промахов подряд.
         *
         * Первый промах не пишет ничего: на экране остаётся прошлое значение, и узел
         * получает вторую попытку. Отказ ставится только со второго промаха.
         */
        fun verdict(core: Int, misses: Int): Pair<Int?, Int> {
            val ms = ofCore(core)

            if (ms > 0) return ms to 0

            val count = misses + 1

            return (if (count >= MISSES_BEFORE_NO_ANSWER) NO_ANSWER else null) to count
        }

        /** Замеры старше этого срока стоит обновить. */
        const val STALE = 5 * 60 * 1000L

        /**
         * Настоящий замер или отметка отказа. Всё прочее — служебные числа ядра
         * (то же `0xffff`), и на экране они выглядели бы как «65535 мс».
         */
        private fun valid(value: Int): Boolean =
            value == NO_ANSWER || value in 1 until CORE_UNKNOWN
    }
}
