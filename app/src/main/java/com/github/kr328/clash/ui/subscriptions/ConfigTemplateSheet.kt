package com.github.kr328.clash.ui.subscriptions

import com.github.kr328.clash.ui.components.sheetSurface
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.R
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.ui.components.DuckerSheet
import com.github.kr328.clash.ui.components.Symbols
import com.github.kr328.clash.ui.components.isolated

/** The choices of «Конфигурация»: the service's registry (`ConfigTemplate.all`) with its words; "" — «Из подписки». */
private class TemplateOption(val id: String, @StringRes val title: Int, @StringRes val hint: Int)

private val OPTIONS = listOf(
    TemplateOption("", R.string.config_template_source, R.string.config_template_source_hint),
    TemplateOption("ducker-ru", R.string.config_template_ducker_ru, R.string.config_template_ducker_ru_hint),
)

/**
 * What the subscription is set to, for its «⋯» line and the active card: the chosen template, and «не применено» when
 * the one in effect differs (a choice stored before the first download, or a crash between the swap and the choice).
 */
@Composable
internal fun templateLabel(profile: Profile): String {
    val title = stringResource(OPTIONS.firstOrNull { it.id == profile.templateRequested }?.title ?: R.string.config_template_source)

    return if (profile.templateRequested == profile.template) title
    else title + stringResource(R.string.separator) + stringResource(R.string.config_template_not_applied)
}

/**
 * «Конфигурация» of one URL subscription. A choice runs at once and the sheet waits for it: closed by the view model on
 * success, kept with the reason on failure, and then the mark is back on what is in effect.
 */
@Composable
internal fun ConfigTemplateSheet(
    open: Boolean,
    name: String,
    /** The choice stored for the subscription. */
    chosen: String,
    /** Being switched to; `null` — nothing running. */
    switching: String?,
    error: String,
    edited: Boolean,
    onChoose: (String) -> Unit,
    onDismiss: () -> Unit,
    onClosed: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val marked = switching ?: chosen

    DuckerSheet(
        open = open,
        title = stringResource(R.string.config_template),
        hint = name.isolated(),
        busy = switching != null,
        onDismiss = onDismiss,
        onClosed = onClosed,
    ) {
        // One backing with inset lines, as «Адреса подписки».
        Column(
            modifier = Modifier.fillMaxWidth()
                .sheetSurface(RoundedCornerShape(20.dp))
                .padding(vertical = 4.dp),
        ) {
            OPTIONS.forEachIndexed { index, option ->
                if (index > 0) {
                    HorizontalDivider(
                        Modifier.padding(start = TEXT_START, end = 16.dp),
                        color = scheme.outlineVariant.copy(alpha = 0.45f),
                    )
                }

                val selected = option.id == marked
                val running = option.id == switching
                val applying = stringResource(R.string.config_template_applying)

                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        // Not disabled while a switch runs: the remote would lose its place and come back without a
                        // ring; the view model ignores a second choice meanwhile.
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            // The one in effect again does nothing; a choice not applied yet is tried again.
                            onClick = { onChoose(option.id) },
                        )
                        .then(if (running) Modifier.semantics { stateDescription = applying } else Modifier)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(ICON_GAP),
                ) {
                    if (running) {
                        CircularProgressIndicator(Modifier.padding(top = 1.dp).size(ICON_SIZE), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = if (selected) Symbols.CheckCircle else Symbols.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (selected) scheme.primary else scheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 1.dp).size(ICON_SIZE),
                        )
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(option.title),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        Text(
                            text = stringResource(option.hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (error.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Symbols.WarningAmber, contentDescription = null, tint = scheme.error, modifier = Modifier.size(18.dp))
                Text(error, style = MaterialTheme.typography.bodyMedium, color = scheme.error)
            }
        } else if (edited) {
            Text(
                text = stringResource(R.string.config_template_edited),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

private val ICON_SIZE = 22.dp
private val ICON_GAP = 14.dp

/** Where the text starts: the line between the rows begins there. */
private val TEXT_START = 16.dp + ICON_SIZE + ICON_GAP
