package com.github.kr328.clash.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    onDismiss: () -> Unit,
    onClosed: () -> Unit,
) {
    DuckerSheet(
        open = open,
        title = stringResource(R.string.app_update_title, release.version),
        busy = false,
        onDismiss = onDismiss,
        onClosed = onClosed,
    ) {
        val language = LocalConfiguration.current.locales[0].language
        val notes = release.notes[language] ?: release.notes["en"].orEmpty()

        if (notes.isNotBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = stringResource(R.string.app_update_whats_new), style = MaterialTheme.typography.titleSmall)
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

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
        }

        // Android closes the app to replace it and does not let it reopen itself. People
        // without notifications get no “updated” notice, so the sheet says it beforehand.
        Text(
            text = stringResource(R.string.app_update_closes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SheetButton(
            text = stringResource(R.string.update),
            busyText = stringResource(
                if (state.installing) R.string.app_update_installing else R.string.app_update_downloading,
            ),
            busy = state.busy,
            enabled = true,
            onClick = onInstall,
            // Установка — кнопка залита целиком: скачивание позади.
            progress = if (state.installing) 1f else state.progress,
        )

        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.not_now))
        }
    }
}
