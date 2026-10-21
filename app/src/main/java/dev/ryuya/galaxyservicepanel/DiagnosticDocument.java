package dev.ryuya.galaxyservicepanel;

import java.util.*;
import java.util.regex.*;

/** Presentation metadata only. Every visible item retains its exact native row and index. */
final class DiagnosticDocument {
  enum Section {
    SELECTION,
    CONNECTION,
    LTE,
    NR,
    DETAILS
  }

  record Value(String label, String text) {}

  record Row(
      int sourceIndex,
      String raw,
      String text,
      String menuKey,
      Section section,
      List<Value> values) {}

  private static final Pattern MENU = Pattern.compile("^\\s*\\[([0-9]{1,2})]\\s*(.+)$");
  private static final Pattern METRIC =
      Pattern.compile(
          "(?i)(?<![A-Za-z0-9_])((?:LTE[ -])?RRC|(?:NR[ -])?RSRP|RSRQ|SINR|SNR|BAND|BW|Earfcn|PCI)\\s*:");

  static List<Row> parse(List<String> nativeRows) {
    List<Row> result = new ArrayList<>();
    Section section = Section.CONNECTION;
    boolean selection =
        nativeRows.stream()
            .filter(Objects::nonNull)
            .anyMatch(s -> s.strip().equals("BASIC INFORMATION"));
    for (int i = 0; i < nativeRows.size(); i++) {
      String raw = nativeRows.get(i);
      if (raw == null || raw.isBlank()) continue;
      String text = raw.strip();
      String upper = text.toUpperCase(Locale.ROOT);
      if (selection) section = Section.SELECTION;
      else if (upper.startsWith("LTE RRC") || upper.startsWith("LTE INFORMATION"))
        section = Section.LTE;
      else if (upper.startsWith("NR-") || upper.startsWith("NR_") || upper.startsWith("NR "))
        section = Section.NR;
      Matcher menu = MENU.matcher(text);
      String key = menu.matches() ? menu.group(1) : "";
      result.add(
          new Row(
              i,
              raw,
              key.isEmpty() ? text : menu.group(2),
              key,
              section,
              key.isEmpty() ? values(text) : List.of()));
    }
    return List.copyOf(result);
  }

  static List<Value> values(String raw) {
    Matcher matcher = METRIC.matcher(raw);
    List<int[]> matches = new ArrayList<>();
    while (matcher.find()) matches.add(new int[] {matcher.start(), matcher.end()});
    // Only split repeated, recognized metrics. Unknown syntax is kept as one complete value.
    if (matches.size() >= 2) {
      List<Value> result = new ArrayList<>();
      for (int i = 0; i < matches.size(); i++) {
        int start = i == 0 ? 0 : matches.get(i)[0];
        int end = matches.get(i)[1];
        int next = i + 1 < matches.size() ? matches.get(i + 1)[0] : raw.length();
        result.add(
            new Value(raw.substring(start, end - 1).strip(), raw.substring(end, next).strip()));
      }
      return List.copyOf(result);
    }
    int colon = raw.indexOf(':');
    if (colon > 0 && colon < raw.length() - 1)
      return List.of(new Value(raw.substring(0, colon).strip(), raw.substring(colon + 1).strip()));
    return List.of();
  }

  static String summary(List<Row> rows) {
    String slot = "", technology = "";
    for (Row row : rows) {
      String text = row.text();
      if (text.matches("(?i)Slot\\s+[0-9]+")) slot = text;
      if (text.startsWith("Serving PLMN") && text.contains("-"))
        technology = text.substring(text.lastIndexOf('-') + 1).strip();
    }
    return slot.isEmpty() ? technology : technology.isEmpty() ? slot : slot + " · " + technology;
  }

  private DiagnosticDocument() {}
}
