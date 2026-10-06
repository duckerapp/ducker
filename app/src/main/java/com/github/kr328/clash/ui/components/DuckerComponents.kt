package com.github.kr328.clash.ui.components

import android.os.SystemClock
import java.net.URLDecoder
import java.util.Base64
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.github.kr328.clash.R
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.shape.RoundedCornerShape

/** Vertical insets arrive in the modifier from HomeActivity's Scaffold. */
@Composable
fun Modifier.screenInsets(): Modifier = windowInsetsPadding(
    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
).focusGroup().semantics { isTraversalGroup = true }

@Composable
fun DuckerCard(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface,
    ),
    shape: Shape = MaterialTheme.shapes.large,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = shape,
        colors = colors,
        border = border,
        content = content,
    )
}

/**
 * Что отдать Coil вместо строки картинки провайдера — логотипа или значка группы: ссылку — как
 * есть, а `data:` — байтами. Строку `data:` Coil 2.7 не понимает (F-184), хотя заголовок
 * `ducker-brand-logo` её обещает (D-35). Ссылка — только в сеть: Coil открыл бы и `file://`, и
 * `content://`, а адрес задаёт провайдер (аудит 9в.7).
 */
internal fun logoModel(logo: String?): Any? {
    val value = logo?.trim().orEmpty()

    if (value.isEmpty()) return null
    if (!value.startsWith("data:", ignoreCase = true)) return value.takeIf {
        it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true)
    }

    val payload = value.substringAfter(',', "")

    return runCatching {
        if (value.substringBefore(',').endsWith(";base64", ignoreCase = true)) Base64.getMimeDecoder().decode(payload)
        else URLDecoder.decode(payload, "UTF-8").toByteArray()
    }.getOrNull()?.takeIf { it.isNotEmpty() }
}

@Composable
fun BrandAvatar(logo: String?, modifier: Modifier = Modifier) {
    // Если провайдер логотип не прислал или он не загрузился — свой (D-45), под тему
    // приложения (D-94): чёрная утка на светлом фоне, белая на тёмном. Тему приложение
    // держит само, поэтому ресурсы `-night` за ней не успевают — смотрим на палитру.
    val own = painterResource(
        if (MaterialTheme.colorScheme.background.luminance() < 0.5f) R.drawable.ducker_mark_dark
        else R.drawable.ducker_mark_light
    )

    AsyncImage(
        model = remember(logo) { logoModel(logo) },
        placeholder = own,
        error = own,
        fallback = own,
        contentDescription = null,
        modifier = modifier.size(52.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

@Composable
fun IconBadge(icon: ImageVector, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(44.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    /** Что сделать, чтобы здесь стало не пусто: подпись и действие. Главной кнопкой, как в панелях. */
    action: Pair<String, () -> Unit>? = null,
) {
    DuckerCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            IconBadge(icon, Modifier.size(64.dp))
            Text(title, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(message, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)

            action?.let { (label, onClick) ->
                SheetButton(text = label, busyText = label, busy = false, enabled = true, onClick = onClick)
            }
        }
    }
}

/**
 * Строка поиска в стиле остального интерфейса: белая пилюля с волосяной рамкой,
 * без плавающей подписи и без подчёркивания — в чёрно-белой системе рамка и есть
 * единственное, чем поле отличается от фона.
 */
@Composable
fun SearchField(
    value: String,
    onValue: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape

    // Пульт (этап 6): строка — одна кнопка, ввод открывается по ОК, см. [keyField].
    val (row, input) = keyField()

    Row(
        modifier = modifier.fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .then(row)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Подсказка — внутри поля, а не рядом: так TalkBack читает её как название поля,
        // а не отдельной строкой перед безымянным «полем ввода».
        BasicTextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).then(input),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium
                .copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            decorationBox = { field ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    field()
                }
            },
        )

        // Кнопка очистки появляется только когда есть что очищать, но место под неё
        // зарезервировано всегда — иначе строка дёргается на первом же символе.
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValue("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.search_clear),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Серая плашка на месте будущего содержимого.
 *
 * Без мерцания и переливов: они стоят кадров и выглядят дешевле, чем сама задержка,
 * которую прикрывают. Смысл заглушки в другом — занять ровно ту высоту, которую займут
 * настоящие карточки, чтобы экран не прыгал, когда данные приедут (D-77).
 */
@Composable
fun Skeleton(
    modifier: Modifier = Modifier,
    height: Dp = 16.dp,
    width: Dp? = null,
) {
    Box(
        modifier = modifier
            .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
            .height(height)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/**
 * Пружина на краю (D-137) — только там, где есть что листать (D-91). Экран, который
 * помещается целиком, тянуть некуда, и «резина» на нём выглядит поломкой; в длинном
 * списке она честно говорит, что дальше ничего нет.
 */
@Composable
fun overscrollFor(state: ScrollableState): BounceOverscroll? {
    val scrollable by remember(state) {
        derivedStateOf { state.canScrollForward || state.canScrollBackward }
    }
    val scope = rememberCoroutineScope()
    val bounce = remember(scope) { BounceOverscroll(scope) }

    return if (scrollable) bounce else null
}

/**
 * Круглая кнопка шапки — на всех экранах одна (D-92). Место под палец — 48: меньше
 * Google не советует (D-91). Без подложки (D-97): бледный серый круг на светлом фоне
 * выглядел пятном; кружок виден только волной нажатия.
 */
@Composable
fun HeaderButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.minimumInteractiveComponentSize()
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Шапка экрана, одна на всё приложение (D-92): стрелка назад у экранов поверх вкладок,
 * заголовок и действия справа теми же круглыми кнопками, что на главной. Раньше у каждого
 * экрана был свой заголовок, а у настроек и журнала не было пути назад, кроме жеста.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onBack != null) {
            HeaderButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                description = stringResource(R.string.back),
                onClick = onBack,
            )
        }

        FitLabel(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f).padding(start = if (onBack != null) 4.dp else 0.dp),
        )

        actions()
    }
}

/** Шире этого карточки и строки читать тяжело: планшет, складной, альбомный режим (D-92). */
val ReadableWidth = 640.dp

/**
 * Рамка экрана (D-92): содержимое не шире [ReadableWidth] и по центру, а сверху — подложка
 * под часами и значками. Экраны рисуются до самого верха, и пролистанные карточки уезжали
 * под строку состояния: в светлой теме значки сливались с текстом. Подложка цвета фона,
 * поэтому, пока ничего не пролистано, её не видно. [wide] — во всю ширину: редактору конфига
 * телефона боком каждая колонка на счету. [entry] — куда пульт входит в экран впервые, см. [tabEntry].
 */
@Composable
fun ScreenFrame(wide: Boolean = false, entry: FocusRequester? = null, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.align(Alignment.TopCenter).widthIn(max = if (wide) Dp.Unspecified else ReadableWidth).fillMaxWidth().fillMaxHeight(),
        ) {
            CompositionLocalProvider(LocalTabEntry provides entry, content = content)
        }

        val color = MaterialTheme.colorScheme.background
        val bar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

        Box(
            Modifier.fillMaxWidth().height(bar + 12.dp).drawBehind {
                val solid = (bar.toPx() / size.height).coerceIn(0f, 1f)

                drawRect(
                    Brush.verticalGradient(
                        0f to color,
                        solid to color.copy(alpha = 0.9f),
                        1f to color.copy(alpha = 0f),
                    ),
                )
            },
        )
    }
}

/**
 * «Потянуть, чтобы обновить» (D-92). Кружок встаёт под строку состояния, а не за неё:
 * экраны рисуются до самого верха, и штатное место индикатора оказалось бы под часами.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullToRefresh(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    top: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val state = rememberPullToRefreshState()
    // Пульт (этап 6): тянуть нечем, а к элементу в фокусе Compose прокручивает тем же путём, что и
    // жест (F-190), — у верхнего края список «тянулся» сам и запускал обновление. С клавишами
    // жеста нет, и кружка тоже: замер и так виден на своей группе.
    val keys = tvKeys()

    Box(modifier.pullToRefresh(isRefreshing = refreshing, state = state, enabled = !keys, onRefresh = onRefresh)) {
        content()

        if (!keys) {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = top),
            )
        }
    }
}

/**
 * Тонкая полоса расхода трафика. Цвета — параметрами: на залитой карточке подписки
 * полоса берёт цвет текста карточки (D-102).
 */
@Composable
fun ThinBar(
    fraction: Float,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    LinearProgressIndicator(
        progress = { fraction },
        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
        color = color,
        trackColor = trackColor,
        drawStopIndicator = {},
    )
}

/**
 * Ряд кнопок с подписями, делящих ширину (D-114). Каждой — по ширине её подписи: где места
 * хватает, все стоят в полный размер, где не хватает — все подписи ужимаются одинаково, а
 * не последняя переносится по буквам. [padding] — отступ подписи от края кнопки, с одной стороны.
 */
@Composable
fun labelWeights(labels: List<String>, style: TextStyle, padding: Dp): List<Float> {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    return remember(labels, style, density) {
        val edges = with(density) { padding.toPx() } * 2

        labels.map { measurer.measure(it, style, maxLines = 1, softWrap = false).size.width + edges }
    }
}

/** Подпись такой кнопки: одной строкой, а не влезла — шрифт мельче, но слово целиком. */
@Composable
fun FitLabel(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Text(
        text,
        modifier = modifier,
        color = color,
        style = style,
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = style.fontSize),
    )
}

/** Что показывает кнопка «Обновить» подписки (D-119). */
enum class UpdateShown { Idle, Busy, Done, Failed }

/**
 * Состояние кнопки «Обновить» — общее у главной и «Подписок»: пока идёт — кольцо, потом на пару
 * секунд итог. Кольцо держится не меньше [MIN_SPIN_MS]: подписка часто обновляется за полсекунды,
 * и «Обновляю…» только мелькало.
 */
@Composable
fun rememberUpdateShown(updating: Boolean, failed: Boolean): UpdateShown {
    var shown by remember { mutableStateOf(if (updating) UpdateShown.Busy else UpdateShown.Idle) }
    var since by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    val failedNow by rememberUpdatedState(failed)

    LaunchedEffect(updating) {
        if (updating) {
            since = SystemClock.uptimeMillis()
            shown = UpdateShown.Busy
        } else if (shown == UpdateShown.Busy) {
            delay(since + MIN_SPIN_MS - SystemClock.uptimeMillis())
            shown = if (failedNow) UpdateShown.Failed else UpdateShown.Done
            delay(OUTCOME_MS)
            shown = UpdateShown.Idle
        }
    }

    return shown
}

/** Подпись кнопки «Обновить»: значок и слово сменяются растворением. «Не удалось» — красным. */
@Composable
fun UpdateLabel(shown: UpdateShown, style: TextStyle, iconSize: Dp) {
    AnimatedContent(
        targetState = shown,
        transitionSpec = { fadeIn(tween(200, delayMillis = 60)) togetherWith fadeOut(tween(100)) },
        label = "update",
    ) { state ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            val color = if (state == UpdateShown.Failed) MaterialTheme.colorScheme.error else LocalContentColor.current
            val icon = Modifier.size(iconSize)

            when (state) {
                UpdateShown.Busy -> CircularProgressIndicator(icon.padding(1.dp), strokeWidth = 2.dp, color = color)
                UpdateShown.Done -> Icon(Icons.Default.Check, contentDescription = null, modifier = icon)
                UpdateShown.Failed -> Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = color, modifier = icon)
                UpdateShown.Idle -> Icon(Icons.Default.Refresh, contentDescription = null, modifier = icon)
            }

            FitLabel(
                text = stringResource(
                    when (state) {
                        UpdateShown.Busy -> R.string.updating
                        UpdateShown.Done -> R.string.updated
                        UpdateShown.Failed -> R.string.update_failed_short
                        UpdateShown.Idle -> R.string.update
                    },
                ),
                style = style,
                color = color,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

/** Сколько держится «Обновлено» или «Не удалось» на кнопке. */
private const val OUTCOME_MS = 2500L

/** Меньше кольцо не крутится, как бы быстро ни пришёл ответ. */
private const val MIN_SPIN_MS = 700L
