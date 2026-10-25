package com.github.kr328.clash.ui.subscriptions

import com.github.kr328.clash.ui.components.Symbols
import com.github.kr328.clash.ui.components.TvLazyColumn
import com.github.kr328.clash.ui.components.LocalTv
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import com.github.kr328.clash.ui.components.tabEntry
import com.github.kr328.clash.ui.components.keepFocus
import com.github.kr328.clash.ui.components.ringCorner
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.material3.IconButton
import com.github.kr328.clash.ui.components.Pill
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.github.kr328.clash.ui.components.DuckerSheet
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
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
import com.github.kr328.clash.common.util.firstProviderLink
import com.github.kr328.clash.common.util.providerLink
import com.github.kr328.clash.log.LogScrubber
import com.github.kr328.clash.service.util.Failure
import com.github.kr328.clash.ui.components.formatBytes
import com.github.kr328.clash.ui.components.formatDate
import com.github.kr328.clash.ui.components.overscrollFor
import com.github.kr328.clash.ui.components.screenInsets
import com.github.kr328.clash.ui.components.airShadow
import java.util.UUID
import com.github.kr328.clash.ui.components.DuckerCard as Card

/**
 * Третья вкладка: подписки (этап 5).
 *
 * The active subscription is a card on top; the others are compact Air cards under
 * the caption «Другие подписки». The model hands them over in order: the
 * active one, favourites, the rest.
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
    /** Открыть ссылку провайдера: «Поддержка» на карточке ошибки (D-227), «Продлить» и «Поддержка» в «⋯». */
    onOpenLink: (String) -> Unit = {},
    /** A subscription is starred or its star taken off. */
    onFavorite: (UUID) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
    /** Идёт то, что запустило «потянуть, чтобы обновить» (D-92). */
    refreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    /** Подписку [from] перетащили на место [to] (этап 13, пакет 5). */
    onMove: (from: UUID, to: UUID) -> Unit = { _, _ -> },
    /** Перестановка кончилась — запомнить порядок. */
    onMoved: () -> Unit = {},
    /** «N запасных адреса» on the active card: its addresses (Q-82). */
    onAddresses: (Profile) -> Unit = {},
    /** «N правил» on the active card: its own rules (Q-83). */
    onRules: (Profile) -> Unit = {},
    /** «Отправить на телевизор» in the menu: the scanner for the TV's code (D-162). */
    onSendToTv: (Profile) -> Unit = {},
) {
    val list = rememberLazyListState()
    val bounce = overscrollFor(list)
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val tv = LocalTv.current

    val active = state.profiles.firstOrNull { it.active }
    val others = state.profiles.filter { !it.active }

    // A row changes places only within its group — a favourite among favourites; the card and the
    // caption are in no group.
    val groups by rememberUpdatedState(
        others.associate<Profile, Any, Boolean> { it.uuid.toString() to (it.uuid in state.favorites) },
    )
    val move by rememberUpdatedState(onMove)
    val moved by rememberUpdatedState(onMoved)
    val reorder = remember(list) {
        ProfileReorder(
            list = list,
            scope = scope,
            sameGroup = { a, b -> groups[a]?.let { it == groups[b] } == true },
            onMove = { from, to -> move(UUID.fromString(from), UUID.fromString(to)) },
            onDrop = { moved() },
            putDown = LIFT_MS,
        )
    }

    // Действия подписки — панелью по «⋯» (D-121). Выбранное действие запускаем,
    // когда панель уехала: правка и удаление открывают свои панели, и две разом наезжали бы.
    var menuFor by remember { mutableStateOf<Profile?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var afterMenu by remember { mutableStateOf<(() -> Unit)?>(null) }
    val openMenu = { profile: Profile ->
        menuFor = profile
        menuOpen = true
    }

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
            link = InstallLink.of(profile.source, profile.mirrors).toString(),
            onDismiss = { shareOpen = false },
            onClosed = { shareFor = null },
        )
    }

    menuFor?.let { profile ->
        // «Продлить» leads where it does on “Главная” and in the expiry notice (D-119): the provider's
        // renewal link, else its support — the subscription's page alone renews nothing — else that page
        // (D-317).
        val renew = firstProviderLink(profile.buyUrl, profile.supportUrl, profile.profileWebPageUrl)
        val support = providerLink(profile.supportUrl)
        ProfileMenu(
            open = menuOpen,
            name = profile.shownName,
            actions = listOfNotNull(
                MenuAction.Update.takeIf { !profile.local },
                MenuAction.Edit,
                MenuAction.Favorite,
                MenuAction.Support.takeIf { support != null },
                MenuAction.Renew.takeIf { renew != null },
                MenuAction.Share.takeIf { !profile.local },
                // A link the TV can load itself; a TV has no camera to scan another TV with.
                MenuAction.SendTv.takeIf { !tv && subscriptionLink(profile.source) != null },
                MenuAction.Delete,
            ),
            favorite = profile.uuid in state.favorites,
            onAction = { action ->
                afterMenu = when (action) {
                    MenuAction.Update -> ({ onUpdate(profile.uuid) })
                    MenuAction.Edit -> ({ onEdit(profile) })
                    MenuAction.Renew -> ({ renew?.let(onOpenLink) })
                    MenuAction.Support -> ({ support?.let(onOpenLink) })
                    MenuAction.Share -> ({ share(profile) })
                    MenuAction.SendTv -> ({ onSendToTv(profile) })
                    // After the sheet has gone, so the row is seen moving to its group.
                    MenuAction.Favorite -> ({ onFavorite(profile.uuid) })
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

    // A chosen row becomes the card on top. A list scrolled away from the card rides up to it; with
    // the remote the focus goes to the card's first button, since the focused row is gone.
    val cardFocus = remember { FocusRequester() }
    var shownActive by remember { mutableStateOf(active?.uuid) }

    LaunchedEffect(active?.uuid) {
        val switched = shownActive != null && active != null && active.uuid != shownActive

        shownActive = active?.uuid
        if (!switched) return@LaunchedEffect

        val card = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == ACTIVE_KEY }

        if (tv) runCatching { cardFocus.requestFocus() }
        if (card == null || card.offset < 0) list.animateScrollToItem(0)
    }

    val direction = LocalLayoutDirection.current
    val start = 16.dp + contentPadding.calculateStartPadding(direction)
    val end = 16.dp + contentPadding.calculateEndPadding(direction)

    PullToRefresh(
        refreshing = refreshing,
        onRefresh = onRefresh,
        top = contentPadding.calculateTopPadding(),
        modifier = modifier.fillMaxSize(),
    ) {
        TvLazyColumn(
            state = list,
            overscrollEffect = bounce,
            modifier = Modifier.fillMaxSize().screenInsets(),
            // Отступы списка, а не самого экрана: тогда карточки уходят под плавающую
            // панель вкладок и видны в просветах по её бокам (D-74). Each card owns its trailing
            // gap so the lazy item's bounds remain stable while it is dragged.
            contentPadding = PaddingValues(
                start = start,
                end = end,
                top = 16.dp + contentPadding.calculateTopPadding(),
                // Плюс над панелью (D-97): с этим запасом последнюю карточку можно
                // докрутить выше кнопки.
                bottom = 16.dp + contentPadding.calculateBottomPadding() + ACTION_SIZE + 12.dp,
            ),
        ) {
            item(key = HEADER_KEY) {
                // От заголовка до первой карточки — как на «Прокси», где под заголовком ещё
                // стоит строка режимов (без VPN — пустая): два шага списка, а не один (D-104).
                ScreenHeader(stringResource(R.string.subscriptions_tab), Modifier.padding(bottom = GAP * 2))
            }

            // Заглушки, а не бесконечная полоска (D-92): так же, как на главной.
            if (!state.loaded) {
                items(2) { ProfileSkeleton(Modifier.padding(bottom = GAP)) }
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
                                stringResource(R.string.support) to { onOpenLink(link) }
                            }
                            else -> null
                        },
                        onDismiss = onDismissError,
                        modifier = Modifier.padding(bottom = GAP),
                    )
                }
            }

            if (active != null) {
                item(key = ACTIVE_KEY) {
                    ActiveCard(
                        profile = active,
                        state = state,
                        focus = cardFocus,
                        onUpdate = onUpdate,
                        onEdit = onEdit,
                        onShare = share,
                        onMenu = openMenu,
                        onAddresses = onAddresses,
                        onRules = onRules,
                    )
                }

                if (others.isNotEmpty()) {
                    item(key = OTHERS_KEY) {
                        Text(
                            text = stringResource(R.string.other_subscriptions),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = ROW_INSET, top = 20.dp, bottom = 8.dp),
                        )
                    }
                }
            }

            itemsIndexed(others, key = { _, it -> it.uuid.toString() }) { index, profile ->
                val key = profile.uuid.toString()
                val favorite = profile.uuid in state.favorites
                val above = others.getOrNull(index - 1)
                val below = others.getOrNull(index + 1)
                val up = stringResource(R.string.move_up)
                val down = stringResource(R.string.move_down)

                ProfileRow(
                    // Соседи расступаются плавно только при перестановке: в остальное время сдвиг
                    // спорил бы с ростом карточки при смене активной (D-121). Поднятая и
                    // доезжающая строки едут сами. The subscription that was active comes in as
                    // a row and fades in at its place.
                    modifier = Modifier.animateItem(
                        fadeInSpec = tween(ROW_FADE_MS),
                        fadeOutSpec = null,
                        placementSpec = if (reorder.active && reorder.dragged != key && reorder.settling != key) {
                            spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold)
                        } else null,
                    ),
                    reorder = reorder,
                    onLift = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                    onStep = { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick) },
                    // TalkBack перетаскивать не умеет — те же перестановки действиями, в своей группе.
                    moves = listOfNotNull(
                        above?.takeIf { (it.uuid in state.favorites) == favorite }?.let {
                            CustomAccessibilityAction(up) { onMove(profile.uuid, it.uuid); onMoved(); true }
                        },
                        below?.takeIf { (it.uuid in state.favorites) == favorite }?.let {
                            CustomAccessibilityAction(down) { onMove(profile.uuid, it.uuid); onMoved(); true }
                        },
                    ),
                    profile = profile,
                    favorite = favorite,
                    updating = state.updating == profile.uuid,
                    rules = state.ruleCounts[profile.uuid] ?: 0,
                    onSelect = { onSelect(profile) },
                    onMenu = { openMenu(profile) },
                )
            }

            if (state.loaded && state.profiles.isEmpty()) {
                item {
                    EmptyState(
                        icon = Symbols.Article,
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

/**
 * The active subscription: the full card (D-121) — expiry, traffic, «Обновить», «Изменить» and
 * “⋯” with the rest. Filled the way a chosen node is on “Прокси” (D-101): black in the light theme,
 * white in the dark one. Отказ панели важнее выбора — такая карточка остаётся красной.
 */
@Composable
private fun ActiveCard(
    profile: Profile,
    state: SubscriptionsState,
    /** The remote's place on the card after another subscription is chosen. */
    focus: FocusRequester,
    onUpdate: (UUID) -> Unit,
    onEdit: (Profile) -> Unit,
    onShare: (Profile) -> Unit,
    onMenu: (Profile) -> Unit,
    onAddresses: (Profile) -> Unit,
    onRules: (Profile) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val tv = LocalTv.current
    // «Активна» is no longer written on the card (Q-83): its place and fill say it; TalkBack still hears it.
    val activeText = stringResource(R.string.ducker_active)

    val container = if (profile.hwidBlocked) scheme.errorContainer else scheme.primaryContainer
    val content = if (profile.hwidBlocked) scheme.onErrorContainer else scheme.onPrimaryContainer

    Card(
        // Повторное касание по выбранной ничего не делает: смена профиля перезагружает конфиг
        // в работающем туннеле. Пульт входит на «Подписки» на неё (этап 6), но сама она фокус
        // не берёт — Compose не водит стрелками внутрь элемента, который сам в фокусе, а
        // нужны её кнопки.
        modifier = Modifier.fillMaxWidth()
            .tabEntry()
            .focusProperties { if (tv) canFocus = false }
            .selectable(selected = true, role = Role.RadioButton, onClick = {})
            .semantics { stateDescription = activeText },
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
        resizing = true,
    ) {
        // Another subscription chosen: the card stays in place, its content fades through to the
        // new one (Material's timings) and the height flows to it.
        AnimatedContent(
            targetState = profile,
            contentKey = { it.uuid },
            transitionSpec = {
                fadeIn(tween(CARD_FADE_IN_MS, delayMillis = CARD_FADE_OUT_MS)) togetherWith
                    fadeOut(tween(CARD_FADE_OUT_MS)) using
                    SizeTransform(clip = false) { _, _ -> spring(stiffness = Spring.StiffnessMediumLow) }
            },
            label = "activeCard",
        ) { shown ->
            Column(
                modifier = Modifier.animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // No gap before the QR button: its icon keeps 12 dp of its touch area on each side, which
                // is gap enough, and the line under the name needs the room (Q-83).
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The logo is on the active card only: the provider's, else ours (D-45).
                    BrandAvatar(shown.brandLogo, Modifier.size(44.dp))
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        // The rules go to the line below (Q-83): beside the name they took its room.
                        ProfileName(
                            shown,
                            favorite = shown.uuid in state.favorites,
                            rules = 0,
                            // A step larger than in the rows.
                            style = LocalTextStyle.current.let { it.copy(fontSize = (it.fontSize.value + 1).sp) },
                        )

                        // Домена под названием больше нет (D-102). Строка остаётся только для
                        // отказа — у него своя причина: лимит устройств и «панель требует
                        // идентификатор» — разные вещи, и чинятся по-разному.
                        if (shown.hwidBlocked || shown.hwidReason == "not_supported") {
                            Text(
                                text = stringResource(hwidLimit(shown)),
                                style = MaterialTheme.typography.bodySmall,
                                color = LocalContentColor.current.copy(alpha = 0.7f),
                            )
                        }

                        CardFacts(
                            rules = state.ruleCounts[shown.uuid] ?: 0,
                            backups = if (shown.imported) shown.backups else 0,
                            onRules = { onRules(shown) },
                            onAddresses = { onAddresses(shown) },
                        )
                    }

                    // «Поделиться» — в свободном углу; своему конфигу делиться нечем. The button's
                    // icon sits 12 dp inside its touch area: moved by that, the icon's edge is on the
                    // card's content edge, as the logo's and the «⋯» button's are (Q-83).
                    if (!shown.local) {
                        IconButton(onClick = { onShare(shown) }, modifier = Modifier.offset(x = 12.dp)) {
                            Icon(Symbols.QrCode2, contentDescription = stringResource(R.string.share_qr))
                        }
                    }
                }

                ActiveDetails(
                    profile = shown,
                    updating = state.updating == shown.uuid,
                    failed = state.updateError.isNotEmpty() && state.updateErrorFor == shown.uuid,
                    // The leaving content keeps no requester: one node takes the focus.
                    focus = focus.takeIf { shown.uuid == profile.uuid },
                    onUpdate = { onUpdate(shown.uuid) },
                    onEdit = { onEdit(shown) },
                    onMenu = { onMenu(shown) },
                )
            }
        }
    }
}

/**
 * A subscription that is not active: one row on the shared backing, without a logo (stage 19,
 * package 4). Touching it makes it active; “⋯” opens its actions (D-121).
 */
@Composable
private fun ProfileRow(
    modifier: Modifier,
    reorder: ProfileReorder,
    onLift: () -> Unit,
    onStep: () -> Unit,
    moves: List<CustomAccessibilityAction>,
    profile: Profile,
    favorite: Boolean,
    updating: Boolean,
    /** Свои правила подписки — маленьким значком у имени (D-123). */
    rules: Int,
    onSelect: () -> Unit,
    onMenu: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val key = profile.uuid.toString()
    val lifted = reorder.dragged == key

    // Keep the released card above its neighbours until it reaches its place and is put down.
    val up = lifted || (reorder.settling == key && reorder.arrived != key)
    val lift by animateFloatAsState(if (up) 1f else 0f, tween(LIFT_MS), label = "lift")
    val raised = lifted || reorder.settling == key || lift > 0f
    val shape = MaterialTheme.shapes.large

    // The row is lifted with the finger still down: its press highlight would stay on the card all
    // the way, a grey card. The press is called off at the lift.
    val interactions = remember { MutableInteractionSource() }
    var press by remember { mutableStateOf<PressInteraction.Press?>(null) }

    LaunchedEffect(interactions) {
        interactions.interactions.collect {
            when (it) {
                is PressInteraction.Press -> press = it
                is PressInteraction.Release, is PressInteraction.Cancel -> press = null
            }
        }
    }
    LaunchedEffect(lifted) {
        if (lifted) press?.let { interactions.emit(PressInteraction.Cancel(it)) }
    }

    // Пульт (этап 6): фокус, взятый строкой, к её «⋯» уже не пускает — Compose не водит
    // стрелками внутрь элемента, который сам в фокусе. ОК — выбрать, вправо — «⋯».
    val self = remember { FocusRequester() }
    val menu = remember { FocusRequester() }
    val tv = LocalTv.current

    Box(modifier.fillMaxWidth().padding(bottom = ROW_GAP).zIndex(if (raised) 1f else 0f)) {
        // The opaque surface and its cached Air shadow travel together in the existing drag layer.
        Row(
            modifier = Modifier.fillMaxWidth()
                .graphicsLayer {
                    translationY = reorder.offset(key)
                    scaleX = 1f + LIFT_SCALE * lift
                    scaleY = scaleX
                    shadowElevation = LIFT_SHADOW.toPx() * lift
                    this.shape = shape
                }
                .airShadow(shape)
                .clip(shape)
                .background(scheme.surface)
                .then(if (tv) Modifier.ringCorner(ROW_CORNER) else Modifier)
                .focusRequester(self)
                .focusProperties { if (tv) right = menu }
                .selectable(
                    selected = false,
                    interactionSource = interactions,
                    indication = LocalIndication.current,
                    role = Role.RadioButton,
                    onClick = onSelect,
                )
                .semantics { customActions = moves }
                .reorderable(reorder, key, scale = { 1f + LIFT_SCALE * lift }, onLift = onLift, onStep = onStep)
                .heightIn(min = ROW_HEIGHT)
                .padding(start = ROW_INSET, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
                Column(
                    modifier = Modifier.weight(1f).padding(vertical = 15.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    ProfileName(profile, favorite, rules, style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight(450))

                    Text(
                        text = profileSummary(profile, updating),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (!profile.imported) scheme.error
                        else scheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    // The panel's refusal: on the card it is red all over, in a row — its line.
                    if (profile.hwidBlocked || profile.hwidReason == "not_supported") {
                        Text(
                            text = stringResource(hwidLimit(profile)),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.error,
                        )
                    }
                }

                IconButton(onClick = onMenu, modifier = Modifier.focusRequester(menu).focusProperties { if (tv) left = self }) {
                    Icon(Symbols.MoreHoriz, contentDescription = stringResource(R.string.more_actions))
                }
            }
        }
    }
}

/** The name with a star when it is a favourite and the rules badge (D-123). */
@Composable
private fun ProfileName(
    profile: Profile,
    favorite: Boolean,
    rules: Int,
    style: TextStyle = LocalTextStyle.current,
    fontWeight: FontWeight = FontWeight.Medium,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = profile.shownName,
            style = style,
            fontWeight = fontWeight,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (favorite) {
            Icon(Symbols.Star, contentDescription = stringResource(R.string.favorite_marked), modifier = Modifier.size(14.dp))
        }
        if (rules > 0) RulesBadge(rules)
    }
}

private fun hwidLimit(profile: Profile): Int =
    if (profile.hwidReason == "not_supported") R.string.limit_hwid_required else R.string.limit_max_devices

/**
 * Under the active card's name (Q-83): the subscription's own rules and the provider's backup addresses,
 * only what there is — most subscriptions have neither, and the line is not there. A touch opens them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardFacts(rules: Int, backups: Int, onRules: () -> Unit, onAddresses: () -> Unit) {
    if (rules == 0 && backups == 0) return

    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (rules > 0) Fact(Symbols.Tune, pluralStringResource(R.plurals.subscription_rules_count, rules, rules), onRules)
        if (backups > 0) {
            Fact(Symbols.AltRoute, pluralStringResource(R.plurals.subscription_backup_addresses, backups, backups), onAddresses)
        }
    }
}

@Composable
private fun Fact(icon: ImageVector, text: String, onClick: () -> Unit) {
    val color = LocalContentColor.current.copy(alpha = 0.7f)

    Row(
        modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Normal, color = color, maxLines = 1)
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
        Icon(Symbols.Tune, contentDescription = stringResource(R.string.rules_title), modifier = Modifier.size(12.dp))
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

/** Подробности активной подписки: срок, трафик и действия. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActiveDetails(
    profile: Profile,
    updating: Boolean,
    failed: Boolean,
    focus: FocusRequester?,
    onUpdate: () -> Unit,
    onEdit: () -> Unit,
    onMenu: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Подписка, которая ни разу не загрузилась, лежит в списке наравне с рабочими,
        // и раньше отличалась от них только нулевым трафиком (D-83). Говорим прямо:
        // цифр у неё нет и не будет, пока не починят ссылку.
        val loaded = profile.imported

        if (!loaded) {
            // На залитой карточке красный теряется, там хватает самих слов.
            Text(
                stringResource(R.string.profile_not_loaded),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Normal,
            )
            Text(
                stringResource(R.string.profile_not_loaded_hint),
                style = MaterialTheme.typography.bodySmall,
                color = LocalContentColor.current.copy(alpha = 0.7f),
            )
        } else if (profile.local) {
            // Свой конфиг: трафика и срока у него нет, счётчик «0 Б» только путал (D-125).
            Text(
                stringResource(R.string.profile_local),
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            )
        } else {
            val used = profile.upload + profile.download

            // The plan as one block (Q-83): how much on the left, until when on the right, the bar under them.
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "${formatBytes(used)} ${stringResource(R.string.ducker_traffic_of,
                            if (profile.total > 0) formatBytes(profile.total) else UNLIMITED)}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                    )
                    if (profile.expire > 0) {
                        Text(
                            stringResource(R.string.valid_until, formatDate(profile.expire)),
                            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Normal,
                            color = LocalContentColor.current.copy(alpha = 0.7f),
                        )
                    }
                }

                // Шкала вместо разделительной линии (D-102): та же, что на главной, только
                // в цвет текста карточки — белая на залитой, чёрная на обычной.
                if (profile.total > 0) {
                    ThinBar(
                        fraction = (used.toFloat() / profile.total).coerceIn(0f, 1f),
                        color = LocalContentColor.current,
                        trackColor = LocalContentColor.current.copy(alpha = 0.15f),
                    )
                }
            }
        }
        // Действия одной строкой вместо карандаша в углу (D-101). Последнее — «⋯» на месте
        // корзины (stage 19, package 4): удаление, избранное, «Продлить» и остальное — в меню.
        //
        // Кнопки залиты (D-102): прозрачные с тонкой обводкой сливались с чёрной
        // карточкой — белые (в тёмной теме — чёрные).
        val action = if (profile.hwidBlocked) scheme.surface else scheme.onPrimaryContainer
        val onAction = if (profile.hwidBlocked) scheme.onSurface else scheme.primaryContainer
        val first = focus?.let { Modifier.focusRequester(it) } ?: Modifier

        // Half as much air again above and below the buttons as the card's other steps.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 7.dp)) {
            // Своему конфигу обновляться неоткуда: «Обновить» шло по пустой ссылке с ошибкой.
            // Итог — в самой кнопке, как на главной (D-119): «Обновлено» или «Не удалось».
            if (!profile.local) {
                val shown = rememberUpdateShown(updating, failed)

                val active = !updating && shown != UpdateShown.Busy

                // С пульта на время обновления кнопка держит фокус (D-158).
                CardAction(onClick = { if (active) onUpdate() }, container = action, content = onAction,
                    enabled = keepFocus(active), modifier = first.weight(1f)) {
                    UpdateLabel(shown, LocalTextStyle.current, 18.dp)
                }
            }

            CardAction(onClick = onEdit, container = action, content = onAction,
                modifier = (if (profile.local) first else Modifier).weight(1f)) {
                Icon(Symbols.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                FitLabel(stringResource(R.string.edit), LocalTextStyle.current, Modifier.padding(start = 6.dp))
            }

            CardAction(onClick = onMenu, container = action, content = onAction, modifier = Modifier.width(48.dp)) {
                Icon(
                    Symbols.MoreHoriz,
                    contentDescription = stringResource(R.string.more_actions),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private enum class MenuAction { Update, Edit, Renew, Support, Share, SendTv, Favorite, Delete }

/** The same actions apply to active and inactive subscriptions. */
@Composable
private fun ProfileMenu(
    open: Boolean,
    name: String,
    actions: List<MenuAction>,
    favorite: Boolean,
    onAction: (MenuAction) -> Unit,
    onDismiss: () -> Unit,
    onClosed: () -> Unit,
) {
    DuckerSheet(open = open, title = name, busy = false, onDismiss = onDismiss, onClosed = onClosed) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (action in actions) {
                val icon = when (action) {
                    MenuAction.Update -> Symbols.Refresh
                    MenuAction.Edit -> Symbols.Edit
                    MenuAction.Share -> Symbols.QrCode2
                    MenuAction.SendTv -> Symbols.Send
                    MenuAction.Favorite -> if (favorite) Symbols.Star else Symbols.StarOutline
                    MenuAction.Renew -> Symbols.CreditCard
                    MenuAction.Support -> Symbols.SupportAgent
                    MenuAction.Delete -> Symbols.DeleteOutline
                }
                val label = when (action) {
                    MenuAction.Update -> R.string.update
                    MenuAction.Edit -> R.string.edit
                    MenuAction.Share -> R.string.share_qr
                    MenuAction.SendTv -> R.string.send_tv_title
                    MenuAction.Favorite -> if (favorite) R.string.unfavorite else R.string.favorite
                    MenuAction.Renew -> R.string.renew
                    MenuAction.Support -> R.string.support
                    MenuAction.Delete -> R.string.delete_subscription_action
                }

                if (action == MenuAction.Delete) {
                    MenuLine(icon, stringResource(label), MaterialTheme.colorScheme.error) { onAction(action) }
                } else {
                    Card(Modifier.fillMaxWidth()) {
                        MenuLine(icon, stringResource(label)) { onAction(action) }
                    }
                }
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
            color = color, modifier = Modifier.weight(1f))
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
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme

    Card(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(scheme.error.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                // A bare mark in the round badge: the outlined circle drew a ring inside it.
                Icon(Symbols.PriorityHigh, contentDescription = null, tint = scheme.error, modifier = Modifier.size(20.dp))
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
                    Pill(text = label, onClick = onClick, modifier = Modifier.padding(top = 4.dp), selected = true)
                }
            }

            IconButton(onClick = onDismiss) {
                Icon(Symbols.Close, contentDescription = stringResource(R.string.dismiss), tint = scheme.onSurfaceVariant)
            }
        }
    }
}

/** Заглушка карточки подписки (D-92). */
@Composable
private fun ProfileSkeleton(modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
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
 * «Плюс» над панелью вкладок, по центру (D-97): он вернулся из шапки — там он был
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
        Icon(Symbols.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

private const val UNLIMITED = "∞"

/** Keys of the list items that are not subscriptions; a subscription's key is its number. */
private const val HEADER_KEY = "header"
private const val ACTIVE_KEY = "active"
private const val OTHERS_KEY = "others"

/** The list's step: below the header (twice), the skeletons and the error card. */
private val GAP = 12.dp

/** Text inset and spacing for the compact subscription cards. */
private val ROW_INSET = 16.dp
private val ROW_HEIGHT = 74.dp
private val ROW_GAP = 10.dp

/** The cards' corners, shared with `shapes.large` for the TV focus ring. */
private val ROW_CORNER = 24.dp

private const val ROW_FADE_MS = 220
private const val CARD_FADE_OUT_MS = 90
private const val CARD_FADE_IN_MS = 210

/** Поднятая карточка: насколько крупнее, какая тень, за сколько поднимается. */
private const val LIFT_SCALE = 0.03f
private val LIFT_SHADOW = 12.dp
private const val LIFT_MS = 150
