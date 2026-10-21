package dev.ryuya.galaxyservicepanel;

import android.app.*;
import android.content.*;
import android.os.*;
import android.os.Process;
import android.provider.Settings;
import java.util.*;

/**
 * Persists an armed interval only while our panel is attached; unknown/manual exits never count.
 */
final class PhoneGuard {
  private final SharedPreferences saved;
  private final Context context;
  private final long start =
      System.currentTimeMillis()
          - SystemClock.elapsedRealtime()
          + Process.getStartElapsedRealtime();
  private final int boot;

  PhoneGuard(Context context) {
    this.context = context;
    saved = context.getSharedPreferences("galaxy-service-panel-guard", Context.MODE_PRIVATE);
    boot = Settings.Global.getInt(context.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
  }

  boolean arm(long nonce) {
    long now = System.currentTimeMillis();
    if (saved.getLong("nonce", 0) != nonce) saved.edit().clear().putLong("nonce", nonce).commit();
    int previousPid = saved.getInt("pid", -1);
    List<Long> times = new ArrayList<>();
    for (String item : saved.getString("failures", "").split(","))
      try {
        times.add(Long.parseLong(item));
      } catch (NumberFormatException ignored) {
      }
    times = CrashWindow.record(times, 0, now, false);
    boolean sameProcess =
        previousPid == Process.myPid()
            && saved.getInt("boot", -2) == boot
            && Math.abs(saved.getLong("start", 0) - start) < 2000;
    if (!sameProcess && previousPid > 0 && saved.getBoolean("armed", false)) {
      try {
        var exits =
            context
                .getSystemService(ActivityManager.class)
                .getHistoricalProcessExitReasons(ConfigProvider.TARGET, previousPid, 4);
        for (ApplicationExitInfo exit : exits) {
          boolean fatal =
              switch (exit.getReason()) {
                case ApplicationExitInfo.REASON_CRASH,
                    ApplicationExitInfo.REASON_CRASH_NATIVE,
                    ApplicationExitInfo.REASON_ANR,
                    ApplicationExitInfo.REASON_INITIALIZATION_FAILURE ->
                    true;
                case ApplicationExitInfo.REASON_SIGNALED ->
                    exit.getStatus() == 6 || exit.getStatus() == 7 || exit.getStatus() == 11;
                default -> false;
              };
          if (exit.getPid() == previousPid
              && "com.android.phone".equals(exit.getProcessName())
              && exit.getTimestamp() >= saved.getLong("armed_since", Long.MAX_VALUE))
            times = CrashWindow.record(times, exit.getTimestamp(), now, fatal);
        }
      } catch (RuntimeException ignored) {
        // No evidence is not a crash. Never infer one from a new PID or a missing exit record.
      }
    }
    StringJoiner failures = new StringJoiner(",");
    times.forEach(t -> failures.add(Long.toString(t)));
    boolean blocked = saved.getBoolean("blocked", false) || CrashWindow.blocked(times);
    saved
        .edit()
        .putInt("pid", Process.myPid())
        .putInt("boot", boot)
        .putLong("start", start)
        .putLong("armed_since", now)
        .putBoolean("armed", !blocked)
        .putString("failures", failures.toString())
        .putBoolean("blocked", blocked)
        .commit();
    return !blocked;
  }

  void disarm() {
    saved.edit().putBoolean("armed", false).commit();
  }
}
