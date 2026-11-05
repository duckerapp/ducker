package com.github.kr328.clash.ui.apps

import com.github.kr328.clash.ui.components.navigationRoom
import com.github.kr328.clash.ui.components.tvEdge
import com.github.kr328.clash.ui.components.keepFocus
import com.github.kr328.clash.ui.components.TvLazyColumn
import com.github.kr328.clash.ui.components.Symbols
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.github.kr328.clash.R
import com.github.kr328.clash.service.model.AccessControlMode
import com.github.kr328.clash.ui.components.DuckerCard as Card
import com.github.kr328.clash.ui.components.ScreenHeader
import com.github.kr328.clash.ui.components.Pill
import com.github.kr328.clash.ui.components.PillSegments
import com.github.kr328.clash.ui.components.SearchField
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
    // «Готово» of the search on a TV takes the frame to the first app (D-377).
    val firstApp = remember { FocusRequester() }

    TvLazyColumn(
        state = list,
        overscrollEffect = overscrollFor(list),
        modifier = modifier.fillMaxSize().screenInsets().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 16.dp + tvEdge(), bottom = 16.dp + tvEdge() + navigationRoom()),
    ) {
        item {
            ScreenHeader(stringResource(R.string.access_control), onBack = onBack)
        }

        // Два режима: «Все» — это «Без VPN» без отметок (D-139). The switch waits for the stored mode, or its
        // pill would ride from the first segment as the screen opens (D-326).
        if (state.mode != null) item {
            val modes = listOf(AccessControlMode.DenySelected, AccessControlMode.AcceptSelected)

            PillSegments(
                labels = listOf(stringResource(R.string.apps_mode_except), stringResource(R.string.apps_mode_only)),
                selected = modes.indexOf(state.mode),
                onSelect = { onMode(modes[it]) },
            )
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
                            imageVector = Symbols.Lock,
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
                SearchField(value = state.query, onValue = onQuery, hint = stringResource(R.string.apps_search), next = firstApp)
            }

            // The count and two pills on one line instead of a card with a switch and two text buttons
            // (D-326). One pill for both bulk actions: it clears while anything shown is marked.
            item {
                val marked = state.apps.any { !it.locked && it.packageName in state.selected }

                CountBesidePills(
                    modifier = Modifier.padding(start = 4.dp),
                    count = {
                        Text(
                            text = stringResource(R.string.apps_selected, state.selected.size, state.apps.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    pills = {
                        Row(horizontalArrangement = Arrangement.spacedBy(PILLS_GAP)) {
                            // Smaller rather than «Tout sé…» when the two do not fit a narrow phone's line.
                            Pill(
                                text = stringResource(R.string.apps_system_short),
                                onClick = { onShowSystem(!state.showSystem) },
                                selected = state.showSystem,
                                role = Role.Switch,
                                fit = true,
                            )
                            Pill(
                                text = stringResource(if (marked) R.string.apps_select_none else R.string.apps_select_all),
                                onClick = if (marked) onSelectNone else onSelectAll,
                                enabled = state.apps.isNotEmpty(),
                                fit = true,
                            )
                        }
                    },
                )
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
                    modifier = if (app === state.apps.first()) Modifier.focusRequester(firstApp) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun AppItem(app: AppRow, checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth()
                // The rows the config decided stand first; on a TV they take the remote too and do nothing on OK.
                // Skipped, they made the remote search past them all: the frame stalled and leapt over them, and
                // past a few dozen it never reached the list.
                .toggleable(
                    value = checked, enabled = keepFocus(!app.locked), role = Role.Checkbox,
                    onValueChange = { if (!app.locked) onToggle() },
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppIcon(app.packageName, 36.dp, Modifier.clip(MaterialTheme.shapes.small))

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
                        imageVector = Symbols.Lock,
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
                    imageVector = if (checked) Symbols.CheckCircle else Symbols.RadioButtonUnchecked,
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
internal fun AppIcon(packageName: String, size: Dp, modifier: Modifier) {
    val context = LocalContext.current
    // As many pixels as it is drawn in: a fixed 96 px was stretched at 4K, where 36 dp take 144 (F-364).
    val px = with(LocalDensity.current) { size.roundToPx() }
    val key = "$packageName@$px"

    // From the cache at once: a row scrolled back into view, or the same app on many rows of «Подключения», drew an
    // empty square for a frame before its icon.
    val icon by produceState(icons[key], key) {
        value = icons[key] ?: withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName)
                    .toBitmap(width = px, height = px)
                    .asImageBitmap()
            }.getOrNull()
        }?.also { icons.put(key, it) }
    }

    val bitmap = icon

    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = modifier.size(size))
    } else {
        Box(modifier.size(size))
    }
}

/** The icons drawn lately, by package and size; a few megabytes, about a hundred phone-sized icons. */
private val icons = object : LruCache<String, ImageBitmap>(4 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}

private val PILLS_GAP = 8.dp

/**
 * The count beside the pills, taking the room they leave, as long as that room holds the count's longest word. Under
 * the largest font on a narrow phone it did not, and the count stood one letter per line: then it goes over the
 * pills. Wherever the row fits nothing changes.
 */
@Composable
private fun CountBesidePills(count: @Composable () -> Unit, pills: @Composable () -> Unit, modifier: Modifier = Modifier) {
    Layout(contents = listOf(count, pills), modifier = modifier) { (counts, pillRows), constraints ->
        val gap = PILLS_GAP.roundToPx()
        val width = constraints.maxWidth
        val row = pillRows.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val room = width - row.width - gap
        val text = counts.first()

        if (room >= text.minIntrinsicWidth(Constraints.Infinity)) {
            val line = text.measure(Constraints(minWidth = room, maxWidth = room))
            val height = maxOf(line.height, row.height)

            layout(width, height) {
                line.placeRelative(0, (height - line.height) / 2)
                row.placeRelative(width - row.width, (height - row.height) / 2)
            }
        } else {
            val line = text.measure(Constraints(maxWidth = width))

            layout(width, line.height + gap + row.height) {
                line.placeRelative(0, 0)
                row.placeRelative(0, line.height + gap)
            }
        }
    }
}
