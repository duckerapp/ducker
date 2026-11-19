package com.github.kr328.clash.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.R
import com.github.kr328.clash.core.model.ProxySort

/** Same setting and typography in the proxy tab and the quick picker. */
@Composable
internal fun ProxySortToggle(sort: ProxySort, onSort: (ProxySort) -> Unit) {
    val byDelay = sort == ProxySort.Delay
    TextButton(
        onClick = { onSort(if (byDelay) ProxySort.Default else ProxySort.Delay) },
        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Text(
            stringResource(if (byDelay) R.string.sort_delay else R.string.sort_default),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/** Group latency check, with its existing TV focus behavior and progress state. */
@Composable
internal fun ProxyCheckButton(
    checking: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    compact: Boolean = false,
) {
    val label = stringResource(if (checking) R.string.checking else R.string.check_delay)
    val active = enabled && !checking
    val backdrop = LocalGlassBackdrop.current
    val fill = MaterialTheme.colorScheme.surfaceContainer
    val container = if (backdrop != null) Color.Transparent else fill
    // The pane glass: the round check swells under a finger as Home's buttons do.
    val swell = rememberGlassPress()
    val press = if (compact) {
        Modifier
            .glassPress(swell?.takeIf { active }, own = true)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = if (swell != null) null else ripple(bounded = false, radius = 24.dp),
                enabled = keepFocus(active), role = Role.Button, onClickLabel = label,
            ) { if (active) onClick() }
    } else {
        Modifier
            .glassPress(swell?.takeIf { active }, own = true)
            .glass(backdrop, GlassMaterial.Control, CircleShape, press = swell)
            .clip(CircleShape)
            .background(container)
            // Keep focus on the remote while a check is running. The pane glass answers with its swell, not a ripple.
            .clickable(
                interactionSource = null,
                indication = if (swell != null) null else LocalIndication.current,
                enabled = keepFocus(active), role = Role.Button, onClickLabel = label,
            ) { if (active) onClick() }
    }
    val indicator: @Composable () -> Unit = {
        if (checking) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Icon(
                imageVector = Symbols.VitalSigns,
                contentDescription = null,
                modifier = Modifier.size(if (compact) 18.dp else 20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.4f),
            )
        }
    }
    Box(
        modifier = modifier.size(size).then(press)
            .semantics(mergeDescendants = compact) {
                contentDescription = label
                if (!active) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        if (compact) {
            Box(
                Modifier.size(44.dp)
                    .then(
                        if (backdrop != null) Modifier.glass(backdrop, GlassMaterial.Control, CircleShape, press = swell)
                        else Modifier.airShadow(CircleShape).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
                    ),
                contentAlignment = Alignment.Center,
            ) { indicator() }
        } else {
            indicator()
        }
    }
}
