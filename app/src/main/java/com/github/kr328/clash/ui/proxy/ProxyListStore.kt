package com.github.kr328.clash.ui.proxy

import android.content.Context
import com.github.kr328.clash.core.model.TunnelState
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * What «Прокси» showed last, a file per subscription (D-391). After the process was killed the tab
 * shows it at once and the live list replaces it, instead of placeholders for the swipe and the load.
 * In the cache folder: it is rebuilt by the next visit, and a system that clears it costs one start
 * with placeholders.
 */
class ProxyListStore(context: Context) {
    private val dir = context.cacheDir.resolve("proxies")

    fun load(uuid: UUID): ProxyState? = runCatching { decodeList(dir.resolve("$uuid.json").readText()) }.getOrNull()

    /** A full disk leaves the previous copy, or none, not a crash. */
    fun save(uuid: UUID, text: String) {
        runCatching {
            dir.mkdirs()

            dir.resolve("$uuid.json").writeText(text)
        }
    }

    fun forget(uuid: UUID) {
        dir.resolve("$uuid.json").delete()
    }
}

/** The list as the tab had it before favourites were lifted: the stars are applied again on reading. */
internal fun encodeList(groups: List<ProxyGroupState>, state: ProxyState): String = JSONObject()
    .put("mode", state.mode?.name)
    .put("modeSwitch", state.modeSwitch)
    .put("canCheck", state.canCheck)
    .put("groups", JSONArray(groups.map { group ->
        JSONObject()
            .put("name", group.name)
            .put("now", group.now)
            .put("icon", group.icon)
            .put("selectable", group.selectable)
            .put("proxies", JSONArray(group.proxies.map { node ->
                JSONObject()
                    .put("name", node.name)
                    .put("type", node.type)
                    .put("delay", node.delay)
                    .put("tested", node.tested)
            }))
    }))
    .toString()

internal fun decodeList(text: String): ProxyState {
    val json = JSONObject(text)
    val groups = json.getJSONArray("groups")

    return ProxyState(
        groups = List(groups.length()) { i ->
            val group = groups.getJSONObject(i)
            val proxies = group.getJSONArray("proxies")

            ProxyGroupState(
                name = group.getString("name"),
                now = group.getString("now"),
                icon = group.getString("icon"),
                proxies = List(proxies.length()) { j ->
                    val node = proxies.getJSONObject(j)

                    NodeState(node.getString("name"), node.getString("type"), node.getInt("delay"), tested = node.getLong("tested"))
                },
                selectable = group.getBoolean("selectable"),
            )
        },
        mode = json.optString("mode").let { name -> TunnelState.Mode.entries.firstOrNull { it.name == name } },
        modeSwitch = json.getBoolean("modeSwitch"),
        canCheck = json.getBoolean("canCheck"),
        loaded = true,
    )
}
