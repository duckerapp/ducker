package com.github.kr328.clash.ui.home

import com.github.kr328.clash.ui.components.Symbols
import com.github.kr328.clash.ui.components.focusRoom
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import com.github.kr328.clash.ui.components.ringCorner
import com.github.kr328.clash.ui.components.ringHold
import com.github.kr328.clash.ui.components.tvKeys
import com.github.kr328.clash.ui.components.tvEdge
import com.github.kr328.clash.ui.components.LocalTv
import com.github.kr328.clash.ui.proxy.ProxyState
import com.github.kr328.clash.core.model.ProxySort
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import com.github.kr328.clash.ui.components.tabEntry
import com.github.kr328.clash.ui.components.keepFocus
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.unit.Constraints
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.center
import androidx.compose.ui.geometry.isSpecified
import com.github.kr328.clash.ui.components.ThinBar
import com.github.kr328.clash.ui.components.Pill
import com.github.kr328.clash.ui.components.FitLabel
import com.github.kr328.clash.ui.components.UpdateLabel
import com.github.kr328.clash.ui.components.UpdateShown
import com.github.kr328.clash.ui.components.rememberUpdateShown
import com.github.kr328.clash.ui.components.HeaderButton
import com.github.kr328.clash.ui.components.overscrollFor
import kotlin.math.hypot
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.em
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.github.kr328.clash.ui.theme.LocalThemeSwitch
import com.github.kr328.clash.R
import com.github.kr328.clash.common.util.firstProviderLink
import com.github.kr328.clash.common.util.providerLink
import com.github.kr328.clash.ui.subscriptions.local
import com.github.kr328.clash.util.Vendor
import com.github.kr328.clash.log.LogScrubber
import com.github.kr328.clash.store.DelayStore
import com.github.kr328.clash.service.model.Profile
import com.github.kr328.clash.service.util.Failure
import com.github.kr328.clash.util.nodeFailure
import com.github.kr328.clash.ui.components.BrandAvatar
import com.github.kr328.clash.ui.components.EmptyState
import com.github.kr328.clash.ui.components.Skeleton
import com.github.kr328.clash.ui.components.formatBytes
import com.github.kr328.clash.ui.components.formatDate
import com.github.kr328.clash.ui.components.screenInsets
import com.github.kr328.clash.ui.components.DuckerSheet
import com.github.kr328.clash.ui.components.SheetButton
import com.github.kr328.clash.ui.components.labelWeights
import com.github.kr328.clash.ui.components.airEdge
import com.github.kr328.clash.ui.components.DuckerCard as Card

/**
 * Главная. Всё, ради чего человек открывает приложение, должно быть здесь: состояние,
 * остаток трафика, срок, текущий сервер и переключение групп — без заходов в другие
 * вкладки.
 */
@Composable
fun HomeScreen(
    state: HomeState,
    /**
     * Цифры, меняющиеся каждую секунду. Приходят лямбдой и читаются только там, где
     * показываются (D-77): прочитать их здесь значило бы пересобирать всю главную
     * раз в секунду ради одних часов.
     */
    metrics: () -> HomeMetrics,
    onToggle: () -> Unit,
    onSettings: () -> Unit,
    onSupport: (String) -> Unit,
    onRefresh: () -> Unit,
    onCheckLink: () -> Unit,
    onOpenProxies: () -> Unit,
    onVpnSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /** Подписки есть, но ни одна не выбрана: кнопка ведёт на «Подписки» (D-213). */
    onChooseSubscription: () -> Unit = {},
    /** Крестик на карточке «VPN не включился» (D-227). */
    onDismissStop: () -> Unit = {},
    /** «Переключиться на самый быстрый» в карточке «нет связи»; `null` — выбирать не из чего. */
    onFastest: (() -> Unit)? = null,
    /** Подписок нет вовсе — вместо главной приветствие (D-98). */
    onboarding: Boolean = false,
    /** Карточка про работу в фоне; `null` — не показывать (D-109). */
    backgroundNotice: BackgroundNotice? = null,
    /** Узлы для быстрой смены в карточке (D-116); `null` — ещё не загружены. */
    picker: NodePicker? = null,
    /** The prepared proxy-tab order, including favorites and non-selectable groups. */
    pickerState: ProxyState = ProxyState(),
    onSortNodes: (ProxySort) -> Unit = {},
    onCheckNodes: (String) -> Unit = {},
    /** Быстрая смена открылась — пора освежить задержки. */
    onOpenPicker: () -> Unit = {},
    onPickNode: (group: String, name: String) -> Unit = { _, _ -> },
    /** Узел выбран — показать его в плашке сразу, до [onPickNode]. */
    onPreviewNode: (name: String) -> Unit = {},
    /** Главная перед глазами: ушли с неё — быстрая смена узла сворачивается. */
    shown: Boolean = true,
    /** Активная подписка обновляется и чем кончилось последнее обновление (D-119). */
    updating: Boolean = false,
    updateFailed: Boolean = false,
    /** Шапка с маркой, темой и настройками. На телевизоре она в колонке дока (D-161). */
    header: Boolean = true,
    /** «Скопировать ошибку» на карточке о конфиге провайдера с ошибкой (D-246). */
    onCopyError: (String) -> Unit = {},
    /** Вышла новая версия приложения — её номер; `null` — ставить нечего. */
    update: String? = null,
    onUpdate: () -> Unit = {},
    /** Its download in percent while it runs, and the installation after it. */
    updateProgress: Int? = null,
    updateInstalling: Boolean = false,
    /** The app has just updated itself — to which version; `null` — nothing to say. */
    updated: String? = null,
    /** “What's new” on that card; `null` — the release came without notes. */
    onWhatsNew: (() -> Unit)? = null,
    onUpdatedSeen: () -> Unit = {},
) {
    // The cards about the app's own version are the same with and without a subscription.
    val appCards: (@Composable () -> Unit)? = if (update == null && updated == null) null else {
        { AppUpdateCards(update, updateProgress, updateInstalling, onUpdate, updated, onWhatsNew, onUpdatedSeen) }
    }

    // Подписок нет — показывать нечего, кроме одного действия: добавить. Приветствие
    // по центру, а саму кнопку даёт панель внизу, ставшая одной кнопкой (D-98). Смена —
    // мягкая: после первой подписки главная проявляется на месте приветствия.
    Crossfade(targetState = onboarding, animationSpec = tween(ONBOARDING_MS), label = "onboarding") { first ->
        if (first) {
            Welcome(onSettings, header, appCards, modifier)
        } else {
            HomeRegular(
                state = state,
                metrics = metrics,
                onToggle = onToggle,
                onSettings = onSettings,
                onSupport = onSupport,
                onRefresh = onRefresh,
                onCheckLink = onCheckLink,
                onOpenProxies = onOpenProxies,
                onVpnSettings = onVpnSettings,
                onChooseSubscription = onChooseSubscription,
                onDismissStop = onDismissStop,
                onFastest = onFastest,
                backgroundNotice = backgroundNotice,
                picker = picker,
                pickerState = pickerState,
                onSortNodes = onSortNodes,
                onCheckNodes = onCheckNodes,
                onOpenPicker = onOpenPicker,
                onPickNode = onPickNode,
                onPreviewNode = onPreviewNode,
                shown = shown,
                updating = updating,
                updateFailed = updateFailed,
                header = header,
                onCopyError = onCopyError,
                appCards = appCards,
                modifier = modifier,
            )
        }
    }
}

/**
 * Приветствие, пока подписок нет (D-98): утка, пара строк и больше ничего. Раньше здесь
 * стояла карточка «первое подключение» без единой кнопки, а под ней — пустой экран.
 */
@Composable
private fun Welcome(
    onSettings: () -> Unit,
    header: Boolean,
    appCards: (@Composable () -> Unit)?,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize()
            .screenInsets()
            .padding(horizontal = 16.dp, vertical = 12.dp + tvEdge()),
    ) {
        if (header) BrandHeader(null, onSettings)

        // A new version matters before the first subscription too: whoever got an old file
        // from a friend should update before setting anything up. The notification cannot
        // tell them — on a fresh install it is not permitted yet.
        if (appCards != null) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                appCards()
            }
        }

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f

            Image(
                painter = painterResource(if (dark) R.drawable.ducker_mark_dark else R.drawable.ducker_mark_light),
                contentDescription = null,
                modifier = Modifier.size(112.dp).clip(RoundedCornerShape(28.dp)),
            )

            Spacer(Modifier.height(28.dp))

            Text(
                text = stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = stringResource(R.string.welcome_text),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun HomeRegular(
    state: HomeState,
    /**
     * Цифры, меняющиеся каждую секунду. Приходят лямбдой и читаются только там, где
     * показываются (D-77): прочитать их здесь значило бы пересобирать всю главную
     * раз в секунду ради одних часов.
     */
    metrics: () -> HomeMetrics,
    onToggle: () -> Unit,
    onSettings: () -> Unit,
    onSupport: (String) -> Unit,
    onRefresh: () -> Unit,
    onCheckLink: () -> Unit,
    onOpenProxies: () -> Unit,
    onVpnSettings: () -> Unit,
    onChooseSubscription: () -> Unit,
    onDismissStop: () -> Unit,
    onFastest: (() -> Unit)?,
    backgroundNotice: BackgroundNotice?,
    picker: NodePicker?,
    pickerState: ProxyState,
    onSortNodes: (ProxySort) -> Unit,
    onCheckNodes: (String) -> Unit,
    onOpenPicker: () -> Unit,
    onPickNode: (group: String, name: String) -> Unit,
    onPreviewNode: (name: String) -> Unit,
    shown: Boolean,
    updating: Boolean,
    updateFailed: Boolean,
    header: Boolean,
    onCopyError: (String) -> Unit,
    appCards: (@Composable () -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val profile = state.profile

    val scroll = rememberScrollState()

    // Быстрая смена узла (D-116). Открыта ли она — здесь, а не в карточке: касание мимо
    // карточки видно только отсюда.
    var picking by remember { mutableStateOf(false) }
    var pickerMounted by remember { mutableStateOf(false) }
    var pendingPick by remember { mutableStateOf<Pair<String, String>?>(null) }
    val tv = LocalTv.current
    // Display-size changes alter the logical window height; text keeps the system's own scale. A TV is
    // 540 tall: there the full-size power button pushed the subscription card's buttons under the bottom edge.
    val compact = LocalConfiguration.current.screenHeightDp < if (tv) 640 else 840
    val requestNodes by rememberUpdatedState(onOpenPicker)
    val nodesLoaded by rememberUpdatedState(pickerState.loaded)
    var viewport by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var heroCard by remember { mutableStateOf<LayoutCoordinates?>(null) }

    LaunchedEffect(shown) {
        if (!shown) picking = false
    }

    LaunchedEffect(picking, tv) {
        if (!tv && picking) {
            pickerMounted = true
            // Loaded content can open first; refreshing the sibling tab must not interrupt the sheet.
            if (nodesLoaded) delay(280)
            requestNodes()
        }
    }

    Column(
        modifier = modifier
            // Во всю высоту, а не по содержимому: иначе касание в пустое место под
            // карточками было мимо главной и панель узлов не сворачивало.
            .fillMaxSize()
            .screenInsets()
            // Панель открыта — касание мимо карточки только сворачивает её: как выйти,
            // человек не гадает, а «Обновить» или шестерёнка под пальцем не срабатывают.
            .onGloballyPositioned { viewport = it }
            .pointerInput(picking) {
                if (!picking || !tv) return@pointerInput

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val box = viewport?.takeIf { it.isAttached }
                    val card = heroCard?.takeIf { it.isAttached }

                    if (box == null || card == null || down.position !in box.localBoundingBoxOf(card)) {
                        down.consume()
                        picking = false
                    }
                }
            }
            // Прокрутка остаётся страховкой на крупный шрифт и редкие карточки
            // (лимит устройств, потеря связи), но в обычном состоянии экран должен
            // помещаться целиком: 12 между карточками вместо 16 — ровно та разница,
            // из-за которой подписка уезжала под панель.
            .verticalScroll(scroll, overscrollEffect = overscrollFor(scroll))
            .padding(horizontal = 16.dp, vertical = 12.dp + tvEdge()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (header) BrandHeader(profile, onSettings)

        if (profile != null && profile.hwidBlocked) {
            LimitCard(profile, onSupport, onRefresh)
        }

        if (profile != null && profile.announce.isNotEmpty() && !profile.hwidBlocked) {
            AnnouncementCard(profile.announce)
        }

        // Список ссылок — сообщение без текста ошибки ядра (D-81): ошибка там всегда одна и
        // та же, «это не конфиг mihomo», и советовать «покажите провайдеру» не за что —
        // ничего не сломалось. Другое дело, когда шаблон включён из-за ошибки в конфиге
        // провайдера (D-246): тут ошибка настоящая, и её стоит переслать.
        if (profile != null && profile.usedTemplate) {
            if (profile.templateFromConfig) {
                InfoCard(
                    stringResource(R.string.template_config),
                    Symbols.WarningAmber,
                    action = stringResource(R.string.template_config_copy) to { onCopyError(profile.lastError) },
                )
            } else {
                InfoCard(stringResource(R.string.template_used), Symbols.Info)
            }
        }

        // Банк «сломался» ровно после включения блокировки в настройках Android — связать
        // одно с другим человек сам не сможет, поэтому говорим прямо и ведём куда надо.
        if (state.running && state.lockdownOffline) {
            InfoCard(
                stringResource(R.string.lockdown_offline),
                Symbols.WarningAmber,
                action = stringResource(R.string.vpn_settings) to onVpnSettings,
            )
        }

        // Работа в фоне (D-109): человек винит в обрывах VPN, а не телефон, который его
        // усыпил. Пока Android может усыплять ducker — просим разрешить; разрешил, но оболочка
        // телефона закрывает приложения сама (F-116, F-121), — ведём к её настройкам.
        if (state.running) {
            when (backgroundNotice) {
                is BackgroundNotice.Restricted -> InfoCard(
                    stringResource(R.string.background_restricted_card),
                    Symbols.ErrorOutline,
                    action = stringResource(R.string.background_allow) to backgroundNotice.onAllow,
                    secondary = stringResource(R.string.not_now) to backgroundNotice.onLater,
                )

                is BackgroundNotice.Shell -> InfoCard(
                    stringResource(R.string.background_hint, stringResource(backgroundNotice.vendor.names)),
                    Symbols.ErrorOutline,
                    action = stringResource(R.string.background_hint_action) to backgroundNotice.onSetUp,
                )

                null -> Unit
            }
        }

        if (state.loaded && profile == null) {
            // Сюда попадают, только когда подписки есть, а активной нет — её удалили: без подписок
            // вовсе вместо главной приветствие (D-98). Прежний текст говорил «подписок пока нет».
            EmptyState(
                icon = Symbols.Shield,
                title = stringResource(R.string.home_no_active_title),
                message = stringResource(R.string.home_no_active_text),
                action = stringResource(R.string.home_choose_subscription) to onChooseSubscription,
            )
        } else if (!state.loaded) {
            HomeSkeleton(compact)
        }

        // VPN остановился не по команде (D-227) — почему и что с этим делать. Без подписки
        // причина уже не к месту: об этом говорит заглушка выше.
        if (profile != null && state.stopFailure != null) {
            StopCard(state.stopFailure, profile, onRefresh, onSupport, onVpnSettings, onDismissStop)
        }

        // Туннель поднят, а связи нет — про это надо сказать прямо, иначе человек
        // видит «Подключено» и считает, что сломалось приложение (D-58).
        if (state.linkOk == false) {
            LinkFailedCard(state, onCheckLink, onOpenProxies, onFastest)
        }

        // Новая версия приложения: карточка висит, пока её не поставят, — обновление по
        // умолчанию решает человек, и напоминание должно быть на виду.
        appCards?.invoke()

        if (profile != null || state.running) {
            HeroCard(
                state = state,
                metrics = metrics,
                onToggle = onToggle,
                onOpenProxies = onOpenProxies,
                picker = picker,
                picking = picking,
                onPicking = { picking = it },
                onOpenPicker = onOpenPicker,
                onPickNode = onPickNode,
                onPreviewNode = onPreviewNode,
                compact = compact,
                modifier = Modifier.onGloballyPositioned { heroCard = it },
            )
        }

        // Свой конфиг — без срока, трафика и обновлений: карточке нечего показать (D-125).
        if (profile != null && !profile.local) {
            SubscriptionCard(profile, updating, updateFailed, onRefresh, onSupport)
        }

    }

    if (!tv && pickerMounted) {
        NodePickerSheet(
            open = picking,
            state = pickerState,
            onSort = onSortNodes,
            onCheck = onCheckNodes,
            initialGroup = picker?.group ?: state.groups.firstOrNull()?.name,
            onPick = { group, name ->
                // A service-specific group must not replace the main connection's node label.
                if (group == state.groups.firstOrNull()?.name) onPreviewNode(name)
                // A pick closes the sheet and is applied once it has gone: applying rebuilds the proxy
                // tab and would cost the closing frames (D-91), as with the old panel (D-116).
                pendingPick = group to name
                picking = false
            },
            onDismiss = { picking = false },
            onClosed = {
                pickerMounted = false
                pendingPick?.let { (group, name) ->
                    pendingPick = null
                    onPickNode(group, name)
                }
            },
        )
    }
}

/**
 * Заглушка на время, пока служба не ответила (D-77).
 *
 * Повторяет не картинку, а разметку: те же карточки с теми же отступами, только
 * содержимое серыми плашками. Поэтому, когда данные приезжают, ничего не прыгает —
 * высота уже занята. Полоса загрузки, которая стояла здесь раньше, мигала пол-секунды
 * и роняла весь экран вниз, когда исчезала.
 */
@Composable
private fun HomeSkeleton(compact: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = if (compact) 16.dp else 22.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 22.dp else 26.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Skeleton(height = 26.dp, width = 190.dp)
                Skeleton(height = 14.dp, width = 130.dp)
            }

            Box(
                modifier = Modifier.size(if (compact && !LocalTv.current) 96.dp else 112.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )

            Skeleton(height = 48.dp)
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Skeleton(height = 12.dp, width = 90.dp)
            Skeleton(height = 22.dp, width = 210.dp)
            Skeleton(height = 6.dp)
        }
    }
}

@Composable
private fun BrandHeader(profile: Profile?, onSettings: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
    ) {
        BrandTitle(profile)

        // The icons half as far apart as two 48 dp targets with a gap would put them: side by side,
        // the theme button 6 dp over the settings one, so neither target shrinks.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.offset(x = 6.dp)) { ThemeButton() }

            // Настройки с главной: раньше до них надо было идти через вкладку подписок.
            HeaderButton(
                icon = Symbols.Settings,
                description = stringResource(R.string.settings),
                onClick = onSettings,
            )
        }
    }
}

/** Марка в шапке: значок и название провайдера или наша. Занимает всё свободное место строки. */
@Composable
internal fun RowScope.BrandTitle(profile: Profile?) {
    // Шапку задаёт только `ducker-brand-name` — заголовок, который провайдер ставит
    // осознанно для этого приложения. `profile-title` сюда не годится: панель задаёт
    // его глобально для всех клиентов, и убрать его правилом нельзя (D-67).
    val brand = profile?.brandName?.takeIf { it.isNotBlank() }

    val logo = profile?.brandLogo?.takeIf { it.isNotBlank() }

    // Значок — только провайдерский. Свой рядом с собственным же названием
    // ничего не добавляет и забирает место у двух кнопок.
    if (logo != null) BrandAvatar(logo)

    // Two steps of the type scale larger on a TV: above the dock, seen from the sofa, the phone's size read small.
    val style = if (LocalTv.current) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineSmall

    if (brand != null) {
        Text(brand, modifier = Modifier.weight(1f), style = style,
            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    } else {
        Wordmark(style, Modifier.weight(1f))
    }
}

/**
 * Марка приложения. Прописными с разрядкой: то же слово, но набрано, а не просто
 * написано — в чёрно-белом стиле это единственный способ сделать шапку заметной.
 */
@Composable
private fun Wordmark(style: TextStyle, modifier: Modifier = Modifier) {
    Text(
        text = "DUCKER",
        modifier = modifier,
        style = style,
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        letterSpacing = 0.14.em,
        maxLines = 1,
    )
}

/**
 * Переключатель темы. Волна пойдёт от него же — отсюда и координаты (D-66). [button] — чем его
 * рисовать: на телевизоре кнопка другая, под доком.
 */
@Composable
internal fun ThemeButton(
    button: @Composable (icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier) -> Unit =
        { icon, description, onClick, modifier -> HeaderButton(icon, description, onClick, modifier) },
) {
    val switch = LocalThemeSwitch.current

    // Какая тема сейчас, видно по самой палитре — тащить настройку через все экраны
    // ради одного значка незачем.
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    var center by remember { mutableStateOf(Offset.Zero) }

    button(
        if (dark) Symbols.LightMode else Symbols.DarkMode,
        stringResource(R.string.theme),
        { switch?.toggle(center) },
        Modifier.onGloballyPositioned { center = it.boundsInRoot().center },
    )
}

/**
 * Главная карточка: состояние, кнопка питания и текущий узел.
 *
 * Подключённое состояние — сплошная чёрная заливка (D-65): в чёрно-белой системе
 * это самый сильный сигнал, и его видно с расстояния вытянутой руки, не читая подписи.
 *
 * Смена состояния — волна (D-93). Карточка рисуется дважды: внизу прежнее состояние,
 * сверху новое, и новое открывается кругом из кнопки питания. Всё, через что проходит
 * круг, — фон, заголовок, подпись, кнопка — уже новое, остальное ещё прежнее. Поэтому
 * ничего не перещёлкивается: раньше текст менялся мгновенно, цвет его тянулся отдельно
 * от волны, а значок в кнопке перекрашивался скачком посреди наката. Круг режет видеочип
 * по контуру слоя — перезаписывать отрисовку на каждом кадре не приходится.
 */
@Composable
private fun HeroCard(
    state: HomeState,
    metrics: () -> HomeMetrics,
    onToggle: () -> Unit,
    onOpenProxies: () -> Unit,
    picker: NodePicker?,
    /** Быстрая смена узла открыта (D-116). */
    picking: Boolean,
    onPicking: (Boolean) -> Unit,
    onOpenPicker: () -> Unit,
    onPickNode: (group: String, name: String) -> Unit,
    onPreviewNode: (name: String) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val active = state.running

    // Какое состояние лежит внизу целиком. Пока идёт волна, поверх него — новое.
    var settled by remember { mutableStateOf(active) }
    val wave = remember { Animatable(0f) }

    var card by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var center by remember { mutableStateOf(Offset.Unspecified) }
    var reach by remember { mutableFloatStateOf(0f) }

    // Нажатие одно на обе копии: иначе под волной и над ней кнопка проседала бы по-разному.
    val press = remember { MutableInteractionSource() }

    LaunchedEffect(active) {
        // Передумали посреди наката — просто убираем верхнюю копию.
        wave.snapTo(0f)

        if (active == settled) return@LaunchedEffect

        // The frame that composes the new copy — with the rest of Home rearranging after the change — is the heavy
        // one (70 ms on the emulator when VPN went off). It passes while the circle has no size yet: inside the
        // wave it made the circle jump at its start.
        withFrameNanos {}
        withFrameNanos {}

        wave.animateTo(1f, tween(WAVE_MS, easing = WaveEasing))

        settled = active

        wave.snapTo(0f)
    }

    fun locate(button: LayoutCoordinates) {
        val box = card?.takeIf { it.isAttached } ?: return

        if (!button.isAttached) return

        val c = box.localPositionOf(button, Offset(button.size.width / 2f, button.size.height / 2f))

        center = c
        reach = Size(box.size.width.toFloat(), box.size.height.toFloat()).farthestFrom(c)
    }

    // Быстрая смена узла (D-116): плашка узла раскрывается на всю карточку.
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    // Панель в композиции: и раскрытая, и пока сворачивается.
    var pickerShown by remember { mutableStateOf(false) }
    var nodeRow by remember { mutableStateOf(Rect.Zero) }
    val loadedPicker by rememberUpdatedState(picker)
    val requestNodes by rememberUpdatedState(onOpenPicker)

    // Пульт (этап 6): раскрытая панель забирает фокус — кнопка питания и плашка остаются под
    // ней и ловили бы его, — а после выбора узла или «назад» фокус возвращается на плашку.
    val keys = tvKeys()
    val tv = LocalTv.current
    val focusManager = LocalFocusManager.current
    val rowFocus = remember { FocusRequester() }
    val panelFocus = remember { FocusRequester() }
    val currentFocus = remember { FocusRequester() }

    // Панель ещё сворачивается поверх плашки, а фокус уже на ней: пока панель в дереве, она
    // его не держит, а после её ухода возврат не срабатывал.
    fun close() {
        if (keys) rowFocus.requestFocus()

        onPicking(false)
    }

    // Свежие задержки просим после раскрытия, а выбранный узел применяем после
    // сворачивания: и то и другое перерисовывает соседнюю вкладку «Прокси» целиком
    // (12–16 мс, D-91) и рвало бы кадры анимации. Сразу — только если узлов ещё нет.
    LaunchedEffect(picking, tv) {
        if (!tv) return@LaunchedEffect
        if (picking) {
            val loaded = loadedPicker != null

            if (!loaded) requestNodes()

            pickerShown = true

            progress.animateTo(1f, tween(PICKER_OPEN_MS, easing = FastOutSlowInEasing))

            if (loaded) requestNodes()
        } else if (pickerShown) {
            progress.animateTo(0f, tween(PICKER_CLOSE_MS, easing = FastOutSlowInEasing))

            pickerShown = false
        }
    }

    BackHandler(enabled = picking && tv) { close() }

    // Узлы «Прокси» могли ещё не загружаться: панель раскрывается сразу и ждёт их сама.
    val onNode = if (state.groups.isNotEmpty()) ({ onPicking(true) }) else onOpenProxies

    fun placeNodeRow(row: LayoutCoordinates) {
        val box = card?.takeIf { it.isAttached } ?: return

        if (row.isAttached) nodeRow = box.localBoundingBoxOf(row)
    }

    // Keep the existing inset and inner outline: both wave layers retain their exact
    // bounds. The outer surface is transparent and the card's shadow is its edge; in OLED,
    // where no shadow shows, the surface is the old ring again ([airEdge]).
    Card(
        // Пока панель раскрывается или сворачивается, рамки пульта нет: фокус переходит сразу, а
        // узлы ещё не выехали — или плашка ещё под панелью (D-161).
        modifier = modifier.fillMaxWidth().then(if (tv) Modifier.ringHold(progress.isRunning) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = if (airEdge() != null) MaterialTheme.colorScheme.outlineVariant else Color.Transparent,
        ),
        border = null,
        // The remote's node panel unfolds inside this card.
        resizing = tv,
    ) {
        Box(Modifier.fillMaxWidth().padding(HERO_RING).clip(HeroInner).onGloballyPositioned { card = it }) {
            HeroFace(
                running = settled,
                state = state,
                metrics = metrics,
                press = press,
                onToggle = onToggle,
                onNode = onNode,
                compact = compact,
                onButtonPlaced = ::locate,
                onNodePlaced = ::placeNodeRow,
                nodeFocus = Modifier.focusRequester(rowFocus),
                // Надписи прежнего состояния гаснут в первой трети волны, новые проявляются
                // во второй половине. Фронт проходит по пустому месту, и два разных слова не
                // встречаются на экране — без этого волна резала заголовок пополам.
                textAlpha = { if (settled != active) 1f - fade(wave.value, 0f, TEXT_OUT) else 1f },
            )

            if (settled != active) {
                HeroFace(
                    running = active,
                    state = state,
                    metrics = metrics,
                    press = press,
                    onToggle = onToggle,
                    onNode = onNode,
                    compact = compact,
                    onButtonPlaced = {},
                    onNodePlaced = {},
                    textAlpha = { fade(wave.value, TEXT_IN, 1f) },
                    modifier = Modifier.matchParentSize().graphicsLayer {
                        clip = true
                        shape = WaveShape(
                            center = if (center.isSpecified) center else Offset(size.width / 2f, size.height / 2f),
                            radius = reach * wave.value,
                        )
                    },
                )
            }

            if (pickerShown) {
                val scheme = MaterialTheme.colorScheme

                LaunchedEffect(Unit) { if (keys) panelFocus.requestFocus() }

                CompositionLocalProvider(
                    LocalContentColor provides if (active) scheme.onPrimaryContainer else scheme.onSurface,
                ) {
                    NodePickerPanel(
                        picker = picker,
                        from = nodeRow,
                        progress = { progress.value },
                        fill = if (active) scheme.primaryContainer else scheme.surface,
                        onPick = { group, name ->
                            close()
                            onPreviewNode(name)

                            // Своей корутиной: открыли панель снова посреди сворачивания —
                            // выбор всё равно должен примениться.
                            scope.launch {
                                delay(PICKER_CLOSE_MS.toLong())
                                onPickNode(group, name)
                            }
                        },
                        onAll = {
                            onPicking(false)
                            onOpenProxies()
                        },
                        modifier = Modifier.matchParentSize()
                            .then(
                                if (tv) {
                                    // Вверх с верхнего узла и вниз со строки «Все узлы» — закрыть панель, фокус
                                    // на плашке (D-161). Дальше идти некуда — значит, край: комната не выпускает.
                                    Modifier.onPreviewKeyEvent {
                                        val direction = when (it.key) {
                                            Key.DirectionUp -> FocusDirection.Up
                                            Key.DirectionDown -> FocusDirection.Down
                                            else -> null
                                        }

                                        if (direction == null) return@onPreviewKeyEvent false
                                        if (it.type == KeyEventType.KeyDown && !focusManager.moveFocus(direction)) close()

                                        true
                                    }.focusRoom(panelFocus, entry = currentFocus)
                                } else Modifier,
                            ),
                        current = currentFocus,
                    )
                }
            }
        }
    }
}

/** Круг волны. Круг выражен скруглённым прямоугольником — такой контур видеочип режет сам. */
private class WaveShape(private val center: Offset, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(Rect(center, radius), CornerRadius(radius)))
}

/** Одна копия карточки — целиком в одном состоянии. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroFace(
    running: Boolean,
    state: HomeState,
    metrics: () -> HomeMetrics,
    press: MutableInteractionSource,
    onToggle: () -> Unit,
    onNode: () -> Unit,
    compact: Boolean,
    onButtonPlaced: (LayoutCoordinates) -> Unit,
    /** Где стоит плашка узла: из неё раскрывается быстрая смена (D-116). */
    onNodePlaced: (LayoutCoordinates) -> Unit,
    /** Куда пульт возвращается после быстрой смены — только у нижнего, осевшего состояния. */
    nodeFocus: Modifier = Modifier,
    /** Прозрачность надписей, которые у двух состояний разные. Читается при отрисовке. */
    textAlpha: () -> Float,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val fill = if (running) scheme.primaryContainer else scheme.surface

    CompositionLocalProvider(LocalContentColor provides if (running) scheme.onPrimaryContainer else scheme.onSurface) {
        Column(
            modifier = modifier.fillMaxWidth()
                .background(fill)
                .padding(horizontal = 20.dp, vertical = if (compact) 16.dp else 22.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
        ) {
            FlowRow(
                modifier = Modifier.graphicsLayer { alpha = textAlpha() },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 2,
            ) {
                Column(Modifier.weight(1f).widthIn(min = 160.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    FitLabel(
                        text = stringResource(if (running) R.string.hero_protected else R.string.hero_off),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    )

                    // Пока служба не ответила, подпись говорит, что происходит, — и сменяется
                    // мягко: это единственный текст, который меняет не волна. Старая гаснет,
                    // потом проявляется новая: одновременно две полупрозрачные строки
                    // наезжали друг на друга.
                    val hint = when {
                        state.busy && running -> R.string.hero_disconnecting
                        state.busy -> R.string.hero_connecting
                        running -> R.string.hero_protected_hint
                        else -> R.string.hero_off_hint
                    }

                    // Копия сменила состояние, когда волна закончилась, — подпись встаёт сразу:
                    // иначе она доигрывала бы свой переход и на миг пропадала.
                    key(running) {
                        AnimatedContent(
                            targetState = hint,
                            transitionSpec = {
                                fadeIn(tween(HINT_IN_MS, delayMillis = HINT_OUT_MS)) togetherWith
                                    fadeOut(tween(HINT_OUT_MS))
                            },
                            label = "hint",
                        ) {
                            Text(
                                text = stringResource(it),
                                style = MaterialTheme.typography.bodyMedium,
                                color = LocalContentColor.current.copy(alpha = 0.7f),
                            )
                        }
                    }
                }

                UptimePill(running, metrics)
            }

            PowerButton(
                running = running,
                busy = state.busy,
                press = press,
                onToggle = onToggle,
                compact = compact,
                modifier = Modifier.align(Alignment.CenterHorizontally)
                    .onGloballyPositioned(onButtonPlaced),
            )

            if (state.groups.isNotEmpty() || state.profile != null) {
                NodeRow(state, onNode, Modifier.onGloballyPositioned(onNodePlaced).then(nodeFocus))
            }
        }
    }
}

/** Радиус, при котором круг из точки накрывает всю карточку. */
private fun Size.farthestFrom(point: Offset): Float = maxOf(
    hypot(point.x, point.y),
    hypot(width - point.x, point.y),
    hypot(point.x, height - point.y),
    hypot(width - point.x, height - point.y),
)

/** Сколько туннель работает. Прочерк, пока не работает, — плашка не пропадает. */
@Composable
private fun UptimePill(running: Boolean, metrics: () -> HomeMetrics) {
    Row(
        // Своим слоем: часы тикают каждую секунду и не должны тянуть за собой карточку.
        modifier = Modifier.graphicsLayer()
            .border(1.dp, LocalContentColor.current.copy(alpha = 0.22f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(6.dp)
                .clip(CircleShape)
                .background(LocalContentColor.current.copy(alpha = if (running) 1f else 0.4f)),
        )

        // Туннель остановился, и счётчик уже обнулён, а волна до плашки ещё не дошла:
        // держим последнее значение, пока его не накроет новое состояние.
        val last = remember { longArrayOf(0L) }
        val uptime = metrics().uptimeMs.also { if (it > 0) last[0] = it }

        // Выключенный туннель прошлое время не держит: иначе следующее подключение первую
        // секунду показывало бы время прошлого сеанса.
        if (!running) last[0] = 0

        Text(
            // Включённый туннель сразу показывает нули, а не прочерк: под волной и над ней
            // иначе стояли бы разные строки, и первая секунда перещёлкивала бы плашку.
            text = if (running) formatUptime(if (uptime > 0) uptime else last[0]) else "--:--:--",
            // Цифры одной ширины: пропорциональные каждую секунду чуть двигали строку.
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            color = LocalContentColor.current.copy(alpha = if (running) 1f else 0.7f),
            maxLines = 1,
        )
    }
}

/**
 * Кнопка питания (D-93). Нажатие — пружина: кнопка проседает под пальцем и мягко
 * возвращается. Ожидание ответа службы — тонкое кольцо вокруг кнопки; появляется, только
 * если служба думает дольше [RING_DELAY_MS], чтобы на быстром ответе ничего не мигало.
 * Прежняя кнопка на время ожидания просто тускнела, и было непонятно, идёт ли работа.
 */
@Composable
private fun PowerButton(
    running: Boolean,
    busy: Boolean,
    press: MutableInteractionSource,
    onToggle: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current

    val pressed by press.collectIsPressedAsState()
    // Пульт (D-161): в фокусе кнопка чуть вырастает — белую кнопку на тёмной карточке одной
    // рамкой с дивана не отличить. Источник нажатий общий у обеих копий, под волной и над ней.
    val lifted = LocalTv.current && press.collectIsFocusedAsState().value
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else if (lifted) TV_LIFT else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "press",
    )

    val face = LocalContentColor.current
    val fill = if (running) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface

    // A TV keeps the full-size button with the compact card around it: it is what the remote is there for.
    val small = compact && !LocalTv.current
    val diameter = if (small) 96.dp else 112.dp
    Box(modifier.size(diameter + 20.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = busy,
            enter = fadeIn(tween(200, delayMillis = RING_DELAY_MS)),
            exit = fadeOut(tween(150)),
        ) {
            // Вращение живёт, только пока кольцо на экране: в покое ничего не крутится (D-91).
            val turn by rememberInfiniteTransition(label = "ring").animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
                label = "turn",
            )

            Canvas(Modifier.fillMaxSize().graphicsLayer { rotationZ = turn }) {
                val stroke = 2.5.dp.toPx()

                drawArc(
                    color = face.copy(alpha = 0.5f),
                    startAngle = -90f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }

        Box(
            modifier = Modifier.size(diameter)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(face)
                // Пульт входит на главную сразу на кнопку питания (этап 6).
                .tabEntry()
                .clickable(
                    interactionSource = press,
                    indication = ripple(color = fill),
                    // С пульта на время подключения кнопка держит фокус (D-158).
                    enabled = keepFocus(!busy),
                    role = Role.Button,
                    onClickLabel = stringResource(if (running) R.string.ducker_disconnect else R.string.ducker_connect),
                    onClick = {
                        if (busy) return@clickable

                        // Отклик рукой: подключение — главное действие приложения. Системные
                        // типы сами уважают выключенную в настройках вибрацию.
                        haptic.performHapticFeedback(
                            if (running) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn
                        )

                        onToggle()
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Symbols.PowerSettingsNew,
                // Без названия TalkBack читал кнопку питания просто «кнопкой».
                contentDescription = stringResource(if (running) R.string.ducker_disconnect else R.string.ducker_connect),
                modifier = Modifier.size(if (small) 44.dp else 48.dp),
                // Значок — цвета карточки: кружок залит её содержимым наоборот.
                tint = fill,
            )
        }
    }
}

/** Плавный переход 0→1 на участке [from, to] хода волны. */
private fun fade(progress: Float, from: Float, to: Float): Float {
    val t = ((progress - from) / (to - from)).coerceIn(0f, 1f)

    return t * t * (3f - 2f * t)
}

/** Волна: быстрый выход из кнопки и долгое мягкое торможение к краям карточки. */
private val WaveEasing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)
/** Насколько кнопка питания вырастает под фокусом пульта. */
private const val TV_LIFT = 1.07f
/** Скругление плашки узла (`shapes.medium`) — рамке пульта. */
private val NODE_ROW_CORNER = 18.dp
/** «Подписка кончается»: меньше трёх дней (D-119). */
internal const val DAY_MS = 24 * 60 * 60 * 1000L
private const val SOON_MS = 3 * DAY_MS

/** Быстрая смена узла раскрывается и сворачивается (D-116). */
private const val PICKER_OPEN_MS = 380
private const val PICKER_CLOSE_MS = 300

/** Смена названия узла в плашке: старое гаснет, потом проявляется новое (D-116). */
private const val NODE_OUT_MS = 120
private const val NODE_IN_MS = 220

private const val WAVE_MS = 650

/** Кольцо обводки главной карточки — как обводка остальных карточек. */
private val HERO_RING = 1.dp

/** Скругление внутри кольца — на его ширину меньше, чем у карточки (`shapes.large`, 24dp). */
private val HeroInner = RoundedCornerShape(24.dp - HERO_RING)

/** Смена приветствия на главную и обратно (D-98). */
private const val ONBOARDING_MS = 360

/** До какой доли волны гаснут старые надписи и с какой проявляются новые. */
private const val TEXT_OUT = 0.3f
private const val TEXT_IN = 0.45f
private const val HINT_OUT_MS = 90
private const val HINT_IN_MS = 150
private const val RING_DELAY_MS = 150

/** Строка с текущим узлом внутри главной карточки. */
@Composable
private fun NodeRow(state: HomeState, onClick: () -> Unit, modifier: Modifier) {
    val group = state.groups.firstOrNull()

    // "Нет ответа" under the button is this screen's own verdict — two checks of the connection
    // in a row that failed, the same that brings the "нет связи" card. A check of the list that
    // one node did not answer does not put it here: the server may well be working (D-282).
    val delay = if (state.linkOk == false) DelayStore.NO_ANSWER else group?.delay ?: 0

    Row(
        modifier = modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(LocalContentColor.current.copy(alpha = PLATE_ALPHA))
            .then(if (LocalTv.current) Modifier.ringCorner(NODE_ROW_CORNER) else Modifier)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Сменился узел — старое название гаснет, новое проявляется (D-116): скачком
        // подмена бросалась в глаза. Свежая задержка того же узла — без анимации.
        AnimatedContent(
            targetState = group?.now.orEmpty() to delay,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                fadeIn(tween(NODE_IN_MS, delayMillis = NODE_OUT_MS)) togetherWith fadeOut(tween(NODE_OUT_MS))
            },
            contentKey = { it.first },
            label = "node",
        ) { (name, shownDelay) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Имя узла приходит от провайдера уже с флагом страны — своего значка здесь
                // не нужно, а название группы ничего не сообщает.
                Text(
                    text = name.ifEmpty { stringResource(R.string.proxies_tab) },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                // Значение приходит из общего хранилища замеров (D-72): «нет ответа» тут
                // означает, что замер был и не удался, а не что его ещё не делали.
                Text(
                    text = when {
                        shownDelay >= DelayStore.SLOW -> stringResource(R.string.delay_slow, shownDelay / 1000)
                        shownDelay > 0 -> stringResource(R.string.delay_ms, shownDelay)
                        shownDelay == DelayStore.NO_ANSWER -> stringResource(R.string.delay_failed)
                        else -> stringResource(R.string.value_unknown)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (shownDelay == DelayStore.NO_ANSWER) MaterialTheme.colorScheme.error
                    else LocalContentColor.current.copy(alpha = 0.7f),
                    maxLines = 1,
                )
            }
        }

        Icon(
            imageVector = Symbols.KeyboardArrowRight,
            contentDescription = null,
            tint = LocalContentColor.current.copy(alpha = 0.7f),
        )
    }
}

/** Часы работы туннеля: `02:41:18`. */
private fun formatUptime(ms: Long): String {
    val total = ms / 1000
    return "%02d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60)
}

/** Карточка про работу в фоне на главной (D-109). */
sealed interface BackgroundNotice {
    /** Android может усыплять ducker: разрешить или отложить на неделю. */
    class Restricted(val onAllow: () -> Unit, val onLater: () -> Unit) : BackgroundNotice

    /** Android разрешил, но оболочка телефона закрывает приложения сама (D-107). */
    class Shell(val vendor: Vendor, val onSetUp: () -> Unit) : BackgroundNotice
}

/**
 * Cards about the app's own version: “just updated” and “a new one is out”. The second one
 * follows the download when the sheet is closed.
 */
@Composable
private fun AppUpdateCards(
    update: String?,
    progress: Int?,
    installing: Boolean,
    onUpdate: () -> Unit,
    updated: String?,
    onWhatsNew: (() -> Unit)?,
    onUpdatedSeen: () -> Unit,
) {
    // The update closed the app and Android does not let it reopen itself: this is where the
    // person learns that it worked, with or without notifications.
    if (updated != null) {
        val seen = stringResource(R.string.dismiss) to onUpdatedSeen
        val whatsNew = onWhatsNew?.let { stringResource(R.string.app_update_whats_new) to it }

        InfoCard(
            stringResource(R.string.app_update_done_title, updated),
            Symbols.CheckCircle,
            action = whatsNew ?: seen,
            secondary = seen.takeIf { whatsNew != null },
        )
    }

    if (update != null) {
        InfoCard(
            when {
                installing -> stringResource(R.string.app_update_card_installing, update)
                progress != null -> stringResource(R.string.app_update_card_downloading, update, progress)
                else -> stringResource(R.string.app_update_card, update)
            },
            Symbols.SystemUpdate,
            // Nothing to press while it downloads: the installation follows by itself.
            action = (stringResource(R.string.update) to onUpdate).takeIf { !installing && progress == null },
        )
    }
}

@Composable
private fun AnnouncementCard(text: String) {
    var mounted by remember(text) { mutableStateOf(false) }
    var open by remember(text) { mutableStateOf(false) }
    val more = stringResource(R.string.announcement_read_more)
    val style = MaterialTheme.typography.bodyMedium
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Measure before drawing so the disclosure never adds a late row and shifts the page.
        val inset = 16.dp
        val iconSize = 24.dp
        val gap = 12.dp
        val textWidth = with(density) { (maxWidth - inset * 2 - iconSize - gap).roundToPx().coerceAtLeast(1) }
        val overflows = measurer.measure(
            text = text, style = style, maxLines = 3, overflow = TextOverflow.Ellipsis,
            constraints = Constraints(maxWidth = textWidth),
        ).hasVisualOverflow

        Card(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .then(if (overflows) Modifier.clickable(role = Role.Button, onClickLabel = more) {
                        mounted = true
                        open = true
                    } else Modifier)
                    .padding(inset),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Symbols.Campaign, contentDescription = null, modifier = Modifier.size(iconSize))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text, style = style, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (overflows) {
                        Text(more, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }

    if (mounted) {
        DuckerSheet(
            open = open, title = stringResource(R.string.provider_announcement), busy = false,
            onDismiss = { open = false }, onClosed = { mounted = false },
        ) {
            Card(Modifier.fillMaxWidth()) {
                SelectionContainer {
                    Text(text, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
                }
            }
            SheetButton(
                text = stringResource(R.string.done), busyText = stringResource(R.string.done),
                busy = false, enabled = true, onClick = { open = false },
            )
        }
    }
}

@Composable
private fun InfoCard(
    text: String,
    icon: ImageVector = Symbols.Campaign,
    action: Pair<String, () -> Unit>? = null,
    secondary: Pair<String, () -> Unit>? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = text, style = MaterialTheme.typography.bodyMedium)

                // The action black, «Не сейчас» and the like white beside it (D-326).
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    action?.let { (label, onClick) -> Pill(text = label, onClick = onClick, selected = true) }
                    secondary?.let { (label, onClick) -> Pill(text = label, onClick = onClick) }
                }
            }
        }
    }
}

/**
 * Лимит устройств (D-26). Профиль при этом остался прежним, поэтому важно объяснить,
 * почему подключение не работает, и дать оба выхода: написать в поддержку или обновить
 * подписку после отвязки устройства в панели.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LimitCard(profile: Profile, onSupport: (String) -> Unit, onRefresh: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(
                    when (profile.hwidReason) {
                        "max_devices" -> R.string.limit_max_devices
                        "not_supported" -> R.string.limit_not_supported
                        else -> R.string.limit_unknown
                    }
                ),
                fontWeight = FontWeight.SemiBold,
            )

            if (profile.announce.isNotEmpty()) {
                Text(text = profile.announce)
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                providerLink(profile.supportUrl)?.let { support ->
                    Pill(text = stringResource(R.string.support), onClick = { onSupport(support) }, selected = true)
                }

                Pill(text = stringResource(R.string.update), onClick = onRefresh)
            }
        }
    }
}

/**
 * VPN остановился не по команде (D-227): что случилось, что с этим делать и кнопка. Раньше была
 * строка на пару секунд, и только про один случай из четырёх.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StopCard(
    failure: Failure,
    profile: Profile,
    onRefresh: () -> Unit,
    onSupport: (String) -> Unit,
    onVpnSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val actions = buildList<Pair<String, () -> Unit>> {
        when (failure.kind.fix) {
            // Свой конфиг не обновляется (D-125) — и спросить за него некого.
            Failure.Fix.Retry -> if (!profile.local) {
                add(stringResource(R.string.update) to onRefresh)
                providerLink(profile.supportUrl)?.let { add(stringResource(R.string.support) to { onSupport(it) }) }
            }

            Failure.Fix.VpnSettings -> add(stringResource(R.string.vpn_settings) to onVpnSettings)

            else -> Unit
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, end = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    // Тот же заголовок, что у уведомления в шторке.
                    text = stringResource(failure.stopTitle),
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )

                IconButton(onClick = onDismiss) {
                    Icon(Symbols.Close, contentDescription = stringResource(R.string.dismiss))
                }
            }

            Text(text = failure.text(LocalContext.current), modifier = Modifier.padding(end = 12.dp))

            // Текст ядра для незнакомой ошибки — мелко и без адресов, как в карточке «нет связи».
            if (failure.detail.isNotBlank()) {
                Text(
                    text = LogScrubber.scrub(failure.detail),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 12.dp),
                )
            }

            if (actions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    actions.forEachIndexed { index, (label, onClick) ->
                        Pill(text = label, onClick = onClick, selected = index == 0)
                    }
                }
            }
        }
    }
}

@Composable
private fun LinkFailedCard(
    state: HomeState,
    onCheckLink: () -> Unit,
    onOpenProxies: () -> Unit,
    onFastest: (() -> Unit)?,
) {
    val tv = LocalTv.current
    // A step larger on a TV: beside the column of buttons the text has the room, and it is read from the sofa.
    val type = MaterialTheme.typography
    val body = if (tv) type.bodyMedium else type.bodySmall

    val about: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Symbols.WarningAmber, contentDescription = null,
                modifier = Modifier.size(if (tv) 22.dp else 20.dp))
            Text(stringResource(R.string.link_failed),
                style = if (tv) type.titleMedium else type.titleSmall, fontWeight = FontWeight.SemiBold)
        }

        Text(stringResource(R.string.link_failed_hint), style = body)

        if (state.linkError.isNotEmpty()) {
            // Причину показываем, адрес узла — нет: этот экран первым уходит
            // скриншотом в поддержку. Правило то же, что для выгрузки журнала
            // (инвариант 4), и чистильщик тот же.
            Text(nodeFailure(state.linkError)?.let { stringResource(it) } ?: LogScrubber.scrub(state.linkError),
                style = if (tv) type.bodySmall else type.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        // Не ответил ни один узел — менять узел бесполезно, дело в сети.
        if (state.noneAnswered) {
            Text(stringResource(R.string.link_none_answered), style = body, fontWeight = FontWeight.SemiBold)
        }
    }

    @Composable
    fun Fastest(modifier: Modifier) {
        if (onFastest != null) {
            Pill(
                text = stringResource(if (state.switching) R.string.link_fastest_busy else R.string.link_fastest),
                onClick = { if (!state.switching) onFastest() },
                modifier = modifier,
                selected = true,
                enabled = keepFocus(!state.switching),
                fit = true,
            )
        }
    }

    @Composable
    fun Check(modifier: Modifier) {
        Pill(
            text = stringResource(if (state.checking) R.string.checking else R.string.link_check),
            onClick = { if (!state.checking) onCheckLink() },
            modifier = modifier,
            enabled = keepFocus(!state.checking),
            fit = true,
        )
    }

    @Composable
    fun Other(modifier: Modifier) {
        Pill(
            text = stringResource(R.string.link_other_node),
            onClick = onOpenProxies,
            modifier = modifier,
            fit = true,
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        if (tv) {
            // The TV's screen is short and the card wide: what happened on the left, the ways out on the right,
            // one under another and as wide as the longest of their labels, the busy ones included — a press
            // does not move them. Stacked under the text, the card took a third of the screen.
            val width = labelWeights(
                listOf(
                    stringResource(R.string.link_fastest), stringResource(R.string.link_fastest_busy),
                    stringResource(R.string.link_check), stringResource(R.string.checking),
                    stringResource(R.string.link_other_node),
                ),
                MaterialTheme.typography.labelLarge,
                PILL_EDGE,
            ).max()

            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    about()
                }

                // No gap of their own: a pill's touch margin already parts them, and the card stays lower.
                Column(Modifier.width(with(LocalDensity.current) { width.toDp() })) {
                    Fastest(Modifier.fillMaxWidth())
                    Check(Modifier.fillMaxWidth())
                    Other(Modifier.fillMaxWidth())
                }
            }
        } else {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                about()

                // Главное действие — во всю ширину: рядом с двумя другими кнопками длинная
                // подпись не влезает, а искать узел руками человеку в этот момент некогда.
                Fastest(Modifier.fillMaxWidth().heightIn(min = 44.dp))

                // Обе кнопки делят строку поровну — во всю ширину, как кнопка над ними.
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Check(Modifier.weight(1f))
                    Other(Modifier.weight(1f))
                }
            }
        }
    }
}

/** A pill's side padding ([Pill]): its label's width plus this on both sides is the whole pill. */
private val PILL_EDGE = 14.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubscriptionCard(
    profile: Profile,
    /** Подписка сейчас обновляется — с главной или с вкладки «Подписки». */
    updating: Boolean,
    /** Последнее обновление кончилось ошибкой. */
    updateFailed: Boolean,
    onRefresh: () -> Unit,
    onOpen: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // «Сейчас» — заново при каждом возврате на экран и раз в час: главная бывает открыта сутками,
    // и срок оставался вчерашним (аудит 9в.8).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val now by produceState(System.currentTimeMillis(), profile) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                value = System.currentTimeMillis()
                delay(3_600_000)
            }
        }
    }
    val used = profile.upload + profile.download

    // Подписка кончается — справа красным, сколько осталось, а вторая кнопка — «Продлить».
    val left = when (val end = ending(profile.expire, now, profile.total, used)) {
        Ending.Expired -> stringResource(R.string.subscription_expired)
        is Ending.Days -> pluralStringResource(R.plurals.days_left, end.days, end.days)
        is Ending.Traffic -> stringResource(R.string.traffic_left, formatBytes(end.bytes))
        null -> null
    }

    // Вторая кнопка — только если провайдер прислал ссылку (D-119).
    val renew = firstProviderLink(profile.buyUrl, profile.supportUrl, profile.profileWebPageUrl)
    val support = providerLink(profile.supportUrl)
    val website = providerLink(profile.profileWebPageUrl)
    val link = when {
        left != null && renew != null -> Triple(R.string.renew, Symbols.CreditCard, renew)
        support != null -> Triple(R.string.support, Symbols.SupportAgent, support)
        website != null -> Triple(R.string.provider_site, Symbols.Language, website)
        else -> null
    }

    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // One line on a TV as well: there the date under its own label cost the short screen a row.
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (profile.expire > 0) stringResource(R.string.valid_until, formatDate(profile.expire))
                    else stringResource(R.string.traffic_unlimited),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Normal,
                )

                if (left != null) {
                    Text(
                        text = left,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.error,
                    )
                }
            }

            // Полоса — во всю ширину карточки: рядом с кнопкой она была короткой
            // и упиралась в «Обновить».
            if (profile.total > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ThinBar((used.toFloat() / profile.total).coerceIn(0f, 1f))

                    // Сколько израсходовано — под полосой, а не над ней: сначала видно
                    // саму полосу, потом цифры к ней.
                    Text(
                        text = "${formatBytes(used)} ${stringResource(R.string.ducker_traffic_of, formatBytes(profile.total))}",
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }

            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val updateLabels = listOf(
                    stringResource(R.string.update), stringResource(R.string.updating),
                    stringResource(R.string.updated), stringResource(R.string.update_failed_short),
                )
                val linkLabel = link?.let { stringResource(it.first) }
                // Include every update outcome so clicking Update never rearranges the buttons.
                val widths = labelWeights(updateLabels + listOfNotNull(linkLabel), MaterialTheme.typography.labelMedium, 23.dp)
                val half = with(LocalDensity.current) { ((maxWidth - 8.dp) / 2).toPx() }
                val stacked = link != null && widths.any { it > half }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = if (stacked) 1 else 2,
                ) {
                    UpdatePill(updating, updateFailed, onRefresh, Modifier.weight(1f))

                    if (link != null) {
                        val (_, icon, url) = link

                        CardPill(onClick = { onOpen(url) }, modifier = Modifier.weight(1f)) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(
                                text = linkLabel.orEmpty(),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(start = 6.dp),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Подписка кончается (D-119, этап 10). */
internal sealed interface Ending {
    data object Expired : Ending
    data class Days(val days: Int) : Ending
    data class Traffic(val bytes: Long) : Ending
}

/**
 * Кончается ли подписка: срок — меньше трёх дней (дни — с округлением вверх: 2,5 дня — это
 * «осталось 3»), трафик — меньше десятой части. Срок важнее: без него трафик не поможет.
 * `null` — всё спокойно, карточка без предупреждения.
 */
internal fun ending(expire: Long, now: Long, total: Long, used: Long): Ending? {
    val left = expire - now

    return when {
        expire > 0 && left <= 0 -> Ending.Expired
        expire > 0 && left < SOON_MS -> Ending.Days(((left + DAY_MS - 1) / DAY_MS).toInt())
        total > 0 && total - used < total / 10 -> Ending.Traffic((total - used).coerceAtLeast(0))
        else -> null
    }
}

/**
 * «Обновить» с ответом (D-119): раньше после нажатия не было понятно, обновилась подписка
 * или загрузка упала. Пока идёт — кольцо и «Обновляю…», потом на пару секунд «Обновлено»
 * или «Не удалось», и кнопка возвращается.
 */
@Composable
private fun UpdatePill(updating: Boolean, failed: Boolean, onRefresh: () -> Unit, modifier: Modifier) {
    val shown = rememberUpdateShown(updating, failed)

    val active = !updating && shown != UpdateShown.Busy

    CardPill(onClick = { if (active) onRefresh() }, enabled = keepFocus(active), modifier = modifier) {
        UpdateLabel(shown, MaterialTheme.typography.labelMedium, 16.dp)
    }
}

/** Кнопка в карточке подписки: серая пилюля, как была «Обновить». */
@Composable
private fun CardPill(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier = modifier.heightIn(min = 40.dp),
        content = content,
    )
}
