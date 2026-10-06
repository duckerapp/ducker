package com.github.kr328.clash.ui.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.github.kr328.clash.R
import com.github.kr328.clash.ui.components.DuckerSheet
import com.github.kr328.clash.ui.components.SheetButton

/**
 * Своя панель перед системным окном VPN при первом подключении. Системное окно пишет только,
 * что приложение «сможет отслеживать трафик», и человек жмёт «Отмена». Панель говорит, что
 * сейчас спросят и зачем; «Продолжить» — системное окно. Показывается один раз, как панель
 * «работа в фоне» (D-109).
 */
@Composable
fun VpnSheet(
    open: Boolean,
    onContinue: () -> Unit,
    onDismiss: () -> Unit,
    onClosed: () -> Unit,
) {
    DuckerSheet(
        open = open,
        title = stringResource(R.string.vpn_sheet_title),
        busy = false,
        onDismiss = onDismiss,
        onClosed = onClosed,
    ) {
        Text(
            text = stringResource(R.string.vpn_sheet_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SheetButton(
            text = stringResource(R.string.vpn_sheet_continue),
            busyText = "",
            busy = false,
            enabled = true,
            onClick = onContinue,
        )

        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cancel))
        }
    }
}
