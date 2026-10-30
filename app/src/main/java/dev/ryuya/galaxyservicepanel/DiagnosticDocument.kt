package dev.ryuya.galaxyservicepanel

import java.util.ArrayList
import java.util.List
import java.util.Objects
import java.util.regex.Pattern
import kotlin.jvm.JvmRecord
import kotlin.jvm.JvmStatic

/* Presentation metadata only. Every visible item retains its exact native row and index. */
object DiagnosticDocument {
    private val MENU: Pattern? = Pattern.compile("^\\s*\\[([0-9]{1,2})]\\s*(.+)$")
    private val METRIC: Pattern? =
        Pattern.compile(
            "(?i)(?<![A-Za-z0-9_])((?:LTE[ -])?RRC|(?:NR[ -])?RSRP|RSRQ|SINR|SNR|BAND|BW|Earfcn|PCI)\\s*:"
        )

    @JvmStatic
    fun parse(nativeRows: MutableList<String?>): MutableList<DiagnosticDocument.Row?>? {
        val result = ArrayList<DiagnosticDocument.Row?>()
        var section = DiagnosticDocument.Section.CONNECTION
        val selection =
            nativeRows
                .stream()
                .filter { obj: Any? -> Objects.nonNull(obj) }
                .anyMatch { s: String? -> s!!.trim() == "BASIC INFORMATION" }
        for (i in nativeRows.indices) {
            val raw = nativeRows.get(i)
            if (raw == null || raw.isBlank()) continue
            val text = raw.trim()
            val upper = text.uppercase()
            if (selection) section = DiagnosticDocument.Section.SELECTION
            else if (upper.startsWith("LTE RRC") || upper.startsWith("LTE INFORMATION"))
                section = DiagnosticDocument.Section.LTE
            else if (upper.startsWith("NR-") || upper.startsWith("NR_") || upper.startsWith("NR "))
                section = DiagnosticDocument.Section.NR
            val menu = DiagnosticDocument.MENU!!.matcher(text)
            val key: String? = if (menu.matches()) menu.group(1) else ""
            result.add(
                DiagnosticDocument.Row(
                    i,
                    raw,
                    if (key!!.isEmpty()) text else menu.group(2),
                    key,
                    section,
                    if (key!!.isEmpty()) DiagnosticDocument.values(text)
                    else java.util.List.of<DiagnosticDocument.Value?>(),
                )
            )
        }
        return List.copyOf<DiagnosticDocument.Row?>(result)
    }

    @JvmStatic
    fun values(raw: String): MutableList<DiagnosticDocument.Value?>? {
        val matcher = DiagnosticDocument.METRIC!!.matcher(raw)
        val matches = ArrayList<IntArray?>()
        while (matcher.find()) matches.add(intArrayOf(matcher.start(), matcher.end()))
        // Only split repeated, recognized metrics. Unknown syntax is kept as one complete value.
        if (matches.size >= 2) {
            val result = ArrayList<DiagnosticDocument.Value?>()
            for (i in matches.indices) {
                val start: Int = if (i == 0) 0 else matches!!.get(i)!![0]
                val end: Int = matches!!.get(i)!![1]
                val next: Int = if (i + 1 < matches.size) matches!!.get(i + 1)!![0] else raw.length
                result.add(
                    DiagnosticDocument.Value(
                        raw.substring(start, end - 1).trim(),
                        raw.substring(end, next).trim(),
                    )
                )
            }
            return List.copyOf<DiagnosticDocument.Value?>(result)
        }
        val colon = raw.indexOf(':')
        if (colon > 0 && colon < raw.length - 1)
            return List.of<DiagnosticDocument.Value?>(
                DiagnosticDocument.Value(
                    raw.substring(0, colon).trim(),
                    raw.substring(colon + 1).trim(),
                )
            )
        return java.util.List.of<DiagnosticDocument.Value?>()
    }

    @JvmStatic
    fun summary(rows: MutableList<DiagnosticDocument.Row?>): String? {
        var slot: String = ""
        var technology: String = ""
        for (row in rows) {
            val text = row!!.text
            if (text!!.matches("(?i)Slot\\s+[0-9]+".toRegex())) slot = text
            if (text!!.startsWith("Serving PLMN") && text!!.contains("-"))
                technology = text!!.substring(text!!.lastIndexOf('-') + 1).trim()
        }
        return if (slot.isEmpty()) technology
        else if (technology.isEmpty()) slot else slot + " · " + technology
    }

    enum class Section {
        SELECTION,
        CONNECTION,
        LTE,
        NR,
        DETAILS,
    }

    @JvmRecord
    data class Value(val label: String?, val text: String?) {
        override fun toString(): String = "Value[label=$label, text=$text]"
    }

    @JvmRecord
    data class Row(
        val sourceIndex: Int,
        val raw: String?,
        val text: String?,
        val menuKey: String?,
        val section: DiagnosticDocument.Section?,
        val values: MutableList<DiagnosticDocument.Value?>?,
    ) {
        override fun toString(): String =
            "Row[sourceIndex=$sourceIndex, raw=$raw, text=$text, menuKey=$menuKey, section=$section, values=$values]"
    }
}
