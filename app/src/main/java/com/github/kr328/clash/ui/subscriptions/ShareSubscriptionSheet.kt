package com.github.kr328.clash.ui.subscriptions

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.R
import com.github.kr328.clash.ui.components.DuckerSheet
import io.nayuki.qrcodegen.QrCode

/**
 * QR-код ссылки подписки (этап 10): перенести подписку на второй телефон или телевизор, не
 * набирая ссылку. Сканер ducker (D-88) читает его как ссылку, введённую руками.
 */
@Composable
fun ShareSubscriptionSheet(
    open: Boolean,
    name: String,
    link: String,
    onDismiss: () -> Unit,
    onClosed: () -> Unit,
) {
    // Ссылку вписывал человек: слишком длинную для QR (больше ~2 КБ) не рисуем, а не падаем.
    val qr = remember(link) { runCatching { QrCode.encodeText(link, QrCode.Ecc.MEDIUM) }.getOrNull() }

    DuckerSheet(open = open, title = name, busy = false, onDismiss = onDismiss, onClosed = onClosed) {
        if (qr != null) {
            Box(
                Modifier.align(Alignment.CenterHorizontally)
                    .widthIn(max = 280.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.medium)
                    .qr(qr),
            )
        }

        Text(
            text = stringResource(R.string.share_qr_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.share_qr_warning),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.done))
        }
    }
}

/**
 * Чёрное на белом с полем в четыре клетки — и в тёмной теме: светлые клетки на тёмном фоне
 * читают не все сканеры. Клетка — целое число пикселей, иначе края клеток размываются.
 */
internal fun Modifier.qr(qr: QrCode) = drawWithCache {
    val cells = qr.size + 8
    val cell = (size.width / cells).toInt().toFloat()
    val start = (size.width - cell * qr.size) / 2
    val path = Path()

    for (y in 0 until qr.size) for (x in 0 until qr.size) {
        if (qr.getModule(x, y)) {
            path.addRect(Rect(start + x * cell, start + y * cell, start + (x + 1) * cell, start + (y + 1) * cell))
        }
    }

    onDrawBehind {
        drawRect(Color.White)
        drawPath(path, Color.Black)
    }
}
