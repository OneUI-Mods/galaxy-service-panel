package dev.ryuya.galaxyservicepanel

import org.junit.Assert.*
import org.junit.Test

class NativeWhitespaceTest {
    @Test
    fun nonBreakingRowsRetainTheirNativeIndicesAndText() {
        val source = mutableListOf<String?>("\u00a0", "\u2007", "\u202f", "\u00a0LTE RRC\u00a0")
        val rows = DiagnosticDocument.parse(source)!!
        assertEquals(4, rows.size)
        for (index in source.indices) {
            assertEquals(index, rows[index]!!.sourceIndex)
            assertEquals(source[index], rows[index]!!.raw)
            assertEquals(source[index], rows[index]!!.text)
        }
    }

    @Test
    fun metricValuesKeepNonBreakingSpacesButStripJavaWhitespace() {
        val values = DiagnosticDocument.values(" RSRP: \u00a0-95\u00a0 ")!!
        assertEquals("RSRP", values.single()!!.label)
        assertEquals("\u00a0-95\u00a0", values.single()!!.text)
    }
}
