package com.github.kr328.clash.ui.subscriptions

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.R
import com.github.kr328.clash.service.model.SubscriptionAddress
import com.github.kr328.clash.ui.components.DuckerSheet
import com.github.kr328.clash.ui.components.Symbols
import com.github.kr328.clash.ui.components.airShadow
import java.text.DateFormat

/**
 * A subscription's addresses (Q-82): its own link and the provider's mirrors, each with the outcome of
 * its last try, and under the address the last resort tried — directly or through the node. Hosts only.
 * The outcomes say what happened, never «blocked»: the app cannot tell a block from a fault.
 */
@Composable
fun SubscriptionAddressesSheet(
    open: Boolean,
    addresses: List<SubscriptionAddress>,
    /** When they were last tried; 0 — not since the list was kept. */
    triedAt: Long,
    onDismiss: () -> Unit,
    onClosed: () -> Unit,
) {
    DuckerSheet(
        open = open,
        title = stringResource(R.string.subscription_addresses_title),
        hint = stringResource(R.string.subscription_addresses_hint),
        busy = false,
        onDismiss = onDismiss,
        onClosed = onClosed,
    ) {
        if (triedAt > 0) {
            Text(
                text = stringResource(
                    R.string.subscription_addresses_checked,
                    DateUtils.formatSameDayTime(triedAt, System.currentTimeMillis(), DateFormat.MEDIUM, DateFormat.SHORT).toString(),
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // One backing with inset lines, as the settings index and «Мои правила» (D-326).
        Column(
            modifier = Modifier.fillMaxWidth()
                .airShadow(RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(vertical = 4.dp),
        ) {
            addresses.forEachIndexed { index, address ->
                if (index > 0) {
                    HorizontalDivider(
                        Modifier.padding(start = TEXT_START, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    )
                }

                AddressRow(address)
            }
        }
    }
}

@Composable
private fun AddressRow(address: SubscriptionAddress) {
    val scheme = MaterialTheme.colorScheme
    // The address worked when it answered one way or another; it failed when every way it was tried failed.
    val worked = address.outcome == "ok" || address.resorts.any { it.outcome == "ok" }
    val failed = !worked && address.outcome.isFailure()

    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(ICON_GAP),
    ) {
        // On the line of the host, not in the middle of a tall row.
        Icon(
            imageVector = when {
                worked -> Symbols.CheckCircle
                failed -> Symbols.Cancel
                else -> Symbols.RadioButtonUnchecked
            },
            contentDescription = null,
            tint = when {
                failed -> scheme.error
                worked -> scheme.onSurface
                else -> scheme.onSurfaceVariant
            },
            modifier = Modifier.padding(top = 1.dp).size(ICON_SIZE),
        )

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = address.host,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            val role = stringResource(if (address.own) R.string.address_own else R.string.address_backup)
            Detail("$role · ${outcome(address.outcome, address.code)}")

            address.resorts.forEach { resort ->
                val way = stringResource(if (resort.via == "direct") R.string.address_direct else R.string.address_proxy)
                Detail("$way · ${outcome(resort.outcome, resort.code)}")
            }

            if (address.served) {
                Text(
                    text = stringResource(R.string.address_served),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun Detail(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun String.isFailure(): Boolean = this !in setOf("ok", "untried", "")

/** "" — not tried since the list was kept — reads as untried: the address was not needed. */
@Composable
private fun outcome(outcome: String, code: Int): String = when (outcome) {
    "ok" -> stringResource(R.string.address_ok)
    "connection" -> stringResource(R.string.address_connection)
    "timeout" -> pluralStringResource(R.plurals.address_timeout, code, code)
    "status" -> stringResource(R.string.address_status, code)
    "webpage" -> stringResource(R.string.address_webpage)
    "failed" -> stringResource(R.string.address_failed)
    else -> stringResource(R.string.address_untried)
}

private val ICON_SIZE = 22.dp
private val ICON_GAP = 14.dp

/** Where the text starts: the lines between rows begin there. */
private val TEXT_START = 16.dp + ICON_SIZE + ICON_GAP
