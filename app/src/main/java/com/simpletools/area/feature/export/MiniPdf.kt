package com.simpletools.area.feature.export

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.util.Locale

/**
 * Générateur PDF 1.4 minimal, sans dépendance et indépendant d'Android (donc testable en JVM).
 * Polices standard (Helvetica, Helvetica-Bold, Courier) encodées en WinAnsi : accents français et « ² » OK.
 * Origine des coordonnées : coin supérieur gauche de la page (unités = points PDF).
 */
class MiniPdf(val pageWidth: Int = 595, val pageHeight: Int = 842) {
    enum class Font(val resource: String) { REGULAR("F1"), BOLD("F2"), MONO("F3") }

    inner class Page internal constructor() {
        internal val content = StringBuilder()

        private fun n(v: Double) = String.format(Locale.ROOT, "%.2f", v)
        private fun rgb(r: Int, g: Int, b: Int) = "${n(r / 255.0)} ${n(g / 255.0)} ${n(b / 255.0)}"

        fun text(x: Double, y: Double, size: Double, s: String, font: Font = Font.REGULAR, color: Triple<Int, Int, Int> = Triple(30, 41, 59)) {
            content.append("BT /${font.resource} ${n(size)} Tf ${rgb(color.first, color.second, color.third)} rg ")
                .append("${n(x)} ${n(pageHeight - y)} Td (${escape(s)}) Tj ET\n")
        }

        fun rect(x: Double, y: Double, w: Double, h: Double, fill: Triple<Int, Int, Int>?, stroke: Triple<Int, Int, Int>?) {
            if (fill != null) content.append("${rgb(fill.first, fill.second, fill.third)} rg ")
            if (stroke != null) content.append("${rgb(stroke.first, stroke.second, stroke.third)} RG 1 w ")
            content.append("${n(x)} ${n(pageHeight - y - h)} ${n(w)} ${n(h)} re ")
            content.append(if (fill != null && stroke != null) "B\n" else if (fill != null) "f\n" else "S\n")
        }

        /** Polygone (ou polyligne) : remplissage semi‑transparent optionnel + contour. */
        fun path(
            pts: List<Pair<Double, Double>>, close: Boolean, fill: Triple<Int, Int, Int>?, fillAlpha: Double,
            stroke: Triple<Int, Int, Int>, lineWidth: Double,
        ) {
            if (pts.size < 2) return
            val p = StringBuilder()
            pts.forEachIndexed { i, (x, y) -> p.append("${n(x)} ${n(pageHeight - y)} ${if (i == 0) "m" else "l"} ") }
            if (close) p.append("h ")
            if (fill != null && close) {
                content.append("q /GS1 gs ${rgb(fill.first, fill.second, fill.third)} rg $p f Q\n")
                content.setLength(content.length) // alpha géré par GS1 (valeur fixée à l'écriture)
            }
            content.append("q ${rgb(stroke.first, stroke.second, stroke.third)} RG ${n(lineWidth)} w 1 j 1 J $p S Q\n")
        }

        fun circle(cx: Double, cy: Double, r: Double, fill: Triple<Int, Int, Int>) {
            val k = 0.5523 * r
            val y = pageHeight - cy
            content.append("${rgb(fill.first, fill.second, fill.third)} rg ")
                .append("${n(cx + r)} ${n(y)} m ")
                .append("${n(cx + r)} ${n(y + k)} ${n(cx + k)} ${n(y + r)} ${n(cx)} ${n(y + r)} c ")
                .append("${n(cx - k)} ${n(y + r)} ${n(cx - r)} ${n(y + k)} ${n(cx - r)} ${n(y)} c ")
                .append("${n(cx - r)} ${n(y - k)} ${n(cx - k)} ${n(y - r)} ${n(cx)} ${n(y - r)} c ")
                .append("${n(cx + k)} ${n(y - r)} ${n(cx + r)} ${n(y - k)} ${n(cx + r)} ${n(y)} c f\n")
        }
    }

    private val pages = ArrayList<Page>()
    var fillAlpha = 0.27

    fun newPage(): Page = Page().also { pages.add(it) }

    val pageCount get() = pages.size

    fun write(out: OutputStream) {
        require(pages.isNotEmpty()) { "Un PDF doit avoir au moins une page" }
        val buf = ByteArrayOutputStream()
        val offsets = ArrayList<Int>()
        fun w(s: String) = buf.write(s.toByteArray(Charsets.ISO_8859_1))
        fun obj(body: String) { offsets.add(buf.size()); w("${offsets.size} 0 obj\n$body\nendobj\n") }

        w("%PDF-1.4\n%âãÏÓ\n")
        // 1 catalogue, 2 pages, 3-5 polices, 6 ExtGState, puis (page, contenu) par page
        val firstPageObj = 7
        obj("<< /Type /Catalog /Pages 2 0 R >>")
        obj("<< /Type /Pages /Count ${pages.size} /Kids [${pages.indices.joinToString(" ") { "${firstPageObj + it * 2} 0 R" }}] >>")
        obj("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
        obj("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>")
        obj("<< /Type /Font /Subtype /Type1 /BaseFont /Courier /Encoding /WinAnsiEncoding >>")
        obj("<< /Type /ExtGState /ca ${String.format(Locale.ROOT, "%.2f", fillAlpha)} >>")
        pages.forEachIndexed { i, p ->
            val contentObj = firstPageObj + i * 2 + 1
            obj(
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $pageWidth $pageHeight] " +
                    "/Resources << /Font << /F1 3 0 R /F2 4 0 R /F3 5 0 R >> /ExtGState << /GS1 6 0 R >> >> /Contents $contentObj 0 R >>",
            )
            val data = p.content.toString()
            obj("<< /Length ${data.toByteArray(Charsets.ISO_8859_1).size} >>\nstream\n$data\nendstream")
        }
        val xrefPos = buf.size()
        w("xref\n0 ${offsets.size + 1}\n0000000000 65535 f \n")
        offsets.forEach { w(String.format(Locale.ROOT, "%010d 00000 n \n", it)) }
        w("trailer\n<< /Size ${offsets.size + 1} /Root 1 0 R >>\nstartxref\n$xrefPos\n%%EOF\n")
        buf.writeTo(out)
    }

    companion object {
        /** Échappement PDF + conversion vers WinAnsi (les caractères hors jeu deviennent « ? »). */
        fun escape(s: String): String {
            val sb = StringBuilder()
            for (ch in s) {
                val c = when (ch) {
                    ' ', ' ', ' ' -> ' '
                    '’', '‘' -> '\''
                    '“', '”' -> '"'
                    '–', '—' -> '-'
                    '•' -> '\u0095'
                    '·' -> '·'
                    else -> ch
                }
                when {
                    c == '(' || c == ')' || c == '\\' -> sb.append('\\').append(c)
                    c == '\n' || c == '\r' || c == '\t' -> sb.append(' ')
                    c.code in 32..126 || c.code in 160..255 || c.code == 0x95 -> sb.append(String.format("\\%03o", c.code).takeIf { c.code > 126 } ?: c.toString())
                    else -> sb.append('?')
                }
            }
            return sb.toString()
        }
    }
}
