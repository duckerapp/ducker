package com.github.kr328.clash.ui.subscriptions

import com.github.kr328.clash.ui.components.GlassTextButton
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.kr328.clash.R
import com.github.kr328.clash.ui.components.DuckerSheet
import com.github.kr328.clash.ui.components.SheetButton

/**
 * Перед обновлением подписки, чей конфиг правили вручную (D-125): обновление заменит его
 * конфигом провайдера, и правка пропадёт молча — поэтому спрашиваем и говорим, как её сберечь.
 */
@Composable
fun UpdateEditedDialog(
    open: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onClosed: () -> Unit,
) {
    DuckerSheet(
        open = open,
        title = stringResource(R.string.update_edited_title),
        busy = false,
        onDismiss = onDismiss,
        onClosed = onClosed,
    ) {
        Text(
            text = stringResource(R.string.update_edited_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SheetButton(
            text = stringResource(R.string.update_edited_confirm),
            busyText = "",
            busy = false,
            enabled = true,
            onClick = onConfirm,
            danger = true,
        )

        GlassTextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cancel))
        }
    }
}
