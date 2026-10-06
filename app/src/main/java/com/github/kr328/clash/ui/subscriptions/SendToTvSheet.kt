package com.github.kr328.clash.ui.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.R
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.ui.components.DuckerSheet
import com.github.kr328.clash.ui.components.SheetButton
import java.util.UUID

/**
 * Отправка подписки на телевизор (D-162): сканер узнал его код. Активная подписка уже выбрана —
 * отправка одним касанием; остальные со ссылкой — списком. Телевизор принял — панель говорит об
 * этом и ждёт «Готово»; не принял или не ответил — почему и что сделать.
 */
@Composable
fun SendToTvSheet(
    open: Boolean,
    /** Подписки со ссылкой: свой конфиг без ссылки телевизору скачать неоткуда. */
    profiles: List<Profile>,
    chosen: UUID?,
    error: String,
    busy: Boolean,
    sent: Boolean,
    onChoose: (UUID) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onClosed: () -> Unit,
) {
    DuckerSheet(
        open = open,
        title = stringResource(R.string.send_tv_title),
        hint = stringResource(
            when {
                sent -> R.string.send_tv_done
                profiles.isEmpty() -> R.string.send_tv_empty
                else -> R.string.send_tv_hint
            }
        ),
        busy = busy,
        onDismiss = onDismiss,
        onClosed = onClosed,
    ) {
        if (sent || profiles.isEmpty()) {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.done))
            }

            return@DuckerSheet
        }

        Column {
            profiles.forEach { profile ->
                val selected = profile.uuid == chosen

                Row(
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(MaterialTheme.shapes.small)
                        .then(if (selected) Modifier.background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)) else Modifier)
                        .selectable(selected = selected, enabled = !busy, role = Role.RadioButton, onClick = { onChoose(profile.uuid) })
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = profile.shownName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (selected) Icon(Icons.Default.Check, contentDescription = null)
                }
            }
        }

        if (error.isNotEmpty()) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        SheetButton(
            text = stringResource(R.string.send_tv),
            busyText = stringResource(R.string.send_tv_busy),
            busy = busy,
            enabled = chosen != null,
            onClick = onConfirm,
        )
    }
}
