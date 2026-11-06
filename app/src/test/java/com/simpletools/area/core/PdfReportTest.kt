package com.simpletools.area.core

import com.simpletools.area.android.TestData
import com.simpletools.area.core.database.MeasureType
import com.simpletools.area.core.geometry.GeoPoint
import com.simpletools.area.feature.export.MiniPdf
import com.simpletools.area.feature.export.PdfLabels
import com.simpletools.area.feature.export.PdfReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Locale

class PdfReportTest {
    private val labels = PdfLabels("SimpleTools Area", "Date", "Type", "Mesure sur carte", "Surface", "Périmètre", "Longueur", "Notes", "Coordonnées (WGS84)", "points", "Mesure indicative. Ne remplace pas un relevé effectué par un géomètre-expert.", "Croquis schématique", "Page")

    private fun gen(p: com.simpletools.area.core.database.ParcelEntity, l: Locale = Locale.FRANCE): ByteArray =
        ByteArrayOutputStream().also { PdfReport.write(p, labels, l, it) }.toByteArray()

    private fun text(b: ByteArray) = String(b, Charsets.ISO_8859_1)

    /** Vérifie la structure : en-tête, %%EOF, table xref pointant exactement sur chaque objet. */
    private fun assertWellFormed(b: ByteArray) {
        val s = text(b)
        assertTrue(s.startsWith("%PDF-1.4"))
        assertTrue(s.trimEnd().endsWith("%%EOF"))
        val startxref = Regex("startxref\\n(\\d+)").find(s)!!.groupValues[1].toInt()
        assertTrue(s.substring(startxref).startsWith("xref"))
        val lines = s.substring(startxref).lines()
        val count = lines[1].split(" ")[1].toInt()
        for (i in 1 until count) {
            val off = lines[2 + i].substring(0, 10).toInt()
            assertTrue("objet $i à $off", s.substring(off).startsWith("$i 0 obj"))
        }
    }

    private fun pages(b: ByteArray) = Regex("/Type /Page /Parent").findAll(text(b)).count()

    @Test fun pdfWithoutMapCapture_isWellFormed_andContainsRequiredData() {
        val b = gen(TestData.parcel(notes = "Bord de rivière"))
        assertWellFormed(b)
        assertEquals(1, pages(b))
        val s = text(b)
        assertTrue(s.contains("(Champ test)"))                       // nom
        assertTrue(s.contains("Surface"))                            // surface
        assertTrue(s.contains("10 000,09 m\\262") || s.contains("m\\262")) // m² (WinAnsi 0xB2)
        assertTrue(s.contains("ha"))                                 // hectares
        assertTrue(s.contains("P\\351rim\\350tre"))                    // « Périmètre » encodé WinAnsi
        assertTrue(s.contains("48.8566000"))                         // coordonnées
        assertTrue(s.contains("2.3522000"))
        assertTrue(s.contains("Mesure indicative"))                  // avertissement
        assertTrue(s.contains("/GS1 gs"))                            // croquis : remplissage transparent
    }

    @Test fun writesSampleReportForManualInspection() {
        val l = listOf(GeoPoint(45.75, 4.85), GeoPoint(45.75, 4.8511), GeoPoint(45.7506, 4.8511), GeoPoint(45.7506, 4.8505), GeoPoint(45.7511, 4.8505), GeoPoint(45.7511, 4.85))
        val p = TestData.parcel("Parcelle en L (Lyon)", l, "Exemple de rapport").copy(areaM2 = 7870.03, perimeterM = 415.7, createdAt = 1_762_430_000_000)
        java.io.File("build/sample-report.pdf").also { it.parentFile?.mkdirs() }.writeBytes(gen(p))
    }

    @Test fun manyPoints_paginate() {
        val pts = (0 until 200).map { i -> GeoPoint(45.0 + 0.0003 * Math.sin(i / 200.0 * 2 * Math.PI), 5.0 + 0.0003 * Math.cos(i / 200.0 * 2 * Math.PI)) }
        val b = gen(TestData.parcel("Grande parcelle", pts), Locale.ENGLISH)
        assertWellFormed(b)
        assertTrue("pages=${pages(b)}", pages(b) >= 3)
        assertTrue(text(b).contains("200 points"))
        assertTrue(text(b).contains("Page 3"))
    }

    @Test fun distanceMeasure_andDegenerateGeometry_doNotCrash() {
        val d = TestData.parcel("Distance", TestData.square.take(2)).copy(type = MeasureType.DISTANCE, areaM2 = 0.0, perimeterM = 123.0)
        assertWellFormed(gen(d)); assertTrue(text(gen(d)).contains("Longueur"))
        assertWellFormed(gen(TestData.parcel("Un point", TestData.square.take(1))))
        assertWellFormed(gen(TestData.parcel("Vide", emptyList())))
    }

    @Test fun specialCharactersAreEscaped() {
        val b = gen(TestData.parcel("Champ (nord) \\ \u201cX\u201d \u4e2d"))
        assertWellFormed(b)
        assertTrue(text(b).contains("Champ \\(nord\\) \\\\ \"X\" ?"))
    }

    @Test fun miniPdf_requiresAPage() {
        val pdf = MiniPdf()
        try { pdf.write(ByteArrayOutputStream()); throw AssertionError("aurait dû échouer") } catch (_: IllegalArgumentException) {}
    }
}
