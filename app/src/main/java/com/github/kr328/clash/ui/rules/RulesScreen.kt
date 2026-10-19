package com.github.kr328.clash.ui.rules

import com.github.kr328.clash.ui.components.Symbols
import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import java.util.Locale
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.R
import com.github.kr328.clash.service.model.UserRule
import com.github.kr328.clash.service.util.PackageFilter
import com.github.kr328.clash.ui.apps.AppIcon
import com.github.kr328.clash.ui.apps.AppRow
import com.github.kr328.clash.ui.apps.loadApps
import com.github.kr328.clash.ui.components.DuckerSheet
import com.github.kr328.clash.ui.components.EmptyState
import com.github.kr328.clash.ui.components.IconBadge
import com.github.kr328.clash.ui.components.LeaveSheet
import com.github.kr328.clash.ui.components.ScreenHeader
import com.github.kr328.clash.ui.components.SheetButton
import com.github.kr328.clash.ui.components.SheetField
import com.github.kr328.clash.ui.components.overscrollFor
import com.github.kr328.clash.ui.components.screenInsets
import com.github.kr328.clash.ui.components.DuckerCard as Card
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.key
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.github.kr328.clash.ui.components.Pill
import com.github.kr328.clash.ui.components.PillSegments
import com.github.kr328.clash.ui.components.airShadow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Свои правила подписки (D-123): сайт, приложение или адрес — мимо VPN, через него или
 * в блок. Встают перед правилами провайдера и переживают обновление подписки. Новое
 * правило — панелью снизу.
 */
@Composable
fun RulesScreen(
    name: String,
    rules: List<UserRule>,
    bypass: Bypass?,
    onAdd: (UserRule) -> Unit,
    onRemove: (UserRule) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var adding by remember { mutableStateOf(false) }
    // Панель живёт в композиции, пока уезжает вниз, как и остальные (D-99).
    var addShown by remember { mutableStateOf(false) }

    if (addShown) {
        AddRuleSheet(
            open = adding,
            rules = rules,
            bypass = bypass,
            onAdd = {
                onAdd(it)
                adding = false
            },
            onDismiss = { adding = false },
            onClosed = { addShown = false },
        )
    }

    val list = rememberLazyListState()

    LazyColumn(
        state = list,
        overscrollEffect = overscrollFor(list),
        modifier = modifier.fillMaxSize().screenInsets().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item { ScreenHeader(stringResource(R.string.rules_title), onBack = onBack) }

        item {
            Text(
                text = stringResource(R.string.rules_hint, name),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (rules.isEmpty()) {
            item {
                EmptyState(
                    icon = Symbols.Tune,
                    title = stringResource(R.string.rules_empty_title),
                    message = stringResource(R.string.rules_empty),
                )
            }
        } else {
            // The rows on one backing with inset lines, as «Подписки» and the settings index (D-326).
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        rules.forEachIndexed { index, rule ->
                            key(rule) {
                                if (index > 0) {
                                    HorizontalDivider(
                                        Modifier.padding(start = 72.dp, end = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                    )
                                }

                                RuleRow(rule, bypass, onRemove = { onRemove(rule) })
                            }
                        }
                    }
                }
            }
        }

        item {
            SheetButton(
                text = stringResource(R.string.rules_add),
                busyText = "",
                busy = false,
                enabled = true,
                icon = Symbols.Add,
                onClick = {
                    adding = true
                    addShown = true
                },
            )
        }
    }
}

/**
 * Название приложения — не в главном потоке (аудит 9в.9): `loadLabel` читает ресурсы чужого APK,
 * и с десятком таких правил экран открывался с рывком. Найденное запоминается, чтобы строка,
 * вернувшаяся в список, не мигала именем пакета.
 */
@Composable
private fun appLabel(packageName: String): String {
    val context = LocalContext.current
    val label by produceState(appLabels[packageName] ?: packageName, packageName) {
        value = withContext(Dispatchers.IO) {
            val pm = context.packageManager

            runCatching { pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString() }.getOrDefault(packageName)
        }
        appLabels[packageName] = value
    }

    return label
}

private val appLabels = HashMap<String, String>()

/** Категории `geosite.dat` читаются один раз на процесс: это 4 МБ из ресурсов (аудит 9б.8). */
private var siteCategories: Set<String>? = null

@Composable
private fun RuleRow(rule: UserRule, bypass: Bypass?, onRemove: () -> Unit) {
    val context = LocalContext.current
    // Приложение ушло мимо туннеля уже после того, как правило добавили (провайдер или
    // «Приложения»): до правил ядра оно не доходит — говорим об этом прямо в строке.
    val outside = if (rule.type == UserRule.Type.App) bypass?.reason(rule.value) else null

    // Название, а не код: «ru.rostel», «category-ru» и «RU» человеку ничего не скажут.
    val label = if (rule.type == UserRule.Type.App) appLabel(rule.value) else remember(rule) {
        when (rule.type) {
            UserRule.Type.Geosite -> categoryLabel(context, rule.value)
            UserRule.Type.Geoip -> Locale("", rule.value).displayCountry.ifEmpty { rule.value }
            else -> rule.value
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (rule.type == UserRule.Type.App) {
            AppIcon(rule.value, Modifier.size(44.dp).clip(MaterialTheme.shapes.medium))
        } else {
            IconBadge(typeIcon(rule.type))
        }

        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = stringResource(typeLabel(rule.type)) + " · " + stringResource(targetLabel(rule.target)),
                style = MaterialTheme.typography.bodySmall,
                color = if (rule.target == UserRule.Target.Block) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (outside != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(
                        Symbols.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        stringResource(
                            if (outside == Bypass.Reason.Provider) R.string.rule_app_provider
                            else R.string.rule_app_access
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        IconButton(onClick = onRemove) {
            Icon(
                Symbols.Close,
                contentDescription = stringResource(R.string.rule_remove),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** The kind's icon: in the list's rows and on the new rule's tiles alike. */
private fun typeIcon(type: UserRule.Type) = when (type) {
    UserRule.Type.Site -> Symbols.Language
    UserRule.Type.App -> Symbols.Apps
    UserRule.Type.Ip -> Symbols.Lan
    UserRule.Type.Keyword -> Symbols.TextFields
    UserRule.Type.Geosite -> Symbols.Category
    UserRule.Type.Geoip -> Symbols.Flag
}

/**
 * Кто при подписке идёт мимо туннеля — своё правило на такое приложение не сработает (D-135).
 * [provider] — только конфиг провайдера, [all] — он же с выбором в «Приложениях»; считает тот же
 * [PackageFilter], что и туннель.
 */
data class Bypass(val provider: PackageFilter.Decision, val all: PackageFilter.Decision) {
    enum class Reason { Provider, Access }

    fun reason(packageName: String): Reason? = when {
        !PackageFilter.inTunnel(packageName, provider) -> Reason.Provider
        !PackageFilter.inTunnel(packageName, all) -> Reason.Access
        else -> null
    }
}

private fun typeLabel(type: UserRule.Type) = when (type) {
    UserRule.Type.Site -> R.string.rule_site
    UserRule.Type.App -> R.string.rule_app
    UserRule.Type.Ip -> R.string.rule_ip
    UserRule.Type.Keyword -> R.string.rule_keyword
    UserRule.Type.Geosite -> R.string.rule_geosite
    UserRule.Type.Geoip -> R.string.rule_geoip
}

/** Частые категории сайтов — подсказками, пока поле пустое (D-126). */
private val POPULAR_SITES = listOf("category-ru", "youtube", "telegram", "openai", "discord", "instagram", "tiktok", "category-ads-all")

/** Частые страны — подсказками. */
private val COUNTRIES = listOf("RU", "BY", "KZ")

/** Категория словами: сервис — своим именем, сборная — по-русски; прочие — как в базе. */
private fun categoryLabel(context: Context, code: String) = when (code) {
    "category-ru" -> context.getString(R.string.rule_geosite_ru)
    "category-ads-all" -> context.getString(R.string.rule_geosite_ads)
    else -> BRANDS[code] ?: code
}

private val BRANDS = mapOf(
    "youtube" to "YouTube",
    "telegram" to "Telegram",
    "openai" to "ChatGPT",
    "discord" to "Discord",
    "instagram" to "Instagram",
    "tiktok" to "TikTok",
)

private fun targetLabel(target: UserRule.Target) = when (target) {
    UserRule.Target.Direct -> R.string.rule_direct
    UserRule.Target.Proxy -> R.string.rule_proxy
    UserRule.Target.Block -> R.string.rule_block
}

@Composable
private fun AddRuleSheet(
    open: Boolean,
    rules: List<UserRule>,
    bypass: Bypass?,
    onAdd: (UserRule) -> Unit,
    onDismiss: () -> Unit,
    onClosed: () -> Unit,
) {
    val context = LocalContext.current
    var type by remember { mutableStateOf(UserRule.Type.Site) }
    var input by remember { mutableStateOf("") }
    var app by remember { mutableStateOf<String?>(null) }
    var target by remember { mutableStateOf(UserRule.Target.Direct) }
    // Введённое правило молча не бросаем: закрыть — значит сначала спросить (D-135).
    var asking by remember { mutableStateOf(false) }
    val entered = if (type == UserRule.Type.App) app != null else input.isNotBlank()

    if (asking) {
        LeaveSheet(
            title = stringResource(R.string.rule_unsaved),
            hint = stringResource(R.string.rule_unsaved_hint),
            leave = stringResource(R.string.rule_discard),
            onLeave = {
                asking = false
                onDismiss()
            },
            onStay = { asking = false },
        )
    }

    // Категории — из базы geosite в самом приложении: неизвестную ядро пропустит молча, а
    // человек должен увидеть опечатку сразу (D-126).
    val categories by produceState(siteCategories) {
        if (value == null) {
            value = withContext(Dispatchers.IO) {
                runCatching { context.assets.open("geosite.dat").use { geositeCategories(it.readBytes()) } }.getOrNull()
            }
            siteCategories = value
        }
    }

    // Приложение, выбранное до ответа службы, могло оказаться под замком. Категория без списка не
    // проходит: неизвестную ядро молча пропустит (D-126), и правило с опечаткой просто не сработает.
    val value = if (type == UserRule.Type.App) app?.takeIf { bypass?.reason(it) == null }
    else ruleValue(type, input)?.takeIf { type != UserRule.Type.Geosite || categories?.contains(it) == true }

    // Подсказки категорий: пока пусто — частые, по мере ввода — подходящие из базы.
    val needle = input.trim().lowercase()
    val codes = remember(categories, needle) {
        if (needle.isEmpty()) POPULAR_SITES
        else categories.orEmpty().filter { needle in it }
            .sortedWith(compareBy({ !it.startsWith(needle) }, { it.length }, { it }))
            .take(SUGGESTIONS)
    }
    // Недописанная категория с подходящими подсказками — ещё не ошибка.
    val choosing = type == UserRule.Type.Geosite && value == null && needle.isNotEmpty() && codes.isNotEmpty()
    // Список категорий ещё читается — «Добавить» ждёт, но ошибкой это не называем.
    val invalid = type != UserRule.Type.App && input.isNotBlank() && value == null && !choosing &&
        !(type == UserRule.Type.Geosite && categories == null)

    fun choose(next: UserRule.Type) {
        if (next == type) return
        type = next
        input = ""
    }

    DuckerSheet(
        open = open,
        title = stringResource(R.string.rule_new),
        busy = false,
        onAsk = if (entered) ({ asking = true }) else null,
        onDismiss = onDismiss,
        onClosed = onClosed,
    ) {
        Labeled(stringResource(R.string.rule_what)) {
            KindTiles(selected = type, onSelect = ::choose)
        }

        if (type == UserRule.Type.App) {
            AppPicker(
                selected = app,
                existing = remember(rules) { rules.filter { it.type == UserRule.Type.App }.associate { it.value to it.target } },
                bypass = bypass,
                onSelect = { app = it },
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SheetField(
                    value = input,
                    onValue = { input = it },
                    placeholder = stringResource(
                        when (type) {
                            UserRule.Type.Site -> R.string.rule_site_hint
                            UserRule.Type.Keyword -> R.string.rule_keyword_hint
                            UserRule.Type.Geosite -> R.string.rule_geosite_hint
                            UserRule.Type.Geoip -> R.string.rule_geoip_hint
                            else -> R.string.rule_ip_hint
                        },
                    ),
                    enabled = true,
                    error = invalid,
                )

                val note = when {
                    invalid -> when (type) {
                        UserRule.Type.Site -> R.string.rule_invalid_site
                        UserRule.Type.Keyword -> R.string.rule_invalid_keyword
                        UserRule.Type.Geosite -> R.string.rule_invalid_geosite
                        UserRule.Type.Geoip -> R.string.rule_invalid_geoip
                        else -> R.string.rule_invalid_ip
                    }
                    choosing -> R.string.rule_geosite_choose
                    type == UserRule.Type.Geosite -> R.string.rule_geosite_about
                    type == UserRule.Type.Geoip -> R.string.rule_geoip_about
                    else -> null
                }

                if (note != null) {
                    Text(
                        text = stringResource(note),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (type == UserRule.Type.Geosite) {
                    Suggestions(codes.map { it to categoryLabel(context, it) }, selected = value, onPick = { input = it })
                }

                if (type == UserRule.Type.Geoip) {
                    Suggestions(
                        COUNTRIES.map { it to Locale("", it).displayCountry },
                        selected = value,
                        onPick = { input = it },
                    )
                }
            }
        }

        Labeled(stringResource(R.string.rule_where)) {
            val targets = listOf(UserRule.Target.Direct, UserRule.Target.Proxy, UserRule.Target.Block)

            PillSegments(
                labels = targets.map { stringResource(targetLabel(it)) },
                selected = targets.indexOf(target),
                onSelect = { target = targets[it] },
            )
        }

        SheetButton(
            text = stringResource(R.string.add),
            busyText = "",
            busy = false,
            enabled = value != null,
            onClick = { value?.let { onAdd(UserRule(type, it, target)) } },
        )
    }
}

/** Готовые значения пилюлями: нажал — оно в поле. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Suggestions(options: List<Pair<String, String>>, selected: String?, onPick: (String) -> Unit) {
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (code, label) ->
            Pill(text = label, onClick = { onPick(code) }, selected = code == selected, role = Role.RadioButton)
        }
    }
}

/** Шесть видов правила плитками со значками, по три в ряд (D-326): значки те же, что у строк списка. */
@Composable
private fun KindTiles(selected: UserRule.Type, onSelect: (UserRule.Type) -> Unit) {
    val kinds = listOf(
        UserRule.Type.Site to R.string.rule_site,
        UserRule.Type.App to R.string.rule_app,
        UserRule.Type.Ip to R.string.rule_ip,
        UserRule.Type.Keyword to R.string.rule_keyword,
        UserRule.Type.Geosite to R.string.rule_geosite,
        UserRule.Type.Geoip to R.string.rule_geoip,
    )

    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        kinds.chunked(3).forEach { row ->
            // A label on two lines makes its tile taller, and the row's other tiles follow it.
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (kind, label) ->
                    KindTile(
                        icon = typeIcon(kind),
                        label = stringResource(label),
                        selected = kind == selected,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onClick = { onSelect(kind) },
                    )
                }
            }
        }
    }
}

@Composable
private fun KindTile(icon: ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium
    val fill by animateColorAsState(if (selected) scheme.primaryContainer else scheme.surface, tween(TILE_FADE_MS), label = "kindFill")
    val ink by animateColorAsState(if (selected) scheme.onPrimaryContainer else scheme.onSurface, tween(TILE_FADE_MS), label = "kindInk")

    Column(
        modifier = modifier.heightIn(min = 76.dp)
            .airShadow(shape)
            .clip(shape)
            .background(fill)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = ink, modifier = Modifier.size(24.dp))
        Text(label, color = ink, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2)
    }
}

/**
 * Выбор приложения поиском: приложений сотни, листать все не хочется. Идущие мимо туннеля — с
 * замком и причиной: правило на них не сработает. У приложения с правилом видно, какое оно, —
 * новое его заменит (D-135).
 */
@Composable
private fun AppPicker(
    selected: String?,
    existing: Map<String, UserRule.Target>,
    bypass: Bypass?,
    onSelect: (String) -> Unit,
) {
    val context = LocalContext.current
    val apps by produceState<List<AppRow>?>(null) {
        value = withContext(Dispatchers.IO) {
            loadApps(context).sortedWith(compareBy<AppRow> { it.system }.thenBy { it.label.lowercase() })
        }
    }
    var query by remember { mutableStateOf("") }

    SheetField(
        value = query,
        onValue = { query = it },
        placeholder = stringResource(R.string.rule_find_app),
        enabled = true,
    )

    Box(Modifier.fillMaxWidth().height(PICKER_HEIGHT), contentAlignment = Alignment.Center) {
        val shown = apps

        if (shown == null) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            val needle = query.trim()
            val filtered = remember(shown, needle) {
                if (needle.isEmpty()) shown
                else shown.filter { it.label.contains(needle, true) || it.packageName.contains(needle, true) }
            }

            LazyColumn(Modifier.fillMaxSize()) {
                items(filtered, key = { it.packageName }) { app ->
                    val chosen = app.packageName == selected
                    val outside = bypass?.reason(app.packageName)
                    val rule = existing[app.packageName]
                    val note = when {
                        outside == Bypass.Reason.Provider -> stringResource(R.string.rule_app_provider)
                        outside == Bypass.Reason.Access -> stringResource(R.string.rule_app_access)
                        rule != null -> stringResource(R.string.rule_app_exists, stringResource(targetLabel(rule)))
                        else -> null
                    }

                    // The chosen row is filled like the chosen subscription (D-101): the former grey
                    // was next to invisible on the sheet in both themes. The fill flows in, and the
                    // text changes color exactly halfway.
                    val fill by animateFloatAsState(if (chosen) 1f else 0f, tween(240), label = "appFill")
                    val filled = fill > 0.5f
                    val ink = when {
                        filled -> MaterialTheme.colorScheme.onPrimaryContainer
                        outside == null -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val quiet = if (filled) ink else MaterialTheme.colorScheme.onSurfaceVariant

                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = fill))
                            // No press wave: it is tinted for the unfilled row, played over the fresh fill
                            // and dimmed the text for a third of a second (D-292). The fill is the answer.
                            .selectable(
                                selected = chosen,
                                interactionSource = null,
                                indication = null,
                                enabled = outside == null,
                                role = Role.RadioButton,
                            ) { onSelect(app.packageName) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        AppIcon(
                            app.packageName,
                            Modifier.size(32.dp).clip(MaterialTheme.shapes.small).alpha(if (outside == null) 1f else DIMMED),
                        )

                        Column(Modifier.weight(1f)) {
                            Text(
                                app.label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )

                            if (note != null) {
                                Text(
                                    note,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = quiet,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        if (outside != null) {
                            Icon(
                                Symbols.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = quiet,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Labeled(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        content()
    }
}

private val PICKER_HEIGHT = 260.dp
private const val DIMMED = 0.5f
private const val SUGGESTIONS = 8
private const val TILE_FADE_MS = 150
