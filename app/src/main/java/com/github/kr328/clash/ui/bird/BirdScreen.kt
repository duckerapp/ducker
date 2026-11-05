package com.github.kr328.clash.ui.bird

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.github.kr328.clash.R
import com.github.kr328.clash.ui.bird.BirdGame.Companion.COLUMN_W
import com.github.kr328.clash.ui.bird.BirdGame.Companion.DUCK_H
import com.github.kr328.clash.ui.bird.BirdGame.Companion.DUCK_X
import com.github.kr328.clash.ui.bird.BirdGame.Companion.GAP
import com.github.kr328.clash.ui.bird.BirdGame.Companion.REED_SLACK
import com.github.kr328.clash.ui.components.HeaderButton
import com.github.kr328.clash.ui.components.Symbols
import com.github.kr328.clash.ui.components.formatCount
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Ducker Bird, the easter egg behind six taps on Home's title: [BirdGame] drawn in black and white — the theme's
 * dark or light, never grey — in whole game pixels. Its frames run only while it is on screen: the layer gone, the
 * loop is cancelled; the app hidden, the frame clock stops and the flight waits for a tap. [best] is the record.
 */
@Composable
internal fun BirdScreen(best: Int, onBest: (Int) -> Unit, onBack: () -> Unit) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val paper = if (dark) Color.Black else Color.White
    val ink = if (dark) Color.White else Color.Black
    val view = LocalView.current
    val density = LocalDensity.current
    val saveBest by rememberUpdatedState(onBest)

    BoxWithConstraints(Modifier.fillMaxSize().background(paper)) {
        val width = constraints.maxWidth
        // The water stays above the three-button bar, the walls start under the arrow.
        val field = constraints.maxHeight - WindowInsets.systemBars.getBottom(density)
        val scale = max(1, min(width / WIDE, field / TALL))
        val shiftX = (width - width / scale * scale) / 2f
        val shiftY = (field - field / scale * scale).toFloat()
        val status = WindowInsets.statusBars.getTop(density)
        val arrow = status + with(density) { ARROW_ROW.toPx() }
        val top = ceil((arrow - shiftY) / scale).toInt()

        val game = remember(width, field, top) { BirdGame(width / scale, field / scale, top) }

        var frame by remember { mutableIntStateOf(0) }
        var phase by remember(game) { mutableStateOf(game.phase) }
        var paused by remember(game) { mutableStateOf(false) }
        var restartable by remember(game) { mutableStateOf(false) }
        var record by remember { mutableIntStateOf(best) }

        LaunchedEffect(game) {
            var last = withFrameNanos { it }

            while (true) {
                val now = withFrameNanos { it }
                val flying = game.phase == BirdGame.Phase.Flying

                game.advance((now - last) / 1e9f)
                last = now

                if (flying && game.phase != BirdGame.Phase.Flying) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

                if (game.phase == BirdGame.Phase.Over && game.score > record) {
                    record = game.score
                    saveBest(record)
                }

                phase = game.phase
                paused = game.paused
                restartable = game.canRestart
                // Paused, nothing moves: the last frame stays on screen without being drawn again.
                if (!game.paused) frame++
            }
        }

        LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { game.pause() }

        Canvas(
            Modifier.fillMaxSize().pointerInput(game) {
                // On the press, not the release: a flap a tap's length late misses the window.
                awaitEachGesture {
                    awaitFirstDown()
                    game.tap()
                }
            },
        ) {
            // Read to draw again each frame; the screen around it does not recompose for it.
            frame

            Pixels(this, scale.toFloat(), shiftX, shiftY, ink, paper).draw(game)
        }

        val hint = when (phase) {
            BirdGame.Phase.Ready -> true
            BirdGame.Phase.Flying -> paused
            BirdGame.Phase.Falling -> false
            BirdGame.Phase.Over -> restartable
        }
        val showBest = record > 0 && (phase == BirdGame.Phase.Ready || phase == BirdGame.Phase.Over)

        Column(
            modifier = Modifier.align(BiasAlignment(0f, 0.05f)).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showBest) {
                Text(
                    text = stringResource(R.string.bird_best, formatCount(record)),
                    style = MaterialTheme.typography.titleMedium,
                    color = ink,
                    textAlign = TextAlign.Center,
                )
            }

            // Kept in the layout while hidden: the record above does not jump when it shows.
            Text(
                text = stringResource(R.string.bird_tap),
                style = MaterialTheme.typography.bodyLarge,
                color = if (hint) ink else Color.Transparent,
                textAlign = TextAlign.Center,
            )
        }

        HeaderButton(
            icon = Symbols.ArrowBack,
            description = stringResource(R.string.back),
            onClick = onBack,
            // From the raw inset, as the field: a layer above may have consumed it for padding.
            modifier = Modifier.padding(start = 4.dp, top = with(density) { status.toDp() } + 4.dp),
        )
    }
}

/** Draws the game in game pixels of [scale] screen pixels, the field shifted by [x] and [y]. */
private class Pixels(
    private val scope: DrawScope,
    private val scale: Float,
    private val x: Float,
    private val y: Float,
    private val ink: Color,
    private val paper: Color,
) {
    fun draw(game: BirdGame) {
        for (column in game.columns) {
            val left = column.x.roundToInt()

            wall(left, column.gap)

            if (column.gap + GAP < game.water) reeds(left, column.gap + GAP, column.seed, game.water)
        }

        duck(game)
        water(game)

        if (game.phase == BirdGame.Phase.Ready) {
            word("DUCKER", game.width, game.height / 5)
            word("BIRD", game.width, game.height / 5 + 20)
        } else {
            word(game.score.toString(), game.width, game.top + 6, size = 2)
        }
    }

    /** A brick wall from above the screen's top down to [bottom]: an outline and courses of bricks laid from below. */
    private fun wall(left: Int, bottom: Int) {
        rect(left, -1, 1, bottom + 1)
        rect(left + COLUMN_W - 1, -1, 1, bottom + 1)

        var line = bottom - 1
        var course = 0

        while (line >= -1) {
            rect(left, line, COLUMN_W, 1)

            var joint = left + (if (course % 2 == 0) BRICK / 2 else BRICK)

            while (joint < left + COLUMN_W - 1) {
                rect(joint, line - COURSE + 1, 1, COURSE - 1)
                joint += BRICK
            }

            line -= COURSE
            course++
        }
    }

    /** Reeds from the water up to [top]: every other stalk with a cattail. The tallest reach the hit line exactly. */
    private fun reeds(left: Int, top: Int, seed: Int, water: Int) {
        for (i in 0 until STALKS) {
            val stalk = left + 2 + 3 * i
            val tip = top + REED_SLACK + REED_TIPS[(i + seed) and 7]

            if (i % 2 == 0) {
                rect(stalk - 1, tip, 3, CATTAIL)
                rect(stalk, tip + CATTAIL, 1, water - tip - CATTAIL)
            } else {
                rect(stalk, tip, 1, water - tip)
            }
        }
    }

    /** The duck: wing up while it rises fast, belly up after a hit, bobbing while it floats. */
    private fun duck(game: BirdGame) {
        val down = game.phase == BirdGame.Phase.Falling || game.phase == BirdGame.Phase.Over
        val sprite = if (game.phase == BirdGame.Phase.Flying && game.vy < RISING) FLAP else REST
        val bob = if (game.afloat) sin(game.clock * BOB).roundToInt() else 0
        val top = game.y.roundToInt() + bob

        for (row in 0 until DUCK_H) {
            val spans = sprite[if (down) DUCK_H - 1 - row else row]

            for (i in spans.indices step 2) rect(DUCK_X + spans[i], top + row, spans[i + 1], 1)
        }
    }

    /** The water over the duck's lower rows: a line and two rows of ripples drifting at two speeds. */
    private fun water(game: BirdGame) {
        val surface = this.y + game.water * scale

        scope.drawRect(paper, Offset(0f, surface), Size(scope.size.width, scope.size.height - surface))
        scope.drawRect(ink, Offset(0f, surface), Size(scope.size.width, scale))

        val near = game.travel.toInt() % RIPPLE
        val far = (game.travel * 0.6f).toInt() % RIPPLE

        for (step in -RIPPLE..game.width + RIPPLE step RIPPLE) {
            rect(step - near + 3, game.water + 4, 4, 1)
            rect(step - far + 9, game.water + 9, 4, 1)
        }
    }

    /** [text] in the 3×5 letters, [size] game pixels to a letter's pixel, centred across [width] at [top]. */
    private fun word(text: String, width: Int, top: Int, size: Int = 3) {
        var left = (width - (text.length * 4 - 1) * size) / 2

        for (char in text) {
            val glyph = GLYPHS.getValue(char)

            for (row in 0 until 5) for (col in 0 until 3) {
                if (glyph[row][col] == '#') rect(left + col * size, top + row * size, size, size)
            }

            left += 4 * size
        }
    }

    private fun rect(left: Int, top: Int, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return

        scope.drawRect(ink, Offset(x + left * scale, y + top * scale), Size(width * scale, height * scale))
    }
}

/** A sprite's rows as runs of ink: start and length, pairs. */
private fun sprite(vararg rows: String): Array<IntArray> = Array(rows.size) { r ->
    val row = rows[r]
    val runs = ArrayList<Int>()
    var start = -1

    for (i in 0..row.length) {
        val inked = i < row.length && row[i] == '#'

        if (inked && start < 0) start = i
        if (!inked && start >= 0) {
            runs += start
            runs += i - start
            start = -1
        }
    }

    runs.toIntArray()
}

/** Our logo's duck in pixels: the tuft, the eye, the beak, the wing as a leaf parted from the body by a line. */
private val REST = sprite(
    ".......##........",
    "........#.####...",
    "........#######..",
    ".......#########.",
    ".......#####o###.",
    ".......##########",
    "........#######..",
    "........#######..",
    "#......#########.",
    "#######o#########",
    ".######o#########",
    "..######o########",
    "...######o######.",
    "....######o####..",
    "......####o###...",
)

private val FLAP = sprite(
    ".......##........",
    "........#.####...",
    "#.......#######..",
    "##.....#########.",
    "###....#####o###.",
    "####...##########",
    ".####..#######...",
    ".#####o#######...",
    "..#####o#######..",
    "...#####o########",
    "....####o########",
    "......##o########",
    ".......########..",
    "........######...",
    "..........###....",
)

private val GLYPHS = mapOf(
    '0' to listOf("###", "#.#", "#.#", "#.#", "###"),
    '1' to listOf(".#.", "##.", ".#.", ".#.", "###"),
    '2' to listOf("###", "..#", "###", "#..", "###"),
    '3' to listOf("###", "..#", ".##", "..#", "###"),
    '4' to listOf("#.#", "#.#", "###", "..#", "..#"),
    '5' to listOf("###", "#..", "###", "..#", "###"),
    '6' to listOf("###", "#..", "###", "#.#", "###"),
    '7' to listOf("###", "..#", "..#", ".#.", ".#."),
    '8' to listOf("###", "#.#", "###", "#.#", "###"),
    '9' to listOf("###", "#.#", "###", "..#", "###"),
    'B' to listOf("##.", "#.#", "##.", "#.#", "##."),
    'C' to listOf(".##", "#..", "#..", "#..", ".##"),
    'D' to listOf("##.", "#.#", "#.#", "#.#", "##."),
    'E' to listOf("###", "#..", "##.", "#..", "###"),
    'I' to listOf("###", ".#.", ".#.", ".#.", "###"),
    'K' to listOf("#.#", "#.#", "##.", "#.#", "#.#"),
    'R' to listOf("##.", "#.#", "##.", "#.#", "#.#"),
    'U' to listOf("#.#", "#.#", "#.#", "#.#", "###"),
)

/** The field's least width and height in game pixels: the scale is the largest whole one that leaves both. */
private const val WIDE = 144
private const val TALL = 250

/** The back arrow's row under the status bar: the walls start below it. */
private val ARROW_ROW = 56.dp

private const val BRICK = 8
private const val COURSE = 4
private const val STALKS = 8
private const val CATTAIL = 6

/** How far under the hit line each stalk ends; a zero in any eight in a row, so the tallest reach it. */
private val REED_TIPS = intArrayOf(1, 0, 3, 2, 2, 0, 3, 1)

private const val RIPPLE = 12

/** Faster up than this, the wing is drawn up. */
private const val RISING = -40f

/** The bobbing afloat, radians a second. */
private const val BOB = 2.4f
