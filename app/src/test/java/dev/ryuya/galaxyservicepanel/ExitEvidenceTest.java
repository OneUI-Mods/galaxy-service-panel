package dev.ryuya.galaxyservicepanel;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ExitEvidenceTest {
  @Test
  public void requiresCorrectPidProcessAndArmedInterval() {
    assertTrue(
        new ExitEvidence(42, "com.android.phone", 2000, ExitEvidence.Reason.CRASH, 0)
            .countable(42, 1000, 3000));
    assertFalse(
        new ExitEvidence(43, "com.android.phone", 2000, ExitEvidence.Reason.CRASH, 0)
            .countable(42, 1000, 3000));
    assertFalse(
        new ExitEvidence(42, "another.phone", 2000, ExitEvidence.Reason.CRASH, 0)
            .countable(42, 1000, 3000));
    assertFalse(
        new ExitEvidence(42, "com.android.phone", 900, ExitEvidence.Reason.CRASH, 0)
            .countable(42, 1000, 3000));
    assertFalse(
        new ExitEvidence(42, "com.android.phone", 4000, ExitEvidence.Reason.CRASH, 0)
            .countable(42, 1000, 3000));
  }

  @Test
  public void normalUnknownTermAndKillNeverCount() {
    assertFalse(
        new ExitEvidence(42, "com.android.phone", 2000, ExitEvidence.Reason.OTHER, 0)
            .countable(42, 1000, 3000));
    for (int signal : new int[] {0, 9, 15})
      assertFalse(
          new ExitEvidence(42, "com.android.phone", 2000, ExitEvidence.Reason.SIGNAL, signal)
              .countable(42, 1000, 3000));
  }

  @Test
  public void fatalNativeSignalsCount() {
    for (int signal : new int[] {6, 7, 11})
      assertTrue(
          new ExitEvidence(42, "com.android.phone", 2000, ExitEvidence.Reason.SIGNAL, signal)
              .countable(42, 1000, 3000));
  }
}
