package dev.ryuya.galaxyservicepanel

import org.junit.Assert.*
import org.junit.Test

class CrashWindowTest {
    @Test
    fun threeConfirmedRecentExitsBlock() {
        val times = CrashWindow.record(java.util.List.of<Long?>(1000L, 2000L), 3000, 4000, true)
        assertTrue(CrashWindow.blocked(times))
    }

    @Test
    fun repeatedHistoryIsNotAnotherCrash() {
        assertEquals(
            java.util.List.of(1000L),
            CrashWindow.record(java.util.List.of<Long?>(1000L), 1000, 2000, true),
        )
    }

    @Test
    fun manualAndUnknownExitDoNotCount() {
        assertEquals(
            java.util.List.of(1000L),
            CrashWindow.record(java.util.List.of<Long?>(1000L), 2000, 3000, false),
        )
    }

    @Test
    fun staleAndFutureTimesAreDiscarded() {
        assertTrue(
            CrashWindow.record(java.util.List.of<Long?>(1000L, 999999L), 999999, 125000, true)
                .isEmpty()
        )
    }
}
