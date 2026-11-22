package com.github.kr328.clash.ui.components

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.json.JSONObject

internal const val GLASS_TUNING_PACKAGE = "app.ducker.glassreview"
internal val LocalGlassTuning = staticCompositionLocalOf<GlassTuning?> { null }

internal enum class GlassTuningGroup(val title: String) {
    Dock("Док"),
    DockPane("Док 2: как карточки"),
    Veil("Подложка вкладки"),
    DockLens("Линза дока"),
    Pane("Карточки"),
    Chosen("Выделение"),
    Switch("Тумблер"),
    Slider("Ползунок"),
    Segments("Сегменты"),
    Toolbar("Панели редактора"),
    Wall("Обои экранов"),
    Wave("Волна вкл/выкл");
}

internal data class GlassKnob(
    val key: String,
    val label: String,
    val min: Float,
    val max: Float,
    val step: Float = 0.01f,
)

private fun knob(key: String, label: String, min: Float, max: Float, step: Float = 0.01f) =
    GlassKnob(key, label, min, max, step)

private val lensKnobs = listOf(
            knob("drawFrom", "Увеличение внутри (минус — крупнее)", -0.35f, 0.35f),
            knob("zoomEdge", "Плавность увеличения к краю", 0.02f, 0.8f),
            knob("push", "Затягивание фона в край", 0f, 2f),
            knob("pushBand", "Ширина мягкого края (0 — острый)", 0f, 1f),
            knob("pushPower", "Резкость острого края", 0.5f, 10f),
            knob("pushAlong", "Затягивание на концах", 0f, 2f),
            knob("pushAcross", "Затягивание сверху и снизу", 0f, 2f),
            knob("pushEndFrom", "Сверху и снизу: начало спада", 0f, 1f),
            knob("pushEndTo", "Сверху и снизу: конец спада", 0f, 1f),
            knob("chroma", "Радуга по краю", 0f, 0.5f),
            knob("disperse", "Радуга в затянутом фоне", 0f, 1f),
            knob("smear", "Размазывание края", 0f, 2f),
            knob("bandSide", "Отражение: ширина сверху и снизу", 0f, 0.5f),
            knob("bandEnd", "Отражение: ширина на концах", 0f, 0.5f),
            knob("mirror", "Отражение: глубина", 0f, 2f),
            knob("mirrorSoft", "Отражение: мягкость", 0f, 0.4f),
            knob("mirrorChroma", "Отражение: радуга", 0f, 1f),
            knob("mirrorMix", "Отражение: яркость", 0f, 1f),
            knob("ring", "Тёмный контур", 0f, 1f),
            knob("rimShade", "Затемнение к краю", 0f, 0.8f),
            knob("lighten", "Осветление линзы", 0f, 0.4f),
            knob("shineDark", "Светлая линия по краю", 0f, 1f),
            knob("shineLight", "Светлая линия по краю", 0f, 1f),
            knob("shineEnds", "Светлая линия на концах", 0f, 1f),
            knob("irisDark", "Цветной отлив по краю", 0f, 1f),
            knob("irisLight", "Цветной отлив по краю", 0f, 1f),
            knob("shadowDark", "Тень под линзой", 0f, 1f),
            knob("shadowLight", "Тень под линзой", 0f, 1f),
            knob("clearBlur", "Размытие внутри, dp", 0f, 12f),
            knob("frostBlur", "Размытие при отпускании, dp", 0f, 18f)
)

/** The pane glass's knobs: the cards', and the dock's body in its second mode («Док 2»). */
private val paneKnobs = listOf(
            knob("blur", "Размытие", 0f, 100f, 1f),
            knob("tint", "Тонировка", 0f, 100f, 1f),
            knob("vibrancy", "Насыщенность", 0f, 100f, 1f),
            knob("refraction", "Преломление", 0f, 100f, 1f),
            knob("dispersion", "Разделение цветов", 0f, 100f, 1f),
            knob("depth", "Глубина", 0f, 100f, 1f),
            knob("highlight", "Блик", 0f, 100f, 1f),
            knob("noise", "Зерно", 0f, 100f, 1f),
            knob("band", "Ширина преломлённого края, dp", 1f, 45f),
            knob("push", "Сдвиг фона на краю, dp", 0f, 25f),
            knob("chroma", "Ширина цветного края, dp", 0f, 5f),
            knob("saturation", "Насыщенность внутри", 0f, 2f),
            knob("vivid", "Насыщенность края", 0f, 0.6f),
            knob("roll", "Осветление края", 0f, 0.4f),
            knob("line", "Собственный блик: сила", 0f, 1.5f),
            knob("lineWidth", "Собственный блик: ширина линии, dp", 0.25f, 12f),
            knob("lineSpread", "Блик: рассеивание вглубь, dp", 0f, 48f),
            knob("lineGlow", "Блик: сила рассеивания", 0f, 1f),
            knob("lineFall", "Блик: спад (больше — быстрее гаснет)", 0.3f, 6f),
            knob("lineDirect", "Блик: направленность (0 — по всему краю)", 0f, 1f),
            knob("volume", "Разница света сверху и снизу", 0f, 0.5f),
            knob("spare", "Прозрачность оттенка на краю", 0f, 1f),
            knob("ring", "Внешний тёмный контур", 0f, 0.5f),
            knob("ringWidth", "Ширина тёмного контура, dp", 0.25f, 4f),
            knob("denser", "Плотность тонировки", 0f, 2f),
            knob("pressClear", "Осветление при нажатии", 0f, 1f),
            knob("pressGrow", "Рост при нажатии, dp", 0f, 20f),
            knob("growMin", "Минимальный рост при нажатии", 0f, 0.08f),
            knob("growMax", "Максимальный рост при нажатии", 0f, 0.12f)
    
)

internal val glassKnobs = mapOf(
    GlassTuningGroup.Dock to listOf(
            knob("blur", "Размытие", 0f, 100f, 1f),
            knob("tint", "Тонировка", 0f, 100f, 1f),
            knob("vibrancy", "Насыщенность", 0f, 100f, 1f),
            knob("refraction", "Преломление", 0f, 100f, 1f),
            knob("dispersion", "Разделение цветов", 0f, 100f, 1f),
            knob("depth", "Глубина", 0f, 100f, 1f),
            knob("highlight", "Блик", 0f, 100f, 1f),
            knob("noise", "Зерно", 0f, 100f, 1f),
            knob("breath", "Рост корпуса при нажатии", 0f, 0.16f),
            knob("line", "Собственный блик: сила", 0f, 1.5f),
            knob("lineWidth", "Собственный блик: ширина линии, dp", 0.25f, 12f),
            knob("lineSpread", "Блик: рассеивание вглубь, dp", 0f, 48f),
            knob("lineGlow", "Блик: сила рассеивания", 0f, 1f),
            knob("lineFall", "Блик: спад (больше — быстрее гаснет)", 0.3f, 6f),
            knob("lineDirect", "Блик: направленность (0 — по всему краю)", 0f, 1f),
            knob("tintEase", "Осветление корпуса при нажатии", 0f, 1f),
            knob("tintNear", "Осветление рядом с линзой", 0f, 1f),
            knob("tintReach", "Ширина осветления около линзы", 0f, 2f)
    ),
    GlassTuningGroup.Veil to listOf(
            knob("opacity", "Видимость подложки", 0f, 1f),
            knob("shade", "Затемнение подложки", 0f, 0.8f),
            knob("blur", "Дополнительное размытие, dp", 0f, 30f)
    ),
    GlassTuningGroup.DockLens to listOf(
            knob("height", "Высота линзы", 0.85f, 1.65f),
            knob("aspect", "Ширина к высоте", 0.8f, 2.05f),
            knob("swellWide", "Расширение при касании", 0f, 0.35f),
            knob("swellFlat", "Сплющивание при касании", 0f, 0.3f),
            knob("swellZoom", "Увеличение при касании", 0f, 0.4f)
    ) + lensKnobs,
    GlassTuningGroup.Pane to paneKnobs,
    GlassTuningGroup.DockPane to paneKnobs,
    GlassTuningGroup.Chosen to listOf(
            knob("opacity", "Плотность выделения", 0f, 1f),
            knob("white", "Белизна выделения", 0f, 1f)
    ),
    GlassTuningGroup.Switch to listOf(
            knob("swell", "Размер под пальцем", 1f, 2.2f)
    ) + lensKnobs,
    GlassTuningGroup.Slider to listOf(
            knob("swell", "Размер под пальцем", 1f, 2.2f),
            knob("zoom", "Увеличение под пальцем", -0.15f, 0.4f),
            knob("bezel", "Ширина края, dp", 0.5f, 24f),
            knob("strength", "Преломление", 0f, 0.8f),
            knob("spread", "Разделение цветов", 0f, 0.6f),
            knob("rim", "Сила ободка", 0f, 2f),
            knob("rimWidth", "Ширина ободка, dp", 0.25f, 3f)
    ),
    GlassTuningGroup.Segments to listOf(
            knob("rise", "Рост по высоте, dp", 0f, 20f),
            knob("widen", "Рост по ширине", 0f, 0.4f)
    ) + lensKnobs,
    GlassTuningGroup.Toolbar to listOf(
            knob("height", "Высота линзы", 0.85f, 1.65f),
            knob("aspect", "Ширина к высоте", 0.8f, 2.05f),
            knob("widen", "Рост по ширине", 0f, 0.4f)
    ) + lensKnobs,
    GlassTuningGroup.Wave to listOf(
            knob("ring", "Толщина кольца, dp", 4f, 120f, 1f),
            knob("strength", "Сила искажения", 0f, 4f),
            knob("chroma", "Разложение цветов", 0f, 1.5f),
            knob("light", "Блик на гребне", 0f, 1.5f),
            knob("duration", "Длительность, мс", 300f, 3000f, 10f),
            knob("brake", "Торможение (1 — ровный ход)", 1f, 5f),
            knob("fadeIn", "Нарастание у кнопки, в толщинах кольца", 0f, 5f),
            knob("decay", "Ослабление к краю экрана", 0f, 1f),
            knob("fadeFrom", "Угасание с доли пути", 0.2f, 1f),
            knob("swell", "Набухание кнопки при вкл/выкл", 0f, 0.25f),
            knob("swellMs", "Набухание: время роста, мс", 40f, 800f, 10f),
            knob("swellBounce", "Набухание: упругость (меньше — сильнее качается)", 0.1f, 1f),
            knob("swellSpeed", "Набухание: скорость возврата", 50f, 800f, 5f),
            knob("glow", "Засветка вокруг кнопки при вкл", 0f, 0.5f),
            knob("glowReach", "Размер засветки, в радиусах кнопки", 1f, 6f),
            knob("onBend", "Изгиб края карточки при вкл", 0f, 3f),
            knob("onRim", "Блик края карточки при вкл", 0f, 3f)
    ),
    GlassTuningGroup.Wall to listOf(
            knob("blur", "Размытие обоев, dp", 0f, 40f, 0.5f),
            knob("tint", "Тонировка: плотность", 0f, 0.9f),
            knob("tone", "Тонировка: 0 — чёрная, 1 — белая", 0f, 1f),
            knob("saturation", "Насыщенность обоев", 0f, 2f)
    ),
)

internal fun GlassTuningGroup.usesLightValues(key: String, light: Boolean): Boolean = light && when (this) {
    GlassTuningGroup.Pane, GlassTuningGroup.DockPane, GlassTuningGroup.Wave -> false
    GlassTuningGroup.Dock -> key == "tintEase" || key == "tintNear"
    GlassTuningGroup.Veil -> key == "shade"
    else -> true
}

private fun GlassTuningGroup.storageKey(key: String, light: Boolean) = "$name.$key." + if (light) "light" else "dark"

@Stable
internal class GlassTuning(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("glass_tuning_review", Context.MODE_PRIVATE)
    private val values = buildMap {
        for ((group, knobs) in glassKnobs) {
            for (knob in knobs) {
                for (light in listOf(false, true)) {
                    val key = group.storageKey(knob.key, light)
                    val value = prefs.getFloat(key, Float.NaN)
                    put(key, mutableFloatStateOf(if (value.isFinite()) value.coerceIn(knob.min, knob.max) else Float.NaN))
                }
            }
        }
    }
    val sheets = mutableStateListOf<Any>()
    var expanded by mutableStateOf(false)
    var lower by mutableStateOf(false)
    var group by mutableStateOf(GlassTuningGroup.Dock)
    var enabled by mutableStateOf(prefs.getBoolean("enabled", true))
        private set
    var paneOwnRim by mutableStateOf(prefs.getBoolean("pane_own_rim", !PaneGlass.DOCK_RIM))
        private set
    /** «Док 2»: the dock's body in the pane glass (the cards'), tuned by its own group; off — its own optics. */
    var dockPane by mutableStateOf(prefs.getBoolean("dock_pane", false))
        private set
    var dockOwnRim by mutableStateOf(prefs.getBoolean("dock_own_rim", false))
        private set

    /** What the screens over the tabs lie on: Home's picture, the theme's plain background, or iOS's ([WallMode]). */
    var wallMode by mutableIntStateOf(prefs.getInt("wall_mode", WallMode.DEFAULT))
        private set

    fun useWallMode(mode: Int) {
        wallMode = mode
        prefs.edit().putInt("wall_mode", mode).apply()
    }

    fun useDockPane(on: Boolean) {
        dockPane = on
        prefs.edit().putBoolean("dock_pane", on).apply()
    }

    fun dockRim(on: Boolean) {
        dockOwnRim = on
        prefs.edit().putBoolean("dock_own_rim", on).apply()
    }

    /** The own light line of [group]'s panes: the cards' or the dock body's in its second mode. */
    fun ownRimOf(group: GlassTuningGroup) = when (group) {
        GlassTuningGroup.DockPane -> dockOwnRim
        GlassTuningGroup.Dock -> dockOneRim
        else -> paneOwnRim
    }

    /** «Док 1»'s body with the cards' own light line over its own glass, instead of its thin rim light. */
    var dockOneRim by mutableStateOf(prefs.getBoolean("dock1_own_rim", DockGlass.OWN_LINE))
        private set

    fun dock1Rim(on: Boolean) {
        dockOneRim = on
        prefs.edit().putBoolean("dock1_own_rim", on).apply()
    }

    fun enable(on: Boolean) {
        enabled = on
        prefs.edit().putBoolean("enabled", on).apply()
    }

    fun ownRim(on: Boolean) {
        paneOwnRim = on
        prefs.edit().putBoolean("pane_own_rim", on).apply()
    }

    fun saved(group: GlassTuningGroup, knob: String, light: Boolean = false): Float? =
        values.getValue(group.storageKey(knob, light)).floatValue.takeIf { it.isFinite() }

    fun value(group: GlassTuningGroup, knob: String, fallback: Float, light: Boolean = false): Float =
        if (enabled) saved(group, knob, light) ?: fallback else fallback

    fun set(group: GlassTuningGroup, knob: GlassKnob, light: Boolean, value: Float) {
        val key = group.storageKey(knob.key, light)
        val rounded = ((value / knob.step).roundToInt() * knob.step).coerceIn(knob.min, knob.max)
        if (values.getValue(key).floatValue == rounded) return
        values.getValue(key).floatValue = rounded
        prefs.edit().putFloat(key, rounded).apply()
    }

    fun export(): String {
        val groups = JSONObject()
        for ((group, knobs) in glassKnobs) {
            val modes = JSONObject()
            for ((mode, themed, light) in listOf(
                Triple("shared", false, false), Triple("dark", true, false), Triple("light", true, true),
            )) {
                val custom = JSONObject()
                for (knob in knobs) {
                    if (group.usesLightValues(knob.key, true) != themed) continue
                    saved(group, knob.key, light)?.let { custom.put(knob.key, it.toString().toDouble()) }
                }
                if (custom.length() > 0) modes.put(mode, custom)
            }
            if (modes.length() > 0) groups.put(group.name, modes)
        }
        return JSONObject()
            .put("format", "ducker-glass-tuning-v1")
            .put("base", "exp/glass-tuning-20261122")
            .put("enabled", enabled)
            .put("paneOwnRim", paneOwnRim)
            .put("dockPane", dockPane)
            .put("dockOwnRim", dockOwnRim)
            .put("dock1OwnRim", dockOneRim)
            .put("wallMode", wallMode)
            .put("overrides", groups)
            .toString(2)
    }

    /** One knob back to what it inherits. */
    fun clear(group: GlassTuningGroup, knob: GlassKnob, light: Boolean) {
        val key = group.storageKey(knob.key, light)
        values.getValue(key).floatValue = Float.NaN
        prefs.edit().remove(key).apply()
    }

    /** Several knobs of [group] at once (a preset), each clamped to its range. */
    fun apply(group: GlassTuningGroup, light: Boolean, preset: Map<String, Float>) {
        for (knob in glassKnobs.getValue(group)) {
            val value = preset[knob.key] ?: continue
            set(group, knob, group.usesLightValues(knob.key, light), value)
        }
    }

    /** The knobs «Мягкие края» overwrote, per group and theme, so pressing it again puts them back. */
    private val softBackups = mutableStateMapOf<String, String>().apply {
        for (group in GlassTuningGroup.entries) for (light in listOf(false, true)) {
            prefs.getString(softKey(group, light), null)?.let { put(softKey(group, light), it) }
        }
    }

    private fun softKey(group: GlassTuningGroup, light: Boolean) = "soft.${group.name}." + if (light) "light" else "dark"

    fun softOn(group: GlassTuningGroup, light: Boolean) = softKey(group, light) in softBackups

    /** «Мягкие края» on: the preset over the lens, its knobs as they were kept; off: those put back. */
    fun soft(group: GlassTuningGroup, light: Boolean, on: Boolean) {
        val key = softKey(group, light)
        val knobs = glassKnobs.getValue(group).filter { it.key in SOFT_EDGE_PRESET }
        if (on) {
            val backup = knobs.joinToString(";") { "${it.key}=${saved(group, it.key, group.usesLightValues(it.key, light)) ?: ""}" }
            softBackups[key] = backup
            prefs.edit().putString(key, backup).apply()
            apply(group, light, SOFT_EDGE_PRESET)
        } else {
            val backup = softBackups.remove(key) ?: return
            prefs.edit().remove(key).apply()
            val was = backup.split(";").associate { it.substringBefore("=") to it.substringAfter("=").toFloatOrNull() }
            for (knob in knobs) {
                val themed = group.usesLightValues(knob.key, light)
                val value = was[knob.key]
                if (value != null) set(group, knob, themed, value) else clear(group, knob, themed)
            }
        }
    }

    fun reset(group: GlassTuningGroup, light: Boolean) {
        softBackups.remove(softKey(group, light))
        prefs.edit().remove(softKey(group, light)).apply()
        val editor = prefs.edit()
        for (knob in glassKnobs.getValue(group)) {
            val key = group.storageKey(knob.key, group.usesLightValues(knob.key, light))
            values.getValue(key).floatValue = Float.NaN
            editor.remove(key)
        }
        // Back to the defaults: their own light lines as the app has them.
        if (group == GlassTuningGroup.Pane) ownRim(!PaneGlass.DOCK_RIM)
        if (group == GlassTuningGroup.DockPane) dockRim(false)
        if (group == GlassTuningGroup.Dock) dock1Rim(DockGlass.OWN_LINE)
        editor.apply()
    }
}

internal fun GlassTuning?.optics(group: GlassTuningGroup, fallback: GlassStyle): GlassStyle {
    if (this == null || !enabled) return fallback
    return fallback.copy(
        blur = value(group, "blur", fallback.blur.toFloat()).roundToInt(),
        tint = value(group, "tint", fallback.tint.toFloat()).roundToInt(),
        vibrancy = value(group, "vibrancy", fallback.vibrancy.toFloat()).roundToInt(),
        refraction = value(group, "refraction", fallback.refraction.toFloat()).roundToInt(),
        dispersion = value(group, "dispersion", fallback.dispersion.toFloat()).roundToInt(),
        depth = value(group, "depth", fallback.depth.toFloat()).roundToInt(),
        highlight = value(group, "highlight", fallback.highlight.toFloat()).roundToInt(),
        noise = value(group, "noise", fallback.noise.toFloat()).roundToInt(),
    )
}

internal fun GlassTuning?.lens(group: GlassTuningGroup, fallback: LensLook, light: Boolean): LensLook {
    if (this == null || !enabled) return fallback
    val endFrom = value(group, "pushEndFrom", fallback.pushEndFrom, light).coerceAtMost(0.999f)
    val endTo = value(group, "pushEndTo", fallback.pushEndTo, light).coerceAtLeast(endFrom + 0.001f)
    return fallback.copy(
        drawFrom = value(group, "drawFrom", fallback.drawFrom, light),
        zoomEdge = value(group, "zoomEdge", fallback.zoomEdge, light),
        push = value(group, "push", fallback.push, light),
        pushPower = value(group, "pushPower", fallback.pushPower, light),
        pushBand = value(group, "pushBand", fallback.pushBand, light),
        pushAlong = value(group, "pushAlong", fallback.pushAlong, light),
        pushAcross = value(group, "pushAcross", fallback.pushAcross, light),
        pushEndFrom = endFrom,
        pushEndTo = endTo,
        chroma = value(group, "chroma", fallback.chroma, light),
        disperse = value(group, "disperse", fallback.disperse, light),
        smear = value(group, "smear", fallback.smear, light),
        bandSide = value(group, "bandSide", fallback.bandSide, light),
        bandEnd = value(group, "bandEnd", fallback.bandEnd, light),
        mirror = value(group, "mirror", fallback.mirror, light),
        mirrorSoft = value(group, "mirrorSoft", fallback.mirrorSoft, light),
        mirrorChroma = value(group, "mirrorChroma", fallback.mirrorChroma, light),
        mirrorMix = value(group, "mirrorMix", fallback.mirrorMix, light),
        ring = value(group, "ring", fallback.ring, light),
        rimShade = value(group, "rimShade", fallback.rimShade, light),
        lighten = value(group, "lighten", fallback.lighten, light),
        shineDark = value(group, "shineDark", fallback.shineDark, light),
        shineLight = value(group, "shineLight", fallback.shineLight, light),
        shineEnds = value(group, "shineEnds", fallback.shineEnds, light),
        irisDark = value(group, "irisDark", fallback.irisDark, light),
        irisLight = value(group, "irisLight", fallback.irisLight, light),
        shadowDark = value(group, "shadowDark", fallback.shadowDark, light),
        shadowLight = value(group, "shadowLight", fallback.shadowLight, light),
        clearBlur = value(group, "clearBlur", fallback.clearBlur.value, light).dp,
        frostBlur = value(group, "frostBlur", fallback.frostBlur.value, light).dp,
    )
}

/** The lens groups: a preset applies to them. */
internal val GlassTuningGroup.lens: Boolean
    get() = this == GlassTuningGroup.DockLens || this == GlassTuningGroup.Switch ||
        this == GlassTuningGroup.Segments || this == GlassTuningGroup.Toolbar

/**
 * «Мягкие края»: a starting point after a lens the owner liked (FlClashX's glass dock, 22.11): the middle magnified, the
 * picture drawn into a wide soft rim along a circle's edge, smeared rather than copied, faint colours, a faint mirror,
 * no dark ring. The owner tunes from here; nothing applies until pressed.
 */
/** The screens' backgrounds in «Обои экранов». */
internal object WallMode {
    const val PICTURE = 0
    /** The theme's own background, no picture. */
    const val PLAIN = 1
    /** iOS's grouped background (its Settings): black in the dark, #F2F2F7 in the light. */
    const val IOS = 2
    /** The owner's choice (22.11). */
    const val DEFAULT = IOS
}

/** The picture's blur (dp) and tint over it in «Обои экранов»: the owner's on the dark theme (22.11), none on the light. */
internal fun wallBlur(dark: Boolean): Float = if (dark) 20f else 0f
internal fun wallTint(dark: Boolean): Float = if (dark) 0.2f else 0f

internal val SOFT_EDGE_PRESET = mapOf(
    "drawFrom" to -0.18f, "zoomEdge" to 0.6f, "push" to 1.4f, "pushBand" to 0.7f, "pushAlong" to 1f, "pushAcross" to 1f,
    "pushEndFrom" to 0.1f, "pushEndTo" to 0.6f, "chroma" to 0.05f, "disperse" to 0.15f, "smear" to 1.6f,
    "bandSide" to 0.2f, "mirrorSoft" to 0.16f, "mirrorMix" to 0.25f, "ring" to 0.08f, "rimShade" to 0f, "lighten" to 0.04f,
)

internal fun lensDefaults(look: LensLook): Map<String, Float> = mapOf(
    "drawFrom" to look.drawFrom,
    "zoomEdge" to look.zoomEdge,
    "push" to look.push,
    "pushPower" to look.pushPower,
    "pushBand" to look.pushBand,
    "pushAlong" to look.pushAlong,
    "pushAcross" to look.pushAcross,
    "pushEndFrom" to look.pushEndFrom,
    "pushEndTo" to look.pushEndTo,
    "chroma" to look.chroma,
    "disperse" to look.disperse,
    "smear" to look.smear,
    "bandSide" to look.bandSide,
    "bandEnd" to look.bandEnd,
    "mirror" to look.mirror,
    "mirrorSoft" to look.mirrorSoft,
    "mirrorChroma" to look.mirrorChroma,
    "mirrorMix" to look.mirrorMix,
    "ring" to look.ring,
    "rimShade" to look.rimShade,
    "lighten" to look.lighten,
    "shineDark" to look.shineDark,
    "shineLight" to look.shineLight,
    "shineEnds" to look.shineEnds,
    "irisDark" to look.irisDark,
    "irisLight" to look.irisLight,
    "shadowDark" to look.shadowDark,
    "shadowLight" to look.shadowLight,
    "clearBlur" to look.clearBlur.value,
    "frostBlur" to look.frostBlur.value,
)
