package com.github.kr328.clash.ui.config

import com.github.kr328.clash.ui.components.tvKeys
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontFamily
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticRegion
import io.github.rosemoe.sora.lang.diagnostic.DiagnosticsContainer
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import android.graphics.Typeface
import androidx.compose.runtime.withFrameNanos
import android.os.SystemClock
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.runtime.saveable.rememberSaveable
import com.github.kr328.clash.ui.components.HeaderButton
import com.github.kr328.clash.ui.settings.SettingsStore
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.activity.compose.LocalActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.Surface
import androidx.compose.material3.LocalContentColor
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.activity.compose.BackHandler
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.kr328.clash.R
import com.github.kr328.clash.ui.components.DuckerSheet
import com.github.kr328.clash.ui.components.LeaveSheet
import com.github.kr328.clash.ui.components.FitLabel
import com.github.kr328.clash.ui.components.SheetButton
import com.github.kr328.clash.ui.components.SheetField
import com.github.kr328.clash.ui.components.labelWeights
import com.github.kr328.clash.ui.components.screenInsets
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.event.ScrollEvent
import io.github.rosemoe.sora.widget.component.EditorTextActionWindow
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme

/**
 * Конфиг подписки — посмотреть и поправить (D-124). Редактор — sora-editor: рисует только
 * видимые строки, так что конфиг в тысячи строк листается и правится без задержек. Проверяет
 * конфиг ядро — тем же разбором, что при загрузке; ошибку YAML можно нажать и перейти к строке.
 */
@Composable
fun ConfigScreen(
    state: ConfigState,
    onFormat:(text: String, apply: (String) -> Unit) -> Unit,
    onSave: (String) -> Unit,
    onSaveCopy: (String) -> Unit,
    onExport: (text: String, bare: Boolean) -> Unit,
    onResult: (ConfigResult?) -> Unit,
    onDirty: () -> Unit,
    /** Приложение уходит в фон с несохранённой правкой: её текст — в черновик (D-228). */
    onStash: (String) -> Unit,
    /** Ответ на вопрос о черновике: вернули правку в редактор или удалили. */
    onDraft: (restore: Boolean) -> Unit,
    onAskLeave: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }

    var editor by remember { mutableStateOf<CodeEditor?>(null) }
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var matches by remember { mutableIntStateOf(0) }
    var current by remember { mutableIntStateOf(-1) }
    // После замены к следующему — только по свежим позициям совпадений, см. replaceOne.
    var nextAfterSearch by remember { mutableStateOf(false) }
    // Размер шрифта запоминается: кому-то мелко, кому-то нужно больше строк на экране.
    var textSize by remember { mutableFloatStateOf(store.configTextSize) }
    // Во весь экран: остаются код и инструменты правки, остальное прячется.
    var full by rememberSaveable { mutableStateOf(false) }
    var showSections by remember { mutableStateOf(false) }
    var showSaveAs by remember { mutableStateOf(false) }
    // Высота плавающих кнопок: низ кода — над ними или над клавиатурой, что выше. До замера —
    // обычная (48 кнопка + 12 рамка + 32 поля), чтобы в первом кадре код не доходил до низа.
    val density = LocalDensity.current
    var bar by remember { mutableIntStateOf(with(density) { 92.dp.roundToPx() }) }
    // Редактор — кадром позже самого экрана (D-128): на слабом телефоне его создание и сборка
    // экрана в одном кадре рвали въезд. Кадр, а не пауза: на быстром это 8 мс.
    var ready by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        ready = true
    }

    fun resize(by: Float) {
        textSize = (textSize + by).coerceIn(MIN_TEXT, MAX_TEXT)
        store.configTextSize = textSize
        editor?.setTextSize(textSize)
    }

    // Выровненный текст и черновик — заменой в редакторе, а не новым текстом: так «Отменить»
    // вернёт прежний.
    fun replaceAll(editor: CodeEditor, tidy: String) {
        val text = editor.text
        val line = editor.cursor.leftLine
        val x = editor.offsetX
        val y = editor.offsetY
        val last = text.lineCount - 1

        text.replace(0, 0, last, text.getColumnCount(last), tidy)
        // Экран стоит где стоял: замена целиком уводила к курсору, и код дёргался.
        editor.setSelection(line.coerceAtMost(text.lineCount - 1), 0, false)
        editor.scrollTo(x, y, animate = false)
    }

    fun format() {
        val editor = editor ?: return

        onFormat(editor.text.toString()) { replaceAll(editor, it) }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (state.dirty) editor?.let { onStash(it.text.toString()) }
    }

    fun closeSearch() {
        searching = false
        query = ""
        replacement = ""
        editor?.searcher?.takeIf { it.hasQuery() }?.stopSearch()
    }

    // Заменить найденное и перейти к следующему; не выделено ни одно — сначала к нему.
    // Переход — когда sora перепишет позиции совпадений: она ищет заново в своём потоке, и
    // сразу после замены позиции ещё старые, сдвинутые на разницу длин. По ним выделялся
    // кусок мимо совпадения, и следующее нажатие перепрыгивало настоящее.
    fun replaceOne() {
        val searcher = editor?.searcher?.takeIf { it.hasQuery() } ?: return

        if (searcher.isMatchedPositionSelected) {
            nextAfterSearch = true
            searcher.replaceCurrentMatch(replacement)
        } else {
            searcher.gotoNext()
        }
    }

    fun replaceAll() {
        val searcher = editor?.searcher?.takeIf { it.hasQuery() } ?: return
        val count = searcher.matchedPositionCount
        val deleted = replacement.isEmpty()

        if (count > 0) searcher.replaceAll(replacement) { onResult(ConfigResult.Replaced(count, deleted)) }
    }

    val colors = editorColors()

    // Низкое окно — телефон боком или половина экрана: шапка, подсказка и нижние кнопки съедали
    // почти всю высоту, коду оставалось три строки. Тогда всё — одной строкой сверху, как
    // «во весь экран», но с именем и записью; сам «во весь экран» там не нужен.
    val compact = lowWindow()
    val bare = full && !compact

    // Там же прячется строка состояния — ещё строка-другая кода; смахнуть сверху — вернётся.
    // Планшеты и портрет этого не видят: у них окно не низкое.
    val activity = LocalActivity.current
    DisposableEffect(compact, activity) {
        val bars = activity?.window?.let { WindowCompat.getInsetsController(it, it.decorView) }

        if (compact && bars != null) {
            bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars.hide(WindowInsetsCompat.Type.statusBars())
        }

        onDispose { if (compact) bars?.show(WindowInsetsCompat.Type.statusBars()) }
    }
    val onBack = { if (state.dirty) onAskLeave(true) else onClose() }

    // Несохранённое — не терять одним «назад»: сначала спросить (ловит раньше, чем слой).
    BackHandler(enabled = state.dirty) { onAskLeave(true) }
    // Объявлен позже — ловит раньше: из полного экрана «назад» возвращает обычный вид.
    BackHandler(enabled = bare) { full = false }

    // Пульт (D-158): код — одна кнопка, как поле ввода. Иначе фокус заходил в редактор, стрелки
    // двигали в нём курсор, и до «Сохранить» было не добраться. ОК — править: стрелки ведут курсор;
    // «назад» — закончить, фокус снова на карточке.
    val keys = tvKeys()
    val code = remember { FocusRequester() }
    var typing by remember { mutableStateOf(false) }

    BackHandler(enabled = keys && typing) {
        typing = false
        code.requestFocus()
    }

    // Строка с ошибкой — волной прямо в коде, пока её не поправят.
    LaunchedEffect(editor, state.result) {
        val editor = editor ?: return@LaunchedEffect
        val line = (state.result as? ConfigResult.Invalid)?.line?.minus(1)

        editor.diagnostics = DiagnosticsContainer().apply {
            if (line != null && line in 0 until editor.lineCount) {
                val text = editor.text
                val start = text.getCharIndex(line, 0)
                val end = text.getCharIndex(line, text.getColumnCount(line))

                if (end > start) addDiagnostic(DiagnosticRegion(start, end, DiagnosticRegion.SEVERITY_ERROR))
            }
        }
    }

    val ime = WindowInsets.ime
    val navigation = WindowInsets.navigationBars

    Box(modifier.fillMaxSize().screenInsets()) {
        Column(Modifier.fillMaxSize().aboveKeyboard(ime, navigation) { if (bare || compact) 0 else bar }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = if (compact) 4.dp else if (bare) 8.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 12.dp),
            ) {
                if (!bare && !compact) {
                    Header(
                        kind = stringResource(if (state.file != null) R.string.config_kind_file else R.string.config_kind_subscription),
                        name = state.name,
                        onBack = onBack,
                        onFull = { full = true },
                    )

                    if (state.fromSubscription || state.file != null) {
                        Hint(stringResource(if (state.file != null) R.string.config_file_hint else R.string.config_subscription_hint))
                    }
                }

                if (searching) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SearchLines(side = compact) { line ->
                            Row(line, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.weight(1f)) {
                                    SheetField(
                                        value = query,
                                        onValue = {
                                            query = it
                                            val searcher = editor?.searcher ?: return@SheetField
                                            if (it.isEmpty()) searcher.takeIf { s -> s.hasQuery() }?.stopSearch()
                                            else searcher.search(it, EditorSearcher.SearchOptions(true, false))
                                        },
                                        placeholder = stringResource(R.string.config_search),
                                        enabled = true,
                                    )
                                }

                                ToolGroup {
                                    ToolButton(Icons.Default.KeyboardArrowUp, stringResource(R.string.config_search_previous), matches > 0) {
                                        editor?.searcher?.takeIf { it.hasQuery() }?.gotoPrevious()
                                    }
                                    ToolButton(Icons.Default.KeyboardArrowDown, stringResource(R.string.config_search_next), matches > 0) {
                                        editor?.searcher?.takeIf { it.hasQuery() }?.gotoNext()
                                    }
                                    ToolButton(Icons.Default.Close, stringResource(R.string.config_search_close), true) { closeSearch() }
                                }
                            }

                            // Замена; пустое «на что» — удаление найденного.
                            Row(line, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.weight(1f)) {
                                    SheetField(
                                        value = replacement,
                                        onValue = { replacement = it },
                                        placeholder = stringResource(R.string.config_replace_with),
                                        enabled = true,
                                    )
                                }

                                TextPill(
                                    stringResource(if (replacement.isEmpty()) R.string.config_delete_one else R.string.config_replace_one),
                                    matches > 0,
                                    ::replaceOne,
                                )
                                TextPill(stringResource(R.string.config_replace_all), matches > 0, ::replaceAll)
                            }
                        }

                        if (query.isNotEmpty()) {
                            Text(
                                text = if (matches > 0) stringResource(R.string.config_search_found, (current + 1).coerceAtLeast(1), matches)
                                else stringResource(R.string.config_search_none),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                } else {
                    val edits: @Composable () -> Unit = {
                        ToolGroup {
                            ToolButton(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.config_undo), canUndo) { editor?.undo() }
                            ToolButton(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.config_redo), canRedo) { editor?.redo() }
                        }
                        ToolGroup {
                            ToolButton(Icons.Default.TextDecrease, stringResource(R.string.config_text_smaller), textSize > MIN_TEXT) { resize(-1f) }
                            ToolButton(Icons.Default.TextIncrease, stringResource(R.string.config_text_larger), textSize < MAX_TEXT) { resize(1f) }
                        }
                    }
                    val tools: @Composable () -> Unit = {
                        ToolGroup {
                            // Проверка и выравнивание — одна кнопка: выравнивает только то, что ядро
                            // разобрало. В полном экране — только правка, как и без этой кнопки.
                            if (!bare) {
                                ToolButton(Icons.Default.TaskAlt, stringResource(R.string.config_format), !state.busy && state.text != null && editor != null) { format() }
                            }
                            ToolButton(Icons.AutoMirrored.Filled.List, stringResource(R.string.config_sections), editor != null) { showSections = true }
                            ToolButton(Icons.Default.Search, stringResource(R.string.config_search), editor != null) { searching = true }
                        }
                    }

                    if (compact) {
                        // «Сохранить» всегда на виду справа; остальное, если не влезло (узкий
                        // телефон боком, длинные подписи), листается вбок, а не обрезает её.
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                HeaderButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), onBack)
                                Text(
                                    text = state.name,
                                    modifier = Modifier.widthIn(max = 160.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                edits()
                                tools()
                                TextPill(stringResource(R.string.config_save_as), !state.busy && state.text != null) { showSaveAs = true }
                            }
                            SaveButton(
                                label = stringResource(R.string.save),
                                main = true,
                                state = state,
                                onClick = { editor?.text?.toString()?.let(onSave) },
                                modifier = Modifier.heightIn(min = 44.dp),
                            )
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (full) {
                                ToolGroup {
                                    ToolButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.config_fullscreen_exit), true) { full = false }
                                }
                            }
                            edits()
                            Spacer(Modifier.weight(1f))
                            tools()
                        }
                    }
                }
            }

            // Код — в карточке, как всё в приложении: белая плашка с рамкой и скруглением.
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = if (compact) 8.dp else 12.dp, bottom = if (compact) 8.dp else if (bare) 16.dp else 0.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
                    .then(
                        if (keys) {
                            Modifier.focusRequester(code).clickable(interactionSource = null, indication = null) {
                                editor?.let {
                                    typing = true
                                    it.requestFocus()
                                }
                            }
                        } else Modifier,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                val text = state.text

                if (state.loadError != null) {
                    Text(
                        state.loadError,
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                } else if (text == null || !ready) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                } else {
                    // Другой конфиг — другой редактор: текст он берёт только при создании, и `.yaml`,
                    // открытый поверх, показывал прежний текст под новым именем (F-167).
                    key(state.uuid, state.file) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize().padding(vertical = if (compact) 4.dp else 8.dp),
                            factory = { context ->
                                ConfigEditor(context).apply {
                                    typefaceText = Typeface.MONOSPACE
                                    typefaceLineNumber = Typeface.MONOSPACE
                                    setTextSize(textSize)
                                    tabWidth = 2
                                    isWordwrap = false
                                    setPinLineNumber(true)
                                    setDividerWidth(0f)
                                    // Плашка с номером строки у ползунка — чёрный прямоугольник на пол-экрана, пугала.
                                    isDisplayLnPanel = false
                                    replaceComponent(EditorTextActionWindow::class.java, EditorMenu(this))
                                    setEditorLanguage(YamlLanguage())
                                    setText(text)

                                    // Клавиатура выезжает кадрами, и на каждый кадр sora заново плавно
                                    // прокручивала к курсору — код ехал лесенкой. Теперь код сдвигается
                                    // ровно на столько, на сколько курсор закрыло, кадр в кадр, и под
                                    // курсором остаётся [ConfigEditor.room], как при наборе.
                                    props.adjustToSelectionOnResize = false
                                    addOnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
                                        val was = oldBottom - oldTop
                                        val now = bottom - top
                                        val row = getRowBottom(cursor.rightLine)

                                        if (now in 1 until was && row <= offsetY + was) {
                                            val hidden = row + room - (offsetY + now)
                                            if (hidden > 0) eventHandler.scrollBy(0f, hidden.toFloat())
                                        }
                                    }

                                    subscribeAlways(ContentChangeEvent::class.java) { event ->
                                        // При отмене sora сообщает о правке до сдвига истории
                                        // (`UndoManager.java:140–141`) — спрашиваем после (F-149).
                                        post {
                                            canUndo = canUndo()
                                            canRedo = canRedo()
                                        }
                                        if (event.action != ContentChangeEvent.ACTION_SET_NEW_TEXT) onDirty()
                                    }
                                    // Приходит и на закрытие поиска — тогда счётчики у sora уже не спросить.
                                    subscribeAlways(PublishSearchResultEvent::class.java) {
                                        if (nextAfterSearch && searcher.hasQuery()) searcher.gotoNext()
                                        nextAfterSearch = false
                                        matches = if (searcher.hasQuery()) searcher.matchedPositionCount else 0
                                        current = if (searcher.hasQuery()) searcher.currentMatchedPositionIndex else -1
                                    }
                                    // sora зажигает полосы прокрутки после броска и отпущенного ползунка, но
                                    // не когда ведёшь пальцем: погасшие полосы так и не появлялись.
                                    var scrolled = 0L
                                    subscribeAlways(ScrollEvent::class.java) {
                                        val now = SystemClock.uptimeMillis()
                                        if (now - scrolled > 250) {
                                            scrolled = now
                                            eventHandler.notifyScrolled()
                                        }
                                    }

                                    // Фокус ушёл из кода сам (стрелкой за край) — правка кончилась.
                                    setOnFocusChangeListener { _, has -> if (!has) typing = false }

                                    editor = this
                                }
                            },
                            update = {
                                it.colorScheme = colors
                                it.styleScrollbars()
                            },
                            onRelease = { it.release() },
                        )
                    }
                }

                // Итог — плашкой поверх кода, а не строкой над ним: строка появлялась и пропадала,
                // и редактор дёргался при каждой проверке.
                ResultPill(
                    result = state.result,
                    onLine = { editor?.jumpTo(it - 1) },
                    onDismiss = { onResult(null) },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                )
            }

        }

        // Кнопки — у нижнего края, клавиатура их просто накрывает: прятать их и показывать
        // снова значило дёргать рамку кода, когда клавиатура уезжала.
        if (!bare && !compact) {
            Box(Modifier.align(Alignment.BottomCenter).onSizeChanged { bar = it.height }) {
                ActionBar(state, onSaveAs = { showSaveAs = true }, onSave) { editor?.text?.toString() }
            }
        }
    }

    if (showSections) {
        SectionsSheet(
            text = remember { editor?.text?.toString().orEmpty() },
            onPick = { editor?.jumpTo(it) },
            onClosed = { showSections = false },
        )
    }

    if (showSaveAs) {
        SaveAsSheet(
            file = state.file != null,
            onPick = { option ->
                val text = editor?.text?.toString() ?: return@SaveAsSheet

                when (option) {
                    SaveAs.Profile -> onSaveCopy(text)
                    SaveAs.File -> onExport(text, false)
                    SaveAs.Bare -> onExport(text, true)
                }
            },
            onClosed = { showSaveAs = false },
        )
    }

    if (state.draft != null && state.text != null && editor != null) {
        LeaveSheet(
            title = stringResource(R.string.config_draft),
            hint = stringResource(R.string.config_draft_hint),
            leave = stringResource(R.string.config_draft_drop),
            stay = stringResource(R.string.config_draft_restore),
            onLeave = { onDraft(false) },
            // Закрыли окно жестом — тоже вернуть: правка в редакторе отменяется, удалённая — нет.
            onStay = {
                editor?.let { replaceAll(it, state.draft) }
                onDraft(true)
            },
        )
    }

    if (state.askLeave) {
        LeaveSheet(
            title = stringResource(R.string.config_unsaved),
            hint = stringResource(R.string.config_unsaved_hint),
            leave = stringResource(R.string.config_leave),
            onLeave = {
                onAskLeave(false)
                onClose()
            },
            onStay = { onAskLeave(false) },
        )
    }
}

/**
 * Низ — над клавиатурой или над кнопками [bar], что выше, кадр в кадр: отступы читаются при
 * раскладке, и экран не пересобирается на каждый кадр выезда клавиатуры.
 */
private fun Modifier.aboveKeyboard(ime: WindowInsets, navigation: WindowInsets, bar: () -> Int) =
    layout { measurable, constraints ->
        val keyboard = (ime.getBottom(this) - navigation.getBottom(this)).coerceAtLeast(0)
        val bottom = maxOf(keyboard, bar()).coerceAtMost(constraints.maxHeight)
        val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = constraints.maxHeight - bottom))

        layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
    }

/** К строке [line] (с нуля): курсор — в её начало, сама — в верхнюю треть, чтобы было видно, что над ней. */
private fun CodeEditor.jumpTo(line: Int) {
    val target = line.coerceIn(0, (lineCount - 1).coerceAtLeast(0))

    setSelection(target, 0, false)
    scrollTo(0, getRowTop(target) - height / 3, animate = true)
}

private fun CodeEditor.scrollTo(x: Int, y: Int, animate: Boolean) {
    val toX = x.coerceIn(0, scrollMaxX.coerceAtLeast(0))
    val toY = y.coerceIn(0, scrollMaxY.coerceAtLeast(0))

    scroller.forceFinished(true)
    scroller.startScroll(offsetX, offsetY, toX - offsetX, toY - offsetY, if (animate) props.scrollAnimationDurationMs else 0)
    invalidate()
}

/** Шапка: мелко — что это, крупно — чьё; справа — «во весь экран». */
@Composable
private fun Header(kind: String, name: String, onBack: () -> Unit, onFull: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HeaderButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), onBack)

        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                text = kind,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        HeaderButton(Icons.Default.Fullscreen, stringResource(R.string.config_fullscreen), onFull)
    }
}

/**
 * Кнопки — плавающей панелью, как панель вкладок: белая, с рамкой и тенью. Второстепенные
 * на ней серые, главная — чёрная; на белом фоне экрана серые пилюли сливались.
 */
@Composable
private fun ActionBar(
    state: ConfigState,
    onSaveAs: () -> Unit,
    onSave: (String) -> Unit,
    text: () -> String?,
) {
    val labels = listOf(
        stringResource(R.string.config_save_as),
        stringResource(R.string.save),
    )
    // Ширины самих подписей, без полей.
    val words = labelWeights(labels, MaterialTheme.typography.labelLarge, 0.dp)
    val actions = listOf({ _: String -> onSaveAs() }, onSave)

    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 8.dp,
    ) {
        BoxWithConstraints(Modifier.padding(6.dp)) {
            // Свободное место — кнопкам поровну, а не по длине подписи: иначе у «Сохранить как»
            // поля от края до слова были шире, чем у «Сохранить». Не хватает места — поля по
            // 14dp, и подписи ужимаются одинаково, как у `labelWeights`.
            val fields = with(LocalDensity.current) {
                val least = 28.dp.toPx()
                ((constraints.maxWidth - 6.dp.toPx() * (labels.size - 1) - words.sum()) / labels.size).coerceAtLeast(least)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                labels.forEachIndexed { i, label ->
                    SaveButton(
                        label = label,
                        main = i == labels.lastIndex,
                        state = state,
                        onClick = { text()?.let(actions[i]) },
                        modifier = Modifier.weight(words[i] + fields).heightIn(min = 48.dp),
                    )
                }
            }
        }
    }
}

/** Кнопка записи: главная — чёрная, пока идёт работа — с кружком вместо надписи. */
@Composable
private fun SaveButton(label: String, main: Boolean, state: ConfigState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val enabled = !state.busy && state.text != null

    Box(
        modifier = modifier
            // Подложка — до обрезки, в слое панели: внутри слоя обрезки она иногда не
            // рисовалась, пока кнопку не нажмёшь, — белая «Сохранить» на белом пропадала.
            .background(if (main) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.5f)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        val content = if (main) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        val spinning = state.busy && main

        // Надпись на время работы не убирается, а прячется под кружок: «Сохранить»
        // иногда пропадала и появлялась, только когда на неё нажмёшь.
        CompositionLocalProvider(LocalContentColor provides content) {
            Box(Modifier.alpha(if (spinning) 0f else 1f)) {
                FitLabel(label, MaterialTheme.typography.labelLarge)
            }
        }

        if (spinning) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = content)
        }
    }
}

/** Строки поиска: друг под другом, а в низком окне — рядом, в одну строку. */
@Composable
private fun SearchLines(side: Boolean, content: @Composable (Modifier) -> Unit) {
    if (side) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            content(Modifier.weight(1f))
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content(Modifier) }
    }
}

/** Подсказка в фирменном овале: значок и текст на белой плашке с рамкой. */
@Composable
private fun Hint(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Группа круглых кнопок в одном овале — как пилюли на остальных экранах. */
@Composable
private fun ToolGroup(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun ToolButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        // 40, а не 44: в полном экране в строку встают шесть кнопок и на узком телефоне.
        modifier = Modifier.size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.3f),
        )
    }
}

/** Пилюля-кнопка со словом — для замены в поиске. */
@Composable
private fun TextPill(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.heightIn(min = 44.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

private val ConfigResult.ok: Boolean
    get() = this !is ConfigResult.Invalid && this !is ConfigResult.ReadOnly && this != ConfigResult.SaveFailed

/**
 * Итог проверки или записи — плашкой поверх кода снизу. Удачный сам гаснет через несколько
 * секунд; ошибка висит, пока её не закроют или не поправят, а со строкой — нажимается.
 */
@Composable
private fun ResultPill(result: ConfigResult?, onLine: (Int) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    // Уходящая плашка дорисовывается с прежним текстом.
    val last = remember { arrayOfNulls<ConfigResult>(1) }
    if (result != null) last[0] = result

    LaunchedEffect(result) {
        if (result?.ok == true) {
            delay(RESULT_MS)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = result != null,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        last[0]?.let { ResultContent(it, onLine, onDismiss) }
    }
}

@Composable
private fun ResultContent(result: ConfigResult, onLine: (Int) -> Unit, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val ok = result.ok
    val text = when (result) {
        ConfigResult.Valid -> stringResource(R.string.config_valid)
        ConfigResult.Saved -> stringResource(R.string.config_saved)
        ConfigResult.SaveFailed -> stringResource(R.string.config_save_failed)
        is ConfigResult.Copied -> stringResource(R.string.config_copied, result.name)
        is ConfigResult.Added -> stringResource(R.string.config_added, result.name)
        ConfigResult.ReadOnly -> stringResource(R.string.config_file_readonly)
        ConfigResult.Formatted -> stringResource(R.string.config_formatted)
        ConfigResult.FormatFailed -> stringResource(R.string.config_format_failed)
        is ConfigResult.Exported -> stringResource(R.string.config_exported, result.name)
        is ConfigResult.Replaced -> pluralStringResource(
            if (result.deleted) R.plurals.config_deleted else R.plurals.config_replaced, result.count, result.count,
        )
        is ConfigResult.Invalid ->
            (if (result.line != null) stringResource(R.string.config_error_line, result.line) + " " else "") + result.message
    }
    val line = (result as? ConfigResult.Invalid)?.line
    val color = if (ok) scheme.onSurface else scheme.error

    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (ok) scheme.surface else scheme.errorContainer,
        border = BorderStroke(1.dp, if (ok) scheme.outlineVariant else scheme.error.copy(alpha = 0.4f)),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .then(if (line != null) Modifier.clickable(role = Role.Button) { onLine(line) } else Modifier)
                .padding(start = 16.dp, end = if (ok) 16.dp else 4.dp, top = 6.dp, bottom = 6.dp)
                .heightIn(min = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = if (ok) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = color,
            )
            Text(
                text = text,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = color,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (!ok) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape)
                        .clickable(role = Role.Button, onClickLabel = stringResource(R.string.config_result_close), onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.config_result_close), modifier = Modifier.size(18.dp), tint = color)
                }
            }
        }
    }
}

/** Разделы конфига — ключи верхнего уровня; нажал — переход к строке. */
@Composable
private fun SectionsSheet(text: String, onPick: (Int) -> Unit, onClosed: () -> Unit) {
    val sections = remember(text) { sections(text) }
    var open by remember { mutableStateOf(true) }
    var picked by remember { mutableStateOf<Int?>(null) }

    DuckerSheet(
        open = open,
        title = stringResource(R.string.config_sections),
        busy = false,
        onDismiss = { open = false },
        // Переход — когда панель уехала: под ней прокрутку не видно.
        onClosed = {
            picked?.let(onPick)
            onClosed()
        },
    ) {
        if (sections.isEmpty()) {
            Text(
                text = stringResource(R.string.config_sections_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            sections.forEach { (key, line) ->
                val known = SECTION_NAMES[key]

                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable(role = Role.Button) {
                            picked = line
                            open = false
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        if (key.isEmpty()) {
                            Text(stringResource(R.string.config_section_settings), style = MaterialTheme.typography.bodyLarge)
                        } else {
                            Text(key, style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
                        }
                        if (known != null) {
                            Text(
                                text = stringResource(known),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.config_section_line, line + 1),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Что значат частые разделы — для тех, кто видит конфиг впервые. */
private val SECTION_NAMES = mapOf(
    "proxies" to R.string.config_section_proxies,
    "proxy-groups" to R.string.config_section_groups,
    "proxy-providers" to R.string.config_section_providers,
    "rules" to R.string.config_section_rules,
    "rule-providers" to R.string.config_section_rule_sets,
    "dns" to R.string.config_section_dns,
)

private enum class SaveAs { Profile, File, Bare }

/**
 * «Сохранить как» (D-124): отдельной подпиской в ducker, файлом на телефон или файлом без
 * серверов — поделиться, не показав свои адреса и пароли.
 */
@Composable
private fun SaveAsSheet(file: Boolean, onPick: (SaveAs) -> Unit, onClosed: () -> Unit) {
    var open by remember { mutableStateOf(true) }
    var picked by remember { mutableStateOf<SaveAs?>(null) }

    DuckerSheet(
        open = open,
        title = stringResource(R.string.config_save_as),
        busy = false,
        onDismiss = { open = false },
        // Системное окно выбора файла — когда панель уехала, а не поверх неё.
        onClosed = {
            picked?.let(onPick)
            onClosed()
        },
    ) {
        fun pick(option: SaveAs) {
            picked = option
            open = false
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SaveOption(
                Icons.Default.AddCircleOutline,
                stringResource(if (file) R.string.config_add_profile else R.string.config_save_profile),
                stringResource(R.string.config_save_profile_hint),
            ) { pick(SaveAs.Profile) }
            SaveOption(
                Icons.Outlined.FileDownload,
                stringResource(R.string.config_save_file),
                stringResource(R.string.config_save_file_hint),
            ) { pick(SaveAs.File) }
            SaveOption(
                Icons.Outlined.VisibilityOff,
                stringResource(R.string.config_save_bare),
                stringResource(R.string.config_save_bare_hint),
            ) { pick(SaveAs.Bare) }
        }
    }
}

@Composable
private fun SaveOption(icon: ImageVector, title: String, hint: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val MIN_TEXT = 9f
private const val MAX_TEXT = 22f

/**
 * Окно ниже 480dp (граница компактной высоты у Material) — телефон боком или половина экрана.
 * Планшет таким не бывает и боком. Редактору конфига тогда нужна каждая строка (D-145).
 */
@Composable
internal fun lowWindow(): Boolean =
    with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() } < 480.dp

private const val RESULT_MS = 4000L

/** Цвета редактора — из темы приложения; подсветка — спокойная, читаемая в обеих темах. */
@Composable
private fun editorColors(): EditorColorScheme {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f

    return remember(scheme, dark) {
        EditorColorScheme().apply {
            fun set(id: Int, color: Color) = setColor(id, color.toArgb())

            set(EditorColorScheme.WHOLE_BACKGROUND, scheme.surface)
            set(EditorColorScheme.LINE_NUMBER_BACKGROUND, scheme.surface)
            set(EditorColorScheme.LINE_NUMBER, scheme.onSurfaceVariant.copy(alpha = 0.6f))
            set(EditorColorScheme.LINE_NUMBER_CURRENT, scheme.onSurface)
            set(EditorColorScheme.LINE_DIVIDER, scheme.surface)
            set(EditorColorScheme.CURRENT_LINE, scheme.onSurface.copy(alpha = 0.05f))
            set(EditorColorScheme.TEXT_NORMAL, scheme.onSurface)
            set(EditorColorScheme.SELECTED_TEXT_BACKGROUND, scheme.primary.copy(alpha = 0.25f))
            set(EditorColorScheme.SELECTION_INSERT, scheme.onSurface)
            set(EditorColorScheme.SELECTION_HANDLE, scheme.onSurface)
            set(EditorColorScheme.MATCHED_TEXT_BACKGROUND, Color(if (dark) 0x66FDD663 else 0x66F9AB00))
            set(EditorColorScheme.KEYWORD, Color(if (dark) 0xFF8AB4F8 else 0xFF1A5FB4))
            set(EditorColorScheme.LITERAL, Color(if (dark) 0xFF81C995 else 0xFF26844B))
            set(EditorColorScheme.ANNOTATION, Color(if (dark) 0xFFD7AEFB else 0xFF8E24AA))
            set(EditorColorScheme.COMMENT, scheme.onSurfaceVariant.copy(alpha = 0.8f))
            set(EditorColorScheme.OPERATOR, scheme.onSurfaceVariant)
            set(EditorColorScheme.FUNCTION_NAME, Color(if (dark) 0xFFFDBA74 else 0xFFC2410C))
            set(EditorColorScheme.SCROLL_BAR_THUMB, scheme.onSurfaceVariant.copy(alpha = 0.4f))
            set(EditorColorScheme.SCROLL_BAR_THUMB_PRESSED, scheme.onSurface.copy(alpha = 0.7f))
            set(EditorColorScheme.SCROLL_BAR_TRACK, scheme.onSurface.copy(alpha = 0.06f))
            set(EditorColorScheme.TEXT_ACTION_WINDOW_BACKGROUND, scheme.surface)
            set(EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR, scheme.onSurface)
            set(EditorColorScheme.PROBLEM_ERROR, scheme.error)
        }
    }
}
