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
import com.github.kr328.clash.ui.bird.BirdGame.Companion.DUCK_X
import com.github.kr328.clash.ui.bird.BirdGame.Companion.GAP
import com.github.kr328.clash.ui.bird.BirdGame.Companion.REED_SLACK
import com.github.kr328.clash.ui.components.HeaderButton
import com.github.kr328.clash.ui.components.Symbols
import com.github.kr328.clash.ui.components.formatCount
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
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
        val shiftX = ((width - width / scale * scale) / 2).toFloat()
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

/**
 * Draws the game in game pixels of [scale] screen pixels, the field shifted by [x] and [y]. What moves is placed to the
 * screen's pixel, not the game's: in whole game pixels a slow cloud jumped seven to ten screen pixels at a time.
 */
private class Pixels(
    private val scope: DrawScope,
    private val scale: Float,
    private val x: Float,
    private val y: Float,
    private val ink: Color,
    private val paper: Color,
) {
    fun draw(game: BirdGame) {
        sky(game)

        for (column in game.columns) {
            val left = snap(column.x)

            wall(left, column.gap, column.seed)

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

    /** Clouds and specks, drifting slower than the columns and the specks slowest: the sky has depth. */
    private fun sky(game: BirdGame) {
        val room = game.water - game.top

        for (i in CLOUDS.indices step 2) {
            val span = (game.width + CLOUD_W).toFloat()
            val left = wrap(CLOUDS[i] * span - game.travel * CLOUD_PACE, span) - CLOUD_W

            blit(CLOUD, snap(left), (game.top + (CLOUDS[i + 1] * room).toInt()).toFloat())
        }

        for (i in SPECKS.indices step 2) {
            val left = wrap(SPECKS[i] * game.width - game.travel * SPECK_PACE, game.width.toFloat())

            rect(left, (game.top + (SPECKS[i + 1] * room).toInt()).toFloat(), 1, 1)
        }
    }

    /** A brick wall from above the screen's top down to [bottom], over the sky: courses laid from below, a dark brick here and there. */
    private fun wall(left: Float, bottom: Int, seed: Int) {
        rect(left, -1f, COLUMN_W, bottom + 1, paper)
        rect(left, -1f, 1, bottom + 1)
        rect(left + COLUMN_W - 1, -1f, 1, bottom + 1)

        var line = bottom - 1
        var course = 0

        while (line >= -1) {
            rect(left, line.toFloat(), COLUMN_W, 1)

            val even = course % 2 == 0
            var joint = if (even) BRICK / 2 else BRICK

            while (joint < COLUMN_W - 1) {
                rect(left + joint, (line - COURSE + 1).toFloat(), 1, COURSE - 1)
                joint += BRICK
            }

            // The same bricks stay dark while the wall passes: picked from its seed and the course, not at random.
            val mix = (seed + course) * MIX

            if ((mix ushr 24) % DARK == 0) {
                val start = 1 + (if (even) BRICK / 2 else 0) + BRICK * ((mix ushr 8) and 1)

                rect(left + start, (line - COURSE + 1).toFloat(), minOf(BRICK - 1, COLUMN_W - 1 - start), COURSE - 1)
            }

            line -= COURSE
            course++
        }
    }

    /** Reeds from the water up to [top]: thin stalks with square heads; the tallest reach the hit line exactly. */
    private fun reeds(left: Float, top: Int, seed: Int, water: Int) {
        for (i in 0 until STALKS) {
            val stalk = left + 2 + 5 * i
            val tip = top + REED_SLACK + REED_TIPS[Math.floorMod(i + seed, STALKS)]

            rect(stalk - 1, tip.toFloat(), 3, 3)
            rect(stalk, (tip + 3).toFloat(), 1, water - tip - 3)
        }
    }

    /** The duck: the wing up while it rises, tucked while it dives, spread otherwise; belly up after a hit; bobbing afloat. */
    private fun duck(game: BirdGame) {
        val down = game.phase == BirdGame.Phase.Falling || game.phase == BirdGame.Phase.Over
        val flying = game.phase == BirdGame.Phase.Flying && !game.afloat
        val sprite = when {
            !flying -> GLIDE
            game.vy < RISING -> FLAP
            game.vy > DIVING -> TUCK
            else -> GLIDE
        }
        val bob = if (game.afloat) sin(game.clock * BOB) else 0f

        blit(sprite, DUCK_X.toFloat(), snap(game.y + bob), flip = down)
    }

    /** The water over the duck's lower rows: a line and two rows of ripples drifting at two speeds. */
    private fun water(game: BirdGame) {
        val surface = this.y + game.water * scale

        scope.drawRect(paper, Offset(0f, surface), Size(scope.size.width, scope.size.height - surface))
        scope.drawRect(ink, Offset(0f, surface), Size(scope.size.width, scale))

        val near = game.travel % RIPPLE
        val far = game.travel * 0.6f % RIPPLE

        for (step in -RIPPLE..game.width + RIPPLE step RIPPLE) {
            rect(step - near + 3, (game.water + 5).toFloat(), 5, 1)
            rect(step - far + 9, (game.water + 11).toFloat(), 5, 1)
        }
    }

    /** [text] in the 3×5 letters, [size] game pixels to a letter's pixel, centred across [width] at [top]. */
    private fun word(text: String, width: Int, top: Int, size: Int = 3) {
        var left = (width - (text.length * 4 - 1) * size) / 2

        for (char in text) {
            val glyph = GLYPHS.getValue(char)

            for (row in 0 until 5) for (col in 0 until 3) {
                if (glyph[row][col] == '#') rect((left + col * size).toFloat(), (top + row * size).toFloat(), size, size)
            }

            left += 4 * size
        }
    }

    /** A [sprite] with its top left at [left], [top]; [flip] — upside down. */
    private fun blit(sprite: Array<IntArray>, left: Float, top: Float, flip: Boolean = false) {
        for (row in sprite.indices) {
            val runs = sprite[if (flip) sprite.size - 1 - row else row]

            for (i in runs.indices step 3) rect(left + runs[i], top + row, runs[i + 1], 1, if (runs[i + 2] == INK) ink else paper)
        }
    }

    /** Game pixels from [left], [top] — fractional for what moves — placed to the screen's whole pixel, so edges stay sharp. */
    private fun rect(left: Float, top: Float, width: Int, height: Int, color: Color = ink) {
        if (width <= 0 || height <= 0) return

        scope.drawRect(color, Offset(round(x + left * scale), round(y + top * scale)), Size(width * scale, height * scale))
    }

    /** An object's position to the screen's pixel first: each of its parts then rounds alike, without a seam between them. */
    private fun snap(value: Float) = round(value * scale) / scale

    /** [value] brought into 0 until [span]. */
    private fun wrap(value: Float, span: Float) = (value % span + span) % span
}

/**
 * A sprite's rows as runs: start, length and [INK] or [PAPER], three numbers each. `#` is the outline, `o` the fill that
 * hides what is behind, `.` lets it show.
 */
private fun sprite(vararg rows: String): Array<IntArray> = Array(rows.size) { r ->
    val row = rows[r]
    val runs = ArrayList<Int>()
    var start = 0

    while (start < row.length) {
        var end = start

        while (end < row.length && row[end] == row[start]) end++

        if (row[start] != '.') {
            runs += start
            runs += end - start
            runs += if (row[start] == '#') INK else PAPER
        }

        start = end
    }

    runs.toIntArray()
}

private const val INK = 1
private const val PAPER = 0

/** The duck — a round chick after the owner's sketch — in three states: gliding, flapping, diving. */
private val GLIDE = sprite(
    "..........####......",
    "........##oooo#.....",
    ".......#ooooooo#....",
    ".......#oooooooo#...",
    "......#ooooo##oo#...",
    "......#ooooo##o#####",
    ".######oooooooo#ooo#",
    "#oooooo#ooooooo####.",
    "###ooooo#ooooooo#...",
    "#ooooooooooooooo#...",
    ".###ooo#oooooooo#...",
    ".#o####oooooooo#....",
    "..#ooooooooooo#.....",
    "...#ooooooooo#......",
    "....##########......",
    "....###...#.........",
    ".....##...###.......",
)

private val FLAP = sprite(
    "..........####......",
    "........##oooo#.....",
    ".#####.#ooooooo#....",
    "#ooooo##oooooooo#...",
    "###oooo#oooo##oo#...",
    ".#oooooo#ooo##o#####",
    "..##oo##ooooooo#ooo#",
    "...###ooooooooo####.",
    "..#ooooooooooooo#...",
    ".#oooooooooooooo#...",
    ".#oooooooooooooo#...",
    ".#ooooooooooooo#....",
    "..#ooooooooooo#.....",
    "...#ooooooooo#......",
    "....##########......",
    "....###...#.........",
    ".....##...###.......",
)

private val TUCK = sprite(
    "..........####......",
    "........##oooo#.....",
    ".......#ooooooo#....",
    ".......#oooooooo#...",
    "......#ooooo##oo#...",
    "......#ooooo##o#####",
    ".....#ooooooooo#ooo#",
    "...#####ooooooo####.",
    "..#ooooo#ooooooo#...",
    ".#o####oo#oooooo#...",
    ".###ooooo#oooooo#...",
    ".#oo#####oooooo#....",
    "..#ooooooooooo#.....",
    "...#ooooooooo#......",
    "....##########......",
    "....###...#.........",
    ".....##...###.......",
)

private val CLOUD = sprite(
    ".......####.........",
    "......#oooo#........",
    "...###oooooo####....",
    "..#ooooooooooooo##..",
    ".#oooooooooooooooo##",
    "#oooooooooooooooooo#",
    ".##################.",
)

private const val CLOUD_W = 20

/** Where the clouds and the specks start: across the field's width and down the sky between the arrow and the water. */
private val CLOUDS = floatArrayOf(0.08f, 0.06f, 0.70f, 0.40f, 0.36f, 0.62f)
private val SPECKS = floatArrayOf(0.14f, 0.36f, 0.62f, 0.22f, 0.86f, 0.12f, 0.36f, 0.66f, 0.26f, 0.82f, 0.78f, 0.74f, 0.50f, 0.48f)

/** The sky's share of the columns' speed. */
private const val CLOUD_PACE = 0.25f
private const val SPECK_PACE = 0.12f

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
private const val WIDE = 150
private const val TALL = 260

/** The back arrow's row under the status bar: the walls start below it. */
private val ARROW_ROW = 56.dp

private const val BRICK = 8
private const val COURSE = 4

/** About one course in ten has a dark brick; [MIX] spreads the seed over the courses (the golden ratio's hash). */
private const val DARK = 10
private const val MIX = -0x61c88647

private const val STALKS = 5

/** How far under the hit line each stalk ends: every five in a row have a zero, so the tallest reach it. */
private val REED_TIPS = intArrayOf(1, 0, 2, 0, 1)

private const val RIPPLE = 12

/** Faster up than this, the wing is up; faster down than [DIVING], tucked. */
private const val RISING = -40f
private const val DIVING = 150f

/** The bobbing afloat, radians a second. */
private const val BOB = 2.4f
