package com.github.kr328.clash.ui.subscriptions

import com.github.kr328.clash.ui.components.TvLazyColumn
import com.github.kr328.clash.ui.components.LocalTv
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import com.github.kr328.clash.ui.components.tabEntry
import com.github.kr328.clash.ui.components.keepFocus
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.semantics
import androidx.compose.material.icons.filled.Tune
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.github.kr328.clash.ui.components.DuckerSheet
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.kr328.clash.R
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.ui.components.ACTION_SIZE
import com.github.kr328.clash.ui.components.BrandAvatar
import com.github.kr328.clash.ui.components.EmptyState
import com.github.kr328.clash.ui.components.PullToRefresh
import com.github.kr328.clash.ui.components.ScreenHeader
import com.github.kr328.clash.ui.components.Skeleton
import com.github.kr328.clash.ui.components.ThinBar
import com.github.kr328.clash.ui.components.FitLabel
import com.github.kr328.clash.ui.components.UpdateLabel
import com.github.kr328.clash.ui.components.UpdateShown
import com.github.kr328.clash.ui.components.rememberUpdateShown
import com.github.kr328.clash.common.util.providerLink
import com.github.kr328.clash.log.LogScrubber
import com.github.kr328.clash.service.util.Failure
import com.github.kr328.clash.ui.components.formatBytes
import com.github.kr328.clash.ui.components.formatDate
import com.github.kr328.clash.ui.components.overscrollFor
import com.github.kr328.clash.ui.components.screenInsets
import java.util.UUID
import com.github.kr328.clash.ui.components.DuckerCard as Card

/**
 * Третья вкладка: подписки (этап 5).
 */
@Composable
fun SubscriptionsScreen(
    state: SubscriptionsState,
    onSelect: (Profile) -> Unit,
    onUpdate: (UUID) -> Unit,
    onEdit: (Profile) -> Unit,
    onDelete: (Profile) -> Unit,
    onAdd: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier,
    /** «Поддержка» на карточке ошибки обновления — открыть ссылку провайдера (D-227). */
    onSupport: (String) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
    /** Идёт то, что запустило «потянуть, чтобы обновить» (D-92). */
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    /** Подписку [from] перетащили на место [to] (этап 13, пакет 5). */
    onMove: (from: UUID, to: UUID) -> Unit = { _, _ -> },
    /** Перестановка кончилась — запомнить порядок. */
    onMoved: () -> Unit = {},
) {
    val list = rememberLazyListState()
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val keys by rememberUpdatedState(state.profiles.mapTo(HashSet()) { it.uuid.toString() })
    val move by rememberUpdatedState(onMove)
    val moved by rememberUpdatedState(onMoved)
    val reorder = remember(list) {
        ProfileReorder(
            list = list,
            scope = scope,
            isProfile = { it in keys },
            onMove = { from, to -> move(UUID.fromString(from), UUID.fromString(to)) },
            onDrop = { moved() },
        )
    }

    // Действия неактивной подписки — панелью по «⋯» (D-121). Выбранное действие запускаем,
    // когда панель уехала: правка и удаление открывают свои панели, и две разом наезжали бы.
    var menuFor by remember { mutableStateOf<Profile?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var afterMenu by remember { mutableStateOf<(() -> Unit)?>(null) }

    // QR ссылки (этап 10): панель живёт в композиции, пока уезжает вниз.
    var shareFor by remember { mutableStateOf<Profile?>(null) }
    var shareOpen by remember { mutableStateOf(false) }
    val share = { profile: Profile ->
        shareFor = profile
        shareOpen = true
    }

    shareFor?.let { profile ->
        ShareSubscriptionSheet(
            open = shareOpen,
            name = profile.shownName,
            link = profile.source,
            onDismiss = { shareOpen = false },
            onClosed = { shareFor = null },
        )
    }

    menuFor?.let { profile ->
        ProfileMenu(
            open = menuOpen,
            name = profile.shownName,
            local = profile.local,
            onAction = { action ->
                afterMenu = when (action) {
                    MenuAction.Update -> ({ onUpdate(profile.uuid) })
                    MenuAction.Share -> ({ share(profile) })
                    MenuAction.Edit -> ({ onEdit(profile) })
                    MenuAction.Delete -> ({ onDelete(profile) })
                }
                menuOpen = false
            },
            onDismiss = { menuOpen = false },
            onClosed = {
                menuFor = null
                afterMenu?.invoke()
                afterMenu = null
            },
        )
    }

    PullToRefresh(
        refreshing = refreshing,
        onRefresh = onRefresh,
        top = contentPadding.calculateTopPadding(),
        modifier = modifier.fillMaxSize(),
    ) {
        TvLazyColumn(
            state = list,
            overscrollEffect = overscrollFor(list),
            modifier = Modifier.fillMaxSize().screenInsets(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            // Отступы списка, а не самого экрана: тогда карточки уходят под плавающую
            // панель вкладок и видны в просветах по её бокам (D-74).
            contentPadding = PaddingValues(
                start = 16.dp + contentPadding.calculateStartPadding(LocalLayoutDirection.current),
                end = 16.dp + contentPadding.calculateEndPadding(LocalLayoutDirection.current),
                top = 16.dp + contentPadding.calculateTopPadding(),
                // Плюс над панелью (D-97): с этим запасом последнюю карточку можно
                // докрутить выше кнопки.
                bottom = 16.dp + contentPadding.calculateBottomPadding() + ACTION_SIZE + 12.dp,
            ),
        ) {
            item {
                // От заголовка до первой карточки — как на «Прокси», где под заголовком ещё
                // стоит строка режимов (без VPN — пустая): два шага списка, а не один (D-104).
                ScreenHeader(stringResource(R.string.subscriptions_tab), Modifier.padding(bottom = 12.dp))
            }

            // Заглушки, а не бесконечная полоска (D-92): так же, как на главной.
            if (!state.loaded) {
                items(2) { ProfileSkeleton() }
            }
            if (state.updateError.isNotEmpty()) {
                item {
                    val failed = state.profiles.firstOrNull { it.uuid == state.updateErrorFor }
                    val fix = state.updateFailure?.kind?.fix

                    UpdateErrorCard(
                        name = failed?.shownName,
                        reason = state.updateError,
                        failure = state.updateFailure,
                        // Что поможет именно при этой неудаче (D-227): подписка с карточки — та же.
                        action = when {
                            failed == null -> null
                            fix == Failure.Fix.Retry -> stringResource(R.string.update) to { onUpdate(failed.uuid) }
                            fix == Failure.Fix.EditLink -> stringResource(R.string.edit) to { onEdit(failed) }
                            fix == Failure.Fix.Support -> providerLink(failed.supportUrl)?.let { link ->
                                stringResource(R.string.support) to { onSupport(link) }
                            }
                            else -> null
                        },
                        onDismiss = onDismissError,
                    )
                }
            }

            itemsIndexed(state.profiles, key = { _, it -> it.uuid.toString() }) { index, profile ->
                val key = profile.uuid.toString()
                val above = state.profiles.getOrNull(index - 1)
                val below = state.profiles.getOrNull(index + 1)
                val up = stringResource(R.string.move_up)
                val down = stringResource(R.string.move_down)

                ProfileCard(
                    // Соседи расступаются плавно только при перестановке: в остальное время сдвиг
                    // спорил бы с ростом карточки при смене активной (D-121). Поднятая и
                    // доезжающая карточки едут сами.
                    modifier = Modifier.animateItem(
                        fadeInSpec = null,
                        fadeOutSpec = null,
                        placementSpec = if (reorder.active && reorder.dragged != key && reorder.settling != key) {
                            spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)
                        } else null,
                    ),
                    reorder = reorder,
                    onLift = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                    onStep = { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick) },
                    // TalkBack перетаскивать не умеет — те же перестановки действиями.
                    moves = listOfNotNull(
                        above?.let { CustomAccessibilityAction(up) { onMove(profile.uuid, it.uuid); onMoved(); true } },
                        below?.let { CustomAccessibilityAction(down) { onMove(profile.uuid, it.uuid); onMoved(); true } },
                    ),
                    profile = profile,
                    updating = state.updating == profile.uuid,
                    failed = state.updateError.isNotEmpty() && state.updateErrorFor == profile.uuid,
                    rules = state.ruleCounts[profile.uuid] ?: 0,
                    onSelect = { onSelect(profile) },
                    onUpdate = { onUpdate(profile.uuid) },
                    onEdit = { onEdit(profile) },
                    onDelete = { onDelete(profile) },
                    onShare = { share(profile) },
                    onMenu = {
                        menuFor = profile
                        menuOpen = true
                    },
                )
            }

            if (state.loaded && state.profiles.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Subscriptions,
                        title = stringResource(R.string.ducker_no_subscription),
                        message = stringResource(R.string.subscriptions_empty),
                    )
                }
            }

            // Карточки «Настройки приложения» здесь больше нет (D-98): настройки открывает
            // шестерёнка на главной, а карточку в конце списка закрывал «плюс» (D-97).
            // Идентификатор устройства переехал в настройки, в самый низ (D-103).
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileCard(
    modifier: Modifier,
    reorder: ProfileReorder,
    onLift: () -> Unit,
    onStep: () -> Unit,
    moves: List<CustomAccessibilityAction>,
    profile: Profile,
    updating: Boolean,
    /** Последнее обновление этой подписки не удалось — «Не удалось» на её кнопке. */
    failed: Boolean,
    /** Свои правила подписки — маленьким значком у имени (D-123). */
    rules: Int,
    onSelect: () -> Unit,
    onUpdate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onMenu: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val selected = profile.active

    // Выбранная подписка заливается, как выбранный узел на «Прокси» (D-101): чёрным в светлой
    // теме, белым в тёмной. Прежде её отличала только точка переключателя в углу. Отказ
    // панели важнее выбора — такая карточка остаётся красной.
    //
    // Заливка переливается, а текст меняет цвет ровно на середине: если перекрашивать оба
    // плавно, они сходятся в сером, и на миг текст пропадает.
    val fill by animateFloatAsState(if (selected) 1f else 0f, tween(240), label = "profileFill")
    val filled = fill > 0.5f

    val container = when {
        profile.hwidBlocked -> scheme.errorContainer
        else -> lerp(scheme.surface, scheme.primaryContainer, fill)
    }
    val content = when {
        profile.hwidBlocked -> scheme.onErrorContainer
        filled -> scheme.onPrimaryContainer
        else -> scheme.onSurface
    }
    val border = if (profile.hwidBlocked) scheme.outlineVariant
    else lerp(scheme.outlineVariant, scheme.primary, fill)

    // Долгое нажатие поднимает карточку (этап 13, пакет 5): тень и чуть крупнее, поверх соседей.
    val key = profile.uuid.toString()
    val lifted = reorder.dragged == key
    val lift by animateFloatAsState(if (lifted) 1f else 0f, tween(LIFT_MS), label = "lift")
    val shape = MaterialTheme.shapes.large

    // Пульт (этап 6): фокус, взятый карточкой, к её кнопкам уже не пускает — Compose не водит
    // стрелками внутрь элемента, который сам в фокусе. Активной он и не нужен: нажатие по ней
    // ничего не делает, — фокус сразу у её кнопок. У остальных ОК — выбрать, вправо — «⋯».
    val self = remember { FocusRequester() }
    val menu = remember { FocusRequester() }
    val tv = LocalTv.current

    Card(
        // Выбор — касанием по карточке, как узел на «Прокси». Повторное касание по уже
        // выбранной ничего не делает: смена профиля перезагружает конфиг в работающем туннеле.
        modifier = modifier.fillMaxWidth()
            .zIndex(if (lifted || reorder.settling == key) 1f else 0f)
            .graphicsLayer {
                translationY = reorder.offset(key)
                scaleX = 1f + LIFT_SCALE * lift
                scaleY = scaleX
                shadowElevation = LIFT_SHADOW.toPx() * lift
                this.shape = shape
            }
            .clip(shape)
            // Пульт входит на «Подписки» на активную (этап 6).
            .then(if (selected) Modifier.tabEntry() else Modifier)
            .focusRequester(self)
            .focusProperties {
                if (!tv) return@focusProperties

                canFocus = !selected
                if (!selected) right = menu
            }
            .selectable(selected = selected, role = Role.RadioButton, onClick = { if (!selected) onSelect() })
            .semantics { customActions = moves }
            .reorderable(reorder, key, scale = { 1f + LIFT_SCALE * lift }, onLift = onLift, onStep = onStep),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        border = BorderStroke(1.dp, border),
    ) {
        // Полная карточка — только у активной подписки; остальные — строкой (D-121): при
        // пяти подписках огромные карточки заставляли листать. Смена активной — перетеканием
        // высоты, а не скачком.
        Column(
            modifier = Modifier.animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = if (selected) 4.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BrandAvatar(profile.brandLogo, Modifier.size(if (selected) 44.dp else 36.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = profile.shownName,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        if (rules > 0) RulesBadge(rules)
                    }

                    if (!selected) {
                        Text(
                            text = profileSummary(profile, updating),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (!profile.imported) scheme.error
                            else LocalContentColor.current.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Домена под названием больше нет (D-102): название и так узнаётся,
                    // а адрес провайдера на скриншоте в поддержку ни к чему. Строка остаётся
                    // только для отказа — у него своя причина: лимит устройств и «панель
                    // требует идентификатор» — разные вещи, и чинятся по-разному.
                    if (profile.hwidBlocked || profile.hwidReason == "not_supported") {
                        Text(
                            text = stringResource(
                                if (profile.hwidReason == "not_supported") R.string.limit_hwid_required
                                else R.string.limit_max_devices
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalContentColor.current.copy(alpha = 0.7f),
                        )
                    }
                }

                if (!selected) {
                    IconButton(onClick = onMenu, modifier = Modifier.focusRequester(menu).focusProperties { if (tv) left = self }) {
                        Icon(Icons.Default.MoreHoriz, contentDescription = stringResource(R.string.more_actions))
                    }
                } else if (!profile.local) {
                    // У активной меню нет — её действия в карточке; «поделиться» — в свободном углу.
                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.QrCode2, contentDescription = stringResource(R.string.share_qr))
                    }
                }
            }

            if (selected) ActiveDetails(profile, updating, failed, filled, fill, onUpdate, onEdit, onDelete)
        }
    }
}

/** У подписки есть свои правила: значок и число, в цвет текста карточки (D-123). */
@Composable
private fun RulesBadge(count: Int) {
    val color = LocalContentColor.current

    Row(
        modifier = Modifier.clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Default.Tune, contentDescription = stringResource(R.string.rules_title), modifier = Modifier.size(12.dp))
        Text(count.toString(), style = MaterialTheme.typography.labelSmall)
    }
}

/** Строка неактивной подписки под именем: срок и трафик одной строкой (D-121). */
@Composable
private fun profileSummary(profile: Profile, updating: Boolean): String {
    if (!profile.imported) return stringResource(R.string.profile_not_loaded)
    if (updating) return stringResource(R.string.updating)
    if (profile.local) return stringResource(R.string.profile_local)

    val used = profile.upload + profile.download
    val parts = listOfNotNull(
        profile.expire.takeIf { it > 0 }?.let { stringResource(R.string.valid_until, formatDate(it)) },
        // Один вид у всех: «50 ГБ из 200 ГБ», без лимита — «50 ГБ из ∞».
        when {
            profile.total > 0 -> "${formatBytes(used)} ${stringResource(R.string.ducker_traffic_of, formatBytes(profile.total))}"
            used > 0 -> "${formatBytes(used)} ${stringResource(R.string.ducker_traffic_of, UNLIMITED)}"
            profile.expire <= 0 -> stringResource(R.string.traffic_unlimited)
            else -> null
        },
    )

    return parts.joinToString(" · ")
}

/** Подробности активной подписки: срок, трафик и три действия. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveDetails(
    profile: Profile,
    updating: Boolean,
    failed: Boolean,
    filled: Boolean,
    fill: Float,
    onUpdate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Подписка, которая ни разу не загрузилась, лежит в списке наравне с рабочими,
        // и раньше отличалась от них только нулевым трафиком (D-83). Говорим прямо:
        // цифр у неё нет и не будет, пока не починят ссылку.
        val loaded = profile.imported

        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(
                    if (loaded) R.string.ducker_active else R.string.profile_not_loaded
                ),
                style = MaterialTheme.typography.labelMedium,
                // На залитой карточке красный теряется, там хватает самих слов.
                color = if (!loaded && !filled) scheme.error else LocalContentColor.current,
            )
            if (loaded && profile.expire > 0 && !profile.local) {
                Text(stringResource(R.string.valid_until, formatDate(profile.expire)),
                    style = MaterialTheme.typography.labelMedium)
            }
        }
        if (loaded && profile.local) {
            // Свой конфиг: трафика и срока у него нет, счётчик «0 Б» только путал (D-125).
            Text(
                stringResource(R.string.profile_local),
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            )
        } else if (loaded) {
            val used = profile.upload + profile.download

            Text(
                stringResource(R.string.ducker_traffic_usage, formatBytes(used),
                    if (profile.total > 0) formatBytes(profile.total) else UNLIMITED),
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            )

            // Шкала вместо разделительной линии (D-102): та же, что на главной, только
            // в цвет текста карточки — белая на залитой, чёрная на обычной.
            if (profile.total > 0) {
                ThinBar(
                    fraction = (used.toFloat() / profile.total).coerceIn(0f, 1f),
                    color = LocalContentColor.current,
                    trackColor = LocalContentColor.current.copy(alpha = 0.15f),
                )
            }
        } else {
            Text(
                stringResource(R.string.profile_not_loaded_hint),
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.7f),
            )
        }
        // Три действия одной строкой вместо карандаша в углу (D-101). Удаление — только
        // значком: оно редкое и всё равно переспрашивает, а место нужно двум частым.
        //
        // Кнопки залиты (D-102): прозрачные с тонкой обводкой сливались с чёрной
        // карточкой. На выбранной — белые (в тёмной теме — чёрные), на обычной — серые,
        // как «Обновить» на главной; перетекают вместе с заливкой карточки.
        val action = when {
            profile.hwidBlocked -> scheme.surface
            else -> lerp(scheme.surfaceVariant, scheme.onPrimaryContainer, fill)
        }
        val onAction = when {
            profile.hwidBlocked -> scheme.onSurface
            else -> lerp(scheme.onSurface, scheme.primaryContainer, fill)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            // Своему конфигу обновляться неоткуда: «Обновить» шло по пустой ссылке с ошибкой.
            // Итог — в самой кнопке, как на главной (D-119): «Обновлено» или «Не удалось».
            if (!profile.local) {
                val shown = rememberUpdateShown(updating, failed)

                val active = !updating && shown != UpdateShown.Busy

                // С пульта на время обновления кнопка держит фокус (D-158).
                CardAction(onClick = { if (active) onUpdate() }, container = action, content = onAction,
                    enabled = keepFocus(active), modifier = Modifier.weight(1f)) {
                    UpdateLabel(shown, LocalTextStyle.current, 18.dp)
                }
            }

            CardAction(onClick = onEdit, container = action, content = onAction, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                FitLabel(stringResource(R.string.edit), LocalTextStyle.current, Modifier.padding(start = 6.dp))
            }

            CardAction(onClick = onDelete, container = action, content = onAction, modifier = Modifier.width(48.dp)) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.delete),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private enum class MenuAction { Update, Share, Edit, Delete }

/** Действия неактивной подписки — панелью снизу (D-121). */
@Composable
private fun ProfileMenu(
    open: Boolean,
    name: String,
    local: Boolean,
    onAction: (MenuAction) -> Unit,
    onDismiss: () -> Unit,
    onClosed: () -> Unit,
) {
    DuckerSheet(open = open, title = name, busy = false, onDismiss = onDismiss, onClosed = onClosed) {
        Column {
            if (!local) MenuLine(Icons.Default.Refresh, stringResource(R.string.update)) { onAction(MenuAction.Update) }
            if (!local) MenuLine(Icons.Default.QrCode2, stringResource(R.string.share_qr)) { onAction(MenuAction.Share) }
            MenuLine(Icons.Outlined.Edit, stringResource(R.string.edit)) { onAction(MenuAction.Edit) }
            MenuLine(Icons.Default.DeleteOutline, stringResource(R.string.delete), MaterialTheme.colorScheme.error) {
                onAction(MenuAction.Delete)
            }
        }
    }
}

@Composable
private fun MenuLine(
    icon: ImageVector,
    text: String,
    color: Color = LocalContentColor.current,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color)
        Text(text, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

/** Кнопка внутри карточки подписки: залитая пилюля, цвета задаёт карточка. */
@Composable
private fun CardAction(
    onClick: () -> Unit,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            // Выключается только «Обновить» на время обновления — и не сереет, как на главной:
            // кольцо и так говорит, что идёт.
            disabledContainerColor = container,
            disabledContentColor = content,
        ),
        content = label,
    )
}

/**
 * Обновление не удалось (D-83) — белой карточкой, как строки списка: красная плашка с текстовой
 * кнопкой оставалась от CMFA. Красный — только значок. Адреса из причины вычищены, как в карточке
 * «нет связи» на главной: экран первым уходит скриншотом в поддержку.
 */
@Composable
private fun UpdateErrorCard(
    name: String?,
    reason: String,
    failure: Failure?,
    action: Pair<String, () -> Unit>?,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(scheme.error.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = scheme.error, modifier = Modifier.size(20.dp))
            }

            Column(Modifier.weight(1f).padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    // Подписка обновилась или поменяли настройку, а ядро новый конфиг не приняло
                    // (D-207): «не удалось обновить» тут неправда — VPN работает на прежнем.
                    text = when {
                        failure?.kind == Failure.Kind.Reload -> stringResource(R.string.config_reload_failed_title)
                        name != null -> stringResource(R.string.update_failed_named, name)
                        else -> stringResource(R.string.update_failed)
                    },
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = LogScrubber.scrub(reason),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )

                // Текст ядра для незнакомой ошибки — мелко: человеку он не нужен, поддержке нужен.
                if (!failure?.detail.isNullOrBlank()) {
                    Text(
                        text = LogScrubber.scrub(failure!!.detail),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }

                action?.let { (label, onClick) ->
                    TextButton(onClick = onClick, contentPadding = PaddingValues(0.dp)) { Text(label) }
                }
            }

            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.dismiss), tint = scheme.onSurfaceVariant)
            }
        }
    }
}

/** Заглушка карточки подписки (D-92). */
@Composable
private fun ProfileSkeleton() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Skeleton(height = 44.dp, width = 44.dp)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Skeleton(height = 18.dp, width = 160.dp)
                    Skeleton(height = 12.dp, width = 120.dp)
                }
            }

            Skeleton(height = 14.dp, width = 200.dp)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Skeleton(Modifier.weight(1f), height = 40.dp)
                Skeleton(Modifier.weight(1f), height = 40.dp)
            }
        }
    }
}

/**
 * «Плюс» над панелью вкладок, по центру (D-97): владелец вернул его из шапки — там он был
 * неожиданным и далеко от пальца. Цвета подсветки панели: чёрный кружок в светлой теме,
 * белый в тёмной. Только вид: нажатие и подпись для TalkBack — у панели, полем шире
 * кружка (D-115).
 */
@Composable
fun AddSubscriptionButton() {
    Box(
        modifier = Modifier.size(ACTION_SIZE)
            .shadow(6.dp, CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

private const val UNLIMITED = "∞"

/** Поднятая карточка: насколько крупнее, какая тень, за сколько поднимается. */
private const val LIFT_SCALE = 0.03f
private val LIFT_SHADOW = 12.dp
private const val LIFT_MS = 150
