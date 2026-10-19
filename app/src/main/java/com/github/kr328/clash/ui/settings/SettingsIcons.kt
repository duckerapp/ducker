package com.github.kr328.clash.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.ImageVector.Builder
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Optical corrections keep these contours balanced in the settings rows' existing 24dp slots. */
internal object SettingsIcons {
    val Language: ImageVector by lazy {
        icon("settings_language") {
            contour("M7.4 13.5H5.7A2.7 2.7 0 0 1 3 10.8V5.7A2.7 2.7 0 0 1 5.7 3H10.8A2.7 2.7 0 0 1 13.5 5.7V7.4", width = 1.7f)
            contour("M13.1 10.5H18.4A2.6 2.6 0 0 1 21 13.1V18.4A2.6 2.6 0 0 1 18.4 21H13.1A2.6 2.6 0 0 1 10.5 18.4V13.1A2.6 2.6 0 0 1 13.1 10.5Z", width = 1.7f)
            contour("M8.15 5.25V6.5M5.7 6.5H10.7M9.75 6.5C9.25 8.45 7.95 9.95 5.9 10.85M6.5 7.55C7.2 8.9 8.3 9.95 9.7 10.5", width = 1.3f)
            contour("M13.3 18.15 15.75 13.25 18.2 18.15M14.15 16.5H17.35", width = 1.4f)
        }
    }

    val Appearance: ImageVector by lazy {
        icon("settings_appearance") {
            contour("M20.45 13.3C19.75 17.4 16.2 20.5 11.95 20.5C7.2 20.5 3.35 16.65 3.35 11.9C3.35 7.7 6.4 4.15 10.5 3.45C9.4 4.85 8.8 6.6 8.8 8.45C8.8 12.4 12 15.6 15.95 15.6C17.6 15.6 19.2 14.75 20.45 13.3Z")
        }
    }

    val Connection: ImageVector by lazy {
        icon("settings_connection") {
            contour("M10.5 6.9 12.6 4.8C14.5 2.9 17.6 2.9 19.5 4.8C21.4 6.7 21.4 9.8 19.5 11.7L17.4 13.8C15.5 15.7 12.4 15.7 10.5 13.8")
            contour("M13.5 17.1 11.4 19.2C9.5 21.1 6.4 21.1 4.5 19.2C2.6 17.3 2.6 14.2 4.5 12.3L6.6 10.2C8.5 8.3 11.6 8.3 13.5 10.2")
        }
    }

    val Privacy: ImageVector by lazy {
        icon("settings_privacy") {
            contour("M12 3.3 18.75 6C19.15 6.15 19.4 6.5 19.4 6.95V11.35C19.4 15.5 16.9 18.7 12 20.9C7.1 18.7 4.6 15.5 4.6 11.35V6.95C4.6 6.5 4.85 6.15 5.25 6L12 3.3Z")
            contour("M10.1 11.1V9.55A1.9 1.9 0 0 1 13.9 9.55V11.1", width = 1.4f)
            contour("M9.8 11.1H14.2A0.8 0.8 0 0 1 15 11.9V15.1A0.8 0.8 0 0 1 14.2 15.9H9.8A0.8 0.8 0 0 1 9 15.1V11.9A0.8 0.8 0 0 1 9.8 11.1Z", width = 1.4f)
        }
    }

    val Help: ImageVector by lazy {
        icon("settings_help") {
            contour("M5.2 11.8V10.6A6.8 6.8 0 0 1 18.8 10.6V11.8")
            contour("M5 11.8H5.6A1.4 1.4 0 0 1 7 13.2V16.8A1.4 1.4 0 0 1 5.6 18.2H5A1.4 1.4 0 0 1 3.6 16.8V13.2A1.4 1.4 0 0 1 5 11.8Z")
            contour("M18.4 11.8H19A1.4 1.4 0 0 1 20.4 13.2V16.8A1.4 1.4 0 0 1 19 18.2H18.4A1.4 1.4 0 0 1 17 16.8V13.2A1.4 1.4 0 0 1 18.4 11.8Z")
            contour("M18.8 18.2C18.8 19.8 17.15 20.7 15.2 20.7H12.9")
        }
    }

    val Update: ImageVector by lazy {
        icon("settings_update") {
            contour("M19.8 8.8C18.55 5.65 15.5 3.6 12 3.6C7.36 3.6 3.6 7.36 3.6 12C3.6 16.64 7.36 20.4 12 20.4C15.8 20.4 19.1 17.85 20.1 14.35")
            contour("M19.8 4.2V8.8H15.2")
        }
    }

    val Project: ImageVector by lazy {
        icon("settings_project") {
            contour("M12 20.6 4.6 13.2C2 10.6 2.4 7.2 4.6 5.3C6.8 3.4 9.7 3.9 12 6.3C14.3 3.9 17.2 3.4 19.4 5.3C21.6 7.2 22 10.6 19.4 13.2L12 20.6Z")
        }
    }

    val About: ImageVector by lazy {
        icon("settings_about") {
            contour("M12 3.5A8.5 8.5 0 0 1 12 20.5A8.5 8.5 0 0 1 12 3.5Z")
            contour("M12 11.25V16.25")
            solid("M13 7.6A1 1 0 0 1 11 7.6A1 1 0 0 1 13 7.6Z")
        }
    }

    private fun icon(name: String, paths: Builder.() -> Unit): ImageVector = Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(paths).build()

    private fun Builder.contour(data: String, width: Float = 1.8f) {
        addPath(
            pathData = addPathNodes(data),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }

    private fun Builder.solid(data: String) {
        addPath(pathData = addPathNodes(data), fill = SolidColor(Color.Black))
    }
}
