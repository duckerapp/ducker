package com.github.kr328.clash.ui.subscriptions

import com.github.kr328.clash.ui.components.isolated
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.kr328.clash.R
import com.github.kr328.clash.ui.components.DuckerSheet
import com.github.kr328.clash.ui.components.SheetButton

/**
 * Вопрос перед удалением подписки.
 *
 * Нужен потому, что удаление необратимо и уносит с собой ссылку — а она у человека
 * часто только здесь. Одно случайное касание по кнопке рядом с «Обновить» стоило бы
 * ему доступа, и восстановить его можно только через поддержку провайдера.
 *
 * Панелью снизу, как добавление и правка (D-118): стандартное окно было последним
 * местом в стиле CMFA на этой вкладке.
 */
@Composable
fun DeleteSubscriptionDialog(
    open: Boolean,
    name: String,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onClosed: () -> Unit,
) {
    // Пульт (этап 6) встаёт на «Отмену»: лишнее нажатие ОК не должно стирать подписку.
    val cancel = remember { FocusRequester() }

    DuckerSheet(
        open = open,
        title = stringResource(R.string.delete_subscription, name.isolated()),
        busy = busy,
        start = cancel,
        onDismiss = onDismiss,
        onClosed = onClosed,
    ) {
        Text(
            text = stringResource(R.string.delete_subscription_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SheetButton(
            text = stringResource(R.string.delete),
            busyText = stringResource(R.string.deleting),
            busy = busy,
            enabled = true,
            onClick = onConfirm,
            danger = true,
        )

        TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.fillMaxWidth().focusRequester(cancel)) {
            Text(stringResource(R.string.cancel))
        }
    }
}
