package com.github.kr328.clash

import androidx.compose.runtime.CompositionLocalProvider
import com.github.kr328.clash.ui.components.LocalFrosted
import com.github.kr328.clash.ui.components.Symbols
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.annotation.StringRes
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size as Area
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.github.kr328.clash.ui.subscriptions.SourceTile
import com.github.kr328.clash.ui.subscriptions.TvLink
import com.github.kr328.clash.ui.theme.DuckerTheme
import com.github.kr328.clash.ui.components.GlassStyle
import com.github.kr328.clash.ui.components.LocalGlassStyle
import com.github.kr328.clash.ui.components.glassRim
import com.github.kr328.clash.ui.components.glassSupported
import com.github.kr328.clash.ui.settings.SettingsStore
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawOutline
import kotlinx.coroutines.delay
import zxingcpp.BarcodeReader
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

/**
 * Сканер QR подписки (D-88).
 *
 * Свой вместо quickie: тот тащил ML Kit, а ML Kit по условиям Google отправляет ему
 * статистику использования — против инварианта «ничего не шлём наружу». Распознаёт
 * zxing-cpp (Apache 2.0, работает без сервисов Google), камера — CameraX.
 *
 * Вид — как остальное приложение (этап 13, пакет 5): окно в затемнённой камере, подсказка и
 * кнопки — белой панелью, как у всех панелей; в альбомной панель справа и не наезжает на окно.
 * Код пойман — уголки смыкаются в рамку, галочка и вибрация, потом экран закрывается: раньше он
 * исчезал сразу, и было непонятно, считался ли код. «Из галереи» — QR со скриншота: код часто
 * приходит картинкой на этот же телефон, а свой экран камерой не снять.
 */
class QrScanActivity : AppCompatActivity() {
    private val analysis = Executors.newSingleThreadExecutor()
    private val found = AtomicBoolean(false)

    private val reader by lazy {
        BarcodeReader(
            BarcodeReader.Options(
                formats = setOf(BarcodeReader.Format.QR_CODE),
                tryHarder = true,
                // Светлый код на тёмном фоне: так его рисуют тёмные темы панелей.
                tryInvert = true,
            )
        )
    }

    /** Считанный код: экран доигрывает «поймал» и закрывается с ним. */
    private val caught = mutableStateOf<String?>(null)

    /** На выбранной картинке кода не нашлось — говорим об этом в панели. */
    private val galleryMiss = mutableStateOf(false)

    /** Opened from «Отправить на телевизор»: only a TV's code is taken (D-162). */
    private val tvOnly by lazy { intent.getBooleanExtra(EXTRA_TV, false) }

    /** In [tvOnly] mode another code was read: the panel says so and the scan goes on. */
    private val notTv = mutableStateOf(false)

    private val permission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showCamera()
        } else {
            Toast.makeText(this, R.string.scan_no_camera, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    // Системный выбор фото: без разрешения на чтение файлов.
    private val gallery = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) readImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Камера — во весь экран, под строкой состояния; значки на ней — светлые.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

        // После поворота экрана окно разрешения уже открыто: ответ придёт в `permission`.
        when {
            granted -> showCamera()
            savedInstanceState == null -> permission.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        analysis.shutdown()
    }

    private fun catch(text: String) {
        if (tvOnly && TvLink.parse(text.trim()) == null) return runOnUiThread { notTv.value = true }
        if (found.compareAndSet(false, true)) runOnUiThread { caught.value = text }
    }

    private fun showCamera() {
        val controller = LifecycleCameraController(this).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            // По умолчанию кадр для разбора 640×480: плотный код подписки с экрана
            // монитора на таком не читается.
            imageAnalysisResolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        Size(1280, 720),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                    )
                )
                .build()
            setImageAnalysisAnalyzer(analysis) { image ->
                if (found.get()) {
                    image.close()

                    return@setImageAnalysisAnalyzer
                }

                image.use { reader.read(it) }.firstText()?.let(::catch)
            }
            bindToLifecycle(this@QrScanActivity)
        }

        // The glass style reaches this screen too, under Home's conditions: its panel is a glass sheet over the camera.
        val glass = GlassStyle(
            enabled = glassSupported && !packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) &&
                SettingsStore(this).style == SettingsStore.STYLE_GLASS,
        )

        setContent {
            DuckerTheme(glass = glass) {
                var torch by remember { mutableStateOf(false) }

                ScanScreen(
                    controller = controller,
                    caught = caught.value,
                    hint = when {
                        galleryMiss.value -> R.string.scan_gallery_miss
                        notTv.value -> R.string.scan_not_tv
                        tvOnly -> R.string.scan_hint_tv
                        else -> R.string.scan_hint
                    },
                    error = galleryMiss.value || notTv.value,
                    torch = torch,
                    onTorch = {
                        torch = !torch
                        controller.enableTorch(torch)
                    },
                    onGallery = {
                        galleryMiss.value = false
                        notTv.value = false
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onClose = ::finish,
                    onDone = { text ->
                        setResult(RESULT_OK, Intent().putExtra(EXTRA_TEXT, text))
                        finish()
                    },
                    onBars = { dark ->
                        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = dark
                    },
                )
            }
        }
    }

    /** QR с картинки — в той же очереди, что и кадры камеры: распознаватель у них один. */
    private fun readImage(uri: Uri) {
        analysis.execute {
            val text = runCatching { reader.read(decode(uri)).firstText() }.getOrNull()

            if (text != null) catch(text) else runOnUiThread { galleryMiss.value = true }
        }
    }

    /**
     * Картинка в памяти, как её ждёт распознаватель: программная, не больше [MAX_IMAGE] по длинной
     * стороне — фото с камеры в 50 Мп разбиралось бы секундами, а скриншоту хватает и так.
     */
    private fun decode(uri: Uri): Bitmap {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return decodeLegacy(uri)

        return ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val side = max(info.size.width, info.size.height)
            if (side > MAX_IMAGE) decoder.setTargetSampleSize((side + MAX_IMAGE - 1) / MAX_IMAGE)
        }
    }

    /** Android 8 has no [ImageDecoder]: the size first, then the pixels at that sample size; software by default. */
    private fun decodeLegacy(uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }

        val side = max(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply {
            if (side > MAX_IMAGE) inSampleSize = (side + MAX_IMAGE - 1) / MAX_IMAGE
        }

        return checkNotNull(contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) })
    }

    private fun List<BarcodeReader.Result>.firstText(): String? =
        firstNotNullOfOrNull { result -> result.text?.takeIf { it.isNotBlank() } }

    /**
     * Открывает сканер; результат — текст кода или `null`, если человек закрыл экран. Input `true`: only a TV's
     * code, with a hint about it; any other code is refused on the spot instead of being returned.
     */
    object Scan : ActivityResultContract<Boolean, String?>() {
        override fun createIntent(context: Context, input: Boolean) =
            Intent(context, QrScanActivity::class.java).putExtra(EXTRA_TV, input)

        override fun parseResult(resultCode: Int, intent: Intent?): String? =
            intent?.takeIf { resultCode == RESULT_OK }?.getStringExtra(EXTRA_TEXT)
    }

    private companion object {
        const val EXTRA_TEXT = "text"
        const val EXTRA_TV = "tv"
        const val MAX_IMAGE = 2048
    }
}

@Composable
private fun ScanScreen(
    controller: LifecycleCameraController,
    caught: String?,
    @StringRes hint: Int,
    error: Boolean,
    torch: Boolean,
    onTorch: () -> Unit,
    onGallery: () -> Unit,
    onClose: () -> Unit,
    onDone: (String) -> Unit,
    /** Тёмные ли значки полосы навигации. */
    onBars: (Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    // «Поймал» доигрывает, потом экран закрывается с кодом.
    LaunchedEffect(caught) {
        if (caught == null) return@LaunchedEffect

        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        delay(CAUGHT_MS)
        onDone(caught)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { PreviewView(it).apply { this.controller = controller } },
            modifier = Modifier.fillMaxSize(),
        )

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth > maxHeight
            val light = MaterialTheme.colorScheme.background.luminance() > 0.5f

            // Внизу в книжной — белая панель, значки навигации на ней тёмные; в альбомной внизу камера.
            SideEffect { onBars(light && !wide) }

            val panel = @Composable { modifier: Modifier ->
                ScanPanel(caught == null, hint, error, torch, onTorch, onGallery, wide, modifier)
            }

            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    Finder(caught != null, WindowInsetsSides.Top + WindowInsetsSides.Start, Modifier.weight(1f).fillMaxHeight())
                    panel(Modifier.width(PANEL_WIDTH).fillMaxHeight())
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Finder(caught != null, WindowInsetsSides.Top + WindowInsetsSides.Horizontal, Modifier.weight(1f).fillMaxWidth())
                    panel(Modifier.fillMaxWidth())
                }
            }
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier.safeDrawingPadding().padding(12.dp)
                .clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)),
        ) {
            Icon(Symbols.Close, stringResource(R.string.cancel), tint = Color.White)
        }
    }
}

/**
 * Окно видоискателя: вокруг камера притемнена, по углам — скобки. Пока ищет — окно чуть «дышит»:
 * видно, что экран живой. Поймал — скобки смыкаются в рамку, в середине галочка.
 */
@Composable
private fun Finder(caught: Boolean, sides: WindowInsetsSides, modifier: Modifier) {
    val breath by rememberInfiniteTransition(label = "breath").animateFloat(
        initialValue = 1f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(tween(BREATH_MS, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    val close by animateFloatAsState(if (caught) 1f else 0f, tween(CLOSE_MS, easing = FastOutSlowInEasing), label = "close")
    val check by animateFloatAsState(if (caught) 1f else 0f, tween(CHECK_MS, delayMillis = CLOSE_MS - 60), label = "check")
    val ink = MaterialTheme.colorScheme.onSurface
    val insets = WindowInsets.safeDrawing.only(sides)
    val direction = LocalLayoutDirection.current

    Canvas(
        // Отдельный слой: иначе вырез в затемнении прорезал бы и камеру под ним.
        modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen),
    ) {
        // Окно — посреди видимой части камеры, без строки состояния и выреза; затемнено всё.
        val area = Rect(
            insets.getLeft(this, direction).toFloat(),
            insets.getTop(this).toFloat(),
            size.width - insets.getRight(this, direction),
            size.height - insets.getBottom(this),
        )
        val scale = if (caught) lerp(1f, 0.94f, close) else breath
        val side = min(min(area.width, area.height) * 0.72f, FINDER_MAX.toPx()) * scale
        val middle = area.center
        val topLeft = middle - Offset(side / 2, side / 2)
        val radius = RADIUS.toPx()

        drawRect(Color.Black.copy(alpha = SCRIM))
        drawRoundRect(Color.Transparent, topLeft, Area(side, side), CornerRadius(radius), blendMode = BlendMode.Clear)

        // Скобка левого верхнего угла; остальные — она же, повёрнутая вокруг середины окна.
        val arm = lerp(side * 0.22f, side / 2, close)
        val bracket = Path().apply {
            moveTo(topLeft.x, topLeft.y + arm)
            lineTo(topLeft.x, topLeft.y + radius)
            arcTo(Rect(topLeft, Area(radius * 2, radius * 2)), 180f, 90f, false)
            lineTo(topLeft.x + arm, topLeft.y)
        }
        val stroke = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)

        for (turn in 0 until 4) rotate(90f * turn, pivot = middle) { drawPath(bracket, Color.White, style = stroke) }

        if (check > 0f) {
            val unit = 1.dp.toPx() * check

            drawCircle(Color.White, radius = 30 * unit, center = middle)
            drawPath(
                Path().apply {
                    moveTo(middle.x - 11 * unit, middle.y + 1 * unit)
                    lineTo(middle.x - 3 * unit, middle.y + 8 * unit)
                    lineTo(middle.x + 12 * unit, middle.y - 8 * unit)
                },
                ink,
                style = Stroke(width = 4 * unit, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/** Подсказка и кнопки — белой панелью, как все панели приложения (D-99, D-127). */
@Composable
private fun ScanPanel(
    enabled: Boolean,
    @StringRes hint: Int,
    error: Boolean,
    torch: Boolean,
    onTorch: () -> Unit,
    onGallery: () -> Unit,
    wide: Boolean,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = if (wide) RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp)
    else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val insets = if (wide) WindowInsetsSides.Vertical + WindowInsetsSides.End else WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal

    Column(
        // Под скруглёнными углами — то же затемнение, что над камерой, а не яркая камера.
        modifier = modifier.background(Color.Black.copy(alpha = SCRIM)).clip(shape)
            .then(
                // As a sheet of the glass style: the camera shows faintly through, light catches the edge.
                if (LocalGlassStyle.current.enabled) Modifier.background(scheme.background.copy(alpha = 0.85f)).drawWithContent {
                    drawContent()
                    drawOutline(shape.createOutline(size, layoutDirection, this), glassRim(scheme.background), style = Stroke(1.dp.toPx()))
                } else Modifier.background(scheme.background),
            )
            .windowInsetsPadding(WindowInsets.safeDrawing.only(insets))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, if (wide) Alignment.CenterVertically else Alignment.Top),
    ) {
        // Its tiles as a frosted sheet's controls: the camera is not drawn where glass could show it through.
        CompositionLocalProvider(LocalFrosted provides LocalGlassStyle.current.enabled) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(R.string.scan_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                // The panel is a plain column, not a Surface: without a colour the title stayed black on dark.
                color = scheme.onBackground,
            )
            Text(
                text = stringResource(hint),
                style = MaterialTheme.typography.bodyMedium,
                color = if (error) scheme.error else scheme.onSurfaceVariant,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SourceTile(
                icon = if (torch) Symbols.FlashOn else Symbols.FlashOff,
                label = stringResource(R.string.scan_torch),
                enabled = enabled,
                onClick = onTorch,
                modifier = Modifier.weight(1f),
            )
            SourceTile(
                icon = Symbols.PhotoLibrary,
                label = stringResource(R.string.scan_gallery),
                enabled = enabled,
                onClick = onGallery,
                modifier = Modifier.weight(1f),
            )
        }
        }
    }
}

private val PANEL_WIDTH = 340.dp
private val RADIUS = 24.dp
private val FINDER_MAX = 280.dp
private const val SCRIM = 0.55f

private const val BREATH_MS = 1300
private const val CLOSE_MS = 240
private const val CHECK_MS = 200

/** Сколько «поймал» держится на экране, прежде чем сканер закроется. */
private const val CAUGHT_MS = 650L
