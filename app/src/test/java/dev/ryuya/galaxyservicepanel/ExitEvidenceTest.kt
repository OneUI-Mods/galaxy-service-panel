package dev.ryuya.galaxyservicepanel

import org.junit.Assert.*
import org.junit.Test

class ExitEvidenceTest {
    @Test
    fun requiresCorrectPidProcessAndArmedInterval() {
        assertTrue(
            ExitEvidence(42, "com.android.phone", 2000, ExitEvidence.Reason.CRASH, 0)
                .countable(42, 1000, 3000)
        )
        assertFalse(
            ExitEvidence(43, "com.android.phone", 2000, ExitEvidence.Reason.CRASH, 0)
                .countable(42, 1000, 3000)
        )
        assertFalse(
            ExitEvidence(42, "another.phone", 2000, ExitEvidence.Reason.CRASH, 0)
                .countable(42, 1000, 3000)
        )
        assertFalse(
            ExitEvidence(42, "com.android.phone", 900, ExitEvidence.Reason.CRASH, 0)
                .countable(42, 1000, 3000)
        )
        assertFalse(
            ExitEvidence(42, "com.android.phone", 4000, ExitEvidence.Reason.CRASH, 0)
                .countable(42, 1000, 3000)
        )
    }

    @Test
    fun normalUnknownTermAndKillNeverCount() {
        assertFalse(
            ExitEvidence(42, "com.android.phone", 2000, ExitEvidence.Reason.OTHER, 0)
                .countable(42, 1000, 3000)
        )
        for (signal in intArrayOf(0, 9, 15)) assertFalse(
            ExitEvidence(42, "com.android.phone", 2000, ExitEvidence.Reason.SIGNAL, signal)
                .countable(42, 1000, 3000)
        )
    }

    @Test
    fun fatalNativeSignalsCount() {
        for (signal in intArrayOf(6, 7, 11)) assertTrue(
            ExitEvidence(42, "com.android.phone", 2000, ExitEvidence.Reason.SIGNAL, signal)
                .countable(42, 1000, 3000)
        )
    }
}
