package com.github.kr328.clash.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Свои значки панели вкладок — те же, что в концепте (D-75).
 *
 * Готовые из Material нарисованы заливкой и рядом с нашими волосяными рамками выглядят
 * жирнее всего остального. Эти — линией той же толщины, что и остальная графика.
 */
object DuckerIcons {
    /** Домик: скат крыши и стена, без двери и окон. */
    val Home: ImageVector by lazy {
        strokeIcon(
            "ducker_home",
            "M4 10.5 12 4l8 6.5V19a1.4 1.4 0 0 1-1.4 1.4H5.4A1.4 1.4 0 0 1 4 19v-8.5Z",
        )
    }

    /** Подписка: карточка с двумя строками. */
    val Subscriptions: ImageVector by lazy {
        strokeIcon(
            "ducker_subscriptions",
            "M6 5H18A2.2 2.2 0 0 1 20.2 7.2V16.8A2.2 2.2 0 0 1 18 19H6A2.2 2.2 0 0 1 3.8 16.8V7.2A2.2 2.2 0 0 1 6 5Z",
            "M8 9.5H16M8 13H13",
        )
    }

    /** Глобус: окружность, экватор и меридиан. */
    val Proxies: ImageVector by lazy {
        strokeIcon(
            "ducker_proxies",
            "M12 3.8A8.2 8.2 0 0 1 12 20.2A8.2 8.2 0 0 1 12 3.8Z",
            "M3.8 12H20.2",
            "M12 3.8c2.1 2.3 3.2 5.1 3.2 8.2s-1.1 5.9-3.2 8.2c-2.1-2.3-3.2-5.1-3.2-8.2s1.1-5.9 3.2-8.2Z",
        )
    }

    /** Замер задержки: линия пульса, «сетевой импульс» (этап 13, пакет 4). */
    val Ping: ImageVector by lazy {
        strokeIcon("ducker_ping", "M3 13h3.5l2.5-7 3.5 12 2.5-8 1.5 3H21")
    }

    /**
     * Цвет здесь не важен: [androidx.compose.material3.Icon] перекрашивает значок
     * целиком, и в панели он берёт цвет вкладки.
     */
    private fun strokeIcon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            paths.forEach { data ->
                addPath(
                    pathData = addPathNodes(data),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.8f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
}
