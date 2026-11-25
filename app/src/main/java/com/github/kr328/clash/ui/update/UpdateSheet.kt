package com.github.kr328.clash.ui.update

import com.github.kr328.clash.ui.components.GlassTextButton
import com.github.kr328.clash.ui.components.sheetSurface
import androidx.compose.foundation.layout.Arrangement
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.github.kr328.clash.ui.components.LocalTv
import com.github.kr328.clash.ui.components.TvKeyScroll
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
    // Android refused ducker's own install (MIUI, F-630): the main button hands the file to Android's installer.
    val refused = !ask && state.error == UpdateError.Install && !state.busy

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
            // On a backing like «Что нового»: what went wrong and what to do reads as one block.
            Column(
                modifier = Modifier.fillMaxWidth().sheetSurface(RoundedCornerShape(20.dp)).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(
                        when (state.error) {
                            UpdateError.Download -> R.string.app_update_error_download
                            UpdateError.Foreign -> R.string.app_update_error_foreign
                            UpdateError.Install -> R.string.app_update_error_install
                        }
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )

                if (refused) {
                    Text(
                        text = stringResource(R.string.app_update_refused_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

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
            text = stringResource(
                when {
                    ask -> R.string.app_update_auto_yes
                    refused -> R.string.app_update_system_install
                    else -> R.string.update
                },
            ),
            busyText = stringResource(
                if (state.installing) R.string.app_update_installing else R.string.app_update_downloading,
            ),
            busy = state.busy,
            enabled = true,
            onClick = when {
                ask -> ({ onAuto(true) })
                refused -> onSystemInstall
                else -> onInstall
            },
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
    val tv = LocalTv.current

    if (text.isNotBlank()) {
        val shape = RoundedCornerShape(20.dp)
        // On a TV the text scrolls inside its card, at most part of the screen tall; it took no focus, so a long «What's
        // new» could not be read. As the config editor's code (D-373): the frame lands on the card, OK lets the arrows
        // scroll it, Back puts the frame back, and from the frame Down leads straight to the buttons.
        val scroll = rememberScrollState()
        val scope = rememberCoroutineScope()
        val keyScroll = remember(scroll, scope) { TvKeyScroll(scroll, scope) }
        var reading by remember { mutableStateOf(false) }

        BackHandler(enabled = reading) { reading = false }
        val room = if (tv) Modifier.heightIn(max = (LocalConfiguration.current.screenHeightDp * TV_NOTES_HEIGHT).dp) else Modifier

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.app_update_whats_new), style = MaterialTheme.typography.titleSmall)
            // On a white backing, like the fields of the other sheets.
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
                    .then(room)
                    .sheetSurface(shape)
                    .then(
                        if (tv) Modifier
                            .onFocusChanged { if (!it.isFocused) reading = false }
                            .onKeyEvent { event ->
                                if (!reading || event.type != KeyEventType.KeyDown) return@onKeyEvent false

                                val step = scroll.viewportSize / 3f

                                // While reading the arrows stay in the text, at its ends too, as the editor's cursor.
                                when (event.key) {
                                    Key.DirectionUp -> true.also { if (scroll.canScrollBackward) keyScroll.by(-step) }
                                    Key.DirectionDown -> true.also { if (scroll.canScrollForward) keyScroll.by(step) }
                                    else -> false
                                }
                            }
                            // Even a short text takes focus: the sheet opens on it with its title in view, as a page is read.
                            .clickable(interactionSource = null, indication = null) { reading = !reading }
                            .verticalScroll(scroll)
                        else Modifier,
                    )
                    .padding(16.dp),
            )
        }
    }
}

private const val TV_NOTES_HEIGHT = 0.4f
