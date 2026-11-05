package com.github.kr328.clash.ui.bird

import kotlin.math.min
import kotlin.random.Random

/**
 * Ducker Bird's rules, apart from drawing: [BirdScreen] draws them, a test plays them. Game pixels with y down, seconds.
 *
 * The duck flies over a pond; a tap is a flap. The water is safe, the duck floats on it. Each column is a brick wall
 * from the top with a window of a constant [GAP] under it; reeds fill the rest down to the water, unless the window
 * reaches the water — then the duck passes under the wall afloat. Only the speed grows, never the window.
 * [top] — the rows under the status bar and the back arrow: a window never starts there.
 */
internal class BirdGame(val width: Int, val height: Int, val top: Int, private val random: Random = Random.Default) {
    enum class Phase { Ready, Flying, Falling, Over }

    /** One column: its left edge and the window's top; [passed] once it has counted. */
    class Column(var x: Float, val gap: Int, val seed: Int) {
        var passed = false
    }

    val water = height - WATER
    val rest = (water - DUCK_H + SINK).toFloat()

    var phase = Phase.Ready
        private set
    var paused = false
        private set
    var score = 0
        private set

    /** The duck's top. */
    var y = rest
        private set
    var vy = 0f
        private set

    /** Game time and the distance flown: the bobbing, the water's ripples, the lockout after a fall. */
    var clock = 0f
        private set
    var travel = 0f
        private set

    val columns = ArrayList<Column>()

    val afloat get() = y >= rest && vy == 0f
    val canRestart get() = phase == Phase.Over && clock - overAt >= LOCKOUT

    private var overAt = 0f
    private var spare = 0f
    private var lastGap = water - GAP

    fun tap() {
        when (phase) {
            Phase.Ready -> phase = Phase.Flying
            Phase.Flying -> paused = false
            Phase.Falling -> return
            Phase.Over -> {
                if (!canRestart) return

                columns.clear()
                score = 0
                y = rest
                lastGap = water - GAP
                phase = Phase.Flying
            }
        }

        vy = FLAP
    }

    /** The app went to the background mid-flight: the next tap goes on. */
    fun pause() {
        if (phase == Phase.Flying) paused = true
    }

    /** [seconds] since the last frame, in fixed steps: the same flight at 60 and at 120 frames a second. */
    fun advance(seconds: Float) {
        if (paused) return

        spare += min(seconds, MAX_FRAME)

        while (spare >= STEP) {
            step()
            spare -= STEP
        }
    }

    private fun step() {
        clock += STEP

        when (phase) {
            Phase.Ready, Phase.Over -> travel += DRIFT * STEP
            Phase.Falling -> {
                fall()

                if (y >= rest) {
                    phase = Phase.Over
                    overAt = clock
                }
            }
            Phase.Flying -> {
                fall()

                val speed = SPEED * min(MAX_BOOST, 1f + score * BOOST)

                travel += speed * STEP

                for (column in columns) {
                    column.x -= speed * STEP

                    if (!column.passed && column.x + COLUMN_W <= DUCK_X + HIT_LEFT) {
                        column.passed = true
                        score++
                    }
                }

                columns.removeAll { it.x + COLUMN_W < 0 }

                if (columns.isEmpty()) columns += Column(width.toFloat(), nextGap(), random.nextInt())

                while (columns.last().x + SPACING <= width + COLUMN_W) {
                    columns += Column(columns.last().x + SPACING, nextGap(), random.nextInt())
                }

                if (hit()) {
                    phase = Phase.Falling
                    vy = 0f
                }
            }
        }
    }

    private fun fall() {
        vy = min(vy + GRAVITY * STEP, MAX_FALL)
        y += vy * STEP

        if (y < 0f) {
            y = 0f
            vy = 0f
        }

        if (y >= rest) {
            y = rest
            vy = 0f
        }
    }

    private fun hit(): Boolean {
        val left = DUCK_X + HIT_LEFT
        val right = DUCK_X + HIT_RIGHT
        val upper = y + HIT_TOP
        val lower = y + HIT_BOTTOM

        return columns.any { column ->
            val reeds = column.gap + GAP

            column.x < right && column.x + COLUMN_W > left &&
                (upper < column.gap || reeds < water && lower > reeds + REED_SLACK)
        }
    }

    /**
     * The next window's top. A quarter of them reach the water, for ducking under a wall; the rest anywhere between
     * the arrow and the water. Never more than [MAX_RISE] above the last one or [MAX_DROP] below it: at the top speed
     * the duck still makes the climb or the drop between two columns; the test's autopilot checks it.
     */
    private fun nextGap(): Int {
        val high = water - GAP
        val low = min(top + MIN_WALL, high)
        val wanted = if (random.nextFloat() < LOW_CHANCE) high else low + random.nextInt(high - low + 1)

        return wanted.coerceIn(lastGap - MAX_RISE, lastGap + MAX_DROP).coerceIn(low, high).also { lastGap = it }
    }

    companion object {
        /** The duck sprite's size and where it flies. */
        const val DUCK_W = 20
        const val DUCK_H = 17
        const val DUCK_X = 26

        /** Rows of the duck under the waterline when it floats: the feet and the belly. */
        const val SINK = 5

        /** What counts as the duck for a hit: without the wing's tip, the beak and the feet — a graze is forgiven. */
        const val HIT_LEFT = 2
        const val HIT_RIGHT = 16
        const val HIT_TOP = 1
        const val HIT_BOTTOM = 15

        /**
         * The window — about four heights of the duck's body, as in the genre's classic — the column and the step between
         * columns, about one a screen on a phone held upright.
         */
        const val GAP = 64
        const val COLUMN_W = 24
        const val SPACING = 136

        /** The water's band at the bottom, the shortest wall at the top, the reeds' tips that do not count. */
        const val WATER = 18
        const val MIN_WALL = 12
        const val REED_SLACK = 2

        /** A flap lifts the duck 28 px, nearly two heights of its body, in 0.3 s. */
        const val GRAVITY = 598f
        const val FLAP = -183f
        const val MAX_FALL = 320f

        /** Pixels a second at the start, plus [BOOST] for each column passed, up to [MAX_BOOST] times. */
        const val SPEED = 64f
        const val BOOST = 0.02f
        const val MAX_BOOST = 1.8f

        /** The water's drift while the duck sits. */
        const val DRIFT = 8f

        const val LOW_CHANCE = 0.25f
        const val MAX_RISE = 48
        const val MAX_DROP = 64

        /** After a fall, taps wait this long: one more frantic tap does not start the next game unseen. */
        const val LOCKOUT = 0.5f

        const val STEP = 1f / 480
        const val MAX_FRAME = 0.05f
    }
}
