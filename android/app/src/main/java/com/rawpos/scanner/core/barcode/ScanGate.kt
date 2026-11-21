package com.rawpos.scanner.core.barcode

/** What the camera analyzer should do with the values it decoded in one frame. */
sealed interface ScanDecision {
    /** Nothing to do. */
    data object None : ScanDecision

    /** Two or more distinct symbols in the target: submit neither and ask the cashier to frame one. */
    data object MultipleCodes : ScanDecision

    /** One stable, re-armed value may become a scan event. */
    data class Emit(val value: String) : ScanDecision
}

/**
 * Layer 1 of the duplicate-suppression design: the candidate stabilizer
 * (`docs/architecture/barcode-scanning.md` §6.1).
 *
 * It turns a stream of decoded frames into at most one [ScanDecision.Emit] per physical presentation:
 *
 * - **stability** — the same value must appear in [stabilityFrames] consecutive frames;
 * - **cooldown** — no emit within [minIntervalMillis] of the previous emit;
 * - **latch** — once emitted, the same value cannot emit again until it has been absent for
 *   [rearmAbsenceMillis], which is what makes a deliberate double scan (remove, present again) work;
 * - **one at a time** — two distinct values in one frame emit neither.
 *
 * Time is a parameter, never a hidden clock, so the state machine is deterministic under test.
 *
 * Phase 3 emits only the value. Event identity, retries, and acknowledgment are Phase 4; the
 * first-submission staleness rule (10 s) belongs there too, because it needs the capture timestamp.
 */
class ScanGate(
    private val stabilityFrames: Int = 2,
    private val minIntervalMillis: Long = 150,
    private val rearmAbsenceMillis: Long = 500,
) {
    init {
        require(stabilityFrames >= 1) { "stabilityFrames must be at least 1" }
        require(minIntervalMillis >= 0) { "minIntervalMillis must not be negative" }
        require(rearmAbsenceMillis >= 0) { "rearmAbsenceMillis must not be negative" }
    }

    private var paused = false
    private var candidate: String? = null
    private var streak = 0
    private var latchedValue: String? = null
    private var lastEmitAtMillis: Long? = null
    private var absentSinceMillis: Long? = null

    /** True while the gate is paused and ignores frames. */
    val isPaused: Boolean get() = paused

    /** True while a value is latched and must leave the frame before it can be emitted again. */
    val hasLatchedValue: Boolean get() = latchedValue != null

    fun pause() {
        paused = true
        resetStreak()
    }

    /** Resume after a pause. Treated as a manual re-arm: any latch is cleared. */
    fun resume() {
        paused = false
        manualRearm()
    }

    /** Clear the latch so the next presentation counts, even without waiting for absence. */
    fun manualRearm() {
        latchedValue = null
        resetStreak()
        absentSinceMillis = null
    }

    /**
     * Feed the distinct values decoded in one analyzed frame.
     *
     * @param distinctValues decoded payloads in the target region, already de-duplicated by the caller.
     * @param nowMillis monotonic frame time in milliseconds.
     */
    fun onFrame(distinctValues: Set<String>, nowMillis: Long): ScanDecision {
        if (paused) {
            return ScanDecision.None
        }
        if (distinctValues.size > 1) {
            resetStreak()
            absentSinceMillis = null
            return ScanDecision.MultipleCodes
        }
        if (distinctValues.isEmpty()) {
            markAbsence(nowMillis)
            return ScanDecision.None
        }

        absentSinceMillis = null
        val value = distinctValues.first()

        if (value == latchedValue) {
            // Already submitted; waiting for the item to leave the frame.
            resetStreak()
            return ScanDecision.None
        }

        if (value == candidate) {
            streak += 1
        } else {
            candidate = value
            streak = 1
        }
        if (streak < stabilityFrames) {
            return ScanDecision.None
        }

        val previousEmit = lastEmitAtMillis
        if (previousEmit != null && nowMillis - previousEmit < minIntervalMillis) {
            return ScanDecision.None
        }

        latchedValue = value
        lastEmitAtMillis = nowMillis
        resetStreak()
        return ScanDecision.Emit(value)
    }

    private fun markAbsence(nowMillis: Long) {
        resetStreak()
        val since = absentSinceMillis
        if (since == null) {
            absentSinceMillis = nowMillis
            return
        }
        if (nowMillis - since >= rearmAbsenceMillis) {
            latchedValue = null
        }
    }

    private fun resetStreak() {
        candidate = null
        streak = 0
    }
}
