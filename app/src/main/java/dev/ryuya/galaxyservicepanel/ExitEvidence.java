package dev.ryuya.galaxyservicepanel;

/** Independent of Android constants so the full attribution boundary can be tested on the JVM. */
record ExitEvidence(int pid, String process, long timestamp, Reason reason, int signal) {
  enum Reason {
    CRASH,
    NATIVE_CRASH,
    ANR,
    INITIALIZATION,
    SIGNAL,
    OTHER
  }

  boolean countable(int expectedPid, long armedSince, long now) {
    if (pid != expectedPid
        || !"com.android.phone".equals(process)
        || timestamp < armedSince
        || timestamp > now) return false;
    return switch (reason) {
      case CRASH, NATIVE_CRASH, ANR, INITIALIZATION -> true;
      case SIGNAL -> signal == 6 || signal == 7 || signal == 11;
      case OTHER -> false;
    };
  }
}
