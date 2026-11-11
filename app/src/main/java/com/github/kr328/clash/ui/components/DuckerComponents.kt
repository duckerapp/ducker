package com.github.kr328.clash.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.annotation.DrawableRes
import java.net.URLDecoder
import java.util.Base64
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.Layout
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.Image
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.LayerOutsets
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import coil3.compose.AsyncImage
import com.github.kr328.clash.R
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.shape.RoundedCornerShape

/** Vertical insets arrive in the modifier from HomeActivity's Scaffold. */
@Composable
fun Modifier.screenInsets(): Modifier = windowInsetsPadding(
    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
).focusGroup().semantics { isTraversalGroup = true }

/** The custom subscription backing shares the cards' light direction and softness. */
internal val DuckerCardShadow = Shadow(
    radius = 8.dp,
    color = Color.Black.copy(alpha = 0.10f),
    offset = DpOffset(0.dp, 3.dp),
)

/** Room for [DuckerCardShadow] around a faded layer: below full alpha it draws offscreen, cut to its bounds. */
internal val DuckerShadowOutsets = LayerOutsets(left = 12.dp, top = 12.dp, right = 12.dp, bottom = 12.dp)

/**
 * The OLED theme makes a surface as black as the screen behind it, and no shadow shows on black:
 * there the surface keeps the hairline it had before the soft shadows. [fill] — the surface's color.
 */
@Composable
internal fun airEdge(fill: Color = MaterialTheme.colorScheme.surface): BorderStroke? {
    val scheme = MaterialTheme.colorScheme

    return if (fill == scheme.background) BorderStroke(1.dp, scheme.outlineVariant) else null
}

/** [DuckerCardShadow], or the hairline of [airEdge] where a shadow cannot show. */
@Composable
internal fun Modifier.airShadow(shape: Shape, fill: Color = MaterialTheme.colorScheme.surface): Modifier =
    airEdge(fill)?.let { border(it, shape) } ?: dropShadow(shape, DuckerCardShadow)

/**
 * The shadow of a surface whose size animates. [dropShadow] keeps one blurred mask per size in a
 * window-wide cache that is emptied only when the app goes to the background, so each frame of a
 * height animation would blur a new mask and keep it. The platform outline shadow has no mask; its
 * values were matched to [DuckerCardShadow] on the config editor.
 */
internal fun Modifier.resizingShadow(shape: Shape): Modifier = shadow(
    3.dp, shape, clip = false,
    ambientColor = Color.Black.copy(alpha = 0.5f),
    spotColor = Color.Black.copy(alpha = 0.5f),
)

/**
 * [surfaceModifier] keeps clipped press feedback inside the card's outer shadow.
 * [resizing] — the card's height animates, see [resizingShadow].
 */
@Composable
fun DuckerCard(
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface,
    ),
    shape: Shape = MaterialTheme.shapes.large,
    border: BorderStroke? = null,
    surfaceModifier: Modifier = Modifier,
    resizing: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val edge = airEdge(colors.containerColor)

    Card(
        modifier = modifier
            .then(
                when {
                    edge != null -> Modifier
                    resizing -> Modifier.resizingShadow(shape)
                    else -> Modifier.dropShadow(shape, DuckerCardShadow)
                },
            )
            .then(surfaceModifier),
        shape = shape,
        colors = colors,
        border = border ?: edge,
        content = content,
    )
}

/**
 * Что отдать Coil вместо строки картинки провайдера — логотипа или значка группы: ссылку — как
 * есть, а `data:` — байтами. Coil 3 сам читает `data:` только в строгом base64: ни текстом, ни
 * base64 с переносами строк (F-184, D-332), а заголовок `ducker-brand-logo` обещает любой (D-35).
 * Ссылка — только в сеть: Coil открыл бы и `file://`, и `content://`, а адрес задаёт провайдер
 * (аудит 9в.7).
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
    val own = smoothResource(
        if (MaterialTheme.colorScheme.background.luminance() < 0.5f) R.drawable.ducker_mark_dark
        else R.drawable.ducker_mark_light,
        52.dp,
    )

    val model = remember(logo) { logoModel(logo) }
    var failed by remember(model) { mutableStateOf(false) }

    // Ours is drawn directly, as on the welcome screen and in «О приложении». Through Coil it kept the
    // tile of the theme it was first drawn in after the theme changed; and the grey under it showed as a
    // hairline at the clipped edge — across the duck where it peeks over the tile's bottom.
    if (model == null || failed) {
        Image(
            bitmap = own,
            contentDescription = null,
            modifier = modifier.size(52.dp).clip(MaterialTheme.shapes.medium),
        )
        return
    }

    AsyncImage(
        model = model,
        placeholder = remember(own) { BitmapPainter(own) },
        onError = { failed = true },
        contentDescription = null,
        modifier = modifier.size(52.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/**
 * A square raster drawn small without jagged edges, [size] across. Android draws a bitmap with bilinear filtering and no
 * mipmaps (HWUI, `RecordingCanvas.cpp`: `SkMipmapMode::kNone`), so the 512 px duck in 52 dp — 104 px on a 1080p TV —
 * took about one pixel in five and came out pixelated. Halved step by step, each pixel is the average of four, and the
 * last step lands on the exact size at any density.
 */
@Composable
internal fun smoothResource(@DrawableRes id: Int, size: Dp): ImageBitmap {
    val resources = LocalContext.current.resources
    val px = with(LocalDensity.current) { size.roundToPx() }

    return remember(id, px) {
        var bitmap = BitmapFactory.decodeResource(resources, id)
        while (bitmap.width >= px * 2) bitmap = Bitmap.createScaledBitmap(bitmap, bitmap.width / 2, bitmap.height / 2, true)

        Bitmap.createScaledBitmap(bitmap, px, px, true).asImageBitmap()
    }
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

/** A soft shadow separates the search surface; floating lift is read during shadow drawing. */
@Composable
fun SearchField(
    value: String,
    onValue: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    shadowLift: () -> Float = { 0f },
    compact: Boolean = false,
    /** On a TV, where «Готово» takes the frame: the first result (D-377). */
    next: FocusRequester? = null,
) {
    val visualHeight = with(LocalDensity.current) {
        MaterialTheme.typography.bodyMedium.lineHeight.toDp() + 8.dp
    }.coerceAtLeast(44.dp)
    val shape = remember(compact, visualHeight) { if (compact) CompactSearchShape(visualHeight) else CircleShape }

    // Пульт (этап 6): строка — одна кнопка, ввод открывается по ОК, см. [keyField].
    val (row, input) = keyField()
    val edge = airEdge()

    Box(
        modifier = modifier.fillMaxWidth()
            .then(when {
                edge != null -> Modifier.border(edge, shape)
                compact -> Modifier.dropShadow(shape, DuckerCardShadow)
                else -> Modifier.dropShadow(shape) {
                    val lift = shadowLift().coerceIn(0f, 1f)
                    // Keep blur geometry fixed while scrolling so the cached mask stays reusable.
                    radius = DuckerCardShadow.radius.toPx()
                    color = DuckerCardShadow.color.copy(alpha = DuckerCardShadow.color.alpha * (1f + 0.4f * lift))
                    offset = Offset(
                        DuckerCardShadow.offset.x.toPx(), DuckerCardShadow.offset.y.toPx(),
                    )
                }
            })
            .then(
                if (compact) Modifier.background(MaterialTheme.colorScheme.surface, shape)
                else Modifier.clip(shape).background(MaterialTheme.colorScheme.surface),
            )
            .then(row),
    ) {
        // Decoration belongs to the input so its icon, padding and empty clear slot also accept taps.
        BasicTextField(
            value = value,
            onValueChange = onValue,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).then(input)
                .semantics { contentDescription = hint },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium
                .copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            // Search's own key did nothing, and the keyboard stayed over the list (F-362).
            keyboardActions = tvDone(next) ?: KeyboardActions.Default,
            decorationBox = { field ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp),
                ) {
                    Icon(
                        imageVector = Symbols.Search,
                        contentDescription = null,
                        modifier = Modifier.size(if (compact) 21.dp else 18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    // Keep the hint inside the input's semantics, including when the query is empty.
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            // Smaller rather than cut, as a button's label: «Узел или про…» under the largest font.
                            FitLabel(
                                text = hint,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.clearAndSetSemantics {},
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        field()
                    }

                    // Reserve the clear button's space so typing the first character does not shift text.
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        if (value.isNotEmpty()) {
                            IconButton(onClick = { onValue("") }) {
                                Icon(
                                    imageVector = Symbols.Close,
                                    contentDescription = stringResource(R.string.search_clear),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
        )
    }
}

/** The small search surface stays centered inside its unchanged 48 dp input target. */
private data class CompactSearchShape(val height: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val visible = with(density) { height.toPx() }.coerceAtMost(size.height)
        val top = (size.height - visible) / 2f
        return Outline.Rounded(
            RoundRect(
                Rect(0f, top, size.width, top + visible),
                CornerRadius(visible / 2f),
            ),
        )
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
    val enlarged = LocalDensity.current.fontScale > 1f

    // The actions go under the title when an enlarged system font leaves the title no room for its full size:
    // on a narrow phone «Прокси» shrank to a few pixels beside «Rules» and «По порядку». At the normal size the
    // title shrinks beside them, as before.
    Layout(
        contents = listOf(
            {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HEADER_GAP)) {
                    if (onBack != null) {
                        HeaderButton(
                            icon = Symbols.ArrowBack,
                            description = stringResource(R.string.back),
                            onClick = onBack,
                        )
                    }

                    FitLabel(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.weight(1f).padding(start = if (onBack != null) 4.dp else 0.dp),
                    )
                }
            },
            {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HEADER_GAP)) {
                    actions()
                }
            },
        ),
        modifier = modifier.fillMaxWidth().then(
            if (onBack != null) Modifier.layout { measurable, constraints ->
                // Let the 48dp touch target enter the gutter; the arrow aligns with section labels.
                val gutter = 12.dp.roundToPx()
                val placeable = measurable.measure(constraints.offset(horizontal = gutter))
                layout(placeable.width - gutter, placeable.height) {
                    placeable.placeRelative(-gutter, 0)
                }
            } else Modifier,
        ),
    ) { (leads, rows), constraints ->
        val width = constraints.maxWidth
        val row = rows.first().measure(Constraints(maxWidth = width))
        val gap = if (row.width > 0) HEADER_GAP.roundToPx() else 0
        val lead = leads.first()
        val stacked = enlarged && row.width > 0 && lead.maxIntrinsicWidth(Constraints.Infinity) + gap + row.width > width

        if (stacked) {
            val line = lead.measure(Constraints.fixedWidth(width))

            layout(width, line.height + row.height) {
                line.placeRelative(0, 0)
                row.placeRelative(width - row.width, line.height)
            }
        } else {
            val line = lead.measure(Constraints.fixedWidth((width - gap - row.width).coerceAtLeast(0)))
            val height = maxOf(line.height, row.height)

            layout(width, height) {
                line.placeRelative(0, (height - line.height) / 2)
                row.placeRelative(width - row.width, (height - row.height) / 2)
            }
        }
    }
}

private val HEADER_GAP = 8.dp

/**
 * Окно ниже 480dp (граница компактной высоты у Material) — телефон боком или половина экрана.
 * Планшет таким не бывает и боком. Редактору конфига тогда нужна каждая строка (D-145).
 * The dock stands at the side then and the status bar hides (D-480).
 */
// From the configuration, not `LocalWindowInfo.containerSize`: on Android 12 (MIUI) that lags one turn
// behind — upright again, it still gave the sideways size, and the editor hid the status bar (D-478).
@Composable
fun lowWindow(): Boolean = LocalConfiguration.current.screenHeightDp < 480

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
        // A soft veil, as the fade at the bottom (D-481): solid under the clock and gone within 12 dp, it drew a hard
        // band in the background's colour across a dark card on the light theme and a light card on the dark one.
        val veil = remember(color) {
            Brush.verticalGradient(*TOP_VEIL.map { (at, alpha) -> at to color.copy(alpha = alpha) }.toTypedArray())
        }

        Box(Modifier.fillMaxWidth().height(bar + TOP_VEIL_RAMP).background(veil))
    }
}

/**
 * The top veil's stops, densest at the top: [TOP_VEIL_MAX] at most, so the content still shows faintly under the
 * clock, easing in and out (smoothstep) so neither end draws a line. Nine stops are smooth enough on any screen.
 */
private val TOP_VEIL_MAX = 0.86f
private val TOP_VEIL_RAMP = 28.dp
private val TOP_VEIL = List(9) { i ->
    val t = i / 8f

    t to TOP_VEIL_MAX * (1f - t * t * (3f - 2f * t))
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
    val scope = rememberCoroutineScope()
    val threshold = with(LocalDensity.current) { PullToRefreshDefaults.PositionalThreshold.toPx() }
    val pull = remember(state, scope) { PullGesture(state, scope) }

    SideEffect {
        pull.enabled = !keys
        pull.refreshing = refreshing
        pull.onRefresh = onRefresh
        pull.threshold = threshold
    }
    LaunchedEffect(refreshing) {
        if (!refreshing) pull.finished()
    }

    Box(
        modifier
            // The finger only tells when a drag begins and when it is over; the release itself is the
            // list's fling (onPreFling), which comes after the drag's last step.
            .pointerInput(pull) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    pull.down()
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                    } while (event.changes.any { it.pressed })
                    pull.up()
                }
            }
            .nestedScroll(pull),
    ) {
        content()

        if (!keys) {
            PullToRefreshDefaults.Indicator(
                state = state,
                // Only a refresh the pull asked for: a check from the group's button or one that starts by
                // itself has its own progress, and a minute-long check kept the circle up as if stuck.
                isRefreshing = pull.own && refreshing,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = top),
            )
        }
    }
}

/**
 * The pull of [PullToRefresh], in place of Material's `pullToRefresh`. Distances as there: the circle
 * follows half the finger, [threshold] is a full pull.
 *
 * - Only a drag that began with the list at its top pulls; a drag that scrolls up to the top leaves the
 *   rest to the list's edge spring.
 * - A drag that catches the circle on its way back carries it on from where it is.
 * - The release is the drag's fling, which arrives after its last step: a release taken at the finger's
 *   up came before that step, and the step pulled the circle out again, to stay. Should no fling come,
 *   the finger's up releases a moment later.
 * - The circle shows only a refresh the pull asked for ([own]). Pulled while one is already running,
 *   it adopts that one instead of starting another; a refresh that does not start (nothing to update)
 *   does not leave it at its threshold.
 */
@OptIn(ExperimentalMaterial3Api::class)
internal class PullGesture(
    private val state: PullToRefreshState,
    private val scope: CoroutineScope,
) : NestedScrollConnection {
    var enabled = true
    var refreshing by mutableStateOf(false)
    var onRefresh: () -> Unit = {}

    /** The circle stands for the running refresh: the pull asked for it, or adopted it. */
    var own by mutableStateOf(false)
        private set
    var threshold = 1f

    /** Finger travel the circle has taken in this drag, px. */
    private var pulled = 0f

    /** Whether this drag pulls: decided by its first move — the list could not scroll up, the finger went down. */
    private var armed: Boolean? = null

    private var fingerDown = false

    val pulling get() = pulled > 0f

    fun down() {
        armed = null
        fingerDown = true
    }

    fun up() {
        fingerDown = false
        if (pulled <= 0f) return

        scope.launch {
            delay(UP_GRACE_MS)
            if (!fingerDown) release()
        }
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        // The finger goes back up: the circle returns before the list scrolls.
        if (source != NestedScrollSource.UserInput || pulled <= 0f || available.y >= 0f) return Offset.Zero

        val used = maxOf(available.y, -pulled)
        pulled += used
        follow()

        return Offset(0f, used)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput || !enabled || own) return Offset.Zero
        if (armed == null && (consumed.y != 0f || available.y != 0f)) {
            armed = consumed.y == 0f && available.y > 0f
            if (armed == true) pulled = state.distanceFraction * threshold / DRAG
        }
        if (armed != true || available.y <= 0f) return Offset.Zero

        pulled += available.y
        follow()

        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val pulledHere = pulling
        release()

        // A fling down after a pull belongs to the pull, not to the list's edge.
        return if (pulledHere && available.y > 0f) Velocity(0f, available.y) else Velocity.Zero
    }

    private fun release() {
        if (pulled <= 0f) return

        val full = pulled * DRAG >= threshold
        pulled = 0f

        scope.launch {
            if (!full) return@launch state.animateToHidden()

            own = true
            if (!refreshing) onRefresh()
            state.animateToThreshold()
            val started = withTimeoutOrNull(REFRESH_START_MS) { snapshotFlow { refreshing }.first { it } }
            if (started == null) finished()
        }
    }

    /** The refresh is over (or never began): the circle goes, unless a finger is pulling it again. */
    fun finished() {
        if (!own) return
        own = false
        if (!pulling) scope.launch { state.animateToHidden() }
    }

    private fun follow() {
        val fraction = pulled * DRAG / threshold
        scope.launch { state.snapTo(fraction) }
    }

    private companion object {
        const val DRAG = 0.5f
        const val UP_GRACE_MS = 100L
        const val REFRESH_START_MS = 500L
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
    // The floor is 8 sp at the normal text size. The system's largest font draws 8 sp twice as big, and a label
    // that did not fit even so was cut in the middle of a word on a narrow phone; the floor keeps its 100 % size.
    val floor = 8.sp / LocalDensity.current.fontScale.coerceAtLeast(1f)

    Text(
        text,
        modifier = modifier,
        color = color,
        style = style,
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = floor, maxFontSize = style.fontSize),
    )
}

/**
 * A link, a domain, an address: machine text reads left to right in every language. In a right-to-left one its
 * slashes, dots and stars moved to the wrong end («*.example.com/» showed as «/example.com.*»); it stays on the
 * right there, where the layout puts text.
 */
@Composable
fun TextStyle.machine(): TextStyle =
    if (LocalLayoutDirection.current == LayoutDirection.Rtl) copy(textDirection = TextDirection.Ltr, textAlign = TextAlign.Right)
    else this

/**
 * A provider's or the person's name standing alone in a right-to-left layout: its paragraph takes the name's own
 * direction (with [asName], which leaves out the isolate marks that would hide it) and stays on the right. In a
 * right-to-left paragraph Android 10 and 11 drew a Latin name cut with «…» shifted over its own start — «ery long node
 * name tha…», the «V» under the delay beside it.
 */
@Composable
fun TextStyle.named(): TextStyle =
    if (LocalLayoutDirection.current == LayoutDirection.Rtl) copy(textDirection = TextDirection.Content, textAlign = TextAlign.Right)
    else this

/** The text for a [named] style: bare in a right-to-left layout, in isolate marks ([isolated]) in a left-to-right one. */
@Composable
fun String.asName(): String = if (LocalLayoutDirection.current == LayoutDirection.Rtl) this else isolated()

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
                UpdateShown.Done -> Icon(Symbols.Check, contentDescription = null, modifier = icon)
                UpdateShown.Failed -> Icon(Symbols.ErrorOutline, contentDescription = null, tint = color, modifier = icon)
                UpdateShown.Idle -> Icon(Symbols.Refresh, contentDescription = null, modifier = icon)
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
