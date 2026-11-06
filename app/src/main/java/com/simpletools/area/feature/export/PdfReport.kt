package com.simpletools.area.feature.export

import com.simpletools.area.core.database.ParcelEntity
import com.simpletools.area.core.geometry.AreaUnit
import com.simpletools.area.core.geometry.GeoMath
import com.simpletools.area.core.geometry.UnitFormat
import java.io.OutputStream
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Libellés du rapport (fournis par l'interface selon la langue). */
data class PdfLabels(
    val title: String,
    val date: String,
    val type: String,
    val typeName: String,
    val area: String,
    val perimeter: String,
    val length: String,
    val notes: String,
    val coordinates: String,
    val pointsCount: String,
    val disclaimer: String,
    val sketchCaption: String,
    val page: String,
)

/**
 * Rapport PDF A4 : nom, date, type, surface (m², ha, acres, ft²), périmètre, croquis vectoriel et tableau
 * des coordonnées (paginé). Aucune capture de fond de carte : les conditions des fournisseurs de tuiles ne
 * l'autorisent pas systématiquement, le PDF reste donc générable hors connexion et sans droit tiers.
 */
object PdfReport {
    private const val M = 40.0
    private val BLUE = Triple(21, 101, 192)
    private val INK = Triple(30, 41, 59)
    private val GRAY = Triple(100, 116, 139)

    fun write(parcel: ParcelEntity, labels: PdfLabels, locale: Locale, out: OutputStream) {
        val pdf = MiniPdf()
        val w = pdf.pageWidth.toDouble(); val h = pdf.pageHeight.toDouble()
        var pageNo = 1
        var page = pdf.newPage()
        var y = M + 10

        fun footer() {
            page.text(M, h - 28, 8.0, labels.disclaimer, color = GRAY)
            page.text(w - M - 40, h - 28, 8.0, "${labels.page} $pageNo", color = GRAY)
        }

        page.text(M, y, 22.0, labels.title, MiniPdf.Font.BOLD, BLUE); y += 28
        page.text(M, y, 16.0, parcel.name.take(60), MiniPdf.Font.BOLD); y += 22
        val dateStr = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(parcel.createdAt))
        page.text(M, y, 11.0, "${labels.date} : $dateStr"); y += 16
        page.text(M, y, 11.0, "${labels.type} : ${labels.typeName}"); y += 22

        val isDistance = parcel.areaM2 <= 0.0
        val per = UnitFormat.formatLength(parcel.perimeterM, UnitFormat.autoLengthUnit(parcel.perimeterM), locale)
        if (!isDistance) {
            page.text(M, y, 12.0, labels.area, MiniPdf.Font.BOLD); y += 17
            for (u in listOf(AreaUnit.SQUARE_METER, AreaUnit.HECTARE, AreaUnit.ACRE, AreaUnit.SQUARE_FOOT)) {
                page.text(M + 8, y, 11.0, "• " + UnitFormat.formatArea(parcel.areaM2, u, locale)); y += 15
            }
            y += 6
            page.text(M, y, 12.0, "${labels.perimeter} : $per", MiniPdf.Font.BOLD); y += 22
        } else {
            page.text(M, y, 12.0, "${labels.length} : $per", MiniPdf.Font.BOLD); y += 22
        }
        if (parcel.notes.isNotBlank()) {
            page.text(M, y, 11.0, "${labels.notes} : ${parcel.notes.take(110)}"); y += 20
        }

        val sketchH = 230.0
        drawSketch(page, parcel, M, y, w - 2 * M, sketchH, closed = !isDistance)
        y += sketchH + 12
        page.text(M, y, 9.0, labels.sketchCaption, color = GRAY); y += 22

        page.text(M, y, 12.0, "${labels.coordinates} (${parcel.points.size} ${labels.pointsCount})", MiniPdf.Font.BOLD); y += 16
        parcel.points.forEachIndexed { i, p ->
            if (y > h - 60) {
                footer(); pageNo++; page = pdf.newPage(); y = M + 10
            }
            page.text(M + 8, y, 10.0, String.format(Locale.ROOT, "%3d   %.7f   %.7f", i + 1, p.lat, p.lon), MiniPdf.Font.MONO)
            y += 14
        }
        footer()
        pdf.write(out)
    }

    private fun drawSketch(page: MiniPdf.Page, parcel: ParcelEntity, x: Double, y: Double, w: Double, h: Double, closed: Boolean) {
        page.rect(x, y, w, h, Triple(243, 245, 248), Triple(203, 213, 225))
        val pts = parcel.points
        if (pts.size < 2 || pts.any { !it.isValid }) return
        val plane = GeoMath.projectEqualArea(pts)
        val minX = plane.minOf { it.x }; val maxX = plane.maxOf { it.x }
        val minY = plane.minOf { it.y }; val maxY = plane.maxOf { it.y }
        val spanX = (maxX - minX).coerceAtLeast(1e-6); val spanY = (maxY - minY).coerceAtLeast(1e-6)
        val pad = 24.0
        val scale = minOf((w - 2 * pad) / spanX, (h - 2 * pad) / spanY)
        val offX = x + (w - spanX * scale) / 2
        val offY = y + (h - spanY * scale) / 2
        val sp = plane.map { (offX + (it.x - minX) * scale) to (offY + (maxY - it.y) * scale) }
        page.path(sp, close = closed, fill = BLUE, fillAlpha = 0.27, stroke = BLUE, lineWidth = 2.0)
        sp.forEach { (px, py) -> page.circle(px, py, 4.0, BLUE); page.circle(px, py, 2.4, Triple(255, 255, 255)) }
    }
}
