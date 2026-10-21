package dev.ryuya.galaxyservicepanel;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public final class CrashWindowTest {
  @Test
  public void threeConfirmedRecentExitsBlock() {
    var times = CrashWindow.record(List.of(1000L, 2000L), 3000, 4000, true);
    assertTrue(CrashWindow.blocked(times));
  }

  @Test
  public void repeatedHistoryIsNotAnotherCrash() {
    assertEquals(List.of(1000L), CrashWindow.record(List.of(1000L), 1000, 2000, true));
  }

  @Test
  public void manualAndUnknownExitDoNotCount() {
    assertEquals(List.of(1000L), CrashWindow.record(List.of(1000L), 2000, 3000, false));
  }

  @Test
  public void staleAndFutureTimesAreDiscarded() {
    assertTrue(CrashWindow.record(List.of(1000L, 999999L), 999999, 125000, true).isEmpty());
  }
}
