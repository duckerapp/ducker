package com.github.kr328.clash.ui.subscriptions

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.github.kr328.clash.R

@Composable
internal fun SubscriptionHttpWarning(address: String) {
    if (address.trim().startsWith("http://", ignoreCase = true)) {
        Text(
            text = stringResource(R.string.subscription_http_warning),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
