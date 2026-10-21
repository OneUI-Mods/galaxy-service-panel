package dev.ryuya.galaxyservicepanel;

import java.util.*;

/** Counts only confirmed fatal exits; callers separately match process, PID and armed time. */
final class CrashWindow {
  static final long WINDOW = 120_000;

  static List<Long> record(List<Long> saved, long exitTime, long now, boolean confirmed) {
    List<Long> result = new ArrayList<>();
    for (long time : saved)
      if (time <= now && now - time <= WINDOW && !result.contains(time)) result.add(time);
    if (confirmed
        && exitTime > 0
        && exitTime <= now
        && now - exitTime <= WINDOW
        && !result.contains(exitTime)) result.add(exitTime);
    return List.copyOf(result);
  }

  static boolean blocked(List<Long> times) {
    return times.size() >= 3;
  }

  private CrashWindow() {}
}
