package com.github.kr328.clash.ui.bird

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BirdGameTest {
    /** Every course stays passable at the top speed: a plain autopilot that sees one column ahead clears it. */
    @Test
    fun `an autopilot clears 300 columns on every course`() {
        // A tall phone, a short one, a phone on its side.
        for ((width, height) in listOf(144 to 316, 136 to 250, 600 to 200)) {
            for (seed in 1..20) {
                val game = BirdGame(width, height, top = 20, random = Random(seed))

                game.tap()

                var frames = 0

                while (game.phase == BirdGame.Phase.Flying && game.score < 300 && frames++ < 300 * 60 * 120) {
                    game.autopilot()
                    game.advance(FRAME)
                }

                assertEquals(BirdGame.Phase.Flying, game.phase, "${width}x$height, course $seed, fell at ${game.score}")
                assertEquals(300, game.score)
            }
        }
    }

    @Test
    fun `the water holds the duck`() {
        val game = BirdGame(144, 316, top = 20, random = Random(1))

        game.tap()
        repeat((1.5f / FRAME).toInt()) { game.advance(FRAME) }

        // Up and back down well before the first column arrives: afloat, still in the game.
        assertEquals(BirdGame.Phase.Flying, game.phase)
        assertTrue(game.afloat)
        assertEquals(game.rest, game.y)
    }

    @Test
    fun `a tap right after a fall does not start the next game`() {
        val game = BirdGame(144, 316, top = 20, random = Random(2))

        game.tap()

        // No more taps: sooner or later reeds take the floating duck.
        repeat((120f / FRAME).toInt()) {
            if (game.phase != BirdGame.Phase.Over) game.advance(FRAME)
        }

        assertEquals(BirdGame.Phase.Over, game.phase)

        game.tap()
        assertEquals(BirdGame.Phase.Over, game.phase)

        repeat((BirdGame.LOCKOUT / FRAME).toInt() + 1) { game.advance(FRAME) }
        game.tap()

        assertEquals(BirdGame.Phase.Flying, game.phase)
        assertEquals(0, game.score)
        assertTrue(game.columns.isEmpty())
    }

    /** Flaps when the duck sinks near the bottom of the next window. */
    private fun BirdGame.autopilot() {
        val next = columns.firstOrNull { it.x + BirdGame.COLUMN_W > BirdGame.DUCK_X + BirdGame.HIT_LEFT }
        val floor = next?.let { it.gap + BirdGame.GAP } ?: water

        if (y + BirdGame.HIT_BOTTOM > floor - 8 && vy >= 0f) tap()
    }

    private companion object {
        const val FRAME = 1f / 120
    }
}
