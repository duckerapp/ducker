package com.github.kr328.clash.ui.update

import com.github.kr328.clash.ui.components.GlassTextButton
import com.github.kr328.clash.ui.components.sheetSurface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.R
import com.github.kr328.clash.ui.components.DuckerSheet
import com.github.kr328.clash.ui.components.SheetButton
import com.github.kr328.clash.ui.components.formatBytes
import com.github.kr328.clash.update.Release
import com.github.kr328.clash.update.UpdateError
import com.github.kr328.clash.update.UpdateState
import com.github.kr328.clash.update.Updated

/**
 * Панель обновления приложения: что нового и одна кнопка. Кнопка заливается по ходу скачивания,
 * потом — «Устанавливаю», и приложение перезапускается уже новой версией.
 *
 * Панель не держит человека, пока файл качается: её можно закрыть, скачивание идёт дальше, а
 * открытая заново она показывает, где оно сейчас.
 */
@Composable
fun UpdateSheet(
    open: Boolean,
    release: Release,
    state: UpdateState,
    /** VPN включён: файл пойдёт через узел, то есть из трафика подписки. */
    vpn: Boolean,
    /** Android уже разрешил ducker ставить приложения; нет — «Обновить» сначала ведёт в настройки. */
    permitted: Boolean,
    onInstall: () -> Unit,
    /** After Android refused the install: hand the downloaded file to the system's installer. */
    onSystemInstall: () -> Unit,
    /** The answer to “download updates in advance?”; “yes” installs this version as well. */
    onAuto: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onClosed: () -> Unit,
) {
    // Asked once, with the first update the person opens (D-287).
    val ask = state.auto == null

    DuckerSheet(
        open = open,
        title = stringResource(R.string.app_update_title, release.version),
        busy = false,
        onDismiss = onDismiss,
        onClosed = onClosed,
    ) {
        Notes(release.notes)

        Text(
            text = stringResource(
                if (vpn) R.string.app_update_size_vpn else R.string.app_update_size,
                formatBytes(release.apk.size),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Что сейчас откроется и зачем — до системного экрана, а не после (как у панели VPN).
        if (!permitted && !state.busy) {
            Text(
                text = stringResource(R.string.app_update_permission),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.error != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(
                        when (state.error) {
                            UpdateError.Download -> R.string.app_update_error_download
                            UpdateError.Foreign -> R.string.app_update_error_foreign
                            UpdateError.Install -> R.string.app_update_error_install
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

                // Что сказал Android — мелко: человеку это мало что скажет, поддержке — всё.
                if (state.errorDetail.isNotBlank()) {
                    Text(
                        text = state.errorDetail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.error == UpdateError.Install && !state.busy) {
                GlassTextButton(onClick = onSystemInstall, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.app_update_system_install))
                }
            }
        }

        // Android closes the app to replace it and does not let it reopen itself. People
        // without notifications get no “updated” notice, so the sheet says it beforehand.
        Text(
            text = stringResource(R.string.app_update_closes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (ask) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.app_update_auto_question),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.app_update_auto_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SheetButton(
            text = stringResource(if (ask) R.string.app_update_auto_yes else R.string.update),
            busyText = stringResource(
                if (state.installing) R.string.app_update_installing else R.string.app_update_downloading,
            ),
            busy = state.busy,
            enabled = true,
            onClick = if (ask) ({ onAuto(true) }) else onInstall,
            // Установка — кнопка залита целиком: скачивание позади.
            progress = if (state.installing) 1f else state.progress,
        )

        GlassTextButton(onClick = if (ask) ({ onAuto(false) }) else onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (ask) R.string.app_update_auto_no else R.string.not_now))
        }
    }
}

/**
 * What the version the app has just updated itself to brought. Opened from the “updated” card on
 * the home screen: the update closed the app, and here the person sees that it worked.
 */
@Composable
fun UpdatedSheet(open: Boolean, updated: Updated, onDismiss: () -> Unit, onClosed: () -> Unit) {
    DuckerSheet(
        open = open,
        title = stringResource(R.string.app_update_done_title, updated.version),
        busy = false,
        onDismiss = onDismiss,
        onClosed = onClosed,
    ) {
        Notes(updated.notes)

        SheetButton(
            text = stringResource(R.string.dismiss),
            busyText = "",
            busy = false,
            enabled = true,
            onClick = onDismiss,
        )
    }
}

/** “What's new” in the phone's language; in English when the release has no such text. */
@Composable
private fun Notes(notes: Map<String, String>) {
    val language = LocalConfiguration.current.locales[0].language
    val text = notes[language] ?: notes["en"].orEmpty()

    if (text.isNotBlank()) {
        val shape = RoundedCornerShape(20.dp)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.app_update_whats_new), style = MaterialTheme.typography.titleSmall)
            // On a white backing, like the fields of the other sheets.
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
                    .sheetSurface(shape)
                    .padding(16.dp),
            )
        }
    }
}
