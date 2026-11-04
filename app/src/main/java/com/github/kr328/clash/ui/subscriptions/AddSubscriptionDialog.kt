package com.github.kr328.clash.ui.subscriptions

import com.github.kr328.clash.ui.components.Symbols
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.R
import com.github.kr328.clash.ui.components.SheetField
import com.github.kr328.clash.ui.components.SheetButton
import com.github.kr328.clash.ui.components.DuckerSheet
import com.github.kr328.clash.ui.components.FitLabel
import com.github.kr328.clash.ui.components.LocalTv
import com.github.kr328.clash.ui.components.labelWeights
import com.github.kr328.clash.ui.components.airShadow
import io.nayuki.qrcodegen.QrCode

/**
 * Добавление подписки (D-17, D-99): ссылка, QR-код, вставка из буфера и одиночные ссылки
 * протоколов.
 *
 * Панелью снизу, в языке остального приложения: поле — как поиск узлов, «Вставить» и
 * «Сканировать QR» — плитками, «Добавить» — чёрной пилюлей, как кнопка на панели вкладок.
 * Прежнее окно было стандартным диалогом, оставшимся от CMFA: серые текстовые кнопки,
 * поле с рамкой, а на время загрузки всё просто серело, как будто зависло. Теперь
 * загрузку показывает сама кнопка — крутящимся кольцом и словами.
 *
 * Поля «имя» нет: у подписки провайдер и так присылает название заголовком
 * `profile-title`, а до первого обновления сойдёт домен. Лишний ввод на этом шаге —
 * лишний повод бросить его на полпути.
 *
 * На телевизоре (D-162) слева от ввода — QR: подписку присылает телефон, а пультом ссылку
 * набирают, только если телефона с ducker нет. «Сканировать QR» там нет: у телевизора нет камеры.
 */
@Composable
fun AddSubscriptionDialog(
    open: Boolean,
    value: String,
    /** [value] is only the beginning of a long text (F-268): it is shown, not edited. */
    longText: Boolean = false,
    error: String,
    busy: Boolean,
    /** Доля загрузки подписки, 0..1 (D-212); `null` — пока неизвестна. */
    progress: Float? = null,
    /** The core asks a backup address of the subscription (stage 21): the line under the button says so. */
    backup: Boolean = false,
    onValue: (String) -> Unit,
    onPaste: () -> Unit,
    onScan: () -> Unit,
    onFile: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onClosed: () -> Unit,
    /** Телевизор: что на месте QR. */
    receive: Receive? = null,
    onNewCode: () -> Unit = {},
) {
    val tv = LocalTv.current
    // Closing stops the reception at once and clears its code: the sheet, still sliding down, keeps the code it
    // showed, not an empty frame.
    val last = remember { arrayOf(receive) }
    if (open) last[0] = receive
    // «Готово» on a TV takes the frame to «Добавить» (D-377).
    val add = remember { FocusRequester() }

    DuckerSheet(
        open = open,
        title = stringResource(R.string.add_subscription),
        hint = stringResource(if (tv) R.string.receive_hint else R.string.subscription_link_hint),
        // Закрывается и во время загрузки — это её отмена: провайдер может не ответить вовсе.
        busy = false,
        onDismiss = onDismiss,
        onClosed = onClosed,
    ) {
        val manual: @Composable ColumnScope.() -> Unit = {
            SheetField(
                value = value,
                onValue = onValue,
                placeholder = stringResource(R.string.subscription_link_placeholder),
                enabled = !busy,
                error = error.isNotEmpty(),
                maxLines = 4,
                readOnly = longText,
                next = add,
                machine = true,
            )

            if (longText) {
                Text(
                    text = stringResource(R.string.subscription_input_long),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (error.isNotEmpty()) FailureText(error)

            SubscriptionHttpWarning(value)

            val sources = listOfNotNull(
                Triple(Symbols.ContentPaste, stringResource(R.string.paste), onPaste),
                Triple(Symbols.QrCodeScanner, stringResource(R.string.scan_qr), onScan).takeIf { !tv },
                Triple(Symbols.FileOpen, stringResource(R.string.add_from_file), onFile),
            )
            // Ширина — по длине подписей (D-114): «Сканировать QR» втрое длиннее «Файла».
            val weights = labelWeights(sources.map { it.second }, MaterialTheme.typography.labelLarge, TILE_PADDING)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sources.forEachIndexed { i, (icon, label, onClick) ->
                    SourceTile(icon, label, enabled = !busy, onClick = onClick, modifier = Modifier.weight(weights[i]))
                }
            }

            SheetButton(
                text = stringResource(R.string.add),
                busyText = stringResource(R.string.adding),
                busy = busy,
                enabled = value.isNotBlank(),
                progress = progress,
                onClick = onConfirm,
                modifier = Modifier.focusRequester(add),
            )

            // Только растворение: рост высоты под строку ведёт сама панель (DuckerSheet).
            AnimatedVisibility(
                visible = busy,
                enter = fadeIn(tween(260, delayMillis = 120)),
                exit = fadeOut(tween(120)),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Crossfade(backup, label = "addingHint") { trying ->
                        Text(
                            text = stringResource(if (trying) R.string.subscription_trying_backup else R.string.adding_hint),
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                }
            }
        }

        if (tv) {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                ReceiveCode(last[0], onNewCode, Modifier.size(CODE_SIZE))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp), content = manual)
            }
        } else {
            manual()
        }
    }
}

/** Место QR в панели «+» на телевизоре (D-162): код, пока ждём телефон, иначе — что с ним стало. */
@Composable
private fun ReceiveCode(receive: Receive?, onNewCode: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium

    if (receive is Receive.Waiting) {
        val qr = remember(receive.link) { QrCode.encodeText(receive.link, QrCode.Ecc.MEDIUM) }
        val label = stringResource(R.string.receive_code)
        val view = LocalView.current

        // Экран не гаснет, пока код показан: заставка закрыла бы его посреди сканирования.
        DisposableEffect(view) {
            view.keepScreenOn = true

            onDispose { view.keepScreenOn = false }
        }

        Box(modifier.clip(shape).qr(qr).semantics { contentDescription = label })

        return
    }

    Column(
        modifier = modifier.clip(shape).background(scheme.surface).border(1.dp, scheme.outlineVariant, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val text = when (receive) {
            Receive.Received -> R.string.receive_received
            Receive.NoNetwork -> R.string.receive_no_network
            Receive.Expired -> R.string.receive_expired
            else -> null
        }

        if (text != null) {
            Text(
                text = stringResource(text),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }

        if (receive == Receive.Expired) {
            TextButton(onClick = onNewCode) { Text(stringResource(R.string.receive_again)) }
        }
    }
}

/** «Вставить», «Сканировать QR» и «Файл» — плитками, а не серыми надписями (D-99). */
@Composable
internal fun SourceTile(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.airShadow(shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = TILE_PADDING).alpha(if (enabled) 1f else 0.5f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            FitLabel(label, MaterialTheme.typography.labelLarge)
        }
    }
}

private val TILE_PADDING = 8.dp
private val CODE_SIZE = 248.dp

/**
 * A download's failure under the link field: the explanation in the error colour, and the core's own text for an
 * unknown error under it small and grey, as on the subscription's card (D-227) — support needs it, the person not.
 * The explanation is always one line, so the first line break parts them.
 */
@Composable
internal fun FailureText(error: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = error.substringBefore('\n'),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )

        val detail = error.substringAfter('\n', "")

        if (detail.isNotBlank()) {
            Text(
                text = detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
