package dev.ryuya.galaxyservicepanel

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.SharedPreferences
import android.os.Process
import android.os.SystemClock
import android.provider.Settings.Global
import java.util.ArrayList
import java.util.StringJoiner
import java.util.function.Consumer
import kotlin.math.abs

/*
 * Persists an armed interval only while our panel is attached; unknown/manual exits never count.
 */
class PhoneGuard(context: Context) {
    private val saved: SharedPreferences
    private val context: Context
    private val start: Long =
        (System.currentTimeMillis() - SystemClock.elapsedRealtime() +
            Process.getStartElapsedRealtime())
    private val boot: Int

    init {
        this.context = context
        saved = context.getSharedPreferences("galaxy-service-panel-guard", Context.MODE_PRIVATE)
        boot = Global.getInt(context.getContentResolver(), Global.BOOT_COUNT, -1)
    }

    fun arm(nonce: Long): Boolean {
        val now = System.currentTimeMillis()
        if (saved.getLong("nonce", 0) != nonce)
            saved.edit().clear().putLong("nonce", nonce).commit()
        val previousPid = saved.getInt("pid", -1)
        var times: MutableList<Long?> = ArrayList()
        for (item in
            saved
                .getString("failures", "")!!
                .split(",".toRegex())
                .dropLastWhile { it.isEmpty() }
                .toTypedArray()) try {
            times.add(item.toLong())
        } catch (ignored: NumberFormatException) {}
        times = CrashWindow.record(times, 0, now, false)
        val sameProcess: Boolean =
            previousPid == Process.myPid() &&
                saved.getInt("boot", -2) == boot &&
                abs(saved.getLong("start", 0) - start) < 2000
        if (!sameProcess && previousPid > 0 && saved.getBoolean("armed", false)) {
            try {
                val exits =
                    context
                        .getSystemService<ActivityManager?>(ActivityManager::class.java)
                        .getHistoricalProcessExitReasons(ConfigProvider.TARGET, previousPid, 4)
                for (exit in exits) {
                    val reason: ExitEvidence.Reason =
                        when (exit.getReason()) {
                            ApplicationExitInfo.REASON_CRASH -> ExitEvidence.Reason.CRASH
                            ApplicationExitInfo.REASON_CRASH_NATIVE ->
                                ExitEvidence.Reason.NATIVE_CRASH
                            ApplicationExitInfo.REASON_ANR -> ExitEvidence.Reason.ANR
                            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE ->
                                ExitEvidence.Reason.INITIALIZATION
                            ApplicationExitInfo.REASON_SIGNALED -> ExitEvidence.Reason.SIGNAL
                            else -> ExitEvidence.Reason.OTHER
                        }
                    val evidence =
                        ExitEvidence(
                            exit.getPid(),
                            exit.getProcessName(),
                            exit.getTimestamp(),
                            reason,
                            exit.getStatus(),
                        )
                    times =
                        CrashWindow.record(
                            times,
                            exit.getTimestamp(),
                            now,
                            evidence.countable(
                                previousPid,
                                saved.getLong("armed_since", Long.MAX_VALUE),
                                now,
                            ),
                        )
                }
            } catch (ignored: RuntimeException) {
                // No evidence is not a crash. Never infer one from a new PID or a missing exit
                // record.
            }
        }
        val failures = StringJoiner(",")
        times.forEach(Consumer { t: Long? -> failures.add(t.toString()) })
        val blocked: Boolean = saved.getBoolean("blocked", false) || CrashWindow.blocked(times)
        saved
            .edit()
            .putInt("pid", Process.myPid())
            .putInt("boot", boot)
            .putLong("start", start)
            .putLong("armed_since", now)
            .putBoolean("armed", !blocked)
            .putString("failures", failures.toString())
            .putBoolean("blocked", blocked)
            .commit()
        return !blocked
    }

    fun disarm() {
        saved.edit().putBoolean("armed", false).commit()
    }
}
