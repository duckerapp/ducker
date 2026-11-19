package com.github.kr328.clash.ui.components

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
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
    Dock("Корпус дока"),
    Veil("Подложка вкладки"),
    DockLens("Линза дока"),
    Pane("Карточки и листы"),
    Switch("Линза переключателя"),
    Slider("Линза ползунка"),
    Segments("Линзы сегментов"),
    Toolbar("Линзы редактора");
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
            knob("drawFrom", "Увеличение / масштаб", -0.35f, 0.35f),
            knob("zoomEdge", "Ширина перехода увеличения", 0.02f, 0.8f),
            knob("push", "Преломление края", 0f, 2f),
            knob("pushPower", "Крутизна преломления", 0.5f, 10f),
            knob("pushBand", "Ширина зоны преломления", 0f, 1f),
            knob("pushAlong", "Преломление на концах", 0f, 2f),
            knob("pushAcross", "Преломление сверху и снизу", 0f, 2f),
            knob("pushEndFrom", "Начало затухания по краям", 0f, 1f),
            knob("pushEndTo", "Конец затухания по краям", 0f, 1f),
            knob("chroma", "Разделение цветов", 0f, 0.5f),
            knob("disperse", "Цвета в преломлении", 0f, 1f),
            knob("smear", "Растяжение отражения", 0f, 2f),
            knob("bandSide", "Ширина отражения по сторонам", 0f, 0.5f),
            knob("bandEnd", "Ширина отражения на концах", 0f, 0.5f),
            knob("mirror", "Дальность отражения", 0f, 2f),
            knob("mirrorSoft", "Мягкость отражения", 0f, 0.4f),
            knob("mirrorChroma", "Цвета в отражении", 0f, 1f),
            knob("mirrorMix", "Видимость отражения", 0f, 1f),
            knob("ring", "Тёмный контур", 0f, 1f),
            knob("rimShade", "Затемнение края", 0f, 0.8f),
            knob("lighten", "Осветление линзы", 0f, 0.4f),
            knob("shineDark", "Блик на тёмном", 0f, 1f),
            knob("shineLight", "Блик на светлом", 0f, 1f),
            knob("shineEnds", "Блик на концах", 0f, 1f),
            knob("irisDark", "Радужный край на тёмном", 0f, 1f),
            knob("irisLight", "Радужный край на светлом", 0f, 1f),
            knob("shadowDark", "Тень на тёмном", 0f, 1f),
            knob("shadowLight", "Тень на светлом", 0f, 1f),
            knob("clearBlur", "Размытие прозрачной линзы, dp", 0f, 12f),
            knob("frostBlur", "Размытие при оседании, dp", 0f, 18f)
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
    GlassTuningGroup.Pane to listOf(
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
            knob("line", "Сила собственного блика", 0f, 1f),
            knob("lineWidth", "Ширина собственного блика, dp", 0.25f, 4f),
            knob("volume", "Разница света сверху и снизу", 0f, 0.5f),
            knob("spare", "Прозрачность оттенка на краю", 0f, 1f),
            knob("ring", "Внешний тёмный контур", 0f, 0.5f),
            knob("ringWidth", "Ширина тёмного контура, dp", 0.25f, 4f),
            knob("denser", "Плотность тонировки", 0f, 2f),
            knob("pressClear", "Осветление при нажатии", 0f, 1f),
            knob("pressGrow", "Рост при нажатии, dp", 0f, 20f),
            knob("growMin", "Минимальный рост при нажатии", 0f, 0.08f),
            knob("growMax", "Максимальный рост при нажатии", 0f, 0.12f)
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
)

internal fun GlassTuningGroup.usesLightValues(key: String, light: Boolean): Boolean = light && when (this) {
    GlassTuningGroup.Pane -> false
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
    var paneOwnRim by mutableStateOf(prefs.getBoolean("pane_own_rim", false))
        private set

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
            .put("base", "b5aa95af")
            .put("enabled", enabled)
            .put("paneOwnRim", paneOwnRim)
            .put("overrides", groups)
            .toString(2)
    }

    fun reset(group: GlassTuningGroup, light: Boolean) {
        val editor = prefs.edit()
        for (knob in glassKnobs.getValue(group)) {
            val key = group.storageKey(knob.key, group.usesLightValues(knob.key, light))
            values.getValue(key).floatValue = Float.NaN
            editor.remove(key)
        }
        if (group == GlassTuningGroup.Pane) ownRim(false)
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
