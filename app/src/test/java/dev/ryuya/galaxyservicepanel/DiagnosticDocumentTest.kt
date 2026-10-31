package dev.ryuya.galaxyservicepanel

import java.util.List
import org.junit.Assert.*
import org.junit.Test

class DiagnosticDocumentTest {
    @Test
    fun blankRowsDoNotChangeNativeMenuIndices() {
        val rows =
            DiagnosticDocument.parse(
                mutableListOf<String?>(
                    "BASIC INFORMATION ",
                    "",
                    null,
                    " [1] SIM1 INFORMATION",
                    " [2] SIM2 INFORMATION",
                )
            )
        assertEquals(3, rows!!.size)
        assertEquals(3, rows!!.get(1)!!.sourceIndex)
        assertEquals("1", rows!!.get(1)!!.menuKey)
        assertEquals(" [1] SIM1 INFORMATION", rows!!.get(1)!!.raw)
        assertEquals(4, rows!!.get(2)!!.sourceIndex)
    }

    @Test
    fun bracketedNonMenuDataDoesNotBecomeACommand() {
        val rows =
            DiagnosticDocument.parse(
                mutableListOf<String?>(
                    "Automatic[enabled]",
                    "[  ] Value",
                    "[*] Ignore",
                    "RSRP:[-96]",
                )
            )
        for (row in rows!!) assertTrue(row!!.menuKey!!.isEmpty())
    }

    @Test
    fun completeTextAndOrderSurviveGrouping() {
        val source =
            mutableListOf<String?>(
                "Slot 1",
                "LTE RRC:IDLE BAND:3 BW:20",
                "RSRP:-96 RSRQ:-9 SNR:14.2",
                "NR-RSRP:-- SINR:-- RSRQ:--",
                "unexpected new value: A:B:C",
            )
        val rows = DiagnosticDocument.parse(source)
        for (i in source.indices) {
            assertEquals(i, rows!!.get(i)!!.sourceIndex)
            assertEquals(source.get(i), rows!!.get(i)!!.raw)
        }
        assertEquals(DiagnosticDocument.Section.LTE, rows!!.get(1)!!.section)
        assertEquals(DiagnosticDocument.Section.NR, rows!!.get(3)!!.section)
        assertEquals("A:B:C", rows!!.get(4)!!.values!!.get(0)!!.text)
    }

    @Test
    fun multipleMetricsKeepLabelsAndMissingValues() {
        val values = DiagnosticDocument.values("LTE RRC:IDLE BAND:3 BW:20")
        assertEquals(
            List.of<DiagnosticDocument.Value?>(
                DiagnosticDocument.Value("LTE RRC", "IDLE"),
                DiagnosticDocument.Value("BAND", "3"),
                DiagnosticDocument.Value("BW", "20"),
            ),
            values,
        )
        val missing = DiagnosticDocument.values("NR-RSRP:-- SINR:-- RSRQ:--")
        assertEquals(3, missing!!.size)
        assertEquals("--", missing!!.get(0)!!.text)
        assertEquals(
            "PRX0 RSRP",
            DiagnosticDocument.values("PRX0 RSRP:-96.4, RSRQ:-10")!!.get(0)!!.label,
        )
    }

    @Test
    fun unknownAndLongRowsRemainIntact() {
        val value: String = "Unknown field " + "測定".repeat(500)
        val row = DiagnosticDocument.parse(List.of<String?>(value))!!.get(0)
        assertEquals(value, row!!.raw)
        assertEquals(value, row!!.text)
        assertTrue(row!!.values!!.isEmpty())
    }

    @Test
    fun summaryDoesNotConfuseBandWithRadioTechnology() {
        assertEquals(
            "Slot 2 · LTE",
            DiagnosticDocument.summary(
                DiagnosticDocument.parse(
                    mutableListOf<String?>(
                        "Slot 2",
                        "Serving PLMN(001-01)-LTE",
                        "LTE RRC:IDLE BAND:3 BW:20",
                    )
                )!!
            ),
        )
    }
}
