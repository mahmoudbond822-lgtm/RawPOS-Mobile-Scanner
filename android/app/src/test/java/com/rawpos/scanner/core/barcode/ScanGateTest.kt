package com.rawpos.scanner.core.barcode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanGateTest {

    private fun gate(
        stabilityFrames: Int = 2,
        minIntervalMillis: Long = 150,
        rearmAbsenceMillis: Long = 500,
    ) = ScanGate(
        stabilityFrames = stabilityFrames,
        minIntervalMillis = minIntervalMillis,
        rearmAbsenceMillis = rearmAbsenceMillis,
    )

    private fun emits(decisions: List<ScanDecision>): List<String> =
        decisions.filterIsInstance<ScanDecision.Emit>().map { it.value }

    @Test
    fun `a value held for sixty frames emits exactly once`() {
        val gate = gate()
        val decisions = (0 until 60).map { frame -> gate.onFrame(setOf("6291041500213"), frame * 100L) }

        assertEquals(listOf("6291041500213"), emits(decisions))
    }

    @Test
    fun `one frame is not enough`() {
        val gate = gate()

        assertEquals(ScanDecision.None, gate.onFrame(setOf("123"), 0L))
    }

    @Test
    fun `two matching frames emit`() {
        val gate = gate()

        gate.onFrame(setOf("123"), 0L)
        assertEquals(ScanDecision.Emit("123"), gate.onFrame(setOf("123"), 100L))
    }

    @Test
    fun `a different value resets the streak`() {
        val gate = gate()

        gate.onFrame(setOf("111"), 0L)
        gate.onFrame(setOf("222"), 100L)
        assertEquals(ScanDecision.Emit("222"), gate.onFrame(setOf("222"), 200L))
    }

    @Test
    fun `two distinct codes in one frame emit neither`() {
        val gate = gate()

        assertEquals(ScanDecision.MultipleCodes, gate.onFrame(setOf("111", "222"), 0L))
        assertEquals(ScanDecision.MultipleCodes, gate.onFrame(setOf("111", "222"), 100L))

        // The ambiguity resets the stability streak, so framing one code starts over.
        assertEquals(ScanDecision.None, gate.onFrame(setOf("111"), 200L))
        assertEquals(ScanDecision.Emit("111"), gate.onFrame(setOf("111"), 300L))
    }

    @Test
    fun `a held value does not re-emit until it leaves the frame`() {
        val gate = gate()
        gate.onFrame(setOf("123"), 0L)
        gate.onFrame(setOf("123"), 100L)

        val held = (3 until 40).map { frame -> gate.onFrame(setOf("123"), frame * 100L) }

        assertTrue(emits(held).isEmpty())
    }

    @Test
    fun `absence shorter than the re-arm interval does not re-arm`() {
        val gate = gate()
        gate.onFrame(setOf("123"), 0L)
        gate.onFrame(setOf("123"), 100L)

        gate.onFrame(emptySet(), 200L)
        gate.onFrame(emptySet(), 500L) // 300 ms absent, below 500
        gate.onFrame(setOf("123"), 600L)
        val afterReturn = gate.onFrame(setOf("123"), 700L)

        assertEquals(ScanDecision.None, afterReturn)
    }

    @Test
    fun `a re-presented value after the re-arm interval emits again`() {
        val gate = gate()
        gate.onFrame(setOf("123"), 0L)
        gate.onFrame(setOf("123"), 100L)

        gate.onFrame(emptySet(), 200L)
        gate.onFrame(emptySet(), 700L) // 500 ms absent -> re-armed
        gate.onFrame(setOf("123"), 800L)
        val second = gate.onFrame(setOf("123"), 900L)

        assertEquals(ScanDecision.Emit("123"), second)
    }

    @Test
    fun `the global minimum interval is enforced across different values`() {
        val gate = gate(minIntervalMillis = 150)
        gate.onFrame(setOf("111"), 0L)
        gate.onFrame(setOf("111"), 100L) // emitted at 100

        gate.onFrame(setOf("222"), 150L)
        assertEquals(ScanDecision.None, gate.onFrame(setOf("222"), 200L)) // only 100 ms since emit
        assertEquals(ScanDecision.Emit("222"), gate.onFrame(setOf("222"), 300L))
    }

    @Test
    fun `pause ignores frames and resume re-arms`() {
        val gate = gate()
        gate.pause()
        assertEquals(ScanDecision.None, gate.onFrame(setOf("123"), 0L))
        assertEquals(ScanDecision.None, gate.onFrame(setOf("123"), 100L))

        gate.resume()
        gate.onFrame(setOf("123"), 200L)

        assertEquals(ScanDecision.Emit("123"), gate.onFrame(setOf("123"), 300L))
    }

    @Test
    fun `manual re-arm allows the same value without waiting`() {
        val gate = gate()
        gate.onFrame(setOf("123"), 0L)
        gate.onFrame(setOf("123"), 100L)
        assertTrue(gate.hasLatchedValue)

        gate.manualRearm()
        gate.onFrame(setOf("123"), 200L)

        assertFalse(gate.hasLatchedValue)
        assertEquals(ScanDecision.Emit("123"), gate.onFrame(setOf("123"), 300L))
    }

    @Test
    fun `a single stability frame emits on first sight`() {
        val gate = gate(stabilityFrames = 1)

        assertEquals(ScanDecision.Emit("123"), gate.onFrame(setOf("123"), 0L))
    }

    @Test
    fun `zero stability frames is rejected`() {
        var failed = false
        try {
            ScanGate(stabilityFrames = 0)
        } catch (expected: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }
}
