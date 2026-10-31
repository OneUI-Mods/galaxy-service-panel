package dev.ryuya.galaxyservicepanel

import kotlin.jvm.JvmRecord

/* Independent of Android constants so the full attribution boundary can be tested on the JVM. */
@JvmRecord
data class ExitEvidence(
    val pid: Int,
    val process: String?,
    val timestamp: Long,
    val reason: ExitEvidence.Reason,
    val signal: Int,
) {
    enum class Reason {
        CRASH,
        NATIVE_CRASH,
        ANR,
        INITIALIZATION,
        SIGNAL,
        OTHER,
    }

    fun countable(expectedPid: Int, armedSince: Long, now: Long): Boolean {
        if (
            pid != expectedPid ||
                ("com.android.phone" != process) ||
                timestamp < armedSince ||
                timestamp > now
        )
            return false
        return when (reason) {
            Reason.CRASH,
            Reason.NATIVE_CRASH,
            Reason.ANR,
            Reason.INITIALIZATION -> true
            Reason.SIGNAL -> signal == 6 || signal == 7 || signal == 11
            Reason.OTHER -> false
        }
    }

    override fun toString(): String =
        "ExitEvidence[pid=$pid, process=$process, timestamp=$timestamp, reason=$reason, signal=$signal]"
}
