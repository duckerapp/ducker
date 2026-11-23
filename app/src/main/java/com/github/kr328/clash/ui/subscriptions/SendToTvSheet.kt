package com.github.kr328.clash.ui.subscriptions

import com.github.kr328.clash.ui.components.GlassTextButton
import androidx.compose.foundation.shape.RoundedCornerShape
import com.github.kr328.clash.ui.components.LocalFrosted
import com.github.kr328.clash.ui.components.LocalGlassBackdrop
import com.github.kr328.clash.ui.components.sheetSurface
import com.github.kr328.clash.ui.components.asName
import com.github.kr328.clash.ui.components.named
import com.github.kr328.clash.ui.components.Symbols
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
        // The glass style: a list on a backing and a sheet's button, as the other sheets; Air keeps its plain rows.
        val glass = LocalGlassBackdrop.current != null || LocalFrosted.current

        if (sent || profiles.isEmpty()) {
            if (glass) {
                SheetButton(text = stringResource(R.string.done), busyText = "", busy = false, enabled = true, onClick = onDismiss)
            } else GlassTextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.done))
            }

            return@DuckerSheet
        }

        Column(if (glass) Modifier.sheetSurface(RoundedCornerShape(20.dp)).padding(4.dp) else Modifier) {
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
                        text = profile.shownName.asName(),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge.named(),
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (selected) Icon(Symbols.Check, contentDescription = null)
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
