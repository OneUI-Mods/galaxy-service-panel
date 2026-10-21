package dev.ryuya.galaxyservicepanel;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public final class DiagnosticDocumentTest {
  @Test
  public void blankRowsDoNotChangeNativeMenuIndices() {
    var rows =
        DiagnosticDocument.parse(
            Arrays.asList(
                "BASIC INFORMATION ", "", null, " [1] SIM1 INFORMATION", " [2] SIM2 INFORMATION"));
    assertEquals(3, rows.size());
    assertEquals(3, rows.get(1).sourceIndex());
    assertEquals("1", rows.get(1).menuKey());
    assertEquals(" [1] SIM1 INFORMATION", rows.get(1).raw());
    assertEquals(4, rows.get(2).sourceIndex());
  }

  @Test
  public void bracketedNonMenuDataDoesNotBecomeACommand() {
    var rows =
        DiagnosticDocument.parse(
            List.of("Automatic[enabled]", "[  ] Value", "[*] Ignore", "RSRP:[-96]"));
    for (var row : rows) assertTrue(row.menuKey().isEmpty());
  }

  @Test
  public void completeTextAndOrderSurviveGrouping() {
    var source =
        List.of(
            "Slot 1",
            "LTE RRC:IDLE BAND:3 BW:20",
            "RSRP:-96 RSRQ:-9 SNR:14.2",
            "NR-RSRP:-- SINR:-- RSRQ:--",
            "unexpected new value: A:B:C");
    var rows = DiagnosticDocument.parse(source);
    for (int i = 0; i < source.size(); i++) {
      assertEquals(i, rows.get(i).sourceIndex());
      assertEquals(source.get(i), rows.get(i).raw());
    }
    assertEquals(DiagnosticDocument.Section.LTE, rows.get(1).section());
    assertEquals(DiagnosticDocument.Section.NR, rows.get(3).section());
    assertEquals("A:B:C", rows.get(4).values().get(0).text());
  }

  @Test
  public void multipleMetricsKeepLabelsAndMissingValues() {
    var values = DiagnosticDocument.values("LTE RRC:IDLE BAND:3 BW:20");
    assertEquals(
        List.of(
            new DiagnosticDocument.Value("LTE RRC", "IDLE"),
            new DiagnosticDocument.Value("BAND", "3"),
            new DiagnosticDocument.Value("BW", "20")),
        values);
    var missing = DiagnosticDocument.values("NR-RSRP:-- SINR:-- RSRQ:--");
    assertEquals(3, missing.size());
    assertEquals("--", missing.get(0).text());
    assertEquals(
        "PRX0 RSRP", DiagnosticDocument.values("PRX0 RSRP:-96.4, RSRQ:-10").get(0).label());
  }

  @Test
  public void unknownAndLongRowsRemainIntact() {
    String value = "Unknown field " + "測定".repeat(500);
    var row = DiagnosticDocument.parse(List.of(value)).get(0);
    assertEquals(value, row.raw());
    assertEquals(value, row.text());
    assertTrue(row.values().isEmpty());
  }

  @Test
  public void summaryDoesNotConfuseBandWithRadioTechnology() {
    assertEquals(
        "Slot 2 · LTE",
        DiagnosticDocument.summary(
            DiagnosticDocument.parse(
                List.of("Slot 2", "Serving PLMN(001-01)-LTE", "LTE RRC:IDLE BAND:3 BW:20"))));
  }
}
