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
 *
 * What is kept is as it was measured (D-282): the last delay a node answered with, and whether
 * its last check went unanswered. A list shows the second as "нет ответа" after that one check.
 * "Главная" shows the first and decides about "нет ответа" by its own check of the connection.
 */
class DelayStore(context: Context) {
    private val prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

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

    /** For a list: the last delay of a node, or [NO_ANSWER] when its last check went unanswered. */
    fun all(): Map<String, Int> = known().listed

    /**
     * The last delay each node answered with, also when a later check went unanswered: for the
     * node under the button on "Главная", where one miss in a check of the list must not read
     * "нет ответа" for a server that works (D-282).
     */
    fun answers(): Map<String, Int> = known().answers

    /**
     * What the core knows about the nodes now, node name to its last result ([ofCore]): a delay,
     * [NO_ANSWER], or [UNKNOWN] for a node the core has not tested — that one is left as it was.
     */
    fun merge(results: Map<String, Int>) {
        if (results.values.none { it > 0 || it == NO_ANSWER }) return

        val known = known().after(results)

        prefs.edit()
            .putString(KEY_VALUES, JSONObject(known.answers as Map<*, *>).toString())
            .putStringSet(KEY_SILENT, known.silent)
            .apply()
    }

    private fun known(): Known {
        val stored = prefs.getString(KEY_VALUES, null)?.let { raw ->
            runCatching {
                val json = JSONObject(raw)

                json.keys().asSequence().associateWith { json.getInt(it) }
            }.getOrNull()
        }.orEmpty()

        // Builds before D-282 kept "нет ответа" among the delays, as -1.
        val old = stored.filterValues { it == NO_ANSWER }.keys

        return Known(
            answers = stored.filterValues { it in 1 until CORE_UNKNOWN },
            silent = prefs.getStringSet(KEY_SILENT, null).orEmpty() + old,
        )
    }

    /** What is known about the nodes: the last delay each answered with, and who is silent now. */
    data class Known(val answers: Map<String, Int> = emptyMap(), val silent: Set<String> = emptySet()) {
        val listed: Map<String, Int> get() = answers + silent.associateWith { NO_ANSWER }

        /** After a reading of the core: [results] are node name to its last result. */
        fun after(results: Map<String, Int>): Known {
            val answered = results.filterValues { it > 0 }

            return Known(answers + answered, silent - answered.keys + results.filterValues { it == NO_ANSWER }.keys)
        }
    }

    companion object {
        private const val FILE_NAME = "ducker_delays"
        private const val KEY_VALUES = "values"
        private const val KEY_SILENT = "silent"
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
         * The last result of a node as the core has it: its delay; [NO_ANSWER] when it was tested
         * (at [tested], ms since the epoch) and did not answer; [UNKNOWN] when the core has not
         * tested it. The delay alone is the same for the last two (D-282).
         */
        fun ofCore(delay: Int, tested: Long): Int = when {
            ofCore(delay) > 0 -> delay
            tested > 0 -> NO_ANSWER
            else -> UNKNOWN
        }

        /**
         * Что показать: свежий замер, иначе прошлый. Прочерк остаётся только там, где
         * узел не мерили никогда.
         */
        fun shown(core: Int, stored: Int?): Int {
            if (core == NO_ANSWER) return NO_ANSWER

            val fresh = ofCore(core)

            return if (fresh > 0) fresh else stored ?: UNKNOWN
        }

        /**
         * A node has no result yet: the core has no delay for it, and its last check is not known
         * to have gone unanswered.
         */
        fun undecided(core: Int, stored: Int?): Boolean = ofCore(core) == UNKNOWN && stored != NO_ANSWER

        /**
         * Whether the tab checks a group by itself after [tried] checks; [nodes] are the core's
         * value and the stored one of each node.
         *
         * Once — when a node has no result yet, when a result is [outdated], or when a node is
         * [silent] since long enough. A second time when a node is [silent] after the first: one
         * miss right after the tunnel came up said nothing about a working node (D-318). Again
         * after that — only when the check brought nothing at all: that is a check lost while the
         * tunnel was coming up (D-72), not the nodes.
         */
        fun recheck(
            tried: Int,
            nodes: List<Pair<Int, Int?>>,
            outdated: Boolean = false,
            silent: Boolean = false,
        ): Boolean =
            when {
                tried == 0 -> outdated || silent || nodes.any { (core, stored) -> undecided(core, stored) }
                tried == 1 && silent -> true
                else -> nodes.isNotEmpty() && nodes.none { (core, _) -> ofCore(core) > 0 }
            }

        /**
         * A node reads "нет ответа", and the core has not tested it for [SILENT_RECHECK] ms by
         * [now] — or not at all since it started (the store remembers the miss of an earlier
         * run). Such a label stayed until a tap: groups like "⚡ AUTO" are tested only by a
         * check of the list they are in, and one miss right after the tunnel came up was enough
         * (D-318). [core] is [ofCore] of the core's value, [tested] its time of test.
         */
        fun silent(core: Int, stored: Int?, tested: Long, now: Long): Boolean =
            (core == NO_ANSWER || core == UNKNOWN && stored == NO_ANSWER) && now - tested >= SILENT_RECHECK

        /** How long a "нет ответа" stands before an opening of the tab asks the node again. */
        const val SILENT_RECHECK = 60_000L

        /**
         * A result taken at [tested] is one of another network: the network under the tunnel
         * changed at [changedAt] after it (both ms since the epoch, 0 — never). On the walk home
         * a "нет ответа" of the mobile network was still on screen on Wi-Fi (D-299).
         */
        fun outdated(tested: Long, changedAt: Long): Boolean = tested in 1 until changedAt

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
    }
}
