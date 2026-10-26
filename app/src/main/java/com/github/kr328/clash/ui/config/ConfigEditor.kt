package com.github.kr328.clash.ui.config

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import com.github.kr328.clash.R
import io.github.rosemoe.sora.event.ScrollEvent
import io.github.rosemoe.sora.lang.styling.Styles
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorRenderer
import io.github.rosemoe.sora.widget.component.EditorTextActionWindow
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.style.SelectionHandleStyle.ALIGN_RIGHT
import io.github.rosemoe.sora.widget.style.SelectionHandleStyle.HANDLE_TYPE_INSERT
import io.github.rosemoe.sora.widget.style.SelectionHandleStyle.HANDLE_TYPE_LEFT
import io.github.rosemoe.sora.widget.style.SelectionHandleStyle.HANDLE_TYPE_RIGHT
import io.github.rosemoe.sora.widget.style.SelectionHandleStyle.HandleDescriptor
import io.github.rosemoe.sora.widget.style.builtin.HandleStyleSideDrop

/**
 * Редактор конфига (D-124) — sora-editor с полосами прокрутки, которые ловятся пальцем. У sora
 * горизонтальная берётся ровно по своим 10dp, вертикальная — с запасом в 10dp; тонкие овалы
 * промахивались. Запас — ещё [GRAB] внутрь от края; рисуются полосы как прежде.
 */
internal class ConfigEditor(context: Context) : CodeEditor(context) {
    /** A TV: the keyboard does not move the cursor to the end, see [onCreateInputConnection]. */
    var holdCursor = false

    // Gboard on a TV puts the cursor at the end of the text as it opens, as for a one-line field, and the typing
    // went to the config's last line instead of where the remote had put the cursor (F-358). On a TV a move of a
    // plain cursor to the very end that comes from the keyboard is left undone.
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val connection = super.onCreateInputConnection(outAttrs) ?: return null
        if (!holdCursor) return connection

        return object : InputConnectionWrapper(connection, true) {
            override fun setSelection(start: Int, end: Int): Boolean =
                if (start == end && start == text.length && start != cursor.left) true else super.setSelection(start, end)
        }
    }

    /** Сколько держать под строкой с курсором: не в упор к клавиатуре и не под полосой прокрутки. */
    val room: Int get() = rowHeight * ROOM

    init {
        // Номера строк закреплены и рисуются поверх капель: у первой колонки капля курсора
        // уходила под них наполовину. Зазор — ровно на её половину.
        setDividerMargin(2 * dpUnit, DROP / 2 * dpUnit)
        setSelectionHandleStyle(Handles())
    }

    // Разметку подсветки sora отдаёт через очередь главного потока (`EditorStyleDelegate.java:106`),
    // и она может прийти от текста, который уже сменился целиком: при замене всего текста поток
    // разбора успевал прочесть промежуточную одну строку. С такой разметкой рисование только
    // теряет цвет, а замер позиции курсора — меню, ручки, прокрутка — падает (F-152). Её
    // пропускаем: правка, сменившая текст, уже заказала новый разбор.
    override fun setStyles(styles: Styles?) {
        if (styles?.spans?.let { it.lineCount != lineCount } == true) return
        super.setStyles(styles)
    }

    // Сверху sora оставляет две строки, снизу — ни одной, пока курсор не уйдёт за край.
    override fun ensurePositionVisible(line: Int, column: Int, noAnimation: Boolean) {
        super.ensurePositionVisible(line, column, noAnimation)

        val y = if (scroller.isFinished) offsetY else scroller.finalY
        val toY = (layout.getCharLayoutOffset(line, column)[0] + room - height).toInt().coerceAtMost(scrollMaxY)

        if (toY > y) {
            val x = if (scroller.isFinished) offsetX else scroller.finalX

            scroller.forceFinished(true)
            scroller.startScroll(offsetX, offsetY, x - offsetX, toY - offsetY, if (noAnimation) 0 else props.scrollAnimationDurationMs)
            dispatchEvent(ScrollEvent(this, offsetX, offsetY, x, toY, ScrollEvent.CAUSE_MAKE_POSITION_VISIBLE))
            invalidate()
        }
    }

    /**
     * Капли не заходят под номера строк: левая ручка выделения и капля курсора, которым у края
     * кода не хватает места, рисуются вправо от курсора — как у Android у края экрана. Пока ручку
     * тянут, она не разворачивается: sora ведёт её от пальца с поправкой на сторону, и разворот
     * на ходу перекидывал бы выделение на несколько знаков.
     */
    private inner class Handles : HandleStyleSideDrop(context) {
        override fun draw(canvas: Canvas, handleType: Int, x: Float, y: Float, rowHeight: Int, color: Int, descriptor: HandleDescriptor) {
            val reach = when (handleType) {
                HANDLE_TYPE_LEFT -> SIDE_DROP
                HANDLE_TYPE_INSERT -> DROP / 2
                else -> 0
            } * dpUnit
            val edge = measureTextRegionOffset() - dividerMarginRight
            // «Тянут» у sora не гаснет до следующего касания — ещё и держат ли ручку.
            val dragged = eventHandler.hasAnyHeldHandle() && eventHandler.isHandleMoving
            // Курсор сам под номерами (код сдвинут вбок) — прячется вместе с ним.
            val turn = reach > 0 &&
                if (dragged) descriptor.alignment == ALIGN_RIGHT else x >= edge && x - reach < edge

            super.draw(canvas, if (turn) HANDLE_TYPE_RIGHT else handleType, x, y, rowHeight, color, descriptor)
        }
    }

    override fun onCreateRenderer(): EditorRenderer = object : EditorRenderer(this) {
        override fun drawScrollBarVertical(canvas: Canvas) {
            super.drawScrollBarVertical(canvas)
            verticalScrollBarRect.left -= GRAB * this@ConfigEditor.dpUnit
        }

        override fun drawScrollBarHorizontal(canvas: Canvas) {
            super.drawScrollBarHorizontal(canvas)
            horizontalScrollBarRect.top -= GRAB * this@ConfigEditor.dpUnit
        }
    }

    private companion object {
        const val GRAB = 16
        const val ROOM = 2

        // Ширина капель sora, dp: `HandleStyleDrop` и `HandleStyleSideDrop`.
        const val DROP = 20
        const val SIDE_DROP = 22
    }
}

/**
 * Меню выделения в фирменном овале (D-124): словами, а не значками sora-editor, и только
 * нужное. Когда показаться, решает встроенное окно: долгое нажатие, отпущенные ручки,
 * касание курсора (тогда в меню «Вставить» — вставка прямо в место курсора).
 */
internal class EditorMenu(private val editor: CodeEditor) : EditorTextActionWindow(editor) {
    private val dp = editor.dpUnit

    private val row = LinearLayout(editor.context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
    }

    private val cut = button(R.string.config_menu_cut) { editor.cutText() }
    private val copy = button(R.string.config_menu_copy) {
        editor.copyText()
        editor.setSelection(editor.cursor.rightLine, editor.cursor.rightColumn)
    }
    private val paste = button(R.string.config_menu_paste) {
        editor.pasteText()
        editor.setSelection(editor.cursor.rightLine, editor.cursor.rightColumn)
    }
    private val all = button(R.string.config_menu_select_all, keep = true) {
        editor.selectAll()
        // После «Выделить всё» сразу нужны «Копировать» и «Вырезать» — меню остаётся.
        editor.postDelayedInLifecycle({ displayWindow() }, 100)
    }

    init {
        view.removeAllViews()
        // Не шире редактора; на узком экране меню листается вбок.
        view.addView(object : HorizontalScrollView(editor.context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val most = editor.width - (16 * dp).toInt()
                val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtMost(most)

                super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), heightMeasureSpec)
            }
        }.apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        })
        setSize(width, (HEIGHT * dp).toInt())
    }

    override fun displayWindow() {
        val selected = editor.cursor.isSelected

        cut.isVisible = selected && editor.isEditable
        copy.isVisible = selected
        paste.isVisible = editor.isEditable && editor.hasClip()
        restyle()

        // Место — как у встроенного окна (над выделением, а нет места — под ним), но по
        // ширине слов, а не 230dp под значки, и в пределах редактора, а не у края экрана.
        view.measure(
            MeasureSpec.makeMeasureSpec(editor.width, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST),
        )
        setSize(view.measuredWidth, height)

        val cursor = editor.cursor
        val top = if (selected) {
            minOf(above(editor.leftHandleDescriptor.position), above(editor.rightHandleDescriptor.position))
        } else {
            above(editor.insertHandleDescriptor.position)
        }
        val center = (editor.getOffset(cursor.leftLine, cursor.leftColumn) + editor.getOffset(cursor.rightLine, cursor.rightColumn)) / 2

        setLocationAbsolutely(
            (center - width / 2f).toInt().coerceIn(0, (editor.width - width).coerceAtLeast(0)),
            top.coerceAtMost(editor.height - height - 5).coerceAtLeast(0),
        )
        show()
    }

    /** Верх меню: над строкой ручки, а если там не влезает — под ней. */
    private fun above(handle: RectF): Int {
        val row = editor.rowHeight

        return if (handle.top - row * 3 / 2 > height) (handle.top - row * 3 / 2 - height).toInt()
        else (handle.bottom + row / 2).toInt()
    }

    private fun restyle() {
        val scheme = editor.colorScheme
        val fill = scheme.getColor(EditorColorScheme.TEXT_ACTION_WINDOW_BACKGROUND)
        val ink = scheme.getColor(EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR)

        view.background = pill(fill).apply { setStroke(dp.toInt().coerceAtLeast(1), ColorUtils.setAlphaComponent(ink, 0x26)) }

        for (button in listOf(cut, copy, paste, all)) {
            button.setTextColor(ink)
            button.background = RippleDrawable(ColorStateList.valueOf(ColorUtils.setAlphaComponent(ink, 0x1F)), null, pill(0xFFFFFFFF.toInt()))
        }
    }

    private fun button(label: Int, keep: Boolean = false, action: () -> Unit) = TextView(editor.context).apply {
        text = context.getString(label)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        typeface = Typeface.create(Typeface.DEFAULT, 500, false)
        gravity = Gravity.CENTER
        setPadding((10 * dp).toInt(), 0, (10 * dp).toInt(), 0)
        setOnClickListener {
            action()
            if (!keep) dismiss()
        }
        row.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ((HEIGHT - 8) * dp).toInt()))
    }

    private fun pill(color: Int) = GradientDrawable().apply {
        cornerRadius = HEIGHT * dp / 2
        setColor(color)
    }

    private companion object {
        const val HEIGHT = 48
    }
}

/**
 * Полосы прокрутки — скруглённые, цвета темы (D-124); ползунок, пока его держат, темнеет.
 * sora отдаёт под полосу 10dp, овал — 6dp у края: в 4dp его трудно было поймать и глазом.
 */
internal fun CodeEditor.styleScrollbars() {
    val scheme = colorScheme
    val thumb = ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf()),
        intArrayOf(scheme.getColor(EditorColorScheme.SCROLL_BAR_THUMB_PRESSED), scheme.getColor(EditorColorScheme.SCROLL_BAR_THUMB)),
    )
    val track = ColorStateList.valueOf(scheme.getColor(EditorColorScheme.SCROLL_BAR_TRACK))

    fun bar(color: ColorStateList, vertical: Boolean): Drawable {
        val pill = GradientDrawable().apply {
            cornerRadius = 100 * dpUnit
            this.color = color
        }
        val side = (3 * dpUnit).toInt()
        val edge = (1 * dpUnit).toInt()

        return if (vertical) InsetDrawable(pill, side, edge, edge, edge) else InsetDrawable(pill, edge, side, edge, edge)
    }

    verticalScrollbarThumbDrawable = bar(thumb, true)
    verticalScrollbarTrackDrawable = bar(track, true)
    horizontalScrollbarThumbDrawable = bar(thumb, false)
    horizontalScrollbarTrackDrawable = bar(track, false)
}
