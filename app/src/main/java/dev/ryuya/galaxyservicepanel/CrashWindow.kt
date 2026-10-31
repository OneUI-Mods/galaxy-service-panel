package dev.ryuya.galaxyservicepanel

import java.util.ArrayList
import java.util.List
import kotlin.jvm.JvmStatic

/* Counts only confirmed fatal exits; callers separately match process, PID and armed time. */
object CrashWindow {
    const val WINDOW: Long = 120000

    @JvmStatic
    fun record(
        saved: MutableList<Long?>,
        exitTime: Long,
        now: Long,
        confirmed: Boolean,
    ): MutableList<Long?> {
        val result = ArrayList<Long?>()
        for (time in saved) if (
            time!! <= now && now - time <= CrashWindow.WINDOW && !result.contains(time)
        )
            result.add(time)
        if (
            confirmed &&
                exitTime > 0 &&
                exitTime <= now &&
                now - exitTime <= CrashWindow.WINDOW &&
                !result.contains(exitTime)
        )
            result.add(exitTime)
        return List.copyOf<Long?>(result)
    }

    @JvmStatic
    fun blocked(times: MutableList<Long?>): Boolean {
        return times.size >= 3
    }
}
