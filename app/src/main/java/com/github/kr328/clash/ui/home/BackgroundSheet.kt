package com.github.kr328.clash.ui.home

import com.github.kr328.clash.ui.components.GlassTextButton
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.kr328.clash.R
import com.github.kr328.clash.util.Shortcut
import com.github.kr328.clash.util.labelWith
import com.github.kr328.clash.util.shortcuts
import com.github.kr328.clash.ui.components.DuckerSheet
import com.github.kr328.clash.ui.components.SheetButton
import com.github.kr328.clash.util.Vendor

/** Шаг панели «работа в фоне» (D-109). */
enum class BackgroundStep {
    /** Объяснить и попросить Android не усыплять ducker. */
    Allow,

    /** Android уже разрешил; осталось то, что оболочка телефона делает сама (D-107). */
    Vendor,
}

/**
 * Своя панель перед системным окном «Разрешить работу в фоне?» (D-109). Системное окно
 * говорит только о минусе — «может сократить время работы батареи», — и без объяснения
 * человек жмёт «Отказать». Панель показывается после первого удачного подключения: тогда
 * понятно, о чём речь.
 *
 * На телефонах, которые сами закрывают приложения в фоне, после разрешения в той же
 * панели идёт второй шаг — что включить в настройках оболочки.
 */
@Composable
fun BackgroundSheet(
    open: Boolean,
    step: BackgroundStep,
    vendor: Vendor?,
    /** MIUI's autostart for ducker; `null` — unknown (F-418). */
    autostart: Boolean?,
    onAllow: () -> Unit,
    /** Экран оболочки; `null` — страница приложения. */
    onOpen: (Shortcut?) -> Unit,
    onDismiss: () -> Unit,
    onClosed: () -> Unit,
) {
    val title = if (step == BackgroundStep.Vendor && vendor != null) {
        stringResource(R.string.background_vendor_title, stringResource(vendor.names))
    } else {
        stringResource(R.string.background_sheet_title)
    }

    DuckerSheet(
        open = open,
        title = title,
        busy = false,
        onDismiss = onDismiss,
        onClosed = onClosed,
    ) {
        if (step == BackgroundStep.Vendor && vendor != null) {
            Text(
                text = stringResource(R.string.background_vendor_step) + " " + stringResource(vendor.steps),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Прямые ссылки оболочки, если есть (D-128), иначе — страница приложения.
            vendor.shortcuts.ifEmpty { listOf(null) }.forEach { shortcut ->
                SheetButton(
                    text = stringResource(shortcut.labelWith(autostart)),
                    busyText = "",
                    busy = false,
                    enabled = true,
                    onClick = { onOpen(shortcut) },
                )
            }

            GlassTextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.done))
            }
        } else {
            Text(
                text = stringResource(R.string.background_sheet_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SheetButton(
                text = stringResource(R.string.background_allow),
                busyText = "",
                busy = false,
                enabled = true,
                onClick = onAllow,
            )

            GlassTextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.not_now))
            }
        }
    }
}
