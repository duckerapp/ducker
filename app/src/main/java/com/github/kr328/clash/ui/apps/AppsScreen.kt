package com.github.kr328.clash.ui.apps

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.github.kr328.clash.R
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.ui.components.DuckerCard as Card
import com.github.kr328.clash.ui.components.FitLabel
import com.github.kr328.clash.ui.components.ScreenHeader
import com.github.kr328.clash.ui.components.overscrollFor
import com.github.kr328.clash.ui.components.screenInsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Какие приложения идут через туннель (этап 8.3, замена экрана CMFA).
 *
 * Выбор применяется не сразу: список приложений задаётся при создании туннеля, поэтому
 * сохранение и перезапуск ядра делает вызывающий экран при выходе.
 */
@Composable
fun AppsScreen(
    state: AppsState,
    onMode: (AccessControlMode) -> Unit,
    onToggle: (String) -> Unit,
    onQuery: (String) -> Unit,
    onShowSystem: (Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val choosing = state.mode != null

    val list = rememberLazyListState()

    LazyColumn(
        state = list,
        overscrollEffect = overscrollFor(list),
        modifier = modifier.fillMaxSize().screenInsets().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item {
            ScreenHeader(stringResource(R.string.access_control), onBack = onBack)
        }

        item {
            // Два режима: «Все» — это «Без VPN» без отметок (D-139). Подписи короткие — чипы
            // по их ширине, как у темы и языка; делить строку, как в D-114, больше незачем.
            val modes = listOf(
                AccessControlMode.DenySelected to stringResource(R.string.apps_mode_except),
                AccessControlMode.AcceptSelected to stringResource(R.string.apps_mode_only),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                modes.forEach { (value, label) -> Mode(state.mode, value, label, onMode) }
            }
        }

        if (state.mode != null) item {
            Text(
                text = stringResource(
                    if (state.mode == AccessControlMode.AcceptSelected) R.string.apps_mode_only_hint
                    else R.string.apps_mode_except_hint
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.profileExcluded > 0 || state.profileIncluded > 0) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = if (state.profileIncluded > 0) {
                                stringResource(
                                    R.string.apps_profile_include,
                                    state.profileIncluded, state.profileInstalled,
                                )
                            } else {
                                stringResource(
                                    R.string.apps_profile_exclude,
                                    state.profileExcluded, state.profileInstalled,
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (choosing) {
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQuery,
                    singleLine = true,
                    label = { Text(stringResource(R.string.apps_search)) },
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.toggleable(
                                value = state.showSystem, role = Role.Switch, onValueChange = onShowSystem,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.apps_system),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Switch(checked = state.showSystem, onCheckedChange = null)
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(
                                    R.string.apps_selected,
                                    state.selected.size,
                                    state.apps.size,
                                ),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = onSelectAll) {
                                Text(stringResource(R.string.apps_select_all),
                                    style = MaterialTheme.typography.labelMedium)
                            }
                            TextButton(onClick = onSelectNone) {
                                Text(stringResource(R.string.apps_select_none),
                                    style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            if (state.loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            items(state.apps, key = AppRow::packageName) { app ->
                AppItem(
                    app = app,
                    checked = app.packageName in state.selected,
                    onToggle = { onToggle(app.packageName) },
                )
            }
        }
    }
}

@Composable
private fun Mode(
    current: AccessControlMode?,
    value: AccessControlMode,
    label: String,
    onMode: (AccessControlMode) -> Unit,
) {
    FilterChip(
        selected = current == value,
        onClick = { onMode(value) },
        label = { FitLabel(label, MaterialTheme.typography.labelMedium) },
        shape = MaterialTheme.shapes.extraLarge,
    )
}

@Composable
private fun AppItem(app: AppRow, checked: Boolean, onToggle: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .toggleable(value = checked, enabled = !app.locked, role = Role.Checkbox, onValueChange = { onToggle() })
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIcon(app.packageName, Modifier.size(36.dp).clip(MaterialTheme.shapes.small))

            Column(Modifier.weight(1f)) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (app.locked) {
                // Не показываем галочку, которая ничего не делает: вместо неё — что решил
                // конфиг. Снять это человек не может, в том и смысл списка провайдера.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(
                            if (app.inTunnel) R.string.apps_locked_tunnel
                            else R.string.apps_locked_direct
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // Круглая отметка вместо квадратного флажка (этап 13); нажатие ловит вся строка.
                Icon(
                    imageVector = if (checked) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(4.dp),
                )
            }
        }
    }
}

/**
 * Иконка грузится на строку, а не списком заранее: приложений сотни, а видно на экране
 * десяток, и держать все значки в памяти незачем.
 */
@Composable
internal fun AppIcon(packageName: String, modifier: Modifier) {
    val context = LocalContext.current

    val icon by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName)
                    .toBitmap(width = 96, height = 96)
                    .asImageBitmap()
            }.getOrNull()
        }
    }

    val bitmap = icon

    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier)
    } else {
        Box(modifier)
    }
}
