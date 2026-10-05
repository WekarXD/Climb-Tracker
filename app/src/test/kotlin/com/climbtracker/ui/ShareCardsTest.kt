package com.climbtracker.ui

import com.climbtracker.core.tracker.AttemptResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset

class ShareCardsTest {

    private val day = 86_400_000L
    private val october4 = 20_365 * day // 2025-10-04
    private val fail = AttemptResult.FAIL
    private val send = AttemptResult.SEND

    private fun card(attempts: List<Pair<Long, AttemptResult>>, best: Float = 0f, gym: String? = "Mi roco") =
        ShareCards.build("Amarillo", "6A", gym, 0xFFE8C020.toInt(), best, october4, attempts, ZoneOffset.UTC)

    @Test
    fun aFlashSaysSoWithItsFirstAttempt() {
        val card = card(listOf(october4 + day to send))
        assertEquals("FLASH", card.headline)
        assertNull(card.tagline)
        assertEquals("1.er intento", card.attempts)
        assertEquals("5 DE OCTUBRE", card.date)
        assertEquals("Amarillo \u00b7 6A", card.title)
        assertEquals("Mi roco", card.gym)
    }

    @Test
    fun aSendCountsTheAttemptThatDidIt() {
        val card = card(listOf(october4 to fail, october4 + day to fail, october4 + 2 * day to send, october4 + 3 * day to send))
        assertEquals("ENCADENADO", card.headline)
        assertEquals("3.er intento", card.attempts)
        assertEquals("6 DE OCTUBRE", card.date)
    }

    @Test
    fun aProjectShowsHowFarItGot() {
        val card = card(listOf(october4 to fail, october4 + 2 * day to fail), best = 0.62f)
        assertEquals("62 %", card.headline)
        assertEquals("EN PROYECTO", card.tagline)
        assertEquals("2 intentos", card.attempts)
        assertEquals("6 DE OCTUBRE", card.date)
    }

    @Test
    fun aBoulderNotTriedYetUsesTheDayItWasSaved() {
        val card = card(emptyList(), gym = null)
        assertEquals("0 %", card.headline)
        assertEquals("0 intentos", card.attempts)
        assertEquals("4 DE OCTUBRE", card.date)
        assertNull(card.gym)
    }

    @Test
    fun thePhotoFillsTheCardAroundTheCircuit() {
        // A wide photo on a tall card: scaled to the card's height, shifted to show the focus.
        val left = ShareCards.cover(4000f, 2000f, 1000f, 2000f, focusX = 0.1f, focusY = 0.5f)
        assertEquals(1f, left.scale, 1e-4f)
        assertEquals(0f, left.dx, 1e-3f)
        val middle = ShareCards.cover(4000f, 2000f, 1000f, 2000f, focusX = 0.5f, focusY = 0.5f)
        assertEquals(-1500f, middle.dx, 1e-3f)
        val right = ShareCards.cover(4000f, 2000f, 1000f, 2000f, focusX = 0.95f, focusY = 0.5f)
        assertEquals(-3000f, right.dx, 1e-3f)
        assertEquals(0f, right.dy, 1e-3f)
    }
}
